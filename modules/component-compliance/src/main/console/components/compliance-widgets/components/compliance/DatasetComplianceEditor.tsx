import { useState } from "react";
import { Box, Stack, Tab, Tabs } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import ComplianceDocumentationTab from "./ComplianceDocumentationTab";
import ComplianceObjectivesWidget from "./ComplianceObjectivesWidget";
import ComplianceReportsTab from "./ComplianceReportsTab";

export interface DatasetComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
  reports?: JsonRecord[];
  onSaveReport?: (report: JsonRecord) => Promise<JsonRecord>;
  onDeleteReport?: (report: JsonRecord) => Promise<void>;
  reportProject?: unknown;
}

/** Compliance spec editor for datasets (DataComplianceSpec): General / Status / Objectives / Documentation tabs. */
export default function DatasetComplianceEditor({
  spec, onChange, reports = [], onSaveReport = async (report) => report, onDeleteReport = async () => undefined, reportProject,
}: DatasetComplianceEditorProps) {
  const t = useTranslate();
  const [tab, setTab] = useState(0);

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

      {tab === 0 && (
        <Stack spacing={1.5}>
          <SectionAccordion title={t("compliance.fields.compliance_status")}>
            <SchemaForm schemaName="ComplianceStatus" only={["status", "assessed_at", "assessor"]} value={status} onChange={setStatus} />
          </SectionAccordion>
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


      {tab === 1 && (
          <ComplianceObjectivesWidget value={objectives} onChange={(next) => onChange({ ...spec, objectives: next })} />
      )}

      {tab === 2 && <ComplianceDocumentationTab documentation={documentation} setDocumentation={setDocumentation} />}
      {tab === 3 && <ComplianceReportsTab reports={reports} onSave={onSaveReport} onDelete={onDeleteReport} project={reportProject} />}
    </Box>
  );
}
