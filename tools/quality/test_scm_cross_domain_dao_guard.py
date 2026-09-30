#!/usr/bin/env python3
"""Isolated fixtures for the cross-domain DAO read-only guard."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import scm_cross_domain_dao_guard as guard


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

        self.assertEqual(guard.collect_failures(self.root, self.allowlist_path), [])

    def test_allowlisted_method_mapped_to_update_fails(self) -> None:
        source = self.write_caller()
        self.write_allowlist(source)
        self.write_mapper("findAllowed", "update")

        failures = guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("maps to <update>" in failure for failure in failures))

    def test_allowlisted_method_mapped_to_delete_fails(self) -> None:
        source = self.write_caller()
        self.write_allowlist(source)
        self.write_mapper("findAllowed", "delete")

        failures = guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("maps to <delete>" in failure for failure in failures))

    def test_custom_method_without_mapper_statement_fails(self) -> None:
        source = self.write_caller("productReadDao.findAllowed(); productReadDao.fetchCustom();")
        self.write_allowlist(source, "findAllowed,fetchCustom")
        self.write_mapper("findAllowed")

        failures = guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("cannot prove SQL operation for fetchCustom" in failure for failure in failures))

    def test_mybatis_plus_select_and_exists_methods_need_no_xml(self) -> None:
        source = self.write_caller("productReadDao.selectById(1L); productReadDao.existsById(1L);")
        self.write_allowlist(source, "selectById,existsById")

        self.assertEqual(guard.collect_failures(self.root, self.allowlist_path), [])

    def test_stale_allowlist_entry_fails(self) -> None:
        self.source_path.write_text(
            "package com.xsy.scm.finance.service; class FinanceReader {}\n", encoding="utf-8")
        source = self.source_path.relative_to(self.root).as_posix()
        self.write_allowlist(source)

        failures = guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("stale allowlist entry" in failure for failure in failures))

    def test_unregistered_cross_domain_dao_fails(self) -> None:
        self.write_caller()
        self.allowlist_path.write_text("", encoding="utf-8")

        failures = guard.collect_failures(self.root, self.allowlist_path)

        self.assertTrue(any("unlisted finance -> com.xsy.scm.product.dao.ProductReadDao" in failure
                            for failure in failures))


if __name__ == "__main__":
    unittest.main(verbosity=2)
