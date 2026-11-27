/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.files;

import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import org.springframework.stereotype.Component;

/**
 * Model managed files path, on UPLOADING
 */
@ProcessorType(stages = { "onUploading" }, type = Model.class, spec = Spec.class)
@Component
public class ModelFilesPathProcessor extends BaseFilesPathProcessor<Model> {}
