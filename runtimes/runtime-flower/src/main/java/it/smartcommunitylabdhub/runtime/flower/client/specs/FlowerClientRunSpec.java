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

package it.smartcommunitylabdhub.runtime.flower.client.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FlowerClientRunSpec extends RunBaseSpec {

    @Schema(title = "fields.flower.superlink.title", description = "fields.flower.superlink.description")
    private String superlink;

    @JsonProperty("node_config")
    @Schema(title = "fields.flower.nodeconfig.title", description = "fields.flower.nodeconfig.description")
    private Map<String, Serializable> nodeConfig = new HashMap<>();

    @JsonProperty("root_certificates")
    @Schema(
        title = "fields.flower.root_certificates.title",
        description = "fields.flower.root_certificates.description"
    )
    private String rootCertificates;

    @JsonProperty("private_key_secret")
    @Schema(
        title = "fields.flower.private_key_secret.title",
        description = "fields.flower.private_key_secret.description"
    )
    private String privateKeySecret;

    @JsonProperty("public_key_secret")
    @Schema(
        title = "fields.flower.public_key_secret.title",
        description = "fields.flower.public_key_secret.description"
    )
    private String publicKeySecret;

    @Schema(
        title = "fields.flower.isolation.title",
        description = "fields.flower.isolation.description",
        defaultValue = "subprocess"
    )
    private IsolationType isolation = IsolationType.subprocess;

    public FlowerClientRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerClientRunSpec spec = mapper.convertValue(data, FlowerClientRunSpec.class);

        this.superlink = spec.getSuperlink();
        this.nodeConfig = spec.getNodeConfig();
        this.rootCertificates = spec.getRootCertificates();
        this.privateKeySecret = spec.getPrivateKeySecret();
        this.publicKeySecret = spec.getPublicKeySecret();
    }

    public enum IsolationType {
        process,
        subprocess,
    }
}
