/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.test;

import it.smartcommunitylabdhub.core.persistence.AbstractEntity;
import it.smartcommunitylabdhub.core.persistence.SpecEntity;
import it.smartcommunitylabdhub.core.persistence.StatusEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_entity")
@Getter
@Setter
@NoArgsConstructor
public class TestEntity extends AbstractEntity implements SpecEntity, StatusEntity {

    @Column(nullable = false)
    private String name;

    @Lob
    private byte[] spec;

    @Lob
    private byte[] status;

    private String state;
}
