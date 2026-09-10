package it.smartcommunitylabdhub.compliance.specs;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.compliance.model.EntityRef;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@SpecType(kind = "operation-compliance", entity = Extension.class)
@ExtensionType(appliesTo = { Function.class })
public class OperationComplianceSpec extends BaseSpec {

    private OperationMode mode;
    private ComplianceOperationType type;
    private OperationCategory category;
    private LifecyclePhase phase;

    private Set<EntityRef> objectives;

    @Override
    public void configure(Map<String, Serializable> data) {
        OperationComplianceSpec spec = mapper.convertValue(data, OperationComplianceSpec.class);
        this.mode = spec.getMode();
        this.type = spec.getType();
        this.category = spec.getCategory();
        this.phase = spec.getPhase();
        this.objectives = spec.getObjectives();
    }

    public enum ComplianceOperationType {
        VALIDATION,
        MITIGATION,
    }


    public enum OperationMode {
        JOB,
        SERVICE,
    }

    public enum OperationCategory {
        DATA_PROFILING,
        DATA_VALIDATION,
        DATA_PREPROCESSING,
        DOCUMENTATION_GENERATION,
        SYNTHETIC_GENERATION,
        AUGMENTATION,
        ANONYMIZATION,
        FEATURE_EVALUATION,
        MODEL_TRAINING,
        TRAINING_AUGMENTATION,
        MODEL_PROFILING,
        RESOURCE_PROFILING,
        MODEL_VALIDATION,
        GUARDRAIL,
        PREPROCESSING,
        POSTPROCESSING,
        INPUT_TRACING,
        OUTPUT_TRACING,
        RESOURCE_MONITORING,
        DRIFT_DETECTION,
        LOG_ANALYSIS,
    }

    public enum LifecyclePhase {
        DATA_PREPARATION,
        MODEL_DEVELOPMENT,
        OPERATIONALIZATION,
    }

    
}
