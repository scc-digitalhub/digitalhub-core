/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.extensions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.extensions.model.ExtensibleDTO;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

/**
 * AOP aspect that augments every {@link EntityService} whose DTO type implements
 * {@link ExtensibleDTO} with extension lifecycle management.
 *
 * <p>This aspect replaces the former {@link ExtensibleEntityService} decorator and fires
 * automatically for all Spring-managed {@code EntityService} beans — no per-entity
 * configuration is required.
 *
 * <p>Only the methods that carry extension semantics ({@code create}, {@code update},
 * {@code get}, {@code delete*}) are intercepted; all other pass-through methods proceed
 * untouched.
 *
 * <p><b>Update advice timing</b>: {@code BaseEntityServiceImpl.update(id, dto)} delegates
 * to {@code this.update(id, dto, false)} via a direct self-call that bypasses the Spring
 * proxy. Therefore the {@code afterUpdate} advice fires exactly once regardless of which
 * overload the external caller uses.
 */
@Aspect
@Component
@Slf4j
public class ExtensionsEntityServiceAspect {

    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private final ExtensionManager extManager;

    public ExtensionsEntityServiceAspect(ExtensionManager extManager) {
        this.extManager = extManager;
    }

    /** Returns true when the DTO type handled by the given service implements {@link ExtensibleDTO}. */
    private boolean isExtensible(EntityService<?> service) {
        return ExtensibleDTO.class.isAssignableFrom(service.getType());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @AfterReturning(
        pointcut = "execution(* *.create(..)) && target(service) && args(dto,..)",
        returning = "result"
    )
    public void afterCreate(EntityService<?> service, Object dto, Object result) {
        if (!isExtensible(service)) {
            return;
        }
        if (!(dto instanceof ExtensibleDTO inputDto)) {
            return;
        }
        if (!(result instanceof ExtensibleDTO outputDto) || !(result instanceof BaseDTO resBase)) {
            return;
        }

        List<Map<String, Serializable>> extensions = inputDto.getExtensions();
        if (extensions == null) {
            return;
        }

        log.debug("create extensions for dto with id {}", String.valueOf(resBase.getId()));
        if (log.isTraceEnabled()) {
            log.trace("extensions: {}", extensions);
        }

        String entityName = EntityUtils.getEntityName(service.getType());
        List<Extension> exts = extensions
            .stream()
            .map(e -> {
                try {
                    Extension ed = mapper.convertValue(e, Extension.class);
                    Extension ext = ExtensionBuilder.from(resBase);
                    ext.setKind(ed.getKind());
                    ext.setName(ed.getName());
                    ext.setSpec(ed.getSpec());
                    ext.setEntity(entityName);
                    return extManager.createExtension(ext);
                } catch (DuplicatedEntityException | BindException | IllegalArgumentException | SystemException ex) {
                    log.error(
                        "error creating extension for entity {}: {}",
                        String.valueOf(resBase.getId()),
                        ex.getMessage()
                    );
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();

        outputDto.setExtensions(exts.stream().map(e -> mapper.convertValue(e, typeRef)).collect(Collectors.toList()));
    }

    // ── update ────────────────────────────────────────────────────────────────
    //
    // Intercepts both update(id, dto) and update(id, dto, forceUpdate).
    // BaseEntityServiceImpl.update(id, dto) delegates to update(id, dto, false) via
    // a direct this-call (self-invocation), so Spring AOP will NOT re-intercept that
    // inner call — extension logic runs exactly once regardless of which overload the
    // caller uses.

    // args(id, dto, ..) binds the first two params of both update overloads:
    //   update(String id, D dto)  and  update(String id, D dto, boolean forceUpdate)
    @AfterReturning(
        pointcut = "execution(* *.update(..)) && target(service) && args(id, dto, ..)",
        returning = "result"
    )
    public void afterUpdate(EntityService<?> service, String id, Object dto, Object result) {
        if (!isExtensible(service)) {
            return;
        }
        if (!(dto instanceof ExtensibleDTO inputDto)) {
            return;
        }
        if (!(result instanceof ExtensibleDTO outputDto) || !(result instanceof BaseDTO resBase)) {
            return;
        }

        List<Map<String, Serializable>> extensions = inputDto.getExtensions();
        if (extensions == null) {
            return;
        }

        log.debug("update extensions for dto with id {}", String.valueOf(resBase.getId()));
        if (log.isTraceEnabled()) {
            log.trace("extensions: {}", extensions);
        }

        String entityName = EntityUtils.getEntityName(service.getType());
        String parentId = ExtensionBuilder.from(resBase).getParent();
        List<Extension> existing;
        try {
            existing = extManager.listExtensionsByParent(parentId);
        } catch (SystemException e) {
            log.error("error listing extensions for reconciliation, id {}: {}", resBase.getId(), e.getMessage());
            existing = Collections.emptyList();
        }

        List<Extension> exts = extensions
            .stream()
            .map(e -> {
                try {
                    Extension ed = mapper.convertValue(e, Extension.class);
                    Extension ext = ExtensionBuilder.from(resBase);
                    ext.setKind(ed.getKind());
                    ext.setName(ed.getName());
                    ext.setSpec(ed.getSpec());
                    ext.setEntity(entityName);
                    return extManager.createOrUpdateExtension(ext);
                } catch (BindException | IllegalArgumentException | SystemException ex) {
                    log.error(
                        "error updating extension for entity {}: {}",
                        String.valueOf(resBase.getId()),
                        ex.getMessage()
                    );
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();

        existing
            .stream()
            .filter(e -> exts.stream().noneMatch(k -> Objects.equals(e.getId(), k.getId())))
            .forEach(e -> {
                try {
                    extManager.deleteExtension(e.getId());
                } catch (SystemException ex) {
                    log.error("error deleting extension with id {}: {}", e.getId(), ex.getMessage());
                }
            });

        outputDto.setExtensions(exts.stream().map(e -> mapper.convertValue(e, typeRef)).collect(Collectors.toList()));
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @AfterReturning(
        pointcut = "execution(* *.get(..)) && target(service)",
        returning = "result"
    )
    public void afterGet(EntityService<?> service, Object result) {
        if (!isExtensible(service)) {
            return;
        }
        if (!(result instanceof ExtensibleDTO outputDto) || !(result instanceof BaseDTO resBase)) {
            return;
        }

        try {
            String entityName = EntityUtils.getEntityName(service.getType());
            String parentId = ExtensionBuilder.from(resBase).getParent();

            List<Extension> extensions = extManager.listExtensionsByParent(parentId);
            if (extensions != null && !extensions.isEmpty()) {
                outputDto.setExtensions(
                    extensions
                        .stream()
                        .filter(e -> entityName.equals(e.getEntity()))
                        .map(e -> mapper.convertValue(e, typeRef))
                        .collect(Collectors.toList())
                );
            }
        } catch (SystemException e) {
            log.error("error fetching extensions for id {}: {}", resBase.getId(), e.getMessage());
        }
    }

    // ── delete(id, cascade) ───────────────────────────────────────────────────
    //
    // Extensions must be removed BEFORE the entity is deleted to avoid orphans in case
    // the entity deletion fails.  We fetch the DTO directly from the raw target (bypassing
    // the proxy) to compute the parent URI without triggering a recursive aspect call.

    @Before("execution(* *.delete(String, Boolean)) && target(service) && args(id,..)")
    public void beforeDeleteById(EntityService<?> service, String id) {
        if (!isExtensible(service)) {
            return;
        }
        try {
            Object found = service.find(id);
            if (found instanceof BaseDTO dto) {
                extManager.deleteExtensionsByParent(ExtensionBuilder.from(dto).getParent());
            }
        } catch (StoreException | SystemException e) {
            log.error("error cleaning up extensions for id {}: {}", id, e.getMessage());
        }
    }

    // ── deleteAll ─────────────────────────────────────────────────────────────

    @AfterReturning("execution(* *.deleteAll(..)) && target(service)")
    public void afterDeleteAll(EntityService<?> service) {
        if (!isExtensible(service)) {
            return;
        }
        try {
            extManager.deleteExtensionByEntity(EntityUtils.getEntityName(service.getType()));
        } catch (StoreException e) {
            log.error("error cleaning up extensions: {}", e.getMessage());
        }
    }

    // ── deleteByUser ──────────────────────────────────────────────────────────

    @AfterReturning("execution(* *.deleteByUser(..)) && target(service) && args(user,..)")
    public void afterDeleteByUser(EntityService<?> service, String user) {
        if (!isExtensible(service)) {
            return;
        }
        try {
            extManager.deleteExtensionByEntityAndUser(EntityUtils.getEntityName(service.getType()), user);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for user {}: {}", user, e.getMessage());
        }
    }

    // ── deleteByProject ───────────────────────────────────────────────────────

    @AfterReturning("execution(* *.deleteByProject(..)) && target(service) && args(project,..)")
    public void afterDeleteByProject(EntityService<?> service, String project) {
        if (!isExtensible(service)) {
            return;
        }
        try {
            extManager.deleteExtensionByEntityAndProject(EntityUtils.getEntityName(service.getType()), project);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for project {}: {}", project, e.getMessage());
        }
    }

    // ── deleteByKind ──────────────────────────────────────────────────────────

    @AfterReturning("execution(* *.deleteByKind(..)) && target(service) && args(kind,..)")
    public void afterDeleteByKind(EntityService<?> service, String kind) {
        if (!isExtensible(service)) {
            return;
        }
        try {
            extManager.deleteExtensionByEntityAndKind(EntityUtils.getEntityName(service.getType()), kind);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for kind {}: {}", kind, e.getMessage());
        }
    }
}
