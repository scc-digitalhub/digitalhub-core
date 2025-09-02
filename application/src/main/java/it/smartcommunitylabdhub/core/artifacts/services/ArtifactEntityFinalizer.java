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

package it.smartcommunitylabdhub.core.artifacts.services;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.core.artifacts.specs.ArtifactBaseSpec;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class ArtifactEntityFinalizer implements EntityFinalizer<Artifact>, InitializingBean {

    private FilesService filesService;
    private FilesInfoService filesInfoService;
    private CredentialsService credentialsService;

    @Autowired(required = false)
    public void setFilesService(FilesService filesService) {
        this.filesService = filesService;
    }

    @Autowired(required = false)
    public void setFilesInfoService(FilesInfoService filesInfoService) {
        this.filesInfoService = filesInfoService;
    }

    @Autowired(required = false)
    public void setCredentialsService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {}

    @Override
    public void finalize(@NotNull Artifact artifact) throws StoreException {
        log.debug("finalize artifact with id {}", artifact.getId());

        String id = artifact.getId();

        if (filesService != null && filesInfoService != null) {
            //files
            log.debug("cascade delete files for artifact with id {}", id);

            //extract path from spec
            ArtifactBaseSpec spec = new ArtifactBaseSpec();
            spec.configure(artifact.getSpec());

            String path = spec.getPath();
            if (StringUtils.hasText(path)) {
                //try to resolve credentials
                UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
                List<Credentials> credentials = auth != null && credentialsService != null
                    ? credentialsService.getCredentials(auth)
                    : null;

                //delete files
                filesService.remove(path, credentials);
                filesInfoService.clearFilesInfo(EntityName.ARTIFACT.name(), id);
            }
        }
    }
}
