import {
  Accordion, AccordionDetails, AccordionSummary, Box, Button, IconButton, Stack, Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import { createDefaultForSchema } from "../../schema/resolver";
import SchemaForm from "../schemaForm/SchemaForm";
import { useSchemaReadOnly } from "../schemaForm/SchemaReadOnlyContext";

interface ComplianceObjectivesWidgetProps {
  value: JsonRecord[];
  onChange: (value: JsonRecord[]) => void;
}

export default function ComplianceObjectivesWidget({ value, onChange }: ComplianceObjectivesWidgetProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();

  const updateObjective = (index: number, objective: JsonRecord) => {
    const next = [...value];
    next[index] = objective;
    onChange(next);
  };

  return (
    <Box>
      {value.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {t("compliance.form.emptyArray")}
        </Typography>
      )}
      <Stack spacing={1}>
        {value.map((objective, index) => {
          return (
            <Accordion key={index} disableGutters variant="outlined">
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ width: "100%", pr: 1 }}>
                  <Typography variant="body2">
                    {(objective.name as string) || t("compliance.form.item", { index: index + 1 })}
                  </Typography>
                  {!readOnly && (
                    <IconButton
                      component="span"
                      size="small"
                      aria-label={t("compliance.form.removeItem") as string}
                      onClick={(event) => {
                        event.stopPropagation();
                        onChange(value.filter((_, objectiveIndex) => objectiveIndex !== index));
                      }}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  )}
                </Stack>
              </AccordionSummary>
              <AccordionDetails>
                <Stack spacing={1.5}>
                  <SchemaForm
                    schemaName="ComplianceObjective"
                    value={objective}
                    onChange={(next) => updateObjective(index, next)}
                  />
                </Stack>
              </AccordionDetails>
            </Accordion>
          );
        })}
      </Stack>
      {!readOnly && (
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() => onChange([...value, createDefaultForSchema("ComplianceObjective")])}
          sx={{ mt: 1 }}
        >
          {t("compliance.form.addItem", { label: t("compliance.fields.objectives") })}
        </Button>
      )}
    </Box>
  );
}