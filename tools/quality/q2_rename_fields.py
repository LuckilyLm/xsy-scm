#!/usr/bin/env python3
"""Q2.1.1 —— 依赖字段改名：只改「声明处」与「接收者位置」，不做整文件文本替换。

背景（写进 docs/quality/java-code-quality-remediation-plan.md 的规则）：
Q2.1 用整文件正则改 `query` / `batch` 时，把权限字面量、@PostMapping 的 URL、
同名方法一起改坏了。因此本次改名的实现方式是：

  1. 逐行解析，只处理两种位置：
     - Java 字段声明：`private final <Type> <old>;`
     - 成员访问接收者：`<old>.` （词边界 + 紧跟点号）
  2. 绝不改动：字符串字面量、注解参数、import / package 路径、同名方法。
  3. 改完必须能编译（由 verify.py backend 兜底），且改名前后
     `grep -c '\b<old>\b'` 在目标文件里必须归零。

用法（默认 dry-run）：
    python tools/quality/q2_rename_fields.py --check
    python tools/quality/q2_rename_fields.py --apply
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# (文件, 旧字段名, 新字段名)
RENAMES: list[tuple[str, str, str]] = [
    (
        "common/scope/ScmDataScopeService.java",
        "scopeDao",
        "dataScopeDao",
    ),
    (
        "product/service/ProductBatchService.java",
        "spus",
        "productSpuDao",
    ),
    (
        "product/service/ProductBatchService.java",
        "categories",
        "productCategoryService",
    ),
    (
        "product/service/ProductBatchService.java",
        "tags",
        "productTagService",
    ),
    (
        "product/service/ProductQueryService.java",
        "spus",
        "productSpuDao",
    ),
    (
        "product/service/ProductQueryService.java",
        "skus",
        "productSkuDao",
    ),
    (
        "product/service/ProductQueryService.java",
        "images",
        "productImageDao",
    ),
    (
        "product/service/ProductQueryService.java",
        "categories",
        "productCategoryService",
    ),
    (
        "product/service/ProductQueryService.java",
        "tags",
        "productTagService",
    ),
    (
        "product/service/ProductSpuService.java",
        "categories",
        "productCategoryService",
    ),
    (
        "product/service/ProductSpuService.java",
        "skus",
        "productSkuSyncManager",
    ),
    (
        "product/service/ProductSpuService.java",
        "images",
        "productImageSyncManager",
    ),
    (
        "product/service/ProductSpuService.java",
        "uom",
        "productUomService",
    ),
    (
        "product/service/ProductSpuService.java",
        "tags",
        "productTagService",
    ),
]

# 声明位置：`private final <Type> <name>;`（允许 = 初始化）
_DECL = re.compile(
    r"^(?P<head>\s*(?:private|protected|public)\s+(?:final\s+)?[A-Z][\w.]*(?:<[^;>]*>)?\s+)"
    r"(?P<name>%s)"
    r"(?P<tail>\s*(?:=[^;]*)?;\s*)$"
)
# 接收者位置：`<name>.`（前面不是 . 或单词字符，后面紧跟点号）
_RECEIVER = re.compile(r"(?<![\w.])(?P<name>%s)(?=\.)")

# 字符串字面量（含转义）与注解头部——这些区域不参与改名
_STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')
_ANNOTATION = re.compile(r"@[A-Za-z_]\w*")


def _protected_spans(line: str) -> list[tuple[int, int]]:
    """行内禁止改名的区间：字符串字面量 + 注解名。

    为什么需要：`if (!"REMOVE".equals(...)) tags.assertUsable(...)` 这一行
    既有字符串字面量又有 `tags.` 接收者。Q2.1 的整文件正则会把这行的
    `"REMOVE"` 之外的 `tags` 全改掉，但若换成更粗的规则就会连字面量一起改。
    这里把保护区标出来，只在保护区之外做接收者替换。
    """
    spans = []
    for match in _STRING_LITERAL.finditer(line):
        spans.append(match.span())
    for match in _ANNOTATION.finditer(line):
        spans.append(match.span())
    return spans


def _in_spans(index: int, spans: list[tuple[int, int]]) -> bool:
    return any(start <= index < end for start, end in spans)


def rewrite(source: str, old: str, new: str) -> tuple[str, int]:
    """返回 (新文本, 改动行数)。只改声明与接收者位置。"""
    decl = re.compile(_DECL.pattern % re.escape(old))
    receiver = re.compile(_RECEIVER.pattern % re.escape(old))
    out: list[str] = []
    changed = 0
    for line in source.splitlines(keepends=True):
        body = line.rstrip("\n")
        newline = "\n" if line.endswith("\n") else ""

        m = decl.match(body)
        if m:
            out.append(m.group("head") + new + m.group("tail") + newline)
            changed += 1
            continue

        if receiver.search(body):
            spans = _protected_spans(body)
            # 从右往左替换，避免位移影响后续 span 判断
            matches = [mm for mm in receiver.finditer(body) if not _in_spans(mm.start(), spans)]
            if not matches:
                out.append(line)
                continue
            rebuilt = body
            for mm in reversed(matches):
                rebuilt = rebuilt[: mm.start()] + new + rebuilt[mm.end():]
            out.append(rebuilt + newline)
            changed += 1
            continue

        out.append(line)
    return "".join(out), changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Q2.1.1 dependency field rename")
    parser.add_argument("--apply", action="store_true", help="真正写回文件（默认只检查）")
    args = parser.parse_args()

    base = ROOT / "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm"
    total = 0
    for relative, old, new in RENAMES:
        path = base / relative
        if not path.is_file():
            print(f"MISSING {relative}")
            return 1
        original = path.read_text(encoding="utf-8")
        updated, changed = rewrite(original, old, new)
        remaining = len(re.findall(rf"(?<![\w.]){re.escape(old)}(?![\w])", updated))
        print(f"{'APPLY' if args.apply else 'CHECK'} {relative}: {old} -> {new} ({changed} lines, {remaining} refs left)")
        if remaining:
            # 仍残留说明有未被识别的形态，交人工
            for number, line in enumerate(updated.splitlines(), 1):
                if re.search(rf"(?<![\w.]){re.escape(old)}(?![\w])", line):
                    print(f"    RESIDUAL {number}: {line.strip()}")
        if args.apply:
            path.write_text(updated, encoding="utf-8")
        total += changed
    print(f"total lines changed: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
