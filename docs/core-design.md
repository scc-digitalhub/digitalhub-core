# Core Design: Entities, Lifecycle, Persistence, Identity

This document explains how entities are stored and how their lifecycle is driven in DigitalHub Core: the roles of entity services, repositories, the lifecycle manager and the FSM, how concurrent operations are ordered, and on whose behalf operations run.

The binding rules for code changes are summarized in `AGENTS.md` ("Core design rules"). This document explains the reasoning behind them.

## Overview

Four layers cooperate on every entity:

| Layer | Class | Responsibility |
|---|---|---|
| Entity service | `BaseEntityServiceImpl` | API facing operations: create, update, delete, search. Validates input, takes the turn, decides between a plain write and a lifecycle transition. |
| Lifecycle manager | `BaseLifecycleManager` (on `AbstractLifecycleManager`) | Drives objects through their FSM: plans and executes steps, runs processors, post processors and effects, persists each step. |
| FSM | `Fsm`, built by kind-specific factories | Owns the state graph and the business rules of each transition. Written by the owner of each entity kind. |
| Repository | `BaseEntityRepositoryImpl` | The persistence port: atomic writes, events after commit, cached reads. |

The guiding idea: **data changes are atomic, orchestration is not**. A transition may call Kubernetes, build an image or create other objects: none of this can be rolled back. So only single writes are transactional, everything else is designed to be consistent without transactions: operations on the same object are ordered, the FSM guards every state change, and failures are recorded instead of undone.

## Persistence and transactions

Every repository write (`create`, `update`, `delete`) runs in its own transaction (`REQUIRES_NEW`), inside the object's turn, and publishes its `EntityEvent` from within that transaction, so listeners (index, relationships, notifications, audit) run after commit. Services, lifecycle managers, finalizers and dispatchers are **not** transactional.

Why `REQUIRES_NEW` when callers are not transactional anyway: async dispatch may reuse threads, and a stray outer transaction must never make a write part of someone else's unit of work. When an outer transaction is detected the repository logs it at debug level.

Details worth knowing:

- `create` uses `persist`, after checking existence inside the turn: an existing row is never silently overwritten. Only unique constraint violations become `DuplicatedEntityException`; other integrity errors are store errors.
- `update` rebuilds the entity from the DTO and restores the fixed fields (`id`, `kind`, `project`, `name`, `created`, `createdBy`). Derived entities may freely change any other field.
- Bulk deletes are done object by object (see "Delete and finalize").

This contract (each write atomic, its event delivered once durable) keeps transaction details out of services and lifecycle code. A different repository implementation, for example one backed by Kubernetes custom resources, can implement the same interface.

Known limitation: events are kept in memory. A crash between commit and delivery loses the event (an outbox would be needed to guarantee delivery).

## Per-object turns

Operations on the **same object** are executed one at a time, in no particular order. Operations on different objects are independent. This is implemented by `EntityLockManager` (local implementation: `LocalEntityLockManager`), one shared component used by services, lifecycle managers and repositories:

- keyed by DTO type and id;
- re-entrant per thread: a service holding the turn can call the lifecycle manager and the repository for the same object without blocking itself. A whole service → lifecycle → repository chain is one operation;
- locks are released from memory when unused;
- waiting is bounded (60s by default); a timeout names the thread holding the turn;
- `runAfterRelease` defers work until the current thread fully releases the turn (outermost release).

Taking turns is only useful if the operation reads the object **inside** its turn. So every operation starts from the stored object read within the turn, never from a copy read before. When the lifecycle manager is called by a caller already holding the turn (for example the service), it trusts the DTO the caller read.

Two further rules keep turns safe:

- **Never wait for another object's turn while holding one.** Work on other objects (finalizers, triggered runs) happens after release or asynchronously. This prevents lock cycles.
- **Turns belong to a thread.** FSM logic, processors and post processors run on executor threads while the caller holds the turn: they must never write to the object being processed, they return data instead.

Reads never take a turn: they see the last committed version.

The local implementation covers a single replica. The interface allows a distributed implementation later (database row lock, Kubernetes Lease) without changing callers.

