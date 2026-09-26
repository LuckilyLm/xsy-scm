#!/usr/bin/env python3
"""Q2.1 命名 / 类型安全审计快照 —— common + product。

只读工具：统计 common 与 product 两个域的命名债与 magic-string 债，
输出 before / after 可对比的确定性数字。不改任何代码，不参与门禁。

用法：
    python tools/quality/q2_audit.py                 # 默认 common + product
    python tools/quality/q2_audit.py --domain product
    python tools/quality/q2_audit.py --json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import quality_guard as guard  # noqa: E402

Q2_DOMAINS = ("common", "product")

# 依赖字段：类型首字母大写，字段名小写开头，带 private/protected/public [final]
_FIELD_DECLARATION = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:final\s+)?"
    r"(?P<type>[A-Z][\w.]*(?:<[^;]*>)?)\s+(?P<name>[a-z]\w*)\s*(?:=[^;]*)?;\s*$"
)

# 裸角色名（与 guard 的 GENERIC_DEPENDENCY_NAMES 同源）
GENERIC_NAMES = guard.GENERIC_DEPENDENCY_NAMES

# 「删掉领域前缀」的缩写字段：ProductSpuDao spuDao / ProductCategoryDao categoryDao
_CAMEL_BOUNDARY = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")

# 单字母 / 无信息局部变量名
_MEANINGLESS_LOCALS = frozenset({"c", "p", "r", "x", "y", "z", "o", "e", "i", "j", "k", "v", "d", "s", "t", "m", "n"})


def domain_dirs(domain: str) -> list[Path]:
    return [
        guard.MAIN_SOURCE_ROOT / "com/xsy/scm" / domain,
        guard.TEST_SOURCE_ROOT / "com/xsy/scm" / domain,
    ]


def camel_tokens(name: str) -> list[str]:
    """把 camelCase 拆成小写 token：productSpuDao -> [product, spu, dao]。"""
    if not name:
        return []
    return [t.lower() for t in _CAMEL_BOUNDARY.split(name)]


def type_tokens(simple_type: str) -> list[str]:
    """类型简单名去掉泛型后拆 token：ProductSpuDao -> [product, spu, dao]。"""
    base = simple_type.split("<", 1)[0].rsplit(".", 1)[-1]
    return [t.lower() for t in _CAMEL_BOUNDARY.split(base)]


def audit_domain(domain: str, vocabulary: dict[str, set[str]]) -> dict:
    """统计一个域的命名债与 magic-string 债。"""
    sources: list = []
    for directory in domain_dirs(domain):
        if directory.is_dir():
            sources.extend(guard.load_sources((directory,)))

    result = {
        "domain": domain,
        "files": len(sources),
        "dependency_fields_total": 0,
        "bare_role_field_count": 0,
        "abbreviated_dao_field_count": 0,
        "meaningless_local_samples": [],
        "magic_string_count": 0,
        "magic_string_with_enum_count": 0,
        "bare_role_fields": [],
        "abbreviated_fields": [],
    }

    meaningless = Counter()
    magic_enum = 0

    for source in sources:
        for number, line in enumerate(source.code.splitlines(), start=1):
            match = _FIELD_DECLARATION.match(line)
            if not match:
                continue
            result["dependency_fields_total"] += 1
            name = match.group("name")
            raw_type = match.group("type")
            simple = raw_type.split("<", 1)[0].rsplit(".", 1)[-1]

            # 裸角色名，且类型名小写后不等于字段名（Dao dao 不算）
            if name in GENERIC_NAMES and simple.lower() != name:
                result["bare_role_field_count"] += 1
                result["bare_role_fields"].append(
                    f"{source.relative_path}:{number} {raw_type} {name}"
                )
                continue

            # 缩写：类型 token 去掉角色后缀后，字段名只是删掉领域前缀
            # 例：ProductSpuDao spuDao -> type tokens [product,spu,dao]，name tokens [spu,dao]
            tt = type_tokens(raw_type)
            nt = camel_tokens(name)
            if len(tt) >= 2 and len(nt) >= 1 and nt == tt[-len(nt):] and len(nt) < len(tt):
                result["abbreviated_dao_field_count"] += 1
                result["abbreviated_fields"].append(
                    f"{source.relative_path}:{number} {raw_type} {name}"
                )

        # magic string：复用 guard 的检测（词汇表来自扫描根的生产源码）
        for _finding in guard.magic_string_literals(source, vocabulary):
            magic_enum += 1

        # 无信息局部变量：var c = / var p = 之类
        for match in re.finditer(r"\bvar\s+([a-z]\w*)\s*=", source.code):
            if match.group(1) in _MEANINGLESS_LOCALS:
                meaningless[source.relative_path] += 1

    result["magic_string_count"] = magic_enum
    result["meaningless_local_count"] = sum(meaningless.values())
    result["meaningless_local_samples"] = [
        f"{path} x{count}" for path, count in sorted(meaningless.items())
    ]
    return result


def build_vocabulary() -> dict[str, set[str]]:
    """enum 词汇表：由 guard 的 SCM 生产源码构建（与 check 同算法）。

    guard.enum_vocabulary 返回 {常量名: {声明它的 enum 类型}}，
    magic_string_literals 需要的正是这个视图。
    """
    return guard.enum_vocabulary(guard.scm_main_sources())


def main() -> int:
    parser = argparse.ArgumentParser(description="Q2.1 naming / type-safety audit")
    parser.add_argument("--domain", action="append", default=None)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    domains = tuple(args.domain) if args.domain else Q2_DOMAINS
    vocabulary = build_vocabulary()

    reports = [audit_domain(domain, vocabulary) for domain in domains]

    if args.json:
        print(json.dumps(reports, ensure_ascii=False, indent=2))
        return 0

    for report in reports:
        print(f"=== {report['domain']} ===")
        print(f"  files scanned                 : {report['files']}")
        print(f"  dependency fields total       : {report['dependency_fields_total']}")
        print(f"  bare role fields (dao/service): {report['bare_role_field_count']}")
        print(f"  abbreviated type-name fields  : {report['abbreviated_dao_field_count']}")
        print(f"  meaningless locals (var c etc): {report['meaningless_local_count']}")
        print(f"  magic strings (enum-backed)   : {report['magic_string_count']}")
        if report["bare_role_fields"]:
            print("  --- bare role fields ---")
            for item in report["bare_role_fields"]:
                print(f"    {item}")
        if report["abbreviated_fields"]:
            print("  --- abbreviated fields ---")
            for item in report["abbreviated_fields"]:
                print(f"    {item}")
        if report["meaningless_local_samples"]:
            print("  --- meaningless locals ---")
            for item in report["meaningless_local_samples"]:
                print(f"    {item}")
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
