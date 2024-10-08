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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@ToString
@Entity
@Table(name = "runs")
public class RunEntity extends AbstractEntity implements SpecEntity, StatusEntity {

    @Column(nullable = false)
    // COMMENT: {kind}+{action}://{project_name}/{function_name}:{version(uuid)} action can be
    // 'build', 'other...'
    private String task;

    @Lob
    @ToString.Exclude
    private byte[] extra;

    @Lob
    @ToString.Exclude
    protected byte[] spec;

    @Lob
    @ToString.Exclude
    protected byte[] status;

    @Convert(converter = StateStringAttributeConverter.class)
    private State state;

    @Override
    public @NotNull String getName() {
        return id;
    }

    @Override
    public void setName(String name) {
        //not available
    }
}
