/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.containerimages;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.specs.ContainerImageBaseSpec;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Container image cleanup on delete: remove the image from the registry, when possible.
 *
 * A failed removal fails the finalization (CoreRuntimeException), a missing image is fine.
 * Idempotent.
 */
@EffectType(stages = { "onDeleting" }, type = ContainerImage.class)
@Component
@Slf4j
public class ContainerImagesFinalizer implements Effect<ContainerImage> {

    @Autowired(required = false)
    private ContainerImagesService imagesService;

    @Override
    public void process(String stage, ContainerImage dto, @Nullable Serializable output) {
        if (imagesService == null || dto == null || dto.getProject() == null) {
            return;
        }

        ContainerImageBaseSpec spec = new ContainerImageBaseSpec();
        spec.configure(dto.getSpec());

        if (StringUtils.hasText(spec.getImage())) {
            log.debug("Deleting image from registry: {}", spec.getImage());
            try {
                imagesService.deleteImage(spec.getImage());
            } catch (IOException | StoreException e) {
                log.error("Error deleting image {} for {}: {}", spec.getImage(), dto.getId(), e.getMessage());
                throw new CoreRuntimeException("error deleting image: " + e.getMessage(), e);
            }
        }
    }
}
