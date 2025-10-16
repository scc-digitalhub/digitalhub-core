/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.workflows.processor;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.base.K8sWorkflowTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume.VolumeType;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onBuilt" }, type = Run.class, spec = Spec.class)
@Component
@ConditionalOnKubernetes
@Slf4j
public class WorkflowStepRunProcessor implements Processor<Run, K8sFunctionTaskBaseSpec> {

    @Autowired
    private EntityRepository<Run> runRepository;

    @Autowired
    protected K8sBuilderHelper k8sBuilderHelper;

    @Override
    public <I> K8sFunctionTaskBaseSpec process(String stage, Run dto, I input) throws CoreRuntimeException {
        //check if run is linked to a workflow step
        RelationshipsMetadata rm = RelationshipsMetadata.from(dto.getMetadata());
        if (rm.getRelationships() != null) {
            //we pick first, there should be only one
            RelationshipDetail rd = rm
                .getRelationships()
                .stream()
                .filter(r -> RelationshipName.STEP_OF == r.getType())
                .findFirst()
                .orElse(null);

            if (rd != null) {
                try {
                    //load run to get spec
                    KeyAccessor ka = KeyAccessor.with(rd.getDest());
                    String workflowRunId = ka.getId();
                    Run run = runRepository.find(workflowRunId);

                    if (run != null) {
                        //sanity check: ownership+project must match
                        if (run.getUser() != null && !run.getUser().equals(dto.getUser())) {
                            throw new CoreRuntimeException("user-mismatch");
                        }
                        if (run.getProject() != null && !run.getProject().equals(dto.getProject())) {
                            throw new CoreRuntimeException("project-mismatch");
                        }

                        //parse and merge spec
                        K8sWorkflowTaskBaseSpec wspec = K8sWorkflowTaskBaseSpec.from(run.getSpec());
                        K8sFunctionTaskBaseSpec rspec = K8sFunctionTaskBaseSpec.from(dto.getSpec());

                        log.debug("merge workflow step spec {} into run {}", ka.getId(), dto.getId());

                        if (log.isTraceEnabled()) {
                            log.trace("workflow step spec for run {}: {}", ka.getId(), wspec.toMap());
                            log.trace("current run spec for run {}: {}", dto.getId(), rspec.toMap());
                        }

                        //resources
                        if (wspec.getResources() != null && rspec.getResources() == null) {
                            rspec.setResources(wspec.getResources());
                        }

                        //nodeselector
                        if (
                            wspec.getNodeSelector() != null &&
                            (rspec.getNodeSelector() == null || rspec.getNodeSelector().isEmpty())
                        ) {
                            rspec.setNodeSelector(wspec.getNodeSelector());
                        }

                        //affinity
                        if (wspec.getAffinity() != null && rspec.getAffinity() == null) {
                            rspec.setAffinity(wspec.getAffinity());
                        }

                        //tolerations
                        if (
                            wspec.getTolerations() != null &&
                            (rspec.getTolerations() == null || rspec.getTolerations().isEmpty())
                        ) {
                            rspec.setTolerations(wspec.getTolerations());
                        }

                        //runtimeClass
                        if (
                            StringUtils.hasText(wspec.getRuntimeClass()) &&
                            !StringUtils.hasText(rspec.getRuntimeClass())
                        ) {
                            rspec.setRuntimeClass(wspec.getRuntimeClass());
                        }

                        //priorityClass
                        if (
                            StringUtils.hasText(wspec.getPriorityClass()) &&
                            !StringUtils.hasText(rspec.getPriorityClass())
                        ) {
                            rspec.setPriorityClass(wspec.getPriorityClass());
                        }

                        //profile
                        if (StringUtils.hasText(wspec.getProfile()) && !StringUtils.hasText(rspec.getProfile())) {
                            rspec.setProfile(wspec.getProfile());
                        }

                        //env merge
                        if (wspec.getEnvs() != null) {
                            List<CoreEnv> envs = new ArrayList<>(wspec.getEnvs());
                            if (rspec.getEnvs() != null) {
                                envs.addAll(rspec.getEnvs());
                            }

                            rspec.setEnvs(envs);
                        }

                        //secrets merge
                        if (wspec.getSecrets() != null) {
                            Set<String> secrets = new HashSet<>(wspec.getSecrets());
                            if (rspec.getSecrets() != null) {
                                secrets.addAll(rspec.getSecrets());
                            }

                            rspec.setSecrets(secrets);
                        }

                        //shared volumes
                        if (wspec.getVolumes() != null) {
                            List<CoreVolume> volumes = new ArrayList<>();
                            if (rspec.getVolumes() != null) {
                                volumes.addAll(rspec.getVolumes());
                            }

                            if (wspec.getVolumes() != null) {
                                //add pvc as shared volume with explicit claimName
                                wspec
                                    .getVolumes()
                                    .stream()
                                    .filter(v -> VolumeType.persistent_volume_claim == v.getVolumeType())
                                    .forEach(v -> {
                                        volumes.add(
                                            new CoreVolume(
                                                VolumeType.shared_volume,
                                                v.getMountPath(),
                                                v.getName(),
                                                Map.of(
                                                    "claimName",
                                                    k8sBuilderHelper.getVolumeName(workflowRunId, v.getName())
                                                )
                                            )
                                        );
                                    });
                            }

                            rspec.setVolumes(volumes);
                        }

                        if (log.isTraceEnabled()) {
                            log.trace("processed run spec for run {}: {}", dto.getId(), rspec.toMap());
                        }

                        return rspec;
                    }
                } catch (StoreException e) {
                    log.error("Error loading workflow step run {} for {}", rd.getDest(), dto.getId(), e);
                    throw new CoreRuntimeException("storeException");
                }
            }
        }

        return null;
    }
}
