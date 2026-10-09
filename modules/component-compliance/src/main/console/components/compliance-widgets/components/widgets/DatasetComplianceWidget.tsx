import { createComplianceWidget } from "./ComplianceWidget";
import DatasetComplianceEditor from "../compliance/DatasetComplianceEditor";

/**
 * Standalone widget: given a dataset `entityId`, fetches its definition and
 * `DataComplianceSpec` via the injected services, and lets the user view/edit it (with AI
 * generate/extend assistance and a Save action). See `ComplianceServicesProvider`.
 */
const DatasetComplianceWidget = createComplianceWidget("dataset", DatasetComplianceEditor);

export default DatasetComplianceWidget;
