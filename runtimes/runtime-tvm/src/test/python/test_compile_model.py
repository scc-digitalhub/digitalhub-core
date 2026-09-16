# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Tests of compile_model.py, tuning.py and benchmark.py. Run them in the tvm-toolkit image:

    python3 -m unittest discover -s src/test/python -v
"""

import contextlib
import io
import json
import os
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS = Path(__file__).parents[2] / "main/resources/runtime-tvm/scripts"
sys.path.insert(0, str(SCRIPTS))

import tvm  # noqa: E402
from tvm.script import ir as I  # noqa: E402
from tvm.script import relax as R  # noqa: E402

import benchmark  # noqa: E402
import compile_model  # noqa: E402
import tuning  # noqa: E402

TINY_MODULE = """
@I.ir_module
class Tiny:
    @R.function
    def main(
        x: R.Tensor((1, 8), "float32"),
        weight: R.Tensor((8, 8), "float32"),
    ) -> R.Tensor((1, 8), "float32"):
        with R.dataflow():
            output = R.matmul(x, weight)
            R.output(output)
        return output
"""
TINY_INPUTS = [
    {"name": "x", "shape": [1, 8], "dtype": "float32"},
    {"name": "weight", "shape": [8, 8], "dtype": "float32"},
]
TUNE = (
    "--tuning-mode", "tune", "--tuning-trials", "1", "--max-trials-per-task", "1", "--tuning-workers", "1",
    "--tuning-number", "1", "--tuning-repeat", "1", "--tuning-min-repeat-ms", "0", "--tuning-alloc-repeat", "2",
)


class CompileModelTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.ir_dir = self.root / "ir"
        self.ir_dir.mkdir()
        module = tvm.script.from_source(TINY_MODULE, {"I": I, "R": R})
        (self.ir_dir / "model.relax.json").write_text(tvm.ir.save_json(module))
        (self.ir_dir / "metadata.json").write_text(
            json.dumps({"entry": "main", "source_sha256": "fixture-source", "inputs": TINY_INPUTS})
        )

        self.published = []
        publisher = types.ModuleType("publish")
        publisher.publish_compiled_model = lambda *args: self.published.append(args)
        self.modules = mock.patch.dict(sys.modules, {"publish": publisher})
        self.modules.start()

    def tearDown(self):
        self.modules.stop()
        self.temp_dir.cleanup()

    def compile(self, output: Path, *arguments: str) -> dict:
        argv = ["--ir-dir", str(self.ir_dir), "--output", str(output), "--target", '{"kind":"llvm","num-cores":1}',
                "--exec-mode", "compiled", "--benchmark-runs", "0", *arguments]
        with contextlib.redirect_stdout(io.StringIO()):
            compile_model.main(argv)
        self.assertTrue((output / "model.so").is_file())
        return json.loads((output / "metadata.json").read_text())

    def test_off_tune_resume_apply_lifecycle(self):
        off = self.compile(self.root / "off")
        self.assertNotIn("meta_schedule", off)
        self.assertNotIn("benchmark", off)
        if "TVM_GIT_COMMIT" in os.environ:
            self.assertEqual(os.environ["TVM_GIT_COMMIT"], off["tvm_git_commit"])

        tuned_dir = self.root / "tuned"
        tuned = self.compile(tuned_dir, *TUNE)
        self.assertEqual("tune", tuned["meta_schedule"]["mode"])
        self.assertEqual(2, tuned["meta_schedule"]["alloc_repeat"])
        self.assertEqual(64, tuned["meta_schedule"]["trials_per_iter"])
        # LLVM targets are tuned with weight prepacking, and the database records it.
        self.assertTrue(tuned["meta_schedule"]["weight_prepack"])
        self.assertTrue(json.loads((tuned_dir / "tuning" / "manifest.json").read_text())["cpu_weight_prepack"])
        self.assertEqual(1, tuned["meta_schedule"]["covered_function_count"])
        self.assertEqual([], tuned["meta_schedule"]["missing_functions"])

        resumed_dir = self.root / "resumed"
        resumed = self.compile(resumed_dir, *TUNE, "--tuning-database", str(tuned_dir))
        self.assertTrue(resumed["meta_schedule"]["database_reused"])
        self.assertTrue(resumed["meta_schedule"]["weight_prepack"])

        applied = self.compile(self.root / "applied", "--tuning-mode", "apply", "--tuning-database", str(resumed_dir))
        self.assertEqual("apply", applied["meta_schedule"]["mode"])
        self.assertEqual(1, applied["meta_schedule"]["covered_function_count"])

    def test_publishes_the_compiled_model_named_after_the_tag(self):
        self.compile(self.root / "tagged", "--name", "yolo", "--tag", "arm64", "--source-ir-key", "store://p/model/tvm-ir/yolo-ir")
        out_dir, name, metadata, source_ir_key = self.published[-1]
        self.assertEqual("yolo-arm64", name)
        self.assertEqual("arm64", metadata["tag"])
        self.assertEqual("store://p/model/tvm-ir/yolo-ir", source_ir_key)

    def test_options_come_from_the_job_environment(self):
        env = {"TVM_OPT_LEVEL": "2", "TVM_EXEC_MODE": "compiled", "TVM_TUNING_OPS": "matmul, conv2d",
               "TVM_ALLOW_PARTIAL_TUNING": "true", "TVM_TARGET": "llvm"}
        with mock.patch.dict(os.environ, env):
            args = compile_model.parse_args([])
            overridden = compile_model.parse_args(["--opt-level", "1"])
        self.assertEqual(2, args.opt_level)
        self.assertEqual("compiled", args.exec_mode)
        self.assertEqual(["matmul", "conv2d"], args.tuning_ops)
        self.assertTrue(args.allow_partial_tuning)
        self.assertEqual(1, overridden.opt_level)

    def test_rejects_an_unknown_mode_from_the_environment(self):
        with mock.patch.dict(os.environ, {"TVM_TUNING_MODE": "fast"}), contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                compile_model.parse_args([])

    def test_records_a_benchmark_of_the_library(self):
        compiled = self.compile(self.root / "bench", "--benchmark-runs", "3")
        timings = compiled["benchmark"]
        self.assertEqual(3, timings["runs"])
        self.assertGreater(timings["mean_ms"], 0)
        self.assertLessEqual(timings["min_ms"], timings["p90_ms"])

    def test_skips_the_benchmark_of_cross_compiled_libraries(self):
        result = benchmark.benchmark_library(self.root / "model.so", {"inputs": TINY_INPUTS}, 5, "aarch64-linux-gnu-g++", False)
        self.assertIn("cross-compiled", result["skipped"])

    def test_first_round_budget_uses_the_smaller_batch(self):
        self.assertEqual(65 * 64, tuning.first_round_budget(65, 256, 64))
        self.assertEqual(65 * 16, tuning.first_round_budget(65, 16, 64))

    def test_databases_without_the_prepack_flag_are_applied_without_it(self):
        database = self.root / "legacy" / "tuning"
        database.mkdir(parents=True)
        (database / tuning.WORKLOAD_FILE).write_text("")
        (database / tuning.RECORD_FILE).write_text("")
        identity = {"tvm_version": tvm.__version__, "target": "llvm"}
        (database / tuning.MANIFEST_FILE).write_text(json.dumps(identity))

        with contextlib.redirect_stdout(io.StringIO()):
            opened = tuning.open_database(self.root / "out", str(database), identity, True)

        self.assertTrue(opened.reused)
        self.assertFalse(opened.weight_prepack)

    def test_rejects_incompatible_database(self):
        database = self.root / "database" / "tuning"
        database.mkdir(parents=True)
        (database / tuning.WORKLOAD_FILE).write_text("")
        (database / tuning.RECORD_FILE).write_text("")
        (database / tuning.MANIFEST_FILE).write_text(json.dumps({"tvm_version": tvm.__version__, "target": "cuda"}))
        with self.assertRaisesRegex(ValueError, "incompatible MetaSchedule database"):
            self.compile(self.root / "invalid", "--tuning-mode", "apply", "--tuning-database", str(database))

    def test_rejects_database_from_different_tvm_revision(self):
        identity = {"tvm_version": tvm.__version__, "target": "llvm"}
        with self.assertRaisesRegex(ValueError, "tvm_git_commit"):
            tuning.check_identity({**identity, "tvm_git_commit": "database"}, {**identity, "tvm_git_commit": "compiler"},
                                  "manifest")

    def test_rejects_incomplete_rpc_configuration(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self.compile(self.root / "rpc", "--tuning-mode", "tune", "--tuning-trials", "1", "--max-trials-per-task", "1",
                         "--tuning-runner", "rpc")

    def test_rejects_partial_budget_by_default(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self.compile(self.root / "partial", "--tuning-mode", "tune", "--tuning-trials", "1", "--max-trials-per-task", "2")


if __name__ == "__main__":
    unittest.main()
