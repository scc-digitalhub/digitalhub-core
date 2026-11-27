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
- **Failure during a step**: the step's logic may already have acted on the outside world, and that can not be undone. The object moves to ERROR, with no automatic recovery (the FSM decides what is allowed from ERROR, at least deleting again), keeping the partially changed DTO: it records what was done. When the transition logic timed out its changes are discarded and the pre-step DTO is used. The ERROR step receives the operation input (runtimes use it to clean up).
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
└─ 5. effects                     async, fire and forget (detached transitions: see below)
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

Post processors (`PostProcessor`, registered with `@PostProcessorType`) alter the **output** of a transition, typically the runnable a runtime produced, never the object. They are chained in `@Order` (default lowest precedence): each receives a copy of the previous output and returns a new one, or null to keep it. Registering on the stage `"*"` runs a post processor on every stage. A transition produces at most one output: for runs, one runnable with the run id.

Examples: `RunCredentialsPostProcessor` and `RunConfigurationPostProcessor` (highest precedence) inject the owner's credentials and the platform configurations into runnables. Credentials are filled only when a runtime did not set any, so runtimes with restricted credentials (Hera, KFP pipelines) keep theirs; configurations are appended. `S3CredentialsProcessor` and `RunInitializerProcessor` then adapt the runnable.

### Failure rule for processors and post processors

Implementations are responsible for signalling real failures:

- throwing `CoreRuntimeException` fails the step, and the object moves to ERROR;
- any other exception, a timeout or a full executor is logged and skipped, so a badly written processor can not break the whole system.

### Effects

Effects (`Effect`, returning `void`) are side effects: they are dispatched asynchronously after the step is written, each one isolated, and they never influence the transition. Example: the autoscaler, which requests a stop or a restart through the run lifecycle.

Effects of a detached transition are the exception: they run in order on the detached execution, and their failure matters (see "Detached transitions"). Finalizers are effects of this kind.

### Caller effect and events

A caller of `perform`/`handle` may pass its own effect. It runs after the turn is fully released, once per written step, with the persisted object. ERROR steps are included: the effect checks the state it receives. For a detached step the caller effect runs later, on the detached execution (see below).

Then **one** `LifecycleEvent` is published for the whole operation, carrying the final state. Intermediate steps of a walk are not published: how many steps were needed is not part of the caller's intent. Events are for publishing outcomes. Processing must never be driven by events: it belongs to the FSM, processors, post processors and effects.

## Detached transitions

Some transitions do work that can take long, minutes or hours: deleting the files of an artifact, waiting for Kubernetes to remove a job, deleting every child of a project. They can not hold a caller, nor a turn, nor fit the 10s bounds of a step. The FSM declares them **detached** (`Transition.Builder.detached()`); nothing else changes in the FSM, which stays self contained.

```
caller: perform(DELETE)                         detached execution (virtual thread)
│                                               │
├─ step written as usual (→ DELETING)           ├─ delay (backoff by attempts, capped)
├─ registered in the DetachedRegistry           ├─ stage effects, in @Order, no time bound
└─ returns: the object is DELETING              │    CoreRuntimeException → ERROR
                                                ├─ turn acquired, object re-read
                                                │    still DELETING? → caller effect
                                                │      (calls on the manager re-enter the turn)
                                                └─ done, or scheduled again
```

- The step is written synchronously by the caller: an invalid transition still throws back, nothing written. Only the effects and the caller effect are detached.
- The **caller effect decides** what comes next, looking at the actual object, and acts like any code: `handle(DELETED)`, `perform(DELETE)` again, or nothing. It runs inside the object's turn after re-reading it, and only if the object is still in the detached state; its calls re-enter the turn. That is the whole safeguard: nobody can move the object between the check and the act, and a late callback can never resurrect or delete an object that someone else moved to ERROR meanwhile.
- A `perform`/`handle` started from the detached execution of the **same** object writes its step now, and its detached part is scheduled again, delayed. With the same state it keeps the same registry entry: the start time is kept, attempts are counted and drive the backoff (1s base, 1h cap by default).
- Detached work of **other** objects reached from a detached execution (a project deleting its children) runs **inline** in the same thread and is never registered: the parent sees the result when the call returns.
- At most one live execution per object and state: launches collapse into it.

