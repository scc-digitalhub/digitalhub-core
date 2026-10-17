import AddIcon from "@mui/icons-material/Add";
import { Button, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { useGetOne, useRecordContext, useResourceContext } from "react-admin";
import { useTranslate } from "react-admin";
import { v4 as uuidv4 } from "uuid";
import { createDefaultForSchema } from "../../schema/resolver";
import type { JsonRecord } from "../../types";
import { useSchemaReadOnly } from "../schemaForm/SchemaReadOnlyContext";
import ComplianceReportWidget from "./ComplianceReportWidget";
import { useComplianceServices } from "../..";
import { useRootSelector } from "@dslab/ra-root-selector";
import { randomId } from "@digitalhub/console/common/utils/helpers";

export default function ComplianceReportsTab() {
  const t = useTranslate();
  const { complianceService } = useComplianceServices();

  const canManageReports = useSchemaReadOnly();
  const [newReport, setNewReport] = useState<JsonRecord | null>(null);
  const [reportEditing, setReportEditing] = useState(false);
  const [reports, setReports] = useState<JsonRecord[]>([]);
  const { root: project } = useRootSelector();

  const entity = useRecordContext();
  const resource = useResourceContext();
  const { data: refreshedEntity, refetch: refetchEntity } = useGetOne(
    resource as string,
    { id: entity?.id as string },
    { enabled: Boolean(entity?.id) },
  );

  useEffect(() => {
    if (!canManageReports) {
      setNewReport(null);
      setReportEditing(false);
    }
    const currentEntity = refreshedEntity ?? entity;
    if (currentEntity) {
      const initialReports = ((currentEntity.extensions as JsonRecord[] | undefined) ?? [])
      .filter((item) => item.kind === "compliance-report");
      setReports(initialReports);
    }

  }, [canManageReports, entity, refreshedEntity]);

  const addReport = () => {
    if (!canManageReports) return;
    setNewReport({
      id: randomId(),
      name: "",
      project: project,
      kind: "compliance-report",
      spec: createDefaultForSchema("ReportSpec"),
    });
    setReportEditing(true);
  };

  const saveReport = async (report: JsonRecord) => {
      const saved = await complianceService.saveComplianceSpec("report", resource as string, entity?.id as string, report);
      const { data: updatedEntity } = await refetchEntity();
      const updatedReports = ((updatedEntity?.extensions as JsonRecord[] | undefined) ?? [])
        .filter((item) => item.kind === "compliance-report");
      setReports(updatedReports);
      return updatedReports.find((item) => item.id === saved.id) ?? saved;
    };

    const deleteReport = async (report: JsonRecord) => {
      await complianceService.deleteComplianceSpec("report", resource as string, entity?.id as string, report.id as string);
      const { data: updatedEntity } = await refetchEntity();
      setReports(
        ((updatedEntity?.extensions as JsonRecord[] | undefined) ?? [])
          .filter((item) => item.kind === "compliance-report"),
      );
    };

  const saveNewReport = async (report: JsonRecord) => {
    const saved = await saveReport(report);
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
          onSave={saveReport}
          onDelete={deleteReport}
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
