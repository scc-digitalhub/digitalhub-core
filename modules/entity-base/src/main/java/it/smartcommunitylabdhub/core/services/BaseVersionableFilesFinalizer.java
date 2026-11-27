/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.files.service.BaseFilesFinalizer;
import it.smartcommunitylabdhub.files.utils.PathUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Files finalizer for versioned entities: a version owns its folder <base>/<type>/<name>/<id>/.
 *
 * Versions of the same name may share a folder (a new version pointing to the folder of a previous
 * one): the folder is removed only when no other version still in use references it, so the last
 * version referencing it removes it. Versions being deleted are not considered, so that versions
 * deleted together never leave the folder behind.
 */
@Slf4j
public abstract class BaseVersionableFilesFinalizer<D extends BaseDTO & SpecDTO & StatusDTO>
    extends BaseFilesFinalizer<D> {

    private static final Set<String> GONE_STATES = Set.of("DELETING", "DELETED");

    //lazy: the service depends on the lifecycle, which runs this finalizer
    private ObjectProvider<VersionableEntityService<D>> versionableService;

    @Autowired(required = false)
    public void setVersionableService(ObjectProvider<VersionableEntityService<D>> versionableService) {
        this.versionableService = versionableService;
    }

    @Override
    @Nullable
    protected String ownedFolder(@NotNull D dto, @NotNull String base) {
        String type = EntityUtils.getEntityName(dto.getClass()).toLowerCase();
        return versionFolder(PathAccessor.with(dto.getSpec()).getPath(), base, type, dto.getName());
    }

    @Override
    protected boolean isReferenced(@NotNull D dto, @NotNull String folder) {
        VersionableEntityService<D> service = versionableService != null ? versionableService.getIfAvailable() : null;
        if (service == null) {
            return false;
        }

        try {
            List<D> versions = service.findAll(dto.getProject(), dto.getName());
            return versions
                .stream()
                .filter(v -> !dto.getId().equals(v.getId()))
                .filter(v -> !GONE_STATES.contains(StatusFieldAccessor.with(v.getStatus()).getState()))
                .anyMatch(v -> PathUtils.isWithin(PathAccessor.with(v.getSpec()).getPath(), folder));
        } catch (StoreException e) {
            //unknown: never remove files possibly in use
            log.error("error reading versions of {}: {}", dto.getName(), e.getMessage());
            return true;
        }
    }

    /**
     * The version folder containing the path: <base><type>/<name>/<segment>/, null when the path is
     * not within <base><type>/<name>/ or has no version segment
     */
    @Nullable
    protected static String versionFolder(
        @Nullable String path,
        @NotNull String base,
        @NotNull String type,
        @NotNull String name
    ) {
        String p = PathUtils.normalize(path);
        String prefix = PathUtils.normalize(base + type + "/" + name + "/");
        if (p == null || prefix == null || !p.startsWith(prefix)) {
            return null;
        }

        String rest = p.substring(prefix.length());
        int idx = rest.indexOf('/');
        String segment = idx < 0 ? rest : rest.substring(0, idx);

        return StringUtils.hasText(segment) ? prefix + segment + "/" : null;
    }
}
