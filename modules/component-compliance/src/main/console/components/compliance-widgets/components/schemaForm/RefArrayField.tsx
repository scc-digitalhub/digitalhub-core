import { Accordion, AccordionDetails, AccordionSummary, Box, Button, IconButton, Stack, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AddIcon from "@mui/icons-material/Add";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import { createDefaultForSchema } from "../../schema/resolver";
import SchemaForm from "./SchemaForm";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface RefArrayFieldProps {
  label: string;
  itemSchemaName: string;
  value: JsonRecord[];
  onChange: (value: JsonRecord[]) => void;
}

function itemTitle(item: JsonRecord, index: number): string {
  return (item.name as string) || (item.title as string) || (item.id as string) || `#${index + 1}`;
}

/** Renders an array of `$ref` objects as a list of add/remove-able accordions ("subsections"). */
export default function RefArrayField({ label, itemSchemaName, value, onChange }: RefArrayFieldProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();

  const updateItem = (index: number, next: JsonRecord) => {
    const copy = [...value];
    copy[index] = next;
    onChange(copy);
  };

  const removeItem = (index: number) => {
    onChange(value.filter((_, i) => i !== index));
  };

  const addItem = () => {
    onChange([...value, createDefaultForSchema(itemSchemaName)]);
  };

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {label}
      </Typography>
      {value.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {t("compliance.form.emptyArray")}
        </Typography>
      )}
      <Stack spacing={1}>
        {value.map((item, index) => (
          <Accordion key={index} disableGutters variant="outlined">
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ width: "100%", pr: 1 }}>
                <Typography variant="body2">{itemTitle(item, index)}</Typography>
                {!readOnly && <IconButton
                  component="span"
                  size="small"
                  aria-label={t("compliance.form.removeItem") as string}
                  onClick={(e) => {
                    e.stopPropagation();
                    removeItem(index);
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>}
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <SchemaForm schemaName={itemSchemaName} value={item} onChange={(next) => updateItem(index, next)} />
            </AccordionDetails>
          </Accordion>
        ))}
      </Stack>
      {!readOnly && (
        <Button size="small" startIcon={<AddIcon />} onClick={addItem} sx={{ mt: 1 }}>
          {t("compliance.form.addItem", { label })}
        </Button>
      )}
    </Box>
  );
}
