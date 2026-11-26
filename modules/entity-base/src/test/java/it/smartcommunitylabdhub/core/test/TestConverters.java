/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.test;

import it.smartcommunitylabdhub.core.repositories.MapToCborAttributeConverter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;

/**
 * Explicit converters between test entity and dto, mirroring production entity builders
 */
public final class TestConverters {

    private static final MapToCborAttributeConverter CBOR = new MapToCborAttributeConverter();

    private TestConverters() {}

    private static Map<String, Serializable> toMap(byte[] bytes) {
        Map<String, Serializable> map = CBOR.convertToEntityAttribute(bytes);
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    public static class EntityBuilder implements Converter<TestDTO, TestEntity> {

        @Override
        public TestEntity convert(TestDTO dto) {
            TestEntity e = new TestEntity();
            e.setId(dto.getId());
            e.setName(dto.getName());
            e.setKind(dto.getKind());
            e.setProject(dto.getProject());
            e.setSpec(CBOR.convertToDatabaseColumn(dto.getSpec()));
            e.setStatus(CBOR.convertToDatabaseColumn(dto.getStatus()));

            Object state = dto.getStatus() != null ? dto.getStatus().get("state") : null;
            e.setState(state != null ? state.toString() : "CREATED");
            return e;
        }
    }

    public static class DtoBuilder implements Converter<TestEntity, TestDTO> {

        @Override
        public TestDTO convert(TestEntity e) {
            Map<String, Serializable> status = toMap(e.getStatus());
            if (e.getState() != null) {
                status.put("state", e.getState());
            }

            return TestDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .kind(e.getKind())
                .project(e.getProject())
                .user(e.getCreatedBy())
                .key(e.getKey())
                .spec(toMap(e.getSpec()))
                .status(status)
                .build();
        }
    }
}
