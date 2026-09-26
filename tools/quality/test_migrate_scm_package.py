#!/usr/bin/env python3
"""`migrate_scm_package.py` 的自测。

重点覆盖两条容易出事的性质：

1. **域边界锚定**：`common` 与 `customer` 首字母相同。锚定一旦写松，
   迁 `common` 会把 `scm.customer`、`scm.common*` 一并改掉 ——
   而 `git mv` 从没移动过那些文件，编译产物会变成「改了引用但文件还在旧包」。
2. **排除清单**：`ScmArchitectureTest` 必须保留旧包名字面量（它靠这个
   判断迁移是否完成），被脚本改掉会让这个断言永久静默失效。
"""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import migrate_scm_package as mig


class DomainAnchoringTest(unittest.TestCase):
    """替换必须锚定在完整域段上，不能命中同前缀的其它域或前缀相同的类名。"""

    def setUp(self) -> None:
        self.pattern = mig.domain_pattern("common")

    def test_matches_subpackage_reference(self) -> None:
        text = "import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;"
        self.assertIsNotNone(self.pattern.search(text))

    def test_matches_bare_domain_at_line_end(self) -> None:
        text = "net.lab1024.sa.admin.module.scm.common"
        self.assertIsNotNone(self.pattern.search(text))

    def test_does_not_match_sibling_domain_sharing_prefix(self) -> None:
        # customer 与 common 都以 "c" 开头，且 "common" 是 "customer" 的前缀干扰候选
        text = "import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;"
        self.assertIsNone(self.pattern.search(text))

    def test_does_not_match_longer_domain_name(self) -> None:
        text = "net.lab1024.sa.admin.module.scm.commonstatus.Foo"
        self.assertIsNone(self.pattern.search(text))

    def test_does_not_match_class_name_starting_with_domain(self) -> None:
        text = "net.lab1024.sa.admin.module.scm.commonAccess"
        self.assertIsNone(self.pattern.search(text))

    def test_does_not_match_sibling_domain_suffix(self) -> None:
        text = "net.lab1024.sa.admin.module.scm.mycommon"
        self.assertIsNone(self.pattern.search(text))


class RewriteTest(unittest.TestCase):
    """替换结果必须保留原后缀，只换前缀。"""

    def test_rewrites_prefix_and_keeps_suffix(self) -> None:
        text = "import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;"
        new_text, count = mig.rewrite_text(text, mig.domain_pattern("common"), "common")
        self.assertEqual(count, 1)
        self.assertEqual(new_text, "import com.xsy.scm.common.scope.ScmValueScope;")

    def test_rewrites_qualified_expression_not_only_imports(self) -> None:
        # 这类全限定表达式不经过 import，容易在「只改 import」的方案里漏掉
        text = ("throw new net.lab1024.sa.admin.module.scm.common.exception"
                ".ScmBusinessException(x);")
        new_text, count = mig.rewrite_text(text, mig.domain_pattern("common"), "common")
        self.assertEqual(count, 1)
        self.assertIn("com.xsy.scm.common.exception", new_text)

    def test_is_idempotent(self) -> None:
        text = "import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;"
        pattern = mig.domain_pattern("common")
        once, _ = mig.rewrite_text(text, pattern, "common")
        twice, count = mig.rewrite_text(once, pattern, "common")
        self.assertEqual(count, 0)
        self.assertEqual(once, twice)

    def test_rewrites_every_occurrence_on_a_line(self) -> None:
        text = ("net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode "
                "net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings")
        _, count = mig.rewrite_text(text, mig.domain_pattern("common"), "common")
        self.assertEqual(count, 2)

    def test_other_domains_in_the_same_line_are_untouched(self) -> None:
        # 同一行里 common 与 order 并存：只有 common 那半要变
        text = ("net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException "
                "net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode")
        new_text, count = mig.rewrite_text(text, mig.domain_pattern("common"), "common")
        self.assertEqual(count, 1)
        self.assertIn("com.xsy.scm.common.exception", new_text)
        self.assertIn("net.lab1024.sa.admin.module.scm.order.constant", new_text)


class ExcludedFilesTest(unittest.TestCase):
    """`ScmArchitectureTest` 的旧包名是断言的一部分，必须保留。"""

    def test_architecture_test_is_excluded(self) -> None:
        path = (mig.ROOT / "xsy-scm-server" / "sa-admin" / "src" / "test" / "java"
                / "net" / "lab1024" / "sa" / "admin" / "module" / "scm"
                / "ScmArchitectureTest.java")
        self.assertTrue(mig.is_excluded(path))

    def test_an_ordinary_test_file_is_not_excluded(self) -> None:
        path = (mig.ROOT / "xsy-scm-server" / "sa-admin" / "src" / "test" / "java"
                / "net" / "lab1024" / "sa" / "admin" / "module" / "scm" / "common"
                / "util" / "ScmDecimalStringsTest.java")
        self.assertFalse(mig.is_excluded(path))


