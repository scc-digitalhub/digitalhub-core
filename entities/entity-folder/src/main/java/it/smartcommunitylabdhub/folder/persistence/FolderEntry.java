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

package it.smartcommunitylabdhub.folder.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
@ToString
@Entity
@Table(
    name = "folder_entries",
    indexes = {
        @Index(name = "folder_entries_prj_index", columnList = "project"),
        @Index(name = "folder_entries_prj_entry", columnList = "project, entry_name"),
        @Index(name = "folder_entries_prj_folder_entry", columnList = "project, folder_id, entry_name", unique = true),
    }
)
@EntityListeners({ AuditingEntityListener.class })
public class FolderEntry implements Serializable {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false, updatable = false)
    private String project;

    @Column(nullable = true, name = "folder_id")
    private String folderId;

    @Column(nullable = false, name = "entry_type", updatable = false)
    private String type;

    @Column(nullable = false, name = "entry_kind", updatable = false)
    private String kind;

    @Column(nullable = false, name = "entry_name", updatable = false)
    private String name;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    protected String user;

    @LastModifiedDate
    private Date updated;

    private Long size;
}
