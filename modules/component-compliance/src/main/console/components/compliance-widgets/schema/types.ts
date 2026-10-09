/**
 * Minimal JSON-Schema-like typing used to drive the generic form renderer.
 * Mirrors (a practical subset of) the JSON Schema 2020-12 dialect used under okb/schemas,
 * with `$ref` values pointing at other keys of the schema registry (see registry.ts)
 * instead of file paths.
 */

export type PrimitiveType = "string" | "number" | "integer" | "boolean" | "object" | "array";

export interface SchemaRef {
  $ref: string;
}

export interface JSONSchemaNode {
  title?: string;
  description?: string;
  type?: PrimitiveType;
  format?: "date-time" | "uri" | string;
  enum?: string[];
  properties?: Record<string, JSONSchemaNode | SchemaRef>;
  items?: JSONSchemaNode | SchemaRef;
  required?: string[];
  allOf?: Array<JSONSchemaNode | SchemaRef>;
  uniqueItems?: boolean;
  minProperties?: number;
  maxProperties?: number;
}

export function isSchemaRef(node: JSONSchemaNode | SchemaRef | undefined): node is SchemaRef {
  return !!node && typeof node === "object" && "$ref" in node;
}

/** A fully merged (allOf-flattened) object schema, ready to render. */
export interface ResolvedObjectSchema {
  name: string;
  title: string;
  description?: string;
  properties: Record<string, JSONSchemaNode | SchemaRef>;
  required: string[];
}
