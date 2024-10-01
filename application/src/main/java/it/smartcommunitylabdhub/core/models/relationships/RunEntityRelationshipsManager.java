package it.smartcommunitylabdhub.core.models.relationships;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.builders.run.RunDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;

@Component
@Transactional
public class RunEntityRelationshipsManager extends BaseEntityRelationshipsManager<RunEntity, Run> {
	private static final String TYPE = EntityName.MODEL.getValue();
	
	private final RunDTOBuilder builder;
	
	public RunEntityRelationshipsManager(RunDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");
        this.builder = builder;		
	}
	
	@Override
	public void handleEvent(EntityEvent<RunEntity> event) {
		Assert.notNull(event.getEntity(), "entity can not be null");
		switch (event.getAction()) {
		case CREATE: {
			Run item = builder.convert(event.getEntity());
	        if (item == null) {
	            throw new IllegalArgumentException("invalid or null entity");
	        }
	        RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
	        createRelationships(TYPE, item, relationships);
			break;
		}
		case DELETE: {
			Run item = builder.convert(event.getEntity());
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
