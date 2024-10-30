package it.smartcommunitylabdhub.core.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.builders.function.FunctionDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.FunctionEntity;
import it.smartcommunitylabdhub.core.models.entities.RelationshipEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class FunctionEntityRelationshipsManager extends BaseEntityRelationshipsManager<FunctionEntity> {

    private static final EntityName TYPE = EntityName.FUNCTION;

    private final FunctionDTOBuilder builder;

    public FunctionEntityRelationshipsManager(FunctionDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");
        this.builder = builder;
    }

    @Override
    public void register(FunctionEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Function item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("register for function {}", entity.getId());

            RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
            service.register(item.getProject(), TYPE, item.getId(), item.getKey(), relationships.getRelationships());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public void clear(FunctionEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Function item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("clear for function {}", entity.getId());

            service.clear(item.getProject(), TYPE, item.getId());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(FunctionEntity entity) throws StoreException {
        Assert.notNull(entity, "entity can not be null");

        log.debug("get for function {}", entity.getId());

        List<RelationshipEntity> entries = service.listByEntity(entity.getProject(), TYPE, entity.getId());
        return entries
            .stream()
            .map(e -> new RelationshipDetail(e.getRelationship(), e.getSourceKey(), e.getDestKey()))
            .toList();
    }
}