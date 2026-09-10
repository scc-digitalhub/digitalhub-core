package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Input/output signature of a model's inference function. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InferenceSignature {

    @Schema(title = "fields.compliance.inference_signature.inputs.title", description = "fields.compliance.inference_signature.inputs.description")
    private List<TensorSpec> inputs;
    @Schema(title = "fields.compliance.inference_signature.outputs.title", description = "fields.compliance.inference_signature.outputs.description")
    private List<TensorSpec> outputs;
    /** Optional inference-time parameters (e.g., temperature, top_k). */
    @Schema(title = "fields.compliance.inference_signature.parameters.title", description = "fields.compliance.inference_signature.parameters.description")
    private List<ParameterSpec> parameters;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParameterSpec {

        @Schema(title = "fields.compliance.parameter_spec.name.title", description = "fields.compliance.parameter_spec.name.description")
        private String name;
        @Schema(title = "fields.compliance.parameter_spec.dtype.title", description = "fields.compliance.parameter_spec.dtype.description")
        private FieldSpec.DataType dtype;
        @Schema(title = "fields.compliance.parameter_spec.default_value.title", description = "fields.compliance.parameter_spec.default_value.description")
        private Object defaultValue;
        @Schema(title = "fields.compliance.parameter_spec.description.title", description = "fields.compliance.parameter_spec.description.description")
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TensorSpec {

        @Schema(title = "fields.compliance.tensor_spec.name.title", description = "fields.compliance.tensor_spec.name.description")
        private String name;
        @Schema(title = "fields.compliance.tensor_spec.dtype.title", description = "fields.compliance.tensor_spec.dtype.description")
        private NumericType dtype;
        /** Shape dimensions; -1 denotes a variable-length dimension. */
        @Schema(title = "fields.compliance.tensor_spec.shape.title", description = "fields.compliance.tensor_spec.shape.description")
        private List<Integer> shape;
        @Schema(title = "fields.compliance.tensor_spec.description.title", description = "fields.compliance.tensor_spec.description.description")
        private String description;

        public enum NumericType {
            FP16,
            FP32,
            FP64,
            INT8,
            INT16,
            INT32,
            INT64,
            UINT8,
            UINT16,
            UINT32,
            UINT64,
            BOOL,
            BYTES
        }
    }

}
