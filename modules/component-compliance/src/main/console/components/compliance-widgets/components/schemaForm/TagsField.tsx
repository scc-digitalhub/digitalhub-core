import { useState } from "react";
import { Autocomplete, Chip, TextField } from "@mui/material";
import { useTranslate } from "react-admin";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface TagsFieldProps {
  label: string;
  value: string[];
  onChange: (value: string[]) => void;
  helperText?: string;
}

/** Free-form multi-value text input (chips), used for plain `string[]` schema properties. */
export default function TagsField({ label, value, onChange, helperText }: TagsFieldProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();
  const [inputValue, setInputValue] = useState("");
  return (
    <Autocomplete
      multiple
      freeSolo
      disabled={readOnly}
      options={[]}
      value={value}
      inputValue={inputValue}
      onInputChange={(_e, newInput) => setInputValue(newInput)}
      onChange={(_e, newValue) => onChange(newValue as string[])}
      renderTags={(tagValue, getTagProps) =>
        tagValue.map((option, index) => {
          const { key, ...rest } = getTagProps({ index });
          return <Chip variant="outlined" size="small" label={option} key={key} {...rest} />;
        })
      }
      renderInput={(params) => (
        <TextField {...params} label={label} helperText={helperText} placeholder={t("compliance.common.add") as string} size="small" />
      )}
    />
  );
}
