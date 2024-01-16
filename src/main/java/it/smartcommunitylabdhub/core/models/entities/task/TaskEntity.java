package it.smartcommunitylabdhub.core.models.entities.task;

import it.smartcommunitylabdhub.core.components.cloud.listeners.EntitySavedListener;
import it.smartcommunitylabdhub.core.models.base.interfaces.BaseEntity;
import it.smartcommunitylabdhub.core.models.enums.State;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "tasks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"function", "kind"})

})
@EntityListeners({AuditingEntityListener.class, EntitySavedListener.class})
public class TaskEntity implements BaseEntity {

    @Id
    @Column(unique = true)
    private String id;

    @Column(nullable = false)
    // COMMENT: {function_kind}://{project}/{function}:{version}
    private String function;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String project;

    @Lob
    private byte[] metadata;

    @Lob
    private byte[] spec;

    @Lob
    private byte[] extra;

    @Lob
    private byte[] status;

    @CreatedDate
    @Column(updatable = false)
    private Date created;

    @LastModifiedDate
    private Date updated;

    @Enumerated(EnumType.STRING)
    private State state;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
