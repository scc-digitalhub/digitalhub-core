package it.smartcommunitylabdhub.core.models.relationships;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import it.smartcommunitylabdhub.core.models.entities.RelationshipEntity;
import it.smartcommunitylabdhub.core.repositories.RelationshipRepository;

public abstract class BaseEntityRelationshipsManager<T extends BaseEntity, D extends BaseDTO>
    implements EntityRelationshipsManager<T>, InitializingBean {
	
	@Autowired
	protected RelationshipRepository repository;

    @Override
    public void afterPropertiesSet() throws Exception {
    }
    
    public List<RelationshipDetail> getRelationships(String project, String entityId) {
		List<RelationshipDetail> result = new ArrayList<>();
		List<RelationshipEntity> list = repository.findByEntityId(project, entityId);
		for(RelationshipEntity entity : list) {
			RelationshipDetail detail = new RelationshipDetail();
			detail.setType(entity.getRelationship());
			if(entity.getDestId().equals(entityId)) {
				detail.setSource(entity.getSourceKey());
			}
			if(entity.getSourceId().equals(entityId)) {
				detail.setDest(entity.getDestKey());
			}
			result.add(detail);
		}
		return result;   	
    }
    
    protected void createRelationships(String type, BaseDTO dto, RelationshipsMetadata relationships) {
    	for(RelationshipDetail detail : relationships.getRelationships()) {
        	RelationshipEntity entity = new RelationshipEntity();
        	entity.setId(UUID.randomUUID().toString());
        	entity.setProject(dto.getProject());
        	entity.setRelationship(detail.getType());
        	
        	switch (detail.getType()) {
				case PRODUCEDBY: {
					if(!dto.getProject().equals(getProject(detail.getDest()))) 
						continue;
					entity.setDestId(getId(detail.getDest()));
					entity.setDestType(getType(detail.getDest()));
					entity.setDestKey(detail.getDest());
					entity.setSourceId(dto.getId());
					entity.setSourceKey(dto.getKey());
					entity.setSourceType(type);
					break;
				}
				case CONSUMES:
					if(!dto.getProject().equals(getProject(detail.getDest()))) 
						continue;
					entity.setDestId(getId(detail.getDest()));
					entity.setDestType(getType(detail.getDest()));
					entity.setDestKey(detail.getDest());
					entity.setSourceId(dto.getId());
					entity.setSourceKey(dto.getKey());
					entity.setSourceType(type);
					break;
				default:
					break;
			}
        	
        	repository.save(entity);
    	}
    }
    
    protected String getId(String key) {
    	String id = key.replace("store://", "").split("/")[3];
    	if(id.contains(":")) {
    		String[] strings = id.split(":");
    		return strings[strings.length -1];
    	}
    	return id;
    }
    
    protected String getType(String key) {
    	return key.replace("store://", "").split("/")[1];
    }
    
    protected String getProject(String key) {
    	return key.replace("store://", "").split("/")[0];
    }
    
    protected void deleteRelationships(BaseDTO dto) {
    	List<RelationshipEntity> list = repository.findByEntityId(dto.getProject(), dto.getId());
    	repository.deleteAll(list);
    }
}
