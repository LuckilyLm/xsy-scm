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

# 本仓库 `.editorconfig` 对 `*.java` / `*.xml` 声明 `end_of_line = lf`，
# 且明确说明「Java 文件在 Windows 上以 CRLF 检出（core.autocrlf=true），
# 仓库内存的是 LF，因此这里声明 lf 与索引中的规范形式一致」。
#
# 结论：**规范形态是 LF**。CRLF 只是 core.autocrlf=true 检出时的工作区表象。
# 因此改写写回时必须把 CRLF 归一化为 LF —— 否则 Spotless（检查工作区字节）
# 会报「整个文件需要重排」，而 `git diff` 因为 autocrlf 归一化完全看不见，
# 排查成本极高（Q1 warehouse 域踩过一次）。
#
# 注意不要用 `read_text()`：它的 universal-newline 会把 CRLF 静默降级成 LF，
# 看似「没问题」，但那是隐式副作用，一旦换回 `newline=""` 写入就恢复成 CRLF。
# 这里显式归一化，让行为可读、可测。
NORMALIZE_TO_LF_SUFFIXES = SCAN_SUFFIXES

# 扫描范围：只碰 sa-admin 的源码与资源。sa-base 是 SmartAdmin 底座，不在 Q1 范围内。
SCAN_ROOTS = (
    SERVER / "sa-admin" / "src" / "main" / "java",
    SERVER / "sa-admin" / "src" / "main" / "resources",
    SERVER / "sa-admin" / "src" / "test" / "java",
)

# 这些文件里的旧包名是**设计意图**，不能整文件改写：
# - ScmArchitectureTest 同时分析 `net.lab1024.sa.admin.module.scm` 与 `com.xsy.scm`，
#   并在旧包归零时打印迁移完成。它必须继续用旧包名字面量
#   （`LEGACY_SCM_PACKAGE = "net.lab1024.sa.admin.module.scm"`）。
#
# 但「排除整文件」是错的：该文件同时有**编译期 import**，例如
#     import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
# 用于 `belongToAnyOf(OrderIdempotencyService.class)`。域一旦迁走，这个 import
# 就指向不存在的包，测试编译直接失败（order 域迁移时真实踩到）。
#
# 因此语义是「只排除字面量，不排除 import」：这些文件里只改写 import 行。
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
    """文件是否只允许改写 import 行（字面量保持旧包名）。"""
    try:
        rel = path.relative_to(ROOT).as_posix()
    except ValueError:
        return False
    return rel in EXCLUDED_RELATIVE


def rewrite_imports_only(text: str, pattern: re.Pattern[str], domain: str) -> tuple[str, int]:
    """只改写 import 行，其余（含字符串字面量）原样保留。

    用于 EXCLUDED_RELATIVE 中的文件：它们的旧包名字面量是设计意图，
    但 import 必须跟着类一起走，否则域迁走后测试编译失败。
    """
    new_prefix = f"{XSY_PREFIX}.{domain}"
    out: list[str] = []
    replacements = 0
    for line in text.splitlines(keepends=True):
        if line.lstrip().startswith("import ") and pattern.search(line):
            line, n = pattern.subn(new_prefix, line)
            replacements += n
        out.append(line)
    return "".join(out), replacements


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
            raw = read_bytes_safely(path)
            if raw is None:
                continue
            text = raw.decode("utf-8")
            if not pattern.search(text):
                continue

            # 排除名单里的文件只改 import 行：它们的旧包名字面量是设计意图
            # （ScmArchitectureTest 的 LEGACY_SCM_PACKAGE），但 import 必须跟着类走。
            import_only = is_excluded(path)
            count = 0
            kinds: set[str] = set()
            if import_only:
                _, count = rewrite_imports_only(text, pattern, domain)
                if count:
                    kinds.add("import")
            else:
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


