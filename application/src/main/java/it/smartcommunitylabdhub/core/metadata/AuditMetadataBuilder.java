package it.smartcommunitylabdhub.core.metadata;

import it.smartcommunitylabdhub.commons.models.metadata.AuditMetadata;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class AuditMetadataBuilder implements Converter<BaseEntity, AuditMetadata> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public AuditMetadataBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    @Override
    public AuditMetadata convert(BaseEntity entity) {
        Assert.notNull(entity, "entity can not be null");
        //read metadata map as-is
        Map<String, Serializable> meta = converter.convertToEntityAttribute(entity.getMetadata());

        AuditMetadata metadata = AuditMetadata.from(meta);

        //inflate with values from entity
        metadata.setCreatedBy(entity.getCreatedBy());
        metadata.setUpdatedBy(entity.getUpdatedBy());

        metadata.setCreated(
            entity.getCreated() != null
                ? OffsetDateTime.ofInstant(entity.getCreated().toInstant(), ZoneOffset.UTC)
                : null
        );
        metadata.setUpdated(
            entity.getUpdated() != null
                ? OffsetDateTime.ofInstant(entity.getUpdated().toInstant(), ZoneOffset.UTC)
                : null
        );

        return metadata;
    }
}
