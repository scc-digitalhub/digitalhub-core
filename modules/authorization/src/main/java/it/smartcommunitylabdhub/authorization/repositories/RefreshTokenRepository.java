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

package it.smartcommunitylabdhub.authorization.repositories;

import it.smartcommunitylabdhub.authorization.model.RefreshToken;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Transactional
public class RefreshTokenRepository {

    private static final String INSERT_SQL =
        "INSERT INTO refresh_tokens (id, _user, issued_at, expires_at, token, scope, ip_addr, _auth) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_SQL = "SELECT * FROM refresh_tokens WHERE id = ?";
    //TODO: use FOR UPDATE on postgresql, h2 has a different syntax...
    private static final String SELECT_FOR_UPDATE_SQL = "SELECT * FROM refresh_tokens WHERE token = ?";
    private static final String SELECT_USER_SQL =
        "SELECT * FROM refresh_tokens WHERE _user = ? ORDER BY issued_at DESC";
    private static final String DELETE_SQL = "DELETE FROM refresh_tokens WHERE id = ?";
    private static final String DELETE_EXPIRED_SQL = "DELETE FROM refresh_tokens WHERE expires_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private RowMapper<RefreshToken> rowMapper;

    public RefreshTokenRepository(DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource required");
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        this.rowMapper =
            (rs, rowNum) -> {
                RefreshToken token = new RefreshToken();
                token.setId(rs.getString("id"));
                token.setUser(rs.getString("_user"));
                token.setIssuedAt(rs.getTimestamp("issued_at"));
                token.setExpiresAt(rs.getTimestamp("expires_at"));
                token.setToken(rs.getString("token"));
                token.setScopes(StringUtils.commaDelimitedListToSet(rs.getString("scope")));
                token.setIpAddress(rs.getString("ip_addr"));
                token.setAuth(rs.getBytes("_auth"));
                return token;
            };
    }

    public void store(@NotNull String id, @NotNull RefreshToken token) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        Assert.notNull(token, "token must not be null");
        Assert.hasText(token.getUser(), "token user must not be null");
        Assert.hasText(token.getToken(), "token value must not be null");

        log.debug("store refresh token {}", id);

        byte[] data = token.getAuth();
        Timestamp createdAt = token.getIssuedAt() != null
            ? new Timestamp(token.getIssuedAt().getTime())
            : new Timestamp(Date.from(Instant.now()).getTime());
        Timestamp expiresAt = token.getExpiresAt() != null ? new Timestamp(token.getExpiresAt().getTime()) : null;
        SqlLobValue lob = new SqlLobValue(data);

        jdbcTemplate.update(
            INSERT_SQL,
            new Object[] {
                id,
                token.getUser(),
                createdAt,
                expiresAt,
                token.getToken(),
                StringUtils.collectionToCommaDelimitedString(token.getScopes()),
                token.getIpAddress(),
                lob,
            },
            new int[] {
                Types.VARCHAR,
                Types.VARCHAR,
                Types.TIMESTAMP,
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.BLOB,
            }
        );
    }

    public void remove(@NotNull String id) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        log.debug("remove refresh token {} for update", id);

        jdbcTemplate.update(DELETE_SQL, id);
    }

    public void removeByExpirationTime(@NotNull @NonNull Date expirationTime) throws StoreException {
        Assert.notNull(expirationTime, "expirationTime must not be empty");
        log.debug("remove refresh token {} expired before {}", expirationTime);

        jdbcTemplate.update(DELETE_EXPIRED_SQL, new Object[] { expirationTime }, new int[] { Types.TIMESTAMP });
    }

    public RefreshToken find(@NotNull String id) throws StoreException {
        Assert.hasText(id, "id must not be empty");
        log.debug("find refresh token {}", id);

        try {
            return jdbcTemplate.queryForObject(SELECT_SQL, new Object[] { id }, new int[] { Types.VARCHAR }, rowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public RefreshToken consume(@NotNull String value) throws StoreException {
        Assert.hasText(value, "token must not be empty");
        log.debug("consume refresh token {}", value);

        try {
            RefreshToken token = jdbcTemplate.queryForObject(
                SELECT_FOR_UPDATE_SQL,
                new Object[] { value },
                new int[] { Types.VARCHAR },
                rowMapper
            );

            if (token == null) {
                return null;
            }

            //remove
            jdbcTemplate.update(DELETE_SQL, value);

            return token;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<RefreshToken> findByUser(@NotNull String user) throws StoreException {
        Assert.hasText(user, "user must not be empty");
        log.debug("find refresh token by user {}", user);

        return jdbcTemplate.query(SELECT_USER_SQL, new Object[] { user }, new int[] { Types.VARCHAR }, rowMapper);
    }
}
