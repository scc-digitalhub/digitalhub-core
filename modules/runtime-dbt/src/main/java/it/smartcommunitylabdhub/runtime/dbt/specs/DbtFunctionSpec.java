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

package it.smartcommunitylabdhub.runtime.dbt.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.runtime.dbt.DbtRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = DbtRuntime.RUNTIME, kind = DbtRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class DbtFunctionSpec extends FunctionBaseSpec {

    @NotNull
    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private SourceCode<DbtSourceCodeLanguages> source;

    public DbtFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        DbtFunctionSpec spec = mapper.convertValue(data, DbtFunctionSpec.class);
        this.source = spec.getSource();
    }

    public enum DbtSourceCodeLanguages {
        sql,
        python,
    }
}
