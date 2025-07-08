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

package it.smartcommunitylabdhub.authorization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@EntityListeners({ AuditingEntityListener.class })
@Table(name = "resource_shares")
public class ResourceShareEntity {

    @Id
    private String id;

    @NotNull
    @Column(name = "project", updatable = false)
    private String project;

    @NotNull
    @Column(name = "entity_type", updatable = false)
    private String entity;

    @NotNull
    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @CreatedBy
    @Column(name = "owner", updatable = false)
    private String owner;

    @NotNull
    @Column(name = "username", updatable = false)
    private String user;

    @CreatedDate
    @Column(name = "issued_at", updatable = false)
    private Date issuedTime;

    @Column(name = "expires_at")
    private Date expirationTime;
}
