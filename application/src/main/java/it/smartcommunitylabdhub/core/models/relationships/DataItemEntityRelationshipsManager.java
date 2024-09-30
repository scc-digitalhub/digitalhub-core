package it.smartcommunitylabdhub.core.models.relationships;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.models.entities.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.builders.dataitem.DataItemDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.DataItemEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;

@Component
@Transactional
public class DataItemEntityRelationshipsManager extends BaseEntityRelationshipsManager<DataItemEntity, DataItem> {
	private static final String TYPE = EntityName.DATAITEM.getValue();
	
	private final DataItemDTOBuilder builder;
	
	public DataItemEntityRelationshipsManager(DataItemDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");
        this.builder = builder;		
	}
	
	@Override
	public void handleEvent(EntityEvent<DataItemEntity> event) {
		Assert.notNull(event.getEntity(), "entity can not be null");
		switch (event.getAction()) {
		case CREATE: {
			DataItem item = builder.convert(event.getEntity());
	        if (item == null) {
	            throw new IllegalArgumentException("invalid or null entity");
	        }
	        RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
	        createRelationships(TYPE, item, relationships);
			break;
		}
		case DELETE: {
			DataItem item = builder.convert(event.getEntity());
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
