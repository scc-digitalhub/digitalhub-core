import type { ComplianceWidgetServices } from "../compliance-widgets/services/types";
import { createComplianceService } from "./complianceService";
import { createAgentService } from "./agentService";

export function createServices(dataProvider: any, authProvider: any): ComplianceWidgetServices {

  return {
    complianceService: createComplianceService(dataProvider),
    agentService: createAgentService(authProvider),
  };
}
