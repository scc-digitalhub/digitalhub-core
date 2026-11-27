# Repository agent instructions

## Tests

- DO NOT add new tests when patching existing behavior. Only update relevant tests if they already exist.
- For NEW implementations, ALWAYS ask the user whether they want tests added before creating any tests. Do not add tests without explicit approval.

## Scope and approval

- Keep autonomous work as narrowly scoped as possible. If a change involves MORE than one module, ASK for confirmation before making the change.
- If a change modifies an external, public-facing API mapped under `/api/v1/**`, WARN the user about the API impact and ASK for confirmation before making the change.

## Core design rules (entities, lifecycle, persistence, identity)

These rules are binding. Rationale and flows: `docs/core-design.md`. Any behavior change not explicitly agreed with the user must be surfaced, never decided silently.

### Persistence and transactions

- Only repository writes are transactional: one `REQUIRES_NEW` transaction per `create`/`update`/`delete` in `BaseEntityRepositoryImpl`. NEVER add `@Transactional` to entity services, lifecycle managers, finalizers, dispatchers or any orchestration code.
- Orchestration is never atomic: external actions can not be rolled back, only single data changes are.
- Repository `update` rebuilds the entity from the DTO and then RESTORES the fixed fields (`id`, `kind`, `project`, `name`, `created`, `createdBy`): derived entities may change any other field. Keep this logic.
- Repository `create` uses `persist` (never `merge`): an existing row must never be silently overwritten. Only unique violations map to `DuplicatedEntityException`.
- `EntityEvent`s are published inside the repository transaction and delivered `AFTER_COMMIT`. Listeners must not rely on an outer transaction; use `fallbackExecution = true` when an event may be published outside a transaction.
- Bulk deletes in services go ONE BY ONE: `repository.delete` without cascade, the full delete flow (operations bus or inline) with cascade.

### Per-object turns (ordering)

- Every operation on an existing object runs inside the object's turn (`EntityLockManager`, shared bean, keyed by DTO type + id, re-entrant per thread). Services take the turn for read → compute → write; lifecycle and repository writes re-enter it.
- Reads never take the turn.
- Inside a turn always start from the STORED object, read within the turn. A lifecycle call made by a caller already holding the turn trusts the caller's DTO.
- Never wait for another object's turn while holding one: cross-object work runs after release (`EntityLockManager.runAfterRelease`) or asynchronously.
- Turns are thread-bound: work running on other threads while a turn is held (FSM logic, processors, post processors) must NEVER write the same object; it returns data instead.

### State and FSM

- Only the FSM changes state. Updates without a state change keep the STORED state; a missing state in an update means "keep the current one".
- `perform(event)` executes exactly one step. `handle(state)` reconciles to a state following the shortest path (`Fsm.pathTo`: at least one step, transitions in declaration order, same state only via explicit self loop). The path is planned BEFORE starting: no path → `InvalidTransitionException`, nothing written.
- Create persists `CREATED`, then walks to the requested state via `handle`. FSM authors are responsible for graphs without ambiguous paths.
- Invalid transitions and rejected FSM executions throw back to the caller: nothing written, no ERROR. Failures DURING a step move the object to ERROR (no automatic recovery: what is allowed from ERROR is decided by the FSM, at least deleting again), keeping the partially changed DTO (the pre-step DTO on FSM timeout). The ERROR step receives the operation input.
- From the FSM context take back ONLY `metadata`, `spec` and `status`: every other field is effectively final.
- All lifecycle writes go through `AbstractLifecycleManager.exec`, all lifecycle reads through `AbstractLifecycleManager.read`. `BaseLifecycleManager` must never call `entityRepository` directly.

### Step pipeline

1. FSM transition logic: on the FSM executor, on a copy of the DTO, bounded (10s).
2. Processors: batches metadata → spec → status. Each processor gets its own copy of the DTO and the operation INPUT; processors in a batch run in parallel (10s each); each batch sees the results merged by the previous ones.
3. Post processors (`PostProcessor`, `@PostProcessorType`, `"*"` = all stages): alter the transition OUTPUT, never the DTO. Chained in `@Order` (default lowest precedence), each on its own copy of the output, 10s each, 30s per chain. A transition produces at most one output (ex. one runnable per run).
4. Write via `exec` (`DELETED` → `exec(DELETE)` removes the object).
5. Effects (`Effect`, `void`): side effects only, dispatched asynchronously after the write, each isolated, never affecting the transition. For DETACHED transitions this step is replaced (see "Detached transitions").

Failure handling for processors and post processors: `CoreRuntimeException` FAILS the step (ERROR); any other exception, timeout or executor rejection is logged and skipped.

After the outermost turn is released: the caller effect runs once per written (attached) step (ERROR included), in order, then ONE `LifecycleEvent` is published for the whole operation. Events publish outcomes: never perform work driven by `LifecycleEvent`s.

- Credentials and configurations are injected by `RunCredentialsPostProcessor` / `RunConfigurationPostProcessor` (all stages, highest precedence, explicit failure). Credentials are filled only when the runnable has none (runtimes managing credentials themselves, ex. Hera/KFP, keep theirs); configurations are appended. Runtimes must NOT inject default credentials or configurations.
- Internal work uses lifecycle/service APIs (ex. `runManager.create(...)` + `runManager.perform(...)`), never `EntityOperation` workarounds: operations are for asynchronous dispatch only.

