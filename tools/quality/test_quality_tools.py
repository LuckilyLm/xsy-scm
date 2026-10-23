#!/usr/bin/env python3
"""SCM 质量门禁自测合集。

这些用例由 `verify.py quality` 逐个分组执行（`python test_quality_tools.py <TestCase...>`），
合并只为了减少文件数；每个 TestCase 仍只覆盖一个门禁组件，失败标签由 verify.py 指定。
直接运行全部用例：

    python tools/quality/test_quality_tools.py
"""
from __future__ import annotations

import argparse
import contextlib
import io
import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

import migrate_baseline_paths as migrate
import package_migration_readiness as readiness
import quality_guard as guard
from java_source import JavaSource
from migrate_baseline_paths import Report, migrate_file, migrate_line
from quality_guard import Finding
import scm_common_domain_source_guard as common_source_guard
import scm_cross_domain_dao_guard as cross_domain_guard



# ===========================================================================
# test_scm_cross_domain_dao_guard.py
# ===========================================================================
# Isolated fixtures for the cross-domain DAO read-only guard.






class CrossDomainDaoGuardFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source_path = self.root / "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/finance/service/FinanceReader.java"
        self.mapper_path = self.root / "xsy-scm-server/sa-admin/src/main/resources/mapper/product/ProductReadDao.xml"
        self.allowlist_path = self.root / "tools/quality/cross-domain-dao-allowlist.tsv"
        self.source_path.parent.mkdir(parents=True)
        self.mapper_path.parent.mkdir(parents=True)
        self.allowlist_path.parent.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_caller(self, calls: str = "productReadDao.findAllowed();") -> str:
        self.source_path.write_text(
            "package com.xsy.scm.finance.service;\n"
            "import com.xsy.scm.product.dao.ProductReadDao;\n"
            "class FinanceReader {\n"
            "  private final ProductReadDao productReadDao;\n"
            f"  void read() {{ {calls} }}\n"
            "}\n",
            encoding="utf-8",
        )
        return self.source_path.relative_to(self.root).as_posix()

    def write_allowlist(self, source: str, methods: str = "findAllowed") -> None:
        self.allowlist_path.write_text(
            f"{source}\tcom.xsy.scm.product.dao.ProductReadDao\t{methods}\tfixture read contract\n",
            encoding="utf-8",
        )

    def write_mapper(self, statement: str, operation: str = "select") -> None:
        self.mapper_path.write_text(
            '<mapper namespace="com.xsy.scm.product.dao.ProductReadDao">\n'
            f'  <{operation} id="{statement}">SELECT 1</{operation}>\n'
            "</mapper>\n",
            encoding="utf-8",
        )

    def test_allowlisted_method_mapped_to_select_passes(self) -> None:
        source = self.write_caller()
        self.write_allowlist(source)
        self.write_mapper("findAllowed")

        self.assertEqual(cross_domain_guard.collect_failures(self.root, self.allowlist_path), [])

    def test_allowlisted_method_mapped_to_update_fails(self) -> None:
        source = self.write_caller()
        self.write_allowlist(source)
        self.write_mapper("findAllowed", "update")

        failures = cross_domain_guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("maps to <update>" in failure for failure in failures))

    def test_allowlisted_method_mapped_to_delete_fails(self) -> None:
        source = self.write_caller()
        self.write_allowlist(source)
        self.write_mapper("findAllowed", "delete")

        failures = cross_domain_guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("maps to <delete>" in failure for failure in failures))

    def test_custom_method_without_mapper_statement_fails(self) -> None:
        source = self.write_caller("productReadDao.findAllowed(); productReadDao.fetchCustom();")
        self.write_allowlist(source, "findAllowed,fetchCustom")
        self.write_mapper("findAllowed")

        failures = cross_domain_guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("cannot prove SQL operation for fetchCustom" in failure for failure in failures))

    def test_mybatis_plus_select_and_exists_methods_need_no_xml(self) -> None:
        source = self.write_caller("productReadDao.selectById(1L); productReadDao.existsById(1L);")
        self.write_allowlist(source, "selectById,existsById")

        self.assertEqual(cross_domain_guard.collect_failures(self.root, self.allowlist_path), [])

    def test_stale_allowlist_entry_fails(self) -> None:
        self.source_path.write_text(
            "package com.xsy.scm.finance.service; class FinanceReader {}\n", encoding="utf-8")
        source = self.source_path.relative_to(self.root).as_posix()
        self.write_allowlist(source)

        failures = cross_domain_guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("stale allowlist entry" in failure for failure in failures))

    def test_unregistered_cross_domain_dao_fails(self) -> None:
        self.write_caller()
        self.allowlist_path.write_text("", encoding="utf-8")

        failures = cross_domain_guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("unlisted finance -> com.xsy.scm.product.dao.ProductReadDao" in failure
                            for failure in failures))

