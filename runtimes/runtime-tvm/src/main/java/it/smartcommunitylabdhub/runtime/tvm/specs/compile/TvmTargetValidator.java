/*
 * SPDX-FileCopyrightText: © 2026 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import java.util.List;

/** The target contract shared by configured profiles and explicit compile targets. */
public final class TvmTargetValidator {

    public static final String CUDA_ARCH_PATTERN = "^sm_[0-9]+[a-z]?$";
    public static final String CUDA_HOST_TRIPLE_PATTERN =
        "^(x86_64|amd64|aarch64|arm64)(-[A-Za-z0-9_+.]+)*-linux-gnu$";
    public static final List<String> CUDA_TUNING_LIMITS = List.of(
        "max_shared_memory_per_block", "max_threads_per_block", "thread_warp_size"
    );

    private TvmTargetValidator() {}

    public static JsonNode parse(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("TVM target must be a non-empty JSON object string");
        }
        try {
            JsonNode definition = JacksonMapper.OBJECT_MAPPER.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(target);
            validate(definition);
            return definition;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("TVM target must be a valid JSON object string", e);
        }
    }

    public static void validate(JsonNode target) {
        if (target == null || !target.isObject() || target.isEmpty()) {
            throw new IllegalArgumentException("TVM target must be a non-empty JSON object");
        }
        String kind = target.path("kind").asText();
        if (!"llvm".equals(kind) && !"cuda".equals(kind)) {
            throw new IllegalArgumentException("target.kind must be llvm or cuda");
        }
        if (!"cuda".equals(kind)) return;
        JsonNode arch = target.path("arch");
        if (!arch.isTextual() || !arch.asText().matches(CUDA_ARCH_PATTERN)) {
            throw new IllegalArgumentException("CUDA target.arch is required and must name an explicit architecture, such as sm_87");
        }
        JsonNode host = target.path("host");
        if (!host.isObject() || !"llvm".equals(host.path("kind").asText())) {
            throw new IllegalArgumentException("CUDA target.host must be a nested LLVM target object with kind=llvm");
        }
        JsonNode triple = host.path("mtriple");
        if (!triple.isTextual() || !triple.asText().matches(CUDA_HOST_TRIPLE_PATTERN)) {
            throw new IllegalArgumentException(
                "CUDA target.host.mtriple must explicitly select an AMD64 or ARM64 Linux GNU host, " +
                "such as x86_64-linux-gnu or aarch64-linux-gnu"
            );
        }
        for (String name : List.of("mcpu", "mfloat-abi")) {
            if (host.has(name) && !host.path(name).isTextual()) {
                throw new IllegalArgumentException("CUDA target.host." + name + " must be a string");
            }
        }
        if (host.has("mattr")) {
            JsonNode features = host.path("mattr");
            if (!features.isArray()) {
                throw new IllegalArgumentException("CUDA target.host.mattr must be an array of strings");
            }
            for (JsonNode feature : features) {
                if (!feature.isTextual()) {
                    throw new IllegalArgumentException("CUDA target.host.mattr must be an array of strings");
                }
            }
        }
        if (host.has("num-cores")) normalizePositiveInteger((ObjectNode) host, "num-cores", "CUDA target.host");
        for (String name : CUDA_TUNING_LIMITS) {
            if (target.has(name)) normalizePositiveInteger((ObjectNode) target, name, "CUDA target");
        }
    }

    private static void normalizePositiveInteger(ObjectNode target, String name, String path) {
        JsonNode value = target.get(name);
        if (value.isNumber()) {
            try {
                var integer = value.decimalValue().toBigIntegerExact();
                if (integer.signum() > 0) {
                    target.put(name, integer);
                    return;
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Non-integral and non-finite numbers are invalid device limits.
            }
        }
        throw new IllegalArgumentException(path + "." + name + " must be a positive integer");
    }
}
