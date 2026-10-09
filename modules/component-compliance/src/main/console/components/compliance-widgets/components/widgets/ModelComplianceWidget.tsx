import { createComplianceWidget } from "./ComplianceWidget";
import ModelComplianceEditor from "../compliance/ModelComplianceEditor";

/**
 * Standalone widget: given a model `entityId`, fetches its definition and
 * `ModelComplianceSpec` via the injected services, and lets the user view/edit it (with AI
 * generate/extend assistance and a Save action). See `ComplianceServicesProvider`.
 */
const ModelComplianceWidget = createComplianceWidget("model", ModelComplianceEditor);

export default ModelComplianceWidget;
