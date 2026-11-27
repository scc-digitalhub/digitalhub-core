/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.triggers.service;

import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.core.services.BaseEntityFinalizer;
import org.springframework.stereotype.Component;

/**
 * Finalizer for Trigger: runs the "onDeleting" effects of the type
 */
@Component
public class TriggerEntityFinalizer extends BaseEntityFinalizer<Trigger> {}
