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

package it.smartcommunitylabdhub.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.smartcommunitylabdhub.commons.Keys;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceMetrics {

    @Nullable
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String id;

    @Nullable
    private String run;

    @Nullable
    private String project;

    @Nullable
    private String user;

    @Builder.Default
    private Map<String, Serializable> metadata = new HashMap<>();

    private List<Metrics> metrics;

    public record Metrics(
        String name,
        @Nullable String unit,
        @Nullable List<Metric> metrics,
        @Nullable List<Summary> summary
    ) implements Serializable {}

    public record Metric(Long timestamp, Double value) implements Serializable {}

    public record Summary(String name, Double value) implements Serializable {}
}
