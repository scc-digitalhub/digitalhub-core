package it.smartcommunitylabdhub.core.models.builders.log;

import it.smartcommunitylabdhub.commons.models.entities.log.Log;
import it.smartcommunitylabdhub.commons.models.entities.log.LogBaseSpec;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.core.models.entities.LogEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class LogEntityBuilder implements Converter<Log, LogEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;
    private final AttributeConverter<String, byte[]> stringConverter;

    public LogEntityBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter,
        @Qualifier("cborStringConverter") AttributeConverter<String, byte[]> stringConverter
    ) {
        this.converter = cborConverter;
        this.stringConverter = stringConverter;
    }

    /**
     * Build a Log from a LogDTO and store extra values as a cbor
     *
     * @return LogEntity
     */
    public LogEntity build(Log dto) {
        // Extract data
        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        LogBaseSpec spec = new LogBaseSpec();
        spec.configure(dto.getSpec());

        return LogEntity
            .builder()
            .id(dto.getId())
            .kind(dto.getKind())
            .project(dto.getProject())
            .metadata(converter.convertToDatabaseColumn(dto.getMetadata()))
            .spec(converter.convertToDatabaseColumn(dto.getSpec()))
            .status(converter.convertToDatabaseColumn(dto.getStatus()))
            .content(stringConverter.convertToDatabaseColumn(dto.getContent()))
            //extract ref
            .run(spec.getRun())
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
    public LogEntity convert(Log source) {
        return build(source);
    }
}
