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
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseSpec;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseStatus;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onDeleted" }, type = ContainerImage.class, spec = Status.class)
@Component
@Slf4j
public class ContainerImagesDeleteProcessor implements Processor<ContainerImage, ContainerImageBaseStatus> {

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
                log.debug("Deleting image from registry: {}", spec.getImage());

                //try to delete the image from the registry when possible
                imagesService.deleteImage(spec.getImage());
            }
        } catch (Exception e) {
            log.error("Error processing {} for stage {}: {}", dto.getId(), stage, e.getMessage(), e);
        }

        return null;
    }
}
