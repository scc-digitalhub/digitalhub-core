package it.smartcommunitylabdhub.core.models.entities;

import java.util.Date;

import org.springframework.data.annotation.LastModifiedDate;

import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.core.models.converters.types.RelationshipStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
		name = "relationships",
	    indexes = {
	            @Index(name = "relationships_project_index", columnList = "project"),
	            @Index(name = "relationships_parent_index", columnList = "parentId"),
	            @Index(name = "relationships_child_index", columnList = "childId"),
	        }		
)
public class RelationshipEntity {
    @Id
    @Column(unique = true, updatable = false)
    private String id;

    @Convert(converter = RelationshipStringAttributeConverter.class)
    @Column(nullable = false)
    private RelationshipName relationship;

    @Column(nullable = false, updatable = false)
    private String project;
    
    @Column(nullable = false)
    private String parentType;
    
    @Column(nullable = false)
    private String parentId;
    
    @Column(nullable = false)
    private String parentKey;
    
    @Column(nullable = false)
    private String childType;
    
    @Column(nullable = false)
    private String childId;
    
    @Column(nullable = false)
    private String childKey;
    
    @LastModifiedDate
    private Date updated;

    
}
