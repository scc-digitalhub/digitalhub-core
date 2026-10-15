/** The three entity kinds the compliance widgets support, matching okb "appliesTo" targets. */
export type ComplianceEntityKind = "project" | "dataset" | "model";

/** Generic JSON object — entity definitions and compliance specs are treated as loosely-typed
 * records edited through the schema-driven form, mirroring how a real backend exchanges plain
 * JSON payloads validated against the OKB JSON Schemas. */
export type JsonRecord = Record<string, unknown>;

export interface EntityRecord {
  id: string;
  name: string;
  [key: string]: unknown;
}
