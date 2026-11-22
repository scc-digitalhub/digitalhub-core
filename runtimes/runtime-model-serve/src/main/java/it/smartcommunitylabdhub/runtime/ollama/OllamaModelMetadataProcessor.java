package it.smartcommunitylabdhub.runtime.ollama;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.runtime.ollama.models.ModelDetail;
import it.smartcommunitylabdhub.runtime.ollama.service.OllamaRepositoryClient;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelStatus;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onCreated", "onReady" }, type = Model.class, spec = Metadata.class)
@Component
@Slf4j
public class OllamaModelMetadataProcessor implements Processor<Model, Metadata> {

    @Autowired
    private OllamaRepositoryClient client;

    @Override
    public <I extends Serializable> Metadata process(String stage, Model dto, I input) throws CoreRuntimeException {
        if (dto == null || !OllamaModelSpec.KIND.equals(dto.getKind())) {
            return null;
        }

        OllamaModelSpec spec = new OllamaModelSpec();
        spec.configure(dto.getSpec());
        String path = spec.getPath();
        if (path == null || path.isEmpty() || !path.startsWith("ollama://")) {
            return null;
        }

        //get model name
        String model = path.substring("ollama://".length());

        log.debug("Fetching metadata for model: {}", model);

        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        Set<String> labels = new HashSet<>();
        if (metadata.getLabels() != null) {
            labels.addAll(metadata.getLabels());
        }

        OllamaModelStatus status = OllamaModelStatus.with(dto.getStatus());
        List<String> tags = null;
        if (status.getOllama() != null && status.getOllama().getCapabilities() != null) {
            //lets merge capabilities from existing metadata
            tags = status.getOllama().getCapabilities();
        } else {
            //build from scratch
            ModelDetail detail = client.getModelDetail(model);
            log.trace("Fetched metadata for model: {}: {}", model, detail);

            if (detail != null && detail.getCapabilities() != null) {
                tags = detail.getCapabilities();
            }
        }

        if (tags != null) {
            //limit to 10 max
            labels.addAll(tags.stream().limit(10).toList());
        }

        metadata.setLabels(labels);
        return metadata;
    }
}
