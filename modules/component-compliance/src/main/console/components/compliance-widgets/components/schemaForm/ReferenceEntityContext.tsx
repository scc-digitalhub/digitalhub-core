import { createContext, useContext, type ReactNode } from "react";
import type { JsonRecord } from "../../types";

interface ReferenceEntityContextValue {
  entity: JsonRecord | undefined;
  resource: string | undefined;
}

const COMPLIANCE_EXTENSION_BY_RESOURCE: Record<string, string> = {
  projects: "ai-compliance",
  models: "model-compliance",
  dataitems: "data-compliance",
  artifacts: "data-compliance",
};

export function getReferenceObjectives({ entity, resource }: ReferenceEntityContextValue): JsonRecord[] {
  const extensionKind = resource ? COMPLIANCE_EXTENSION_BY_RESOURCE[resource] : undefined;
  const extensions = (entity?.extensions as JsonRecord[] | undefined) ?? [];
  const spec = extensions.find((extension) => extensionKind && extension.kind === extensionKind)?.spec as JsonRecord | undefined;
  return (spec?.objectives as JsonRecord[] | undefined) ?? [];
}

const ReferenceEntityContext = createContext<ReferenceEntityContextValue | null>(null);

export function ReferenceEntityProvider({
  entity,
  resource,
  children,
}: ReferenceEntityContextValue & { children: ReactNode }) {
  return (
    <ReferenceEntityContext.Provider value={{ entity, resource }}>
      {children}
    </ReferenceEntityContext.Provider>
  );
}

export function useReferenceEntity(): ReferenceEntityContextValue {
  const context = useContext(ReferenceEntityContext);
  if (!context) throw new Error("useReferenceEntity() must be used within a <ReferenceEntityProvider>.");
  return context;
}
