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

package it.smartcommunitylabdhub.framework.k8s.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CoreItems implements Serializable {

    private List<Map<String, Serializable>> coreItems;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Map<String, Serializable>> coreItems = new ArrayList<>();

        public Builder keyToPath(Map<String, Serializable> keyToPath) {
            this.coreItems.add(keyToPath);
            return this;
        }

        public Builder keyToPath(List<Map<String, Serializable>> keyToPath) {
            this.coreItems.addAll(keyToPath);
            return this;
        }

        public CoreItems build() {
            return new CoreItems(coreItems);
        }
    }
}
