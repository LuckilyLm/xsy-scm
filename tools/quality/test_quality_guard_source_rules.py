#!/usr/bin/env python3
"""Contract tests for source segmentation and process-comment detection."""

from __future__ import annotations

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
