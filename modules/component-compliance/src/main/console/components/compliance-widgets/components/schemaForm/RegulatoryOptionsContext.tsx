import { createContext, useContext, type ReactNode } from "react";
import type { JsonRecord } from "../../types";

const RegulatoryOptionsContext = createContext<JsonRecord[] | null>(null);

export function RegulatoryOptionsProvider({
  regulations,
  children,
}: {
  regulations: JsonRecord[];
  children: ReactNode;
}) {
  return <RegulatoryOptionsContext.Provider value={regulations}>{children}</RegulatoryOptionsContext.Provider>;
}

export function useRegulatoryOptions(): JsonRecord[] | null {
  return useContext(RegulatoryOptionsContext);
}
