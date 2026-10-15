import { v4 as uuidv4 } from "uuid";
import type { ComplianceEntityKind, JsonRecord } from "../compliance-widgets/types";
import type { AgentResult, AgentService } from "../compliance-widgets/services/types";
import { fetchUtils } from "react-admin";

const mockData = {
  project:{
          "narrative_summary": "The AI-assisted CV screening system for software engineer hiring operates within the EU, adhering to high-risk AI regulations under the EU AI Act and GDPR.",
          "regulatory_analysis": "The system complies with EU AI Act Article 10 and GDPR Article 22, ensuring data governance and human review.",
          "risk_analysis": "The system is classified as high-risk due to potential biases in decision-making processes.",
          "oversight_analysis": "Human oversight is implemented through recruiter review and intervention mechanisms.",
          "compliance_context": {
              "environment": "PRODUCTION",
              "regulations": [
                  {
                      "framework": "EU_AI_ACT",
                      "article": "10",
                      "version": "2021",
                      "uri": null
                  }
              ],
              "geography": [
                  "EU"
              ],
              "actor_role": "PROVIDER",
              "actor_role_value": null,
              "requirements": [
                  {
                      "id": "req1",
                      "name": "Data Governance",
                      "description": "Ensure datasets are relevant, representative, and free from bias.",
                      "source": {
                          "framework": "EU_AI_ACT",
                          "article": "10",
                          "version": "2021",
                          "uri": null
                      },
                      "requirement_text": "Maintain data governance practices.",
                      "summary": "Data governance for fairness.",
                      "kind": "DATA_PROTECTION",
                      "applicability_status": "APPLICABLE",
                      "related_requirements": []
                  }
              ],
              "affected_groups": [
                  {
                      "id": "group1",
                      "name": "Job Seekers",
                      "description": "Individuals applying for job positions.",
                      "group_kind": "APPLICANTS",
                      "group_role": "IMPACTED",
                      "vulnerability": "Potential bias in hiring algorithms.",
                      "vulnerability_value": "HIGH",
                      "rationale": "Directly impacted by hiring decisions."
                  }
              ],
              "vulnerable_groups": [
                  {
                      "id": "group2",
                      "name": "Racial/Ethnic Minorities",
                      "description": "Individuals from underrepresented backgrounds.",
                      "group_kind": "MINORITIES",
                      "group_role": "VULNERABLE",
                      "vulnerability": "Bias in algorithmic decisions.",
                      "vulnerability_value": "MODERATE",
                      "rationale": "Risk of indirect discrimination."
                  }
              ],
              "protected_attributes": [
                  {
                      "id": "attr1",
                      "name": "Gender",
                      "semantic_type": "DEMOGRAPHIC",
                      "semantic_type_value": "GENDER",
                      "attribute_role": "FAIRNESS",
                      "attribute_role_value": "HIGH",
                      "observability": "VISIBLE",
                      "use_permissions": [
                          "ANALYSIS"
                      ],
                      "rationale": "High fairness risk in hiring."
                  }
              ],
              "foreseeable_misuse": [
                  {
                      "id": "misuse1",
                      "name": "Systemic exclusion of certain groups",
                      "misuse_kind": "Bias in Recommendations",
                      "intentionality": "Unintentional",
                      "likelihood": "Moderate",
                      "impact": "High",
                      "rationale": "Bias in training data."
                  }
              ],
              "risk_classification": {
                  "risk_level": "HIGH",
                  "risk_level_value": "Significant",
                  "classification_status": "PRELIMINARY",
                  "rationale": "Potential bias in decision-making."
              },
              "human_oversight": {
                  "oversight_mode": "HUMAN_IN_THE_LOOP",
                  "overseer_roles": [
                      "Recruiter"
                  ],
                  "intervention_points": [
                      "Decision Review"
                  ],
                  "authority": [
                      "HR Department"
                  ],
                  "availability": "BUSINESS_HOURS",
                  "escalation_channels": [
                      "Email",
                      "Phone"
                  ],
                  "rationale": "Ensure fairness and compliance."
              }
          }
      },
      dataset: {
    "narrative_summary": "The dataset is used for validation in an AI system for CV screening. Compliance with data quality, privacy, and fairness standards is essential.",
    "data_quality_analysis": "The dataset must be complete, accurate, and representative of the target population. Missing or incorrect data could lead to biased or unreliable validation results.",
    "privacy_analysis": "If sensitive attributes are present, GDPR Art. 9 exemptions must be documented. Privacy-by-design principles (Art. 25) must be implemented.",
    "fairness_analysis": "The system must ensure fairness across protected groups. Metrics like demographic parity and equalized odds should be monitored to prevent discrimination.",
    "scope": "VALIDATION",
    "protected_attributes": [
        {
            "id": "gender",
            "name": "Gender",
            "semantic_type": "Categorical",
            "semantic_type_value": "Gender",
            "attribute_role": "Protected",
            "attribute_role_value": "Sensitive",
            "observability": "High",
            "use_permissions": [
                "Analysis"
            ],
            "rationale": "Protected under EU Directive 2006/54/EC."
        }
    ],
    "objectives": [
        {
            "name": "Accuracy Objective",
            "description": "Ensure data values correctly represent real-world entities.",
            "metric": {
                "id": "accuracy-correctness",
                "name": "Accuracy/Correctness",
                "description": "Accuracy of data values.",
                "kind": "percentage",
                "scale": "ratio",
                "unit": {
                    "symbol": "%",
                    "name": "Percentage"
                },
                "range": {
                    "lower": 0,
                    "upper": 100,
                    "discrete": false,
                    "categories": null
                }
            },
            "condition": {
                "threshold": {
                    "operator": "GEQ",
                    "value": 95
                },
                "range": null
            },
            "priority": "RECOMMENDED",
            "severity": "MEDIUM",
            "requirements": [
                "EU AI Act Art. 10"
            ],
            "rationale": null
        }
    ]
  },
  model: {
    "narrative_summary": "The Gemma-4-E4B model addresses compliance concerns in fairness, robustness, explainability, and calibration for HR-assisted CV screening.",
    "fairness_analysis": "The model ensures equitable treatment across demographic groups by adhering to fairness metrics.",
    "robustness_analysis": "The model demonstrates resilience to adversarial inputs and distribution shifts.",
    "transparency_analysis": "The model provides clear and interpretable explanations for its predictions.",
    "objectives": [
        {
            "name": "Fairness Objective 1",
            "description": "Ensure demographic parity difference is within acceptable limits.",
            "metric": {
                "id": "metric_001",
                "name": "Demographic Parity Difference",
                "description": "Measures the difference in positive prediction rates across groups.",
                "kind": "statistical",
                "scale": "ratio",
                "unit": null,
                "range": null
            },
            "condition": {
                "threshold": {
                    "operator": "LEQ",
                    "value": 0.05
                },
                "range": null
            },
            "priority": "MANDATORY",
            "severity": "CRITICAL",
            "requirements": [
                "EU AI Act Articles 10, 13, 14"
            ],
            "rationale": "To prevent bias in hiring decisions."
        },
        {
            "name": "Robustness Objective 1",
            "description": "Maintain adversarial accuracy above the threshold.",
            "metric": {
                "id": "metric_002",
                "name": "Adversarial Accuracy",
                "description": "Accuracy on adversarially perturbed inputs.",
                "kind": "performance",
                "scale": "ratio",
                "unit": null,
                "range": null
            },
            "condition": {
                "threshold": {
                    "operator": "GEQ",
                    "value": 0.8
                },
                "range": null
            },
            "priority": "RECOMMENDED",
            "severity": "HIGH",
            "requirements": [
                "EU AI Act Articles 13, 14"
            ],
            "rationale": "To ensure resilience against adversarial inputs."
        }
    ]
  }
}

