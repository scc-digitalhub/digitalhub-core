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

package it.smartcommunitylabdhub.runtime.huggingface.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.runtime.huggingface.models.HuggingfaceMetadata;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HuggingfaceModelStatus extends BaseSpec implements Status {

    @JsonProperty("huggingface")
    private HuggingfaceMetadata huggingface;

    @Override
    public void configure(Map<String, Serializable> data) {
        HuggingfaceModelStatus spec = mapper.convertValue(data, HuggingfaceModelStatus.class);
        this.huggingface = spec.getHuggingface();
    }

    public static HuggingfaceModelStatus with(Map<String, Serializable> data) {
        HuggingfaceModelStatus spec = new HuggingfaceModelStatus();
        spec.configure(data);

        return spec;
    }
}
