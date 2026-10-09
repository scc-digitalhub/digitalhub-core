/** Generic recursive object type used by the compliance-spec merge helpers. */
export type AnyRecord = Record<string, unknown>;

function isPlainObject(value: unknown): value is AnyRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function identityOf(item: unknown): string {
  if (isPlainObject(item)) {
    if (typeof item.id === "string") return `id:${item.id}`;
    if (typeof item.name === "string") return `name:${item.name}`;
  }
  return JSON.stringify(item);
}

/**
 * Merges `patch` into `base` WITHOUT overwriting any value the user has already filled in:
 * - empty/undefined/null scalar fields in `base` are filled from `patch`
 * - arrays are concatenated, de-duplicated by id/name (or full value equality for primitives)
 * - nested objects are merged recursively
 * Used for the "Extend with AI" action, so agent suggestions augment rather than clobber edits.
 */
export function mergeFillGaps(base: AnyRecord, patch: AnyRecord): AnyRecord {
  const result: AnyRecord = { ...base };
  for (const key of Object.keys(patch)) {
    const baseValue = result[key];
    const patchValue = patch[key];
    if (Array.isArray(patchValue)) {
      const baseArray = Array.isArray(baseValue) ? baseValue : [];
      const seen = new Set(baseArray.map(identityOf));
      const merged = [...baseArray];
      for (const item of patchValue) {
        const id = identityOf(item);
        if (!seen.has(id)) {
          seen.add(id);
          merged.push(item);
        }
      }
      result[key] = merged;
    } else if (isPlainObject(patchValue)) {
      result[key] = isPlainObject(baseValue) ? mergeFillGaps(baseValue, patchValue) : patchValue;
    } else if (baseValue === undefined || baseValue === null || baseValue === "") {
      result[key] = patchValue;
    }
  }
  return result;
}
