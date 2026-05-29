/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.job;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Task spec for the {@code ray+job} task.
 *
 * <p>Inherits the K8s function task base, whose fields ({@code resources},
 * {@code volumes}, {@code envs}, {@code secrets}, {@code profile}) describe
 * the <em>head</em> pod. The {@code envs} and {@code secrets} are also
 * propagated to worker pods via the ray runtime environment.</p>
 *
 * <p>The cluster topology is fixed to one head + one worker group. Worker pod
 * sizing is provided through dedicated {@code worker_*} fields. Defaults for
 * image, ray-start params, ray resources and job lifecycle (backoff,
 * shutdown/ttl) come from the runtime properties and may be overridden here.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = RayRuntime.RUNTIME, kind = RayJobTaskSpec.KIND, entity = Task.class)
public class RayJobTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "ray+job";

    /**
     * Optional override for the ray job entrypoint command.
     * When omitted, the runtime derives it from the function source code
     * (e.g. {@code python /shared/main.py}).
     */
    // @Schema(title = "fields.ray.entrypoint.title", description = "fields.ray.entrypoint.description")
    // private String entrypoint;

    /**
     * Optional selector to attach the job to an existing RayCluster instead of
     * spawning a new one. When set, the cluster spec is ignored by the operator.
     */
    // @JsonProperty("cluster_selector")
    // @Schema(title = "fields.ray.clusterSelector.title", description = "fields.ray.clusterSelector.description")
    // private Map<String, String> clusterSelector;

    /**
     * Number of worker pod replicas. Defaults to {@code 1} when not provided.
     */
    @JsonProperty("replicas")
    @Schema(title = "fields.ray.replicas.title", description = "fields.ray.replicas.description")
    private Integer replicas;

    /**
     * Optional minimum number of worker replicas. Defaults to {@code 1} when not
     * provided.
     */
    @JsonProperty("min_replicas")
    @Schema(title = "fields.ray.minReplicas.title", description = "fields.ray.minReplicas.description")
    private Integer minReplicas;

    /**
     * Optional maximum number of worker replicas. Defaults to {@code 1} when not
     * provided.
     */
    @JsonProperty("max_replicas")
    @Schema(title = "fields.ray.maxReplicas.title", description = "fields.ray.maxReplicas.description")
    private Integer maxReplicas;

    /**
     * Optional resource requests/limits for head pods. The inherited
     * {@code resources} field applies to the head pod.
     */
    // @JsonProperty("head_resources")
    // @Schema(title = "fields.ray.headResources.title", description = "fields.ray.headResources.description")
    // private CoreResource headResources;

    public RayJobTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayJobTaskSpec spec = mapper.convertValue(data, RayJobTaskSpec.class);

        // this.entrypoint = spec.getEntrypoint();
        // this.clusterSelector = spec.getClusterSelector();
        this.replicas = spec.getReplicas();
        this.minReplicas = spec.getMinReplicas();
        this.maxReplicas = spec.getMaxReplicas();
        // this.headResources = spec.getHeadResources();
    }
}
