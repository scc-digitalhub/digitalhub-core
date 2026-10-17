import { useEffect, useState, type ComponentType } from "react";
import { Alert, Box, Button, CircularProgress, Divider, Stack } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import VisibilityIcon from "@mui/icons-material/Visibility";
import { RecordContextProvider, useTranslate } from "react-admin";
import { v4 as uuidv4 } from "uuid";
import type { ComplianceEntityKind, JsonRecord } from "../../types";
import { useComplianceServices } from "../../services/context";
import { createDefaultForSchema } from "../../schema/resolver";
import { validateSpec } from "../../schema/validateSpec";
import { fieldLabel } from "../schemaForm/fieldHelpers";
import AgentAssistPanel from "../compliance/AgentAssistPanel";
import { SchemaReadOnlyProvider } from "../schemaForm/SchemaReadOnlyContext";
import { ProjectComplianceProvider } from "../compliance/ProjectComplianceContext";

export interface ComplianceWidgetProps {
  /** project/dataset/model entity whose compliance specification this widget edits. */
  entity: any;
  resource: string;
  /** Called after the specification has been successfully saved. */
  onSaved?: (spec: JsonRecord) => void;
}

interface ComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

const SPEC_SCHEMA_NAME: Record<ComplianceEntityKind, string> = {
  project: "ProjectComplianceSpec",
  dataset: "DataComplianceSpec",
  model: "ModelComplianceSpec",
  report: "ComplianceReportSpec",
};

/**
 * Builds a self-contained "smart" compliance widget for one entity kind: fetches the entity
 * definition + compliance spec via the injected services (see `ComplianceServicesProvider`),
 * offers AI generate/extend assistance, renders the given (controlled) editor component, and
 * saves changes back through the services. Used to build `ProjectComplianceWidget`,
 * `DatasetComplianceWidget` and `ModelComplianceWidget`.
 */
export function createComplianceWidget(kind: ComplianceEntityKind, Editor: ComponentType<ComplianceEditorProps>) {
  return function ComplianceWidget({ entity, resource, onSaved }: ComplianceWidgetProps) {
    const t = useTranslate();

    const { complianceService } = useComplianceServices();
    const [spec, setSpec] = useState<JsonRecord>({});
    const [savedSpec, setSavedSpec] = useState<JsonRecord>({});
    const [extension, setExtension] = useState<any>(null);
    const [editing, setEditing] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [enabling, setEnabling] = useState(false);
    const [enableError, setEnableError] = useState(false);
    const [validationErrors, setValidationErrors] = useState<string[]>([]);

    useEffect(() => {
      if (extension) {
        setSpec(extension.spec);
        setSavedSpec(extension.spec);
        setEditing(false);
        setLoading(false);
      } else {
        let cancelled = false;
        setLoading(true);
        complianceService.getComplianceSpec(kind, resource, entity.id).then(
          (fetchedExtension) => {
            if (cancelled) return;
            if (fetchedExtension) {
                setExtension(fetchedExtension);
                setSpec(fetchedExtension.spec as JsonRecord);
                setSavedSpec(fetchedExtension.spec as JsonRecord);
            }

            setEditing(false);
            setLoading(false);
          },
          () => {
            if (cancelled) return;
            setExtension(null);
            setSpec({});
            setSavedSpec({});
            setEditing(false);
            setLoading(false);
          },
        );
        return () => {
          cancelled = true;
        };
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [entity.id]);

    const save = async () => {
      const errors = validateSpec(SPEC_SCHEMA_NAME[kind], spec, (key) => fieldLabel(key, t));
      if (errors.length > 0) {
        setValidationErrors(errors);
        return;
      }
      setValidationErrors([]);
      setSaving(true);
      try {
        extension.spec = spec;
        const saved = await complianceService.saveComplianceSpec(kind, resource, entity.id, extension);
        setExtension(saved);
        setSpec(saved.spec as JsonRecord);
        setSavedSpec(saved.spec as JsonRecord);
        setEditing(false);
        onSaved?.(saved);
      } finally {
        setSaving(false);
      }
    };

    const enableProjectCompliance = async () => {
      const minimalSpec = createDefaultForSchema("ProjectComplianceSpec");
      const newExtension: JsonRecord = {
        id: uuidv4(),
        name: "ai-compliance",
        kind: "ai-compliance",
        project: entity.id,
        spec: minimalSpec,
      };

      setEnabling(true);
      setEnableError(false);
      try {
        setExtension(newExtension);
        setSpec(minimalSpec);
        setEditing(true);
      } catch {
        setEnableError(true);
      } finally {
        setEnabling(false);
      }
    };

    if (loading) {
      return <CircularProgress />;
    }

    if (kind === "project" && Object.keys(spec).length === 0) {
      return (
        <Alert
          severity={enableError ? "error" : "info"}
          action={(
            <Button
              color="inherit"
              size="small"
              startIcon={enabling ? <CircularProgress color="inherit" size={16} /> : <AddIcon />}
              disabled={enabling}
              onClick={enableProjectCompliance}
            >
              {t("compliance.pages.compliance.create")}
            </Button>
          )}
        >
          {enableError ? t("compliance.agent.errorNotice") : t("compliance.pages.compliance.empty")}
        </Alert>
      );
    }

    return (
      <Stack spacing={3}>
        {editing ? (
          <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" flexWrap="wrap" gap={2}>
              <AgentAssistPanel kind={kind} entity={entity} currentSpec={spec} onApply={setSpec} />
              <Stack direction="row" spacing={1}>
                <Button
                  startIcon={<VisibilityIcon />}
                  onClick={() => {
                    setSpec(savedSpec);
                    setValidationErrors([]);
                    setEditing(false);
                  }}
                  disabled={saving}
                >
                  {t("compliance.common.cancel")}
                </Button>
                <Button variant="contained" startIcon={<SaveIcon />} onClick={save} disabled={saving}>
                  {t("compliance.common.save")}
                </Button>
              </Stack>
            </Stack>
            <Divider />
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
          </>
        ) : (
          <Stack direction="row" justifyContent="flex-end">
            <Button variant="contained" startIcon={<EditIcon />} onClick={() => setEditing(true)}>
              {t("compliance.widget.edit")}
            </Button>
          </Stack>
        )}
        {Object.keys(spec).length === 0 && <Alert severity="info">{t("compliance.agent.generateCta")}</Alert>}
        <Box sx={{ minWidth: 0, width: "100%" }}>
          <SchemaReadOnlyProvider readOnly={!editing}>
            {kind === "project" ? (
              <RecordContextProvider value={entity}>
              <Editor
                spec={spec}
                onChange={setSpec}
              />
              </RecordContextProvider>
            ) : (
              <ProjectComplianceProvider>
                <Editor
                  spec={spec}
                  onChange={setSpec}
                />
              </ProjectComplianceProvider>
            )}
          </SchemaReadOnlyProvider>
        </Box>
      </Stack>
    );
  };
}
