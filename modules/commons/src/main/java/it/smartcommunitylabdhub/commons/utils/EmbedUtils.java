/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.commons.utils;

import it.smartcommunitylabdhub.commons.accessors.fields.EmbeddedFieldAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;

public class EmbedUtils {

    public static <T extends BaseDTO & MetadataDTO> T embed(T dto) {
        return embed(dto, true);
    }

    public static <T extends BaseDTO & MetadataDTO> T embed(T dto, boolean keepIds) {
        EmbeddedFieldAccessor embeddedAccessor = EmbeddedFieldAccessor.with(dto.getMetadata());
        if (embeddedAccessor.isEmbedded()) {
            //embedded dto, use as is
            return dto;
        }
        //transform into embedded representation
        if (!keepIds) {
            //no id
            dto.setId(null);
        }
        //no specs
        if (dto instanceof SpecDTO) {
            ((SpecDTO) dto).setSpec(null);
        }

        if (dto instanceof StatusDTO) {
            ((StatusDTO) dto).setStatus(null);
        }

        //keep only base metadata
        //TODO evaluate removing *all* metadata
        if (dto.getMetadata() != null) {
            dto.setMetadata(BaseMetadata.from(dto.getMetadata()).toMap());
        }

        return dto;
    }

    private EmbedUtils() {}
}
