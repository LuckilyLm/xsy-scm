#!/usr/bin/env python3
"""Q2.1 命名 / 类型安全审计快照 —— common + product。

只读工具：统计 common 与 product 两个域的命名债与 magic-string 债，
输出 before / after 可对比的确定性数字。不改任何代码，不参与门禁。

依赖字段命名判定（Q2.1.1 收紧后）：

  规则 A  ABBREV_BARE      裸技术角色名：``private final ProductCategoryDao dao;``
                           类型已给出领域名，字段名却只写角色 → 必须整改。
  规则 B  ABBREV_TRUNCATED 类型业务名被明显裁剪：
                           ``ProductTagService tags`` / ``ProductSkuSyncManager skus``
                           ——字段名 = 类型 token 去掉项目级冗余前缀后的后缀，
                           丢掉的是「这是哪个领域对象」，必须整改。
  规则 C  ROLE_ALIAS       合法角色别名：``DataSource readOnlyDataSource`` /
                           ``PlatformTransactionManager transactionManager`` /
                           ``ScmErrorCode errorCode`` —— 表达的是「这个对象在本类里的角色」，
                           不是偷懒缩写 → 不算债。

自动规则无法高置信度判断时一律落到 **MANUAL_REVIEW**，绝不自动判成 CLEAN。
历史教训：旧版算法用「字段名 token 是类型 token 后缀」判定，于是
``ProductTagService tags``（token 为 ``tags``，类型后缀 token 是 ``service``）根本不匹配，
product 域因此报出「abbreviated = 0」的假阴性。

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

_CAMEL_BOUNDARY = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")

# 单字母 / 无信息局部变量名
_MEANINGLESS_LOCALS = frozenset({"c", "p", "r", "x", "y", "z", "o", "e", "i", "j", "k", "v", "d", "s", "t", "m", "n"})

# 只有以这些角色后缀收尾的类型才按「注入协作方」检查命名。
# 值对象 / 表单 / 实体 / 枚举不在本工具的判定范围内（它们的字段名本就该是业务名）。
_COLLABORATOR_SUFFIXES = (
    "service",
    "dao",
    "manager",
    "validator",
    "repository",
    "mapper",
    "reader",
    "writer",
    "client",
    "controller",
    "handler",
    "factory",
    "provider",
    "resolver",
    "support",
    "guard",
    "helper",
)

# 项目级冗余前缀：只剥离「根命名空间前缀」。`Scm` 是整个仓库的统一前缀，
# 每个类型都有，对「这是哪个领域对象」零信息量 → 剥离后得到期望字段名：
#   ScmDataScopeDao      -> dataScopeDao
#   ScmWarehouseScopeGuard -> warehouseScopeGuard
# `Product` 是**领域名**，剥掉会让字段名丢失信息（`ProductTagService` 变成 `tagService`
# 就分不清是商品标签还是采购标签），因此保留：
#   ProductTagService    -> productTagService
#   ProductCategoryService -> productCategoryService
_PROJECT_PREFIX_TOKENS = frozenset({"scm"})

# 合法角色别名的固定白名单：这些是「同一个类型在本类里扮演不同角色」的表达，
# 不是缩写。见 Q2.1.1 §2。
_ROLE_ALIAS_NAMES = frozenset(
    {
        "readonlydatasource",
        "primarydatasource",
        "secondarydatasource",
        "transactionmanager",
        "errorcode",
        "objectmapper",
        "sqlsessionfactory",
        "jsonserializer",
        "jsondeserializer",
    }
)


def domain_dirs(domain: str) -> list[Path]:
    return [
        guard.MAIN_SOURCE_ROOT / "com/xsy/scm" / domain,
        guard.TEST_SOURCE_ROOT / "com/xsy/scm" / domain,
    ]


def camel_tokens(name: str) -> list[str]:
    """把 camelCase 拆成小写 token：``productSpuDao`` -> ``[product, spu, dao]``。"""
    if not name:
        return []
    return [t.lower() for t in _CAMEL_BOUNDARY.split(name)]


def type_tokens(simple_type: str) -> list[str]:
    """类型简单名去掉泛型后拆 token：``ProductSpuDao`` -> ``[product, spu, dao]``。"""
    base = simple_type.split("<", 1)[0].rsplit(".", 1)[-1]
    return [t.lower() for t in _CAMEL_BOUNDARY.split(base)]


def _canonical_name(tokens: list[str]) -> str:
    """type token 序列还原成 lowerCamelCase：``[product, spu, dao]`` -> ``productSpuDao``。"""
    if not tokens:
        return ""
    return tokens[0] + "".join(t[:1].upper() + t[1:] for t in tokens[1:])


def _is_collaborator(simple_type: str) -> bool:
    lowered = simple_type.lower()
    return any(lowered.endswith(suffix) for suffix in _COLLABORATOR_SUFFIXES)


def _singular(token: str) -> str:
    """把 token 还原成单数，用于「字段名是类型业务名词的复数形态」判定。

    tags -> tag / images -> image / categories -> category / spus -> spu
    """
    if token.endswith("ies") and len(token) > 3:
        return token[:-3] + "y"
    if token.endswith("ses") and len(token) > 3:
        return token[:-2]
    if token.endswith("s") and not token.endswith("ss") and len(token) > 1:
        return token[:-1]
    return token


def classify_field(simple_type: str, name: str) -> tuple[str, str]:
    """判定一个依赖字段的命名，返回 ``(decision, recommended_name)``。

    decision 取值：
      ``CLEAN``           已经等于「类型全名去项目级冗余前缀后的 lowerCamelCase」。
      ``ABBREV_BARE``     裸技术角色名（规则 A）。
      ``ABBREV_TRUNCATED``类型业务名被明显裁剪（规则 B）。
      ``MANUAL_REVIEW``   自动规则无法高置信度判断，留人工裁决——绝不自动判 CLEAN。

    ``recommended_name`` 对 CLEAN / MANUAL_REVIEW 为空串。
    """
    tokens = type_tokens(simple_type)
    if not tokens:
        return "MANUAL_REVIEW", ""

    # 去掉项目级冗余前缀后的「期望字段名」
    stripped = [t for t in tokens if t not in _PROJECT_PREFIX_TOKENS]
    if not stripped:
        # 类型本身只有项目前缀（如 `ScmService`），没有领域信息可拼，交人工。
        return "MANUAL_REVIEW", ""
    canonical = _canonical_name(stripped)

    if name == canonical:
        return "CLEAN", ""

    lowered_name = name.lower()
    if lowered_name in _ROLE_ALIAS_NAMES:
        return "CLEAN", ""

    # 规则 A：裸技术角色名。类型不是 `Dao` 这种同名兜底时才算债。
    if name in GENERIC_NAMES and simple_type.lower() != name:
        return "ABBREV_BARE", canonical

    # 规则 C：合法角色别名——字段名以完整期望名结尾，前面加了「角色限定词」。
    # 例 `dataScopeService`（期望 `scopeService` 之上有 "data" 限定）、
    # `readOnlyDataSource`（期望 `dataSource` 之上有 "readOnly" 限定）。
    # 这类表达的是对象在本类里的角色，不判为债。
    if lowered_name.endswith(canonical) and len(lowered_name) > len(canonical):
        return "CLEAN", ""

    # 规则 B：字段名是「类型里那个业务名词」的被裁剪形态。
    #
    # 判定方式：把去前缀后的类型 token 当作语料，看字段名 token 序列能否由
    # 「保留其中一段连续 token、末 token 允许加复数 s」得到，且长度更短。
    # 例：
    #   ProductTagService   [product, tag, service]  tags       -> [tag]     ✓
    #   ProductSkuSyncManager [product, sku, sync, manager] skus -> [sku]     ✓
    #   ProductImageDao     [product, image, dao]    images     -> [image]   ✓
    #   ProductUomService   [product, uom, service]  uom        -> [uom]     ✓
    #   ProductCategoryService [product, category, service] categories -> [category] ✓
    #   ProductSpuDao       [product, spu, dao]      spus       -> [spu]     ✓
    # 而 `productCategoryService` 是 canonical 本身（前面已判 CLEAN），
    # `dataScopeService` 是角色限定（前面已判 CLEAN），都不会落到这里。
    nt = camel_tokens(name)
    hit = False
    if nt and len(nt) < len(stripped):
        for start in range(len(stripped)):
            for end in range(start + 1, len(stripped) + 1):
                window = stripped[start:end]
                if len(window) != len(nt):
                    continue
                if window == nt:
                    hit = True
                    break
                # 末 token 允许复数：tags/tag、images/image、categories/category。
                if window[:-1] == nt[:-1] and _singular(window[-1]) == _singular(nt[-1]):
                    hit = True
                    break
            if hit:
                break
    if hit:
        return "ABBREV_TRUNCATED", canonical

    return "MANUAL_REVIEW", ""


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
        "collaborator_fields_total": 0,
        "bare_role_field_count": 0,
        "abbreviated_dao_field_count": 0,
        "role_alias_field_count": 0,
        "manual_review_field_count": 0,
        "meaningless_local_samples": [],
        "magic_string_count": 0,
        "magic_string_with_enum_count": 0,
        "bare_role_fields": [],
        "abbreviated_fields": [],
        "manual_review_fields": [],
        "field_table": [],
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

            if not _is_collaborator(simple):
                continue
            result["collaborator_fields_total"] += 1

            decision, recommended = classify_field(simple, name)
            where = f"{source.relative_path}:{number}"
            result["field_table"].append(
                {
                    "file": source.relative_path,
                    "line": number,
                    "type": raw_type,
                    "currentName": name,
                    "recommendedName": recommended,
                    "decision": decision,
                }
            )
            entry = f"{where} {raw_type} {name}" + (
                f" -> {recommended}" if recommended else ""
            )
            if decision == "ABBREV_BARE":
                result["bare_role_field_count"] += 1
                result["bare_role_fields"].append(entry)
            elif decision == "ABBREV_TRUNCATED":
                result["abbreviated_dao_field_count"] += 1
                result["abbreviated_fields"].append(entry)
            elif decision == "MANUAL_REVIEW":
                result["manual_review_field_count"] += 1
                result["manual_review_fields"].append(entry)
            else:
                result["role_alias_field_count"] += 1

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
    parser.add_argument(
        "--table",
        action="store_true",
        help="打印 file / type / currentName / recommendedName / decision 明细表",
    )
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
        print(f"  collaborator fields           : {report['collaborator_fields_total']}")
        print(f"  bare role fields (A)          : {report['bare_role_field_count']}")
        print(f"  abbreviated type-name fields(B): {report['abbreviated_dao_field_count']}")
        print(f"  role alias fields (C, legit)  : {report['role_alias_field_count']}")
        print(f"  manual review                 : {report['manual_review_field_count']}")
        print(f"  meaningless locals (var c etc): {report['meaningless_local_count']}")
        print(f"  magic strings (enum-backed)   : {report['magic_string_count']}")
        if report["bare_role_fields"]:
            print("  --- bare role fields (A, must fix) ---")
            for item in report["bare_role_fields"]:
                print(f"    {item}")
        if report["abbreviated_fields"]:
            print("  --- abbreviated type-name fields (B, must fix) ---")
            for item in report["abbreviated_fields"]:
                print(f"    {item}")
        if report["manual_review_fields"]:
            print("  --- manual review (not counted as debt) ---")
            for item in report["manual_review_fields"]:
                print(f"    {item}")
        if report["meaningless_local_samples"]:
            print("  --- meaningless locals ---")
            for item in report["meaningless_local_samples"]:
                print(f"    {item}")
        if args.table:
            print("  --- field table ---")
            print(f"    {'file':<70} {'type':<28} {'current':<16} {'recommended':<22} decision")
            for row in report["field_table"]:
                print(
                    f"    {row['file']:<70} {row['type']:<28} {row['currentName']:<16} "
                    f"{row['recommendedName']:<22} {row['decision']}"
                )
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