const baseUrl = process.env.COMPLIANCE_AGENT_URL ?? 'http://localhost:8000';

const convertProjectInput = (project, spec) => {
    const input = {} as JsonRecord;
    input.name = project.metadata.name + ' ' + project.metadata.description;
    if (spec) {
      input.domain = spec.domain.domain ?? '';
      input.subdomain = spec.domain.subdomain ?? '';
      input.ai_task = spec.ai_task ?? '';
      input.goal = spec.goal ?? '';
      input.purpose = spec.purpose ?? '';
      input.audience = spec.audience ?? '';
      input.scope_in = spec.scope.in_scope ?? [];
      input.scope_out = spec.scope.out_scope ?? [];
      input.deployment = spec.deployment ?? 'CLOUD';
      input.geography = spec.context.geography ?? ['EU'];
      input.environment = spec.context.environment ?? 'PRODUCTION';
    }
    return input;
};

async function generateProjectContext(project, spec, httpClient) {

    const input = convertProjectInput(project, spec);

    try {
      // const data = await httpClient(`${baseUrl}/compliance/context`, { method: 'POST', body: JSON.stringify(input) });
      const data = {json: mockData.project};
      return {narrative: data.json.narrative_summary, patch: {context: data.json.compliance_context}} as AgentResult;
    } catch (error) {
      console.error('Error generating compliance context:', error);
      return Promise.reject(error);
    }
}