def conform_to_editorconfig(path: Path, text: str) -> tuple[str, bool, bool]:
    """按 `.editorconfig` 对 `*.java` / `*.xml` 的声明规整文本。

    返回 `(新文本, 是否改了行尾, 是否补了末尾换行)`。

    `.editorconfig` 对这两个后缀声明了三条会直接影响 Spotless 的规则：

    - `end_of_line = lf`
    - `insert_final_newline = true`
    - `trim_trailing_whitespace = true`（由 Spotless 自己管，这里不碰）

    前两条必须在这里做，否则写回的文件会带着检出形态的 CRLF 或缺失的末尾换行，
    Spotless 报「format violation」而 `git diff` 因 `core.autocrlf=true` 看不见。

    末尾换行这条尤其阴险：`insert_final_newline` 只影响文件最后**一个字节**，
    所以 diff 形态是 `-}` / `+}` 这种看起来"没区别"的一行。仓库里确实存在
    末尾缺换行的存量文件（例如 `PriceResolverTest.java`），它们平时被 Spotless
    的 `ratchetFrom` 跳过、一直不报；一旦因迁包被判定为"已改动"就立刻暴露。
    在这里统一补齐，迁包就不会顺手把这类存量问题带进门禁失败。
    """
    if path.suffix not in NORMALIZE_TO_LF_SUFFIXES:
        return text, False, False

    line_ending_fixed = False
    if "\r\n" in text:
        text = text.replace("\r\n", "\n")
        line_ending_fixed = True

    final_newline_added = False
    if text and not text.endswith("\n"):
        text = text + "\n"
        final_newline_added = True

    return text, line_ending_fixed, final_newline_added


def apply_edits(edits: list[FileEdit], domain: str) -> int:
    """改写引用，并按 `.editorconfig` 规整行尾与末尾换行。

    三件事必须同时做对：

    1. **替换**：用 `rewrite_text()` 把旧全限定前缀换成新的。
    2. **行尾归一化**：`.editorconfig` 对 `*.java` / `*.xml` 声明 `end_of_line = lf`。
       工作区文件可能因 `core.autocrlf=true` 是 CRLF 检出形态，若不归一化就写回，
       Spotless 会报「整个文件需要重排」。`git diff` 看不到这个差异，因为
       autocrlf 在读索引时就把 LF 转换成了 CRLF，两边看起来一样。
    3. **末尾换行**：同样来自 `.editorconfig` 的 `insert_final_newline = true`。
       存量文件里确实有末尾缺换行的，被 `ratchetFrom` 跳过后一直没暴露；
       迁包把它们拉进门禁范围时会当场失败。详见 `conform_to_editorconfig()`。

    为什么不直接 `read_text()`：它的 universal-newline 会隐式把 CRLF 降级成 LF，
    行为上"碰巧正确"，但那是副作用而非契约 —— 一旦有人改成 `newline=""` 就会静默
    退回 CRLF。这里显式规整，行为可读、可测（见 `EditorConfigConformanceTest`）。

    非 UTF-8 文件（`read_bytes_safely()` 返回 None）不会进入 `edits`，无需在此再判。
    """
    pattern = domain_pattern(domain)
    written = 0
    total = 0
    fixed_eol = 0
    fixed_eof = 0
    for edit in edits:
        raw = edit.path.read_bytes()
        text = raw.decode("utf-8")
        if is_excluded(edit.path):
            new_text, count = rewrite_imports_only(text, pattern, domain)
        else:
            new_text, count = rewrite_text(text, pattern, domain)
        if count == 0:
            continue
        new_text, eol_fixed, eof_added = conform_to_editorconfig(edit.path, new_text)
        fixed_eol += int(eol_fixed)
        fixed_eof += int(eof_added)
        edit.path.write_bytes(new_text.encode("utf-8"))
        written += 1
        total += count
    print()
    notes = []
    if fixed_eol:
        notes.append(f"{fixed_eol} LF-normalized")
    if fixed_eof:
        notes.append(f"{fixed_eof} final-newline added")
    suffix = f", {', '.join(notes)}" if notes else ""
    print(f"RESULT: APPLIED ({written} files, {total} occurrences{suffix})")
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
