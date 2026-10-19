/*
 * Copyright 2026 the original author or authors.
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
 */

package it.smartcommunitylabdhub.security.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.security.model.SecurityAssessment;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(kind = SecurityReportSpec.KIND, entity = Extension.class)
@ExtensionType(appliesTo = { Function.class })
public class SecurityReportSpec extends BaseSpec {

    public static final String KIND = "security-report";

    @Schema(title = "Analyzer", description = "Name of the security analysis function producing the report")
    @NotNull
    private String analyzer;

    @Schema(title = "Target", description = "Analyzed function")
    private String target;

    @Schema(title = "Image", description = "Container image analyzed, if any")
    private String image;

    @Schema(title = "Run", description = "Analysis run key")
    private String run;

    @Schema(title = "Assessment", description = "Overall security assessment")
    private SecurityAssessment assessment;

    @Schema(title = "Findings", description = "Number of findings by severity")
    private Map<String, Integer> findings = new HashMap<>();

    @Schema(title = "Path", description = "Path to the report files")
    private String path;

    @Override
    public void configure(Map<String, Serializable> data) {
        SecurityReportSpec spec = mapper.convertValue(data, SecurityReportSpec.class);
        this.analyzer = spec.getAnalyzer();
        this.target = spec.getTarget();
        this.image = spec.getImage();
        this.run = spec.getRun();
        this.assessment = spec.getAssessment();
        this.findings = spec.getFindings();
        this.path = spec.getPath();
    }

    public static SecurityReportSpec with(Map<String, Serializable> data) {
        SecurityReportSpec spec = new SecurityReportSpec();
        spec.configure(data);
        return spec;
    }
}
