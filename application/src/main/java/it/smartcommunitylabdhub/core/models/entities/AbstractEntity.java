package it.smartcommunitylabdhub.core.models.entities;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@EntityListeners({ AuditingEntityListener.class })
public abstract class AbstractEntity implements BaseEntity {

    @Id
    @Column(unique = true, updatable = false)
    protected String id;

    @Column(nullable = false, updatable = false)
    protected String kind;

    @Column(nullable = false, updatable = false)
    protected String project;

    @CreatedDate
    @Column(updatable = false)
    protected Date created;

    @LastModifiedDate
    protected Date updated;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    protected String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    protected String updatedBy;

    @Lob
    @ToString.Exclude
    protected byte[] metadata;
    // @PrePersist
    // public void prePersist() {
    //     if (id == null) {
    //         this.id = UUID.randomUUID().toString();
    //     }
    // }
}
