/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.lifecycle.detached.DetachedEntry;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedRegistry;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

/**
 * Persistent ledger of detached operations (JDBC, table detached_operations)
 */
@Slf4j
public class JdbcDetachedRegistry implements DetachedRegistry {

    private static final String INSERT_SQL =
        "INSERT INTO detached_operations (entity_type, entity_id, state, event, started_at, last_attempt, attempts) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String ATTEMPT_SQL =
        "UPDATE detached_operations SET attempts = attempts + 1, last_attempt = ? WHERE entity_type = ? AND entity_id = ? AND state = ?";
    private static final String SELECT_SQL =
        "SELECT * FROM detached_operations WHERE entity_type = ? AND entity_id = ? AND state = ?";
    private static final String SELECT_ALL_SQL = "SELECT * FROM detached_operations";
    private static final String DELETE_SQL =
        "DELETE FROM detached_operations WHERE entity_type = ? AND entity_id = ? AND state = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DetachedEntry> rowMapper = new DetachedEntryRowMapper();

    public JdbcDetachedRegistry(@NotNull DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource required");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public DetachedEntry register(
        @NotNull String type,
        @NotNull String id,
        @NotNull String state,
        @Nullable String event
    ) {
        try {
            jdbcTemplate.update(INSERT_SQL, type, id, state, event, Timestamp.from(Instant.now()), null, 0);
        } catch (DuplicateKeyException e) {
            //already registered: keep the existing entry
            log.trace("detached {}:{}:{} already registered", type, id, state);
        }

        DetachedEntry entry = find(type, id, state);
        if (entry == null) {
            throw new IllegalStateException("unable to register detached " + DetachedEntry.key(type, id, state));
        }
        return entry;
    }

    @Override
    public DetachedEntry attempt(@NotNull String type, @NotNull String id, @NotNull String state) {
        jdbcTemplate.update(ATTEMPT_SQL, Timestamp.from(Instant.now()), type, id, state);
        return find(type, id, state);
    }

    private DetachedEntry find(String type, String id, String state) {
        try {
            return jdbcTemplate.queryForObject(SELECT_SQL, rowMapper, type, id, state);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void remove(@NotNull String type, @NotNull String id, @NotNull String state) {
        jdbcTemplate.update(DELETE_SQL, type, id, state);
    }

    @Override
    public List<DetachedEntry> list() {
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    private static class DetachedEntryRowMapper implements RowMapper<DetachedEntry> {

        @Override
        public DetachedEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp startedAt = rs.getTimestamp("started_at");
            Timestamp lastAttempt = rs.getTimestamp("last_attempt");
            return new DetachedEntry(
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("state"),
                rs.getString("event"),
                startedAt != null ? startedAt.toInstant() : Instant.now(),
                lastAttempt != null ? lastAttempt.toInstant() : null,
                rs.getInt("attempts")
            );
        }
    }
}