class EditorConfigConformanceTest(unittest.TestCase):
    """写回时必须满足 `.editorconfig` 对 `*.java` / `*.xml` 的声明。

    `.editorconfig` 声明了两条会被 Spotless 直接检查的规则：

    - `end_of_line = lf`
      并注明「Java 文件在 Windows 上以 CRLF 检出（core.autocrlf=true），
      仓库内存的是 LF」。即规范形态是 LF，CRLF 只是检出表象。
      不归一化 → Spotless 报「整个文件需要重排」，而 `git diff` 因 autocrlf 看不到。
      （Q1 warehouse 域真实踩过。）
    - `insert_final_newline = true`
      只影响最后一个字节，diff 形态是 `-}` / `+}`，看着"没区别"。
      仓库里有末尾缺换行的存量文件，被 Spotless 的 `ratchetFrom` 跳过而长期不报，
      一旦因迁包被判为"已改动"就会当场失败。（Q1 product 域真实踩过：
      `PriceResolverTest.java`。）

    两条都在 `conform_to_editorconfig()` 里统一处理，行为可测。
    """

    def _conform(self, name: str, raw: bytes) -> tuple[bytes, bool, bool]:
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / name
            p.write_bytes(raw)
            text, eol, eof = mig.conform_to_editorconfig(p, raw.decode("utf-8"))
            return text.encode("utf-8"), eol, eof

    def test_crlf_is_normalized_to_lf(self) -> None:
        out, eol, eof = self._conform(
            "A.java", b"package a;\r\nclass A {}\r\n")
        self.assertTrue(eol)
        self.assertFalse(eof)
        self.assertNotIn(b"\r\n", out)

    def test_lf_is_left_alone(self) -> None:
        out, eol, eof = self._conform("A.java", b"package a;\nclass A {}\n")
        self.assertFalse(eol)
        self.assertFalse(eof)
        self.assertEqual(out, b"package a;\nclass A {}\n")

    def test_missing_final_newline_is_added(self) -> None:
        """末尾缺换行必须补齐 —— 这是 product 域 Spotless 失败的真实原因。"""
        out, eol, eof = self._conform("A.java", b"package a;\nclass A {}")
        self.assertFalse(eol)
        self.assertTrue(eof)
        self.assertTrue(out.endswith(b"}\n"))

    def test_crlf_and_missing_newline_are_both_fixed(self) -> None:
        out, eol, eof = self._conform("A.java", b"package a;\r\nclass A {}")
        self.assertTrue(eol)
        self.assertTrue(eof)
        self.assertNotIn(b"\r\n", out)
        self.assertTrue(out.endswith(b"}\n"))

    def test_other_suffixes_are_not_touched(self) -> None:
        """`.sql` 不在 `NORMALIZE_TO_LF_SUFFIXES` 里：Flyway 按行 CRC32 校验，
        行尾由 .gitattributes 钉死，绝不能被迁包脚本改写。"""
        raw = b"SELECT 1;\r\n"
        out, eol, eof = self._conform("V1__x.sql", raw)
        self.assertFalse(eol)
        self.assertFalse(eof)
        self.assertEqual(out, raw)

    def test_crlf_normalize_does_not_mangle_multibyte_content(self) -> None:
        """CRLF 归一化必须是纯字节级替换，不能碰中文注释的多字节序列。"""
        out, _, _ = self._conform(
            "A.java", "// 中文注释：仓库规范是 LF\r\npackage a;\r\n".encode("utf-8"))
        self.assertNotIn(b"\r\n", out)
        self.assertIn("中文注释：仓库规范是 LF".encode("utf-8"), out)

    def test_binary_looking_file_is_skipped_not_corrupted(self) -> None:
        """非 UTF-8 文件必须被 `read_bytes_safely()` 判为 None，不得进入改写集合。"""
        raw = b"\xff\xfe\x00\x01 net.lab1024.sa.admin.module.scm.common"
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "bogus.java"
            p.write_bytes(raw)
            self.assertIsNone(mig.read_bytes_safely(p))
            self.assertEqual(p.read_bytes(), raw)


class DomainNameValidationTest(unittest.TestCase):
    """域名直接进正则与前缀拼接，必须是简单包段。"""

    def test_rejects_path_traversal(self) -> None:
        self.assertIsNotNone(mig.main(["--domain", "../etc", "--dry-run"]) != 0)

    def test_rejects_uppercase(self) -> None:
        self.assertNotEqual(mig.main(["--domain", "Common", "--dry-run"]), 0)

    def test_rejects_dotted_name(self) -> None:
        self.assertNotEqual(mig.main(["--domain", "scm.common", "--dry-run"]), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
