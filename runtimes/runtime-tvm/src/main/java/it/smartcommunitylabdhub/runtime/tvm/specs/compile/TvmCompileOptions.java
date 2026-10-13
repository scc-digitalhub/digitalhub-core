/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 * SPDX-License-Identifier: Apache-2.0
 */
package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Grouped form representation; legacy flat SDK parameters are still accepted. */
public final class TvmCompileOptions {

    public static final List<String> OPTIMIZATION = List.of(
        "target_num_cores",
        "opt_level",
        "cross_cc",
        "exec_mode",
        "relax_pipeline",
        "tir_pipeline",
        "system_lib",
        "benchmark_runs",
        "params_path"
    );
    public static final List<String> APPLY = List.of("tuning_model_path", "tuning_ops", "allow_partial_tuning");
    public static final List<String> SEARCH = List.of(
        "tuning_trials",
        "max_trials_per_task",
        "tuning_trials_per_iter",
        "tuning_runner",
        "tuning_workers",
        "tuning_seed",
        "tuning_number",
        "tuning_repeat",
        "tuning_min_repeat_ms",
        "tuning_alloc_repeat",
        "tuning_enable_cpu_cache_flush",
        "tuning_builder_timeout_sec",
        "tuning_runner_timeout_sec"
    );
    public static final List<String> RPC = List.of(
        "rpc_tracker_host",
        "rpc_tracker_port",
        "rpc_tracker_key",
        "rpc_session_timeout_sec"
    );
    public static final List<String> TUNING = Stream.of(APPLY, SEARCH, RPC).flatMap(List::stream).toList();
    private static final List<String> TUNING_GROUP = Stream.concat(Stream.of("tuning_mode"), TUNING.stream()).toList();

    private TvmCompileOptions() {}

    public static Map<String, Serializable> flatten(Map<String, Serializable> data) {
        Map<String, Serializable> result = new LinkedHashMap<>(data);
        flattenGroup(result, "optimization", OPTIMIZATION);
        flattenGroup(result, "tuning", TUNING_GROUP);
        return result;
    }

    private static void flattenGroup(Map<String, Serializable> data, String name, List<String> fields) {
        Object group = data.remove(name);
        if (group == null) return;
        if (!(group instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        for (var entry : values.entrySet()) {
            if (!fields.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown " + name + " option: " + entry.getKey());
            }
            // Grouped values override legacy flat values within the same spec.
            data.put((String) entry.getKey(), (Serializable) entry.getValue());
        }
    }

    public static Map<String, Serializable> active(Map<String, Serializable> data) {
        Map<String, Serializable> result = flatten(data);
        Object mode = result.get("tuning_mode");
        switch (mode == null ? "off" : mode.toString()) {
            case "off" -> result.keySet().removeAll(TUNING);
            case "apply" -> {
                result.keySet().removeAll(SEARCH);
                result.keySet().removeAll(RPC);
            }
            case "tune" -> {
                Object runner = result.get("tuning_runner");
                if (runner == null || "local".equals(runner.toString())) {
                    result.keySet().removeAll(RPC);
                } else if ("rpc".equals(runner.toString())) {
                    result.remove("tuning_runner_timeout_sec");
                }
            }
        }
        return result;
    }

    public static Map<String, Serializable> inactive(Map<String, Serializable> data, Map<String, Serializable> active) {
        Map<String, Serializable> result = flatten(data);
        result.keySet().removeAll(active.keySet());
        return result;
    }

    public static Map<String, Serializable> stored(
        Map<String, Serializable> active,
        Map<String, Serializable> inactive
    ) {
        Map<String, Serializable> result = new LinkedHashMap<>(inactive);
        result.putAll(active);
        return grouped(result);
    }

    public static Map<String, Serializable> grouped(Map<String, Serializable> data) {
        Map<String, Serializable> result = flatten(data);
        group(result, "optimization", OPTIMIZATION);
        group(result, "tuning", TUNING_GROUP);
        return result;
    }

    private static void group(Map<String, Serializable> data, String name, List<String> fields) {
        LinkedHashMap<String, Serializable> values = new LinkedHashMap<>();
        fields.forEach(field -> {
            Serializable value = data.remove(field);
            if (value != null) values.put(field, value);
        });
        if (!values.isEmpty()) data.put(name, values);
    }
}
