import { Autocomplete, Chip, MenuItem, TextField } from "@mui/material";
import { useTranslate } from "react-admin";
import { enumLabel } from "./fieldHelpers";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface EnumSelectProps {
  label: string;
  options: string[];
  value: string | undefined;
  onChange: (value: string) => void;
  required?: boolean;
  helperText?: string;
  badgeColors?: Record<string, "default" | "success" | "error" | "warning">;
}

interface CustomEnumTextFieldProps {
  label: string;
  options: string[];
  value: string | undefined;
  customValue: string | undefined;
  sentinel: string;
  onCustomChange: (baseValue: string | undefined, customValue: string | undefined) => void;
  required?: boolean;
  helperText?: string;
}

export function EnumSelect({ label, options, value, onChange, required, helperText, badgeColors }: EnumSelectProps) {
  const translate = useTranslate();
  const readOnly = useSchemaReadOnly();
  return (
    <TextField
      select
      fullWidth
      size="small"
      disabled={readOnly}
      label={label}
      required={required}
      value={value ?? ""}
      helperText={helperText}
      onChange={(e) => onChange(e.target.value)}
      SelectProps={badgeColors ? {
        renderValue: (selected) => {
          const option = selected as string;
          return <Chip size="medium" label={enumLabel(option, translate)} color={badgeColors[option] ?? "default"} />;
        },
      } : undefined}
    >
      {options.map((option) => (
        <MenuItem key={option} value={option}>
          {badgeColors ? (
            <Chip size="small" label={enumLabel(option, translate)} color={badgeColors[option] ?? "default"} />
          ) : enumLabel(option, translate)}
        </MenuItem>
      ))}
    </TextField>
  );
}

export function CustomEnumTextField({
  label,
  options,
  value,
  customValue,
  sentinel,
  onCustomChange,
  required,
  helperText,
}: CustomEnumTextFieldProps) {
  const translate = useTranslate();
  const readOnly = useSchemaReadOnly();
  const predefinedOptions = options.filter((option) => option !== sentinel && option !== "OTHER" && option !== "CUSTOM");
  const displayedValue = value === sentinel ? (customValue ?? "") : (value ?? "");

  return (
    <Autocomplete
      freeSolo
      size="small"
      disabled={readOnly}
      options={predefinedOptions}
      value={displayedValue}
      getOptionLabel={(option) => enumLabel(option, translate)}
      onChange={(_event, nextValue) => {
        if (!nextValue) {
          onCustomChange(undefined, undefined);
        } else if (predefinedOptions.includes(nextValue)) {
          onCustomChange(nextValue, undefined);
        } else {
          onCustomChange(sentinel, nextValue);
        }
      }}
      renderInput={(params) => (
        <TextField {...params} label={label} required={required} helperText={helperText} />
      )}
    />
  );
}

interface MultiEnumSelectProps {
  label: string;
  options: string[];
  value: string[];
  onChange: (value: string[]) => void;
  helperText?: string;
}

export function MultiEnumSelect({ label, options, value, onChange, helperText }: MultiEnumSelectProps) {
  const translate = useTranslate();
  const readOnly = useSchemaReadOnly();
  return (
    <Autocomplete
      multiple
      size="small"
      disabled={readOnly}
      options={options}
      value={value}
      getOptionLabel={(option) => enumLabel(option, translate)}
      onChange={(_e, newValue) => onChange(newValue)}
      renderTags={(tagValue, getTagProps) =>
        tagValue.map((option, index) => {
          const { key, ...rest } = getTagProps({ index });
          return <Chip size="small" label={enumLabel(option, translate)} key={key} {...rest} />;
        })
      }
      renderInput={(params) => <TextField {...params} label={label} helperText={helperText} />}
    />
  );
}
