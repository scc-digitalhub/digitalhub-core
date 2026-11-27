/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.dataitems.services;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.core.services.BaseVersionableFilesFinalizer;
import it.smartcommunitylabdhub.dataitems.DataItem;
import org.springframework.stereotype.Component;

/**
 * DataItem files cleanup on delete
 */
@EffectType(stages = { "onDeleting" }, type = DataItem.class)
@Component
public class DataItemFilesFinalizer extends BaseVersionableFilesFinalizer<DataItem> {}
