package it.smartcommunitylabdhub.runtime.ollama;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.runtime.ollama.models.ModelDetail;
import it.smartcommunitylabdhub.runtime.ollama.service.OllamaRepositoryClient;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelStatus;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onCreated", "onReady" }, type = Model.class, spec = Status.class)
@Component
@Slf4j
public class OllamaModelProcessor implements Processor<Model, Status> {

    @Autowired
    private OllamaRepositoryClient client;

    @Override
    public <I extends Serializable> Status process(String stage, Model dto, I input) throws CoreRuntimeException {
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

        OllamaModelStatus status = OllamaModelStatus.with(dto.getStatus());
        if (status.getOllama() != null) {
            //use as-is, we don't refresh metadata
            return status;
        }

        log.debug("Fetching metadata for model: {}", model);

        //build from scratch
        ModelDetail detail = client.getModelDetail(model);
        log.trace("Fetched metadata for model: {}: {}", model, detail);

        status.setOllama(detail);
        return status;
    }
}
