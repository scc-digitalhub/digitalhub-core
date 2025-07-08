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

package it.smartcommunitylabdhub.core.runs.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import javax.sql.DataSource;
import org.joda.time.Instant;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public class RunnableRepository {

    private static final String INSERT_SQL =
        "INSERT INTO runnables (id, _user, created, updated, _clazz, _data) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE runnables SET _data = ?, updated = ? WHERE id = ? AND _clazz = ?";
    private static final String SELECT_SQL = "SELECT * FROM runnables WHERE id = ? and _clazz = ?";
    private static final String SELECT_ALL_SQL = "SELECT * FROM runnables WHERE _clazz = ?";
    private static final String DELETE_SQL = "DELETE FROM runnables WHERE id = ? AND _clazz = ?";

    private final JdbcTemplate jdbcTemplate;
    private RowMapper<RunnableEntity> rowMapper;

    public RunnableRepository(DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource required");
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        this.rowMapper = new RunnableEntityRowMapper();
    }

    public void save(String clazz, RunnableEntity entity) {
        if (clazz == null || entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("invalid data");
        }

        Timestamp now = new Timestamp(Instant.now().toDate().getTime());
        SqlLobValue lob = new SqlLobValue(entity.getData());

        jdbcTemplate.update(
            INSERT_SQL,
            new Object[] { entity.getId(), entity.getUser(), now, now, clazz, lob },
            new int[] { Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.BLOB }
        );
    }

    public void update(String clazz, String id, RunnableEntity entity) {
        if (clazz == null || id == null || entity == null) {
            throw new IllegalArgumentException("invalid data");
        }

        Timestamp now = new Timestamp(Instant.now().toDate().getTime());
        SqlLobValue lob = new SqlLobValue(entity.getData());

        jdbcTemplate.update(
            UPDATE_SQL,
            new Object[] { lob, now, id, clazz },
            new int[] { Types.BLOB, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR }
        );
    }

    public RunnableEntity find(String clazz, String id) {
        if (clazz == null || id == null) {
            throw new IllegalArgumentException("invalid data");
        }
        try {
            return jdbcTemplate.queryForObject(
                SELECT_SQL,
                new Object[] { id, clazz },
                new int[] { Types.VARCHAR, Types.VARCHAR },
                rowMapper
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<RunnableEntity> findAll(String clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("invalid data");
        }

        return jdbcTemplate.query(SELECT_ALL_SQL, new Object[] { clazz }, new int[] { Types.VARCHAR }, rowMapper);
    }

    public void delete(String clazz, String id) {
        if (clazz == null || id == null) {
            throw new IllegalArgumentException("invalid data");
        }

        jdbcTemplate.update(DELETE_SQL, id, clazz);
    }

    private class RunnableEntityRowMapper implements RowMapper<RunnableEntity> {

        @Override
        public RunnableEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString("id");
            String user = rs.getString("_user");
            Timestamp created = rs.getTimestamp("created");
            Timestamp updated = rs.getTimestamp("updated");

            String clazz = rs.getString("_clazz");
            byte[] data = rs.getBytes("_data");

            if (id == null || clazz == null) {
                return null;
            }

            return new RunnableEntity(id, user, created, updated, clazz, data);
        }
    }
}
