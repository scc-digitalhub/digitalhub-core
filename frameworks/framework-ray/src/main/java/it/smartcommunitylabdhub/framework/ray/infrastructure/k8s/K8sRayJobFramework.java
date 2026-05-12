/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.ray.model.ray.JobSubmissionMode;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayClusterSpec;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayJobSpec;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sRayJobFramework.FRAMEWORK)
public class K8sRayJobFramework extends K8sRayBaseFramework<K8sRayJobRunnable> {

    public static final String FRAMEWORK = "rayjob";
    public static final String KIND = "RayJob";
    public static final String PLURAL = "rayjobs";

    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days
    public static final int DEADLINE_MIN = 120;

    public static final int DEFAULT_BACKOFF_LIMIT = 0;

    private int activeDeadlineSeconds = DEADLINE_SECONDS;

    protected static final ObjectMapper yamlMapper = JacksonMapper.YAML_OBJECT_MAPPER;


    public K8sRayJobFramework(ApiClient apiClient) {
        super(apiClient);
    }

    @Autowired
    public void setActiveDeadlineSeconds(
        @Value("${ray.job.active-deadline-seconds}") Integer activeDeadlineSeconds
    ) {
        Assert.isTrue(activeDeadlineSeconds > DEADLINE_MIN, "Minimum deadline seconds is " + DEADLINE_MIN);
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    @Autowired
    public void setCpuRequestsResourceDefinition(
        @Value("${ray.job.resources.cpu.requests}") String cpuResourceDefinition
    ) {
        if (StringUtils.hasText(cpuResourceDefinition)) {
            this.cpuRequestResourceDefinition.setValue(cpuResourceDefinition);
        }
    }

    @Autowired
    public void setCpuLimitsResourceDefinition(
        @Value("${ray.job.resources.cpu.limits}") String cpuResourceDefinition
    ) {
        if (StringUtils.hasText(cpuResourceDefinition)) {
            this.cpuLimitResourceDefinition.setValue(cpuResourceDefinition);
        }
    }

    @Autowired
    public void setMemRequestsResourceDefinition(
        @Value("${ray.job.resources.mem.requests}") String memResourceDefinition
    ) {
        if (StringUtils.hasText(memResourceDefinition)) {
            //check request is a valid measure for memory
            Quantity q = Quantity.fromString(memResourceDefinition);
            if (q.getNumber().compareTo(new BigDecimal(MIN_MEM)) >= 0) {
                this.memRequestResourceDefinition.setValue(memResourceDefinition);
            } else {
                log.warn("Memory requests must be at least {} bytes", MIN_MEM);
            }
        } else {
            log.warn("Memory requests not set, removing default value");
            this.memRequestResourceDefinition.setValue(null);
        }
    }

    @Autowired
    public void setMemLimitsResourceDefinition(
        @Value("${ray.job.resources.mem.limits}") String memResourceDefinition
    ) {
        if (StringUtils.hasText(memResourceDefinition)) {
            //check request is a valid measure for memory
            Quantity q = Quantity.fromString(memResourceDefinition);
            if (q.getNumber().compareTo(new BigDecimal(MIN_MEM)) >= 0) {
                this.memLimitResourceDefinition.setValue(memResourceDefinition);
            } else {
                log.warn("Memory limits must be at least {} bytes", MIN_MEM);
            }
        }
    }

    @Autowired
    public void setEphemeralRequestsResourceDefinition(
        @Value("${ray.job.resources.ephemeral.requests}") String ephemeralResourceDefinition
    ) {
        if (StringUtils.hasText(ephemeralResourceDefinition)) {
            this.ephemeralRequestResourceDefinition.setValue(ephemeralResourceDefinition);
        }
    }

    @Autowired
    public void setEphemeralLimitsResourceDefinition(
        @Value("${ray.job.resources.ephemeral.limits}") String ephemeralResourceDefinition
    ) {
        if (StringUtils.hasText(ephemeralResourceDefinition)) {
            this.ephemeralLimitResourceDefinition.setValue(ephemeralResourceDefinition);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        //load templates
        this.templates = loadTemplates(K8sRayJobRunnable.class);
        // fixed volume
        k8sProperties.setSharedVolume(
            new CoreVolume(CoreVolume.VolumeType.empty_dir, "/shared", "shared-dir", Map.of("sizeLimit", "500Mi"))
        );

    }

    @Override
    protected String getKind() {
        return KIND;
    }

    @Override
    protected String getPlural() {
        return PLURAL;
    }

    @Override
    protected Map<String, Serializable> getSpec(K8sRayJobRunnable runnable, RayClusterSpec clusterSpec) throws K8sFrameworkException {

        int backoffLimit = Optional.ofNullable(runnable.getSpec().getBackoffLimit()).orElse(DEFAULT_BACKOFF_LIMIT).intValue();

        // submitterPodTemplate: as head, with context refs and init container 
        V1PodSpec submitter = convertPodModel(runnable, "submitter", runnable.getSpec().getCluster().getHeadSpec(), true, false);
        submitter.setRestartPolicy("Never"); // important for job mode, to avoid unintended restarts from ray operator

        RayJobSpec spec = RayJobSpec.builder()
            .jobId(runnable.getId())
            .activeDeadlineSeconds(activeDeadlineSeconds)
            .backoffLimit(backoffLimit)
            .rayClusterSpec(clusterSpec)
            .clusterSelector(runnable.getSpec().getClusterSelector())
            .entrypoint(runnable.getSpec().getEntrypoint())
            .runtimeEnvYAML(buildEnvYAML(runnable))
            .shutdownAfterJobFinishes(runnable.getSpec().getShutdownAfterJobFinishes())
            .ttlSecondsAfterFinished(runnable.getSpec().getTtlSecondsAfterFinished())
            .preRunningDeadlineSeconds(runnable.getSpec().getPreRunningDeadlineSeconds())
            .submissionMode(JobSubmissionMode.K8sJobMode)
            .submitterPodTemplate(new V1PodTemplateSpec().spec(submitter))
            .build();
        return mapper.convertValue(spec, typeRef);
    }

    @Override
    protected void setStatus(K8sRayJobRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }

    @Override
    protected K8sRunnable.K8sRunnableBuilder<?, ?> newRunnableBuilder() {
        return K8sRayJobRunnable.builder();
    }

    private String buildEnvYAML(K8sRayJobRunnable runnable) {
        Map<String, Object> map = new HashMap<>();
        map.put("working_dir", "/shared/");
        if (runnable.getEnvs() != null && !runnable.getEnvs().isEmpty() && runnable.getSpec().getClusterSelector() != null) {
            map.put("env_vars", runnable.getEnvs());
        }
        if (runnable.getSpec().getDependencyFormat() != null && runnable.getSpec().getDependencySpec() != null) {
            map.put(runnable.getSpec().getDependencyFormat(), runnable.getSpec().getDependencySpec());
        }
        try {
            return yamlMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Error converting map to YAML: {}", e.getMessage());
            return null;
        }
    }
}
