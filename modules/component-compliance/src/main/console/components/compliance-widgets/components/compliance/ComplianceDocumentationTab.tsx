import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";

interface ComplianceDocumentationTabProps {
  documentation: JsonRecord;
  setDocumentation: (next: JsonRecord) => void;
}

/** Shared "Documentation" tab content (ComplianceDocumentation), identical across all specs. */
export default function ComplianceDocumentationTab({ documentation, setDocumentation }: ComplianceDocumentationTabProps) {
  const t = useTranslate();
  return (
    <SectionAccordion title={t("compliance.fields.sections")}>
      <SchemaForm schemaName="ComplianceDocumentation" only={["sections"]} value={documentation} onChange={setDocumentation} />
    </SectionAccordion>
  );
}
