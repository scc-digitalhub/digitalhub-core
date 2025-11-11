/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.objects;

import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.io.Serializable;

public class K8sWorkflowObject implements KubernetesObject, Serializable {

    private IoArgoprojWorkflowV1alpha1Workflow workflow;

    public K8sWorkflowObject(IoArgoprojWorkflowV1alpha1Workflow workflow) {
        this.workflow = workflow;
    }

    //private to allow deserialization via jackson
    protected K8sWorkflowObject() {
        this(null);
    }

    @Override
    public String getApiVersion() {
        return workflow.getApiVersion();
    }

    @Override
    public String getKind() {
        return workflow.getKind();
    }

    @Override
    public V1ObjectMeta getMetadata() {
        return workflow.getMetadata();
    }

    public IoArgoprojWorkflowV1alpha1Workflow getWorkflow() {
        return workflow;
    }
}
