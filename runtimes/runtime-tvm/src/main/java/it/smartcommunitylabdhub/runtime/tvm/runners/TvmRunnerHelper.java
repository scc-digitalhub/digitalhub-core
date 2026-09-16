/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.ModelManager;
import jakarta.annotation.Nullable;
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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

// Stateless helpers shared by the TVM runners: pod scripts, model references, names, CPU
// quantities and node architectures.
public final class TvmRunnerHelper {

    // Folder of the pod scripts on the classpath.
    public static final String SCRIPTS_CLASSPATH = "classpath:/runtime-tvm/scripts/";
    public static final String ENTRYPOINT_NAME = "entrypoint.sh";
    // Modules imported by the task scripts: options and IR helpers, Model publishing,
    // MetaSchedule tuning and the benchmark.
    public static final List<String> SHARED_SCRIPTS = List.of("common.py", "publish.py", "tuning.py", "benchmark.py");

    // Label that every Kubernetes node carries with its CPU architecture (amd64, arm64, arm).
    public static final String NODE_ARCH_LABEL = "kubernetes.io/arch";

    // mtriple and mcpu in a TVM target, written as JSON ("mtriple":"...") or as flags (-mtriple=...).
    private static final Pattern TARGET_TRIPLE = Pattern.compile("mtriple\"?\\s*[:=]\\s*\"?([\\w.-]+)");
    private static final Pattern TARGET_X86_CPU = Pattern.compile("mcpu\"?\\s*[:=]\\s*\"?x86-64");

    private static final DefaultResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();
    private static final Map<String, String> SHARED_SCRIPT_CACHE = new ConcurrentHashMap<>();

    private TvmRunnerHelper() {}

    // Files mounted in every TVM Job pod: the entrypoint, the task script under its own name
    // (the entrypoint runs the one named in TVM_TASK_SCRIPT) and the shared modules it imports.
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

    // File name of a script location, e.g. build_onnx.py for classpath:/runtime-tvm/scripts/build_onnx.py.
    public static String scriptName(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // Text content of a classpath resource, e.g. one of the pod scripts.
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

    private static String loadClasspathStatic(String location) {
        try {
            return new String(
                new org.springframework.core.io.DefaultResourceLoader().getResource(location).getContentAsByteArray(),
                StandardCharsets.UTF_8
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("failed to load classpath: " + location, e);
        }
    }

    // ContextRef telling the init container to pre-download an S3/HTTP source; null when there is no source.
    public static ContextRef inputContextRef(String s3OrHttpUri, String destination) {
        if (!StringUtils.hasText(s3OrHttpUri)) return null;
        UriComponents uri = UriComponentsBuilder.fromUriString(s3OrHttpUri).build();
        return ContextRef.builder().source(s3OrHttpUri).protocol(uri.getScheme()).destination(destination).build();
    }

    // Resolves a store:// key to the Model's S3 path; s3:// / https:// returned as-is.
    @Nullable
    public static String resolveModelPath(String path, ModelManager modelService) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }
        if (!path.startsWith(Keys.STORE_PREFIX)) {
            return path;
        }
        KeyAccessor ka = KeyAccessor.with(path);
        if (!EntityUtils.getEntityName(Model.class).equalsIgnoreCase(ka.getType())) {
            throw new CoreRuntimeException("invalid entity kind reference, expected model");
        }
        Model model;
        try {
            model =
                ka.getId() != null
                    ? modelService.findModel(ka.getId())
                    : modelService.getLatestModelByKey(ka.getProject(), path);
        } catch (NoSuchEntityException e) {
            model = null;
        }
        if (model == null) {
            throw new CoreRuntimeException("model not found for key: " + path);
        }
        Object p = model.getSpec() != null ? model.getSpec().get("path") : null;
        if (!(p instanceof String)) {
            throw new CoreRuntimeException("model spec.path is missing for: " + path);
        }
        return (String) p;
    }

    // Resolves a model key to its S3 folder; forces a trailing slash so the init container pulls the whole dir.
    public static String resolveModelDir(String modelKey, ModelManager modelService) {
        String path = resolveModelPath(modelKey, modelService);
        if (!path.endsWith("/") && !path.endsWith(".zip")) {
            path = path + "/";
        }
        return path;
    }

    public static String extractFileName(String uri) {
        if (!StringUtils.hasText(uri)) return "";
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    // Last segment of function name (no `function/tvm/` prefix or `:id`); used for servedName and Service names.
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.substring(name.lastIndexOf('/') + 1);
        int colon = clean.indexOf(':');
        return colon >= 0 ? clean.substring(0, colon) : clean;
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

    // Node architecture that can run a compiled Model, read from its spec: the target_triple
    // that tvm+compile writes in the manifest, else the mtriple or x86 mcpu of the target
    // (Models compiled before target_triple existed). Null when the spec does not tell it.
    public static String modelArchitecture(Map<String, Serializable> spec) {
        if (spec == null) {
            return null;
        }
        if (
            spec.get("manifest") instanceof Map<?, ?> manifest &&
            manifest.get("target_triple") instanceof String triple &&
            StringUtils.hasText(triple)
        ) {
            return tripleArchitecture(triple);
        }
        return spec.get("target") instanceof String target ? targetArchitecture(target) : null;
    }

    // Node architecture for a TVM target: from its mtriple, or amd64 for an x86-64 mcpu.
    // Null when the target builds for the machine running the compile (e.g. plain llvm).
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

    // Kubernetes name of the architecture in an LLVM triple: x86_64-linux-gnu -> amd64,
    // aarch64-linux-gnu -> arm64, armv7l-linux-gnueabihf -> arm. Null for the others.
    public static String tripleArchitecture(String triple) {
        String arch = triple.split("-", 2)[0].toLowerCase(Locale.ROOT);
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "amd64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        return arch.startsWith("arm") ? "arm" : null;
    }
}
