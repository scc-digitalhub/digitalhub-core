import type { JSONSchemaNode } from "./types";

/**
 * Schemas for the underlying business entities (project / dataset / model definitions),
 * mirrored from src/compliance_agent/api/models.py (ProjectInput, DatasetInput, ModelInput).
 * These are the inputs the compliance agent reasons over to generate/extend a ComplianceSpec.
 */
export const ENTITY_SCHEMAS: Record<string, JSONSchemaNode> = {
  FieldSchemaEntry: {
    title: "FieldSchemaEntry",
    description: "Column/field definition of a dataset.",
    type: "object",
    properties: {
      name: { type: "string" },
      dtype: { type: "string" },
      nullable: { type: "boolean" },
      description: { type: "string" },
      sensitivity: { type: "string", enum: ["NONE", "PII", "SENSITIVE", "RESTRICTED", "PROHIBITED"] },
    },
    required: ["name", "dtype"],
  },

  ProjectInput: {
    title: "ProjectInput",
    description: "Project or AI application definition used to derive a ProjectComplianceSpec.",
    type: "object",
    properties: {
      name: { type: "string" },
      domain: { type: "string", description: "Application domain, e.g. healthcare, finance" },
      subdomain: { type: "string" },
      ai_task: { type: "string", description: "AI task type, e.g. CLASSIFICATION, DETECTION, GENERATION" },
      goal: { type: "string", description: "High-level goal of the AI application" },
      purpose: { type: "string", description: "Intended function or business capability" },
      audience: { type: "string", description: "Target users" },
      scope_in: { type: "array", items: { type: "string" }, description: "In-scope capabilities" },
      scope_out: { type: "array", items: { type: "string" }, description: "Out-of-scope capabilities" },
      deployment: { type: "string", enum: ["EDGE", "CLOUD", "EMBEDDED", "PREMISE"] },
      geography: { type: "array", items: { type: "string" }, description: "Deployment jurisdictions" },
      environment: { type: "string", enum: ["PRODUCTION", "SANDBOX", "PILOT"] },
    },
    required: ["name", "domain", "ai_task", "goal"],
  },

  DatasetInput: {
    title: "DatasetInput",
    description: "Dataset definition used to derive a DataComplianceSpec.",
    type: "object",
    properties: {
      name: { type: "string" },
      description: { type: "string" },
      scope: { type: "string", enum: ["TRAINING", "VALIDATION", "SUPPORT"] },
      domain: { type: "string" },
      sensitivity: { type: "string", enum: ["NONE", "PII", "SENSITIVE", "RESTRICTED", "PROHIBITED"] },
      n_records: { type: "number" },
      schema_fields: { type: "array", items: { $ref: "FieldSchemaEntry" }, description: "Column definitions" },
      known_protected_attributes: { type: "array", items: { type: "string" }, description: "Known sensitive field names" },
    },
    required: ["name", "scope"],
  },

  ModelInput: {
    title: "ModelInput",
    description: "Model definition used to derive a ModelComplianceSpec.",
    type: "object",
    properties: {
      name: { type: "string" },
      description: { type: "string" },
      ai_task: { type: "string", description: "AI task type matching the project" },
      domain: { type: "string" },
      framework: { type: "string", description: "e.g. PYTORCH, SKLEARN, HUGGINGFACE" },
      output_type: {
        type: "string",
        enum: ["BINARY_CLASSIFICATION", "MULTI_CLASS", "REGRESSION", "GENERATION", "RANKING"],
      },
      produces_probabilities: { type: "boolean" },
      trained_on_sensitive_data: { type: "boolean" },
      known_affected_groups: { type: "array", items: { type: "string" }, description: "Groups affected by model decisions" },
    },
    required: ["name", "ai_task"],
  },
};
