# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Tests of publish.py with the digitalhub SDK of the tvm-toolkit image: every Model gets
the kind of what it contains, whatever SDK release is installed."""

import contextlib
import io
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS = Path(__file__).parents[2] / "main/resources/runtime-tvm/scripts"
sys.path.insert(0, str(SCRIPTS))

with mock.patch.dict(os.environ, {"RUN_ID": "run-1"}):
    import publish  # noqa: E402

from digitalhub.factory.registry import registry  # noqa: E402

METADATA = {"entry": "main", "inputs": [{"name": "x", "dtype": "float32", "shape": [1, 4]}], "outputs": [],
            "source_format": "onnx", "target": "llvm", "opt_level": 3}


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.logged = []
        self.outputs = []
        model = mock.Mock(key="store://p/model/kind/name:1")
        patches = [
            mock.patch.dict(os.environ, {"PROJECT_NAME": "p", "DHCORE_ENDPOINT": "http://core"}),
            mock.patch("digitalhub.entities.model._base.crud.log_base_model",
                       side_effect=lambda **kwargs: self.logged.append(kwargs) or model),
            mock.patch.object(publish, "set_run_output", side_effect=lambda *args: self.outputs.append(args)),
        ]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def publish(self, function, *args):
        with contextlib.redirect_stdout(io.StringIO()):
            return function(*args)

    def test_the_run_id_is_kept_away_from_the_sdk(self):
        self.assertEqual("run-1", publish.RUN_ID)

    def test_the_ir_is_published_as_tvm_ir(self):
        self.publish(publish.publish_ir_model, Path("/tmp/ir"), "yolo", METADATA, {"opset": 17})
        self.assertEqual("tvm-ir", self.logged[-1]["kind"])
        self.assertEqual("yolo-ir", self.logged[-1]["name"])
        self.assertEqual("onnx", self.logged[-1]["source_format"])
        self.assertEqual(("p", "run-1", "ir_module", "store://p/model/kind/name:1"), self.outputs[-1])

    def test_the_library_is_published_as_tvm_so(self):
        with mock.patch.object(publish, "link_consumed_model") as link:
            self.publish(publish.publish_compiled_model, Path("/tmp/so"), "yolo-x86", METADATA, "store://p/model/tvm-ir/yolo-ir")
        self.assertEqual("tvm-so", self.logged[-1]["kind"])
        self.assertEqual("llvm", self.logged[-1]["target"])
        self.assertEqual(METADATA, self.logged[-1]["manifest"])
        self.assertEqual("compiled_so", self.outputs[-1][2])
        link.assert_called_once()

    def test_both_kinds_can_be_built_by_the_installed_sdk(self):
        for kind in publish.MODEL_KINDS:
            with contextlib.redirect_stdout(io.StringIO()):
                publish.enable_model_kind(kind)
            builder = registry.get_entity_builder(kind)
            self.assertEqual(kind, builder.ENTITY_KIND)
            spec = builder.build_spec(path="s3://bucket/model/", entry="main", inputs=[], outputs=[])
            self.assertEqual("main", spec.entry)

    def test_entity_names_are_sanitized(self):
        self.assertEqual("yolo-v8-arm64", publish.entity_name("YOLO v8_arm64".replace("_", "-")))
        self.assertEqual("tvm-model", publish.entity_name(""))


if __name__ == "__main__":
    unittest.main()
