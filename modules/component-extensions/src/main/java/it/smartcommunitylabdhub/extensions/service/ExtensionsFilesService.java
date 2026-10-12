/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.extensions.service;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.extensions.model.ExtensibleDTO;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.model.ParentAccessor;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionEntity;
import it.smartcommunitylabdhub.files.base.BaseFilesService;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class ExtensionsFilesService extends BaseFilesService<Extension> {

    private final SearchableEntityRepository<ExtensionEntity, Extension> entityRepository;

    private Map<String, EntityFilesService<? extends BaseDTO>> entityFilesServices;

    public ExtensionsFilesService(SearchableEntityRepository<ExtensionEntity, Extension> entityRepository) {
        Assert.notNull(entityRepository, "entity repository is required");

        this.entityRepository = entityRepository;
    }

    @Autowired
    public void setEntityFilesServices(Collection<EntityFilesService<? extends BaseDTO>> entityFilesServices) {
        //pick only extensible entity file services
        this.entityFilesServices = entityFilesServices
            .stream()
            .filter(this::isExtensible)
            .collect(Collectors.toMap(s -> EntityUtils.getEntityName(s.getType()).toLowerCase(), e -> e));
    }

    @Override
    protected String resolveBasePath(String project, String name, String id, String filename) throws StoreException {
        //entity may not exists (yet)
        Extension dto = entityRepository.find(id);
        if (dto != null) {
            //check for path in parent and resolve relative
            String entity = dto.getEntity().toLowerCase();
            String parent = dto.getParent();
            ParentAccessor accessor = ParentAccessor.with(parent);

            EntityFilesService<? extends BaseDTO> service = entityFilesServices.get(entity);
            if (service != null) {
                UploadInfo info = service.uploadFileAsUrl(
                    accessor.getProject(),
                    accessor.getName(),
                    accessor.getId(),
                    "extensions"
                );

                if (info != null) {
                    return info.getPath();
                }
            }
        }

        //TODO resolve project as fallback - not possible because of circular dependency
        // Project prj = projectService.get(project);
        // ConfigSpecAccessor cfg = ConfigSpecAccessor.with(prj.getSpec());

        // return (
        //     filesService.getDefaultStore(cfg.getConfig()) +
        //     "/" +
        //     project +
        //     "/" +
        //     EntityUtils.getEntityName(Extension.class).toLowerCase()
        // );

        //fallback to default
        return (
            filesService.getDefaultStore(null) + "/" + project + "/" + EntityUtils.getEntityName(type).toLowerCase()
        );
    }

    /** Returns true when the DTO type handled by the given service implements {@link ExtensibleDTO}. */
    private boolean isExtensible(EntityFilesService<?> service) {
        return ExtensibleDTO.class.isAssignableFrom(service.getType());
    }
}
