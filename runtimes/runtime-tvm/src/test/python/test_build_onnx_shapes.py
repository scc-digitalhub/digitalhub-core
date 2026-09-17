# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Tests of the automatic simplification of build_onnx.py, run in the tvm-toolkit image.

TVM 0.26 imports some shape arithmetic as slices sized at run time, as in the box decoding
of YOLOv8: the IR outputs lose their shape and tvm+compile fails. The build then simplifies
the graph with onnxsim and converts it again.
"""

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
import onnxsim  # noqa: F401 - imported once here: its C extension cannot be imported again after a test
import tvm
from onnx import TensorProto, helper

SCRIPTS = Path(__file__).parents[2] / "main/resources/runtime-tvm/scripts"
sys.path.insert(0, str(SCRIPTS))

import build_onnx  # noqa: E402
import compile_model  # noqa: E402


def constant(name: str, values: list) -> onnx.NodeProto:
    array = np.array(values, dtype=np.int64)
    return helper.make_node("Constant", [], [name], value=helper.make_tensor(f"{name}_value", TensorProto.INT64,
                                                                           array.shape, array.tolist()))


def sliced_by_shape_model(path: Path) -> None:
    """x [1, 8, 4, 4] -> reshape [1, 4, 16] -> keep the first (channels + 1) / 2 channels
    -> y [1, 2, 16], the slice end computed from Shape like in the YOLOv8 head."""
    nodes = [
        constant("new_shape", [1, 4, -1]), helper.make_node("Reshape", ["x", "new_shape"], ["reshaped"]),
        helper.make_node("Shape", ["reshaped"], ["shape"]),
        constant("axis", [1]), helper.make_node("Gather", ["shape", "axis"], ["channels"], axis=0),
        constant("one", [1]), helper.make_node("Add", ["channels", "one"], ["plus_one"]),
        constant("two", [2]), helper.make_node("Div", ["plus_one", "two"], ["end"]),
        constant("zero", [0]), helper.make_node("Slice", ["reshaped", "zero", "end", "axis"], ["sliced"]),
        helper.make_node("Sub", ["sliced", "sliced"], ["y"]),
    ]
    graph = helper.make_graph(nodes, "sliced_by_shape", [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1, 8, 4, 4])],
                              [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1, 2, 16])])
    onnx.save(helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)]), path)


def relu_model(path: Path) -> None:
    """x [1, 4] -> Relu -> y [1, 4]: every shape is known."""
    graph = helper.make_graph([helper.make_node("Relu", ["x"], ["y"])], "relu",
                              [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1, 4])],
                              [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1, 4])])
    onnx.save(helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)]), path)


class BuildOnnxShapesTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.published = []
        publisher = types.ModuleType("publish")
        publisher.publish_ir_model = lambda *args, **kwargs: self.published.append(kwargs["parameters"])
        publisher.publish_compiled_model = lambda *args: None
        self.modules = mock.patch.dict(sys.modules, {"publish": publisher})
        self.modules.start()

    def tearDown(self):
        self.modules.stop()
        self.temp_dir.cleanup()

    def build(self, source: Path, *arguments: str) -> tuple[dict, str]:
        output = self.root / "ir"
        log = io.StringIO()
        with contextlib.redirect_stdout(log):
            build_onnx.main(["--input", str(source), "--output", str(output), "--name", "model", *arguments])
        return json.loads((output / "metadata.json").read_text()), log.getvalue()

    def ir_outputs_without_shape(self) -> list[int]:
        module = tvm.ir.load_json((self.root / "ir" / "model.relax.json").read_text())
        return build_onnx.outputs_without_shape(module)

    def test_simplifies_by_itself_when_the_outputs_lose_their_shape(self):
        sliced_by_shape_model(self.root / "sliced.onnx")
        metadata, log = self.build(self.root / "sliced.onnx")

        self.assertIn("some outputs have no shape", log)
        self.assertTrue(metadata["simplify"])
        self.assertTrue(metadata["simplified_automatically"])
        self.assertTrue(self.published[-1]["simplify"])
        self.assertEqual([], self.ir_outputs_without_shape())

        # The simplified IR compiles.
        with contextlib.redirect_stdout(io.StringIO()):
            compile_model.main(["--ir-dir", str(self.root / "ir"), "--output", str(self.root / "so"), "--target", "llvm",
                                "--benchmark-runs", "1"])
        self.assertTrue((self.root / "so" / "model.so").is_file())

    def test_keeps_the_conversion_when_every_shape_is_known(self):
        relu_model(self.root / "relu.onnx")
        metadata, log = self.build(self.root / "relu.onnx")

        self.assertNotIn("some outputs have no shape", log)
        self.assertFalse(metadata["simplify"])
        self.assertFalse(metadata["simplified_automatically"])

    def test_an_explicit_simplify_is_not_automatic(self):
        sliced_by_shape_model(self.root / "sliced.onnx")
        metadata, log = self.build(self.root / "sliced.onnx", "--simplify", "true")

        self.assertNotIn("some outputs have no shape", log)
        self.assertTrue(metadata["simplify"])
        self.assertFalse(metadata["simplified_automatically"])

    def test_keeps_the_first_conversion_when_onnxsim_fails(self):
        sliced_by_shape_model(self.root / "sliced.onnx")
        with mock.patch.object(build_onnx, "simplify_graph", side_effect=build_onnx.SimplifyError("onnxsim failed")):
            metadata, log = self.build(self.root / "sliced.onnx")

        self.assertIn("WARN: onnxsim failed; keeping the first conversion", log)
        self.assertIn("still have no shape", log)
        self.assertFalse(metadata["simplify"])
        self.assertEqual([0], self.ir_outputs_without_shape())


if __name__ == "__main__":
    unittest.main()
