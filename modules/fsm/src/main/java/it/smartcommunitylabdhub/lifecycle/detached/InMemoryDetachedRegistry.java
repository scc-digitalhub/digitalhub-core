/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.lifecycle.detached;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non persistent registry: entries are lost on restart (no relaunch). For tests and unwired setups.
 */
public class InMemoryDetachedRegistry implements DetachedRegistry {

    private final ConcurrentHashMap<String, DetachedEntry> entries = new ConcurrentHashMap<>();

    @Override
    public DetachedEntry register(
        @NotNull String type,
        @NotNull String id,
        @NotNull String state,
        @Nullable String event
    ) {
        return entries.computeIfAbsent(DetachedEntry.key(type, id, state), k ->
            new DetachedEntry(type, id, state, event, Instant.now(), null, 0)
        );
    }

    @Override
    public DetachedEntry attempt(@NotNull String type, @NotNull String id, @NotNull String state) {
        return entries.computeIfPresent(DetachedEntry.key(type, id, state), (k, e) ->
            new DetachedEntry(e.type(), e.id(), e.state(), e.event(), e.startedAt(), Instant.now(), e.attempts() + 1)
        );
    }

    @Override
    public void remove(@NotNull String type, @NotNull String id, @NotNull String state) {
        entries.remove(DetachedEntry.key(type, id, state));
    }

    @Override
    public List<DetachedEntry> list() {
        return List.copyOf(entries.values());
    }
}
