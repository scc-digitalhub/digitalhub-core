import { useEffect, useState, type ComponentType } from "react";
import { Alert, Box, Button, CircularProgress, Divider, Stack } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import VisibilityIcon from "@mui/icons-material/Visibility";
import { useResourceContext, useTranslate } from "react-admin";
import type { ComplianceEntityKind, JsonRecord } from "../../types";
import { useComplianceServices } from "../../services/context";
import AgentAssistPanel from "../compliance/AgentAssistPanel";
import { SchemaReadOnlyProvider } from "../schemaForm/SchemaReadOnlyContext";

export interface ComplianceWidgetProps {
  /** project/dataset/model entity whose compliance specification this widget edits. */
  entity: any;
  resource: string;
  extension?: any;
  /** Called after the specification has been successfully saved. */
  onSaved?: (spec: JsonRecord) => void;
}

interface ComplianceEditorProps {
  spec: JsonRecord;
  onChange: (spec: JsonRecord) => void;
}

/**
 * Builds a self-contained "smart" compliance widget for one entity kind: fetches the entity
 * definition + compliance spec via the injected services (see `ComplianceServicesProvider`),
 * offers AI generate/extend assistance, renders the given (controlled) editor component, and
 * saves changes back through the services. Used to build `ProjectComplianceWidget`,
 * `DatasetComplianceWidget` and `ModelComplianceWidget`.
 */
export function createComplianceWidget(kind: ComplianceEntityKind, Editor: ComponentType<ComplianceEditorProps>) {
  return function ComplianceWidget({ entity, resource, extension: initialExt, onSaved }: ComplianceWidgetProps) {
    const t = useTranslate();

    const { complianceService } = useComplianceServices();
    const [spec, setSpec] = useState<JsonRecord>({});
    const [savedSpec, setSavedSpec] = useState<JsonRecord>({});
    const [extension, setExtension] = useState<any>(initialExt);
    const [editing, setEditing] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

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
        );
        return () => {
          cancelled = true;
        };
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [entity.id]);

    const save = async () => {
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

    if (loading) {
      return <CircularProgress />;
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
          <Editor spec={spec} onChange={setSpec} />
          </SchemaReadOnlyProvider>
        </Box>
      </Stack>
    );
  };
}
