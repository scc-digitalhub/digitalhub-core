import AddIcon from "@mui/icons-material/Add";
import { Button, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { useTranslate } from "react-admin";
import { v4 as uuidv4 } from "uuid";
import { createDefaultForSchema } from "../../schema/resolver";
import type { JsonRecord } from "../../types";
import { useSchemaReadOnly } from "../schemaForm/SchemaReadOnlyContext";
import ComplianceReportWidget from "./ComplianceReportWidget";

interface ComplianceReportsTabProps {
  reports: JsonRecord[];
  onSave: (report: JsonRecord) => Promise<JsonRecord>;
  onDelete: (report: JsonRecord) => Promise<void>;
  project: unknown;
}

export default function ComplianceReportsTab({ reports, onSave, onDelete, project }: ComplianceReportsTabProps) {
  const t = useTranslate();
  const canManageReports = useSchemaReadOnly();
  const [newReport, setNewReport] = useState<JsonRecord | null>(null);
  const [reportEditing, setReportEditing] = useState(false);

  useEffect(() => {
    if (!canManageReports) {
      setNewReport(null);
      setReportEditing(false);
    }
  }, [canManageReports]);

  const addReport = () => {
    if (!canManageReports) return;
    setNewReport({
      id: uuidv4(),
      name: "",
      kind: "compliance-report",
      project,
      spec: createDefaultForSchema("ReportSpec"),
    });
    setReportEditing(true);
  };

  const saveNewReport = async (report: JsonRecord) => {
    const saved = await onSave(report);
    setNewReport(null);
    return saved;
  };

  return (
    <Stack spacing={1}>
      {reports.length === 0 && !newReport && (
        <Typography color="text.secondary">{t("compliance.form.emptyArray")}</Typography>
      )}

      {reports.map((report, index) => (
        <ComplianceReportWidget
          key={(report.id as string) ?? index}
          report={report}
          index={index + 1}
          canManage={canManageReports}
          managementLocked={reportEditing}
          onSave={onSave}
          onDelete={onDelete}
          onEditingChange={setReportEditing}
        />
      ))}

      {canManageReports && newReport && (
        <ComplianceReportWidget
          key={newReport.id as string}
          report={newReport}
          index={reports.length + 1}
          canManage
          managementLocked={false}
          initialEditing
          onSave={saveNewReport}
          onEditingChange={(editing) => {
            setReportEditing(editing);
            if (!editing) setNewReport(null);
          }}
        />
      )}

      {canManageReports && (
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={addReport}
          disabled={reportEditing}
          sx={{ alignSelf: "flex-start" }}
        >
          {t("compliance.common.add")}
        </Button>
      )}
    </Stack>
  );
}
