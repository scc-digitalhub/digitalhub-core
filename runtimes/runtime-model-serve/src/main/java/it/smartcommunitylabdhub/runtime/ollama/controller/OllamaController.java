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

package it.smartcommunitylabdhub.runtime.ollama.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.runtime.ollama.service.OllamaRepositoryClient;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/models")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Ollama Model context API", description = "Endpoints related to Ollama models management in project")
public class OllamaController {

    @Autowired
    private OllamaRepositoryClient client;

    @Autowired
    private EntityService<Model> modelService;

    @GetMapping(path = "/{id}/modelcard/" + OllamaModelSpec.KIND, produces = "application/json; charset=UTF-8")
    public ModelCard getModelCard(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        Model model = modelService.get(id);

        //check for project and name match
        if (!model.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (!OllamaModelSpec.KIND.equals(model.getKind())) {
            throw new IllegalArgumentException("invalid model kind");
        }
        OllamaModelSpec spec = new OllamaModelSpec();
        spec.configure(model.getSpec());

        String path = spec.getPath();
        if (path == null || path.isEmpty() || !path.startsWith("ollama://")) {
            return null;
        }

        //get model name
        String modelName = path.substring("ollama://".length());

        return new ModelCard(client.getReadme(modelName), "text/html");
    }

    private record ModelCard(String content, String format) {}
}
