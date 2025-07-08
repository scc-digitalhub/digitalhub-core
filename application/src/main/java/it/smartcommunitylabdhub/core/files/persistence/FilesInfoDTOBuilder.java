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

package it.smartcommunitylabdhub.core.files.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.commons.models.files.FilesInfo;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FilesInfoDTOBuilder implements Converter<FilesInfoEntity, FilesInfo> {

    private static final TypeReference<List<FileInfo>> typeRef = new TypeReference<>() {};

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    public FilesInfo build(FilesInfoEntity entity) {
        List<FileInfo> files = null;
        try {
            if ((entity.getFiles() != null) && entity.getFiles().length > 0) {
                files = mapper.readValue(entity.getFiles(), typeRef);
            }
        } catch (IOException e) {
            log.error("FilesInfo build error: {}", e.getMessage());
        }

        return FilesInfo
            .builder()
            .id(entity.getId())
            .entityName(entity.getEntityName())
            .entityId(entity.getEntityId())
            .files(files)
            .build();
    }

    @Override
    public FilesInfo convert(FilesInfoEntity source) {
        return build(source);
    }
}
