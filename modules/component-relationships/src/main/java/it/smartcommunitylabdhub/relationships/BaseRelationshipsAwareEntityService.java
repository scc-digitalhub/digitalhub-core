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

package it.smartcommunitylabdhub.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

@Transactional
@Slf4j
public class BaseRelationshipsAwareEntityService<D extends BaseDTO & MetadataDTO>
    implements RelationshipsAwareEntityService<D>, InitializingBean {

    protected EntityRepository<D> entityService;
    protected EntityRelationshipsManager<D> relationshipsManager;

    @Autowired
    public void setEntityService(EntityRepository<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setRelationshipsManager(EntityRelationshipsManager<D> relationshipsManager) {
        this.relationshipsManager = relationshipsManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityService, "entity service can not be null");
        Assert.notNull(relationshipsManager, "relationships manager can not be null");
    }

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for workflow {}", String.valueOf(id));

        try {
            D item = entityService.get(id);
            return relationshipsManager.getRelationships(item);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
