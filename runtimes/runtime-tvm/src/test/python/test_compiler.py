import contextlib
import io
import json
import os
import runpy
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

import tvm
from tvm.script import ir as I
from tvm.script import relax as R


COMPILER_PATH = (
    Path(__file__).parents[2]
    / "main/resources/runtime-tvm/docker/compiler.py"
)
COMPILER = runpy.run_path(str(COMPILER_PATH), run_name="compiler_under_test")
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


class CompilerLifecycleTest(unittest.TestCase):
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

        publisher = types.ModuleType("_dh_publish")
        publisher.publish_model_and_register_output = lambda **_kwargs: None
        self.publisher_patch = mock.patch.dict(sys.modules, {"_dh_publish": publisher})
        self.publisher_patch.start()

    def tearDown(self):
        self.publisher_patch.stop()
        self.temp_dir.cleanup()

    def run_compiler(self, output: Path, *arguments: str):
        argv = [
            str(COMPILER_PATH),
            "--ir-dir",
            str(self.ir_dir),
            "--output",
            str(output),
            "--target",
            '{"kind":"llvm","num-cores":1}',
            "--exec-mode",
            "compiled",
            "--benchmark-runs",
            "0",
            *arguments,
        ]
        with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
            COMPILER["main"]()
        self.assertTrue((output / "model.so").is_file())
        return json.loads((output / "metadata.json").read_text())

    def test_off_tune_resume_apply_lifecycle(self):
        off = self.run_compiler(self.root / "off")
        self.assertNotIn("meta_schedule", off)
        self.assertNotIn("benchmark", off)
        if "TVM_GIT_COMMIT" in os.environ:
            self.assertEqual(os.environ["TVM_GIT_COMMIT"], off["tvm_git_commit"])

        tuning_arguments = (
            "--tuning-mode",
            "tune",
            "--tuning-trials",
            "1",
            "--max-trials-per-task",
            "1",
            "--tuning-workers",
            "1",
            "--tuning-number",
            "1",
            "--tuning-repeat",
            "1",
            "--tuning-min-repeat-ms",
            "0",
            "--tuning-alloc-repeat",
            "2",
        )
        tuned_dir = self.root / "tuned"
        tuned = self.run_compiler(tuned_dir, *tuning_arguments)
        self.assertEqual("tune", tuned["meta_schedule"]["mode"])
        self.assertEqual(2, tuned["meta_schedule"]["alloc_repeat"])
        self.assertEqual(64, tuned["meta_schedule"]["trials_per_iter"])
        # LLVM targets are tuned with weight prepacking, and the database records it.
        self.assertTrue(tuned["meta_schedule"]["weight_prepack"])
        manifest = json.loads((tuned_dir / "tuning" / "manifest.json").read_text())
        self.assertTrue(manifest["cpu_weight_prepack"])
        self.assertEqual(1, tuned["meta_schedule"]["covered_function_count"])
        self.assertEqual([], tuned["meta_schedule"]["missing_functions"])

        resumed_dir = self.root / "resumed"
        resumed = self.run_compiler(
            resumed_dir,
            *tuning_arguments,
            "--tuning-database",
            str(tuned_dir),
        )
        self.assertTrue(resumed["meta_schedule"]["database_reused"])
        self.assertTrue(resumed["meta_schedule"]["weight_prepack"])

        applied = self.run_compiler(
            self.root / "applied",
            "--tuning-mode",
            "apply",
            "--tuning-database",
            str(resumed_dir),
        )
        self.assertEqual("apply", applied["meta_schedule"]["mode"])
        self.assertEqual(1, applied["meta_schedule"]["covered_function_count"])

    def test_records_a_benchmark_of_the_library(self):
        compiled = self.run_compiler(self.root / "bench", "--benchmark-runs", "3")
        benchmark = compiled["benchmark"]
        self.assertEqual(3, benchmark["runs"])
        self.assertGreater(benchmark["mean_ms"], 0)
        self.assertLessEqual(benchmark["min_ms"], benchmark["p90_ms"])

    def test_skips_the_benchmark_of_cross_compiled_libraries(self):
        args = types.SimpleNamespace(benchmark_runs=5, cross_cc="aarch64-linux-gnu-g++", system_lib=False)
        result = COMPILER["benchmark_library"](self.root / "model.so", {"inputs": TINY_INPUTS}, args)
        self.assertIn("cross-compiled", result["skipped"])

    def test_first_round_budget_uses_the_smaller_batch(self):
        budget = COMPILER["first_round_budget"]
        self.assertEqual(65 * 64, budget(65, 256, 64))
        self.assertEqual(65 * 16, budget(65, 16, 64))

    def test_databases_without_the_prepack_flag_are_applied_without_it(self):
        database = self.root / "legacy" / "tuning"
        database.mkdir(parents=True)
        (database / "database_workload.json").write_text("")
        (database / "database_tuning_record.json").write_text("")
        identity = {"tvm_version": tvm.__version__, "target": "llvm"}
        (database / "manifest.json").write_text(json.dumps(identity))

        opened = COMPILER["open_tuning_database"](self.root / "out", str(database), identity, True)

        self.assertTrue(opened.reused)
        self.assertFalse(opened.weight_prepack)

    def test_rejects_incompatible_database(self):
        database = self.root / "database" / "tuning"
        database.mkdir(parents=True)
        (database / "database_workload.json").write_text("")
        (database / "database_tuning_record.json").write_text("")
        (database / "manifest.json").write_text(
            json.dumps({"tvm_version": tvm.__version__, "target": "cuda"})
        )
        with self.assertRaisesRegex(ValueError, "incompatible MetaSchedule database"):
            self.run_compiler(
                self.root / "invalid",
                "--tuning-mode",
                "apply",
                "--tuning-database",
                str(database),
            )

    def test_rejects_database_from_different_tvm_revision(self):
        identity = {"tvm_version": tvm.__version__, "target": "llvm"}
        with self.assertRaisesRegex(ValueError, "tvm_git_commit"):
            COMPILER["_validate_database_identity"](
                {**identity, "tvm_git_commit": "database-revision"},
                {**identity, "tvm_git_commit": "compiler-revision"},
                "manifest",
            )

    def test_rejects_incomplete_rpc_configuration(self):
        with self.assertRaises(SystemExit):
            self.run_compiler(
                self.root / "rpc",
                "--tuning-mode",
                "tune",
                "--tuning-trials",
                "1",
                "--max-trials-per-task",
                "1",
                "--tuning-runner",
                "rpc",
            )

    def test_rejects_partial_budget_by_default(self):
        with self.assertRaises(SystemExit):
            self.run_compiler(
                self.root / "partial",
                "--tuning-mode",
                "tune",
                "--tuning-trials",
                "1",
                "--max-trials-per-task",
                "2",
            )


if __name__ == "__main__":
    unittest.main()
