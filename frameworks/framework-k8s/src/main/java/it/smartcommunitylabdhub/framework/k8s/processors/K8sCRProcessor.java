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

package it.smartcommunitylabdhub.framework.k8s.processors;

import io.kubernetes.client.openapi.models.V1Service;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sCRHelper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sCRStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CustomResource;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onRunning" }, type = Run.class, spec = Status.class)
@Component
@Slf4j
public class K8sCRProcessor implements Processor<Run, K8sCRStatus> {

    @Autowired
    private K8sCRHelper k8sCRHelper;

    @Override
    public <I> K8sCRStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable) {
            List<CustomResource> customResources = ((K8sRunnable) input).getCustomResources();
            Map<String, Serializable> res = ((K8sRunnable) input).getResults();
            //extract k8s details for svc
            //note: build only if missing, we don't update service info
            if (res != null && customResources != null && run.getStatus().get("crs") == null && res.containsKey("service")) {
                try {
                    Map<String, Serializable> s = (Map<String, Serializable>) res.get("service");
                    V1Service service = KubernetesMapper.OBJECT_MAPPER.convertValue(s, V1Service.class);
                    if (
                        service.getMetadata() == null ||
                        service.getSpec() == null ||
                        service.getSpec().getPorts() == null
                    ) {
                        //missing info
                        return null;
                    }

                    List<Map<String, Serializable>> crSpecs = new java.util.ArrayList<>();  
                    for (CustomResource cr : customResources) {
                        Map<String, Serializable> crSpec = k8sCRHelper.create(cr, service.getMetadata().getLabels(), service);
                        crSpecs.add(crSpec);
                    }

                    return K8sCRStatus.builder().crs(crSpecs).build();
                } catch (ClassCastException | NullPointerException | K8sFrameworkException e) {
                    //invalid definition, skip
                }
            }
        }

        return null;
    }
}
