import { useState } from "react";
import { Box, Stack, Tab, Tabs } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import ComplianceStatusTab from "./ComplianceStatusTab";
import ComplianceDocumentationTab from "./ComplianceDocumentationTab";

export interface DatasetComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

/** Compliance spec editor for datasets (DataComplianceSpec): General / Status / Objectives / Documentation tabs. */
export default function DatasetComplianceEditor({ spec, onChange }: DatasetComplianceEditorProps) {
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
        <Stack spacing={1.5}>
          <SectionAccordion title={t("compliance.fields.scope")}>
            <SchemaForm schemaName="DataComplianceSpec" only={["scope"]} value={spec} onChange={onChange} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.governance")}>
            <SchemaForm schemaName="DataComplianceSpec" only={["governance"]} value={spec} onChange={onChange} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.attributes")}>
            <SchemaForm schemaName="DataComplianceSpec" only={["attributes"]} value={spec} onChange={onChange} />
          </SectionAccordion>
        </Stack>
      )}

      {tab === 1 && <ComplianceStatusTab status={status} setStatus={setStatus} />}

      {tab === 2 && (
        <SectionAccordion title={t("compliance.fields.objectives")}>
          <SchemaForm schemaName="DataComplianceSpec" only={["objectives"]} value={spec} onChange={onChange} />
        </SectionAccordion>
      )}

      {tab === 3 && <ComplianceDocumentationTab documentation={documentation} setDocumentation={setDocumentation} />}
    </Box>
  );
}
