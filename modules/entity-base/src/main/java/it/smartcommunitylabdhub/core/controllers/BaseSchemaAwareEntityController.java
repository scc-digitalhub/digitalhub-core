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

package it.smartcommunitylabdhub.core.controllers;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

public abstract class BaseSchemaAwareEntityController<D extends BaseDTO> {

    protected SchemaService<D> schemaService;

    @Autowired
    public void setSchemaService(SchemaService<D> schemaService) {
        this.schemaService = schemaService;
    }

    @Operation(
        summary = "List entity schemas",
        description = "Return a list of all the spec schemas available for the given entity"
    )
    @GetMapping(path = "/schemas")
    public ResponseEntity<Page<Schema>> listSchemas(
        @RequestParam(required = false) Optional<String> runtime,
        Pageable pageable
    ) {
        Collection<Schema> schemas = runtime.isPresent()
            ? schemaService.listSchemas(runtime.get())
            : schemaService.listSchemas();
        PageImpl<Schema> page = new PageImpl<>(new ArrayList<>(schemas), pageable, schemas.size());

        return ResponseEntity.ok(page);
    }

    @GetMapping(path = "/schemas/{kind}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Schema> getSchema(@PathVariable @NotBlank String kind) {
        Schema schema = schemaService.getSchema(kind);

        return ResponseEntity.ok(schema);
    }
}
