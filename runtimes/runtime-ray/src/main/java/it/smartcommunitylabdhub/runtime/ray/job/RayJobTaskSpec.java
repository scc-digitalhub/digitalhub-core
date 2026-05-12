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
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import java.io.Serializable;
import java.util.List;
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
    @Schema(title = "fields.ray.entrypoint.title", description = "fields.ray.entrypoint.description")
    private String entrypoint;

    /**
     * Optional selector to attach the job to an existing RayCluster instead of
     * spawning a new one. When set, the cluster spec is ignored by the operator.
     */
    @JsonProperty("cluster_selector")
    @Schema(title = "fields.ray.clusterSelector.title", description = "fields.ray.clusterSelector.description")
    private Map<String, String> clusterSelector;

    /**
     * Optional override for the ray version reported in the cluster spec.
     */
    @JsonProperty("ray_version")
    @Schema(title = "fields.ray.version.title", description = "fields.ray.version.description")
    private String rayVersion;

    /**
     * Optional override for the head pod image. Falls back to the function image,
     * then to the runtime default.
     */
    @JsonProperty("head_image")
    @Schema(title = "fields.ray.headImage.title", description = "fields.ray.headImage.description")
    private String headImage;

    /**
     * Optional override for the worker pod image. Falls back to the function
     * image, then to the runtime worker default, then to {@link #headImage}.
     */
    @JsonProperty("worker_image")
    @Schema(title = "fields.ray.workerImage.title", description = "fields.ray.workerImage.description")
    private String workerImage;

    /**
     * Number of worker pod replicas. Defaults to {@code 1} when not provided.
     */
    @JsonProperty("worker_replicas")
    @Schema(title = "fields.ray.workerReplicas.title", description = "fields.ray.workerReplicas.description")
    private Integer workerReplicas;

    /**
     * Optional minimum number of worker replicas. Defaults to {@code 1} when not
     * provided.
     */
    @JsonProperty("min_worker_replicas")
    @Schema(title = "fields.ray.minWorkerReplicas.title", description = "fields.ray.minWorkerReplicas.description")
    private Integer minWorkerReplicas;

    /**
     * Optional maximum number of worker replicas. Defaults to {@code 1} when not
     * provided.
     */
    @JsonProperty("max_worker_replicas")
    @Schema(title = "fields.ray.maxWorkerReplicas.title", description = "fields.ray.maxWorkerReplicas.description")
    private Integer maxWorkerReplicas;

    /**
     * Optional resource requests/limits for worker pods. The inherited
     * {@code resources} field applies to the head pod.
     */
    @JsonProperty("worker_resources")
    @Schema(title = "fields.ray.workerResources.title", description = "fields.ray.workerResources.description")
    private CoreResource workerResources;

    /**
     * Optional volumes mounted on worker pods. The inherited {@code volumes}
     * field applies to the head pod.
     */
    @JsonProperty("worker_volumes")
    @Schema(title = "fields.ray.workerVolumes.title", description = "fields.ray.workerVolumes.description")
    private List<CoreVolume> workerVolumes;

    /**
     * Optional pod template/profile for worker pods. The inherited
     * {@code profile} field applies to the head pod.
     */
    @JsonProperty("worker_profile")
    @Schema(title = "fields.ray.workerProfile.title", description = "fields.ray.workerProfile.description")
    private String workerProfile;

    public RayJobTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayJobTaskSpec spec = mapper.convertValue(data, RayJobTaskSpec.class);

        this.workerProfile = spec.getWorkerProfile();
        this.entrypoint = spec.getEntrypoint();
        this.clusterSelector = spec.getClusterSelector();
        this.rayVersion = spec.getRayVersion();
        this.headImage = spec.getHeadImage();
        this.workerImage = spec.getWorkerImage();
        this.workerReplicas = spec.getWorkerReplicas();
        this.workerResources = spec.getWorkerResources();
        this.workerVolumes = spec.getWorkerVolumes();
    }
}
