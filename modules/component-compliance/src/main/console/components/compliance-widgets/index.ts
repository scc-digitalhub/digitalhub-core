/**
 * compliance-widgets — a standalone module providing the Project/Dataset/Model compliance
 * widgets (React + MUI), decoupled from any specific backend via an injectable service layer.
 *
 * Minimal usage:
 *
 * ```tsx
 * import {
 *   ComplianceServicesProvider, ProjectComplianceWidget, complianceWidgetMessages,
 * } from "@/compliance-widgets";
 *
 * function App() {
 *   return (
 *     // Omit `services` to use the bundled in-browser mock; pass your own ComplianceService/
 *     // AgentService implementations to connect a real backend.
 *     <ComplianceServicesProvider>
 *       <ProjectComplianceWidget entityId="11111111-1111-1111-1111-111111111111" />
 *     </ComplianceServicesProvider>
 *   );
 * }
 * ```
 */

// Widgets — self-contained: fetch/save via injected services + AI assist + Save action.
export { default as ProjectComplianceWidget } from "./components/widgets/ProjectComplianceWidget";
export { default as DatasetComplianceWidget } from "./components/widgets/DatasetComplianceWidget";
export { default as ModelComplianceWidget } from "./components/widgets/ModelComplianceWidget";
export { createComplianceWidget } from "./components/widgets/ComplianceWidget";
export type { ComplianceWidgetProps } from "./components/widgets/ComplianceWidget";

// Lower-level, fully controlled editors (bring your own data fetching/saving).
export { default as ProjectComplianceEditor } from "./components/compliance/ProjectComplianceEditor3";
export { default as DatasetComplianceEditor } from "./components/compliance/DatasetComplianceEditor";
export { default as ModelComplianceEditor } from "./components/compliance/ModelComplianceEditor";
export { default as AgentAssistPanel } from "./components/compliance/AgentAssistPanel";
export type { ProjectComplianceEditorProps } from "./components/compliance/ProjectComplianceEditor3";
export type { DatasetComplianceEditorProps } from "./components/compliance/DatasetComplianceEditor";
export type { ModelComplianceEditorProps } from "./components/compliance/ModelComplianceEditor";

// Services: types (implement these against your backend), context/provider, bundled mock.
export type {
  ComplianceService, AgentService, AgentResult, ComplianceWidgetServices,
} from "./services/types";
export { ComplianceServicesProvider, useComplianceServices } from "./services/context";
// i18n: bundled English/Italian resources for the host's React Admin i18n provider.
export { complianceWidgetMessages } from "./i18n/register";

// Domain types and the schema-name lookup tables used by the editors/EntityForm.
export type { ComplianceEntityKind, JsonRecord } from "./types";

// Schema engine (advanced use — e.g. building custom editors on top of the same schemas).
export { resolveSchema, createDefaultForSchema } from "./schema/resolver";
export { default as SchemaForm } from "./components/schemaForm/SchemaForm";
