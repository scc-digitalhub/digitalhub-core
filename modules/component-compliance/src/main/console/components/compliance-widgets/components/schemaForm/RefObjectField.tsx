import { useEffect } from "react";
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, IconButton, Stack, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AddIcon from "@mui/icons-material/Add";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import { createDefaultForSchema } from "../../schema/resolver";
import SchemaForm from "./SchemaForm";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface RefObjectFieldProps {
  label: string;
  schemaName: string;
  value: JsonRecord | undefined;
  onChange: (value: JsonRecord | undefined) => void;
  required?: boolean;
  description?: string;
}

/** Renders a single nested `$ref` object property as a collapsible "subsection" accordion. */
export default function RefObjectField({ label, schemaName, value, onChange, required, description }: RefObjectFieldProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();

  // Required nested objects are always materialized so the user can fill them in immediately.
  useEffect(() => {
    if (!readOnly && required && !value) {
      onChange(createDefaultForSchema(schemaName));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly, required, value, schemaName]);

  if (!value) {
    if (required) {
      return null;
    }
    return readOnly ? null : (
      <Button size="small" startIcon={<AddIcon />} onClick={() => onChange(createDefaultForSchema(schemaName))} sx={{ alignSelf: "flex-start" }}>
        {t("compliance.form.addSubsection", { label })}
      </Button>
    );
  }

  return (
    <Accordion defaultExpanded disableGutters variant="outlined">
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ width: "100%", pr: 1 }}>
          <Box>
            <Typography variant="subtitle2">{label}</Typography>
            {description && (
              <Typography variant="caption" color="text.secondary">
                {description}
              </Typography>
            )}
          </Box>
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
        <SchemaForm schemaName={schemaName} value={value} onChange={(next) => onChange(next)} />
      </AccordionDetails>
    </Accordion>
  );
}
