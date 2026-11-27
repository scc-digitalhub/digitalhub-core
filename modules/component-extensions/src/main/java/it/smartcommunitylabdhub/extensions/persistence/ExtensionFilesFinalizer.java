/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.extensions.persistence;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.model.ParentAccessor;
import it.smartcommunitylabdhub.extensions.service.ExtensionsFilesService;
import it.smartcommunitylabdhub.files.service.BaseFilesFinalizer;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Extension files cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = Extension.class)
@Component
public class ExtensionFilesFinalizer extends BaseFilesFinalizer<Extension> {

    /**
     * Extensions own their folder inside the parent folder:
     * <base><parent type>/<parent name>/<parent id>/extensions/<ext name>/
     */
    @Override
    @Nullable
    protected String ownedFolder(@NotNull Extension extension, @NotNull String base) {
        if (!StringUtils.hasText(extension.getEntity()) || !StringUtils.hasText(extension.getName())) {
            return null;
        }

        try {
            ParentAccessor parent = ParentAccessor.with(extension.getParent());
            if (!StringUtils.hasText(parent.getName()) || !StringUtils.hasText(parent.getId())) {
                return null;
            }

            //same folder used for uploads (parent name encoded as in keys)
            return ExtensionsFilesService.extensionFolder(
                base,
                extension.getEntity(),
                parent.getName(),
                parent.getId(),
                extension.getName()
            );
        } catch (IllegalArgumentException e) {
            //invalid parent: nothing owned
            return null;
        }
    }
}
