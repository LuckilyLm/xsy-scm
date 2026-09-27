#!/usr/bin/env python3
"""Self-test for the Q2.1.1 dependency-naming classifier.

Run: ``python tools/quality/test_q2_audit_naming.py``

The failure this file pins down is the one that shipped in Q2.1: the audit
reported ``abbreviated = 0`` for the whole ``product`` domain while
``ProductTagService tags`` sat in the tree. The old rule required the field name
to be a *token suffix of the type name*, but ``tags`` has nothing to do with the
type's ``Service`` suffix - so the detection silently missed every case the rule
was written to catch, and the report looked clean.

These cases fix the boundary explicitly:

* A - a bare technical role name is debt.
* B - a business type name whose leading domain word was dropped is debt.
* C - a genuine role qualifier (``readOnlyDataSource``, ``transactionManager``,
      ``errorCode``) is **not** debt, even though it is not the type name.
* Anything the rules cannot judge with high confidence must fall to
  ``MANUAL_REVIEW`` - never auto-``CLEAN``.
"""

from __future__ import annotations

import unittest

import q2_audit


class ClassifyFieldTest(unittest.TestCase):
    """``classify_field`` returns (decision, recommended_name)."""

    # --- rule C: legitimate role aliases must stay clean -------------------

    def test_role_alias_transaction_manager_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("PlatformTransactionManager", "transactionManager"),
            ("CLEAN", ""),
        )

    def test_role_alias_error_code_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ScmErrorCode", "errorCode"),
            ("CLEAN", ""),
        )

    def test_role_alias_readonly_datasource_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("DataSource", "readOnlyDataSource"),
            ("CLEAN", ""),
        )

    def test_role_qualifier_prefix_is_clean(self) -> None:
        """`dataScopeService` is `ScmDataScopeService` with a role qualifier."""
        self.assertEqual(
            q2_audit.classify_field("ScmDataScopeService", "dataScopeService"),
            ("CLEAN", ""),
        )

    # --- already-correct names stay clean ---------------------------------

    def test_full_type_name_lowercamel_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductCategoryService", "productCategoryService"),
            ("CLEAN", ""),
        )

    def test_scm_prefix_stripped_name_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ScmDataScopeDao", "dataScopeDao"),
            ("CLEAN", ""),
        )

    def test_sql_session_factory_is_clean(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("SqlSessionFactory", "sqlSessionFactory"),
            ("CLEAN", ""),
        )

    # --- rule A: bare technical role names --------------------------------

    def test_bare_role_name_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductCategoryDao", "dao"),
            ("ABBREV_BARE", "productCategoryDao"),
        )

    def test_bare_role_service_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductTagService", "service"),
            ("ABBREV_BARE", "productTagService"),
        )

    # --- rule B: truncated business type names ----------------------------

    def test_truncated_scope_dao_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ScmDataScopeDao", "scopeDao"),
            ("ABBREV_TRUNCATED", "dataScopeDao"),
        )

    def test_plural_tags_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductTagService", "tags"),
            ("ABBREV_TRUNCATED", "productTagService"),
        )

    def test_plural_categories_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductCategoryService", "categories"),
            ("ABBREV_TRUNCATED", "productCategoryService"),
        )

    def test_plural_skus_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductSkuSyncManager", "skus"),
            ("ABBREV_TRUNCATED", "productSkuSyncManager"),
        )

    def test_plural_images_dao_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductImageDao", "images"),
            ("ABBREV_TRUNCATED", "productImageDao"),
        )

    def test_uom_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductUomService", "uom"),
            ("ABBREV_TRUNCATED", "productUomService"),
        )

    def test_plural_spus_is_debt(self) -> None:
        self.assertEqual(
            q2_audit.classify_field("ProductSpuDao", "spus"),
            ("ABBREV_TRUNCATED", "productSpuDao"),
        )

    # --- unknown shapes must not be auto-cleaned --------------------------

    def test_unjudgeable_shape_goes_to_manual_review(self) -> None:
        """`ObjectMapper json` is neither the type name nor a known role alias."""
        self.assertEqual(
            q2_audit.classify_field("ObjectMapper", "json"),
            ("MANUAL_REVIEW", ""),
        )

    def test_never_returns_clean_for_unknown(self) -> None:
        """The regression guard: no unknown shape may be silently accepted."""
        samples = [
            ("ObjectMapper", "json"),
            ("PlatformTransactionManager", "txm"),
            ("ScmValueScope", "warehouse"),
        ]
        for simple, name in samples:
            decision, _ = q2_audit.classify_field(simple, name)
            self.assertIn(decision, {"MANUAL_REVIEW", "ABBREV_BARE", "ABBREV_TRUNCATED"})
            self.assertNotEqual(decision, "CLEAN")

    # --- collaborator filter ----------------------------------------------

    def test_value_objects_are_not_collaborators(self) -> None:
        """Value objects / forms / entities are out of scope for this rule."""
        for simple in ("ProductSpuEntity", "ProductSkuForm", "ProductBatchResultVO", "ScmValueScope"):
            self.assertFalse(q2_audit._is_collaborator(simple), simple)

    def test_collaborators_are_detected(self) -> None:
        for simple in ("ProductTagService", "ProductImageDao", "ProductSkuSyncManager", "FileService"):
            self.assertTrue(q2_audit._is_collaborator(simple), simple)

    # --- plural helper ----------------------------------------------------

    def test_singular_handles_ies_and_s(self) -> None:
        self.assertEqual(q2_audit._singular("categories"), "category")
        self.assertEqual(q2_audit._singular("images"), "image")
        self.assertEqual(q2_audit._singular("tags"), "tag")
        self.assertEqual(q2_audit._singular("spus"), "spu")
        self.assertEqual(q2_audit._singular("address"), "address")
        self.assertEqual(q2_audit._singular("status"), "statu")


if __name__ == "__main__":
    unittest.main(verbosity=2)
