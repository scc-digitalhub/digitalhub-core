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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

// Stateless helpers shared by the TVM runners: ContextSources, model/S3 path resolution, name cleanup.
public final class TvmRunnerHelper {

    public static final String ENTRYPOINT_NAME = "entrypoint.sh";
    public static final String TASK_SCRIPT_NAME = "task.py";
    // Shared SDK helper (dh.log_model + run.set_status) injected into every pod.
    public static final String DH_PUBLISH_SCRIPT_NAME = "_dh_publish.py";
    public static final String DH_PUBLISH_CLASSPATH = "classpath:/runtime-tvm/docker/_dh_publish.py";

    private static String dhPublishScriptCache;

    private TvmRunnerHelper() {}

    // Files injected into every TVM Job pod: entrypoint, per-task script (mounted as task.py), publish helper.
    public static List<ContextSource> createContextSources(
        @NotNull String entrypoint,
        @NotNull String taskScript
    ) {
        List<ContextSource> sources = new ArrayList<>();
        sources.add(b64Source(ENTRYPOINT_NAME, entrypoint));
        sources.add(b64Source(TASK_SCRIPT_NAME, taskScript));
        if (dhPublishScriptCache == null) {
            dhPublishScriptCache = loadClasspathStatic(DH_PUBLISH_CLASSPATH);
        }
        sources.add(b64Source(DH_PUBLISH_SCRIPT_NAME, dhPublishScriptCache));
        return sources;
    }

    // ContextSource with base64-encoded UTF-8 content, as the injector expects.
    private static ContextSource b64Source(String name, String content) {
        return ContextSource
            .builder()
            .name(name)
            .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
            .build();
    }

    private static String loadClasspathStatic(String location) {
        try {
            return new String(
                new org.springframework.core.io.DefaultResourceLoader()
                    .getResource(location)
                    .getContentAsByteArray(),
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
        return ContextRef
            .builder()
            .source(s3OrHttpUri)
            .protocol(uri.getScheme())
            .destination(destination)
            .build();
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
            model = ka.getId() != null
                ? modelService.findModel(ka.getId())
                : modelService.getLatestModel(ka.getProject(), ka.getName());
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
        if (name == null) return null;
        String n = name;
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        int colon = n.indexOf(':');
        if (colon >= 0) n = n.substring(0, colon);
        return n;
    }
}
