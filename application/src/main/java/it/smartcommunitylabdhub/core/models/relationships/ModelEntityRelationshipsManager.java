package it.smartcommunitylabdhub.core.models.relationships;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.models.entities.model.Model;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.builders.model.ModelDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;

@Component
@Transactional
public class ModelEntityRelationshipsManager extends BaseEntityRelationshipsManager<ModelEntity, Model> {
	private static final String TYPE = EntityName.MODEL.getValue();
	
	private final ModelDTOBuilder builder;
	
	public ModelEntityRelationshipsManager(ModelDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");
        this.builder = builder;		
	}
	
	@Override
	public void handleEvent(EntityEvent<ModelEntity> event) {
		Assert.notNull(event.getEntity(), "entity can not be null");
		switch (event.getAction()) {
		case CREATE: {
			Model item = builder.convert(event.getEntity());
	        if (item == null) {
	            throw new IllegalArgumentException("invalid or null entity");
	        }
	        RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
	        createRelationships(TYPE, item, relationships);
			break;
		}
		case DELETE: {
			Model item = builder.convert(event.getEntity());
	        if (item == null) {
	            throw new IllegalArgumentException("invalid or null entity");
	        }
	        deleteRelationships(item);
	        break;
		}
		default:
			break;
		}
	}

}
