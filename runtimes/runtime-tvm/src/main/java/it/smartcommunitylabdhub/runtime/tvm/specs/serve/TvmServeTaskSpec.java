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

// Task spec for tvm+serve: deploy the compiled .so Model (kind tvm-so) behind the
// tvm-serve server (OpenInference v2, REST on 8080 + gRPC on 9000). Model-centric:
// an init container pulls the model.so + metadata.json from the tvm-so Model's S3
// folder at startup, so there is no per-model baked image. The serving ports are
// hardcoded in the runner; the Service follows the framework default like the
// python runtime.
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmServeTaskSpec.KIND, entity = Task.class)
public class TvmServeTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+serve";
    // Accepts an empty value or a store:// model key: store://<project>/model/<kind>/<name>[:<id>]
    public static final String COMPILED_PATH_REGEX = "^(store://[^/]+/model/[^/]+/[^:]+(:.+)?)?$";

    // Explicit tvm-so Model key (store://) to serve; overrides function.spec.so_model.
    @JsonProperty("model_path")
    @Pattern(regexp = COMPILED_PATH_REGEX)
    @Schema(title = "fields.tvm.serve.modelPath.title", description = "fields.tvm.serve.modelPath.description")
    private String modelPath;

    // Model name exposed at /v2/models/<served_name> (default: function name).
    // Constrained to a k8s-label-like charset: the value ends up in URLs and in the
    // go backend's generated processor.yaml, so free text (quotes, spaces, newlines)
    // would break the config or allow YAML injection.
    @JsonProperty("served_name")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$")
    @Schema(title = "fields.tvm.serve.servedName.title", description = "fields.tvm.serve.servedName.description")
    private String servedName;

    // Override the serving image (default: runtime.tvm.serve).
    @JsonProperty("image")
    @Schema(title = "fields.tvm.serve.image.title", description = "fields.tvm.serve.image.description")
    private String image;

    @JsonProperty("replicas")
    @Min(0)
    private Integer replicas;

    @JsonProperty(value = "service_type", defaultValue = "ClusterIP")
    @Schema(defaultValue = "ClusterIP")
    private CoreServiceType serviceType;

    @JsonProperty("service_name")
    private String serviceName;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmServeTaskSpec spec = mapper.convertValue(data, TvmServeTaskSpec.class);
        this.modelPath = spec.getModelPath();
        this.servedName = spec.getServedName();
        this.image = spec.getImage();
        this.replicas = spec.getReplicas();
        this.serviceType = spec.getServiceType();
        this.serviceName = spec.getServiceName();
    }

    public static TvmServeTaskSpec with(Map<String, Serializable> data) {
        TvmServeTaskSpec spec = new TvmServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
