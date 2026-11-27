/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.artifacts.services;

import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.core.services.BaseVersionableFilesFinalizer;
import org.springframework.stereotype.Component;

/**
 * Artifact files cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = Artifact.class)
@Component
public class ArtifactFilesFinalizer extends BaseVersionableFilesFinalizer<Artifact> {}