## State and the FSM

**Only the FSM changes state.** The FSM, defined per entity kind by its owner, is the authority on which transitions are valid and what they do. Everything else respects it:

- an update that does not change state keeps the stored state, and a missing state in an update means "keep the current one";
- a state change requested by an update or a create goes through the lifecycle manager.

The lifecycle manager offers two ways to move an object:

- `perform(event)`: "execute this", one step. Find the transition matching the event on the current state and follow it.
- `handle(state)`: "I observed this state, reconcile". From the current state, find the shortest path to the target (`Fsm.pathTo`: breadth first, at least one step, transitions in declaration order) and walk it step by step. Reaching the same state requires an explicit self loop in the FSM (used for progress updates such as `RUNNING → RUNNING`).

The path is planned **before** anything runs: when there is no path, `InvalidTransitionException` is thrown and nothing is written. FSM authors are responsible for graphs without ambiguous paths.

Creation persists the object as `CREATED` first, so the full lifecycle (processors, effects, events) applies to every step, then walks to the requested state. For example a run created as `BUILT` goes `CREATED → BUILT`, and the build logic runs.

A transition works on a copy of the object; from its result only `metadata`, `spec` and `status` are taken back. Every other field is effectively final.

### Failures

- **Invalid transition, or no path**: thrown back to the caller. Nothing is written, the object does not move to ERROR.
- **FSM executor full**: the transition never started, so it is thrown back to the caller, nothing written.
- **Failure during a step**: the step's logic may already have acted on the outside world, and that can not be undone. The object moves to ERROR, which is terminal, keeping the partially changed DTO: it records what was done. When the transition logic timed out its changes are discarded and the pre-step DTO is used. The ERROR step receives the operation input (runtimes use it to clean up).
- If ERROR itself can not be reached, the failure is propagated.

All lifecycle writes go through `AbstractLifecycleManager.exec` (write-back under the turn) and all reads through `AbstractLifecycleManager.read`.

## The step pipeline

A lifecycle operation (`perform` or `handle`) runs on behalf of the owner, inside the object's turn. Each step:

```
turn acquired, stored object read
│
├─ 1. FSM transition logic        fsm executor, copy of the dto, 10s
├─ 2. processors                  metadata → spec → status batches, 10s each
├─ 3. post processors             chained on the transition output, 10s each, 30s chain
├─ 4. write                       via exec (DELETED removes the object)
└─ 5. effects                     async, fire and forget
│
next step (handle walks) or turn released
│
├─ caller effect, once per written step, in order (ERROR included)
└─ one LifecycleEvent for the whole operation
```

Every stage is time bounded, so a step is bounded too.

### Processors

Processors contribute partial `metadata`, `spec` or `status`, merged into the object being processed. They receive the operation **input** (for example the runnable reported by a runtime callback).

- Batches run in order: metadata, then spec, then status. Each batch sees the results merged by the previous batches.
- Within a batch processors run in parallel, each on its own copy of the object: they never see each other's changes. `@Order` decides the merge order.

### Post processors

Post processors (`PostProcessor`, registered with `@PostProcessorType`) alter the **output** of a transition, typically the runnable a runtime produced, never the object. They are chained in `@Order` (default lowest precedence): each receives a copy of the previous output and returns a new one, or null to keep it. Registering on the stage `"*"` runs a post processor on every stage. Collection outputs go through the chain element by element.

Examples: `RunCredentialsPostProcessor` and `RunConfigurationPostProcessor` (highest precedence) inject the owner's credentials and the platform configurations into runnables. Credentials are filled only when a runtime did not set any, so runtimes with restricted credentials (Hera, KFP pipelines) keep theirs; configurations are appended. `S3CredentialsProcessor` and `RunInitializerProcessor` then adapt the runnable.

### Failure rule for processors and post processors

Implementations are responsible for signalling real failures:

- throwing `CoreRuntimeException` fails the step, and the object moves to ERROR;
- any other exception, a timeout or a full executor is logged and skipped, so a badly written processor can not break the whole system.

### Effects

