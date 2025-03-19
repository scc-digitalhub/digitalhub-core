package it.smartcommunitylabdhub.core.models.entities;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.models.converters.StateStringAttributeConverter;
import it.smartcommunitylabdhub.core.models.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.sql.Types;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
@ToString
@Entity
@Table(name = "workflows")
public class WorkflowEntity extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    private Boolean embedded;

    @JdbcTypeCode(Types.LONGVARBINARY)
    @ToString.Exclude
    protected byte[] spec;

    @JdbcTypeCode(Types.LONGVARBINARY)
    @ToString.Exclude
    protected byte[] status;

    @Convert(converter = StateStringAttributeConverter.class)
    private State state;
}
