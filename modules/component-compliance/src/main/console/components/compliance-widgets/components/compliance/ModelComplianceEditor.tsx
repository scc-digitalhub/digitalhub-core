import { useState } from "react";
import { Alert, Box, Tab, Tabs } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import ComplianceStatusTab from "./ComplianceStatusTab";
import ComplianceDocumentationTab from "./ComplianceDocumentationTab";

export interface ModelComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

/** Compliance spec editor for models (ModelComplianceSpec): General / Status / Objectives / Documentation tabs. */
export default function ModelComplianceEditor({ spec, onChange }: ModelComplianceEditorProps) {
  const t = useTranslate();
  const [tab, setTab] = useState(0);

  const status = (spec.compliance_status as JsonRecord) ?? {};
  const setStatus = (next: JsonRecord) => onChange({ ...spec, compliance_status: next });

  const documentation = (spec.documentation as JsonRecord) ?? {};
  const setDocumentation = (next: JsonRecord) => onChange({ ...spec, documentation: next });

  return (
    <Box>
      <Tabs value={tab} onChange={(_e, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
        <Tab label={t("compliance.complianceTabs.general")} />
        <Tab label={t("compliance.complianceTabs.status")} />
        <Tab label={t("compliance.complianceTabs.objectives")} />
        <Tab label={t("compliance.complianceTabs.documentation")} />
      </Tabs>

      {tab === 0 && (
        <Alert severity="info">
          {t(
            "modelGeneralNotice",
            "ModelComplianceSpec adds no model-specific fields beyond the shared ComplianceSpec — see the Status, Objectives and Documentation tabs.",
          )}
        </Alert>
      )}

      {tab === 1 && <ComplianceStatusTab status={status} setStatus={setStatus} />}

      {tab === 2 && (
        <SectionAccordion title={t("compliance.fields.objectives")}>
          <SchemaForm schemaName="ModelComplianceSpec" only={["objectives"]} value={spec} onChange={onChange} />
        </SectionAccordion>
      )}

      {tab === 3 && <ComplianceDocumentationTab documentation={documentation} setDocumentation={setDocumentation} />}
    </Box>
  );
}
