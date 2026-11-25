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

package it.smartcommunitylabdhub.triggers.service;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.triggers.persistence.TriggerEntity;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;

@Service
@Transactional
@Validated
@Slf4j
public class TriggerEntityServiceImpl extends BaseEntityServiceImpl<Trigger, TriggerEntity> {

	@Override
	public Trigger create(@NotNull Trigger dto)
		throws IllegalArgumentException, BindException, DuplicatedEntityException, StoreException {
		if (!StringUtils.hasText(dto.getProject())) {
			throw new IllegalArgumentException("invalid or missing project");
		}

		TriggerBaseSpec baseSpec = TriggerBaseSpec.from(dto.getSpec());
		if (!StringUtils.hasText(baseSpec.getTask())) {
			throw new IllegalArgumentException("spec: missing task");
		}
		if (!StringUtils.hasText(baseSpec.getFunction()) && !StringUtils.hasText(baseSpec.getWorkflow())) {
			throw new IllegalArgumentException("spec: missing function or workflow");
		}

		RunSpecAccessor specAccessor = RunSpecAccessor.with(dto.getSpec());
		if (!dto.getProject().equals(specAccessor.getProject())) {
			throw new IllegalArgumentException("project mismatch");
		}

		return super.create(dto);
	}
}
