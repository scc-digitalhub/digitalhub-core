/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.models.services;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.metrics.service.BaseEntityMetricsFinalizer;
import it.smartcommunitylabdhub.models.Model;
import org.springframework.stereotype.Component;

/**
 * Model metrics cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = Model.class)
@Component
public class ModelMetricsFinalizer extends BaseEntityMetricsFinalizer<Model> {}
