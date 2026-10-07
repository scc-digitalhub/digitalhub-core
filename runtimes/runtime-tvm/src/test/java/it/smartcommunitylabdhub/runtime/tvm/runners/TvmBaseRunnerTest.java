/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kubernetes.client.openapi.models.V1NodeSelectorRequirement;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvmBaseRunnerTest {

    @Test
    void keepsTheToolkitJobsOffThe32BitArmNodes() {
        CoreAffinity affinity = TvmBaseRunner.toolkitAffinity();
        List<V1NodeSelectorTerm> terms = affinity
            .getNodeAffinity()
            .getRequiredDuringSchedulingIgnoredDuringExecution()
            .getNodeSelectorTerms();

        assertEquals(1, terms.size());
        V1NodeSelectorRequirement requirement = terms.get(0).getMatchExpressions().get(0);
        assertEquals("kubernetes.io/arch", requirement.getKey());
        assertEquals("In", requirement.getOperator());
        assertEquals(List.of("amd64", "arm64"), requirement.getValues());
        assertFalse(requirement.getValues().contains("arm"));
    }
}
