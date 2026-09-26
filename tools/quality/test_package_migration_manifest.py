#!/usr/bin/env python3
"""Attack tests for the Q1 migration manifest and the domain completeness assertion.

Run: ``python tools/quality/test_package_migration_manifest.py``

The manifest exists to make "this domain is migrated" a claim a machine can check.
Every case below is an attempt to satisfy the *shape* of that claim without actually
moving the right files - which is exactly the class of mistake a count-based ledger
cannot see:

===========  ====================================================================
A            one file was never moved
B            ``A.java`` was forgotten and ``D.java`` added; the count is unchanged
C            the file was copied instead of moved (both packages hold it)
D            the new side has a file the manifest never listed
E            the migration is exact - the only case that may pass
F            the manifest is regenerated mid-migration with ``--force``
G            capture: 1 recorded defect fixed + 1 new defect, total unchanged
H            capture: 2 recorded defects fixed + 1 new defect, total falls
===========  ====================================================================

The trees are fabricated under a temporary root rather than moved for real, because
the property under test is the *comparison*, and a fabricated tree can express
"copied instead of moved" and "manifest never listed it" without risking the real
checkout. The assertion itself is exercised unmodified against those trees.
"""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import package_migration_readiness as readiness
import quality_guard as guard
from quality_guard import Finding

MAIN_REL = "sa-admin/src/main/java"
TEST_REL = "sa-admin/src/test/java"
LEGACY = "net/lab1024/sa/admin/module/scm"
NEW = "com/xsy/scm"


