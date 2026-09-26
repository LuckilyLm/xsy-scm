#!/usr/bin/env python3
"""Q1 迁包：把 SCM 业务域的 Java 包名从 SmartAdmin 命名空间搬到 `com.xsy.scm`。

背景
----
Q1 逐域把 `net.lab1024.sa.admin.module.scm.<domain>` 迁到 `com.xsy.scm.<domain>`。
一次 `git mv` 只移动文件，**不会**改任何内容；而一个域被引用的地方远超它自己：

- 该域内的 `package` 声明；
- 其它域、`AdminApplication`、`config` 等对该域的 `import`；
- MyBatis XML 里的 `resultType` / `parameterType` / `namespace` 全限定名；
- 以及散落在表达式里的全限定引用（`extends net.lab1024...ScmLocationForm`、
  `throw new net...ScmBusinessException(...)`）。

`common` 域实测 **726 处**（662 Java import + 60 XML + 41 package 声明，有重叠），
手工改写必然出错，因此用这个脚本。

为什么必须锚定到域边界
----------------------
`net.lab1024.sa.admin.module.scm.` 这个前缀被 **15 个域共享**。迁 `common` 时，
`net.lab1024.sa.admin.module.scm.customer` 必须以 `scm.customer` 开头**拒绝**匹配 ——
否则一次 `common` 迁移会把所有域的限定名一起改掉，编译产物里出现
「一半旧包一半新包」且 `git mv` 从未移动那些文件。

因此替换模式是 `net.lab1024.sa.admin.module.scm.<domain>` 后接
**`.`（进入子包）或非标识符字符（类名结束）**，再统一成 `com.xsy.scm.<domain>`。
`common` 与 `customer` 首字母相同，正是这条锚定要被测试覆盖的理由。

用法
----
    python tools/quality/migrate_scm_package.py --domain common --dry-run
    python tools/quality/migrate_scm_package.py --domain common --apply

`--dry-run` 打印将要改动的文件与替换点数，不写盘。`--apply` 才写。
两种模式都幂等：已经迁过的文件再次运行时不再计入。
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "xsy-scm-server"

LEGACY_PREFIX = "net.lab1024.sa.admin.module.scm"
XSY_PREFIX = "com.xsy.scm"

SCAN_SUFFIXES = (".java", ".xml")

# 扫描范围：只碰 sa-admin 的源码与资源。sa-base 是 SmartAdmin 底座，不在 Q1 范围内。
SCAN_ROOTS = (
    SERVER / "sa-admin" / "src" / "main" / "java",
    SERVER / "sa-admin" / "src" / "main" / "resources",
    SERVER / "sa-admin" / "src" / "test" / "java",
)

# 这些文件里的旧包名是**设计意图**，不能改：
# - ScmArchitectureTest 同时分析 `net.lab1024.sa.admin.module.scm` 与 `com.xsy.scm`，
#   并在旧包归零时打印迁移完成。它必须继续用旧包名字面量。
EXCLUDED_RELATIVE = {
    "xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/"
    "ScmArchitectureTest.java",
}


@dataclass
class FileEdit:
    path: Path
    replacements: int
    kinds: set[str] = field(default_factory=set)


def is_excluded(path: Path) -> bool:
    """判断文件是否在禁止改写的名单里（按仓库相对路径，正斜杠）。"""
    try:
        rel = path.relative_to(ROOT).as_posix()
    except ValueError:
        return False
    return rel in EXCLUDED_RELATIVE


def domain_pattern(domain: str) -> re.Pattern[str]:
    """构造锚定到域边界的旧全限定名前缀。

    `scm.<domain>` 之后必须是 `.`（进入子包）或非标识符字符（类名到此结束），
    这样 `common` 不会匹配到 `customer`。
    """
    return re.compile(
        r"\b" + re.escape(f"{LEGACY_PREFIX}.{domain}") + r"(?=[.\W]|$)"
    )


def rewrite_text(text: str, pattern: re.Pattern[str], domain: str) -> tuple[str, int]:
    """把命中的旧前缀换成新前缀，返回 (新文本, 替换次数)。"""
    new_prefix = f"{XSY_PREFIX}.{domain}"
    return pattern.subn(new_prefix, text)


def classify(line: str) -> str:
    """给命中行归类，便于 dry-run 报告按形态计数。"""
    stripped = line.lstrip()
    if stripped.startswith("package "):
        return "package"
    if stripped.startswith("import "):
        return "import"
    if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
        return "comment"
    return "reference"


def collect_edits(domain: str) -> list[FileEdit]:
    pattern = domain_pattern(domain)
    edits: list[FileEdit] = []

    for scan_root in SCAN_ROOTS:
        if not scan_root.is_dir():
            continue
        for path in sorted(scan_root.rglob("*")):
            if not path.is_file() or path.suffix not in SCAN_SUFFIXES:
                continue
            if is_excluded(path):
                continue
            raw = read_bytes_safely(path)
            if raw is None:
                continue
            text = raw.decode("utf-8")
            if not pattern.search(text):
                continue

            count = 0
            kinds: set[str] = set()
            for line in text.splitlines():
                hits = len(pattern.findall(line))
                if hits:
                    count += hits
                    kinds.add(classify(line))
            if count:
                edits.append(FileEdit(path, count, kinds))

    return edits


def read_bytes_safely(path: Path) -> bytes | None:
    """按字节读文件；非 UTF-8 内容返回 None（XML/Java 之外的东西不在扫描范围内）。"""
    raw = path.read_bytes()
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError:
        return None
    return raw


def report(edits: list[FileEdit], domain: str, mode: str) -> int:
    total = sum(edit.replacements for edit in edits)
    kind_totals: dict[str, int] = {}
    for edit in edits:
        for kind in edit.kinds:
            kind_totals[kind] = kind_totals.get(kind, 0) + 1

    print(f"=== migrate_scm_package ({mode}) ===")
    print(f"domain     : {domain}")
    print(f"rewrite    : {LEGACY_PREFIX}.{domain} -> {XSY_PREFIX}.{domain}")
    print(f"files      : {len(edits)}")
    print(f"occurrences: {total}")
    if kind_totals:
        parts = ", ".join(f"{k}={v}" for k, v in sorted(kind_totals.items()))
        print(f"file kinds : {parts}")
    if not edits:
        print()
        print("RESULT: NOTHING TO DO (already migrated, or the domain has no references)")
        return 0
    print()
    for edit in edits:
        rel = edit.path.relative_to(ROOT).as_posix()
        print(f"  {edit.replacements:4d}  {rel}")

    if mode == "dry-run":
        print()
        print("RESULT: DRY-RUN (no file written)")
    return total


def apply_edits(edits: list[FileEdit], domain: str) -> int:
    """按字节改写，**不触碰行尾符**。

    `read_text()` 会做 universal-newline 归一化（`\\r\\n` -> `\\n`），再用
    `newline=""` 写回就会把 CRLF 文件整体降级成 LF —— Spotless 检查的是工作区
    字节，会因此报「整个文件需要重排」，而 git diff（core.autocrlf=true）看不到，
    排查成本很高。这里一律读原始字节、只替换 ASCII 前缀、原样写回。
    """
    pattern = domain_pattern(domain)
    written = 0
    total = 0
    for edit in edits:
        raw = edit.path.read_bytes()
        text = raw.decode("utf-8")
        new_text, count = rewrite_text(text, pattern, domain)
        if count == 0:
            continue
        edit.path.write_bytes(new_text.encode("utf-8"))
        written += 1
        total += count
    print()
    print(f"RESULT: APPLIED ({written} files, {total} occurrences)")
    return total


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Q1: move one SCM domain's package to com.xsy.scm",
    )
    parser.add_argument("--domain", required=True, help="SCM 业务域名，例如 common")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--dry-run", action="store_true", help="只报告，不写盘")
    group.add_argument("--apply", action="store_true", help="执行改写")
    args = parser.parse_args(argv)

    # 域名必须是一个简单的包段：既防止拼出畸形正则，也防止把路径塞进来。
    if not re.fullmatch(r"[a-z][a-z0-9_]*", args.domain):
        print(f"ERROR: invalid domain name: {args.domain!r}", file=sys.stderr)
        return 2

    edits = collect_edits(args.domain)
    mode = "dry-run" if args.dry_run else "apply"
    report(edits, args.domain, mode)

    if args.apply and edits:
        apply_edits(edits, args.domain)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