async function generateDatasetContext(dataset, spec, project, projectSpec, httpClient) {
    try {

      // project: Optional[ProjectInput] = None
      const input = {} as JsonRecord;
      input.name = dataset.metadata.name;
      input.description = dataset.metadata.description ?? '';

      if (dataset.spec.schema) {
        input.schema_fields = dataset.spec.schema.fields.map(f => ({name: f.name, dtype: f.type}));  
      }
      if (dataset.status && dataset.status.preview) {
        const rows: JsonRecord[] = [];
        for (var i = 0; i < Math.min(dataset.status.preview.cols[0].value.length, 3); i++) {
          const record = {};
          for (var j = 0; j < dataset.status.preview.cols.length; j++) {
            record[dataset.status.preview.cols[j].name] = dataset.status.preview.cols[j].value[i];
          }
          rows.push(record);
        }
        input.sample_records = rows;
        input.n_records = dataset.status.preview.rows ?? 0;
      }

      if (spec) {
        input.scope = spec.scope ?? '';
        if (spec.data_governance) {
          input.sensitivity = spec.data_governance.sensitivity ?? '';
        }
      }

      if (project && projectSpec) {
        input.project = convertProjectInput(project, projectSpec);
      }

      // const data = await httpClient(`${baseUrl}/compliance/dataset`, { method: 'POST', body: JSON.stringify(input) });
      const data = {json: mockData.dataset};
      return {narrative: data.json.narrative_summary, patch: {attributes: data.json.protected_attributes, objectives: data.json.objectives}} as AgentResult;
    } catch (error) {
      console.error('Error generating compliance context:', error);
      return Promise.reject(error);
    }
}

async function generateModelContext(model, spec, project, projectSpec, httpClient) {
    try {
    // framework: Optional[str] = Field(None, description="e.g. PYTORCH, SKLEARN, HUGGINGFACE")
    
    // output_type: str = Field(default="BINARY_CLASSIFICATION",
    //                           description="BINARY_CLASSIFICATION | MULTI_CLASS | REGRESSION | GENERATION | RANKING")
    // produces_probabilities: bool = True
    // trained_on_sensitive_data: bool = False
    // known_affected_groups: list[str] = Field(default=[], description="Groups affected by model decisions")
    // project: Optional[ProjectInput] = None
    // prior_requirements: list[dict[str, Any]] = Field(default=[],
    //                                                   description="Requirements from prior ComplianceContext analysis")


      const input = {} as JsonRecord;
      input.name = model.metadata.name;
      input.description = model.metadata.description ?? '';

      input.framework = model.spec.framework ?? '';
      input.framework += ' ' + (model.spec.algorithm ?? '');

      if (project && projectSpec) {
        input.ai_task = projectSpec?.ai_task ?? '';
        input.project = convertProjectInput(project, projectSpec);
      }

      // const data = await httpClient(`${baseUrl}/compliance/model`, { method: 'POST', body: JSON.stringify(input) });
      const data = {json: mockData.model};
      return {narrative: data.json.narrative_summary, patch: {objectives: data.json.objectives}} as AgentResult;
    } catch (error) {
      console.error('Error generating compliance context:', error);
      return Promise.reject(error);
    }
}

/** In-browser mock {@link AgentService}. See module doc comment above. */
export function createAgentService(authProvider: any): AgentService {
  const baseUrl = process.env.COMPLIANCE_AGENT_URL ?? 'http://localhost:8000';

  const httpClient = async (url: string, options: fetchUtils.Options = {}) => {
      const headers = (options.headers ||
          new Headers({
              Accept: 'application/json',
          })) as Headers;
      if (authProvider) {
          const authHeader = await authProvider.getAuthorization();
          if (authHeader) {
              headers.set('Authorization', authHeader);
          }
      }

      options.headers = headers;
      return fetchUtils.fetchJson(url, options);
  };

  return {
    async extendComplianceSpec(kind, entity, _currentSpec, project = null, projectSpec = null) {
      console.log(`Extending compliance spec for kind: ${kind}`, entity, _currentSpec, project, projectSpec);
      if ('project' === kind) {
        const spec = (entity.extensions as any[] || []).find(e => e.kind === 'ai-compliance');
        const ctx = await generateProjectContext(entity, spec.spec ?? null, httpClient);
        console.log('Generated compliance context:', ctx);
        return ctx ? Promise.resolve(ctx) : Promise.reject(undefined) as any;
      }
      if ('dataset' === kind) {
        const spec = (entity.extensions as any[] || []).find(e => e.kind === 'data-compliance');
        const ctx = await generateDatasetContext(entity, spec.spec ?? null, project, projectSpec, httpClient);
        console.log('Generated compliance context:', ctx);
        return ctx ? Promise.resolve(ctx) : Promise.reject(undefined) as any;
      }
      if ('model' === kind) {
        const spec = (entity.extensions as any[] || []).find(e => e.kind === 'model-compliance');
        const ctx = await generateModelContext(entity, spec.spec ?? null, project, projectSpec, httpClient);
        console.log('Generated compliance context:', ctx);
        return ctx ? Promise.resolve(ctx) : Promise.reject(undefined) as any;
      }
    },
  };
}
