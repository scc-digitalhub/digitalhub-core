/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runs.service;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.metrics.service.BaseEntityMetricsFinalizer;
import it.smartcommunitylabdhub.runs.Run;
import org.springframework.stereotype.Component;

/**
 * Run metrics cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = Run.class)
@Component
public class RunMetricsFinalizer extends BaseEntityMetricsFinalizer<Run> {}
