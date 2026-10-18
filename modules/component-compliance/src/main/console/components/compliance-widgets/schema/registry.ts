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
      id: { type: "string", description: "compliance.explanations.id", columns: 3 },
      namespace: { type: "string", description: "compliance.explanations.namespace", columns: 3 },
      name: { type: "string", columns: 6 },
      description: { type: "string", columns: 12 },
    },
    required: ["id", "name"],
  },

  ActorRef: {
    title: "ActorRef",
    description: "Reference to a human user, service account, or automated agent that performed an action.",
    type: "object",
    properties: {
      kind: { type: "string", enum: ["HUMAN", "SERVICE", "AGENT"], columns: 4 },
      id: { type: "string", description: "compliance.explanations.actorId", columns: 8 },
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
    getTitle: (item) => [item.framework, item.article].filter(Boolean).join(", "),
    properties: {
      framework: { type: "string", description: "compliance.explanations.regulatoryFramework", columns: 4 },
      article: { type: "string", description: "compliance.explanations.regulatoryArticle", columns: 4 },
      version: { type: "string", columns: 4 },
      uri: { type: "string", format: "uri" },
    },
    required: ["framework"],
  },

  DomainDescriptor: {
    title: "DomainDescriptor",
    description: "Domain and use-case classification.",
    type: "object",
    properties: {
      domain: { type: "string", description: "compliance.explanations.domain" , columns: 6 },
      subdomain: { type: "string" , columns: 6 },
      use_case: { type: "string" },
    },
    required: ["domain"],
  },

  UnitOfMeasure: {
    title: "UnitOfMeasure",
    description: "Physical unit of measurement for a metric value.",
    type: "object",
    properties: {
      symbol: { type: "string", description: "compliance.explanations.unitSymbol" },
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
      components: { type: "array", items: { $ref: "Metric" }, description: "compliance.explanations.metricComponents" },
      aggregation: { type: "string", enum: ["MEAN", "MIN", "MAX", "SUM", "PRODUCT", "OTHER"] },
      custom_fn: { type: "string", description: "compliance.explanations.customAggregation" },
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
      requirements: { type: "array", items: { type: "string" }, uniqueItems: true, widget: "requirements" },
      priority: { type: "string", enum: ["MANDATORY", "RECOMMENDED", "INFORMATIONAL"] },
      severity: { type: "string", enum: ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"] },
      metric: { $ref: "Metric" },
      condition: { $ref: "ObjectiveCondition", widget: "objective-condition" },
    },
    required: ["name", "metric", "condition", "priority", "severity"],
  },

  ComplianceFinding: {
    title: "ComplianceFinding",
    description: "A non-conformance finding produced during a compliance assessment.",
    type: "object",
    properties: {
      description: { type: "string" },
      objective: { type: "string", description: "compliance.explanations.findingObjective" },
      report: { $ref: "EntityRef", widget: "entity-reference", entityResources: ["extensions"], entityKind: "compliance-report" },
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
        description: "compliance.explanations.mitigationStatus",
        enum: [
          "NOT_REQUIRED", "PROPOSED", "PLANNED", "IN_PROGRESS", "APPLIED",
          "VERIFIED", "REJECTED", "DEFERRED", "ACCEPTED_RISK",
        ],
      },
      rationale: { type: "string" },
      owner: { $ref: "ActorRef"  },
      due_at: { type: "string", format: "date-time", columns: 6 },
      applied_at: { type: "string", format: "date-time", columns: 6 },
      evidence: { type: "array", items: { $ref: "EntityRef" }, widget: "entity-reference", entityResources: ["extensions"], entityKind: "compliance-report" },
      verification_report: { $ref: "EntityRef", widget: "entity-reference", entityResources: ["extensions"], entityKind: "compliance-report" },
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
      status: {
        type: "string",
        enum: ["COMPLIANT", "NON_COMPLIANT", "PARTIALLY_COMPLIANT", "PENDING_REVIEW"],
        default: "PENDING_REVIEW",
        enumColors: {
          COMPLIANT: "success",
          NON_COMPLIANT: "error",
          PARTIALLY_COMPLIANT: "warning",
          PENDING_REVIEW: "default",
        },
        columns: 6,
      },
      assessed_at: { type: "string", format: "date-time", description: "compliance.explanations.assessedAt", columns: 6 },
      assessor: { $ref: "ActorRef", description: "compliance.explanations.assessor" },
    },
    required: ["status", "assessed_at", "assessor"],
  },

  DocumentSection: {
    title: "DocumentSection",
    description: "Structured section within a documentation artifact.",
    type: "object",
    properties: {
      title: { type: "string" },
      content: { type: "string", description: "compliance.explanations.documentContent" },
      format: { type: "string", enum: ["MODELCARD", "DATACARD", "SYSTEMCARD", "OTHER"] },
      format_value: { type: "string" },
      subsections: { type: "array", items: { $ref: "DocumentSection" } },
      specification: { type: "object", description: "compliance.explanations.documentSpecification" },
      reports: { type: "array", items: { $ref: "EntityRef" }, widget: "entity-reference", entityResources: ["extensions"], entityKind: "compliance-report" },
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
        columns: 6,
      },
      semantic_type_value: { type: "string", columns: 6 },
      attribute_role: {
        type: "string",
        enum: [
          "INPUT_FEATURE", "TARGET", "PROTECTED_ATTRIBUTE", "SENSITIVE_ATTRIBUTE", "PROXY_ATTRIBUTE",
          "GROUP_ATTRIBUTE", "STRATIFICATION_ATTRIBUTE", "TIMESTAMP", "OTHER",
        ],
        columns: 6,
      },
      attribute_role_value: { type: "string", columns: 6 },
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
        columns: 6,
      },
      group_kind_value: { type: "string", columns: 6 },
      group_role: {
        type: "string",
        enum: [
          "TARGET_SUBJECT", "DECISION_SUBJECT", "END_USER", "OPERATOR", "BENEFICIARY",
          "EXCLUDED_POPULATION", "REFERENCE_GROUP", "OTHER",
        ],
        columns: 6,
      },
      group_role_value: { type: "string", columns: 6 },
      defining_attributes: { type: "array", items: { $ref: "AttributeRef" } },
      vulnerability: {
        type: "string",
        enum: [
          "AGE", "DISABILITY", "HEALTH_STATUS", "ECONOMIC_DEPENDENCY", "EDUCATIONAL_ACCESS",
          "DIGITAL_LITERACY", "EMPLOYMENT_DEPENDENCY", "MINORITY_STATUS", "LANGUAGE_ACCESS", "OTHER",
        ],
        columns: 6,
      },
      vulnerability_value: { type: "string", columns: 6 },
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
        columns: 6,
      },
      oversight_mode_value: { type: "string", columns: 6 },
      overseer_roles: { type: "array", items: { type: "string" }, columns: 6 },
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
        columns: 6,
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
        columns: 6,
      },
      availability: {
        type: "string",
        enum: ["CONTINUOUS", "BUSINESS_HOURS", "ON_CALL", "BATCH_REVIEW", "PERIODIC", "NONE"],
        columns: 6,
      },
      escalation_channels: {
        type: "array",
        uniqueItems: true,
        items: {
          type: "string",
          enum: ["HUMAN_REVIEW", "COMPLIANCE_REVIEW", "TECHNICAL_REVIEW", "LEGAL_REVIEW", "MANAGEMENT_APPROVAL", "OTHER"],
        },
        columns: 6,
      },
      limitations: { type: "array", items: { type: "string" }, columns: 6 },
      rationale: { type: "string" },
    },
    required: ["oversight_mode"],
  },

  RiskClassification: {
    title: "RiskClassification",
    description: "Risk classification of an AI system within a compliance context.",
    type: "object",
    properties: {
      risk_level: { type: "string", enum: ["PROHIBITED", "HIGH", "LIMITED", "MINIMAL", "UNKNOWN", "OTHER"], columns: 4 },
      risk_level_value: { type: "string", columns: 4 },
      classification_status: {
        type: "string",
        enum: ["PRELIMINARY", "ASSESSED", "APPROVED", "NEEDS_REVIEW", "SUPERSEDED"],
        columns: 4,
      },
      rationale: { type: "string" },
      assessor: { $ref: "ActorRef", columns: 6 },
      approver: { $ref: "ActorRef", columns: 6 },
      assessed_at: { type: "string", format: "date-time", columns: 4 },
      valid_until: { type: "string", format: "date-time", columns: 4 },
      review_cadence: { type: "string", description: "compliance.explanations.reviewCadence", columns: 4 },
    },
    required: ["risk_level", "classification_status"],
  },

  Requirement: {
    title: "Requirement",
    description: "A normative compliance requirement derived from a regulatory framework.",
    type: "object",
    allOf: [{ $ref: "BaseComplianceObject" }],
    properties: {
      source: { $ref: "RegulatoryRef", widget: "regulatory-reference" },
      requirement_text: { type: "string" },
      kind: {
        type: "string",
        enum: [
          "DESIGN", "TESTING", "DOCUMENTATION", "MONITORING", "DATA_PROTECTION", "TRANSPARENCY",
          "HUMAN_OVERSIGHT", "DATA_GOVERNANCE", "RISK_MANAGEMENT", "CYBERSECURITY",
          "QUALITY_MANAGEMENT", "OPERATIONAL_CONTROL", "OTHER",
        ],
      },
      compliance_status: { $ref: "ComplianceStatus" },
      findings: { type: "array", items: { $ref: "ComplianceFinding" } },
      mitigations: { type: "array", items: { $ref: "MitigationAction" } },
      related_requirements: { type: "array", items: { type: "string" } },
    },
    required: ["source", "requirement_text", "kind", "compliance_status"],
  },

  ComplianceContext: {
    title: "ComplianceContext",
    description: "Contextualizes an AI project's usage with respect to intended purpose and applicable compliance requirements.",
    type: "object",
    properties: {
      environment: { type: "string", enum: ["PRODUCTION", "SANDBOX", "PILOT"], columns: 6 },
      regulations: { type: "array", items: { $ref: "RegulatoryRef" } },
      geography: { type: "array", items: { type: "string" }, description: "compliance.explanations.geography", columns: 6 },
      actor_role: { type: "string", description: "compliance.explanations.actorRole", enum: ["PROVIDER",
        "DEPLOYER",
        "DISTRIBUTOR",
        "AUTHORIZED_REPRESENTATIVE",
        "PRODUCT_MANUFACTURER",
        "MODEL_DEVELOPER",
        "SYSTEM_INTEGRATOR",
        "SERVICE_OPERATOR",
        "DATA_PROVIDER",
        "DATA_CONTROLLER",
        "DATA_PROCESSOR",
        "HUMAN_OVERSIGHT_OPERATOR",
        "END_USER",
        "AFFECTED_PERSON",
        "COMPLIANCE_OWNER", "OTHER"] },
      actor_role_value: { type: "string"},
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
      objectives: { type: "array", items: { $ref: "ComplianceObjective" }, uniqueItems: true, description: "compliance.explanations.objectives" },
      compliance_status: { $ref: "ComplianceStatus", description: "compliance.explanations.complianceStatus" },
      documentation: { $ref: "ComplianceDocumentation", description: "compliance.explanations.documentation" },
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
      sensitivity: { type: "string", enum: ["NONE", "PII", "SENSITIVE", "RESTRICTED", "PROHIBITED"], description: "compliance.explanations.sensitivity" },
      license: {
        type: "string",
        description: "compliance.explanations.license",
        explanation: "compliance.tooltips.license",
      },
      retention: { $ref: "RetentionPolicy", description: "compliance.explanations.retention" },
      consent: { $ref: "ConsentSpec", description: "compliance.explanations.consent" },
    },
    required: ["sensitivity"],
  },

  ConfidenceInterval: {
    title: "ConfidenceInterval",
    type: "object",
    properties: {
      lower: { type: "number", columns: 4 },
      upper: { type: "number", columns: 4 },
      level: { type: "number", description: "compliance.explanations.confidenceLevel", columns: 4 },
    },
  },

  MetricResult: {
    title: "MetricResult",
    type: "object",
    properties: {
      metric: { $ref: "Metric", description: "compliance.explanations.metric" },
      value: { type: "object", description: "compliance.explanations.metricValue" },
      confidence: { $ref: "ConfidenceInterval", description: "compliance.explanations.confidence" },
      computed_at: { type: "string", format: "date-time", description: "compliance.explanations.computedAt" },
    },
  },

  TestResult: {
    title: "TestResult",
    type: "object",
    properties: {
      measured: { type: "string", description: "compliance.explanations.measured" },
      expected: { $ref: "ObjectiveCondition", widget: "objective-condition", description: "compliance.explanations.expected" },
      passed: { type: "boolean", description: "compliance.explanations.passed" },
      details: { type: "string", description: "compliance.explanations.testDetails" },
    },
  },

  EvaluationContent: {
    title: "EvaluationContent",
    type: "object",
    properties: {
      metrics: { type: "array", items: { $ref: "MetricResult" }, description: "compliance.explanations.metrics" },
      summary: { type: "string", description: "compliance.explanations.evaluationSummary" },
    },
  },

  TestContent: {
    title: "TestContent",
    type: "object",
    properties: {
      results: { type: "array", items: { $ref: "TestResult" }, description: "compliance.explanations.testResults" },
      verdict: { type: "string", enum: ["PASS", "FAIL", "INCONCLUSIVE", "ERROR"], description: "compliance.explanations.verdict" },
      summary: { type: "string", description: "compliance.explanations.testSummary" },
    },
  },

  TimeWindow: {
    title: "TimeWindow",
    type: "object",
    properties: {
      from: { type: "string", format: "date-time", columns: 6 },
      to: { type: "string", format: "date-time", columns: 6 },
    },
  },

  LogContent: {
    title: "LogContent",
    type: "object",
    properties: {
      kind: { type: "string", enum: ["INPUT_OUTPUT", "RESOURCE", "EVENT", "AUDIT"] },
      window: { $ref: "TimeWindow" },
    },
  },

  ReportContent: {
    title: "ReportContent",
    type: "object",
    properties: {
      log: { $ref: "LogContent" },
      evaluation: { $ref: "EvaluationContent" },
      test: { $ref: "TestContent" },
    },
  },

  Lineage: {
    title: "Lineage",
    type: "object",
    properties: {
      sources: { type: "array", items: { $ref: "EntityRef" }, widget: "entity-reference", entityResources: ["dataitems", "artifacts", "models"], description: "compliance.explanations.lineageSources" },
      operation: { $ref: "EntityRef", widget: "entity-reference", entityResources: ["runs"], description: "compliance.explanations.lineageOperation" },
    },
  },

  ReportSpec: {
    title: "ReportSpec",
    description: "Compliance evaluation, test, or log report attached to a data or model entity.",
    type: "object",
    getTitle: (item) => String(item.name ?? item.kind ?? "Report"),
    properties: {
      name: { type: "string", description: "compliance.explanations.reportName" },
      kind: { type: "string", enum: ["EVALUATION", "TEST", "LOG"], description: "compliance.explanations.reportKind" },
      content: { $ref: "ReportContent", widget: "report-content", description: "compliance.explanations.reportContent" },
      objectives: { type: "array", items: { type: "string" }, uniqueItems: true, widget: "project-objectives", description: "compliance.explanations.reportObjectives" },
      path: {
        type: "string",
        description: "compliance.explanations.reportPath",
        explanation: "compliance.tooltips.reportPath",
      },
      lineage: { $ref: "Lineage", description: "compliance.explanations.reportLineage" },
    },
    required: ["name", "kind", "content"],
  },

  // ── Top-level compliance specs ────────────────────────────────────────────

  ProjectComplianceSpec: {
    title: "ProjectComplianceSpec",
    description: "Compliance extension for a project entity (kind: ai-compliance, appliesTo: project).",
    type: "object",
    allOf: [{ $ref: "ComplianceSpec" }],
    properties: {
      domain: { $ref: "DomainDescriptor", description: "compliance.explanations.projectDomain" },
      ai_task: {
        type: "string",
        description: "compliance.explanations.aiTask",
        explanation: "compliance.tooltips.aiTask",
        enum: [
          "CLASSIFICATION", "REGRESSION", "RANKING", "RECOMMENDATION", "GENERATION",
          "DETECTION", "FORECASTING", "CONTROL", "DECISION_SUPPORT", "OTHER",
        ],
      },
      ai_task_value: { type: "string", description: "compliance.explanations.aiTaskValue" },
      goal: { type: "string", description: "compliance.explanations.goal" },
      purpose: { type: "string", description: "compliance.explanations.purpose" },
      audience: { type: "string", description: "compliance.explanations.audience" },
      scope: {
        type: "object",
        description: "compliance.explanations.projectScope",
        properties: {
          in_scope: {
            type: "array",
            items: { type: "string" },
            description: "compliance.explanations.inScope",
            explanation: "compliance.tooltips.inScope",
          },
          out_of_scope: {
            type: "array",
            items: { type: "string" },
            description: "compliance.explanations.outOfScope",
            explanation: "compliance.tooltips.outOfScope",
          },
        },
      },
      deployment: { type: "string", enum: ["EDGE", "PREMISE", "CLOUD", "EMBEDDED"], description: "compliance.explanations.deployment" },
      context: { $ref: "ComplianceContext", description: "compliance.explanations.complianceContext" },
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
      scope: { type: "string", enum: ["TRAINING", "VALIDATION", "SUPPORT"], description: "compliance.explanations.dataScope" },
      governance: { $ref: "DataGovernance", description: "compliance.explanations.dataGovernance" },
      attributes: { type: "array", items: { $ref: "AttributeRef" }, uniqueItems: true, description: "compliance.explanations.dataAttributes" },
    },
  },
};