# ===========================================================================
# test_scm_common_domain_source_guard.py
# ===========================================================================
# Source-level dependency boundary tests for SCM common.






class CommonDomainSourceGuardTest(unittest.TestCase):
    def test_regular_import_is_rejected(self) -> None:
        findings = common_source_guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "import com.xsy.scm.product.dao.ProductDao;\n"
            "class ScopeHelper {}\n"
        )
        self.assertTrue(any("forbidden import to com.xsy.scm.product" in finding for finding in findings))

    def test_static_import_is_rejected(self) -> None:
        findings = common_source_guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "import static com.xsy.scm.report.constant.ReportPermission.COST_QUERY;\n"
            "class ScopeHelper {}\n"
        )
        self.assertTrue(any("forbidden import to com.xsy.scm.report" in finding for finding in findings))

    def test_code_fully_qualified_reference_is_rejected(self) -> None:
        findings = common_source_guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "class ScopeHelper { Object value = com.xsy.scm.finance.constant.FinancePermission.PAYMENT_QUERY; }\n"
        )
        self.assertTrue(any("fully-qualified reference to com.xsy.scm.finance" in finding
                            for finding in findings))

    def test_comments_and_string_literals_do_not_count(self) -> None:
        findings = common_source_guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "// import com.xsy.scm.customer.dao.CustomerDao;\n"
            "/* com.xsy.scm.inventory.dao.InventoryDao */\n"
            'class ScopeHelper { String example = "com.xsy.scm.delivery.dao.DeliveryDao"; }\n'
        )
        self.assertEqual(findings, [])

    def test_common_and_jdk_references_remain_allowed(self) -> None:
        findings = common_source_guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "import java.util.List;\n"
            "import com.xsy.scm.common.error.ScmCommonErrorCode;\n"
            "class ScopeHelper { List<Number> values; }\n"
        )
        self.assertEqual(findings, [])

    def test_tree_scan_uses_only_temporary_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            common_root = Path(temporary) / "common"
            common_root.mkdir()
            (common_root / "ScopeHelper.java").write_text(
                "package com.xsy.scm.common.scope;\n"
                "import com.xsy.scm.warehouse.dao.WarehouseDao;\n"
                "class ScopeHelper {}\n",
                encoding="utf-8",
            )

            findings = common_source_guard.scan_tree(common_root)

        self.assertTrue(any("forbidden import to com.xsy.scm.warehouse" in finding for finding in findings))

# ===========================================================================
# test_quality_guard_source_rules.py
# ===========================================================================
# Contract tests for source segmentation and process-comment detection.






class StageCommentScannerTest(unittest.TestCase):
    def scan(self, text: str) -> list[guard.Finding]:
        source = JavaSource(Path("Fixture.java"), "Fixture.java", text)
        return guard.stage_comments(source)

    def test_plan_labels_and_bare_tokens_are_detected(self) -> None:
        findings = self.scan(
            "// P0 基线收口裁决\n"
            "/* P3 Finance */\n"
            "// P2\n"
        )

        self.assertEqual([finding.locator for finding in findings], ["P<n>", "P<n>", "P<n>"])

    def test_business_identifiers_and_normal_prose_are_not_misclassified(self) -> None:
        findings = self.scan(
            "// SKU-P3 is a product code.\n"
            "// 商品等级 P2 用于标注采购规格。\n"
            "// Priority P1 is a customer service tier.\n"
        )

        self.assertEqual(findings, [])

    def test_strings_and_code_are_not_scanned_as_comments(self) -> None:
        findings = self.scan(
            'String example = "P0 基线收口裁决";\n'
            "int P3 = 3;\n"
        )

        self.assertEqual(findings, [])


