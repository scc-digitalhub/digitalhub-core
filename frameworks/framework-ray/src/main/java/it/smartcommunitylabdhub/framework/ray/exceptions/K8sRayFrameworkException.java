/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.exceptions;

import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;

public class K8sRayFrameworkException extends K8sFrameworkException {

    public K8sRayFrameworkException(String message) {
        super(message);
    }

    public K8sRayFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public K8sRayFrameworkException(String message, String error) {
        super(message, error);
    }
}
