package it.smartcommunitylabdhub.core.models.builders.trigger;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TriggerEntityBuilder implements Converter<Trigger, TriggerEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public TriggerEntityBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    public TriggerEntity build(Trigger dto) {
        // Extract data
        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(dto.getStatus());

        return TriggerEntity
            .builder()
            .id(dto.getId())
            .name(dto.getName())
            .kind(dto.getKind())
            .project(dto.getProject())
            .metadata(converter.convertToDatabaseColumn(dto.getMetadata()))
            .spec(converter.convertToDatabaseColumn(dto.getSpec()))
            .status(converter.convertToDatabaseColumn(dto.getStatus()))
            .state(
                // Store status if not present
                statusFieldAccessor.getState() == null ? State.CREATED : State.valueOf(statusFieldAccessor.getState())
            )
            //extract refs from specs
            .task(TriggerBaseSpec.from(dto.getSpec()).getTask())
            // Metadata Extraction
            .created(
                metadata.getCreated() != null
                    ? Date.from(metadata.getCreated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                    : null
            )
            .updated(
                metadata.getUpdated() != null
                    ? Date.from(metadata.getUpdated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                    : null
            )
            .build();
    }

    @Override
    public TriggerEntity convert(Trigger source) {
        return build(source);
    }
}
