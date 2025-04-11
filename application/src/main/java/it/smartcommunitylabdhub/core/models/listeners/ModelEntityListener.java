package it.smartcommunitylabdhub.core.models.listeners;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.commons.models.files.FilesInfo;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.model.ModelBaseSpec;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.services.FilesInfoService;
import it.smartcommunitylabdhub.core.components.security.UserAuthenticationHelper;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.files.service.FilesService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class ModelEntityListener extends AbstractEntityListener<ModelEntity, Model> {

    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private FilesService filesService;

    @Autowired
    private FilesInfoService filesInfoService;

    public ModelEntityListener(Converter<ModelEntity, Model> converter) {
        super(converter);
    }

    @Autowired
    public void setProjectService(EntityService<Project, ProjectEntity> projectService) {
        this.projectService = projectService;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityEvent<ModelEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        //handle
        super.handle(event);

        //files
        if ((filesService != null) && event.getAction().equals(EntityAction.DELETE)) {
            try {
                String id = event.getEntity().getId();
                Model entity = converter.convert(event.getEntity());

                StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
                List<FileInfo> files = statusFieldAccessor.getFiles();

                if (files == null || files.isEmpty()) {
                    FilesInfo filesInfo = filesInfoService.getFilesInfo(EntityName.MODEL.getValue(), id);
                    if (filesInfo != null && (filesInfo.getFiles() != null)) {
                        files = filesInfo.getFiles();
                    } else {
                        files = null;
                    }
                }

                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(entity.getSpec());

                String path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }

                for (FileInfo fileInfo : files) {
                    if (path.endsWith("/")) {
                        filesService.remove(
                            path + fileInfo.getPath(),
                            UserAuthenticationHelper.getUserAuthentication()
                        );
                    } else {
                        filesService.remove(path, UserAuthenticationHelper.getUserAuthentication());
                    }
                }
            } catch (Exception e) {
                log.error("store error", e.getMessage());
            }
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
