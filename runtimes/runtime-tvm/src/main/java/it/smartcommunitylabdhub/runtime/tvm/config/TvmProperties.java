/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
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
            target = targets.values().stream().filter(value -> value.aliases().contains(key)).findFirst()
                    .orElse(null);
        }
        if (target == null || !StringUtils.hasText(target.target())) {
            throw new IllegalArgumentException("Unknown or unconfigured target_architecture: " + key);
        }
        return target;
    }

    public record Target(
            String label,
            String target,
            Linker linker,
            String compilerArchitecture,
            List<String> aliases) {

        public Target {
            if (!StringUtils.hasText(target)) {
                throw new IllegalArgumentException("TVM target must be a non-empty JSON object string");
            }
            try {
                var definition = JacksonMapper.OBJECT_MAPPER.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(target);
                if (!definition.isObject() || definition.isEmpty()) {
                    throw new IllegalArgumentException("TVM target must be a non-empty JSON object string");
                }
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("TVM target must be a valid JSON object string", e);
            }
            linker = linker == null ? new Linker(null, List.of()) : linker;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        public String getLabel() {
            return label;
        }

        public String getTarget() {
            return target;
        }

        public Linker getLinker() {
            return linker;
        }

        public String getCompilerArchitecture() {
            return compilerArchitecture;
        }

        public List<String> getAliases() {
            return aliases;
        }
    }

    public record Linker(String command, List<String> options) {
        public Linker {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public String getCommand() {
            return command;
        }

        public List<String> getOptions() {
            return options;
        }
    }
}
