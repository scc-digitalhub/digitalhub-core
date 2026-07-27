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

package it.smartcommunitylabdhub.metrics.config;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@ConfigurationProperties(prefix = "providers.prometheus", ignoreUnknownFields = true)
public class PrometheusProperties {

    private String url;

    private String username;
    private String password;

    private String namespace;

    private Boolean lazyFilter;

    private Integer rateInterval = 30;
    private String rateOperation = "irate";

    private Map<String, MetricMapping> metrics;
    private Map<String, String> mapping;

    public boolean useLazyFilter() {
        return lazyFilter != null && lazyFilter;
    }

    public record MetricMapping(
        String name,
        String unit,
        String label,
        String groupBy,
        String operation,
        String window
    ) {}
}
