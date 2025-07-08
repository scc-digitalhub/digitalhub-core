/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.exceptions;

import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;

public class K8sArgoFrameworkException extends K8sFrameworkException {

    public K8sArgoFrameworkException(String message) {
        super(message);
    }

    public K8sArgoFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
