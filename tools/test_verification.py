"""Guard against false-green verification results."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import ts_baseline_ratchet as ratchet
from verify import Verification


class VerificationTest(unittest.TestCase):
    def test_compiler_crash_is_not_zero_errors(self):
        failed = subprocess.CompletedProcess([], 1, "", "Cannot find module 'vue-tsc'")
        with patch.object(ratchet.shutil, "which", return_value="node"), \
                patch.object(ratchet.subprocess, "run", return_value=failed):
            with self.assertRaises(RuntimeError):
                ratchet.run_typecheck()

    def test_global_configuration_error_is_blocking(self):
        with self.assertRaises(RuntimeError):
            ratchet.validate_output("error TS18003: No inputs were found in config file")

    def test_repeated_error_cannot_replace_a_fixed_error(self):
        first = "src/upstream.ts(1,1): error TS2339: missing field"
        other = "src/other.ts(1,1): error TS2339: another field"
        with tempfile.TemporaryDirectory() as directory:
            baseline = Path(directory) / "baseline.json"
            diagnostics = ratchet.parse_tsc_output(first + "\n" + other)
            baseline.write_text(json.dumps({"summary": {"total": 2},
                                          "diagnostics": [d.to_json() for d in diagnostics]}))
            with patch.object(ratchet, "BASELINE_PATH", baseline), \
                    patch.object(ratchet, "run_typecheck", return_value=(first + "\n" + first, 2)), \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, ratchet.cmd_check(SimpleNamespace(raw=None)))

    def test_capture_refuses_scm_errors_without_overwriting_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = Path(directory) / "baseline.json"
            baseline.write_text("original")
            error = "src/views/business/scm/order.vue(1,1): error TS2339: missing field"
            with patch.object(ratchet, "BASELINE_PATH", baseline), \
                    patch.object(ratchet, "run_typecheck", return_value=(error, 2)):
                with self.assertRaises(RuntimeError):
                    ratchet.cmd_capture(SimpleNamespace(raw=None, stamp=None))
            self.assertEqual("original", baseline.read_text())

    def test_summary_distinguishes_incomplete_from_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            verification = Verification.__new__(Verification)
            verification.logs = Path(directory)
            verification.failed = []
            verification.incomplete = ["cloud test skipped"]
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(2, verification.summary())
                verification.failed.append("compiler failed")
                self.assertEqual(1, verification.summary())
                verification.failed.clear()
                verification.incomplete.clear()
                self.assertEqual(0, verification.summary())

    def test_missing_command_is_a_failure(self):
        verification = Verification.__new__(Verification)
        verification.failed = []
        with patch("verify.shutil.which", return_value=None):
            self.assertFalse(verification.run("backend", ["mvn"], Path.cwd()))
        self.assertEqual(["backend: missing mvn"], verification.failed)


if __name__ == "__main__":
    unittest.main()
