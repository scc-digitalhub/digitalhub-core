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

package it.smartcommunitylabdhub.core.components.label;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.label.Label;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.services.LabelService;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LabelComponent {

    @Autowired
    private LabelService labelService;

    @Autowired
    private AttributeConverter<Map<String, Serializable>, byte[]> converter;

    @Async
    @EventListener
    public void receive(EntityEvent<? extends BaseEntity> event) {
        if (event.getEntity() == null || event.getEntity().getMetadata() == null) {
            return;
        }
        Map<String, Serializable> map = converter.convertToEntityAttribute(event.getEntity().getMetadata());
        BaseMetadata metadata = BaseMetadata.from(map);

        if (metadata.getLabels() != null) {
            updateLabels(event.getEntity().getProject(), metadata.getLabels());
        }
    }

    private void updateLabels(String project, Set<String> labels) {
        if (labels != null) {
            labels.forEach(label -> {
                try {
                    String value = label.trim();
                    Label l = labelService.searchLabel(project, value);
                    if (l == null) {
                        l = labelService.addLabel(project, value);
                    }
                } catch (SystemException | DuplicatedEntityException e) {
                    log.warn("updateLabels[{}][{}]:{}", project, label, e.getMessage());
                }
            });
        }
    }
}
