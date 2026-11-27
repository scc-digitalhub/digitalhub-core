/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.lifecycle.detached;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A registered detached operation: the object (type + id) in the state written by the detached step.
 *
 * @param type        the object type (DTO class name)
 * @param id          the object id
 * @param state       the state written by the detached step
 * @param event       the event which started it, informative
 * @param startedAt   first launch, never reset by loops
 * @param lastAttempt last (re)launch
 * @param attempts    number of launches, drives the backoff
 */
public record DetachedEntry(
    @NotNull String type,
    @NotNull String id,
    @NotNull String state,
    @Nullable String event,
    @NotNull Instant startedAt,
    @Nullable Instant lastAttempt,
    int attempts
) {
    public String key() {
        return key(type, id, state);
    }

    public static String key(String type, String id, String state) {
        return type + ":" + id + ":" + state;
    }
}
