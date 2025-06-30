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

package it.smartcommunitylabdhub.core.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.services.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.services.EntityService;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.Assert;

@Transactional
@Slf4j
public class BaseRelationshipsAwareEntityService<E extends BaseEntity, D extends BaseDTO & MetadataDTO>
    implements RelationshipsAwareEntityService<D>, InitializingBean {

    protected EntityService<D, E> entityService;
    protected Converter<D, E> entityBuilder;
    protected EntityRelationshipsManager<E> relationshipsManager;

    @Autowired
    public void setEntityService(EntityService<D, E> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setEntityBuilder(Converter<D, E> entityBuilder) {
        this.entityBuilder = entityBuilder;
    }

    @Autowired
    public void setRelationshipsManager(EntityRelationshipsManager<E> relationshipsManager) {
        this.relationshipsManager = relationshipsManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityBuilder, "builder can not be null");
        Assert.notNull(entityService, "entity service can not be null");
        Assert.notNull(relationshipsManager, "relationships manager can not be null");
    }

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for workflow {}", String.valueOf(id));

        try {
            D workflow = entityService.get(id);
            return relationshipsManager.getRelationships(entityBuilder.convert(workflow));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
