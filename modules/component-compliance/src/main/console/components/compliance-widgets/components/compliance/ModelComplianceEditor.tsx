import { useState } from "react";
import { Box, Tab, Tabs, Typography } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import ComplianceDocumentationTab from "./ComplianceDocumentationTab";
import ComplianceObjectivesWidget from "./ComplianceObjectivesWidget";
import ComplianceReportsTab from "./ComplianceReportsTab";

export interface ModelComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

/** Compliance spec editor for models (ModelComplianceSpec): General / Status / Objectives / Documentation tabs. */
export default function ModelComplianceEditor({ spec, onChange }: ModelComplianceEditorProps) {
  const t = useTranslate();
  const [tab, setTab] = useState(0);
  const guidanceKeys = ["general", "objectives", "documentation", "reports"];

  const status = (spec.compliance_status as JsonRecord) ?? {};
  const setStatus = (next: JsonRecord) => onChange({ ...spec, compliance_status: next });

  const documentation = (spec.documentation as JsonRecord) ?? {};
  const setDocumentation = (next: JsonRecord) => onChange({ ...spec, documentation: next });
  const objectives = (spec.objectives as JsonRecord[]) ?? [];

  return (
    <Box>
      <Tabs value={tab} onChange={(_e, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
        <Tab label={t("compliance.compliancetabs.general")} />
        <Tab label={t("compliance.compliancetabs.objectives")} />
        <Tab label={t("compliance.compliancetabs.documentation")} />
        <Tab label={t("compliance.compliancetabs.reports")} />
      </Tabs>
      <Typography variant="body2" color="text.secondary" mb={2}>
        {t(`compliance.guidance.model.${guidanceKeys[tab]}`)}
      </Typography>

      {tab === 0 && (
          <SectionAccordion title={t("compliance.fields.compliance_status")}>
            <SchemaForm schemaName="ComplianceStatus" only={["status", "assessed_at", "assessor"]} value={status} onChange={setStatus} />
          </SectionAccordion>
      )}

      {tab === 1 && (
          <ComplianceObjectivesWidget value={objectives} onChange={(next) => onChange({ ...spec, objectives: next })} />
      )}


      {tab === 2 && <ComplianceDocumentationTab documentation={documentation} setDocumentation={setDocumentation} />}
      {tab === 3 && <ComplianceReportsTab />}
    </Box>
  );
}
