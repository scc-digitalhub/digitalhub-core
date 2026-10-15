import { createContext, useContext, useMemo, type ReactNode } from "react";
import type { ComplianceWidgetServices } from "./types";

const ComplianceServicesContext = createContext<ComplianceWidgetServices | null>(null);

export interface ComplianceServicesProviderProps {
  /** Real service implementations to use. Defaults to the bundled in-browser mock when omitted. */
  services?: ComplianceWidgetServices;
  children: ReactNode;
}

/**
 * Supplies the entity/compliance/agent services the compliance widgets need. Wrap your app (or
 * just the part of it that renders the widgets) once with this provider:
 *
 * ```tsx
 * <ComplianceServicesProvider services={myRealServices}>
 *   <ProjectComplianceWidget entityId={projectId} />
 * </ComplianceServicesProvider>
 * ```
 *
 * Omit `services` to use the bundled mock implementation (localStorage-backed, seeded with
 * sample data) so the widgets work out of the box before a real backend is wired up.
 */
export function ComplianceServicesProvider({ services, children }: ComplianceServicesProviderProps) {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const value = useMemo(() => services ?? null, [services]);
  return <ComplianceServicesContext.Provider value={value}>{children}</ComplianceServicesContext.Provider>;
}

export function useComplianceServices(): ComplianceWidgetServices {
  const services = useContext(ComplianceServicesContext);
  if (!services) {
    throw new Error("useComplianceServices() must be used within a <ComplianceServicesProvider>.");
  }
  return services;
}
