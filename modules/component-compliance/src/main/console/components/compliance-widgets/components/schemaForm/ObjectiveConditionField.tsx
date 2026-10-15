import { useEffect } from "react";
import {
  Accordion, AccordionDetails, AccordionSummary, Box, Button, IconButton, MenuItem, Stack, TextField, Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AddIcon from "@mui/icons-material/Add";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import type { JSONSchemaNode } from "../../schema/types";
import { createDefaultForInlineSchema, resolveSchema } from "../../schema/resolver";
import SchemaField from "./SchemaField";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

const CONDITION_TYPES = ["threshold", "range", "categorical", "statistical"] as const;
type ConditionType = (typeof CONDITION_TYPES)[number];

interface ObjectiveConditionFieldProps {
  label: string;
  value: JsonRecord | undefined;
  onChange: (value: JsonRecord | undefined) => void;
  required?: boolean;
}

/**
 * Discriminated-union widget for ObjectiveCondition: exactly one of threshold/range/categorical/
 * statistical may be populated (schema minProperties/maxProperties: 1). The user first picks the
 * condition type, then only the relevant sub-fields are shown; switching type clears the others.
 */
export default function ObjectiveConditionField({ label, value, onChange, required }: ObjectiveConditionFieldProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();
  const { properties } = resolveSchema("ObjectiveCondition");
  const currentType = CONDITION_TYPES.find((type) => value?.[type] !== undefined);

  useEffect(() => {
    if (!readOnly && required && !value) {
      onChange({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly, required, value]);

  if (!value) {
    if (required) return null;
    return readOnly ? null : (
      <Button size="small" startIcon={<AddIcon />} onClick={() => onChange({})} sx={{ alignSelf: "flex-start" }}>
        {t("compliance.form.addSubsection", { label })}
      </Button>
    );
  }

  const selectType = (type: ConditionType) => {
    const schema = properties[type] as JSONSchemaNode;
    onChange({ [type]: createDefaultForInlineSchema(schema) });
  };

  const typeSchema = currentType ? (properties[currentType] as JSONSchemaNode) : undefined;
  const typeValue = currentType ? ((value[currentType] as JsonRecord) ?? {}) : undefined;

  return (
    <Accordion defaultExpanded disableGutters variant="outlined">
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ width: "100%", pr: 1 }}>
          <Typography variant="subtitle2">{label}</Typography>
          {!readOnly && !required && (
            <IconButton
              component="span"
              size="small"
              aria-label={t("compliance.form.removeSubsection", { label }) as string}
              onClick={(e) => {
                e.stopPropagation();
                onChange(undefined);
              }}
            >
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          )}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        <Stack spacing={2}>
          <TextField
            select
            fullWidth
            size="small"
            disabled={readOnly}
            label={t("compliance.objectiveCondition.typeLabel")}
            value={currentType ?? ""}
            onChange={(e) => selectType(e.target.value as ConditionType)}
          >
            {CONDITION_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {t(`compliance.objectiveCondition.${type}`)}
              </MenuItem>
            ))}
          </TextField>
          {currentType && typeSchema && (
            <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1, p: 1.5 }}>
              <Stack spacing={1.5}>
                {Object.entries(typeSchema.properties ?? {}).map(([key, prop]) => (
                  <SchemaField
                    key={key}
                    fieldKey={key}
                    prop={prop}
                    value={typeValue?.[key]}
                    required={(typeSchema.required ?? []).includes(key)}
                    onChange={(next) => onChange({ [currentType]: { ...typeValue, [key]: next } })}
                  />
                ))}
              </Stack>
            </Box>
          )}
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}