The registry (`detached_operations` table, JDBC) is the ledger of executions in progress:

- after the **timeout** (12h by default, `lifecycle.detached.timeout`) the execution is interrupted (cooperatively) and the object moves to ERROR;
- after a **restart**, entries left without an execution move their object to ERROR (`DetachedRecovery`). They are never re-run: effects and services may have held state in memory, so resuming autonomously is not safe. The ERROR is visible to the user, on the object and in parent/child views, and the user can operate again from ERROR.

Transitions without an event are a design feature: they can be reached only via `handle`, for example `DELETING → DELETED`.

FSM definitions are beans injected into their managers. To avoid dependency cycles they never inject a service or manager of their own type; other dependencies are resolved lazily (`ObjectProvider`). Heavy work belongs to effects, which the effect registry resolves lazily.

## Delete and finalize

Deleting an object usually means cleaning up what it owns: child objects (a project's functions and runs, a function's tasks) and content living elsewhere (files in stores, logs, metrics, registry images, Kubernetes resources). That is the finalizers' job.

**Finalizers are effects on the `onDeleting` stage**, many per type, ordered with `@Order`, idempotent (retries re-run them). They use services and managers, never repositories: children have children, and going through their lifecycle keeps every level consistent. Shared logic lives in base classes extended per type (`BaseFilesFinalizer` and `BaseVersionableFilesFinalizer` for artifacts, data items, models and extensions, see "Files and ownership"; `BaseEntityMetricsFinalizer` for runs and models).

### With a lifecycle

The FSM has a detached `DELETING` state with a retry loop:

```
CREATED | READY | ERROR | ...  -(DELETE)->  DELETING   [detached]
DELETING                       -(DELETE)->  DELETING   [detached, retry]
DELETING                                ->  DELETED    (no event)
DELETING                                ->  ERROR      (no event)
```

The finalizers run as the detached effects of `DELETING`, then the caller concludes. By default the conclusion is the inline lambda passed by `BaseEntityServiceImpl.doDelete`: still `DELETING`, so `handle(DELETED)`, which removes the object. A failed removal of owned content throws `CoreRuntimeException`: the object moves to ERROR with the message and the user deletes it again; a missing item (404) is not a failure.

Entities that must wait for children conclude in their own lifecycle manager, which wraps `DELETE` for every caller (API, lifecycle actions, cascades); their service overrides `doDelete` without a lambda:

- **runs**: the runnable is the child. While it is still in a runnable store the run deletes again later; once it is gone the run reaches `DELETED`.
- **projects**: the project finalizer deletes every child one by one (inline, skipping children already `DELETING`), and removes the shared content (secrets, labels) only when no child is left. Then the project lifecycle checks the children: one in ERROR fails the project, children left mean another pass later (a run waiting for Kubernetes), none concludes the delete. No orphans, shared content removed last.

Functions and workflows delete their tasks (no lifecycle, synchronous); tracking their runs is still to do.

`cascade` is not considered with a lifecycle: deleting always cleans up.

### Without a lifecycle

The object is not fully managed: it is removed, then, with `cascade`, a FINALIZE operation (operations bus, inline as fallback) runs `BaseEntityFinalizer`, which every entity extends: the type's `onDeleting` effects, best effort, every effect runs and failures are logged. There is nothing left to move to ERROR.

### Bulk deletes and internal work

Bulk deletes (`deleteByProject`, `deleteByKind`, …) go object by object: with cascade each object goes through the full flow (via the operations bus, possibly in parallel, or sequentially as fallback); without cascade objects are removed from the store one by one, each in its own turn and transaction.

Internal work uses the lifecycle and service APIs directly, for example a trigger firing creates its run as `BUILT` and then performs `RUN`. `EntityOperation`s are only used to dispatch work asynchronously.

## Runs and runnables

A run executes through a **runnable**, built by its runtime and executed by a framework (Kubernetes jobs, deployments, services, custom resources, Argo, Ray, Kaniko). Each run has at most one runnable, with the run id: it is the run's child, found across all runnable stores by run id.

