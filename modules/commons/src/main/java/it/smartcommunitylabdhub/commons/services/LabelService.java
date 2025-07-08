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

package it.smartcommunitylabdhub.commons.services;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.label.Label;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LabelService {
    public List<Label> findLabelsByProject(@NotNull String project) throws SystemException;

    public Page<Label> findLabelsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    public Page<Label> searchLabels(@NotNull String project, @NotNull String label, Pageable pageable)
        throws SystemException;

    public Label findLabel(@NotNull String id) throws SystemException;

    public Label searchLabel(@NotNull String project, @NotNull String label) throws SystemException;

    public Label addLabel(@NotNull String project, @NotNull String label)
        throws DuplicatedEntityException, SystemException;

    public void deleteLabel(@NotNull String id) throws SystemException;

    public void deleteLabelsByProject(@NotNull String project) throws SystemException;
}
