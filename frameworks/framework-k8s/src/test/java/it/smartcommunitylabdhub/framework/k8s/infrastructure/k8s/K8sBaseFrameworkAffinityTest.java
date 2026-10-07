/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1NodeAffinity;
import io.kubernetes.client.openapi.models.V1NodeSelector;
import io.kubernetes.client.openapi.models.V1NodeSelectorRequirement;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// The affinity of a profile replaces the one of the runnable only when the profile sets one.
class K8sBaseFrameworkAffinityTest {

    private static CoreAffinity architectures(String... values) {
        CoreAffinity affinity = new CoreAffinity();
        affinity.setNodeAffinity(
            new V1NodeAffinity().requiredDuringSchedulingIgnoredDuringExecution(
                new V1NodeSelector().addNodeSelectorTermsItem(
                    new V1NodeSelectorTerm().addMatchExpressionsItem(
                        new V1NodeSelectorRequirement().key("kubernetes.io/arch").operator("In").values(List.of(values))
                    )
                )
            )
        );
        return affinity;
    }

    private static K8sJobFramework framework(K8sJobRunnable... profiles) {
        K8sJobFramework framework = new K8sJobFramework(new ApiClient());
        Map<String, K8sTemplate<K8sJobRunnable>> templates = new HashMap<>();
        for (K8sJobRunnable profile : profiles) {
            templates.put(profile.getId(), new K8sTemplate<>(profile.getId(), "", profile, null, null, null, null));
        }
        framework.templates = templates;
        return framework;
    }

    private static K8sJobRunnable run(String profile, CoreAffinity affinity) {
        return K8sJobRunnable.builder().id("run").template(profile).affinity(affinity).build();
    }

    @Test
    void keepsTheRunAffinityWithoutAProfile() throws Exception {
        CoreAffinity own = architectures("amd64", "arm64");
        assertSame(own, framework().buildAffinity(run(null, own)));
    }

    @Test
    void keepsTheRunAffinityWhenTheProfileHasNone() throws Exception {
        CoreAffinity own = architectures("amd64", "arm64");
        K8sJobFramework framework = framework(
            K8sJobRunnable.builder().id("default").build(),
            K8sJobRunnable.builder().id("big").build()
        );

        assertSame(own, framework.buildAffinity(run(null, own)));
        assertSame(own, framework.buildAffinity(run("big", own)));
    }

    @Test
    void theProfileAffinityWins() throws Exception {
        CoreAffinity own = architectures("amd64", "arm64");
        CoreAffinity profileAffinity = architectures("amd64");
        K8sJobFramework framework = framework(K8sJobRunnable.builder().id("only-amd64").affinity(profileAffinity).build());

        assertSame(profileAffinity, framework.buildAffinity(run("only-amd64", own)));
    }

    @Test
    void noAffinityAtAllStaysEmpty() throws Exception {
        assertNull(framework(K8sJobRunnable.builder().id("default").build()).buildAffinity(run(null, null)));
    }
}
