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

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.base.BareSpec;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesModule;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.model.ProfileAccessor;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onBuilt" }, type = Run.class, spec = Spec.class)
@Component
@Slf4j
public class K8sTemplateProcessor implements Processor<Run, Spec>, InitializingBean {

    @Autowired
    private KubernetesModule kubernetesModule;

    private Map<String, K8sTemplate<?>> templates = Map.of();

    @Override
    public void afterPropertiesSet() throws Exception {
        if (kubernetesModule == null) {
            throw new IllegalStateException("kubernetesModule must be set");
        }

        //pick a static copy
        templates =
            kubernetesModule.getTemplates() != null
                ? kubernetesModule.getTemplates().stream().collect(Collectors.toMap(e -> e.getId(), e -> e))
                : Map.of();
        log.debug("Loaded {} templates for post-processing", templates.size());
    }

    @Override
    public Spec process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (run == null || run.getSpec() == null || templates == null) {
            return null;
        }

        String kind = run.getKind();

        //TODO properly discover if the run spec is profile aware, for now we pick `profile`
        ProfileAccessor profileAccessor = ProfileAccessor.with(run.getSpec());
        String profile = profileAccessor.getPath();
        if (!StringUtils.hasText(profile)) {
            return null;
        }
        log.debug("Processing run with kind {} and profile {}", kind, profile);

        K8sTemplate<?> template = templates.get(profile);
        if (template == null || template.getSpecs() == null || !template.getSpecs().containsKey(kind)) {
            log.debug("No template found for profile {} and kind {}", profile, kind);
            return null;
        }

        try {
            log.debug("Found template for profile {} and kind {}", profile, kind);
            Map<String, Serializable> spec = Optional.ofNullable(template.getSpecs().get(kind))
                .map(e -> e.get("spec"))
                .map(e -> (Map<String, Serializable>) e)
                .orElse(Map.of());

            if (log.isTraceEnabled()) {
                log.trace("Template spec for profile {} and kind {}: {}", profile, kind, spec);
            }

            return new BareSpec(spec);
        } catch (Exception e) {
            log.error("Error processing template for profile {} and kind {}", profile, kind, e);
            return null;
        }
    }
}
