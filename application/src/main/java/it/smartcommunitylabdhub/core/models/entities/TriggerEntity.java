package it.smartcommunitylabdhub.core.models.entities;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.models.base.SpecEntity;
import it.smartcommunitylabdhub.core.models.base.StatusEntity;
import it.smartcommunitylabdhub.core.models.converters.types.StateStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
@ToString
@Entity
@Table(name = "triggers")
public class TriggerEntity extends AbstractEntity implements SpecEntity, StatusEntity {

    @Column(nullable = false)
    private String task;

    @Column(nullable = false)
    private String name;

    @Lob
    @ToString.Exclude
    protected byte[] spec;

    @Lob
    @ToString.Exclude
    protected byte[] status;

    @Convert(converter = StateStringAttributeConverter.class)
    private State state;
}
