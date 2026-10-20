import type { ComplianceEntityKind, JsonRecord } from "../compliance-widgets/types";
import type { ComplianceService } from "../compliance-widgets/services/types";

const EXT_MAP = {
  project: 'ai-compliance',
  dataset: 'data-compliance',
  model: 'model-compliance',
  report: 'compliance-report',
};

export function createComplianceService(dataProvider: any): ComplianceService {

  return {
    async getComplianceSpec(kind, entityKind, entityId) {
      const record = (await dataProvider.getOne(entityKind, { id: entityId })).data;
      const ext = (record.extensions || []).find((e: any) => e.kind === EXT_MAP[kind]);
      return ext ? Promise.resolve(ext) : Promise.reject(null);
    },

    async saveComplianceSpec(kind, entityKind, entityId, spec) {
      console.log("Saving compliance spec:", kind, entityKind, entityId, spec);
      // workaround for project compliance spec saving
      if (entityKind === 'projects') {
        const project = (await dataProvider.getOne(entityKind, { id: entityId })).data;
        const extensions = [...(project.extensions || [])];
        const existingIndex = extensions.findIndex((extension: JsonRecord) => extension.id === spec.id);

        if (existingIndex >= 0) {
          extensions[existingIndex] = { ...extensions[existingIndex], ...spec };
        } else {
          extensions.push(spec);
        }

        const savedProject = (await dataProvider.update(entityKind, {
          id: entityId,
          data: { ...project, extensions },
          previousData: project,
        })).data;
        const saved = (savedProject.extensions || []).find((extension: JsonRecord) => extension.id === spec.id);
        return saved ? Promise.resolve(saved) : Promise.reject(null);
      }

      const extensions = (await dataProvider.getExtensions(entityKind, { id: entityId, meta: { root: spec.project } })).extensions || [];

      const existing = extensions.find((e: any) => e.id === spec.id); 
      if (existing) {
          Object.assign(existing, spec);
      } else {
          extensions.push(spec);
      }
      const savedExtensions = (await dataProvider.updateExtensions(entityKind, { id: entityId, data: extensions, meta: { root: spec.project, update: true } })).extensions || [];

      const saved = (savedExtensions || []).find((e: any) => e.id === spec.id);
      console.log("Saved compliance spec:", saved);
      return saved ? Promise.resolve(saved) : Promise.reject(null);
    },

    async deleteComplianceSpec(kind, entityKind, entityId, extensionId) {
      if (entityKind === 'projects') {
        const project = (await dataProvider.getOne(entityKind, { id: entityId })).data;
        const extensions = [...(project.extensions || [])];
        const existingIndex = extensions.findIndex((extension: JsonRecord) => extension.id === extensionId);

        if (existingIndex >= 0) {
          extensions.splice(existingIndex, 1);
        }

        const savedProject = (await dataProvider.update(entityKind, {
          id: entityId,
          data: { ...project, extensions },
          previousData: project,
        })).data;
        const saved = (savedProject.extensions || []);
        return saved ? Promise.resolve(saved) : Promise.reject(null);
      }

      const entity = (await dataProvider.getOne(entityKind, { id: entityId })).data;
      const extensions = (await dataProvider.getExtensions(entityKind, { id: entityId, meta: { root: entity.project } })).extensions || [];

      const existing = extensions.filter((e: any) => e.id !== extensionId); 
      (await dataProvider.updateExtensions(entityKind, { id: entityId, data: existing, meta: { root: entity.project, update: true } })).extensions || [];
    },
  };
}
