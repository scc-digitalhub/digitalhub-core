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
- Invalid transitions and rejected FSM executions throw back to the caller: nothing written, no ERROR. Failures DURING a step move the object to ERROR (terminal, no recovery), keeping the partially changed DTO (the pre-step DTO on FSM timeout). The ERROR step receives the operation input.
- From the FSM context take back ONLY `metadata`, `spec` and `status`: every other field is effectively final.
- All lifecycle writes go through `AbstractLifecycleManager.exec`, all lifecycle reads through `AbstractLifecycleManager.read`. `BaseLifecycleManager` must never call `entityRepository` directly.

### Step pipeline

1. FSM transition logic: on the FSM executor, on a copy of the DTO, bounded (10s).
2. Processors: batches metadata → spec → status. Each processor gets its own copy of the DTO and the operation INPUT; processors in a batch run in parallel (10s each); each batch sees the results merged by the previous ones.
3. Post processors (`PostProcessor`, `@PostProcessorType`, `"*"` = all stages): alter the transition OUTPUT, never the DTO. Chained in `@Order` (default lowest precedence), each on its own copy of the output, 10s each, 30s per chain, collections processed per element.
4. Write via `exec` (`DELETED` → `exec(DELETE)` removes the object).
5. Effects (`Effect`, `void`): side effects only, dispatched asynchronously after the write, each isolated, never affecting the transition.

Failure handling for processors and post processors: `CoreRuntimeException` FAILS the step (ERROR); any other exception, timeout or executor rejection is logged and skipped.

After the outermost turn is released: the caller effect runs once per written step (ERROR included), in order, then ONE `LifecycleEvent` is published for the whole operation. Events publish outcomes: never perform work driven by `LifecycleEvent`s.

- Credentials and configurations are injected by `RunCredentialsPostProcessor` / `RunConfigurationPostProcessor` (all stages, highest precedence, explicit failure). Credentials are filled only when the runnable has none (runtimes managing credentials themselves, ex. Hera/KFP, keep theirs); configurations are appended. Runtimes must NOT inject default credentials or configurations.
- Internal work uses lifecycle/service APIs (ex. `runManager.create(...)` + `runManager.perform(...)`), never `EntityOperation` workarounds: operations are for asynchronous dispatch only.

### Delete and finalize

- With a lifecycle, the FSM decides: with an intermediate `DELETING` state (runs, triggers) `DELETE` → `DELETING` → caller effect runs the finalizer → `DELETED` → repository delete; FSMs going directly to `DELETED` remove the object, then the finalizer runs. The finalizer never runs when the result is ERROR.
- Without a lifecycle (deliberate developer choice): repository delete, then finalize, best effort.
- Finalize is dispatched via the operations bus; when the bus is unavailable or full it runs inline.

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
