/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.config;

import it.smartcommunitylabdhub.runtime.ray.model.RayDependencyFormat;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RayProperties {

    /**
     * Default container image used for the Ray head/worker pods when neither
     * the function nor the task provides one.
     */
    private String image;

    /**
     * Optional separate default image for worker pods. Falls back to
     * {@link #image} when not set.
     */
    private String workerImage;

    /**
     * Default Ray version reported in the cluster spec.
     */
    private String version;

    /**
     * Working directory mounted on the head/worker pods (matches the framework
     * shared volume mount path).
     */
    private String homeDir;

    /**
     * Default user ID for the Ray head/worker pods.
     */
    private Integer userId;

    /**
     * Default group ID for the Ray head/worker pods.
     */
    private Integer groupId;

    /**
     * Default password template for the Ray head/worker pods image.
     */
    private String passwdTemplate;

    /**
     * Optional list of dependencies to be added to every job (e.g. SDK packages).
     */
    private List<String> dependencies;

    /**
     * Default dependency format used to translate the simple {@code requirements}
     * list into a runtime env block (e.g. "pip", "conda").
     */
    private RayDependencyFormat dependencyFormat;

    /**
     * Default ray-start params for the head pod.
     */
    private Map<String, String> headStartParams;

    /**
     * Default ray resources advertised by the head pod (e.g. {@code {"CPU": "1"}}).
     */
    private Map<String, String> headRayResources;

    /**
     * Default ray-start params for worker pods.
     */
    private Map<String, String> workerStartParams;

    /**
     * Default ray resources advertised by worker pods.
     */
    private Map<String, String> workerRayResources;

    /**
     * Default worker group name.
     */
    private String workerGroupName;

    // ---- job lifecycle defaults (formerly task-level fields) ----

    private Integer backoffLimit;
    private Boolean shutdownAfterJobFinishes;
    private Integer ttlSecondsAfterFinished;
    private Integer preRunningDeadlineSeconds;
}
