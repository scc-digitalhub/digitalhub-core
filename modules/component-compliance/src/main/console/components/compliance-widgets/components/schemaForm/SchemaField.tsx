import { Switch, FormControlLabel, TextField } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JSONSchemaNode, SchemaRef } from "../../schema/types";
import { isSchemaRef } from "../../schema/types";
import type { JsonRecord } from "../../types";
import { fieldLabel, isLongTextKey } from "./fieldHelpers";
import { EnumSelect, MultiEnumSelect } from "./EnumSelect";
import TagsField from "./TagsField";
import JsonField from "./JsonField";
import RefObjectField from "./RefObjectField";
import RefArrayField from "./RefArrayField";
import InlineObjectField from "./InlineObjectField";
import ObjectiveConditionField from "./ObjectiveConditionField";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface SchemaFieldProps {
  fieldKey: string;
  prop: JSONSchemaNode | SchemaRef;
  value: unknown;
  onChange: (value: unknown) => void;
  required?: boolean;
}

/**
 * Renders a single schema property with the appropriate MUI control, dispatching on the
 * property shape (enum / $ref object / array / inline object / primitive). This is the single
 * building block reused both by <SchemaForm> (renders every property of a schema) and by
 * hand-composed tab/accordion layouts that cherry-pick individual fields (see components/compliance).
 */
export default function SchemaField({ fieldKey, prop, value, onChange, required }: SchemaFieldProps) {
  const translate = useTranslate();
  const readOnly = useSchemaReadOnly();
  const label = fieldLabel(fieldKey, translate);

  if (isSchemaRef(prop)) {
    // ObjectiveCondition is a discriminated union (exactly one of threshold/range/categorical/
    // statistical) and needs a dedicated type-selector widget instead of the generic form.
    if (prop.$ref === "ObjectiveCondition") {
      return (
        <ObjectiveConditionField
          label={label}
          value={value as JsonRecord | undefined}
          onChange={(next) => onChange(next)}
          required={required}
        />
      );
    }
    return (
      <RefObjectField
        label={label}
        schemaName={prop.$ref}
        value={value as JsonRecord | undefined}
        onChange={(next) => onChange(next)}
        required={required}
      />
    );
  }

  if (prop.enum) {
    return (
      <EnumSelect
        label={label}
        options={prop.enum}
        value={value as string | undefined}
        onChange={onChange}
        required={required}
        helperText={prop.description}
      />
    );
  }

  if (prop.type === "array") {
    const items = prop.items;
    if (items && isSchemaRef(items)) {
      return (
        <RefArrayField
          label={label}
          itemSchemaName={items.$ref}
          value={(value as JsonRecord[] | undefined) ?? []}
          onChange={(next) => onChange(next)}
        />
      );
    }
    if (items && !isSchemaRef(items) && items.enum) {
      return (
        <MultiEnumSelect
          label={label}
          options={items.enum}
          value={(value as string[] | undefined) ?? []}
          onChange={onChange}
          helperText={prop.description}
        />
      );
    }
    return <TagsField label={label} value={(value as string[] | undefined) ?? []} onChange={onChange} helperText={prop.description} />;
  }

  if (prop.type === "object") {
    if (prop.properties) {
      return (
        <InlineObjectField
          label={label}
          schema={prop}
          value={(value as JsonRecord | undefined) ?? {}}
          onChange={(next) => onChange(next)}
        />
      );
    }
    return <JsonField label={label} value={value as JsonRecord | undefined} onChange={onChange} />;
  }

  if (prop.type === "boolean") {
    return (
      <FormControlLabel
        control={<Switch checked={!!value} disabled={readOnly} onChange={(e) => onChange(e.target.checked)} />}
        label={label}
      />
    );
  }

  if (prop.type === "number" || prop.type === "integer") {
    return (
      <TextField
        label={label}
        type="number"
        size="small"
        fullWidth
        disabled={readOnly}
        required={required}
        helperText={prop.description}
        value={value === undefined || value === null ? "" : (value as number)}
        onChange={(e) => onChange(e.target.value === "" ? undefined : Number(e.target.value))}
      />
    );
  }

  if (prop.format === "date-time") {
    return (
      <TextField
        label={label}
        type="datetime-local"
        size="small"
        fullWidth
        disabled={readOnly}
        required={required}
        InputLabelProps={{ shrink: true }}
        value={value ? String(value).slice(0, 16) : ""}
        onChange={(e) => onChange(e.target.value ? new Date(e.target.value).toISOString() : undefined)}
      />
    );
  }

  return (
    <TextField
      label={label}
      size="small"
      fullWidth
      disabled={readOnly}
      required={required}
      multiline={isLongTextKey(fieldKey)}
      minRows={isLongTextKey(fieldKey) ? 2 : undefined}
      helperText={prop.description}
      value={(value as string) ?? ""}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}
