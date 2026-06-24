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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseSpec;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseStatus;
import it.smartcommunitylabdhub.containerimages.ContainerImagesService.LayerInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onReady" }, type = ContainerImage.class, spec = Status.class)
@Component
@Slf4j
public class ContainerImagesProcessor implements Processor<ContainerImage, ContainerImageBaseStatus> {

    protected static final ObjectMapper mapper = JacksonMapper.OBJECT_MAPPER;
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    protected static final TypeReference<ArrayList<HashMap<String, Serializable>>> arrayRef = new TypeReference<
        ArrayList<HashMap<String, Serializable>>
    >() {};

    @Autowired(required = false)
    private ContainerImagesService imagesService;

    @Override
    public ContainerImageBaseStatus process(String stage, ContainerImage dto, Serializable input)
        throws CoreRuntimeException {
        if (imagesService == null) {
            return null;
        }

        try {
            if (dto == null || dto.getProject() == null) {
                return null;
            }

            ContainerImageBaseSpec spec = new ContainerImageBaseSpec();
            spec.configure(dto.getSpec());

            if (StringUtils.hasText(spec.getImage())) {
                ContainerImageBaseStatus status = new ContainerImageBaseStatus();

                //fetch manifest and details
                ManifestAndDigest<ManifestTemplate> manifestAndDigest = imagesService.getManifest(spec.getImage());
                if (manifestAndDigest != null) {
                    status.setManifest(mapper.convertValue(manifestAndDigest.getManifest(), typeRef));
                    status.setDigest(ContainerImagesService.getDigest(spec.getImage(), manifestAndDigest));
                    status.setMediaType(
                        manifestAndDigest.getManifest().getSchemaVersion() == 2
                            ? manifestAndDigest.getManifest().getManifestMediaType()
                            : "application/vnd.docker.distribution.manifest.v2+json"
                    );
                    status.setSize(ContainerImagesService.getSize(spec.getImage(), manifestAndDigest.getManifest()));

                    //try to fetch layers details if supported by registry
                    try {
                        List<LayerInfo> layerinfo = imagesService.getLayers(spec.getImage());

                        if (layerinfo != null && !layerinfo.isEmpty()) {
                            List<Map<String, Serializable>> layers = new ArrayList<>(
                                mapper.convertValue(layerinfo, arrayRef)
                            );
                            status.setLayers(layers);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch layers for image {}: {}", spec.getImage(), e.getMessage());
                    }

                    return status;
                }
            }
        } catch (Exception e) {
            log.error("Error processing {} for stage {}: {}", dto.getId(), stage, e.getMessage(), e);
        }

        return null;
    }
}
