import { createComplianceWidget } from "./ComplianceWidget";
import ProjectComplianceEditor from "../compliance/ProjectComplianceEditor";

/**
 * Standalone widget: given a project `entityId`, fetches its definition and
 * `ProjectComplianceSpec` via the injected services, and lets the user view/edit it (with AI
 * generate/extend assistance and a Save action). See `ComplianceServicesProvider`.
 */
const ProjectComplianceWidget = createComplianceWidget("project", ProjectComplianceEditor);

export default ProjectComplianceWidget;