Effects (`Effect`, returning `void`) are side effects: they are dispatched asynchronously after the step is written, each one isolated, and they never influence the transition. Example: the autoscaler, which requests a stop or a restart through the run lifecycle.

### Caller effect and events

A caller of `perform`/`handle` may pass its own effect. It runs after the turn is fully released, once per written step, with the persisted object. ERROR steps are included: the effect checks the state it receives.

Then **one** `LifecycleEvent` is published for the whole operation, carrying the final state. Intermediate steps of a walk are not published: how many steps were needed is not part of the caller's intent. Events are for publishing outcomes. Processing must never be driven by events: it belongs to the FSM, processors, post processors and effects.

## Delete and finalize

Deleting an object may require garbage collecting related objects (a project's functions and runs, a function's tasks): that is the finalizer's job.

- **With a lifecycle manager** the FSM decides the order, and the finalizer never runs when the delete ended in ERROR:
  - FSMs with an intermediate `DELETING` state (runs, triggers): `DELETE` moves the object to `DELETING`, the service's effect runs the finalizer, then the object reaches `DELETED` and is removed. Runs with nothing to delete on the runtime side complete `DELETING → DELETED` through the FSM right away; otherwise the runtime callback does.
  - FSMs going directly to `DELETED` (models, artifacts, data items, …): the object is removed, then the finalizer runs.
- **Without a lifecycle manager**: the object is removed, then finalized, best effort. Not having a lifecycle is a deliberate choice of the entity developer.

Finalization is dispatched through the operations bus; when the bus is unavailable or full it runs inline.

Bulk deletes (`deleteByProject`, `deleteByKind`, …) go object by object: with cascade each object goes through the full flow (via the operations bus, possibly in parallel, or sequentially as fallback); without cascade objects are removed from the store one by one, each in its own turn and transaction.

Internal work uses the lifecycle and service APIs directly, for example a trigger firing creates its run as `BUILT` and then performs `RUN`. `EntityOperation`s are only used to dispatch work asynchronously.

## Identity and ownership

Every object has an owner (its creator). The rule:

- **The caller's identity is used only for authorization**, at the API boundary: may this user act on this object?
- **Every operation on an existing object runs on behalf of its owner**: API calls after authorization, asynchronous operations, runtime callbacks.

The reason is consistency and safety. Runtimes build credentials from the current security context. If an admin resumed a user's workspace with the admin's own identity, the admin's credentials would end up inside the user's workload.

How it works:

- `OwnerContextManager.asOwner` switches the security context to the owner, taken from the stored object, never from the caller's DTO. Scopes nest (a cascade on a child owned by someone else switches again) and restore the previous context when closed.
- The installed `DelegatedSecurityContext` carries the owner's authentication, used for execution and credentials, and the real user who asked (the actor). The auditor writes the actor into `updatedBy`: "B stopped A's workspace" stays visible.
- Creation runs as the creator, who becomes the owner.
- Asynchronous paths (operations, events, runtime callbacks) restore the owner. Runnables are always stamped with the run's owner, so all later callbacks act as the owner.
- Work handed to executor threads receives the current security context explicitly.

## Caching

Repositories cache **detached entities**, and build a new DTO on every read. Callers can modify the DTOs they get without affecting anybody else, and a cache hit costs a conversion instead of a database round trip.

- Single object reads load through the cache atomically: a load runs once per key, concurrent readers wait for it.
- After a write commits, still inside the turn, the saved entity is put into the cache. A write waits for an in-flight load of the same key and then overwrites it, so a slow reader can never put an old version back.
- Deletes and any failed write evict; bulk changes clear the cache. When in doubt, invalidate.
- Entries expire automatically, so old objects are not kept warm.

The cache is local to each replica: with several replicas another node may serve an older version until expiry.

## Looking ahead

The design keeps transaction and locking details behind interfaces on purpose:

- the repository contract (atomic write, event after it is durable) can be implemented on top of Kubernetes custom resources, for a core-side operator managing runnables;
- `EntityLockManager` can get a distributed implementation for multiple replicas;
- an entry transition "from nothing" in the FSM (instead of the conventional `CREATED`) is planned and would make creation fully FSM driven.
