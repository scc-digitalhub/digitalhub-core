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

package it.smartcommunitylabdhub.runtime.flower.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FABModel {

    private String name;
    private String version;
    private String description;
    private String publisher;
    private String license;

    private List<String> dependencies;
    private List<String> packages;

    private String serverApp;
    private String clientApp;

    private String defaultFederation;
    private Map<String, Serializable> config;

    private Map<String, Map<String, Serializable>> federationConfigs;

    private String valueOf(String value) {
        return value != null ? value : "";
    }

    private String typedValueOf(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof String ? "\"" + value + "\"" : value.toString();
    }

    // @formatter:off
    public String toTOML() {
        String toml =   "[build-system]\n" + //
                        "requires = [\"hatchling\"]\n" + //
                        "build-backend = \"hatchling.build\"\n" + //
                        "\n" + //
                        "[project]\n" + //
                        "name = \"" + valueOf(name) + "\"\n" + //
                        "version = \"" + valueOf(version) + "\"\n" + //
                        "description = \"" + valueOf(description) + "\"\n" + //
                        // "license = \"" + valueOf(license) + "\"\n" + //
                        "dependencies = [\n" + //
                        String.join(",\n", (dependencies == null ? List.of() : dependencies).stream().map(d -> "\"" + d + "\"").toList()) + "\n" + //
                        "]\n" + //
                        "\n" + //
                        "[tool.hatch.build.targets.wheel]\n" + //
                        "packages = [" + (packages == null ? "\".\"" : StringUtils.collectionToCommaDelimitedString(packages.stream().map(p -> "\"" + p + "\"").toList())) + "]\n" + //
                        "\n" + //
                        "[tool.flwr.app]\n" + //
                        "publisher = \"" + valueOf(publisher) + "\"\n" + //
                        "\n" + //
                        (serverApp != null || clientApp != null ? 
                        "[tool.flwr.app.components]\n" + //
                        (serverApp  != null ? "serverapp = \"" + valueOf(serverApp) + "\"\n" : "") + //
                        (clientApp != null ? "clientapp = \"" + valueOf(clientApp) + "\"\n": ""): "") + //
                        "\n" + //
                        "[tool.flwr.app.config]\n" + //
                        (config != null ? String.join("\n", config.entrySet().stream().map(e -> "\"" + e.getKey() + "\" = " + typedValueOf(e.getValue())).toList()) : "") + //
                        "\n" + //
                        "[tool.flwr.federations]\n" +
                        "default = \"" + valueOf(defaultFederation) + "\"\n" + //
                        "\n" + //
                        (federationConfigs != null
                            ? String.join("\n\n", federationConfigs.entrySet().stream()
                                .map(e -> "[tool.flwr.federations." + e.getKey() + "]\n" +
                                    String.join("\n", e.getValue().entrySet().stream()
                                        .map(f -> "\"" + f.getKey() + "\" = " + typedValueOf(f.getValue()))
                                        .toList()))
                                .toList())
                            : "");
                        
        return toml;
    }
    // @formatter:on

}