Deleting a run:

1. `DELETE` moves the run to `DELETING`; the transition logic asks the runtime for the delete runnable, dispatched to the framework listener. Every retry pass asks again.
2. The listener, holding the runnable's turn, stores it as `DELETING`, asks the framework to delete it and keeps it `DELETING`. "Not found" (404) means already deleted; any other failure on the main resource fails the delete (the run moves to ERROR); secondary resources are only logged.
3. The monitor re-reads each runnable within its turn; for a `DELETING` runnable it checks whether the main resources still exist, and only when they are gone (404) marks it `DELETED` and removes it from the store.
4. Meanwhile the run's finalizers remove logs and user metrics, and the run lifecycle checks the store on every pass: runnable still there, delete again later; gone, `DELETED`.

The listener and the monitors share the per-runnable turn (`EntityLockManager`, keyed by runnable type and id), so the monitor never overwrites what the listener is doing with a stale view.

Runnable events never complete a run delete: a `DELETED` runnable also comes from the cleanup of completed or failed runs and is ignored. For a run being deleted only a runnable `ERROR` is handled, moving the run to ERROR.

## Files and ownership

The project is the root of its files: everything lives under the **project base path**, `<store>/<project>/`, where the store is the one configured for the project or the default one. The base is resolved in one place, `FilesService.getProjectBasePath(project)`, through a `ProjectPathResolver`: a default bean (default store) and a project aware one in the application, `@Primary`, which reads the project configuration. Stores know only paths; the files module knows nothing about entity types.

Under the base, managed entities have a fixed layout; anything else is raw project files:

```
<store>/<project>/                                  project base (raw files allowed anywhere else)
  <type>/<name>/<id>/                               a version of an entity (type, name as in the key)
  <type>/<name>/<id>/extensions/<ext name>/         an extension, inside its parent folder
```

Every entity type name is reserved as first level folder. The layout gives each version its own folder, so ownership is structural: deleting an entity removes its folder, with no guessing about who else uses the files.

**The path is assigned when uploading.** `spec.path` is optional at create. On the transition to `UPLOADING` a processor of the entity type (in the application, which knows all types) sets an empty path to the own folder, or validates a given one: within the own type folder it must be a folder of the same name (any version, to reuse files of a previous version); within another type folder it is invalid; anything else (other stores, external URLs, raw files) is accepted but never owned. The step returns the actual path: SDK and CLI write there directly with their keys, the console asks for upload links. After that `spec.path` is the source of truth, never recomputed, and changes only if the client forces a spec update (a warning: it may break things, like changing the store of a project).

**Upload links** are issued only for objects in `UPLOADING`, inside the folder of their path; for objects not created yet, in the pre-create folder of their type, name and id. Extensions upload in their folder with the parent in any state (a validation report on a `READY` dataset), also before the extension exists through the parent endpoints. Clients holding keys write directly: the backend controls where paths point, not who writes.

**Deletes remove only owned folders.** A version owns `<base><type>/<name>/<id>/`; versions of the same name may share a folder (a new version reusing the code of the previous one), so the folder is removed only when no other version still in use references it: the last one removes it. Versions sharing a folder share its content, `READY` does not freeze it. An extension owns its folder. Files outside the layout, external or under an old base after a store change are never removed (they drifted, and that is fine). The project delete removes the whole base, raw files included, after its children and before its secrets, only when the base is well formed.

Why a fixed layout instead of mirroring user organization in storage: moving files is expensive and not atomic on object stores, and every path is referenced by runs, lineage and workloads. Organization belongs to the (virtual) catalog, storage addresses stay stable. Content addressed storage was considered and set aside: workloads read real folders.

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
- the detached registry would then need an owner or lease per entry; an operator would take over with requeues;
- an entry transition "from nothing" in the FSM (instead of the conventional `CREATED`) is planned and would make creation fully FSM driven;
- a virtual file system (folders as entities, `folderId` on the name) will organize entities without moving files: views resolve names to the latest `READY` version folder through `spec.path`;
- per-project credentials become possible since every managed access is inside the project base.
