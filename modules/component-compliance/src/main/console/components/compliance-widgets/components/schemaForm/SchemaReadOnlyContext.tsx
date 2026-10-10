import { createContext, useContext, type ReactNode } from "react";
import { Box } from "@mui/material";

const SchemaReadOnlyContext = createContext(false);

export function SchemaReadOnlyProvider({ readOnly, children }: { readOnly: boolean; children: ReactNode }) {
  return (
    <SchemaReadOnlyContext.Provider value={readOnly}>
      <Box
        sx={readOnly ? {
          display: "contents",
          "& .MuiInputBase-root.Mui-disabled": { color: "text.primary" },
          "& .MuiInputBase-input.Mui-disabled": { WebkitTextFillColor: "currentColor" },
          "& .MuiFormLabel-root.Mui-disabled": { color: "text.secondary" },
          "& .MuiFormControlLabel-label.Mui-disabled": { color: "text.primary" },
          "& .MuiAutocomplete-tag.Mui-disabled": { opacity: 1 },
        } : { display: "contents" }}
      >
        {children}
      </Box>
    </SchemaReadOnlyContext.Provider>
  );
}

export function useSchemaReadOnly(): boolean {
  return useContext(SchemaReadOnlyContext);
}