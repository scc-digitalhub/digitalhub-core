package it.smartcommunitylabdhub.core.models.entities.project;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "projects")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@EntityListeners({ AuditingEntityListener.class })
public class ProjectEntity implements BaseEntity {

    @Id
    @Column(unique = true)
    private String id;

    @Column(unique = true)
    private String name;

    @Column(nullable = false)
    private String kind;

    private String source;

    @Lob
    @ToString.Exclude
    private byte[] metadata;

    @Lob
    @ToString.Exclude
    private byte[] spec;

    @Lob
    @ToString.Exclude
    private byte[] extra;

    @Lob
    @ToString.Exclude
    private byte[] status;

    @CreatedDate
    @Column(updatable = false)
    private Date created;

    @LastModifiedDate
    private Date updated;

    private State state;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            this.id = this.name;
        }
    }

    @Override
    public String getProject() {
        return name;
    }
}
