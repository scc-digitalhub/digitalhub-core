/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.extensions.persistence;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.files.service.BaseFilesFinalizer;
import org.springframework.stereotype.Component;

/**
 * Extension files cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = Extension.class)
@Component
public class ExtensionFilesFinalizer extends BaseFilesFinalizer<Extension> {}
