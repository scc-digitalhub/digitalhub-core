/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.files.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.files.models.FilesInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FilesInfoEntityBuilder implements Converter<FilesInfo, FilesInfoEntity> {

    private final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    public FilesInfoEntity build(FilesInfo dto) {
        byte[] value = null;
        try {
            if (dto.getFiles() != null) {
                value = mapper.writeValueAsBytes(dto.getFiles());
            }
        } catch (JsonProcessingException e) {
            log.error("FilesInfoEntity build error: {}", e.getMessage());
        }

        return FilesInfoEntity
            .builder()
            .id(dto.getId())
            .entityName(dto.getEntityName())
            .entityId(dto.getEntityId())
            .files(value)
            .build();
    }

    @Override
    public FilesInfoEntity convert(FilesInfo source) {
        return build(source);
    }
}
