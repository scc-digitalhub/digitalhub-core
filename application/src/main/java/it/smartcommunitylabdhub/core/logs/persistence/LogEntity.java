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

package it.smartcommunitylabdhub.core.logs.persistence;

import it.smartcommunitylabdhub.core.logs.LogServiceImpl;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity;
import it.smartcommunitylabdhub.core.persistence.SpecEntity;
import it.smartcommunitylabdhub.core.persistence.StatusEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.sql.Types;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@Entity
@Table(name = "logs")
public class LogEntity extends AbstractEntity implements SpecEntity, StatusEntity {

    @Column(nullable = false)
    private String run;

    @Lob
    @JdbcTypeCode(Types.LONGVARBINARY)
    @Column(length = LogServiceImpl.MAX_LENGTH)
    @ToString.Exclude
    private byte[] content;

    @JdbcTypeCode(Types.LONGVARBINARY)
    @ToString.Exclude
    protected byte[] spec;

    @JdbcTypeCode(Types.LONGVARBINARY)
    @ToString.Exclude
    protected byte[] status;

    @Override
    public @NotNull String getName() {
        return id;
    }

    @Override
    public void setName(String name) {
        //not available
    }

    @Override
    public @NotNull String getKind() {
        return "log";
    }
}
