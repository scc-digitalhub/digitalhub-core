import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useRootSelector } from "@dslab/ra-root-selector";
import type { JsonRecord } from "../../types";
import { useComplianceServices } from "../../services/context";

interface ProjectComplianceContextValue {
  spec: JsonRecord | null;
  loading: boolean;
}

const ProjectComplianceContext = createContext<ProjectComplianceContextValue | null>(null);

export function ProjectComplianceProvider({ children }: { children: ReactNode }) {
  const { root: projectId } = useRootSelector();
  const { complianceService } = useComplianceServices();
  const [spec, setSpec] = useState<JsonRecord | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!projectId) {
      setSpec(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setSpec(null);
    setLoading(true);
    complianceService.getComplianceSpec("project", "projects", projectId).then(
      (extension) => {
        if (!cancelled) setSpec((extension?.spec as JsonRecord | undefined) ?? null);
      },
      () => {
        if (!cancelled) setSpec(null);
      },
    ).finally(() => {
      if (!cancelled) setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [complianceService, projectId]);

  const value = useMemo(() => ({ spec, loading }), [spec, loading]);
  return <ProjectComplianceContext.Provider value={value}>{children}</ProjectComplianceContext.Provider>;
}

export function useProjectCompliance(): ProjectComplianceContextValue {
  const context = useContext(ProjectComplianceContext);
  if (!context) throw new Error("useProjectCompliance() must be used within a <ProjectComplianceProvider>.");
  return context;
}