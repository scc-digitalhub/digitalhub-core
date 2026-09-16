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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

// Stateless helpers shared by the TVM runners: pod scripts, model references, names and
// CPU quantities.
public final class TvmRunnerHelper {

    public static final String ENTRYPOINT_NAME = "entrypoint.sh";
    public static final String TASK_SCRIPT_NAME = "task.py";
    // Shared SDK helper (publishes the Model, records the run output) injected into every pod.
    public static final String DH_PUBLISH_SCRIPT_NAME = "_dh_publish.py";
    public static final String DH_PUBLISH_CLASSPATH = "classpath:/runtime-tvm/docker/_dh_publish.py";

    private static final DefaultResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();
    private static String dhPublishScriptCache;

    private TvmRunnerHelper() {}

    // Files injected into every TVM Job pod: the entrypoint, the task script (always
    // mounted as task.py) and the publish helper.
    public static List<ContextSource> createContextSources(@NotNull String entrypoint, @NotNull String taskScript) {
        if (dhPublishScriptCache == null) {
            dhPublishScriptCache = loadClasspath(DH_PUBLISH_CLASSPATH);
        }
        List<ContextSource> sources = new ArrayList<>();
        sources.add(base64Source(ENTRYPOINT_NAME, entrypoint));
        sources.add(base64Source(TASK_SCRIPT_NAME, taskScript));
        sources.add(base64Source(DH_PUBLISH_SCRIPT_NAME, dhPublishScriptCache));
        return sources;
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
}
