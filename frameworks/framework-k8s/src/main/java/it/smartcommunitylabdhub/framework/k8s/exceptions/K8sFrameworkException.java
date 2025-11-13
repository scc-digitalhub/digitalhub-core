/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.framework.k8s.exceptions;

import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;

public class K8sFrameworkException extends FrameworkException {

    public static final String DEFAULT_MESSAGE = "Error with the Kubernetes API.";
    private final String apiResponse;

    public K8sFrameworkException() {
        super(DEFAULT_MESSAGE);
        this.apiResponse = null;
    }

    public K8sFrameworkException(String msg) {
        super(msg);
        this.apiResponse = null;
    }

    public K8sFrameworkException(String msg, String apiResponse) {
        super(msg);
        this.apiResponse = apiResponse;
    }

    public K8sFrameworkException(String msg, Throwable cause) {
        super(msg, cause);
        this.apiResponse = null;
    }

    public String getApiResponse() {
        return apiResponse;
    }

    public String toError() {
        StringBuilder sb = new StringBuilder("k8s: ");
        if (getMessage() != null) {
            sb.append(getMessage()).append(" ");
        }
        if (getApiResponse() != null) {
            sb.append(getApiResponse()).append(" ");
        }
        return sb.toString();
    }
}
