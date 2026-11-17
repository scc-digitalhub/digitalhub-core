# Repository agent instructions

## Tests

- DO NOT add new tests when patching existing behavior. Only update relevant tests if they already exist.
- For NEW implementations, ALWAYS ask the user whether they want tests added before creating any tests. Do not add tests without explicit approval.

## Scope and approval

- Keep autonomous work as narrowly scoped as possible. If a change involves MORE than one module, ASK for confirmation before making the change.
- If a change modifies an external, public-facing API mapped under `/api/v1/**`, WARN the user about the API impact and ASK for confirmation before making the change.
