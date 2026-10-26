/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.StringUtils;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TvmProperties {

    private Integer userId;
    private Integer groupId;
    private String homeDir;
    private String volumeSize;

    // Source format -> builder image.
    private Map<String, String> builders;

    private String compiler;

    private String serve;

    private String entrypoint;
    private Map<String, String> builderScripts;

    private Map<String, Target> targets = new LinkedHashMap<>();

    public Target target(String name) {
        String key = StringUtils.hasText(name) ? name.trim().toLowerCase(Locale.ROOT) : "auto";
        Target target = targets.get(key);
        if (target == null) {
            target = targets.values().stream().filter(value -> value.getAliases().contains(key)).findFirst().orElse(null);
        }
        if (target == null || !StringUtils.hasText(target.getTarget())) {
            throw new IllegalArgumentException("Unknown or unconfigured target_architecture: " + key);
        }
        return target;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Target {
        private String label;
        private String target;
        private Linker linker = new Linker();
        private String compilerArchitecture;
        private List<String> aliases = List.of();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Linker {
        private String command;
        private List<String> options = List.of();
    }
}
