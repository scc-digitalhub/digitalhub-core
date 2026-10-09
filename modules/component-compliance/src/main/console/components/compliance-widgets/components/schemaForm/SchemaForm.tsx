import { Grid, Stack } from "@mui/material";
import { getCustomValueTrigger, resolveSchema } from "../../schema/resolver";
import type { JsonRecord } from "../../types";
import SchemaField from "./SchemaField";

interface SchemaFormProps {
  schemaName: string;
  value: JsonRecord | undefined;
  onChange: (value: JsonRecord) => void;
  /** Property keys to skip (already surfaced elsewhere, e.g. in a different tab). */
  omit?: string[];
  /** If provided, renders only these keys, in this order (still via the schema's own property spec). */
  only?: string[];
}

/** Renders every property of a registered schema using <SchemaField>, in a vertical stack. */
export default function SchemaForm({ schemaName, value, onChange, omit = [], only }: SchemaFormProps) {
  const resolved = resolveSchema(schemaName);
  const safeValue = value ?? {};
  const keys = (only ?? Object.keys(resolved.properties)).filter((k) => !omit.includes(k) && resolved.properties[k]);

  return (
    <Grid container spacing={1.5}>

      {keys.map((key) => {
        const prop = resolved.properties[key];
        const size = Math.floor(((prop as any).columns || 12));
        const trigger = getCustomValueTrigger(resolved.properties, key);
        if (trigger && !trigger.sentinels.includes(safeValue[trigger.baseKey] as string)) {
          return null;
        }
        return (
          <Grid key={key} size={{ xs: 12, md: size }}>
          <SchemaField
            key={key}
            fieldKey={key}
            prop={resolved.properties[key]}
            value={safeValue[key]}
            required={resolved.required.includes(key)}
            onChange={(next) => onChange({ ...safeValue, [key]: next })}
          />
          </Grid>
        );
      })}
    </Grid>
  );
}
