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

package it.smartcommunitylabdhub.folder.persistence;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.lifecycle.FolderState;
import it.smartcommunitylabdhub.folder.specs.FolderBaseSpec;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class FolderEntityBuilder implements Converter<Folder, FolderEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public FolderEntityBuilder(AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;
    }

    @Override
    public FolderEntity convert(@NonNull Folder dto) {
        // Extract data
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(dto.getStatus());
        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        FolderBaseSpec spec = new FolderBaseSpec();
        spec.configure(dto.getSpec());

        return FolderEntity.builder()
            .id(dto.getId())
            .name(dto.getName())
            .kind(dto.getKind())
            .project(dto.getProject())
            .parentId(spec.getParentId())
            .metadata(converter.convertToDatabaseColumn(dto.getMetadata()))
            .spec(converter.convertToDatabaseColumn(dto.getSpec()))
            .status(converter.convertToDatabaseColumn(dto.getStatus()))
            .state(
                // Store status if not present
                statusFieldAccessor.getState() == null
                    ? FolderState.READY.name()
                    : FolderState.valueOf(statusFieldAccessor.getState()).name()
            )
            // Metadata Extraction
            .created(
                metadata.getCreated() != null
                    ? Date.from(metadata.getCreated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                    : null
            )
            .updated(
                metadata.getUpdated() != null
                    ? Date.from(metadata.getUpdated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                    : null
            )
            .build();
    }
}
