import { Stack } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";

interface ComplianceStatusTabProps {
  status: JsonRecord;
  setStatus: (next: JsonRecord) => void;
}

/** Shared "Status" tab content (ComplianceStatus), identical across project/dataset/model specs. */
export default function ComplianceStatusTab({ status, setStatus }: ComplianceStatusTabProps) {
  const t = useTranslate();
  return (
    <Stack spacing={1.5}>
      <SectionAccordion title={t("compliance.fields.compliance_status")}>
        <SchemaForm schemaName="ComplianceStatus" only={["status", "assessed_at", "assessor"]} value={status} onChange={setStatus} />
      </SectionAccordion>
      <SectionAccordion title={t("compliance.fields.findings")}>
        <SchemaForm schemaName="ComplianceStatus" only={["findings"]} value={status} onChange={setStatus} />
      </SectionAccordion>
      <SectionAccordion title={t("compliance.fields.mitigations")}>
        <SchemaForm schemaName="ComplianceStatus" only={["mitigations"]} value={status} onChange={setStatus} />
      </SectionAccordion>
    </Stack>
  );
}
