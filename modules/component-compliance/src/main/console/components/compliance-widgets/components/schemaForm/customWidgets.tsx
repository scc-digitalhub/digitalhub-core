import { useEffect, useState, type ComponentType } from "react";
import { Autocomplete, Chip, CircularProgress, Stack, TextField, Typography } from "@mui/material";
import { useDataProvider } from "react-admin";
import type { CustomFieldWidget, JSONSchemaNode, SchemaRef } from "../../schema/types";
import type { JsonRecord } from "../../types";
import { useProjectCompliance } from "../compliance/ProjectComplianceContext";
import ObjectiveConditionField from "./ObjectiveConditionField";
import SchemaForm from "./SchemaForm";
import { useRegulatoryOptions } from "./RegulatoryOptionsContext";
import { useSchemaReadOnly } from "./SchemaReadOnlyContext";

interface CustomFieldWidgetProps {
  label: string;
  prop: JSONSchemaNode | SchemaRef;
  formValue: JsonRecord;
  value: unknown;
  onChange: (value: unknown) => void;
  required?: boolean;
}

interface RequirementOption {
  id: string;
  name: string;
}

interface EntityReferenceOption {
  key: string;
  label: string;
  resource?: string;
}

function EntityReferenceWidget({ label, prop, value, onChange, required }: CustomFieldWidgetProps) {
  const readOnly = useSchemaReadOnly();
  const dataProvider = useDataProvider();
  const [options, setOptions] = useState<EntityReferenceOption[]>([]);
  const [loading, setLoading] = useState(false);
  const resources = prop.entityResources ?? [];
  const multiple = !('$ref' in prop) && prop.type === "array";

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all(resources.map(async (resource) => {
      const result = await dataProvider.getList(resource, {
        pagination: { page: 1, perPage: 1000 },
        sort: { field: "name", order: "ASC" },
        filter: prop.entityKind ? { kind: prop.entityKind } : {},
      });
      return result.data.flatMap((entity: JsonRecord) => {
        if (typeof entity.key !== "string") return [];
        const spec = (entity.spec as JsonRecord | undefined) ?? {};
        const suffix = resources.length > 1 ? ` (${resource})` : "";
        const entityLabel = (entity.name ?? spec.name ?? entity.key) + ` (${entity.kind})` + suffix;
        return [{ key: entity.key, label: String(entityLabel), resource }];
      });
    })).then(
      (resourceOptions) => {
        if (cancelled) return;
        const byKey = new Map(resourceOptions.flat().map((option) => [option.key, option]));
        setOptions([...byKey.values()]);
        setLoading(false);
      },
      () => {
        if (!cancelled) setLoading(false);
      },
    );
    return () => { cancelled = true; };
  }, [dataProvider, prop.entityKind, resources.join("|")]);

  const references = multiple
    ? ((value as JsonRecord[] | undefined) ?? [])
    : value && typeof value === "object" ? [value as JsonRecord] : [];
  const selected = references.flatMap((reference) => {
    if (typeof reference.key !== "string") return [];
    return [options.find((option) => option.key === reference.key) ?? { key: reference.key, label: reference.key }];
  });
  const availableOptions = [
    ...options,
    ...selected.filter((item) => !options.some((option) => option.key === item.key)),
  ];

  return (
    <Autocomplete
      multiple={multiple}
      size="small"
      fullWidth
      disabled={readOnly}
      loading={loading}
      options={availableOptions}
      value={multiple ? selected : selected[0] ?? null}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, selectedOption) => option.key === selectedOption.key}
      onChange={(_event, next) => {
        if (multiple) {
          onChange((next as EntityReferenceOption[]).map((option) => ({ key: option.key })));
        } else {
          const selectedOption = next as EntityReferenceOption | null;
          onChange(selectedOption ? { key: selectedOption.key } : undefined);
        }
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          required={required}
          helperText={(prop as JSONSchemaNode).description}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading && <CircularProgress color="inherit" size={18} />}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  );
}

function RequirementsWidget({ label, prop, value, onChange }: CustomFieldWidgetProps) {
  const readOnly = useSchemaReadOnly();
  const { spec, loading } = useProjectCompliance();
  const context = (spec?.context as JsonRecord | undefined) ?? {};
  const requirements = (context.requirements as JsonRecord[] | undefined) ?? [];
  const requirementOptions: RequirementOption[] = requirements.flatMap((requirement) => {
    if (typeof requirement.id !== "string") return [];
    return [{
      id: requirement.id,
      name: typeof requirement.name === "string" && requirement.name ? requirement.name : requirement.id,
    }];
  });
  const selectedIds = (value as string[] | undefined) ?? [];
  const selected = selectedIds.map(
    (id) => requirementOptions.find((option) => option.id === id) ?? { id, name: id },
  );
  const options = [
    ...requirementOptions,
    ...selected.filter((item) => !requirementOptions.some((option) => option.id === item.id)),
  ];

  return (
    <Autocomplete
      multiple
      size="small"
      disabled={readOnly}
      loading={loading}
      options={options}
      value={selected}
      getOptionLabel={(option) => option.name}
      isOptionEqualToValue={(option, selectedOption) => option.id === selectedOption.id}
      onChange={(_event, next) => onChange(next.map((requirement) => requirement.id))}
      renderTags={(tagValue, getTagProps) => tagValue.map((requirement, index) => {
        const { key, ...tagProps } = getTagProps({ index });
        return <Chip key={key} size="small" label={requirement.name} {...tagProps} />;
      })}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          helperText={(prop as JSONSchemaNode).description}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading && <CircularProgress color="inherit" size={18} />}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  );
}

