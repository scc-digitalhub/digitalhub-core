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

/**
 * Model kind "tflite": a TensorFlow Lite flatbuffer uploaded as the source of a tvm
 * function.
 *
 * When a tvm function points at a model of this kind, tvm+build picks the TFLite builder
 * without looking at the file extension. Quantized models describe their affine
 * parameters (scale, zero_point) on the tensors in inputs/outputs.
 */
@Getter
@Setter
@SpecType(kind = TfliteModelSpec.KIND, entity = Model.class)
public class TfliteModelSpec extends TvmSourceModelSpec {

    public static final String KIND = "tflite";
}
