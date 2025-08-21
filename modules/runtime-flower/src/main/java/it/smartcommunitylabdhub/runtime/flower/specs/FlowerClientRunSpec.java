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

package it.smartcommunitylabdhub.runtime.flower.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.FlowerClientRuntime;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerClientRuntime.RUNTIME, kind = FlowerClientRunSpec.KIND, entity = EntityName.RUN)
public class FlowerClientRunSpec extends RunBaseSpec {

    public static final String KIND = FlowerClientRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private FlowerClientTaskSpec taskDeploySpec;
    @JsonUnwrapped
    private FlowerBuildClientTaskSpec taskBuildSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerClientFunctionSpec functionSpec;

    @Schema(title = "fields.flower.superlink.title", description = "fields.flower.superlink.description")
    private String superlink;

    @JsonProperty("node_config")
    @Schema(title = "fields.flower.nodeconfig.title", description = "fields.flower.nodeconfig.description")
    private Map<String, Serializable> nodeConfig = new HashMap<>();

    @JsonProperty("root_certificates")
    @Schema(title = "fields.flower.root_certificates.title", description = "fields.flower.root_certificates.description")
    private String rootCertificates;

    @JsonProperty("private_key_secret")
    @Schema(title = "fields.flower.private_key_secret.title", description = "fields.flower.private_key_secret.description")
    private String privateKeySecret;

    @JsonProperty("public_key_secret")
    @Schema(title = "fields.flower.public_key_secret.title", description = "fields.flower.public_key_secret.description")
    private String publicKeySecret;

    @Schema(title = "fields.flower.isolation.title", description = "fields.flower.isolation.description", defaultValue = "subprocess")
    private IsolationType isolation = IsolationType.subprocess;

    public FlowerClientRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerClientRunSpec spec = mapper.convertValue(data, FlowerClientRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
    
        this.taskDeploySpec = spec.getTaskDeploySpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();
        this.superlink = spec.getSuperlink();
        this.nodeConfig = spec.getNodeConfig();
        this.rootCertificates = spec.getRootCertificates();
        this.privateKeySecret = spec.getPrivateKeySecret();
        this.publicKeySecret = spec.getPublicKeySecret();
    }
    public void setTaskDeploySpec(FlowerClientTaskSpec taskDeploySpec) {
        this.taskDeploySpec = taskDeploySpec;
    }
    public void setTaskBuildSpec(FlowerBuildClientTaskSpec taskBuildSpec) {
        this.taskBuildSpec = taskBuildSpec;
    }
    public void setFunctionSpec(FlowerClientFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public enum IsolationType {
        process, subprocess
    }
}
