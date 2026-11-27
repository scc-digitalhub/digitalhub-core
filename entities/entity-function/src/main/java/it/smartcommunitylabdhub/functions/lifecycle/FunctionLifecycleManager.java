/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.functions.lifecycle;

import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.functions.Function;
import org.springframework.stereotype.Service;

/**
 * Function lifecycle: delete runs the finalizers (tasks) as detached effects of DELETING
 *
 * TODO also track and cleanup runs: the delete should wait for the runs of its tasks to be gone
 */
@Service
public class FunctionLifecycleManager extends BaseLifecycleManager<Function> {}
