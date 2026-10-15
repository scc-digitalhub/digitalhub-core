import { resolveSchema } from "./resolver";
import { isSchemaRef, type JSONSchemaNode, type SchemaRef } from "./types";
import type { JsonRecord } from "../types";

type LabelFor = (key: string) => string;

function isEmpty(value: unknown): boolean {
  if (value === undefined || value === null) return true;
  if (typeof value === "string") return value.trim() === "";
  if (Array.isArray(value)) return value.length === 0;
  return false;
}

function isPlainObject(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function walk(
  prop: JSONSchemaNode | SchemaRef,
  value: unknown,
  pathLabels: string[],
  errors: string[],
  label: LabelFor,
): void {
  if (isSchemaRef(prop)) {
    if (isPlainObject(value)) {
      const resolved = resolveSchema(prop.$ref);
      validateProps(resolved.properties, resolved.required, value, pathLabels, errors, label);
    }
    return;
  }
  if (prop.type === "array" && prop.items) {
    if (Array.isArray(value)) {
      value.forEach((item, index) => {
        if (isPlainObject(item)) {
          walk(prop.items!, item, [...pathLabels, `#${index + 1}`], errors, label);
        }
      });
    }
    return;
  }
  if (prop.type === "object" && prop.properties && isPlainObject(value)) {
    validateProps(prop.properties, prop.required ?? [], value, pathLabels, errors, label);
  }
}

function validateProps(
  properties: Record<string, JSONSchemaNode | SchemaRef>,
  required: string[],
  obj: JsonRecord,
  pathLabels: string[],
  errors: string[],
  label: LabelFor,
): void {
  for (const key of required) {
    if (isEmpty(obj[key])) {
      errors.push([...pathLabels, label(key)].join(" › "));
    }
  }
  for (const [key, prop] of Object.entries(properties)) {
    const value = obj[key];
    if (isEmpty(value)) continue;
    walk(prop, value, [...pathLabels, label(key)], errors, label);
  }
}

/** Collects human-readable paths of every required field left empty in `spec` (recursively). */
export function validateSpec(schemaName: string, spec: JsonRecord, label: LabelFor): string[] {
  const resolved = resolveSchema(schemaName);
  const errors: string[] = [];
  validateProps(resolved.properties, resolved.required, spec, [], errors, label);
  return errors;
}
