package it.smartcommunitylabdhub.core.models.builders.report;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.report.Report;
import it.smartcommunitylabdhub.commons.models.report.ReportBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.EmbeddableMetadata;
import it.smartcommunitylabdhub.core.models.entities.ReportEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ReportEntityBuilder implements Converter<Report, ReportEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public ReportEntityBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    /**
     * Build an artifact from an artifactDTO and store extra values as a cbor
     *
     * @param dto the artifact DTO
     * @return Report
     */
    public ReportEntity build(Report dto) {
        // Extract data
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(dto.getStatus());
        BaseMetadata metadata = BaseMetadata.from(dto.getMetadata());
        EmbeddableMetadata embeddable = EmbeddableMetadata.from(dto.getMetadata());
        ReportBaseSpec spec = new ReportBaseSpec();
        spec.configure(dto.getSpec());

        return ReportEntity
            .builder()
            .id(dto.getId())
            .name(dto.getName())
            .kind(dto.getKind())
            .project(dto.getProject())
            .metadata(converter.convertToDatabaseColumn(dto.getMetadata()))
            .spec(converter.convertToDatabaseColumn(dto.getSpec()))
            .status(converter.convertToDatabaseColumn(dto.getStatus()))
            .entity(spec.getEntity())
            .entityType(spec.getEntityType())
            .state(
                // Store status if not present
                statusFieldAccessor.getState() == null ? State.CREATED : State.valueOf(statusFieldAccessor.getState())
            )
            // Metadata Extraction
            .embedded(embeddable.getEmbedded() == null ? Boolean.FALSE : embeddable.getEmbedded())
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
    public ReportEntity convert(Report source) {
        return build(source);
    }
}
