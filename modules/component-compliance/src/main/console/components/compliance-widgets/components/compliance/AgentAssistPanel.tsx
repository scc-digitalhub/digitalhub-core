import { useEffect, useState } from "react";
import {
  Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Stack, Typography,
} from "@mui/material";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { useDataProvider, useTranslate } from "react-admin";
import type { ComplianceEntityKind, JsonRecord } from "../../types";
import { useComplianceServices } from "../../services/context";
import type { AgentResult } from "../../services/types";
import { mergeFillGaps } from "../../utils/merge";
import { useRootSelector } from "@dslab/ra-root-selector";

interface AgentAssistPanelProps {
  kind: ComplianceEntityKind;
  entity: JsonRecord;
  currentSpec: JsonRecord;
  onApply: (nextSpec: JsonRecord) => void;
}

type Mode = "generate" | "extend" | null;

/**
 * "Agent interaction" panel: lets the user ask the compliance agent (injected via
 * `ComplianceServicesProvider`) to draft a brand new specification from the entity definition, or
 * to extend/fill gaps in the existing one. Suggestions are always previewed (with the agent's
 * rationale) before being applied.
 */
export default function AgentAssistPanel({ kind, entity, currentSpec, onApply }: AgentAssistPanelProps) {
  const t = useTranslate();
  const dataProvider = useDataProvider();
  const { agentService } = useComplianceServices();
  const [mode, setMode] = useState<Mode>(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<AgentResult | null>(null);
  const [error, setError] = useState(false);

  const { root: projectId } = useRootSelector();
  const [project, setProject] = useState<JsonRecord | null>(null);
  const [projectSpec, setProjectSpec] = useState<JsonRecord | null>(null);

  useEffect(() => {
      if (!dataProvider || !projectId) return;

      dataProvider.getOne('projects', { id: projectId }).then(res => {
          if (res.data) {
              setProject(res.data);
              const ext = res.data.extensions?.find(e => e.kind === 'ai-compliance');
              setProjectSpec(ext?.spec ?? null);
          }
      });

  }, [dataProvider, projectId]);

  const runAgent = async (nextMode: "generate" | "extend") => {
    setMode(nextMode);
    setLoading(true);
    setError(false);
    setResult(null);
    try {
      const res =await agentService.extendComplianceSpec(kind, entity, currentSpec ?? {}, project, projectSpec);
      setResult(res);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  const close = () => {
    setMode(null);
    setResult(null);
    setError(false);
  };

  const apply = () => {
    if (!result) return;
    const next = mode === "generate" ? result.patch : mergeFillGaps(currentSpec ?? {}, result.patch);
    onApply(next);
    close();
  };

  return (
    <Box>
      <Stack direction="row" spacing={1}>
        {/* <Button variant="outlined" size="small" startIcon={<AutoAwesomeIcon />} onClick={() => runAgent("generate")}>
          {t("compliance.agent.generate")}
        </Button> */}
        <Button variant="outlined" size="small" startIcon={<AutoAwesomeIcon />} onClick={() => runAgent("extend")}>
          {t("compliance.agent.extend")}
        </Button>
      </Stack>

      <Dialog open={mode !== null} onClose={close} maxWidth="md" fullWidth>
        <DialogTitle>{mode === "generate" ? t("compliance.agent.dialogTitleGenerate") : t("compliance.agent.dialogTitleExtend")}</DialogTitle>
        <DialogContent dividers>
          {loading && (
            <Stack alignItems="center" spacing={2} sx={{ py: 4 }}>
              <CircularProgress size={32} />
              <Typography color="text.secondary">{mode === "generate" ? t("compliance.agent.generating") : t("compliance.agent.extending")}</Typography>
            </Stack>
          )}
          {!loading && error && <Alert severity="error">{t("compliance.agent.errorNotice")}</Alert>}
          {!loading && result && (
            <Stack spacing={2}>
              <Alert severity="info" icon={<AutoAwesomeIcon fontSize="inherit" />}>
                <Typography variant="subtitle2" gutterBottom>
                  {t("compliance.agent.narrative")}
                </Typography>
                <Typography variant="body2">{result.narrative}</Typography>
              </Alert>
              <Typography variant="caption" color="text.secondary">
                {t("compliance.agent.previewHint")}
              </Typography>
              <Box
                component="pre"
                sx={{
                  m: 0,
                  p: 1.5,
                  bgcolor: "grey.100",
                  borderRadius: 1,
                  fontSize: 12,
                  maxHeight: 320,
                  overflow: "auto",
                }}
              >
                {JSON.stringify(result.patch, null, 2)}
              </Box>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={close}>{t("compliance.common.discard")}</Button>
          <Button variant="contained" onClick={apply} disabled={loading || !result}>
            {t("compliance.agent.applyAndMerge")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