### Detached transitions

- A transition declared `.detached()` by the FSM is written as any step (synchronously, by the caller: invalid transitions still throw back). Then, after the turn is released, its stage effects run on a DETACHED execution: registered in the `DetachedRegistry` (JDBC, `detached_operations`), delayed with a backoff growing with attempts (capped), on virtual threads, without time bounds, in `@Order`. `CoreRuntimeException` → ERROR, anything else logged and skipped. Detached effects are never dispatched async as well.
- After the effects, the CALLER EFFECT (the callback passed to `perform`/`handle`) runs WITHIN the object turn, only if the object is still in the detached state. Its calls on the manager re-enter the turn: that is the only safeguard, no other guard. The FSM never carries callbacks and never references the manager.
- A `perform`/`handle` started from the detached execution of the SAME object writes its step now and its detached part is SCHEDULED again (same registry entry when same state: start time kept, attempts counted). Detached work of OTHER objects reached from a detached execution runs INLINE in the same thread, never registered.
- At most one live execution per object state: further launches collapse into it.
- Timeout (12h default, `lifecycle.detached.*`): the execution is interrupted, the object moves to ERROR. Restart: entries left without execution move their object to ERROR (`DetachedRecovery`), NEVER re-run (in-memory state is lost); the user operates again from ERROR. Messages go in the status, never as transition input.
- Transitions without an event are reachable only via `handle` (by design).
- FSM definitions are beans injected into managers: never inject a service or manager of the same type into an FSM definition; other dependencies via `ObjectProvider`, resolved at call time. Heavy work belongs to effects (resolved lazily by the effect registry).

### Delete and finalize

- Finalizers ARE effects on stage `onDeleting` (`@EffectType(stages = "onDeleting", type = X.class)`), many per type, `@Order`, `void`, idempotent (re-run by retries). They clean children and owned content (files, logs, metrics, registry images), never the object itself; they use services/managers, not repositories. Shared logic lives in base classes (`BaseFilesFinalizer`, `BaseEntityMetricsFinalizer`) extended per type.
- With a lifecycle: `DELETE` → `DELETING` (detached, with a `DELETING -(DELETE)-> DELETING` loop for retries) → finalizers → the caller concludes: `DELETED` (repository delete) or ERROR; `DELETING → DELETED/ERROR` have no event. `cascade` is not considered.
  - Default conclusion: inline lambda in `BaseEntityServiceImpl.doDelete` (`DELETING` → `handle(DELETED)`).
  - Entities with children to wait for conclude in their lifecycle manager (wrapping `DELETE` for every caller), with the service overriding `doDelete` without lambda: runs (`RunLifecycleManager`: runnable still present → delete again, gone → `DELETED`) and projects (`ProjectLifecycleManager`: child in ERROR → ERROR, children left → delete again, none → `DELETED`).
  - Children are deleted one by one by the parent finalizer (inline, skipping children already `DELETING`); shared content (ex. project secrets, labels) only once no child is left.
  - Failed removal of owned content throws `CoreRuntimeException` (→ ERROR, delete again to retry); a missing item (404) is not a failure.
- Without a lifecycle (not fully managed): repository delete, then with `cascade` a FINALIZE operation (bus, inline as fallback) runs `BaseEntityFinalizer` (every entity extends it): the type's `onDeleting` effects, best effort, all run, failures logged.
- Triggers have no `DELETING`: `actuator.stop()` in the `→ DELETED` logic is quick and idempotent.

### Runs and runnables

- A run has at most one runnable, with the run id: it is the run's child, found across all `RunnableStore`s by run id.
- The runnable delete is requested by the `DELETING` transition logic (`runtime.delete`), on every pass (retry). Framework deletes treat "not found" (404) as already deleted, fail on any other error of the MAIN resource (secondary resources only logged), and return the runnable in `DELETING`.
- Listener and monitors share a per-runnable turn (`EntityLockManager`, keyed by runnable type + id). The listener stores `DELETING` before acting and keeps it; monitors re-read each runnable within its turn and confirm `DELETED` only when the main resources are gone (HTTP 404, `K8sFrameworkException.isNotFound()`).
- Runnable events never drive `handle(DELETED)` (a `DELETED` runnable also comes from the cleanup of completed/failed runs). For a run in `DELETING` only a runnable `ERROR` is handled (→ run ERROR).

### Identity and ownership

- The caller identity is used ONLY for authorization at the boundary.
- EVERY operation on an existing object (API, async operation, runtime callback) runs on behalf of its OWNER: `OwnerContextManager.asOwner`, with the owner taken from the STORED object, never from the caller DTO. Scopes nest and restore.
- `DelegatedSecurityContext` carries the owner authentication plus the real user (actor); the auditor writes the actor into `updatedBy`.
- Creation runs as the creator, who becomes the owner.
- Async paths restore the owner; dispatched runnables are always stamped with `run.getUser()`.
- Work submitted to other threads must receive the current security context explicitly.

### Caching

- Repositories cache DETACHED ENTITIES programmatically (no `@Cacheable`); DTOs are rebuilt on every read, so callers never share instances.
- Cache updates happen after commit, inside the turn: put on write, evict on delete or any failure, clear on bulk changes. Prefer an extra invalidation over stale content.
