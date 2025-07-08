/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.objects;

import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1CronWorkflow;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.io.Serializable;

public class K8sCronWorkflowObject implements KubernetesObject, Serializable {

    private IoArgoprojWorkflowV1alpha1CronWorkflow workflow;

    public K8sCronWorkflowObject(IoArgoprojWorkflowV1alpha1CronWorkflow workflow) {
        this.workflow = workflow;
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

    public IoArgoprojWorkflowV1alpha1CronWorkflow getWorkflow() {
        return workflow;
    }
}
