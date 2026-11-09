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
import com.google.cloud.tools.jib.api.ImageReference;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseSpec;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onReady" }, type = ContainerImage.class, spec = Metadata.class)
@Component
@Slf4j
public class ContainerImagesMetadataProcessor implements Processor<ContainerImage, VersioningMetadata> {

    protected static final ObjectMapper mapper = JacksonMapper.OBJECT_MAPPER;
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    protected static final TypeReference<ArrayList<HashMap<String, Serializable>>> arrayRef = new TypeReference<
        ArrayList<HashMap<String, Serializable>>
    >() {};

    @Override
    public VersioningMetadata process(String stage, ContainerImage dto, Serializable input)
        throws CoreRuntimeException {
        try {
            if (dto == null || dto.getProject() == null) {
                return null;
            }

            ContainerImageBaseSpec spec = new ContainerImageBaseSpec();
            spec.configure(dto.getSpec());

            if (StringUtils.hasText(spec.getImage())) {
                //use image digest as version
                ImageReference ref = ImageReference.parse(spec.getImage());
                String q = ref.getQualifier();

                if (StringUtils.hasText(q)) {
                    //make sure to use the digest as the version
                    VersioningMetadata metadata = VersioningMetadata.from(dto.getMetadata());
                    metadata.setVersion(q);
                    return metadata;
                }
            }
        } catch (Exception e) {
            log.error("Error processing {} for stage {}: {}", dto.getId(), stage, e.getMessage(), e);
        }

        return null;
    }
}
