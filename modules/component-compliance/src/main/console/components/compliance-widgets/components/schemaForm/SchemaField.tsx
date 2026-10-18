import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { Box, Switch, FormControlLabel, IconButton, Stack, TextField, Tooltip } from "@mui/material";
import type { ReactNode } from "react";
import { useTranslate } from "react-admin";
import type { JSONSchemaNode, SchemaRef } from "../../schema/types";
import { isSchemaRef } from "../../schema/types";
import type { JsonRecord } from "../../types";
import { fieldLabel, isLongTextKey } from "./fieldHelpers";
import { CustomEnumTextField, EnumSelect, MultiEnumSelect } from "./EnumSelect";
import TagsField from "./TagsField";
import JsonField from "./JsonField";
import RefObjectField from "./RefObjectField";
import RefArrayField from "./RefArrayField";
import InlineObjectField from "./InlineObjectField";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";
import { getCustomFieldWidget } from "./customWidgets";

interface SchemaFieldProps {
  fieldKey: string;
  prop: JSONSchemaNode | SchemaRef;
  formValue: JsonRecord;
  value: unknown;
  onChange: (value: unknown) => void;
  required?: boolean;
  customValue?: {
    value: string | undefined;
    sentinel: string;
    onChange: (baseValue: string | undefined, customValue: string | undefined) => void;
  };
}

function FieldWithExplanation({ explanation, children }: { explanation?: string; children: ReactNode }) {
  if (!explanation) return children;

  return (
    <Stack direction="row" alignItems="flex-start" spacing={0.5} width="100%">
      <Box minWidth={0} flex={1}>{children}</Box>
      <Tooltip title={explanation} arrow>
        <IconButton size="small" aria-label={explanation} sx={{ mt: 0.25 }}>
          <InfoOutlinedIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Stack>
  );
}

/**
 * Renders a single schema property with the appropriate MUI control, dispatching on the
 * property shape (enum / $ref object / array / inline object / primitive). This is the single
 * building block reused both by <SchemaForm> (renders every property of a schema) and by
 * hand-composed tab/accordion layouts that cherry-pick individual fields (see components/compliance).
 */
export default function SchemaField({ fieldKey, prop, formValue, value, onChange, required, customValue }: SchemaFieldProps) {
  const translate = useTranslate();
  const readOnly = useSchemaReadOnly();
  const label = fieldLabel(fieldKey, translate);
  const CustomWidget = prop.widget ? getCustomFieldWidget(prop.widget) : null;
  const description = prop.description
    ? translate(prop.description, { _: prop.description })
    : undefined;
  const explanation = prop.explanation
    ? translate(prop.explanation, { _: prop.explanation })
    : undefined;
  const translatedProp = description ? { ...prop, description } : prop;
  const withExplanation = (field: ReactNode) => (
    <FieldWithExplanation explanation={explanation}>{field}</FieldWithExplanation>
  );

  if (CustomWidget) {
    return withExplanation(<CustomWidget label={label} prop={translatedProp} formValue={formValue} value={value} onChange={onChange} required={required} />);
  }

  if (isSchemaRef(prop)) {
    return withExplanation(
      <RefObjectField
        label={label}
        schemaName={prop.$ref}
        value={value as JsonRecord | undefined}
        onChange={(next) => onChange(next)}
        required={required}
      />,
    );
  }

  if (prop.enum) {
    if (customValue) {
      return withExplanation(
        <CustomEnumTextField
          label={label}
          options={prop.enum}
          value={value as string | undefined}
          customValue={customValue.value}
          sentinel={customValue.sentinel}
          onCustomChange={customValue.onChange}
          required={required}
          helperText={description}
        />,
      );
    }
    return withExplanation(
      <EnumSelect
        label={label}
        options={prop.enum}
        value={value as string | undefined}
        onChange={onChange}
        required={required}
        helperText={description}
        badgeColors={prop.enumColors}
      />,
    );
  }

  if (prop.type === "array") {
    const items = prop.items;
    if (items && isSchemaRef(items)) {
      return withExplanation(
        <RefArrayField
          label={label}
          itemSchemaName={items.$ref}
          value={(value as JsonRecord[] | undefined) ?? []}
          onChange={(next) => onChange(next)}
        />,
      );
    }
    if (items && !isSchemaRef(items) && items.enum) {
      return withExplanation(
        <MultiEnumSelect
          label={label}
          options={items.enum}
          value={(value as string[] | undefined) ?? []}
          onChange={onChange}
          helperText={description}
        />,
      );
    }
    return withExplanation(<TagsField label={label} value={(value as string[] | undefined) ?? []} onChange={onChange} helperText={description} />);
  }

  if (prop.type === "object") {
    if (prop.properties) {
      return withExplanation(
        <InlineObjectField
          label={label}
          schema={prop}
          value={(value as JsonRecord | undefined) ?? {}}
          onChange={(next) => onChange(next)}
        />,
      );
    }
    return withExplanation(<JsonField label={label} value={value as JsonRecord | undefined} onChange={onChange} />);
  }

  if (prop.type === "boolean") {
    return withExplanation(
      <FormControlLabel
        control={<Switch checked={!!value} disabled={readOnly} onChange={(e) => onChange(e.target.checked)} />}
        label={label}
      />,
    );
  }

  if (prop.type === "number" || prop.type === "integer") {
    return withExplanation(
      <TextField
        label={label}
        type="number"
        size="small"
        fullWidth
        disabled={readOnly}
        required={required}
        helperText={description}
        value={value === undefined || value === null ? "" : (value as number)}
        onChange={(e) => onChange(e.target.value === "" ? undefined : Number(e.target.value))}
      />,
    );
  }

  if (prop.format === "date-time") {
    return withExplanation(
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
      />,
    );
  }

  return withExplanation(
    <TextField
      label={label}
      size="small"
      fullWidth
      disabled={readOnly}
      required={required}
      multiline={isLongTextKey(fieldKey)}
      minRows={isLongTextKey(fieldKey) ? 2 : undefined}
      helperText={description}
      value={(value as string) ?? ""}
      onChange={(e) => onChange(e.target.value)}
    />,
  );
}
