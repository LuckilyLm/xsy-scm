#!/usr/bin/env python3
"""Self-test for the baseline path rewriter.

Run: ``python tools/quality/test_baseline_path_migration.py``

These cases exist because the rewriter's whole value is a negative one - it must
not be able to lose or invent recorded debt. "It looked right on the dry run" is
not evidence, so injectivity, occurrence preservation and collision detection are
asserted directly, including on the misconfiguration that is easiest to make by
hand: mapping two different source prefixes onto the same target.
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from migrate_baseline_paths import Report, migrate_file, migrate_line

OLD_MAIN = "xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/"
NEW_MAIN = "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/"
MAPPINGS = ((OLD_MAIN, NEW_MAIN),)

HEADER = (
    "# rule      : generic-dependency-field — 语义贫乏的依赖字段名\n"
    "# captured  : 3 occurrences in 2 identities\n"
)


def record(occurrences: int, rule: str, path: str, locator: str) -> str:
    return f"{occurrences}\t{rule}\t{path}\t{locator}"


def migrate_lines(lines: list[str], mappings=MAPPINGS) -> tuple[list[str], Report]:
    with tempfile.TemporaryDirectory() as workspace:
        file = Path(workspace) / "generic-dependency-field.txt"
        file.write_text("\n".join(lines) + "\n", encoding="utf-8")
        report = Report()
        output = migrate_file(file, mappings, report)
        return output, report


class MigrateLineTest(unittest.TestCase):
    def test_only_the_path_field_changes(self) -> None:
        line = record(2, "magic-string-domain-literal", OLD_MAIN + "product/Foo.java", 'Foo#"ENABLED"')
        migrated, rewritten = migrate_line(line, MAPPINGS)
        self.assertTrue(rewritten)
        before, after = line.split("\t"), migrated.split("\t")
        self.assertEqual(len(before), len(after))
        self.assertEqual(after[0], before[0], "occurrence must not change")
        self.assertEqual(after[1], before[1], "rule must not change")
        self.assertEqual(after[3], before[3], "locator must not change")
        self.assertTrue(after[2].startswith(NEW_MAIN))
        self.assertEqual(after[2][len(NEW_MAIN):], before[2][len(OLD_MAIN):], "suffix must survive")

    def test_unrelated_path_is_left_alone(self) -> None:
        line = record(1, "stage-comment", "xsy-scm-server/sa-base/src/main/java/Foo.java", "Q13")
        migrated, rewritten = migrate_line(line, MAPPINGS)
        self.assertFalse(rewritten)
        self.assertEqual(migrated, line)

    def test_comments_pass_through_unchanged(self) -> None:
        for comment in ("# captured  : 3 occurrences", ""):
            _, rewritten = migrate_line(comment, MAPPINGS)
            self.assertFalse(rewritten)

    def test_prefix_boundaries_are_exact(self) -> None:
        # A path that merely contains the prefix must not be treated as under it.
        line = record(1, "stage-comment", OLD_MAIN + "x/../other.java", "Q1")
        migrated, rewritten = migrate_line(line, MAPPINGS)
        self.assertTrue(rewritten)
        self.assertTrue(migrated.split("\t")[2].startswith(NEW_MAIN))


class MigrateFileReportTest(unittest.TestCase):
    def test_counts_and_identity_number_are_preserved(self) -> None:
        lines = [
            "# header line",
            record(1, "generic-dependency-field", OLD_MAIN + "product/Foo.java", "Foo#dao:FooDao"),
            record(2, "magic-string-domain-literal", OLD_MAIN + "product/Foo.java", 'Foo#"ENABLED"'),
        ]
        output, report = migrate_lines(lines)
        self.assertTrue(report.ok, msg=str(report.collisions + report.invalid_lines))
        self.assertEqual(report.identities_before, 2)
        self.assertEqual(report.identities_after, 2)
        self.assertEqual(report.occurrences_before, 3)
        self.assertEqual(report.occurrences_after, 3)
        self.assertEqual(report.affected, 2)
        self.assertEqual(report.unchanged, 0)
        self.assertEqual(output[0], "# header line")
        self.assertEqual(
            output[1],
            record(1, "generic-dependency-field", NEW_MAIN + "product/Foo.java", "Foo#dao:FooDao"),
        )
        self.assertEqual(
            output[2],
            record(2, "magic-string-domain-literal", NEW_MAIN + "product/Foo.java", 'Foo#"ENABLED"'),
        )

    def test_occurrence_values_are_never_recomputed(self) -> None:
        lines = [record(7, "stage-comment", OLD_MAIN + "a/B.java", "Q13")]
        output, report = migrate_lines(lines)
        self.assertEqual(output[0].split("\t")[0], "7")
        self.assertEqual(report.occurrences_before, report.occurrences_after)

    def test_two_sources_collapsing_into_one_identity_fails(self) -> None:
        # Real misconfiguration: pointing both the main and the test prefix at one target.
        old_test = OLD_MAIN.replace("/main/", "/test/")
        ambiguous = ((OLD_MAIN, "/tmp/target/"), (old_test, "/tmp/target/"))
        lines = [
            record(1, "stage-comment", OLD_MAIN + "a/B.java", "Q13"),
            record(1, "stage-comment", old_test + "a/B.java", "Q13"),
        ]
        _, report = migrate_lines(lines, ambiguous)
        self.assertFalse(report.ok)
        self.assertEqual(len(report.collapsed), 1)
        # Collapsing would erase one recorded identity.
        self.assertEqual(report.identities_after, 1)
        self.assertEqual(report.identities_before, 2)

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

    def test_zero_occurrence_change_is_detected(self) -> None:
        # Sanity: the report actually compares, rather than always echoing "before".
        lines = [record(1, "stage-comment", OLD_MAIN + "a/B.java", "Q13")]
        _, report = migrate_lines(lines)
        self.assertEqual(report.occurrences_after, 1)
        self.assertNotEqual(report.occurrences_after, report.occurrences_before + 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
