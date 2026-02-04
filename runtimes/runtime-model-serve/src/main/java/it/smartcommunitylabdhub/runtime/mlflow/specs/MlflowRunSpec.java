/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.runtime.mlflow.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MlflowRunSpec extends RunBaseSpec {

    @JsonProperty("path")
    @Pattern(regexp = "^(store://([^/]+)/model/mlflow/.*)" + "|" + Keys.FOLDER_PATTERN + "|" + Keys.ZIP_PATTERN)
    @Schema(title = "fields.path.title", description = "fields.mlflow.path.description")
    private String path;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        MlflowRunSpec spec = mapper.convertValue(data, MlflowRunSpec.class);
        this.path = spec.getPath();
    }
}
