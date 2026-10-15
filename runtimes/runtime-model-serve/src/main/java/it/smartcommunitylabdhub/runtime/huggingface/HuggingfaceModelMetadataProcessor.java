package it.smartcommunitylabdhub.runtime.huggingface;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.runtime.huggingface.service.HuggingfaceApiService;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceModelSpec;
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceModelStatus;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@ProcessorType(stages = { "onCreated", "onReady" }, type = Model.class, spec = Metadata.class)
@Component
@Slf4j
public class HuggingfaceModelMetadataProcessor implements Processor<Model, Metadata> {

    @Autowired
    private HuggingfaceApiService apiService;

    @Override
    public <I extends Serializable> Metadata process(String stage, Model dto, I input) throws CoreRuntimeException {
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

        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        Set<String> labels = new HashSet<>();
        if (metadata.getLabels() != null) {
            labels.addAll(metadata.getLabels());
        }

        HuggingfaceModelStatus status = HuggingfaceModelStatus.with(dto.getStatus());
        List<String> tags = null;
        if (status.getHuggingface() != null && status.getHuggingface().getModel() != null) {
            //lets merge tags from existing metadata
            tags = (List<String>) status.getHuggingface().getModel().get("tags");
        } else {
            log.debug("Fetching metadata for model: {}", modelId);
            Map<String, Serializable> model = apiService.getMetadata(modelId);
            log.trace("Fetched metadata for model: {}: {}", modelId, model);

            if (model != null && model.get("tags") != null) {
                tags = (List<String>) model.get("tags");
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
