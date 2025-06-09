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

package it.smartcommunitylabdhub.runtime.huggingface.models;

public enum HuggingfaceDType {
    AUTO("auto"),
    FLOAT16("float16"),
    FLOAT32("float32"),
    BFLOAT16("bfloat16"),
    FLOAT("float"),
    HALF("half");

    private final String dtype;

    HuggingfaceDType(String dtype) {
        this.dtype = dtype;
    }

    public String getDType() {
        return dtype;
    }
}
