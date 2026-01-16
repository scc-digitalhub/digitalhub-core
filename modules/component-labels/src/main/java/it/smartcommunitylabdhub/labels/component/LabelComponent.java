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

package it.smartcommunitylabdhub.labels.component;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.components.cloud.CloudEntityEvent;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.labels.LabelService;
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

    @Async
    @EventListener
    public void receive(CloudEntityEvent<? extends BaseDTO> event) {
        if (
            !(event.getDto() instanceof MetadataDTO) ||
            (event.getAction().equals(EntityAction.DELETE) || event.getAction().equals(EntityAction.READ))
        ) {
            return;
        }

        //register labels from metadata on writes
        var dto = (MetadataDTO & BaseDTO) event.getDto();
        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());

        if (metadata.getLabels() != null) {
            updateLabels(dto.getProject(), metadata.getLabels());
        }
    }

    private void updateLabels(String project, Set<String> labels) {
        if (labels != null) {
            try {
                labelService.addLabels(project, labels);
            } catch (StoreException e) {
                log.error("error when storing labels: {}", e.getMessage());
            }
        }
    }
}