class MagicStringRulingTest(unittest.TestCase):
    """撞名例外只在表里写着的那一行生效，别的文件、别的行照旧拦。"""

    VOCABULARY = {"RED": {"ScmFinanceEntryTypeEnum"}, "CANCELLED": {"ScmOrderStatusEnum"}}

    def scan(self, relative_path: str, text: str) -> list[guard.Finding]:
        source = JavaSource(Path(relative_path), relative_path, text)
        return guard.magic_string_literals(source, self.VOCABULARY)

    def test_a_literal_without_a_ruling_is_still_reported(self) -> None:
        findings = self.scan(
            "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/OrderTagService.java",
            "class OrderTagService {\n"
            '    boolean f(String s) {\n        return "CANCELLED".equals(s);\n    }\n'
            "}\n",
        )

        self.assertEqual([finding.locator for finding in findings], ['OrderTagService#"CANCELLED"'])

    def test_a_ruled_collision_is_excused_only_on_the_matching_line(self) -> None:
        path = "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/OrderTagService.java"
        excused = self.scan(
            path,
            "class OrderTagService {\n"
            '    private static final List<String> COLORS = List.of("RED");\n'
            "}\n",
        )
        elsewhere = self.scan(
            path,
            "class OrderTagService {\n"
            '    boolean f(String s) {\n        return "RED".equals(s);\n    }\n'
            "}\n",
        )

        self.assertEqual(excused, [])
        self.assertEqual(len(elsewhere), 1)

    def test_every_ruling_is_well_formed(self) -> None:
        for suffix, value, pattern, reason in guard.MAGIC_STRING_RULINGS:
            with self.subTest(value=value, suffix=suffix):
                self.assertTrue(suffix.startswith("com/xsy/scm/"), "ruling must name an SCM source file")
                self.assertRegex(value, r"^[A-Z][A-Z_]*$")
                self.assertTrue(reason.strip(), "a ruling without a reason is just a mute button")
                re.compile(pattern)

# ===========================================================================
# test_baseline_path_migration.py
# ===========================================================================
# Self-test for the baseline path rewriter.
#
# Run: ``python tools/quality/test_quality_tools.py MigrateLineTest``
#
# These cases exist because the rewriter's whole value is negative - it must not be
# able to lose, invent, or prematurely apply recorded debt. "It looked right on the
# dry run" is not evidence, so injectivity, occurrence preservation, the skip set
# and the "did the file actually move" gate are each asserted directly, including on
# the two misconfigurations that are easiest to make by hand: pointing two source
# prefixes at one target, and running the whole-package mapping after migrating only
# one domain.





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

# ===========================================================================
# test_quality_guard_ledger.py
# ===========================================================================
# Self-test for the baseline ledger's identity safety.
#
# Run: ``python tools/quality/test_quality_tools.py LedgerBreachTest``
#
# ``capture`` exists to shrink a ledger. The failure this file pins down is that a
# shrinking ledger can still *gain* a defect: fix one recorded defect, introduce one
# new defect, and the occurrence total is unchanged, so a rule that only compares
# totals reports ``+0`` and writes the new defect into the baseline. The following
# ``check`` then passes as well, because the new debt is now part of the baseline.
#
# None of these cases needs a compiled tree: they build findings directly and
# compare against a temporary ledger, because the property under test is the
# comparison itself - "every current identity is recorded, and no count grew" -
# not the scanners that produce the findings.






def finding(rule: str, path: str, locator: str, count: int = 1) -> Finding:
    return Finding(rule, path, locator, detail=locator, count=count)


