/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import io.fabric8.kubernetes.api.model.Quantity;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.ModelManager;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

public final class TvmRunnerHelper {

    public static final String SCRIPTS_CLASSPATH = "classpath:/runtime-tvm/scripts/";
    public static final String ENTRYPOINT_NAME = "entrypoint.sh";
    public static final List<String> SHARED_SCRIPTS = List.of("common.py", "publish.py", "tuning.py", "benchmark.py", "toolchain.py");

    public static final String NODE_ARCH_LABEL = "kubernetes.io/arch";

    // Accept both JSON targets and LLVM target flags.
    private static final Pattern TARGET_TRIPLE = Pattern.compile("mtriple\"?\\s*[:=]\\s*\"?([\\w.-]+)");
    private static final Pattern TARGET_X86_CPU = Pattern.compile("mcpu\"?\\s*[:=]\\s*\"?x86-64");

    private static final DefaultResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();
    private static final Map<String, String> SHARED_SCRIPT_CACHE = new ConcurrentHashMap<>();

    private TvmRunnerHelper() {}

    public static List<ContextSource> createContextSources(
        @NotNull String entrypoint,
        @NotNull String taskScriptName,
        @NotNull String taskScript
    ) {
        List<ContextSource> sources = new ArrayList<>();
        sources.add(base64Source(ENTRYPOINT_NAME, entrypoint));
        sources.add(base64Source(taskScriptName, taskScript));
        for (String name : SHARED_SCRIPTS) {
            String content = SHARED_SCRIPT_CACHE.computeIfAbsent(name, n -> loadClasspath(SCRIPTS_CLASSPATH + n));
            sources.add(base64Source(name, content));
        }
        return sources;
    }

    public static String scriptName(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    public static String loadClasspath(String location) {
        try {
            return new String(RESOURCE_LOADER.getResource(location).getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CoreRuntimeException("error reading classpath resource: " + location);
        }
    }

    // The context injector expects the file content base64-encoded.
    private static ContextSource base64Source(String name, String content) {
        return ContextSource.builder()
            .name(name)
            .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
            .build();
    }

    public static ContextRef inputContextRef(String s3OrHttpUri, String destination) {
        if (!StringUtils.hasText(s3OrHttpUri)) {
            return null;
        }
        ContextRef ref = ContextRef.from(s3OrHttpUri);
        if (ref == null) {
            throw new IllegalArgumentException("invalid model source URI");
        }
        ref.setDestination(destination);
        return ref;
    }

    // The Model entity behind a store:// key; the latest version when the key has no id.
    public static Model resolveModel(String key, ModelManager modelManager) {
        KeyAccessor accessor = KeyAccessor.with(key);
        if (!EntityUtils.getEntityName(Model.class).equalsIgnoreCase(accessor.getType())) {
            throw new CoreRuntimeException("invalid entity kind reference, expected model");
        }
        Model model;
        try {
            model =
                accessor.getId() != null
                    ? modelManager.findModel(accessor.getId())
                    : modelManager.getLatestModelByKey(accessor.getProject(), key);
        } catch (NoSuchEntityException e) {
            model = null;
        }
        if (model == null) {
            throw new CoreRuntimeException("model not found for key: " + key);
        }
        return model;
    }

    public static String resolveModelPath(String reference, ModelManager modelManager) {
        if (!StringUtils.hasText(reference)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }
        if (!reference.startsWith(Keys.STORE_PREFIX)) {
            return reference;
        }
        return modelPath(resolveModel(reference, modelManager), reference);
    }

    public static String modelPath(Model model, String reference) {
        Object path = model.getSpec() != null ? model.getSpec().get("path") : null;
        if (!(path instanceof String text)) {
            throw new CoreRuntimeException("model spec.path is missing for: " + reference);
        }
        return text;
    }

    public static String resolveModelDir(String modelKey, ModelManager modelManager) {
        return modelDir(resolveModelPath(modelKey, modelManager));
    }

    // A trailing slash downloads the entire model folder; ZIP paths must remain unchanged.
    public static String modelDir(String path) {
        return path.endsWith("/") || path.endsWith(".zip") ? path : path + "/";
    }

    public static String modelKindOf(String reference) {
        if (reference == null || !reference.startsWith(Keys.STORE_PREFIX)) {
            return null;
        }
        return KeyAccessor.with(reference).getKind();
    }

    public static String extractFileName(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    // Whole CPU cores in a Kubernetes quantity, rounded up: "500m" -> 1, "1.5" -> 2.
    public static int parseCpuCores(String value) {
        final BigDecimal cores;
        try {
            cores = Quantity.parse(value).getNumericalAmount();
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IllegalArgumentException("invalid Kubernetes CPU quantity: " + value, e);
        }
        if (cores.signum() <= 0) {
            throw new IllegalArgumentException("CPU resources must be greater than zero: " + value);
        }
        try {
            return cores.setScale(0, RoundingMode.CEILING).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("CPU quantity is too large: " + value, e);
        }
    }

    // Fall back to the target for models published before target_triple was recorded.
    public static String modelArchitecture(Map<String, Serializable> spec) {
        if (spec == null) {
            return null;
        }
        if (
            spec.get("manifest") instanceof Map<?, ?> manifest &&
            manifest.get("target_triple") instanceof String triple &&
            StringUtils.hasText(triple)
        ) {
            String architecture = tripleArchitecture(triple);
            if (architecture == null) {
                throw new IllegalArgumentException("No serve node architecture mapping for model target_triple: " + triple);
            }
            return architecture;
        }
        return spec.get("target") instanceof String target ? targetArchitecture(target) : null;
    }

    // Host-dependent targets such as plain "llvm" have no fixed architecture.
    public static String targetArchitecture(String target) {
        if (!StringUtils.hasText(target)) {
            return null;
        }
        Matcher triple = TARGET_TRIPLE.matcher(target);
        if (triple.find()) {
            return tripleArchitecture(triple.group(1));
        }
        return TARGET_X86_CPU.matcher(target).find() ? "amd64" : null;
    }

    public static String tripleArchitecture(String triple) {
        String arch = triple.split("-", 2)[0].toLowerCase(Locale.ROOT);
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "amd64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        if (arch.equals("arm") || arch.startsWith("armv") || arch.startsWith("thumb")) return "arm";
        return switch (arch) {
            case "riscv64", "s390x" -> arch;
            case "powerpc64le", "ppc64le" -> "ppc64le";
            case "i386", "i486", "i586", "i686" -> "386";
            default -> null;
        };
    }
}
