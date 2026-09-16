/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.serve;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Task spec for tvm+serve: deploy the compiled tvm-so Model behind tvm-serve (OpenInference v2).
@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmServeTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-serve/uiSchema.json"
)
public class TvmServeTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+serve";
    // Accepts an empty value or a store:// model key: store://<project>/model/<kind>/<name>[:<id>]
    public static final String COMPILED_PATH_REGEX = "^(store://[^/]+/model/[^/]+/[^:]+(:.+)?)?$";

    // Explicit tvm-so Model key (store://) to serve; overrides function.spec.so_model.
    @JsonProperty("model_path")
    @Pattern(regexp = COMPILED_PATH_REGEX)
    @Schema(
        title = "Compiled Model",
        description = "The tvm-so Model to serve; empty uses the one written by tvm+compile in the function."
    )
    private String modelPath;

    // Model name at /v2/models/<served_name> (default: function name); charset-constrained — it lands in URLs and generated YAML.
    @JsonProperty("served_name")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$")
    @Schema(
        title = "Served Model Name",
        description = "Name of the model in the API, /v2/models/<name>; empty uses the function name."
    )
    private String servedName;

    // Override the serving image (default: runtime.tvm.serve).
    @JsonProperty("image")
    @Schema(
        title = "Serve Image",
        description = "Image that serves the model; empty uses the serve image configured on the platform (Go)."
    )
    private String image;

    @JsonProperty("replicas")
    @Min(0)
    @Schema(title = "Replicas", description = "Number of pods that serve the model.")
    private Integer replicas;

    // In-process inference workers per replica (each loads its own model copy) -> TVM_SERVE_WORKERS.
    // The requested CPU cores are split among them through TVM_NUM_THREADS.
    @JsonProperty("workers")
    @Min(1)
    @Schema(
        title = "Workers",
        description = "Requests handled in parallel by each pod; each worker loads its own copy of the model."
    )
    private Integer workers;

    @JsonProperty(value = "service_type", defaultValue = "ClusterIP")
    @Schema(
        title = "Service Type",
        description = "Kubernetes Service type: ClusterIP, NodePort or LoadBalancer.",
        defaultValue = "ClusterIP"
    )
    private CoreServiceType serviceType;

    @JsonProperty("service_name")
    @Schema(title = "Service Name", description = "Extra Service name, <function>-<service name>.")
    private String serviceName;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmServeTaskSpec spec = mapper.convertValue(data, TvmServeTaskSpec.class);
        this.modelPath = spec.getModelPath();
        this.servedName = spec.getServedName();
        this.image = spec.getImage();
        this.replicas = spec.getReplicas();
        this.workers = spec.getWorkers();
        this.serviceType = spec.getServiceType();
        this.serviceName = spec.getServiceName();
    }

    public static TvmServeTaskSpec with(Map<String, Serializable> data) {
        TvmServeTaskSpec spec = new TvmServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
