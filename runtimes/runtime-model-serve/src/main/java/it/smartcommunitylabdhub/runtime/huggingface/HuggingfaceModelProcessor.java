package it.smartcommunitylabdhub.runtime.huggingface;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.runtime.huggingface.models.HuggingfaceMetadata;
import it.smartcommunitylabdhub.runtime.huggingface.service.HuggingfaceApiService;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceModelSpec;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceModelStatus;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@ProcessorType(stages = { "onCreated", "onReady" }, type = Model.class, spec = Status.class)
@Component
@Slf4j
public class HuggingfaceModelProcessor implements Processor<Model, Status> {

    @Autowired
    private HuggingfaceApiService apiService;

    @Override
    public <I extends Serializable> Status process(String stage, Model dto, I input) throws CoreRuntimeException {
        if (dto == null || !HuggingfaceModelSpec.KIND.equals(dto.getKind())) {
            return null;
        }

        HuggingfaceModelSpec spec = new HuggingfaceModelSpec();
        spec.configure(dto.getSpec());

        String path = spec.getPath();

        if (path == null || path.isEmpty() || (!path.startsWith("huggingface://") && !path.startsWith("hf://"))) {
            return null;
        }

        //get model name
        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();
        String modelId = uri.getHost() + uri.getPath();
        String revision = null;
        if (modelId.contains(":")) {
            String[] parts = modelId.split(":");
            modelId = parts[0];
            revision = parts[1];
        }

        HuggingfaceModelStatus status = HuggingfaceModelStatus.with(dto.getStatus());
        if (status.getHuggingface() != null) {
            //use as-is, we don't refresh metadata
            return status;
        }

        log.debug("Fetching metadata for model: {}", modelId);

        //build from scratch
        Instant now = Instant.now();
        HuggingfaceMetadata hf = new HuggingfaceMetadata();
        hf.setModelId(modelId);
        hf.setFetchedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));

        Map<String, Serializable> metadata = apiService.getMetadata(modelId);
        hf.setModel(metadata);
        log.trace("Fetched metadata for model: {}: {}", modelId, metadata);

        Map<String, Serializable> config = apiService.getConfig(modelId);
        hf.setConfig(config);
        log.trace("Fetched config for model: {}: {}", modelId, config);

        status.setHuggingface(hf);
        return status;
    }
}
