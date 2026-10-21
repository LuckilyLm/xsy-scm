#!/usr/bin/env python3
"""Contract tests for source segmentation and process-comment detection."""

from __future__ import annotations

import re
import unittest
from pathlib import Path

import quality_guard as guard
from java_source import JavaSource


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


if __name__ == "__main__":
    unittest.main(verbosity=2)
