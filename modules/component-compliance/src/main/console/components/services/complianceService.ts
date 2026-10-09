import type { ComplianceEntityKind, JsonRecord } from "../compliance-widgets/types";
import type { ComplianceService } from "../compliance-widgets/services/types";

const EXT_MAP = {
  project: 'ai-compliance',
  dataset: 'data-compliance',
  model: 'model-compliance',
};

export function createComplianceService(dataProvider: any): ComplianceService {

  return {
    async getComplianceSpec(kind, entityKind, entityId) {
      const record = (await dataProvider.getOne(entityKind, { id: entityId })).data;
      const ext = (record.extensions || []).find((e: any) => e.kind === EXT_MAP[kind]);
      return ext ? Promise.resolve(ext) : Promise.reject(null);
    },

    async saveComplianceSpec(kind, entityKind, entityId, spec) {
      const entity = (await dataProvider.getOne(entityKind, { id: entityId })).data;
      const extensions = entity.extensions || [];

      const existing = extensions.find((e: any) => e.id === spec.id); 
      if (existing) {
          Object.assign(existing, spec);
      } else {
          extensions.push(spec);
      }
      entity.extensions = extensions;
      const savedEntity = await dataProvider.update(entityKind, { id: entityId, data: entity, meta: { root: spec.project, update: true } });
      console.log('extensions', savedEntity.data.extensions);
      console.log('spec', spec);
      const saved = (savedEntity.data.extensions || []).find((e: any) => e.name === spec.name && e.kind === spec.kind);
      console.log('saved', saved);
      return saved ? Promise.resolve(saved) : Promise.reject(null);
    },

    async deleteComplianceSpec(kind, entityKind, entityId) {
      return new Promise((resolve) => setTimeout(() => resolve(), 500));
    },
  };
}
