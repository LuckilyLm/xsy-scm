#!/usr/bin/env python3
"""Self-test for the baseline path rewriter.

Run: ``python tools/quality/test_baseline_path_migration.py``

These cases exist because the rewriter's whole value is negative - it must not be
able to lose, invent, or prematurely apply recorded debt. "It looked right on the
dry run" is not evidence, so injectivity, occurrence preservation, the skip set
and the "did the file actually move" gate are each asserted directly, including on
the two misconfigurations that are easiest to make by hand: pointing two source
prefixes at one target, and running the whole-package mapping after migrating only
one domain.
"""

from __future__ import annotations

import contextlib
import tempfile
import unittest
from pathlib import Path

import migrate_baseline_paths as migrate
from migrate_baseline_paths import Report, migrate_file, migrate_line

OLD_MAIN = "xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/"
NEW_MAIN = "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/"
OLD_TEST = "xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/"
TEST_BASE_NEW = "xsy-scm-server/sa-admin/src/test/java/com/xsy/scm/"
MAPPINGS = ((OLD_MAIN, NEW_MAIN),)

# Records used by the transform tests are not real files, so the on-disk move check
# is switched off there and exercised separately against a temporary tree.
NO_CHECK = False


def record(occurrences: int, rule: str, path: str, locator: str) -> str:
    return f"{occurrences}\t{rule}\t{path}\t{locator}"


def migrate_lines(lines: list[str], mappings=MAPPINGS, verify_moves=NO_CHECK):
    with tempfile.TemporaryDirectory() as workspace:
        file = Path(workspace) / "generic-dependency-field.txt"
        file.write_text("\n".join(lines) + "\n", encoding="utf-8")
        report = Report()
        output = migrate_file(file, mappings, report, verify_moves)
        return output, report


class MigrateLineTest(unittest.TestCase):
    def test_only_the_path_field_changes(self) -> None:
        line = record(2, "magic-string-domain-literal", OLD_MAIN + "product/Foo.java", 'Foo#"ENABLED"')
        migrated, rewritten = migrate_line(line, MAPPINGS)
        self.assertTrue(rewritten)
        before, after = line.split("\t"), migrated.split("\t")
        self.assertEqual(after[0], before[0], "occurrence must not change")
        self.assertEqual(after[1], before[1], "rule must not change")
        self.assertEqual(after[3], before[3], "locator must not change")
        self.assertTrue(after[2].startswith(NEW_MAIN))
        self.assertEqual(after[2][len(NEW_MAIN):], before[2][len(OLD_MAIN):])

    def test_unrelated_path_is_left_alone(self) -> None:
        line = record(1, "stage-comment", "xsy-scm-server/sa-base/src/main/java/Foo.java", "Q13")
        migrated, rewritten = migrate_line(line, MAPPINGS)
        self.assertFalse(rewritten)
        self.assertEqual(migrated, line)

    def test_comments_pass_through_unchanged(self) -> None:
        for comment in ("# captured  : 3 occurrences", ""):
            _, rewritten = migrate_line(comment, MAPPINGS)
            self.assertFalse(rewritten)


