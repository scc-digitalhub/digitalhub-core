/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.serve;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmServeRunSpec.KIND,
    entity = Run.class,
    uiSchema = "runtime-tvm/tvm-serve-run/uiSchema.json"
)
public final class TvmServeRunSpec extends TvmRunSpec {

    public static final String KIND = TvmServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private TvmFunctionSpec functionSpec;

    @JsonUnwrapped
    private TvmServeTaskSpec taskServeSpec;

    // Empty or store://<project>/model/<kind>/<name>[:<id>].
    public static final String COMPILED_PATH_REGEX = "^(store://[^/]+/model/[^/]+/[^:]+(:.+)?)?$";

    @JsonProperty("model_path")
    @Pattern(regexp = COMPILED_PATH_REGEX)
    @Schema(title = "Compiled Model", description = "The tvm-so Model to serve; empty uses the one written by tvm+compile in the function.")
    private String modelPath;

    // Restrict characters because the name is used in URLs and generated YAML.
    @JsonProperty("served_name")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$")
    @Schema(title = "Served Model Name", description = "Name of the model in the API, /v2/models/<name>; empty uses the function name.")
    private String servedName;

    @JsonProperty("image")
    @Schema(title = "Serve Image", description = "Image that serves the model; empty uses the serve image configured on the platform (Go).")
    private String image;

    @JsonProperty("replicas")
    @Min(0)
    @Schema(title = "Replicas", description = "Number of pods that serve the model.")
    private Integer replicas;

    @JsonProperty("workers")
    @Min(1)
    @Schema(title = "Workers", description = "Requests handled in parallel by each pod; each worker loads its own copy of the model.")
    private Integer workers;

    @JsonProperty(value = "service_type", defaultValue = "ClusterIP")
    @Schema(title = "Service Type", description = "Kubernetes Service type: ClusterIP, NodePort or LoadBalancer.", defaultValue = "ClusterIP")
    private CoreServiceType serviceType;

    @JsonProperty("service_name")
    @Schema(title = "Service Name", description = "Extra Service name, <function>-<service name>.")
    private String serviceName;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmServeRunSpec spec = mapper.convertValue(data, TvmServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.modelPath = spec.getModelPath();
        this.servedName = spec.getServedName();
        this.image = spec.getImage();
        this.replicas = spec.getReplicas();
        this.workers = spec.getWorkers();
        this.serviceType = spec.getServiceType();
        this.serviceName = spec.getServiceName();
    }

    public static TvmServeRunSpec with(Map<String, Serializable> data) {
        TvmServeRunSpec spec = new TvmServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