class ManifestTreeTest(unittest.TestCase):
    """A fabricated repo: a legacy package, a new package, and a manifest."""

    def setUp(self) -> None:
        self._temp = tempfile.TemporaryDirectory()
        self.root = Path(self._temp.name)
        for relative in (MAIN_REL, TEST_REL):
            (self.root / relative / LEGACY / "common").mkdir(parents=True)
            (self.root / relative / NEW / "common").mkdir(parents=True)
        self._saved = (readiness.ROOT, guard.MAIN_SOURCE_ROOT, guard.TEST_SOURCE_ROOT,
                       readiness.MANIFEST_PATH, guard.relative, guard.ROOT)
        readiness.ROOT = self.root
        guard.ROOT = self.root
        guard.MAIN_SOURCE_ROOT = self.root / MAIN_REL
        guard.TEST_SOURCE_ROOT = self.root / TEST_REL
        readiness.MANIFEST_PATH = self.root / "manifest.json"
        # ``guard.relative`` anchors on the real repo root; point it at the fabricated
        # tree so the assertion can be exercised unmodified.
        # ``check_manifest_preconditions`` also runs ``git status`` and ``git ls-files``
        # under its cwd, which is not a repo here.
        guard.relative = lambda path: path.resolve().relative_to(self.root).as_posix()
        self._subprocess_run = readiness.subprocess.run
        readiness.subprocess.run = self._fake_git

    def _fake_git(self, command, **kwargs):  # noqa: ANN001, ANN003
        """Stand in for git: clean tree, everything tracked."""
        import subprocess as _subprocess

        if command[:2] == ["git", "status"]:
            return _subprocess.CompletedProcess(command, 0, "", "")
        if command[:2] == ["git", "ls-files"]:
            return _subprocess.CompletedProcess(command, 0, "\n".join(command[4:]), "")
        return self._subprocess_run(command, **kwargs)

    def tearDown(self) -> None:
        readability = self._saved
        (readiness.ROOT, guard.MAIN_SOURCE_ROOT, guard.TEST_SOURCE_ROOT,
         readiness.MANIFEST_PATH, guard.relative, guard.ROOT) = readability
        readiness.subprocess.run = self._subprocess_run
        self._temp.cleanup()

    # ---------------------------------------------------------------- helpers

    def legacy(self, source_set: str, *names: str) -> None:
        for name in names:
            path = self.root / (MAIN_REL if source_set == "main" else TEST_REL) \
                / LEGACY / "common" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"// {name}\n", encoding="utf-8")

    def new(self, source_set: str, *names: str) -> None:
        for name in names:
            path = self.root / (MAIN_REL if source_set == "main" else TEST_REL) \
                / NEW / "common" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"// {name}\n", encoding="utf-8")

    def write_manifest(self, main: list[str], test: list[str]) -> None:
        readiness.MANIFEST_PATH.write_text(json.dumps({
            "generated_from_commit": "0" * 40,
            "generated_at": "2026-09-26T00:00:00Z",
            "totals": {"main": len(main), "test": len(test), "domains": 1},
            "domains": {"common": {"main": sorted(main), "test": sorted(test)}},
        }), encoding="utf-8")

    def assert_fails_with(self, expected: str) -> list[str]:
        failures = readiness.assert_domain_migrated("common")
        self.assertTrue(failures, "expected the assertion to fail")
        self.assertTrue(any(expected in failure for failure in failures),
                        f"no failure mentioned {expected!r}: {failures}")
        return failures

    # ------------------------------------------------------------------ cases

    def test_E_exact_migration_passes(self) -> None:
        """E: the only shape that may pass."""
        self.write_manifest(["A.java", "B.java"], ["T.java"])
        self.new("main", "A.java", "B.java")
        self.new("test", "T.java")
        self.assertEqual(readiness.assert_domain_migrated("common"), [])

    def test_A_one_file_never_moved_fails(self) -> None:
        """A: a file the manifest lists is still on the legacy side."""
        self.write_manifest(["A.java", "B.java", "C.java"], [])
        self.legacy("main", "C.java")
        self.new("main", "A.java", "B.java")
        self.assert_fails_with("still under the legacy package")

    def test_B_forgot_A_and_added_D_fails_on_the_count_equivalent_swap(self) -> None:
        """B: the exact failure a count cannot see - 3 files, wrong 3 files."""
        self.write_manifest(["A.java", "B.java", "C.java"], [])
        self.new("main", "B.java", "C.java", "D.java")

        failures = readiness.assert_domain_migrated("common")

        self.assertTrue(failures)
        self.assertTrue(any("not on the new side" in f for f in failures), failures)
        self.assertTrue(any("not in the manifest" in f for f in failures), failures)
        # Both sides still hold three files: only the *set* comparison catches this.
        self.assertEqual(len(list((guard.MAIN_SOURCE_ROOT / NEW / "common")
                                 .glob("*.java"))), 3)

    def test_C_file_present_under_both_packages_fails(self) -> None:
        """C: a copy rather than a move leaves compiled/scanned duplicates."""
        self.write_manifest(["A.java"], [])
        self.legacy("main", "A.java")
        self.new("main", "A.java")
        self.assert_fails_with("BOTH packages")

    def test_D_unexpected_file_on_the_new_side_fails(self) -> None:
        """D: Q1 is a namespace migration; it may not add SCM Java files."""
        self.write_manifest(["A.java", "B.java"], [])
        self.new("main", "A.java", "B.java", "D.java")
        self.assert_fails_with("not in the manifest")

    def test_F_manifest_is_refused_once_the_new_package_is_populated(self) -> None:
        """F: regenerating mid-migration would bless the half-moved tree."""
        self.new("main", "A.java")
        failures = readiness.check_manifest_preconditions()
        self.assertTrue(any("already holds" in failure for failure in failures), failures)

    def test_F_force_cannot_overwrite_a_committed_manifest(self) -> None:
        """F: --force is for an uncommitted manifest, never for the committed ledger."""
        self.write_manifest(["A.java"], [])
        self.new("main", "A.java")  # preconditions now fail
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                readiness.record_migration_manifest(force=True)

    def test_F_an_existing_manifest_is_never_regenerated_without_force(self) -> None:
        """F: re-recording would take the current tree as the new truth.

        Preconditions can still hold here (new package empty, tree clean), so the
        precondition check alone does not stop a re-record: a file quietly deleted
        from the ledger would simply become "correct". The existence of the manifest
        must be its own refusal.
        """
        self.write_manifest(["A.java", "B.java"], [])
        before = readiness.MANIFEST_PATH.read_bytes()
        self.legacy("main", "A.java")  # only one of the two is present on disk

        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                readiness.record_migration_manifest(force=False)

        self.assertEqual(readiness.MANIFEST_PATH.read_bytes(), before,
                         "a refused re-record must leave the manifest untouched")

    def test_manifest_is_discovered_from_the_tree_not_hard_coded(self) -> None:
        """The domain list must come from the filesystem, never a literal in code."""
        self.legacy("main", "A.java")
        self.legacy("test", "T.java")
        scanned = readiness.scan_domain_file_set(guard.MAIN_SOURCE_ROOT,
                                                 guard.LEGACY_SCM_PACKAGE_PATH)
        self.assertEqual(sorted(scanned), ["common"])
        source = Path(readiness.__file__).read_text(encoding="utf-8")
        for domain in ("inventory", "purchase", "warehouse"):
            self.assertNotIn(f'"{domain}"', source,
                             "a domain name is hard-coded; the list must be scanned")

    def test_root_level_files_are_their_own_bucket(self) -> None:
        """A class in the package root belongs to no domain and must not join one."""
        root = guard.TEST_SOURCE_ROOT / LEGACY
        (root / "ScmArchitectureTest.java").write_text("// root\n", encoding="utf-8")
        scanned = readiness.scan_domain_file_set(guard.TEST_SOURCE_ROOT,
                                                 guard.LEGACY_SCM_PACKAGE_PATH)
        self.assertEqual(scanned.get(readiness.ROOT_KEY), ["ScmArchitectureTest.java"])
        self.assertNotIn("common", scanned,
                         "a root-level file must not be folded into a domain")


