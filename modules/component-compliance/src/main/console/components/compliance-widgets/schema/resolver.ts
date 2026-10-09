import { v4 as uuidv4 } from "uuid";
import { COMPLIANCE_SCHEMAS } from "./registry";
import { ENTITY_SCHEMAS } from "./entitySchemas";
import type { JSONSchemaNode, ResolvedObjectSchema, SchemaRef } from "./types";
import { isSchemaRef } from "./types";

const ALL_SCHEMAS: Record<string, JSONSchemaNode> = { ...COMPLIANCE_SCHEMAS, ...ENTITY_SCHEMAS };

export function getRawSchema(name: string): JSONSchemaNode {
  const schema = ALL_SCHEMAS[name];
  if (!schema) {
    throw new Error(`Unknown schema "${name}" — is it registered in schema/registry.ts?`);
  }
  return schema;
}

/** Flattens `allOf` (single level of $ref inheritance, as used across the OKB schemas) into one property bag. */
export function resolveSchema(name: string): ResolvedObjectSchema {
  const schema = getRawSchema(name);
  let properties: Record<string, JSONSchemaNode | SchemaRef> = {};
  let required: string[] = [];

  for (const parent of schema.allOf ?? []) {
    if (isSchemaRef(parent)) {
      const resolvedParent = resolveSchema(parent.$ref);
      properties = { ...properties, ...resolvedParent.properties };
      required = [...required, ...resolvedParent.required];
    } else {
      properties = { ...properties, ...(parent.properties ?? {}) };
      required = [...required, ...(parent.required ?? [])];
    }
  }

  properties = { ...properties, ...(schema.properties ?? {}) };
  required = Array.from(new Set([...required, ...(schema.required ?? [])]));

  return { name, title: schema.title ?? name, description: schema.description, properties, required };
}

const CUSTOM_VALUE_SENTINELS = ["OTHER", "CUSTOM"];

/** Free-text "custom value" fields that don't follow the `<enumField>_value` naming convention. */
const CUSTOM_VALUE_FIELD_OVERRIDES: Record<string, string> = {
  custom_fn: "aggregation",
};

/**
 * If `key` is the free-text companion of an enum field with an "OTHER"/"CUSTOM" option (e.g.
 * `ai_task_value` next to `ai_task`, or `custom_fn` next to `aggregation`), returns the base
 * enum field's key and the sentinel value(s) that should reveal it. Returns null otherwise.
 */
export function getCustomValueTrigger(
  properties: Record<string, JSONSchemaNode | SchemaRef>,
  key: string,
): { baseKey: string; sentinels: string[] } | null {
  const baseKey = CUSTOM_VALUE_FIELD_OVERRIDES[key] ?? (key.endsWith("_value") ? key.slice(0, -"_value".length) : null);
  if (!baseKey) return null;
  const baseProp = properties[baseKey];
  if (!baseProp || isSchemaRef(baseProp) || !baseProp.enum) return null;
  const sentinels = baseProp.enum.filter((value) => CUSTOM_VALUE_SENTINELS.includes(value));
  return sentinels.length > 0 ? { baseKey, sentinels } : null;
}

/** Builds a reasonable empty/default value for a named object schema (used for "add" actions). */
export function createDefaultForSchema(name: string): Record<string, unknown> {
  const resolved = resolveSchema(name);
  const value: Record<string, unknown> = {};
  for (const key of resolved.required) {
    const prop = resolved.properties[key];
    if (!prop) continue;
    value[key] = createDefaultForProperty(prop, key);
  }
  return value;
}

function createDefaultForProperty(prop: JSONSchemaNode | SchemaRef, key: string): unknown {
  if (isSchemaRef(prop)) {
    return createDefaultForSchema(prop.$ref);
  }
  if (prop.enum && prop.enum.length > 0) {
    return prop.enum[0];
  }
  if (prop.type === "array") {
    return [];
  }
  if (prop.type === "boolean") {
    return false;
  }
  if (prop.type === "number" || prop.type === "integer") {
    return 0;
  }
  if (key === "id") {
    return uuidv4();
  }
  return "";
}

/** Convenience: default value for an inline (non-registered) object schema node, e.g. `scope` or `threshold`. */
export function createDefaultForInlineSchema(schema: JSONSchemaNode): Record<string, unknown> {
  const value: Record<string, unknown> = {};
  for (const key of schema.required ?? []) {
    const prop = schema.properties?.[key];
    if (!prop) continue;
    value[key] = createDefaultForProperty(prop, key);
  }
  return value;
}

export function humanize(key: string): string {
  return key
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function humanizeEnumValue(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export { ALL_SCHEMAS };
