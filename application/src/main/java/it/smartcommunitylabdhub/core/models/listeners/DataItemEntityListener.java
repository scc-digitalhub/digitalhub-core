package it.smartcommunitylabdhub.core.models.listeners;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.core.models.entities.DataItemEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;
import it.smartcommunitylabdhub.core.services.EntityService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DataItemEntityListener extends AbstractEntityListener<DataItemEntity, DataItem> {

    private EntityService<Project, ProjectEntity> projectService;
    
    public DataItemEntityListener(Converter<DataItemEntity, DataItem> converter) {
        super(converter);
    }

    @Autowired
    public void setProjectService(EntityService<Project, ProjectEntity> projectService) {
        this.projectService = projectService;
    }
    

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityEvent<DataItemEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        //handle
        super.handle(event);

        //files
        if((filesService != null) && event.getAction().equals(EntityAction.DELETE)) {
        }
        
        //update project date
        if (projectService != null) {
            String projectId = event.getEntity().getProject();
            log.debug("touch update project {}", projectId);

            try {
                Project project = projectService.find(projectId);
                if (project != null) {
                    //touch to set updated
                    projectService.update(project.getId(), project);
                }
            } catch (StoreException e) {
                log.error("store error", e.getMessage());
            }
        }
    }
}
