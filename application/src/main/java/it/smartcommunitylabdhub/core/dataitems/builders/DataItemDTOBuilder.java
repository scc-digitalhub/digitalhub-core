package it.smartcommunitylabdhub.core.dataitems.builders;

import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.metadata.EmbeddableMetadata;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.dataitems.persistence.DataItemEntity;
import it.smartcommunitylabdhub.core.metadata.AuditMetadataBuilder;
import it.smartcommunitylabdhub.core.metadata.BaseMetadataBuilder;
import it.smartcommunitylabdhub.core.metadata.VersioningMetadataBuilder;
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
public class DataItemDTOBuilder implements Converter<DataItemEntity, DataItem> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;
    private BaseMetadataBuilder baseMetadataBuilder;
    private AuditMetadataBuilder auditingMetadataBuilder;
    private VersioningMetadataBuilder versioningMetadataBuilder;

    public DataItemDTOBuilder(
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

    @Autowired
    public void setVersioningMetadataBuilder(VersioningMetadataBuilder versioningMetadataBuilder) {
        this.versioningMetadataBuilder = versioningMetadataBuilder;
    }

    public DataItem build(DataItemEntity entity) {
        //read metadata map as-is
        Map<String, Serializable> meta = converter.convertToEntityAttribute(entity.getMetadata());

        // build metadata
        Map<String, Serializable> metadata = new HashMap<>();
        metadata.putAll(meta);

        EmbeddableMetadata embeddable = EmbeddableMetadata.from(meta);
        embeddable.setEmbedded(entity.getEmbedded());
        metadata.putAll(embeddable.toMap());

        Optional.of(baseMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));
        Optional.of(auditingMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));
        Optional.of(versioningMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));

        return DataItem
            .builder()
            .id(entity.getId())
            .name(entity.getName())
            .kind(entity.getKind())
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
    public DataItem convert(DataItemEntity source) {
        return build(source);
    }
}
