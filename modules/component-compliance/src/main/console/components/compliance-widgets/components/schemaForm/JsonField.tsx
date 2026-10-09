import { useState } from "react";
import { Box, TextField, Typography } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface JsonFieldProps {
  label: string;
  value: JsonRecord | undefined;
  onChange: (value: JsonRecord) => void;
}

/** Free-form JSON editor for schema-less `object` properties (e.g. DocumentSection.specification). */
export default function JsonField({ label, value, onChange }: JsonFieldProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();
  const [text, setText] = useState(() => JSON.stringify(value ?? {}, null, 2));
  const [error, setError] = useState<string | null>(null);

  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <TextField
        fullWidth
        multiline
        minRows={4}
        size="small"
        disabled={readOnly}
        value={text}
        error={!!error}
        helperText={error ?? " "}
        onChange={(e) => {
          setText(e.target.value);
          try {
            const parsed = JSON.parse(e.target.value || "{}");
            setError(null);
            onChange(parsed);
          } catch {
            setError(t("compliance.form.invalidJson") as string);
          }
        }}
        sx={{ fontFamily: "monospace" }}
      />
    </Box>
  );
}
