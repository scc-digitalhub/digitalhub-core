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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseEntityRelationshipsManager<T extends BaseEntity, D extends BaseDTO>
    implements EntityRelationshipsManager<T>, InitializingBean {
	
	@Autowired
	protected RelationshipRepository repository;

    @Override
    public void afterPropertiesSet() throws Exception {
    }
    
    public List<RelationshipDetail> getRelationships(String entityId) {
		List<RelationshipDetail> result = new ArrayList<>();
		List<RelationshipEntity> list = repository.findByParentIdOrChildId(entityId, entityId);
		for(RelationshipEntity entity : list) {
			RelationshipDetail detail = new RelationshipDetail();
			detail.setType(entity.getRelationship());
			if(entity.getChildId().equals(entityId)) {
				detail.setDest(entity.getChildKey());
			}
			if(entity.getParentId().equals(entityId)) {
				detail.setSource(entity.getParentKey());
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
				case producedBy: {
					entity.setChildId(getId(detail.getDest()));
					entity.setChildType(getType(detail.getDest()));
					entity.setChildKey(detail.getDest());
					entity.setParentId(dto.getId());
					entity.setParentKey(dto.getKey());
					entity.setParentType(type);
					break;
				}
				case consumer:
					entity.setChildId(getId(detail.getDest()));
					entity.setChildType(getType(detail.getDest()));
					entity.setChildKey(detail.getDest());
					entity.setParentId(dto.getId());
					entity.setParentKey(dto.getKey());
					entity.setParentType(type);
					break;
				default:
					break;
			}
        	
        	repository.save(entity);
    	}
    }
    
    protected String getId(String key) {
    	String id = key.replace("store://", "").split("/")[3];
    	if(id.contains(":"))
    		return id.split(":")[1];
    	return id;
    }
    
    protected String getType(String key) {
    	return key.replace("store://", "").split("/")[1];
    }
    
    protected void deleteRelationships(BaseDTO dto) {
    	List<RelationshipEntity> list = repository.findByParentIdOrChildId(dto.getId(), dto.getId());
    	repository.deleteAll(list);
    }
}
