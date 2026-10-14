/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 * SPDX-License-Identifier: Apache-2.0
 */
package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.util.List;

/** Group TVM options using the console's existing accordions and schema dependencies. */
public class TvmCompileSchemaModule implements com.github.victools.jsonschema.generator.Module {

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        // Entity registries share this builder: install the provider only once.
        if (
            builder
                .forTypesInGeneral()
                .getCustomDefinitionProviders()
                .stream()
                .anyMatch(CompileProvider.class::isInstance)
        ) return;
        builder
            .forFields()
            .withInstanceAttributeOverride((attributes, field, context) -> {
                String declaringType = field.getDeclaringType().getErasedType().getName();
                if (!declaringType.startsWith("it.smartcommunitylabdhub.runtime.tvm.")) return;
                JsonNode value = attributes.get("default");
                if (value == null || !value.isTextual()) return;
                Class<?> type = field.getType().getErasedType();
                if (type == Integer.class) attributes.put("default", Integer.parseInt(value.asText()));
                else if (type == Double.class) attributes.put("default", Double.parseDouble(value.asText()));
                else if (type == Boolean.class) attributes.put("default", Boolean.parseBoolean(value.asText()));
            });
        builder
            .forTypesInGeneral()
            .withTypeAttributeOverride((schema, type, context) -> {
                if (!type.getType().getErasedType().getName().equals(TvmTargetArchitecture.class.getName())) return;
                // Apply labels after enum providers, regardless of registry initialization order.
                schema.remove("enum");
                ArrayNode options = schema.putArray("oneOf");
                for (TvmTargetArchitecture target : TvmTargetArchitecture.values()) {
                    options.addObject().put("const", target.name()).put("title", target.getLabel());
                }
            })
            .withCustomDefinitionProvider(new CompileProvider());
    }

    private static class CompileProvider implements CustomDefinitionProviderV2 {

        @Override
        public CustomDefinition provideCustomSchemaDefinition(ResolvedType type, SchemaGenerationContext context) {
            String name = type.getErasedType().getName();
            if (
                !name.equals(TvmCompileRunSpec.class.getName()) &&
                !name.equals(TvmCompileRunSpec.class.getName() + "Proxy")
            ) return null;
            ObjectNode schema = context.createStandardDefinition(type, this);
            ObjectNode properties = (ObjectNode) schema.get("properties");
            ObjectNode optimization = object(schema, "Advanced compilation");
            move(properties, (ObjectNode) optimization.get("properties"), TvmCompileOptions.OPTIMIZATION);
            properties.set("optimization", optimization);
            properties.set("tuning", tuningSchema(properties));
            properties.remove(TvmCompileOptions.TUNING);
            properties.remove("tuning_mode");
            return new CustomDefinition(schema);
        }
    }

    private static ObjectNode tuningSchema(ObjectNode fields) {
        ObjectNode tuning = object(fields, "MetaSchedule tuning");
        copy(fields, (ObjectNode) tuning.get("properties"), List.of("tuning_mode"));

        ObjectNode tune = object(fields, null);
        ObjectNode tuneProperties = (ObjectNode) tune.get("properties");
        copy(fields, tuneProperties, TvmCompileOptions.APPLY);
        copy(fields, tuneProperties, TvmCompileOptions.SEARCH);
        tuneProperties.remove("tuning_runner_timeout_sec");
        ObjectNode local = object(fields, null);
        copy(fields, (ObjectNode) local.get("properties"), List.of("tuning_runner_timeout_sec"));
        ObjectNode rpc = object(fields, null);
        copy(fields, (ObjectNode) rpc.get("properties"), TvmCompileOptions.RPC);
        rpc.putArray("required").add("rpc_tracker_host").add("rpc_tracker_port").add("rpc_tracker_key");
        ObjectNode runnerCondition = tune.putObject("if");
        runnerCondition.putArray("required").add("tuning_runner");
        runnerCondition.putObject("properties").putObject("tuning_runner").put("const", "rpc");
        tune.set("then", withDefaults(rpc));
        tune.set("else", withDefaults(local));
        tune.putArray("required").add("tuning_trials");

        ObjectNode apply = object(fields, null);
        copy(fields, (ObjectNode) apply.get("properties"), TvmCompileOptions.APPLY);
        ObjectNode database = fields.objectNode().put("minLength", 1);
        database.putArray("allOf").add(fields.get("tuning_model_path"));
        ((ObjectNode) apply.get("properties")).set("tuning_model_path", database);
        apply.putArray("required").add("tuning_model_path");
        choices(tuning, "tuning_mode", List.of("off", "tune", "apply"), List.of(object(fields, null), tune, apply));
        return tuning;
    }

    private static ObjectNode object(ObjectNode factory, String title) {
        ObjectNode result = factory.objectNode().put("type", "object");
        if (title != null) result.put("title", title);
        result.putObject("properties");
        return result;
    }

    private static void move(ObjectNode from, ObjectNode to, List<String> names) {
        names.forEach(name -> to.set(name, from.remove(name)));
    }

    private static void copy(ObjectNode from, ObjectNode to, List<String> names) {
        // Share field nodes: victools resolves their placeholders after this callback.
        names.forEach(name -> to.set(name, from.get(name)));
    }

    private static ObjectNode withDefaults(ObjectNode branch) {
        // Defaults belong to each active branch so the form resolves dependencies before prefilling.
        ObjectNode defaults = branch.objectNode();
        branch.get("properties").fields().forEachRemaining(field -> {
            JsonNode value = field.getValue().findValue("default");
            if (value != null) defaults.set(field.getKey(), value);
        });
        if (!defaults.isEmpty()) branch.set("default", defaults);
        return branch;
    }

    private static void choices(ObjectNode parent, String selector, List<String> values, List<ObjectNode> branches) {
        ObjectNode dependency = parent.objectNode();
        ArrayNode choices = dependency.putArray("oneOf");
        for (int i = 0; i < values.size(); i++) {
            ObjectNode branch = branches.get(i);
            ((ObjectNode) branch.get("properties")).putObject(selector).putArray("enum").add(values.get(i));
            choices.add(withDefaults(branch));
        }
        parent.putObject("dependencies").set(selector, dependency);
    }
}
