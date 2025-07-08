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

package it.smartcommunitylabdhub.core.relationships.persistence;

import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.LastModifiedDate;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
@ToString
@Entity
@Table(
    name = "relationships",
    indexes = {
        @Index(name = "relationships_project_index", columnList = "project"),
        @Index(name = "relationships_source_index", columnList = "sourceId"),
        @Index(name = "relationships_dest_index", columnList = "destId"),
    }
)
public class RelationshipEntity {

    @Id
    @Column(unique = true, updatable = false)
    private String id;

    @Column(nullable = false)
    private RelationshipName relationship;

    @Column(nullable = false, updatable = false)
    private String project;

    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private String sourceKey;

    @Column(nullable = false)
    private String destType;

    @Column(nullable = false)
    private String destId;

    @Column(nullable = false)
    private String destKey;

    @LastModifiedDate
    private Date updated;
}