class MigrateFileReportTest(unittest.TestCase):
    def test_counts_and_identity_number_are_preserved(self) -> None:
        lines = [
            "# header line",
            record(1, "generic-dependency-field", OLD_MAIN + "product/Foo.java", "Foo#dao:FooDao"),
            record(2, "magic-string-domain-literal", OLD_MAIN + "product/Foo.java", 'Foo#"ENABLED"'),
        ]
        output, report = migrate_lines(lines)
        self.assertTrue(report.ok, msg=str(report.collisions + report.invalid_lines))
        self.assertEqual((report.identities_before, report.identities_after), (2, 2))
        self.assertEqual((report.occurrences_before, report.occurrences_after), (3, 3))
        self.assertEqual((report.affected, report.unchanged), (2, 0))
        self.assertEqual(output[0], "# header line")
        self.assertEqual(
            output[1],
            record(1, "generic-dependency-field", NEW_MAIN + "product/Foo.java", "Foo#dao:FooDao"),
        )

    def test_mixture_of_rewritten_and_untouched_records_is_balanced(self) -> None:
        """The accounting must cover every record, not just the rewritten ones.

        A run that rewrites one domain while leaving nine other domains' records
        alone has to still report equal occurrence totals; counting only the
        touched records makes a perfectly correct migration look like data loss.
        """
        lines = [
            record(3, "stage-comment", OLD_MAIN + "common/A.java", "Q13"),
            record(2, "stage-comment", OLD_MAIN + "product/B.java", "Wave <n>"),
            record(1, "magic-string-domain-literal", OLD_MAIN + "order/C.java", 'C#"DRAFT"'),
            record(4, "legacy-scm-package", OLD_MAIN + "common/A.java", "unmigrated-file"),
        ]
        only_common = ((OLD_MAIN + "common/", NEW_MAIN + "common/"),)
        output, report = migrate_lines(lines, only_common)
        self.assertTrue(report.ok, msg=str(report.collisions + report.invalid_lines))
        self.assertEqual(report.occurrences_before, report.occurrences_after)
        self.assertEqual(report.affected, 1)
        self.assertEqual(report.unchanged, 2)
        self.assertEqual(report.skipped_family, 4)
        self.assertEqual(output[1], lines[1], "another domain's record must be untouched")
        self.assertEqual(output[3], lines[3], "the legacy ledger must be untouched")

    def test_legacy_package_ledger_is_never_rewritten(self) -> None:
        """``legacy-scm-package`` records files that are *still* in the old namespace.

        Migrating their paths would erase the anti-reflow ledger, so the family is
        skipped on purpose and its occurrences are reported as skipped instead.
        """
        line = record(1, "legacy-scm-package", OLD_MAIN + "common/ScmOperator.java", "unmigrated-file")
        output, report = migrate_lines([line])
        self.assertEqual(output[0], line, "the legacy ledger entry must survive byte-for-byte")
        self.assertEqual(report.affected, 0)
        self.assertEqual(report.skipped_family, 1)
        self.assertTrue(report.ok)

    def test_two_sources_collapsing_into_one_identity_fails(self) -> None:
        ambiguous = ((OLD_MAIN, "/tmp/target/"), (OLD_TEST, "/tmp/target/"))
        lines = [
            record(1, "stage-comment", OLD_MAIN + "a/B.java", "Q13"),
            record(1, "stage-comment", OLD_TEST + "a/B.java", "Q13"),
        ]
        _, report = migrate_lines(lines, ambiguous)
        self.assertFalse(report.ok)
        self.assertEqual(len(report.collapsed), 1)
        self.assertEqual((report.identities_before, report.identities_after), (2, 1))

    def test_target_already_present_in_baseline_fails(self) -> None:
        lines = [
            record(1, "stage-comment", NEW_MAIN + "a/B.java", "Q13"),
            record(1, "stage-comment", OLD_MAIN + "a/B.java", "Q13"),
        ]
        _, report = migrate_lines(lines)
        self.assertFalse(report.ok)
        self.assertEqual(len(report.collisions), 1)

    def test_malformed_record_is_reported_not_dropped(self) -> None:
        lines = [record(1, "stage-comment", OLD_MAIN + "a/B.java", "Q13"), "1\tstage-comment\tonly-three"]
        output, report = migrate_lines(lines)
        self.assertFalse(report.ok)
        self.assertEqual(len(report.invalid_lines), 1)
        self.assertEqual(len(output), 2, "the malformed line must still be emitted")


class MovedOnDiskTest(unittest.TestCase):
    """The guard that stops the ledger running ahead of an incomplete Q1 move."""

    def test_unmoved_file_is_reported(self) -> None:
        with temp_repo() as repo:
            write(repo, OLD_MAIN + "product/Foo.java", "class Foo {}\n")
            moved, reason = migrate.moved_on_disk(OLD_MAIN + "product/Foo.java",
                                                 NEW_MAIN + "product/Foo.java")
            self.assertFalse(moved)
            self.assertIn("still at the old path", reason)

    def test_half_moved_file_is_reported(self) -> None:
        """Target missing and source missing means the ledger points at nothing."""
        with temp_repo() as repo:
            moved, reason = migrate.moved_on_disk(OLD_MAIN + "product/Gone.java",
                                                 NEW_MAIN + "product/Gone.java")
            self.assertFalse(moved)
            self.assertIn("nothing at the new path", reason)

    def test_completed_move_passes(self) -> None:
        with temp_repo() as repo:
            write(repo, NEW_MAIN + "product/Foo.java", "class Foo {}\n")
            moved, reason = migrate.moved_on_disk(OLD_MAIN + "product/Foo.java",
                                                 NEW_MAIN + "product/Foo.java")
            self.assertTrue(moved, msg=reason)
            self.assertEqual(reason, "")

    def test_migration_refuses_when_the_domain_has_not_moved_yet(self) -> None:
        line = record(1, "generic-dependency-field", OLD_MAIN + "product/Foo.java", "Foo#dao:FooDao")
        with temp_repo() as repo:
            write(repo, OLD_MAIN + "product/Foo.java", "class Foo {}\n")
            with tempfile.TemporaryDirectory() as workspace:
                file = Path(workspace) / "generic-dependency-field.txt"
                file.write_text(line + "\n", encoding="utf-8")
                report = Report()
                migrate_file(file, MAPPINGS, report, verify_moves=True)
        self.assertFalse(report.ok)
        self.assertEqual(len(report.unmoved), 1)


