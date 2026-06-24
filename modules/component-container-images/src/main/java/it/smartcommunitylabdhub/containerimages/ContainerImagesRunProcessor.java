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

package it.smartcommunitylabdhub.containerimages;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.filter.ContainerImageEntityFilter;
import it.smartcommunitylabdhub.containerimage.lifecycle.ContainerImageState;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseStatus;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageSpec;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@ProcessorType(stages = { "onPending" }, type = Run.class, spec = Metadata.class)
@Component
@Slf4j
public class ContainerImagesRunProcessor implements Processor<Run, Metadata> {

    @Autowired
    private EntityService<ContainerImage> imageService;

    @Override
    public Metadata process(String stage, Run dto, Serializable input) throws CoreRuntimeException {
        try {
            if (dto == null || dto.getProject() == null) {
                return null;
            }

            String project = dto.getProject();
            RunSpecAccessor specAccessor = RunSpecAccessor.with(dto.getSpec());

            if (input instanceof K8sContainerBuilderRunnable) {
                //skip k8s build run, we don't want to create a container image for it
                //note: there is a dedicated processor registering the image on completed
                return null;
            }

            //check if run uses an image
            if (input instanceof K8sRunnable runnable) {
                //a k8s runnable needs to have an image
                //note: we don't check spec.image because runtimes could mean different things with it
                String image = runnable.getImage();
                if (image == null) {
                    return null;
                }

                log.debug("Processing run {} for image {}", dto.getId(), image);

                UriComponents uri = UriComponentsBuilder.fromUriString(image).build();
                String baseName = Optional.ofNullable(uri.getPath())
                    .map(path -> path.split(":")[0])
                    .map(path -> {
                        String[] parts = path.split("/");
                        int len = parts.length;
                        if (len >= 2) {
                            return parts[len - 2] + "/" + parts[len - 1];
                        }
                        return parts[len - 1];
                    })
                    .orElse(image);

                //TODO evaluate if we want to use the function name as image name or the image itself
                // String name = StringUtils.hasText(specAccessor.getFunction()) ?
                // specAccessor.getFunction() : baseName;
                String name = baseName
                    .chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .map(c -> c.matches(Keys.SLUG_PATTERN) ? c : "-")
                    .collect(Collectors.joining());

                //check if we have a container image with this name
                ContainerImageEntityFilter filter = new ContainerImageEntityFilter();
                filter.setImage(image);

                List<ContainerImage> images = imageService.searchByProject(project, filter.toSearchFilter());
                if (images.isEmpty()) {
                    log.debug("Create container image for image {}", image);

                    //create a new registration for this image
                    ContainerImage ci = ContainerImage.builder()
                        .project(project)
                        .name(image)
                        .kind(ContainerImageSpec.KIND)
                        .user(dto.getUser())
                        .name(name)
                        .status(Map.of("state", ContainerImageState.READY.name()))
                        .build();

                    ContainerImageSpec spec = new ContainerImageSpec();
                    spec.setImage(image);
                    ci.setSpec(spec.toMap());

                    ci = imageService.create(ci);

                    if (log.isTraceEnabled()) {
                        log.trace("image: {}", ci);
                    }

                    //update run lineage with relationship to this image
                    RelationshipDetail rel = new RelationshipDetail(RelationshipName.CONSUMES, null, ci.getKey());
                    RelationshipsMetadata metadata = RelationshipsMetadata.from(dto.getMetadata());
                    List<RelationshipDetail> relationships = new ArrayList<>();

                    if (metadata.getRelationships() != null) {
                        relationships.addAll(metadata.getRelationships());
                    }
                    relationships.add(rel);
                    metadata.setRelationships(relationships);

                    if (log.isTraceEnabled()) {
                        log.trace("run metadata: {}", metadata);
                    }

                    return metadata;
                } else {
                    //mark as READY if not already
                    for (ContainerImage ci : images) {
                        StatusFieldAccessor statusAccessor = StatusFieldAccessor.with(ci.getStatus());
                        if (
                            ci.getStatus() == null ||
                            !ContainerImageState.READY.name().equals(statusAccessor.getState())
                        ) {
                            //set to READY, will trigger refresh when needed
                            ci.setStatus(
                                MapUtils.mergeMultipleMaps(
                                    ci.getStatus(),
                                    Map.of("state", ContainerImageState.READY.name())
                                )
                            );
                            imageService.update(ci.getId(), ci);
                        } else {
                            //check if we miss status, we'll mark it and then next time it will be refreshed
                            //note: we avoid refreshing here because we don't wanna delay the run processing
                            ContainerImageBaseStatus currentStatus = ContainerImageBaseStatus.with(ci.getStatus());
                            if (currentStatus.getDigest() == null || currentStatus.getMediaType() == null) {
                                //set to CREATED, will trigger refresh when needed
                                ci.setStatus(
                                    MapUtils.mergeMultipleMaps(
                                        ci.getStatus(),
                                        Map.of("state", ContainerImageState.CREATED.name())
                                    )
                                );
                                imageService.update(ci.getId(), ci);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing run {} for stage {}: {}", dto.getId(), stage, e.getMessage(), e);
        }

        return null;
    }
}
