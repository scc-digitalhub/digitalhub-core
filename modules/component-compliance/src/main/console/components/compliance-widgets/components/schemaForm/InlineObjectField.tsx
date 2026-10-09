import { Box, Stack, Typography } from "@mui/material";
import type { JSONSchemaNode } from "../../schema/types";
import type { JsonRecord } from "../../types";
import { getCustomValueTrigger } from "../../schema/resolver";
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
      <Stack spacing={1.5}>
        {Object.entries(properties).map(([key, prop]) => {
          const trigger = getCustomValueTrigger(properties, key);
          if (trigger && !trigger.sentinels.includes(value?.[trigger.baseKey] as string)) {
            return null;
          }
          return (
            <SchemaField
              key={key}
              fieldKey={key}
              prop={prop}
              value={value?.[key]}
              required={required.includes(key)}
              onChange={(next) => onChange({ ...value, [key]: next })}
            />
          );
        })}
      </Stack>
    </Box>
  );
}
