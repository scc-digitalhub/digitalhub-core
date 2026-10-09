import { useState } from "react";
import { Box, Stack, Tab, Tabs } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import ComplianceStatusTab from "./ComplianceStatusTab";
import ComplianceDocumentationTab from "./ComplianceDocumentationTab";
import ReadOnlyObjectives from "./ReadOnlyObjectives";

export interface ProjectComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

/**
 * Compliance spec editor for projects (ProjectComplianceSpec): General / Status / Requirements /
 * Documentation tabs. Compliance objectives are shown read-only in Requirements — they are
 * owned/edited at the dataset and model level, not directly on the project.
 */
export default function ProjectComplianceEditor({ spec, onChange }: ProjectComplianceEditorProps) {
  const t = useTranslate();
  const [tab, setTab] = useState(0);

  const context = (spec.context as JsonRecord) ?? {};
  const setContext = (next: JsonRecord) => onChange({ ...spec, context: next });

  const status = (spec.compliance_status as JsonRecord) ?? {};
  const setStatus = (next: JsonRecord) => onChange({ ...spec, compliance_status: next });

  const documentation = (spec.documentation as JsonRecord) ?? {};
  const setDocumentation = (next: JsonRecord) => onChange({ ...spec, documentation: next });

  const objectives = (spec.objectives as JsonRecord[]) ?? [];

  return (
    <Box>
      <Tabs value={tab} onChange={(_e, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
        <Tab label={t("compliance.complianceTabs.general")} />
        <Tab label={t("compliance.complianceTabs.status")} />
        <Tab label={t("compliance.complianceTabs.requirements")} />
        <Tab label={t("compliance.complianceTabs.documentation")} />
      </Tabs>

      {tab === 0 && (
        <Stack spacing={1.5}>
          <SectionAccordion title={`${t("compliance.fields.domain")} & ${t("compliance.fields.ai_task")}`}>
            <SchemaForm schemaName="ProjectComplianceSpec" only={["domain", "ai_task", "ai_task_value", "goal"]} value={spec} onChange={onChange} />
          </SectionAccordion>
          <SectionAccordion title={`${t("compliance.fields.purpose")} & ${t("compliance.fields.audience")}`}>
            <SchemaForm schemaName="ProjectComplianceSpec" only={["purpose", "audience"]} value={spec} onChange={onChange} />
          </SectionAccordion>
          <SectionAccordion title={`${t("compliance.fields.scope")} & ${t("compliance.fields.deployment")}`}>
            <SchemaForm schemaName="ProjectComplianceSpec" only={["scope", "deployment"]} value={spec} onChange={onChange} />
          </SectionAccordion>
        </Stack>
      )}

      {tab === 1 && <ComplianceStatusTab status={status} setStatus={setStatus} />}

      {tab === 2 && (
        <Stack spacing={1.5}>
          <SectionAccordion title={`${t("compliance.fields.environment")} & ${t("compliance.fields.geography")}`}>
            <SchemaForm schemaName="ComplianceContext" only={["environment", "geography"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.regulations")}>
            <SchemaForm schemaName="ComplianceContext" only={["regulations"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.requirements")}>
            <SchemaForm schemaName="ComplianceContext" only={["requirements"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.risk_classification")}>
            <SchemaForm schemaName="ComplianceContext" only={["risk_classification"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.actor_role")}>
            <SchemaForm schemaName="ComplianceContext" only={["actor_role", "actor_role_value"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={`${t("compliance.fields.affected_groups")} / ${t("compliance.fields.vulnerable_groups")}`}>
            <SchemaForm schemaName="ComplianceContext" only={["affected_groups", "vulnerable_groups"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.protected_attributes")}>
            <SchemaForm schemaName="ComplianceContext" only={["protected_attributes"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.foreseeable_misuse")}>
            <SchemaForm schemaName="ComplianceContext" only={["foreseeable_misuse"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.human_oversight")}>
            <SchemaForm schemaName="ComplianceContext" only={["human_oversight"]} value={context} onChange={setContext} />
          </SectionAccordion>
          <SectionAccordion title={t("compliance.fields.objectives")} subtitle={t("compliance.common.readOnly")} defaultExpanded={false}>
            <ReadOnlyObjectives objectives={objectives} />
          </SectionAccordion>
        </Stack>
      )}

      {tab === 3 && <ComplianceDocumentationTab documentation={documentation} setDocumentation={setDocumentation} />}
    </Box>
  );
}
