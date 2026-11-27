/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.files;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.files.utils.PathUtils;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Managed files path, on the transition to UPLOADING.
 *
 * Managed files live under the project base path: <base><type>/<name>/<id>/, with type and name
 * as encoded in the entity key. Every entity type name is reserved as first level folder under the
 * project base.
 *
 * - empty path: set to the own folder <base><type>/<name>/<id>/
 * - path within <base><type>/: must be within <base><type>/<name>/<some id>/ (same name, any version)
 * - path within the folder of any other entity type: invalid
 * - anything else (other stores, external, raw project files): accepted, never owned
 *
 * Invalid paths fail the step (CoreRuntimeException). Extended by each entity type with files and an
 * UPLOADING state, annotated for its type.
 */
@Slf4j
public abstract class BaseFilesPathProcessor<D extends BaseDTO & SpecDTO & StatusDTO> implements Processor<D, Spec> {

    private static final String PATH = "path";

    protected FilesService filesService;

    @Autowired(required = false)
    public void setFilesService(FilesService filesService) {
        this.filesService = filesService;
    }

    @Override
    public <I extends Serializable> Spec process(String stage, D dto, @Nullable I input) throws CoreRuntimeException {
        if (filesService == null) {
            return null;
        }

        String base = filesService.getProjectBasePath(dto.getProject());
        String type = type(dto);
        String name = name(dto);
        String path = PathAccessor.with(dto.getSpec()).getPath();

        if (!StringUtils.hasText(path)) {
            //assign the own folder
            String folder = base + type + "/" + name + "/" + dto.getId() + "/";
            log.debug("assign path {} to {}", folder, dto.getId());
            return pathSpec(folder);
        }

        if (PathUtils.isWithin(path, base + type + "/")) {
            //managed: a folder of the same name, any version
            String folder = base + type + "/" + name + "/";
            if (!hasVersion(path, folder)) {
                throw new CoreRuntimeException("invalid path " + path + ": managed files must be within " + folder + "<id>/");
            }
            return null;
        }

        for (String other : Types.ALL) {
            if (!other.equals(type) && PathUtils.isWithin(path, base + other + "/")) {
                throw new CoreRuntimeException("invalid path " + path + ": reserved for " + other + " files");
            }
        }

        //not managed: external, other stores or raw project files
        return null;
    }

    /**
     * True when the path is within the folder AND has a version segment below it
     */
    private static boolean hasVersion(String path, String folder) {
        String p = PathUtils.normalize(path);
        String f = PathUtils.normalize(folder);
        if (p == null || f == null) {
            return false;
        }

        String prefix = f.endsWith("/") ? f : f + "/";
        return p.startsWith(prefix) && p.length() > prefix.length();
    }

    private static String type(BaseDTO dto) {
        String type = KeyAccessor.with(dto.getKey()).getType();
        return StringUtils.hasText(type) ? type : EntityUtils.getEntityName(dto.getClass()).toLowerCase();
    }

    private static String name(BaseDTO dto) {
        String name = KeyAccessor.with(dto.getKey()).getName();
        return StringUtils.hasText(name) ? name : KeyUtils.encode(dto.getName());
    }

    private static Spec pathSpec(String path) {
        return new Spec() {
            @Override
            public Map<String, Serializable> toMap() {
                Map<String, Serializable> map = new HashMap<>();
                map.put(PATH, path);
                return map;
            }

            @Override
            public void configure(Map<String, Serializable> data) {
                //not used
            }
        };
    }

    /*
     * Every entity type, as reserved first level folder under the project base (scanned once)
     */
    private static final class Types {

        private static final Set<String> ALL = EntityUtils.scanForEntities(List.of("it.smartcommunitylabdhub"))
            .stream()
            .map(c -> EntityUtils.getEntityName(c).toLowerCase())
            .collect(Collectors.toUnmodifiableSet());
    }
}