class CaptureAttackTest(unittest.TestCase):
    """G and H: capture must never write a ledger that contains an unrecorded finding."""

    def setUp(self) -> None:
        # The temporary ledger must live *under* the repo root: ``capture`` ends by
        # printing ``relative(BASELINE_DIR)``, which cannot express a path outside it.
        self._real = guard.BASELINE_DIR
        self._temp = tempfile.TemporaryDirectory(dir=guard.ROOT)
        guard.BASELINE_DIR = Path(self._temp.name)

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real
        self._temp.cleanup()

    def run_capture(self, findings: list[Finding],
                    families: tuple[str, ...] = ("magic-string-domain-literal",)) -> int:
        fake = guard.Scan(findings, frozenset(families))
        original = guard.collect
        guard.collect = lambda checkstyle: fake
        try:
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                return guard.command_capture(
                    argparse.Namespace(checkstyle=None, verbose=False, limit=10,
                                       allow_growth=False))
        finally:
            guard.collect = original

    def rule(self, name: str) -> object:
        return next(family for family in guard.FAMILIES if family.name == name)

    def test_G_fix_one_add_one_fails_and_leaves_the_baseline_byte_identical(self) -> None:
        rule = "magic-string-domain-literal"
        guard.write_baseline(self.rule(rule),
                             [Finding(rule, "a/Foo.java", 'Foo#"ENABLED"', "old")])
        before = guard.baseline_path(rule).read_bytes()

        code = self.run_capture([Finding(rule, "b/Bar.java", 'Bar#"CONFIRMED"', "new")])

        self.assertEqual(code, 1)
        self.assertEqual(guard.baseline_path(rule).read_bytes(), before,
                         "capture rewrote the baseline despite refusing")

    def test_H_fix_two_add_one_fails_and_leaves_the_baseline_byte_identical(self) -> None:
        rule = "stage-comment"
        guard.write_baseline(self.rule(rule), [
            Finding(rule, "a/Foo.java", "Wave <n>", "old"),
            Finding(rule, "a/Bar.java", "本轮", "old"),
        ])
        before = guard.baseline_path(rule).read_bytes()

        code = self.run_capture([Finding(rule, "b/Baz.java", "下一阶段", "new")],
                                families=(rule,))

        self.assertEqual(code, 1)
        self.assertEqual(guard.baseline_path(rule).read_bytes(), before,
                         "a falling total must not authorise a new defect")

    def test_a_pure_shrink_is_still_written(self) -> None:
        """The counterpart to G/H: refusing everything would make capture useless."""
        rule = "stage-comment"
        guard.write_baseline(self.rule(rule), [
            Finding(rule, "a/Foo.java", "Wave <n>", "old"),
            Finding(rule, "a/Bar.java", "本轮", "old"),
        ])

        code = self.run_capture([Finding(rule, "a/Bar.java", "本轮", "kept")], families=(rule,))

        self.assertEqual(code, 0)
        allowed = guard.read_baseline(rule)
        self.assertEqual(sum(allowed.values()), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
