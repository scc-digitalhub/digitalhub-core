import type { JSONSchemaNode } from "./types";

/**
 * Registry of compliance object schemas, transcribed from `okb/schemas/*.json`.
 * `$ref` targets use the bare schema name (registry key) instead of a `X.json` filename.
 *
 * Keep this in sync with the source of truth under okb/schemas/. Only the schemas
 * reachable from ProjectComplianceSpec, DataComplianceSpec and ModelComplianceSpec are
 * included (see okb/manifest.json for the full catalogue).
 */
export const COMPLIANCE_SCHEMAS: Record<string, JSONSchemaNode> = {
  BaseComplianceObject: {
    title: "BaseComplianceObject",
    description: "Lightweight identity and descriptive base inherited by compliance model objects.",
    type: "object",
    properties: {
      id: { type: "string", description: "Locally unique identifier." },
      namespace: { type: "string", description: "Scoping context (org, project, registry)." },
      name: { type: "string" },
      description: { type: "string" },
    },
    required: ["id", "name"],
  },

  ActorRef: {
    title: "ActorRef",
    description: "Reference to a human user, service account, or automated agent that performed an action.",
    type: "object",
    properties: {
      kind: { type: "string", enum: ["HUMAN", "SERVICE", "AGENT"] },
      id: { type: "string", description: "OIDC subject, service principal, or other identity URI." },
    },
    required: ["kind", "id"],
  },

  EntityRef: {
    title: "EntityRef",
    description: "Opaque cross-entity reference key.",
    type: "object",
    properties: {
      key: { type: "string" },
    },
    required: ["key"],
  },

  RegulatoryRef: {
    title: "RegulatoryRef",
    description: "Normative reference to a regulatory framework article or section.",
    type: "object",
    properties: {
      framework: { type: "string", description: "e.g. EU_AI_ACT, NIST_AI_RMF, ISO_42001" },
      article: { type: "string", description: "e.g. Art. 9, MAP 1.1" },
      version: { type: "string" },
      uri: { type: "string", format: "uri" },
    },
    required: ["framework"],
  },

  DomainDescriptor: {
    title: "DomainDescriptor",
    description: "Domain and use-case classification.",
    type: "object",
    properties: {
      domain: { type: "string", description: "e.g. healthcare, finance, nlp" },
      subdomain: { type: "string" },
      use_case: { type: "string" },
    },
    required: ["domain"],
  },

  UnitOfMeasure: {
    title: "UnitOfMeasure",
    description: "Physical unit of measurement for a metric value.",
    type: "object",
    properties: {
      symbol: { type: "string", description: "e.g. %, ms, tokens/s" },
      name: { type: "string" },
    },
    required: ["symbol", "name"],
  },

  ValueRange: {
    title: "ValueRange",
    description: "Valid value domain for a metric.",
    type: "object",
    properties: {
      lower: { type: "number" },
      upper: { type: "number" },
      discrete: { type: "boolean" },
      categories: { type: "array", items: { type: "string" } },
    },
  },

  MetricComposition: {
    title: "MetricComposition",
    description: "Composition specification for a composite metric.",
    type: "object",
    properties: {
      components: { type: "array", items: { $ref: "Metric" }, description: "Constituent metrics (embedded)." },
      aggregation: { type: "string", enum: ["MEAN", "MIN", "MAX", "SUM", "PRODUCT", "OTHER"] },
      custom_fn: { type: "string", description: "Description for OTHER aggregation." },
    },
    required: ["components", "aggregation"],
  },

  Metric: {
    title: "Metric",
    description: "Quantitative or qualitative measure of a defined notion.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      kind: { type: "string", enum: ["PRIMITIVE", "COMPOSITE"] },
      scale: { type: "string", enum: ["NOMINAL", "ORDINAL", "INTERVAL", "RATIO", "BOOLEAN"] },
      unit: { $ref: "UnitOfMeasure" },
      range: { $ref: "ValueRange" },
      composition: { $ref: "MetricComposition" },
    },
    required: ["kind", "scale"],
  },

  ObjectiveCondition: {
    title: "ObjectiveCondition",
    description: "Envelope for compliance objective conditions. Exactly one condition field should be populated.",
    type: "object",
    properties: {
      threshold: {
        type: "object",
        properties: {
          operator: { type: "string", enum: ["GEQ", "LEQ", "GT", "LT", "EQ", "NEQ"] },
          value: { type: "number" },
        },
        required: ["operator", "value"],
      },
      range: {
        type: "object",
        properties: {
          lower: { type: "number" },
          upper: { type: "number" },
          inclusive: { type: "boolean" },
        },
        required: ["lower", "upper", "inclusive"],
      },
      categorical: {
        type: "object",
        properties: {
          allowed: { type: "array", items: { type: "string" } },
        },
        required: ["allowed"],
      },
      statistical: {
        type: "object",
        properties: {
          test: { type: "string" },
          significance: { type: "number" },
          hypothesis: { type: "string" },
        },
        required: ["test", "significance", "hypothesis"],
      },
    },
    minProperties: 1,
    maxProperties: 1,
  },

  ComplianceObjective: {
    title: "ComplianceObjective",
    description: "Normative constraint binding a Metric to a target value or region.",
    type: "object",
    properties: {
      name: { type: "string" },
      description: { type: "string" },
      metric: { $ref: "Metric" },
      condition: { $ref: "ObjectiveCondition" },
      priority: { type: "string", enum: ["MANDATORY", "RECOMMENDED", "INFORMATIONAL"] },
      severity: { type: "string", enum: ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"] },
      requirements: { type: "array", items: { type: "string" }, uniqueItems: true },
    },
    required: ["name", "metric", "condition", "priority", "severity"],
  },

  ComplianceFinding: {
    title: "ComplianceFinding",
    description: "A non-conformance finding produced during a compliance assessment.",
    type: "object",
    properties: {
      description: { type: "string" },
      objective: { type: "string", description: "Identifier of the violated ComplianceObjective." },
      report: { $ref: "EntityRef" },
    },
    required: ["description", "objective", "report"],
  },

  MitigationAction: {
    title: "MitigationAction",
    description: "Action engaged to resolve a compliance objective violation.",
    type: "object",
    properties: {
      status: {
        type: "string",
        enum: [
          "NOT_REQUIRED", "PROPOSED", "PLANNED", "IN_PROGRESS", "APPLIED",
          "VERIFIED", "REJECTED", "DEFERRED", "ACCEPTED_RISK",
        ],
      },
      rationale: { type: "string" },
      owner: { $ref: "ActorRef" },
      due_at: { type: "string", format: "date-time" },
      applied_at: { type: "string", format: "date-time" },
      evidence: { type: "array", items: { $ref: "EntityRef" } },
      verification_report: { $ref: "EntityRef" },
      residual_status: {
        type: "string",
        enum: ["OPEN", "PARTIALLY_RESOLVED", "RESOLVED", "UNRESOLVED", "ACCEPTED_RESIDUAL_RISK", "NEEDS_RETEST"],
      },
    },
    required: ["status"],
  },

  ComplianceStatus: {
    title: "ComplianceStatus",
    description: "Assessed compliance status of a project or entity at a point in time.",
    type: "object",
    properties: {
      status: { type: "string", enum: ["COMPLIANT", "NON_COMPLIANT", "PARTIALLY_COMPLIANT", "PENDING_REVIEW"] },
      assessed_at: { type: "string", format: "date-time" },
      assessor: { $ref: "ActorRef" },
      findings: { type: "array", items: { $ref: "ComplianceFinding" } },
      mitigations: { type: "array", items: { $ref: "MitigationAction" } },
    },
    required: ["status", "assessed_at", "assessor"],
  },

  DocumentSection: {
    title: "DocumentSection",
    description: "Structured section within a documentation artifact.",
    type: "object",
    properties: {
      title: { type: "string" },
      content: { type: "string", description: "Markdown body." },
      format: { type: "string", enum: ["MODELCARD", "DATACARD", "SYSTEMCARD", "OTHER"] },
      format_value: { type: "string" },
      subsections: { type: "array", items: { $ref: "DocumentSection" } },
      specification: { type: "object", description: "Structured machine-readable representation." },
      reports: { type: "array", items: { $ref: "EntityRef" } },
    },
    required: ["title"],
  },

  ComplianceDocumentation: {
    title: "ComplianceDocumentation",
    description: "Lightweight documentation artifact scoped to the compliance dimension of an entity.",
    type: "object",
    properties: {
      sections: { type: "array", items: { $ref: "DocumentSection" } },
    },
  },

  AttributeRef: {
    title: "AttributeRef",
    description: "Descriptor of a data attribute with semantic and observability metadata.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      semantic_type: {
        type: "string",
        enum: [
          "AGE", "SEX", "GENDER", "RACE_ETHNICITY", "NATIONALITY", "LANGUAGE", "RELIGION",
          "DISABILITY", "HEALTH_STATUS", "LOCATION", "SOCIOECONOMIC_STATUS", "EDUCATION",
          "EMPLOYMENT_STATUS", "OTHER",
        ],
      },
      semantic_type_value: { type: "string" },
      attribute_role: {
        type: "string",
        enum: [
          "INPUT_FEATURE", "TARGET", "PROTECTED_ATTRIBUTE", "SENSITIVE_ATTRIBUTE", "PROXY_ATTRIBUTE",
          "GROUP_ATTRIBUTE", "STRATIFICATION_ATTRIBUTE", "TIMESTAMP", "OTHER",
        ],
      },
      attribute_role_value: { type: "string" },
      proxy_for: { type: "array", items: { $ref: "AttributeRef" } },
      observability: {
        type: "string",
        enum: ["DIRECT", "SELF_REPORTED", "INFERRED", "DERIVED", "PROXY", "EXTERNAL", "UNOBSERVED", "UNKNOWN"],
      },
      use_permissions: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "MODEL_INPUT", "TRAINING", "EVALUATION", "FAIRNESS_TESTING", "ROBUSTNESS_SLICING",
            "MONITORING", "MITIGATION", "REPORTING", "EXCLUDED_FROM_MODEL", "RESTRICTED", "UNKNOWN",
          ],
        },
      },
    },
  },

  GroupSpec: {
    title: "GroupSpec",
    description: "Specification of a population group relevant to a compliance context.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      group_kind: {
        type: "string",
        enum: [
          "DEMOGRAPHIC", "PROTECTED", "VULNERABLE", "OCCUPATIONAL", "USER_GROUP", "GEOGRAPHIC",
          "LANGUAGE_GROUP", "SOCIOECONOMIC", "TECHNICAL_ENVIRONMENT", "OTHER",
        ],
      },
      group_kind_value: { type: "string" },
      group_role: {
        type: "string",
        enum: [
          "TARGET_SUBJECT", "DECISION_SUBJECT", "END_USER", "OPERATOR", "BENEFICIARY",
          "EXCLUDED_POPULATION", "REFERENCE_GROUP", "OTHER",
        ],
      },
      group_role_value: { type: "string" },
      defining_attributes: { type: "array", items: { $ref: "AttributeRef" } },
      vulnerability: {
        type: "string",
        enum: [
          "AGE", "DISABILITY", "HEALTH_STATUS", "ECONOMIC_DEPENDENCY", "EDUCATIONAL_ACCESS",
          "DIGITAL_LITERACY", "EMPLOYMENT_DEPENDENCY", "MINORITY_STATUS", "LANGUAGE_ACCESS", "OTHER",
        ],
      },
      vulnerability_value: { type: "string" },
      rationale: { type: "string" },
    },
  },

  MisuseScenario: {
    title: "MisuseScenario",
    description: "Description of a foreseeable misuse scenario for an AI system.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      misuse_kind: {
        type: "string",
        enum: [
          "OUT_OF_SCOPE_USE", "OVERRELIANCE", "AUTOMATION_BIAS", "HUMAN_OVERSIGHT_BYPASS",
          "DECISION_AUTOMATION_BEYOND_PURPOSE", "UNAUTHORIZED_ACCESS", "DATA_LEAKAGE", "DATA_POISONING",
          "MODEL_POISONING", "ADVERSARIAL_INPUT", "PROMPT_INJECTION", "MODEL_EXTRACTION",
          "MONITORING_EVASION", "FEEDBACK_LOOP_AMPLIFICATION", "UNSAFE_FALLBACK_USE",
          "MISINTERPRETATION_OF_OUTPUT", "DEPLOYMENT_IN_UNVALIDATED_CONTEXT", "OTHER",
        ],
      },
      misuse_kind_value: { type: "string" },
      intentionality: { type: "string", enum: ["ACCIDENTAL", "NEGLIGENT", "INTENTIONAL", "ADVERSARIAL", "UNKNOWN"] },
      likelihood: { type: "string", enum: ["RARE", "UNLIKELY", "POSSIBLE", "LIKELY", "ALMOST_CERTAIN", "UNKNOWN"] },
      impact: { type: "string", enum: ["NEGLIGIBLE", "LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN"] },
      actor_roles: { type: "array", items: { type: "string" } },
      affected_groups: { type: "array", items: { $ref: "GroupSpec" } },
      rationale: { type: "string" },
    },
    required: ["misuse_kind"],
  },

  HumanOversightProfile: {
    title: "HumanOversightProfile",
    description: "Profile describing the human oversight configuration for an AI deployment.",
    type: "object",
    properties: {
      oversight_mode: {
        type: "string",
        enum: [
          "HUMAN_IN_THE_LOOP", "HUMAN_IN_COMMAND", "PRE_DEPLOYMENT_APPROVAL", "POST_HOC_REVIEW",
          "EXCEPTION_BASED_REVIEW", "FULLY_AUTOMATED_WITH_ESCALATION", "MANUAL_OPERATION", "NONE", "OTHER",
        ],
      },
      oversight_mode_value: { type: "string" },
      overseer_roles: { type: "array", items: { type: "string" } },
      intervention_points: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "DATA_APPROVAL", "TRAINING_APPROVAL", "TEST_APPROVAL", "THRESHOLD_APPROVAL", "DEPLOYMENT_APPROVAL",
            "INPUT_REVIEW", "OUTPUT_REVIEW", "DECISION_APPROVAL", "ABSTENTION_REVIEW", "ESCALATION_REVIEW",
            "INCIDENT_REVIEW", "OTHER",
          ],
        },
      },
      authority: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "VIEW_ONLY", "REQUEST_EXPLANATION", "APPROVE", "REJECT", "OVERRIDE_OUTPUT",
            "ESCALATE", "SUSPEND_SERVICE", "RETIRE_MODEL", "OTHER",
          ],
        },
      },
      availability: {
        type: "string",
        enum: ["CONTINUOUS", "BUSINESS_HOURS", "ON_CALL", "BATCH_REVIEW", "PERIODIC", "NONE"],
      },
      escalation_channels: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: ["HUMAN_REVIEW", "COMPLIANCE_REVIEW", "TECHNICAL_REVIEW", "LEGAL_REVIEW", "MANAGEMENT_APPROVAL", "OTHER"],
        },
      },
      limitations: { type: "array", items: { type: "string" } },
      rationale: { type: "string" },
    },
    required: ["oversight_mode"],
  },

  RiskClassification: {
    title: "RiskClassification",
    description: "Risk classification of an AI system within a compliance context.",
    type: "object",
    properties: {
      risk_level: { type: "string", enum: ["PROHIBITED", "HIGH", "LIMITED", "MINIMAL", "UNKNOWN", "OTHER"] },
      risk_level_value: { type: "string" },
      classification_status: {
        type: "string",
        enum: ["PRELIMINARY", "ASSESSED", "APPROVED", "NEEDS_REVIEW", "SUPERSEDED"],
      },
      rationale: { type: "string" },
      assessor: { $ref: "ActorRef" },
      approver: { $ref: "ActorRef" },
      assessed_at: { type: "string", format: "date-time" },
      valid_until: { type: "string", format: "date-time" },
      review_cadence: { type: "string", description: "ISO-8601 duration, e.g. P1Y" },
    },
    required: ["risk_level", "classification_status"],
  },

  Requirement: {
    title: "Requirement",
    description: "A normative compliance requirement derived from a regulatory framework.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      source: { $ref: "RegulatoryRef" },
      requirement_text: { type: "string" },
      summary: { type: "string" },
      kind: {
        type: "string",
        enum: [
          "DESIGN", "TESTING", "DOCUMENTATION", "MONITORING", "DATA_PROTECTION", "TRANSPARENCY",
          "HUMAN_OVERSIGHT", "DATA_GOVERNANCE", "RISK_MANAGEMENT", "CYBERSECURITY",
          "QUALITY_MANAGEMENT", "OPERATIONAL_CONTROL", "OTHER",
        ],
      },
      applicability_status: {
        type: "string",
        enum: ["APPLICABLE", "NOT_APPLICABLE", "PARTIALLY_APPLICABLE", "UNCERTAIN", "NEEDS_REVIEW"],
      },
      related_requirements: { type: "array", items: { type: "string" } },
    },
    required: ["source", "requirement_text", "kind", "applicability_status"],
  },

  ComplianceContext: {
    title: "ComplianceContext",
    description: "Contextualizes an AI project's usage with respect to intended purpose and applicable compliance requirements.",
    type: "object",
    properties: {
      environment: { type: "string", enum: ["PRODUCTION", "SANDBOX", "PILOT"] },
      regulations: { type: "array", items: { $ref: "RegulatoryRef" } },
      geography: { type: "array", items: { type: "string" }, description: "Jurisdictions of deployment." },
      actor_role: { type: "string", description: "Role of the organisation/party in this context." },
      actor_role_value: { type: "string" },
      affected_groups: { type: "array", items: { $ref: "GroupSpec" } },
      vulnerable_groups: { type: "array", items: { $ref: "GroupSpec" } },
      protected_attributes: { type: "array", items: { $ref: "AttributeRef" } },
      foreseeable_misuse: { type: "array", items: { $ref: "MisuseScenario" } },
      human_oversight: { $ref: "HumanOversightProfile" },
      risk_classification: { $ref: "RiskClassification" },
      requirements: { type: "array", items: { $ref: "Requirement" } },
    },
    required: ["environment"],
  },

  ComplianceSpec: {
    title: "ComplianceSpec",
    description: "Top-level compliance extension spec attached to any first-class entity (data, model, model service, project).",
    type: "object",
    properties: {
      objectives: { type: "array", items: { $ref: "ComplianceObjective" }, uniqueItems: true },
      compliance_status: { $ref: "ComplianceStatus" },
      documentation: { $ref: "ComplianceDocumentation" },
    },
  },

  RetentionPolicy: {
    title: "RetentionPolicy",
    description: "Data retention policy defining how long and why data is kept.",
    type: "object",
    properties: {
      applies_to: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "RAW_DATA", "TRAINING_DATA", "VALIDATION_DATA", "TEST_DATA", "SYNTHETIC_DATA",
            "AUGMENTED_DATA", "TEST_REPORTS", "DOCUMENTATION", "OTHER",
          ],
        },
      },
      retention_basis: {
        type: "string",
        enum: ["PURPOSE_NECESSITY", "LEGAL_OBLIGATION", "CONSENT_DEPENDENT", "SECURITY_MONITORING", "OTHER"],
      },
      retention_basis_value: { type: "string" },
      purposes: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "TRAINING", "VALIDATION", "TESTING", "MONITORING", "BIAS_DETECTION", "BIAS_CORRECTION",
            "ROBUSTNESS_EVALUATION", "LEGAL_COMPLIANCE", "RESEARCH", "ARCHIVING", "OTHER",
          ],
        },
      },
      legal_references: { type: "array", items: { $ref: "RegulatoryRef" } },
      justification: { type: "string" },
      owner: { $ref: "ActorRef" },
      approver: { $ref: "ActorRef" },
      valid_from: { type: "string", format: "date-time" },
      valid_until: { type: "string", format: "date-time" },
    },
  },

  ConsentSpec: {
    title: "ConsentSpec",
    description: "Specification of data subject consent requirements and conditions.",
    type: "object",
    properties: {
      consent_required: { type: "boolean" },
      consent_basis: {
        type: "string",
        enum: ["GDPR_ARTICLE_6_1_A", "GDPR_ARTICLE_9_2_A", "AI_ACT_REAL_WORLD_TESTING", "OTHER"],
      },
      consent_basis_value: { type: "string" },
      applies_to: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "COLLECTION", "STORAGE", "TRAINING", "VALIDATION", "TESTING", "PROFILING",
            "AUTOMATED_DECISION_SUPPORT", "BIAS_DETECTION", "BIAS_CORRECTION",
            "ROBUSTNESS_EVALUATION", "DATA_SHARING", "OTHER",
          ],
        },
      },
      data_categories: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "PERSONAL", "SENSITIVE", "ANONYMIZED", "PSEUDONYMIZED", "SYNTHETIC",
            "PUBLICLY_AVAILABLE", "PROPRIETARY", "OTHER",
          ],
        },
      },
      purposes: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: [
            "TRAINING", "VALIDATION", "TESTING", "MONITORING", "BIAS_DETECTION", "BIAS_CORRECTION",
            "ROBUSTNESS_EVALUATION", "LEGAL_COMPLIANCE", "RESEARCH", "ARCHIVING", "OTHER",
          ],
        },
      },
      freely_given: { type: "boolean" },
      specific: { type: "boolean" },
      informed: { type: "boolean" },
      unambiguous: { type: "boolean" },
      explicit: { type: "boolean" },
    },
    required: ["consent_required"],
  },

  DataGovernance: {
    title: "DataGovernance",
    description: "Governance metadata for a dataset.",
    type: "object",
    properties: {
      sensitivity: { type: "string", enum: ["NONE", "PII", "SENSITIVE", "RESTRICTED", "PROHIBITED"] },
      license: { type: "string", description: "SPDX license expression." },
      retention: { $ref: "RetentionPolicy" },
      consent: { $ref: "ConsentSpec" },
    },
    required: ["sensitivity"],
  },

  // ── Top-level compliance specs ────────────────────────────────────────────

  ProjectComplianceSpec: {
    title: "ProjectComplianceSpec",
    description: "Compliance extension for a project entity (kind: ai-compliance, appliesTo: project).",
    type: "object",
    allOf: [{ $ref: "ComplianceSpec" }],
    properties: {
      domain: { $ref: "DomainDescriptor" },
      ai_task: {
        type: "string",
        enum: [
          "CLASSIFICATION", "REGRESSION", "RANKING", "RECOMMENDATION", "GENERATION",
          "DETECTION", "FORECASTING", "CONTROL", "DECISION_SUPPORT", "OTHER",
        ],
      },
      ai_task_value: { type: "string" },
      goal: { type: "string" },
      purpose: { type: "string" },
      audience: { type: "string" },
      scope: {
        type: "object",
        properties: {
          in_scope: { type: "array", items: { type: "string" } },
          out_of_scope: { type: "array", items: { type: "string" } },
        },
      },
      deployment: { type: "string", enum: ["EDGE", "PREMISE", "CLOUD", "EMBEDDED"] },
      context: { $ref: "ComplianceContext" },
    },
    required: ["domain", "ai_task", "goal"],
  },

  ModelComplianceSpec: {
    title: "ModelComplianceSpec",
    description: "Compliance extension for a model entity (kind: model-compliance, appliesTo: model). Inherits all ComplianceSpec fields.",
    type: "object",
    allOf: [{ $ref: "ComplianceSpec" }],
    properties: {},
  },

  DataComplianceSpec: {
    title: "DataComplianceSpec",
    description: "Compliance extension for a data item or artifact entity (kind: data-compliance, appliesTo: dataitem, artifact).",
    type: "object",
    allOf: [{ $ref: "ComplianceSpec" }],
    properties: {
      scope: { type: "string", enum: ["TRAINING", "VALIDATION", "SUPPORT"] },
      governance: { $ref: "DataGovernance" },
      attributes: { type: "array", items: { $ref: "AttributeRef" }, uniqueItems: true },
    },
  },
};
