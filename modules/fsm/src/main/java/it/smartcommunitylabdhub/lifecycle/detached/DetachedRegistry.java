/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.lifecycle.detached;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Ledger of the detached operations in progress.
 *
 * Entries survive restarts (persistent implementations): an entry left without a live execution
 * after a restart is never re-run, its object moves to ERROR. Entries older than the long timeout
 * are expired.
 */
public interface DetachedRegistry {
    /**
     * Register an operation if absent, keeping the existing entry (and its start time) otherwise
     */
    @NotNull
    DetachedEntry register(@NotNull String type, @NotNull String id, @NotNull String state, @Nullable String event);

    /**
     * Record a new attempt: increments attempts and sets the last attempt time
     *
     * @return the updated entry, or null when missing
     */
    @Nullable
    DetachedEntry attempt(@NotNull String type, @NotNull String id, @NotNull String state);

    void remove(@NotNull String type, @NotNull String id, @NotNull String state);

    @NotNull
    List<DetachedEntry> list();
}
