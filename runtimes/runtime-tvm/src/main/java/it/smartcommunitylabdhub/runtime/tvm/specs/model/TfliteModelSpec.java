/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.models.Model;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = TfliteModelSpec.KIND, entity = Model.class, uiSchema = "runtime-tvm/tflite/uiSchema.json")
public class TfliteModelSpec extends TvmSourceModelSpec {

    public static final String KIND = "tflite";
}
