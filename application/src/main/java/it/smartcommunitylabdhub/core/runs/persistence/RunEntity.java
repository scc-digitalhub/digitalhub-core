package it.smartcommunitylabdhub.core.runs.persistence;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity;
import it.smartcommunitylabdhub.core.persistence.SpecEntity;
import it.smartcommunitylabdhub.core.persistence.StatusEntity;
import it.smartcommunitylabdhub.core.repositories.converters.types.StateStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.sql.Types;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;

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
    private String task;

    @JdbcTypeCode(Types.LONGVARBINARY)
    @ToString.Exclude
    protected byte[] spec;

    @JdbcTypeCode(Types.LONGVARBINARY)
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
