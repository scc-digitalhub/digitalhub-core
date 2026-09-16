# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Tests of build_onnx.py, run in the tvm-toolkit image, plus the build -> compile chain."""

import contextlib
import io
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

SCRIPTS = Path(__file__).parents[2] / "main/resources/runtime-tvm/scripts"
sys.path.insert(0, str(SCRIPTS))

import build_onnx  # noqa: E402
import compile_model  # noqa: E402


def dense_model(path: Path) -> None:
    """x [1, 4] -> MatMul(weight [4, 2]) -> y [1, 2]"""
    weight = numpy_helper.from_array(np.arange(8, dtype=np.float32).reshape(4, 2), name="weight")
    graph = helper.make_graph(
        [helper.make_node("MatMul", ["x", "weight"], ["y"])],
        "dense",
        [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1, 4])],
        [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1, 2])],
        [weight],
    )
    onnx.save(helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)]), path)


def quantized_model(path: Path) -> None:
    """q int8 [1, 4] -> DequantizeLinear(scale 0.5, zero point -128) -> y float [1, 4]"""
    graph = helper.make_graph(
        [helper.make_node("DequantizeLinear", ["q", "scale", "zero_point"], ["y"])],
        "dequantize",
        [helper.make_tensor_value_info("q", TensorProto.INT8, [1, 4])],
        [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1, 4])],
        [numpy_helper.from_array(np.array(0.5, dtype=np.float32), name="scale"),
         numpy_helper.from_array(np.array(-128, dtype=np.int8), name="zero_point")],
    )
    onnx.save(helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)]), path)


class BuildOnnxTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.published = []
        publisher = types.ModuleType("publish")
        publisher.publish_ir_model = lambda *args, **kwargs: self.published.append(("ir", args, kwargs))
        publisher.publish_compiled_model = lambda *args: self.published.append(("so", args, {}))
        self.modules = mock.patch.dict(sys.modules, {"publish": publisher})
        self.modules.start()

    def tearDown(self):
        self.modules.stop()
        self.temp_dir.cleanup()

    def build(self, source: Path, output: Path, *arguments: str) -> dict:
        with contextlib.redirect_stdout(io.StringIO()):
            build_onnx.main(["--input", str(source), "--output", str(output), "--name", "dense", *arguments])
        self.assertTrue((output / "model.relax.json").is_file())
        return json.loads((output / "metadata.json").read_text())

    def test_builds_the_ir_and_publishes_it(self):
        dense_model(self.root / "dense.onnx")
        metadata = self.build(self.root / "dense.onnx", self.root / "ir")

        self.assertEqual("onnx", metadata["source_format"])
        self.assertEqual(17, metadata["opset"])
        self.assertEqual([{"name": "x", "shape": [1, 4], "dtype": "float32"}], metadata["inputs"])
        self.assertFalse((self.root / "ir" / "params.bin").exists())
        kind, (out_dir, name, published_metadata), kwargs = self.published[-1]
        self.assertEqual(("ir", "dense"), (kind, name))
        self.assertEqual({"opset": 17, "model_name": "dense", "simplify": False}, kwargs["parameters"])

    def test_weights_kept_apart_are_embedded_again_by_compile(self):
        dense_model(self.root / "dense.onnx")
        self.build(self.root / "dense.onnx", self.root / "ir", "--keep-params-in-input", "true")
        self.assertTrue((self.root / "ir" / "params.bin").is_file())

        with contextlib.redirect_stdout(io.StringIO()):
            compile_model.main(["--ir-dir", str(self.root / "ir"), "--output", str(self.root / "so"), "--target", "llvm",
                                "--benchmark-runs", "2"])
        metadata = json.loads((self.root / "so" / "metadata.json").read_text())
        # The benchmark calls model.so with the real input only: the weights are inside.
        self.assertEqual(2, metadata["benchmark"]["runs"])

    def test_reads_the_quantization_of_int8_inputs(self):
        quantized_model(self.root / "quantized.onnx")
        metadata = self.build(self.root / "quantized.onnx", self.root / "ir")
        self.assertEqual({"name": "q", "shape": [1, 4], "dtype": "int8", "scale": [0.5], "zero_point": [-128]},
                         metadata["inputs"][0])

    def test_finds_the_only_model_of_a_source_folder(self):
        folder = self.root / "input"
        folder.mkdir()
        dense_model(folder / "exported.onnx")
        with mock.patch.object(build_onnx, "INPUT_DIR", folder):
            metadata = self.build(Path("model.onnx"), self.root / "ir")
        self.assertEqual("onnx", metadata["source_format"])


if __name__ == "__main__":
    unittest.main()
