/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.trigger.lifecycle.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerJob;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.util.Assert;

@Slf4j
public class JdbcTriggerJobStore<T extends TriggerJob> implements TriggerJobStore<T> {

    private final Class<T> clazz;
    private static final String INSERT_SQL =
        "INSERT INTO trigger_jobs (id, _user, created, updated, _clazz, _data) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE trigger_jobs SET _data = ?, updated = ? WHERE id = ? AND _clazz = ?";
    private static final String SELECT_SQL = "SELECT * FROM trigger_jobs WHERE id = ? and _clazz = ?";
    private static final String SELECT_ALL_SQL = "SELECT * FROM trigger_jobs WHERE _clazz = ?";
    private static final String DELETE_SQL = "DELETE FROM trigger_jobs WHERE id = ? AND _clazz = ?";

    private final JdbcTemplate jdbcTemplate;
    private RowMapper<T> rowMapper;

    private ObjectMapper objectMapper;

    public JdbcTriggerJobStore(DataSource dataSource, Class<T> clazz) {
        Assert.notNull(dataSource, "DataSource required");
        Assert.notNull(clazz, "class type required");

        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.clazz = clazz;
        //use CBOR mapper as default
        this.objectMapper = JacksonMapper.CBOR_OBJECT_MAPPER;

        this.rowMapper = new TriggerJobRowMapper(clazz);
    }

    @Override
    public void store(@NotNull String id, @NotNull T e) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        Assert.notNull(e, "trigger job must not be null");

        log.debug("store trigger job {}", id);

        try {
            byte[] data = objectMapper.writeValueAsBytes(e);
            Timestamp now = new Timestamp(Date.from(Instant.now()).getTime());
            SqlLobValue lob = new SqlLobValue(data);

            Optional
                .ofNullable(find(id))
                .ifPresentOrElse(
                    r ->
                        jdbcTemplate.update(
                            UPDATE_SQL,
                            new Object[] { lob, now, id, clazz.getName() },
                            new int[] { Types.BLOB, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR }
                        ),
                    () ->
                        jdbcTemplate.update(
                            INSERT_SQL,
                            new Object[] { id, e.getUser(), now, now, clazz.getName(), lob },
                            new int[] {
                                Types.VARCHAR,
                                Types.VARCHAR,
                                Types.TIMESTAMP,
                                Types.TIMESTAMP,
                                Types.VARCHAR,
                                Types.BLOB,
                            }
                        )
                );
        } catch (IOException ex) {
            // Handle serialization error
            log.error("error deserializing runnable: {}", ex.getMessage());
            throw new StoreException("error deserializing runnable");
        }
    }

    @Override
    public void remove(@NotNull String id) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        log.debug("remove trigger job {}", id);

        jdbcTemplate.update(DELETE_SQL, id, clazz.getName());
    }

    @Override
    public T find(@NotNull String id) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        log.debug("find trigger job {}", id);

        try {
            return jdbcTemplate.queryForObject(
                SELECT_SQL,
                new Object[] { id, clazz.getName() },
                new int[] { Types.VARCHAR, Types.VARCHAR },
                rowMapper
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<T> findAll() throws StoreException {
        log.debug("find all trigger jobs");

        return jdbcTemplate.query(
            SELECT_ALL_SQL,
            new Object[] { clazz.getName() },
            new int[] { Types.VARCHAR },
            rowMapper
        );
    }

    @Override
    public List<T> findMatching(Predicate<T> filter) throws StoreException {
        if (filter == null) {
            return findAll();
        }

        //apply filter predicate
        log.debug("find all matching trigger jobs");

        //TODO pushdown predicate to db or cache
        return jdbcTemplate
            .query(SELECT_ALL_SQL, new Object[] { clazz.getName() }, new int[] { Types.VARCHAR }, rowMapper)
            .stream()
            .filter(filter)
            .toList();
    }

    private class TriggerJobRowMapper implements RowMapper<T> {

        private final Class<T> clazz;

        public TriggerJobRowMapper(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString("id");
            // String user = rs.getString("_user");
            // Timestamp created = rs.getTimestamp("created");
            // Timestamp updated = rs.getTimestamp("updated");

            String clazz = rs.getString("_clazz");
            byte[] data = rs.getBytes("_data");

            if (id == null || clazz == null) {
                return null;
            }

            //sanity check
            if (!this.clazz.getName().equals(clazz)) {
                log.error("invalid class type for trigger job {}: expected {}, found {}", id, this.clazz, clazz);
                return null;
            }

            if (data == null) {
                return null;
            }

            try {
                return objectMapper.readValue(data, this.clazz);
            } catch (IOException ex) {
                // Handle serialization error
                log.error("error deserializing data: {}", ex.getMessage());
                throw new SQLException("error deserializing runnable");
            }
        }
    }
}
