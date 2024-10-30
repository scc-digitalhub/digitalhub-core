package it.smartcommunitylabdhub.core.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.core.models.builders.workflow.WorkflowDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.RelationshipEntity;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class WorkflowEntityRelationshipsManager extends BaseEntityRelationshipsManager<WorkflowEntity> {

    private static final EntityName TYPE = EntityName.WORKFLOW;

    private final WorkflowDTOBuilder builder;

    public WorkflowEntityRelationshipsManager(WorkflowDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");
        this.builder = builder;
    }

    @Override
    public void register(WorkflowEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Workflow item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("register for workflow {}", entity.getId());

            RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
            service.register(item.getProject(), TYPE, item.getId(), item.getKey(), relationships.getRelationships());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public void clear(WorkflowEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Workflow item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("clear for workflow {}", entity.getId());

            service.clear(item.getProject(), TYPE, item.getId());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(WorkflowEntity entity) throws StoreException {
        Assert.notNull(entity, "entity can not be null");

        log.debug("get for workflow {}", entity.getId());

        List<RelationshipEntity> entries = service.listByEntity(entity.getProject(), TYPE, entity.getId());
        return entries
            .stream()
            .map(e -> new RelationshipDetail(e.getRelationship(), e.getSourceKey(), e.getDestKey()))
            .toList();
    }
}