class LedgerBreachTest(unittest.TestCase):
    """The identity comparison that decides whether a baseline may be written."""

    def setUp(self) -> None:
        self._real_dir = guard.BASELINE_DIR
        self._temp = tempfile.TemporaryDirectory()
        guard.BASELINE_DIR = Path(self._temp.name)

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real_dir
        self._temp.cleanup()

    def write(self, family: str, findings: list[Finding]) -> None:
        guard.baseline_path(family).write_text(
            "".join(f"{count}\t{identity}\n"
                    for identity, count in sorted(guard.counts_by_identity(findings).items())),
            encoding="utf-8")

    def test_equal_totals_with_a_swap_is_a_breach(self) -> None:
        """The whole bug: 1 fixed + 1 new, total unchanged, must not be allowed."""
        old = finding("magic-string-domain-literal", "a/Foo.java", 'Foo#"ENABLED"')
        new = finding("magic-string-domain-literal", "b/Bar.java", 'Bar#"CONFIRMED"')
        self.write("magic-string-domain-literal", [old])

        breaches = guard.ledger_breaches("magic-string-domain-literal", [new])

        self.assertEqual(len(breaches), 1, "the swapped-in defect must be reported")
        self.assertEqual(breaches[0].kind, "new-identity")
        # The guard against regressing to a total-only rule: the totals match.
        self.assertEqual(guard.occurrences([old]), guard.occurrences([new]))

    def test_fixing_two_and_adding_one_is_still_a_breach(self) -> None:
        """A *falling* total hides a new defect just as well as an unchanged one."""
        old_one = finding("stage-comment", "a/Foo.java", "Wave <n>")
        old_two = finding("stage-comment", "a/Bar.java", "本轮")
        new = finding("stage-comment", "b/Baz.java", "下一阶段")
        self.write("stage-comment", [old_one, old_two])

        breaches = guard.ledger_breaches("stage-comment", [new])

        self.assertEqual([breach.kind for breach in breaches], ["new-identity"])
        self.assertLess(guard.occurrences([new]), guard.occurrences([old_one, old_two]))

    def test_a_pure_subset_is_allowed(self) -> None:
        """What capture is actually for: dropping records, not swapping them."""
        old = finding("stage-comment", "a/Foo.java", "Wave <n>")
        kept = finding("stage-comment", "a/Bar.java", "本轮")
        self.write("stage-comment", [old, kept])

        self.assertEqual(guard.ledger_breaches("stage-comment", [kept]), [])

    def test_an_empty_baseline_blocks_everything(self) -> None:
        """A wiped ledger must not be re-seeded one finding at a time."""
        breaches = guard.ledger_breaches(
            "stage-comment", [finding("stage-comment", "a/Foo.java", "本轮")])
        self.assertEqual([breach.kind for breach in breaches], ["new-identity"])

    def test_a_grown_count_is_a_breach(self) -> None:
        grew = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=3)
        self.write("checkstyle", [finding("checkstyle", "a/Foo.java", "AvoidStarImport")])

        breaches = guard.ledger_breaches("checkstyle", [grew])

        self.assertEqual(len(breaches), 1)
        self.assertEqual(breaches[0].kind, "grown-count")
        self.assertEqual((breaches[0].allowed, breaches[0].current), (1, 3))

    def test_a_repeated_checkstyle_line_is_not_a_breach(self) -> None:
        """One file can legitimately report the same check twice; count is the point."""
        allowed = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=2)
        self.write("checkstyle", [allowed])

        self.assertEqual(
            guard.ledger_breaches("checkstyle",
                                  [finding("checkstyle", "a/Foo.java",
                                           "AvoidStarImport", count=2)]),
            [])


