import { createContext, useContext, type ReactNode } from "react";

const SchemaReadOnlyContext = createContext(false);

export function SchemaReadOnlyProvider({ readOnly, children }: { readOnly: boolean; children: ReactNode }) {
  return <SchemaReadOnlyContext.Provider value={readOnly}>{children}</SchemaReadOnlyContext.Provider>;
}

export function useSchemaReadOnly(): boolean {
  return useContext(SchemaReadOnlyContext);
}