function ObjectiveConditionWidget({ label, value, onChange, required }: CustomFieldWidgetProps) {
  return (
    <ObjectiveConditionField
      label={label}
      value={value as JsonRecord | undefined}
      onChange={onChange}
      required={required}
    />
  );
}

function RegulatoryReferenceWidget({ label, value, onChange, required }: CustomFieldWidgetProps) {
  const readOnly = useSchemaReadOnly();
  const options = useRegulatoryOptions() ?? [];
  const optionLabel = (regulation: JsonRecord) =>
    [regulation.framework, regulation.article, regulation.version].filter(Boolean).join(" - ");
  const optionKey = (regulation: JsonRecord) =>
    [regulation.framework, regulation.article, regulation.version, regulation.uri].join("|");

  return (
    <Autocomplete
      size="small"
      fullWidth
      disabled={readOnly}
      options={options}
      value={(value as JsonRecord | undefined) ?? null}
      getOptionLabel={optionLabel}
      isOptionEqualToValue={(option, selected) => optionKey(option) === optionKey(selected)}
      onChange={(_event, next) => onChange(next ?? undefined)}
      renderInput={(params) => <TextField {...params} label={label} required={required} />}
    />
  );
}

function ProjectObjectivesWidget({ label, value, onChange }: CustomFieldWidgetProps) {
  const readOnly = useSchemaReadOnly();
  const { spec, loading } = useProjectCompliance();
  const objectives = (spec?.objectives as JsonRecord[] | undefined) ?? [];
  const options = objectives.flatMap((objective) => {
    const name = typeof objective.name === "string" ? objective.name : undefined;
    return name ? [{ id: name, name }] : [];
  });
  const selectedIds = (value as string[] | undefined) ?? [];
  const selected = selectedIds.map((id) => options.find((option) => option.id === id) ?? { id, name: id });
  const allOptions = [...options, ...selected.filter((item) => !options.some((option) => option.id === item.id))];

  return (
    <Autocomplete
      multiple
      size="small"
      disabled={readOnly}
      loading={loading}
      options={allOptions}
      value={selected}
      getOptionLabel={(option) => option.name}
      isOptionEqualToValue={(option, selectedOption) => option.id === selectedOption.id}
      onChange={(_event, next) => onChange(next.map((objective) => objective.id))}
      renderTags={(tagValue, getTagProps) => tagValue.map((objective, index) => {
        const { key, ...tagProps } = getTagProps({ index });
        return <Chip key={key} size="small" label={objective.name} {...tagProps} />;
      })}
      renderInput={(params) => <TextField {...params} label={label} />}
    />
  );
}

const REPORT_CONTENT_SCHEMA: Record<string, { key: string; schema: string }> = {
  EVALUATION: { key: "evaluation", schema: "EvaluationContent" },
  TEST: { key: "test", schema: "TestContent" },
  LOG: { key: "log", schema: "LogContent" },
};

function ReportContentWidget({ label, formValue, value, onChange }: CustomFieldWidgetProps) {
  const reportKind = typeof formValue.kind === "string" ? formValue.kind : "";
  const config = REPORT_CONTENT_SCHEMA[reportKind];
  const content = (value as JsonRecord | undefined) ?? {};

  if (!config) {
    return <Typography color="text.secondary">Select a report kind to populate {label.toLowerCase()}.</Typography>;
  }

  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2">{label}</Typography>
      <SchemaForm
        schemaName={config.schema}
        value={(content[config.key] as JsonRecord | undefined) ?? {}}
        onChange={(next) => onChange({ [config.key]: next })}
      />
    </Stack>
  );
}

const CUSTOM_WIDGETS: Record<CustomFieldWidget, ComponentType<CustomFieldWidgetProps>> = {
  requirements: RequirementsWidget,
  "objective-condition": ObjectiveConditionWidget,
  "regulatory-reference": RegulatoryReferenceWidget,
  "report-content": ReportContentWidget,
  "project-objectives": ProjectObjectivesWidget,
  "entity-reference": EntityReferenceWidget,
};

export function getCustomFieldWidget(name: CustomFieldWidget): ComponentType<CustomFieldWidgetProps> {
  return CUSTOM_WIDGETS[name];
}