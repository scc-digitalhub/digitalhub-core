import CloseIcon from "@mui/icons-material/Close";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SaveIcon from "@mui/icons-material/Save";
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Button, IconButton, Stack, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import { Form, useTranslate } from "react-admin";
import { useWatch } from "react-hook-form";
import { validateSpec } from "../../schema/validateSpec";
import type { JsonRecord } from "../../types";
import { fieldLabel } from "../schemaForm/fieldHelpers";
import SchemaForm from "../schemaForm/SchemaForm";
import { SchemaReadOnlyProvider } from "../schemaForm/SchemaReadOnlyContext";
import { useStateUpdateCallbacks } from "@digitalhub/console/common/hooks/useStateUpdateCallbacks";
import { useGetUploader } from "@digitalhub/console/features/files/upload/useGetUploader";
import { PathInput } from "@digitalhub/console/features/files/upload/components/PathInput";
import { FileInfoTree } from "@digitalhub/console/features/files/fileInfoTree/components/FileInfoTree";

interface ComplianceReportWidgetProps {
  report: JsonRecord;
  index: number;
  canManage: boolean;
  managementLocked: boolean;
  initialEditing?: boolean;
  onSave: (report: JsonRecord) => Promise<JsonRecord>;
  onDelete?: (report: JsonRecord) => Promise<void>;
  onEditingChange: (editing: boolean) => void;
}

function PathWatcher({ onChange }: { onChange: (path: string | undefined) => void }) {
  const path = useWatch({ name: "path" });

  useEffect(() => {
    onChange(path || undefined);
  }, [onChange, path]);

  return null;
}

export default function ComplianceReportWidget({
  report,
  index,
  canManage,
  managementLocked,
  initialEditing = false,
  onSave,
  onDelete,
  onEditingChange,
}: ComplianceReportWidgetProps) {
  const t = useTranslate();
  const [draft, setDraft] = useState<JsonRecord | null>(initialEditing ? report : null);
  const [expanded, setExpanded] = useState(initialEditing);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [path, setPath] = useState<string | undefined>(() => {
    const reportSpec = (report.spec as JsonRecord | undefined) ?? {};
    return reportSpec.path as string | undefined;
  });

  const editing = canManage && draft !== null;
  const activeReport = draft ?? report;
  const spec = (activeReport.spec as JsonRecord | undefined) ?? {};
  const title = (spec.name as string) || (spec.kind as string) || t("compliance.form.item", { index });

  const reportId = report.id as string;
  const { onBeforeUpload, onUploadComplete } = useStateUpdateCallbacks({
    id: reportId,
    resource: "extensions",
  });
  const uploader = useGetUploader({
    id: reportId,
    resource: "extensions",
    recordId: reportId,
    onBeforeUpload,
    onUploadComplete,
  });
    

  useEffect(() => {
    if (!canManage && draft) {
      setDraft(null);
      setValidationErrors([]);
      onEditingChange(false);
    }
  }, [canManage, draft, onEditingChange]);

  const startEditing = () => {
    setDraft(structuredClone(report));
    setPath(((report.spec as JsonRecord | undefined)?.path as string | undefined));
    setExpanded(true);
    setValidationErrors([]);
    onEditingChange(true);
  };

  const cancelEditing = () => {
    uploader.uppy.cancelAll();
    setDraft(null);
    setValidationErrors([]);
    onEditingChange(false);
  };

  const saveReport = async () => {
    if (!editing || !draft) return;
    const nextSpec: JsonRecord = { ...spec, path };
    const errors = validateSpec("ReportSpec", nextSpec, (key) => fieldLabel(key, t));
    if (errors.length > 0) {
      setValidationErrors(errors);
      return;
    }

    setSaving(true);
    try {
      const saved = await onSave({ ...draft, name: nextSpec.name, spec: nextSpec });
      if (uploader.files.length > 0) uploader.upload(saved);
      setDraft(null);
      setValidationErrors([]);
      onEditingChange(false);
    } finally {
      setSaving(false);
    }
  };

  const deleteReport = async () => {
    if (!canManage || !onDelete || !window.confirm(t("compliance.common.deleteConfirm"))) return;
    setDeleting(true);
    try {
      await onDelete(report);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Accordion
      disableGutters
      variant="outlined"
      expanded={editing || expanded}
      onChange={(_event, nextExpanded) => setExpanded(nextExpanded)}
    >
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" width="100%" mr={1}>
          <Typography variant="body2">{title}</Typography>
          {canManage && !initialEditing && (
            <Stack direction="row" onClick={(event) => event.stopPropagation()}>
              <Tooltip title={t("compliance.common.edit")}>
                <span>
                  <IconButton size="small" disabled={managementLocked} onClick={startEditing}>
                    <EditIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title={t("compliance.common.delete")}>
                <span>
                  <IconButton size="small" disabled={managementLocked || deleting} onClick={deleteReport}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </Stack>
          )}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        <Form key={`${reportId}-${editing}`} defaultValues={{ path: spec.path ?? "" }} onSubmit={() => undefined}>
          <PathWatcher onChange={setPath} />
          <SchemaReadOnlyProvider readOnly={!editing}>
            <SchemaForm
              schemaName="ReportSpec"
              value={spec}
              omit={["path"]}
              onChange={(nextSpec) => setDraft({ ...activeReport, spec: nextSpec })}
            />
            {editing && <PathInput source="path" uploader={editing ? uploader : undefined} disabled={!editing} />}
            {!editing && <FileInfoTree />}
          </SchemaReadOnlyProvider>
          {validationErrors.length > 0 && (
            <Alert severity="error" onClose={() => setValidationErrors([])}>
            {t("compliance.form.validationError")}
            <ul style={{ margin: "0.5em 0 0", paddingInlineStart: "1.25em" }}>
                {validationErrors.map((error) => (
                <li key={error}>{error}</li>
                ))}
            </ul>
            </Alert>
          )}
          {editing && (
            <Stack direction="row" spacing={1} justifyContent="flex-end" mt={2}>
              <Button startIcon={<CloseIcon />} onClick={cancelEditing} disabled={saving}>
                {t("compliance.common.cancel")}
              </Button>
              <Button variant="contained" startIcon={<SaveIcon />} onClick={saveReport} disabled={saving}>
                {t("compliance.common.save")}
              </Button>
            </Stack>
          )}
        </Form>
      </AccordionDetails>
    </Accordion>
  );
}
