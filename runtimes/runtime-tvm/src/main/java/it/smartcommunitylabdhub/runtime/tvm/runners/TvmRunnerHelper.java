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

    // Tells the init container to download an S3/HTTP source into the pod; null when there
    // is no source.
    public static ContextRef inputContextRef(String s3OrHttpUri, String destination) {
        if (!StringUtils.hasText(s3OrHttpUri)) {
            return null;
        }
        UriComponents uri = UriComponentsBuilder.fromUriString(s3OrHttpUri).build();
        return ContextRef.builder().source(s3OrHttpUri).protocol(uri.getScheme()).destination(destination).build();
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
                    : modelManager.getLatestModel(accessor.getProject(), accessor.getName());
        } catch (NoSuchEntityException e) {
            model = null;
        }
        if (model == null) {
            throw new CoreRuntimeException("model not found for key: " + key);
        }
        return model;
    }

    // Storage path of a model reference: a store:// key becomes the Model's spec.path,
    // while s3:// and https:// paths are returned as they are.
    public static String resolveModelPath(String reference, ModelManager modelManager) {
        if (!StringUtils.hasText(reference)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }
        if (!reference.startsWith(Keys.STORE_PREFIX)) {
            return reference;
        }
        Model model = resolveModel(reference, modelManager);
        Object path = model.getSpec() != null ? model.getSpec().get("path") : null;
        if (!(path instanceof String text)) {
            throw new CoreRuntimeException("model spec.path is missing for: " + reference);
        }
        return text;
    }

    // Like resolveModelPath, with a trailing slash so the init container downloads the whole
    // folder (model.so or model.relax.json together with metadata.json). Zip archives are
    // left as they are.
    public static String resolveModelDir(String modelKey, ModelManager modelManager) {
        String path = resolveModelPath(modelKey, modelManager);
        return path.endsWith("/") || path.endsWith(".zip") ? path : path + "/";
    }

    // Model kind named in a store:// key (store://<project>/model/<kind>/<name>); null for
    // plain paths.
    public static String modelKindOf(String reference) {
        if (reference == null || !reference.startsWith(Keys.STORE_PREFIX)) {
            return null;
        }
        return KeyAccessor.with(reference).getKind();
    }

    // Last path segment of a URI, ignoring a trailing slash.
    public static String extractFileName(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    // Function name without the "function/tvm/" prefix and the ":id" suffix; used for the
    // served model name and the Service names.
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
