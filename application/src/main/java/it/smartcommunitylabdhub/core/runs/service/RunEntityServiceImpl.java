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

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.utils.NamesGenerator;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class RunEntityServiceImpl extends BaseEntityServiceImpl<Run, RunEntity> {

    private NamesGenerator nameGenerator;

    @Autowired(required = false)
    public void setNameGenerator(NamesGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    @Override
    public Run create(@NotNull Run dto)
        throws IllegalArgumentException, BindException, DuplicatedEntityException, StoreException {
        //generate random name if missing
        if (nameGenerator != null && !StringUtils.hasText(dto.getName())) {
            String name = nameGenerator.generateKey();
            dto.setName(name);
        }
        return super.create(dto);
    }

    @Override
    protected LifecycleManager<Run> getLifecycleManager() {
        //disable direct lm access to avoid triggering on externally managed
        //TODO refactor
        return null;
    }
}
