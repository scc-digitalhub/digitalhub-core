import { Box, Grid, Stack, Typography } from "@mui/material";
import type { JSONSchemaNode } from "../../schema/types";
import type { JsonRecord } from "../../types";
import { getCustomValueCompanion, getCustomValueTrigger } from "../../schema/resolver";
import SchemaField from "./SchemaField";

interface InlineObjectFieldProps {
  label: string;
  schema: JSONSchemaNode;
  value: JsonRecord;
  onChange: (value: JsonRecord) => void;
}

/** Renders an inline (non-`$ref`) nested object, e.g. ProjectComplianceSpec.scope or
 * ObjectiveCondition.threshold — grouped visually but without its own accordion, since these
 * tend to be small, always-relevant field clusters. */
export default function InlineObjectField({ label, schema, value, onChange }: InlineObjectFieldProps) {
  const properties = schema.properties ?? {};
  const required = schema.required ?? [];

  return (
    <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1, p: 1.5 }}>
      <Typography variant="subtitle2" gutterBottom>
        {label}
      </Typography>
      <Grid container spacing={1.5}>
        {Object.entries(properties).map(([key, prop]) => {
          const size = Math.floor(((prop as any).columns || 12));
          const trigger = getCustomValueTrigger(properties, key);
          if (trigger) {
            return null;
          }
          const companion = getCustomValueCompanion(properties, key);
          return (
            <Grid key={key} size={{ xs: 12, md: size }}>
              <SchemaField
              key={key}
              fieldKey={key}
              prop={prop}
              value={value?.[key]}
              required={required.includes(key)}
              onChange={(next) => onChange({ ...value, [key]: next })}
              customValue={companion ? {
                value: value?.[companion.valueKey] as string | undefined,
                sentinel: companion.sentinel,
                onChange: (baseValue, customValue) => onChange({
                  ...value,
                  [key]: baseValue,
                  [companion.valueKey]: customValue,
                }),
              } : undefined}
            />
            </Grid>
          );
        })}
      </Grid>
    </Box>
  );
}