class CaptureRefusalTest(unittest.TestCase):
    """``capture`` must refuse, and must write nothing, when a ledger would grow."""

    def setUp(self) -> None:
        self._real_dir = guard.BASELINE_DIR
        self._temp = tempfile.TemporaryDirectory()
        guard.BASELINE_DIR = Path(self._temp.name)

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real_dir
        self._temp.cleanup()

    def test_capture_does_not_write_even_the_clean_families(self) -> None:
        """A half-applied capture is worse than none: the ledger would be part-new.

        ``stage-comment`` is clean (a subset) while ``checkstyle`` grew, so the
        run must abort *before* writing anything - including the clean family.
        """
        import argparse
        import contextlib
        import io

        old_stage = finding("stage-comment", "a/Foo.java", "本轮")
        kept = finding("stage-comment", "a/Bar.java", "下一阶段")
        guard.write_baseline(
            next(f for f in guard.FAMILIES if f.name == "stage-comment"), [old_stage, kept])
        guard.write_baseline(
            next(f for f in guard.FAMILIES if f.name == "checkstyle"),
            [finding("checkstyle", "a/Foo.java", "AvoidStarImport")])
        before_stage = guard.baseline_path("stage-comment").read_text(encoding="utf-8")

        grown = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=9)
        fake_scan = guard.Scan([kept, grown], frozenset({"stage-comment", "checkstyle"}))

        original = guard.collect
        guard.collect = lambda checkstyle: fake_scan
        try:
            args = argparse.Namespace(checkstyle=None, verbose=False, limit=10,
                                      allow_growth=False)
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                code = guard.command_capture(args)
        finally:
            guard.collect = original

        self.assertEqual(code, 1, "capture must fail when a ledger would grow")
        self.assertEqual(
            guard.baseline_path("stage-comment").read_text(encoding="utf-8"), before_stage,
            "the clean family must not be rewritten by a run that failed elsewhere")

# ===========================================================================
# test_package_migration_manifest.py
# ===========================================================================
# Attack tests for the Q1 migration manifest and the domain completeness assertion.
#
# Run: ``python tools/quality/test_quality_tools.py ManifestTreeTest``
#
# The manifest exists to make "this domain is migrated" a claim a machine can check.
# Every case below is an attempt to satisfy the *shape* of that claim without actually
# moving the right files - which is exactly the class of mistake a count-based ledger
# cannot see:
#
# ===========  ====================================================================
# A            one file was never moved
# B            ``A.java`` was forgotten and ``D.java`` added; the count is unchanged
# C            the file was copied instead of moved (both packages hold it)
# D            the new side has a file the manifest never listed
# E            the migration is exact - the only case that may pass
# F            the manifest is regenerated mid-migration with ``--force``
# G            capture: 1 recorded defect fixed + 1 new defect, total unchanged
# H            capture: 2 recorded defects fixed + 1 new defect, total falls
# ===========  ====================================================================
#
# The trees are fabricated under a temporary root rather than moved for real, because
# the property under test is the *comparison*, and a fabricated tree can express
# "copied instead of moved" and "manifest never listed it" without risking the real
# checkout. The assertion itself is exercised unmodified against those trees.





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
        #
        # 目录名固定且带 `.` 前缀 + 明确的 rm 收尾，不用 `tempfile.TemporaryDirectory`：
        # 它在 Windows 上会退化成 `tmpXXXXXXXX/` 落在仓库根**且**在句柄竞争时
        # cleanup() 静默失败，于是每次跑测试都在仓库根留下一堆 tmp 目录，
        # 被后续 `git add -A` 顺手提交进去（2026-09-26 收口提交真实踩到）。
        self._real = guard.BASELINE_DIR
        self._temp_dir = guard.ROOT / ".tmp-quality-capture-attack"
        shutil.rmtree(self._temp_dir, ignore_errors=True)
        self._temp_dir.mkdir(parents=True, exist_ok=True)
        guard.BASELINE_DIR = self._temp_dir

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real
        # 收尾必须重试：capture 刚写完文件，Windows 上杀毒/索引可能短暂持有句柄。
        # 删不掉也要留下**带点前缀**的目录，而不是混进仓库根的 tmp* 垃圾。
        for _ in range(3):
            if not self._temp_dir.exists():
                break
            shutil.rmtree(self._temp_dir, ignore_errors=True)

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
