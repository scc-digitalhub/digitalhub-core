/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.containerimage.services;

import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.core.services.BaseEntityFinalizer;
import org.springframework.stereotype.Component;

/**
 * Finalizer for ContainerImage: runs the "onDeleting" effects of the type
 */
@Component
public class ContainerImageEntityFinalizer extends BaseEntityFinalizer<ContainerImage> {}
