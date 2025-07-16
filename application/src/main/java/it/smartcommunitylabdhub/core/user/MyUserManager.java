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

package it.smartcommunitylabdhub.core.user;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Slf4j
public class MyUserManager {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EntityService<Project> projectService;

    private Map<String, EntityService<? extends BaseDTO>> entityServices = Collections.emptyMap();

    private List<SearchableEntityRepository<? extends BaseEntity, ? extends BaseDTO>> entityRepositories =
        Collections.emptyList();

    @Autowired(required = false)
    public void setEntityServices(List<EntityService<? extends BaseDTO>> entityServices) {
        this.entityServices =
            entityServices
                .stream()
                .filter(s -> s.getType() != null)
                .collect(Collectors.toMap(s -> s.getType().name(), s -> s));
    }

    @Autowired(required = false)
    public void setEntityRepositories(
        List<SearchableEntityRepository<? extends BaseEntity, ? extends BaseDTO>> entityRepositories
    ) {
        this.entityRepositories = entityRepositories;
    }

    private UserAuthentication<?> curAuth() {
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("No (valid) user authentication found");
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }
        return auth;
    }

    private String curUser() {
        return curAuth().getName();
    }

    /*
     * Projects
     */

    public List<Project> listMyProjects() {
        String user = curUser();
        log.debug("list all projects for user {}", user);
        try {
            return projectService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public void deleteMyProject(@NotNull String slug) {
        String user = curUser();
        log.debug("delete project {} for user {}", slug, user);

        try {
            Project project = projectService.get(slug);
            if (!user.equals(project.getUser())) {
                log.warn("User {} not authorized to delete project {}", user, slug);
                throw new InsufficientAuthenticationException("User not authorized to delete project");
            }

            //delete with cascade
            projectService.delete(user, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public void deleteAllMyProjects() {
        String user = curUser();
        log.debug("delete all projects for user {}", user);

        try {
            projectService.deleteByUser(user, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * Entities
     */

    public Map<String, List<? extends BaseDTO>> listMyEntities() {
        String user = curUser();
        log.debug("list all entities for user {}", user);

        return entityServices
            .values()
            .stream()
            .filter(s -> s.getType() != null)
            .collect(
                Collectors.toMap(
                    s -> s.getType().name(),
                    s -> {
                        try {
                            return s.listByUser(user);
                        } catch (StoreException e) {
                            log.error("store error: {}", e.getMessage());
                            throw new SystemException(e.getMessage());
                        }
                    }
                )
            );
    }

    public List<? extends BaseDTO> listMyEntities(@NotNull String type) {
        String user = curUser();
        log.debug("list all {} for user {}", type, user);

        try {
            return entityServices
                .entrySet()
                .stream()
                .filter(e -> e.getKey().equals(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchEntityException("No such entity type: " + type))
                .getValue()
                .listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public void deleteMyEntities() {
        String user = curUser();
        log.debug("delete all entities for user {}", user);

        entityServices
            .values()
            .forEach(s -> {
                try {
                    s.deleteByUser(user, true);
                } catch (StoreException e) {
                    log.error("store error: {}", e.getMessage());
                    throw new SystemException(e.getMessage());
                }
            });
    }

    public void deleteMyEntities(@NotNull String type) {
        String user = curUser();
        log.debug("delete all {} for user {}", type, user);

        try {
            entityServices
                .entrySet()
                .stream()
                .filter(e -> e.getKey().equals(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchEntityException("No such entity type: " + type))
                .getValue()
                .deleteByUser(user, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * User
     */

    public void deleteMyUser() {
        UserAuthentication<?> auth = curAuth();
        String user = auth.getName();

        try {
            log.debug("delete user {}", user);

            //delete all tokens and sessions
            tokenService.getRefreshTokens(auth).forEach(t -> tokenService.revokeRefreshToken(auth, t.getId()));
            tokenService
                .getPersonalAccessTokens(auth)
                .forEach(t -> tokenService.revokePersonalAccessToken(auth, t.getId()));

            Set<String> projectIds = projectService
                .listByUser(user)
                .stream()
                .map(Project::getId)
                .collect(Collectors.toSet());

            //(bulk?) delete all projects with cascade
            //TODO send EntityOp.DELETE for async
            projectIds.forEach(slug -> {
                try {
                    projectService.delete(slug, true);
                } catch (StoreException e) {
                    log.error("store error deleting project {}: {}", slug, e.getMessage());
                    throw new SystemException(e.getMessage());
                }
            });

            //delete all entities in non-owned projects, no cascade
            //TODO send EntityOp.DELETE_ALL for async
            entityRepositories
                .stream()
                .filter(r -> !EntityName.PROJECT.equals(r.getType()))
                .forEach(repo -> {
                    try {
                        repo.deleteAll(projectNotIn(projectIds));
                    } catch (StoreException e) {
                        log.error("store error deleting entities for user {}: {}", user, e.getMessage());
                        throw new SystemException(e.getMessage());
                    }
                });
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * Helper methods
     */
    private static <T extends BaseEntity> Specification<T> projectNotIn(Set<String> ids) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.not(root.get(AbstractEntity_.PROJECT).in(ids));
    }
}
