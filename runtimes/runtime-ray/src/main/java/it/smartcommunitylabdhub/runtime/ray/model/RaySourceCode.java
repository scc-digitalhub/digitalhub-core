/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Source code descriptor for a Ray job. Mirrors the shape of the python source
 * code spec so that the same UI/SDK conventions apply (inline base64 payload,
 * external reference via URI, optional handler).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RaySourceCode extends SourceCode<RaySourceCode.RaySourceCodeLanguages> {

    @Override
    public RaySourceCodeLanguages getLang() {
        return RaySourceCodeLanguages.python;
    }

    public enum RaySourceCodeLanguages {
        python,
    }
}
