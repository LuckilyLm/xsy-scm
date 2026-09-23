"""Guard against false-green verification results."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import migration_checksum_guard as guard
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


class MigrationChecksumGuardTest(unittest.TestCase):
    def assert_quiet(self, func, *args):
        with contextlib.redirect_stdout(io.StringIO()):
            return func(*args)

    def temp_tree(self, directory):
        """把真实迁移目录与快照复制进临时目录，返回 (dir, snapshot, args)。"""
        root = Path(directory)
        migrations = root / "migration"
        shutil.copytree(guard.MIGRATION_DIR, migrations)
        args = SimpleNamespace(migration_dir=str(migrations),
                               snapshot=str(root / "snapshot.json"), force=False, prune=False)
        self.assertEqual(0, self.assert_quiet(guard.cmd_sync, args))
        return migrations, Path(args.snapshot), args

    def test_repository_migrations_match_the_committed_snapshot(self):
        args = SimpleNamespace(migration_dir=str(guard.MIGRATION_DIR),
                               snapshot=str(guard.SNAPSHOT_PATH), force=False, prune=False)
        self.assertEqual(0, self.assert_quiet(guard.cmd_check, args))

    def test_checksum_is_line_ending_agnostic_and_signed(self):
        lf = guard.flyway_checksum("SELECT 1;\nSELECT 2;\n".encode())
        crlf = guard.flyway_checksum("SELECT 1;\r\nSELECT 2;\r\n".encode())
        self.assertEqual(lf, crlf)
        self.assertNotEqual(lf, guard.flyway_checksum("SELECT 1; SELECT 2;\n".encode()))
        self.assertLess(lf, 1 << 31)

    def test_whitespace_reflow_of_an_applied_migration_fails_the_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            migrations, _, args = self.temp_tree(directory)
            target = migrations / "V19__scm_inventory.sql"
            lines = target.read_text(encoding="utf-8").splitlines()
            target.write_text("\n".join(lines[:1] + [" ".join(lines[1:3])] + lines[3:]) + "\n",
                              encoding="utf-8")
            # 只合并了一行缩进，SQL 语义不变；校验和变了就足以让真实库上的 validate 失败。
            self.assertEqual(1, self.assert_quiet(guard.cmd_check, args))

    def test_sync_refuses_to_bless_a_changed_migration(self):
        with tempfile.TemporaryDirectory() as directory:
            migrations, snapshot, args = self.temp_tree(directory)
            baked = snapshot.read_text(encoding="utf-8")
            target = migrations / "V21__scm_inventory_movement_append_only.sql"
            target.write_text(target.read_text(encoding="utf-8").replace("  ", "    "),
                              encoding="utf-8")
            self.assertEqual(1, self.assert_quiet(guard.cmd_check, args))
            self.assertEqual(1, self.assert_quiet(guard.cmd_sync, args))
            self.assertEqual(baked, snapshot.read_text(encoding="utf-8"))
            args.force = True
            self.assertEqual(0, self.assert_quiet(guard.cmd_sync, args))
            self.assertEqual(0, self.assert_quiet(guard.cmd_check, args))

    def test_renumbering_an_applied_migration_fails_the_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            migrations, _, args = self.temp_tree(directory)
            # 字节没动，只把已应用的 V43 改到一个空闲号：真实库上的历史行会悬空。
            # 目标号必须现算——写死 V44 之类会与后来落地的真实迁移撞号，
            # 届时守卫先抛「版本号重复」，本用例想验的改号路径根本走不到。
            taken = guard.scan_migrations(migrations)
            free = max(guard.version_key(version)[0] for version in taken) + 1
            (migrations / "V43__scm_delivery_permissions.sql").rename(
                migrations / f"V{free}__scm_delivery_permissions.sql")
            self.assertEqual(1, self.assert_quiet(guard.cmd_check, args))

    def test_duplicate_version_number_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            migrations, _, args = self.temp_tree(directory)
            # Flyway 在解析阶段就报 "Found more than one migration with version"，任何库都起不来
            shutil.copy(migrations / "V43__scm_delivery_permissions.sql",
                        migrations / "V43__scm_delivery_permissions_copy.sql")
            with self.assertRaises(ValueError):
                guard.cmd_check(args)


if __name__ == "__main__":
    unittest.main()
