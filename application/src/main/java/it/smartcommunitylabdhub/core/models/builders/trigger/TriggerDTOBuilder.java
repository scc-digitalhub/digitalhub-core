package it.smartcommunitylabdhub.core.models.builders.trigger;

import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.metadata.AuditMetadataBuilder;
import it.smartcommunitylabdhub.core.models.metadata.BaseMetadataBuilder;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TriggerDTOBuilder implements Converter<TriggerEntity, Trigger> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;
    private BaseMetadataBuilder baseMetadataBuilder;
    private AuditMetadataBuilder auditingMetadataBuilder;

    public TriggerDTOBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    @Autowired
    public void setBaseMetadataBuilder(BaseMetadataBuilder baseMetadataBuilder) {
        this.baseMetadataBuilder = baseMetadataBuilder;
    }

    @Autowired
    public void setAuditingMetadataBuilder(AuditMetadataBuilder auditingMetadataBuilder) {
        this.auditingMetadataBuilder = auditingMetadataBuilder;
    }

    public Trigger build(TriggerEntity entity) {
        //read metadata map as-is
        Map<String, Serializable> meta = converter.convertToEntityAttribute(entity.getMetadata());

        // build metadata
        Map<String, Serializable> metadata = new HashMap<>();
        metadata.putAll(meta);

        Optional.of(baseMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));
        Optional.of(auditingMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));

        return Trigger
            .builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .name(entity.getName())
            .project(entity.getProject())
            .user(entity.getCreatedBy())
            .metadata(metadata)
            .spec(converter.convertToEntityAttribute(entity.getSpec()))
            .status(
                MapUtils.mergeMultipleMaps(
                    converter.convertToEntityAttribute(entity.getStatus()),
                    Map.of("state", entity.getState().toString())
                )
            )
            .build();
    }

    @Override
    public Trigger convert(TriggerEntity source) {
        return build(source);
    }
}