class DomainMappingTest(unittest.TestCase):
    def test_domain_builds_main_and_test_prefixes_together(self) -> None:
        with temp_repo() as repo:
            write(repo, OLD_MAIN + "common/", "")
            write(repo, OLD_TEST + "common/", "")
            mappings = migrate.domain_mappings("common")
        self.assertEqual(len(mappings), 2)
        for from_prefix, to_prefix in mappings:
            self.assertTrue(from_prefix.endswith("/common/"))
            self.assertTrue(to_prefix.endswith("/com/xsy/scm/common/"))

    def test_domain_already_moved_is_accepted(self) -> None:
        """After the git mv the legacy directory is gone; that is when this runs."""
        with temp_repo() as repo:
            write(repo, NEW_MAIN + "common/", "")
            write(repo, TEST_BASE_NEW + "common/", "")
            mappings = migrate.domain_mappings("common")
        self.assertEqual(len(mappings), 2)

    def test_unknown_domain_is_rejected(self) -> None:
        with temp_repo():
            with self.assertRaises(SystemExit):
                migrate.domain_mappings("nom-such-domain")

    def test_skipped_families_are_declared(self) -> None:
        self.assertIn("legacy-scm-package", migrate.SKIPPED_FAMILIES)


class BaselineFileSelectionTest(unittest.TestCase):
    """Only rule baselines may be rewritten, never a neighbouring ledger.

    ``domain-counts.txt`` holds ``domain<TAB>corner<TAB>count`` records and lives in
    the same directory. A ``*.txt`` glob picks it up, parses it as a malformed
    four-field baseline record, and turns every migration run into ``INVALID`` /
    ``RESULT: FAIL`` - which reads as "the rewriter is broken" rather than "the glob
    is too wide".
    """

    def test_only_known_families_are_selected(self) -> None:
        with tempfile.TemporaryDirectory() as workspace:
            original = migrate.BASELINE_DIR
            migrate.BASELINE_DIR = Path(workspace)
            try:
                (migrate.BASELINE_DIR / "stage-comment.txt").write_text("", encoding="utf-8")
                (migrate.BASELINE_DIR / "domain-counts.txt").write_text(
                    "common\tlegacy_main\t27\n", encoding="utf-8")
                (migrate.BASELINE_DIR / "notes.md").write_text("", encoding="utf-8")
                selected = {path.name for path in migrate.baseline_files()}
            finally:
                migrate.BASELINE_DIR = original
        self.assertEqual(selected, {"stage-comment.txt"})

    def test_the_domain_count_ledger_is_not_parsed_as_a_baseline(self) -> None:
        """The three-field ledger must not raise the four-field record error."""
        with tempfile.TemporaryDirectory() as workspace:
            original = migrate.BASELINE_DIR
            migrate.BASELINE_DIR = Path(workspace)
            try:
                (migrate.BASELINE_DIR / "domain-counts.txt").write_text(
                    "common\tlegacy_main\t27\ncommon\tlegacy_test\t14\n", encoding="utf-8")
                report = migrate.run(migrate.whole_package_mappings(),
                                     apply_changes=False, verify_moves=False)
            finally:
                migrate.BASELINE_DIR = original
        self.assertEqual(report.invalid_lines, [])
        self.assertEqual(report.files, [])


@contextlib.contextmanager
def temp_repo():
    """Point the module at a scratch tree so path checks do not touch the real repo."""
    with tempfile.TemporaryDirectory() as workspace:
        root = Path(workspace)
        original = migrate.ROOT
        migrate.ROOT = root
        try:
            yield root
        finally:
            migrate.ROOT = original


def write(repo: Path, relative_path: str, content: str) -> Path:
    target = repo / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    if relative_path.endswith("/"):
        target.mkdir(parents=True, exist_ok=True)
    else:
        target.write_text(content, encoding="utf-8")
    return target


if __name__ == "__main__":
    unittest.main(verbosity=2)
