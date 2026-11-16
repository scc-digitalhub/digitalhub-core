/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
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
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import java.util.List;

public class TvmCompileSchemaModule implements com.github.victools.jsonschema.generator.Module {

    private final TvmProperties properties;

    public TvmCompileSchemaModule(TvmProperties properties) {
        this.properties = properties;
    }

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
        builder.forTypesInGeneral().withCustomDefinitionProvider(new CompileProvider(properties));
    }

    private static class CompileProvider implements CustomDefinitionProviderV2 {

        private final TvmProperties config;

        CompileProvider(TvmProperties config) {
            this.config = config;
        }

        @Override
        public CustomDefinition provideCustomSchemaDefinition(ResolvedType type, SchemaGenerationContext context) {
            String name = type.getErasedType().getName();
            if (type.getErasedType() == TvmCompileRunSpec.Optimization.class) {
                ObjectNode schema = context.createStandardDefinition(type, this);
                compilerOptionsSchema((ObjectNode) schema.get("properties"));
                return new CustomDefinition(schema, true);
            }
            if (type.getErasedType() == TvmCompileRunSpec.Tuning.class) {
                ObjectNode schema = context.createStandardDefinition(type, this);
                return new CustomDefinition(tuningSchema((ObjectNode) schema.get("properties")), true);
            }
            if (
                !name.equals(TvmCompileRunSpec.class.getName()) &&
                !name.equals(TvmCompileRunSpec.class.getName() + "Proxy")
            ) return null;
            ObjectNode schema = context.createStandardDefinition(type, this);
            ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.set("target_architecture", targetProfileSchema(properties, config));
            properties.set("target", explicitTargetSchema(properties));
            requireAutoProfileForExplicitTarget(schema, config);
            cudaTuningSchema(schema);
            return new CustomDefinition(schema);
        }
    }

    private static ObjectNode targetProfileSchema(ObjectNode factory, TvmProperties config) {
        ObjectNode profile = factory
            .objectNode()
            .put("type", "string")
            .put("title", "Target profile")
            .put("default", "auto");
        ArrayNode profiles = profile.putArray("oneOf");
        config.getTargets().forEach((name, target) -> {
            profiles.addObject().put("const", name).put("title", target.label() != null ? target.label() : name);
        });
        config.getTargets().forEach((name, target) -> target.aliases().forEach(alias -> {
            if (!config.getTargets().containsKey(alias)) {
                profiles.addObject().put("const", alias).put("title", alias + " (alias of " + name + ")");
            }
        }));
        return profile;
    }

    private static ObjectNode explicitTargetSchema(ObjectNode factory) {
        ObjectNode target = object(factory, "Explicit TVM Target");
        ((ObjectNode) target.get("properties")).putObject("kind").put("type", "string")
            .putArray("enum").add("llvm").add("cuda");

        ObjectNode llvm = object(factory, null);
        ObjectNode llvmFields = (ObjectNode) llvm.get("properties");
        llvmFields.putObject("kind").putArray("enum").add("llvm");
        llvmTargetFields(llvmFields);

        ObjectNode cuda = object(factory, null);
        ObjectNode cudaFields = (ObjectNode) cuda.get("properties");
        cudaFields.putObject("kind").putArray("enum").add("cuda");
        cudaFields.putObject("arch").put("type", "string")
            .put("pattern", TvmTargetValidator.CUDA_ARCH_PATTERN)
            .put("description", "Explicit CUDA architecture, for example sm_87 or sm_90a.");
        ObjectNode host = object(factory, "LLVM host target");
        ObjectNode hostFields = (ObjectNode) host.get("properties");
        hostFields.putObject("kind").put("type", "string").put("const", "llvm");
        llvmTargetFields(hostFields);
        hostFields.withObject("/mtriple").put("pattern", TvmTargetValidator.CUDA_HOST_TRIPLE_PATTERN)
            .put("description", "Explicit AMD64 or ARM64 Linux GNU host triple, for example aarch64-linux-gnu.");
        host.putArray("required").add("kind").add("mtriple");
        host.put("additionalProperties", true);
        cudaFields.set("host", host);
        for (String limit : TvmTargetValidator.CUDA_TUNING_LIMITS) {
            cudaFields.putObject(limit).put("type", "integer").put("minimum", 1)
                .put("description", "Real device limit required for CUDA tuning; obtain it from the target GPU.");
        }
        cuda.putArray("required").add("arch").add("host");
        target.putObject("dependencies").putObject("kind").putArray("oneOf").add(llvm).add(cuda);
        target.putObject("dependentSchemas").set("kind", target.path("dependencies").get("kind"));
        // The console resolves legacy dependencies; Draft 2020-12 validators use this condition.
        ObjectNode cudaValidation = target.putArray("allOf").addObject();
        ObjectNode isCuda = cudaValidation.putObject("if");
        isCuda.putArray("required").add("kind");
        isCuda.putObject("properties").putObject("kind").put("const", "cuda");
        ObjectNode constraints = object(factory, null);
        constraints.putArray("required").add("arch").add("host");
        ObjectNode constraintFields = (ObjectNode) constraints.get("properties");
        copy(cudaFields, constraintFields, List.of("arch"));
        copy(cudaFields, constraintFields, TvmTargetValidator.CUDA_TUNING_LIMITS);
        ObjectNode hostConstraints = object(factory, null);
        hostConstraints.putArray("required").add("kind").add("mtriple");
        copy(hostFields, (ObjectNode) hostConstraints.get("properties"), List.of(
            "kind", "mtriple", "mcpu", "mfloat-abi", "mattr", "num-cores"
        ));
        constraintFields.set("host", hostConstraints);
        cudaValidation.set("then", constraints);
        target.putObject("if").put("minProperties", 1);
        target.putObject("then").putArray("required").add("kind");
        target.put("additionalProperties", true);
        target.put(
            "description",
            "Optional standard TVM LLVM or CUDA target; leave the profile at auto. CUDA requires arch and a nested LLVM host with an explicit AMD64 or ARM64 Linux GNU mtriple."
        );
        return target;
    }

    private static void llvmTargetFields(ObjectNode fields) {
        for (String field : List.of("mtriple", "mcpu", "mfloat-abi")) {
            fields.putObject(field).put("type", "string");
        }
        fields.putObject("mattr").put("type", "array").putObject("items").put("type", "string");
        fields.putObject("num-cores").put("type", "integer").put("minimum", 1);
    }

    private static void requireAutoProfileForExplicitTarget(ObjectNode schema, TvmProperties config) {
        ObjectNode choice = schema.withArray("/allOf").addObject();
        ObjectNode explicit = choice.putObject("if");
        explicit.putArray("required").add("target");
        explicit.putObject("properties").putObject("target").put("minProperties", 1);
        ArrayNode names = choice.putObject("then").putObject("properties")
            .putObject("target_architecture").putArray("enum").add("auto");
        config.target("auto").aliases().forEach(names::add);
    }

    private static void cudaTuningSchema(ObjectNode schema) {
        ObjectNode choice = schema.withArray("/allOf").addObject();
        ObjectNode condition = choice.putObject("if");
        condition.putArray("required").add("target").add("tuning");
        ObjectNode fields = condition.putObject("properties");
        fields.putObject("target").putArray("required").add("kind");
        fields.withObject("/target").putObject("properties").putObject("kind").put("const", "cuda");
        fields.putObject("tuning").putArray("required").add("tuning_mode");
        fields.withObject("/tuning").putObject("properties").putObject("tuning_mode").put("const", "tune");
        ObjectNode active = choice.putObject("then").putObject("properties");
        active.putObject("tuning").putObject("properties")
            .putObject("tuning_enable_cpu_cache_flush").put("const", false);
        ArrayNode limits = active.putObject("target").putArray("required");
        TvmTargetValidator.CUDA_TUNING_LIMITS.forEach(limits::add);
    }

    private static void compilerOptionsSchema(ObjectNode fields) {
        ObjectNode arguments = fields.objectNode().put("type", "array").put("title", "Custom arguments");
        arguments.putObject("items").put("type", "string");
        ObjectNode compilerOptions = fields.putObject("cross_cc_options").put("title", "Compiler Options");
        compilerOptions.put(
            "description",
            "Use profile defaults, or replace them with separate compiler arguments. Example item: --sysroot=/opt/sysroots/arm64."
        );
        compilerOptions.putNull("default");
        compilerOptions
            .putArray("oneOf")
            .add(arguments.objectNode().put("type", "null").put("title", "Profile defaults"))
            .add(arguments);
    }

    private static ObjectNode tuningSchema(ObjectNode fields) {
        ObjectNode tuning = object(fields, "MetaSchedule tuning");
        copy(fields, (ObjectNode) tuning.get("properties"), List.of("tuning_mode"));
        // Known dormant values are preserved; active branches below validate their types.
        TvmCompileOptions.TUNING.forEach(name -> ((ObjectNode) tuning.get("properties")).putObject(name));
        tuning.put("additionalProperties", false);

        ObjectNode tune = object(fields, null);
        ObjectNode tuneProperties = (ObjectNode) tune.get("properties");
        copy(fields, tuneProperties, TvmCompileOptions.APPLY);
        copy(fields, tuneProperties, TvmCompileOptions.SEARCH);
        tuneProperties.remove("tuning_runner_timeout_sec");
        ObjectNode local = object(fields, null);
        copy(fields, (ObjectNode) local.get("properties"), List.of("tuning_runner_timeout_sec"));
        ObjectNode rpc = object(fields, null);
        copy(fields, (ObjectNode) rpc.get("properties"), TvmCompileOptions.RPC);
        for (String name : List.of("rpc_tracker_host", "rpc_tracker_key")) {
            ObjectNode nonblank = fields.objectNode().put("type", "string").put("pattern", "\\S");
            JsonNode field = fields.get(name);
            if (field != null && field.isObject()) nonblank.putArray("allOf").add(field);
            ((ObjectNode) rpc.get("properties")).set(name, nonblank);
        }
        rpc.putArray("required").add("rpc_tracker_host").add("rpc_tracker_port").add("rpc_tracker_key");
        ObjectNode runnerCondition = tune.putObject("if");
        runnerCondition.putArray("required").add("tuning_runner");
        runnerCondition.putObject("properties").putObject("tuning_runner").put("const", "rpc");
        tune.set("then", withDefaults(rpc));
        tune.set("else", withDefaults(local));
        tune.putArray("required").add("tuning_trials");

        ObjectNode apply = object(fields, null);
        copy(fields, (ObjectNode) apply.get("properties"), TvmCompileOptions.APPLY);
        ObjectNode database = fields.objectNode().put("pattern", "\\S");
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

    private static void copy(ObjectNode from, ObjectNode to, List<String> names) {
        // Share field nodes: victools resolves their placeholders after this callback.
        names.forEach(name -> to.set(name, from.get(name)));
    }

    private static ObjectNode withDefaults(ObjectNode branch) {
        // Defaults belong to each active branch so the form resolves dependencies
        // before prefilling.
        ObjectNode defaults = branch.objectNode();
        for (var field : branch.get("properties").properties()) {
            JsonNode value = field.getValue().findValue("default");
            if (value != null) defaults.set(field.getKey(), value);
        }
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
        parent.putObject("dependentSchemas").set(selector, dependency);
    }
}
