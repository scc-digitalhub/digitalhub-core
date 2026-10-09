import type { ComplianceEntityKind, JsonRecord } from "../types";

/**
 * Compliance specification persistence service. Mirrors a `/compliance-spec/{kind}/{id}`
 * resource — implement this against your real backend to replace the bundled mock (see
 * services/mock/complianceService.ts).
 */
export interface ComplianceService {
  getComplianceSpec(kind: ComplianceEntityKind, entityKind: string, entityId: string): Promise<JsonRecord | null>;
  saveComplianceSpec(kind: ComplianceEntityKind, entityKind: string, entityId: string, spec: JsonRecord): Promise<JsonRecord>;
  deleteComplianceSpec(kind: ComplianceEntityKind, entityKind: string, entityId: string): Promise<void>;
}

/** Result of an agent "generate"/"extend" call: a narrative rationale plus a JSON patch. */
export interface AgentResult {
  narrative: string;
  patch: JsonRecord;
}

/**
 * AI agent service used by the "Generate with AI" / "Extend with AI" actions. Mirrors
 * `POST /compliance/{context|dataset|model}/{id}` (src/compliance_agent/agent/agent.py) —
 * implement this against your real backend to replace the bundled mock (see
 * services/mock/agentService.ts).
 */
export interface AgentService {
  extendComplianceSpec(kind: ComplianceEntityKind, entity: JsonRecord, currentSpec: JsonRecord, project: JsonRecord | null, projectSpec: JsonRecord | null): Promise<AgentResult>;
}

/** Full set of services a compliance widget needs to operate. */
export interface ComplianceWidgetServices {
  complianceService: ComplianceService;
  agentService: AgentService;
}
