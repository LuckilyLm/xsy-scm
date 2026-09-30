#!/usr/bin/env python3
"""Source-level dependency boundary tests for SCM common."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import scm_common_domain_source_guard as guard


class CommonDomainSourceGuardTest(unittest.TestCase):
    def test_regular_import_is_rejected(self) -> None:
        findings = guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "import com.xsy.scm.product.dao.ProductDao;\n"
            "class ScopeHelper {}\n"
        )
        self.assertTrue(any("forbidden import to com.xsy.scm.product" in finding for finding in findings))

    def test_static_import_is_rejected(self) -> None:
        findings = guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "import static com.xsy.scm.report.constant.ReportPermission.COST_QUERY;\n"
            "class ScopeHelper {}\n"
        )
        self.assertTrue(any("forbidden import to com.xsy.scm.report" in finding for finding in findings))

    def test_code_fully_qualified_reference_is_rejected(self) -> None:
        findings = guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "class ScopeHelper { Object value = com.xsy.scm.finance.constant.FinancePermission.PAYMENT_QUERY; }\n"
        )
        self.assertTrue(any("fully-qualified reference to com.xsy.scm.finance" in finding
                            for finding in findings))

    def test_comments_and_string_literals_do_not_count(self) -> None:
        findings = guard.violations_in_source(
            "package com.xsy.scm.common.scope;\n"
            "// import com.xsy.scm.customer.dao.CustomerDao;\n"
            "/* com.xsy.scm.inventory.dao.InventoryDao */\n"
            'class ScopeHelper { String example = "com.xsy.scm.delivery.dao.DeliveryDao"; }\n'
        )
        self.assertEqual(findings, [])

    def test_common_and_jdk_references_remain_allowed(self) -> None:
        findings = guard.violations_in_source(
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

            findings = guard.scan_tree(common_root)

        self.assertTrue(any("forbidden import to com.xsy.scm.warehouse" in finding for finding in findings))


if __name__ == "__main__":
    unittest.main(verbosity=2)
