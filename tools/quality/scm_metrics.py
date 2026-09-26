#!/usr/bin/env python3
"""Informational metrics for the SCM Java quality audit.

``quality_guard.py`` answers "may this be committed". This module answers
"how much is there", because the remediation plan (§28) requires the audit report
to carry real numbers, and every number has to name its scan scope, its
exclusions and its detection rule.

Nothing here is a gate. Deliberately: enum inventories, class sizes and
abbreviation counts need human adjudication before they can block anything, and
a gate tuned to pass is worse than no gate.

Run with ``--json`` to machine-read the result.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

import quality_guard as guard
from java_source import JavaSource

ROOT = guard.ROOT
SERVER = guard.SERVER
SA_ADMIN_MAIN = SERVER / "sa-admin/src/main/java"
# 度量范围与门禁一致：Q1 迁移期新旧两个包都要统计，否则「已迁移部分」在审计数字里隐身。
LEGACY_MAIN_ROOT = guard.MAIN_SOURCE_ROOT / guard.LEGACY_SCM_PACKAGE_PATH
LEGACY_TEST_ROOT = guard.TEST_SOURCE_ROOT / guard.LEGACY_SCM_PACKAGE_PATH
# 自定义 SQL 目前落在两套目录约定下（mapper/business/scm/** 与 mapper/scm/**）。
# 只统计后者会把 Q1 迁包影响面少算 18 个文件，所以这里扫整个 mapper 树。
MAPPER_ROOT = SERVER / "sa-admin/src/main/resources/mapper"

LEGACY_SCM_PACKAGE = "net.lab1024.sa.admin.module.scm"

# 计划 §10：这些约束注解的 defaultMessage 会直接成为客户端看到的提示文本
# （GlobalExceptionHandler 把 FieldError.defaultMessage 原样拼接返回），
# 所以缺 message 是正式质量债务。@Valid / @Validated 不表达取值约束，不在表内。
CONSTRAINT_ANNOTATIONS = (
    "NotNull",
    "NotBlank",
    "NotEmpty",
    "Size",
    "Min",
    "Max",
    "Positive",
    "PositiveOrZero",
    "Negative",
    "NegativeOrZero",
    "DecimalMin",
    "DecimalMax",
    "Pattern",
    "Past",
    "Future",
    "PastOrPresent",
    "FutureOrPresent",
    "Digits",
    "AssertTrue",
    "AssertFalse",
)

ENUM_LIKE_PATTERN = re.compile(r'^\s*regexp\s*=\s*"[A-Z0-9_]+(\|[A-Z0-9_]+)+"')
ALL_CAPS_TOKEN = re.compile(r"^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$")
ENUM_DECLARATION = re.compile(r"\benum\s+([A-Z]\w*)")
FIELD_DECLARATION = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:static\s+|final\s+|transient\s+|volatile\s+)*"
    r"(?P<type>[A-Z][\w.]*(?:<[^;=]*>)?)\s+(?P<name>[a-z]\w*)\s*(?:=[^;]*)?;\s*$"
)
METHOD_DECLARATION = re.compile(
    r"^\s*(?P<modifier>public|private|protected)\s+(?:static\s+|final\s+|synchronized\s+)*"
    r"(?:<[^>]+>\s+)?[\w.<>\[\],?\s]+?\s+(?P<name>\w+)\s*\("
)
SINGLE_LETTER_LOCAL = re.compile(r"\bvar\s+(?P<name>[a-z])\s*=")
SINGLE_LETTER_LAMBDA = re.compile(r"\(\s*(?P<name>[a-z])\s*\)\s*->|(?<![\w.)])\s(?P<bare>[a-z])\s*->")
FORBIDDEN_WRAPPER_UTILS = re.compile(r"\b(Scm|Xsy)(String|Object|Collection|Map|Date|Number)Utils?\b")
REINVENTED_HELPER = re.compile(
    r"^\s*private\s+static\s+[\w<>\[\],.?\s]+\s+"
    r"(?P<name>trimToNull|trimToEmpty|isBlank|isNotBlank|isEmpty|isNotEmpty|nullToZero|"
    r"defaultString|join|split|capitalize)\s*\("
)

LOC_REVIEW_TRIGGER = 400
DEPENDENCY_REVIEW_TRIGGER = 10
METHOD_REVIEW_TRIGGER = 15


@dataclass
class ClassMetrics:
    """Size and coupling of one production class, used only to trigger review."""

    path: str
    name: str
    lines: int
    dependencies: int
    public_methods: int
    private_methods: int

    @property
    def review_triggers(self) -> list[str]:
        triggers = []
        if self.lines > LOC_REVIEW_TRIGGER:
            triggers.append(f">{LOC_REVIEW_TRIGGER} lines")
        if self.dependencies > DEPENDENCY_REVIEW_TRIGGER:
            triggers.append(f">{DEPENDENCY_REVIEW_TRIGGER} injected dependencies")
        if self.public_methods + self.private_methods > METHOD_REVIEW_TRIGGER:
            triggers.append(f">{METHOD_REVIEW_TRIGGER} methods")
        return triggers


@dataclass
class Metrics:
    """Every number the audit report needs, each tagged with how it was obtained."""

    scope: dict = field(default_factory=dict)
    counts: dict = field(default_factory=dict)
    lists: dict = field(default_factory=dict)
    top: dict = field(default_factory=dict)
    samples: dict = field(default_factory=dict)
    rules: dict = field(default_factory=dict)


def _domain_of(relative_path: str) -> str:
    """The SCM domain a file belongs to: the package segment right after ``scm``."""
    for marker in ("/module/scm/", "/com/xsy/scm/", "/xsy/scm/"):
        if marker in relative_path:
            parts = relative_path.split(marker, 1)[1].split("/")
            return parts[0] if len(parts) > 1 else "(scm root)"
    return "(outside scm)"


def load_sources(roots: list[Path]) -> list[JavaSource]:
    return guard.load_sources(roots)


# ------------------------------------------------------------------ structure & enums


def measure_structure(metrics: Metrics, main: list[JavaSource], test: list[JavaSource]) -> None:
    metrics.counts["scm_main_java_files"] = len(main)
    metrics.counts["scm_test_java_files"] = len(test)
    metrics.counts["scm_main_loc"] = sum(source.line_count for source in main)
    metrics.counts["scm_test_loc"] = sum(source.line_count for source in test)
    mapper_files = sorted(MAPPER_ROOT.rglob("*.xml")) if MAPPER_ROOT.is_dir() else []
    metrics.counts["mapper_xml_files_total"] = len(mapper_files)
    metrics.counts["mapper_xml_files_referencing_scm"] = sum(
        1
        for xml in mapper_files
        if LEGACY_SCM_PACKAGE in xml.read_text(encoding="utf-8", errors="replace")
    )
    metrics.lists["mapper_xml_files_per_location"] = dict(
        sorted(
            Counter(
                xml.relative_to(MAPPER_ROOT).as_posix().split("/", 2)[0]
                for xml in mapper_files
                if LEGACY_SCM_PACKAGE in xml.read_text(encoding="utf-8", errors="replace")
            ).items()
        )
    )
    metrics.lists["files_per_domain"] = dict(
        sorted(Counter(_domain_of(s.relative_path) for s in main).items())
    )


def measure_enums(metrics: Metrics, main: list[JavaSource]) -> None:
    vocabulary = guard.enum_vocabulary(main)
    declarations: dict[str, str] = {}
    for source in main:
        for name in ENUM_DECLARATION.findall(source.code):
            declarations[name] = source.relative_path
    error_code_enums = {n for n in declarations if n.endswith("ErrorCode")}
    metrics.counts["scm_enum_declarations"] = len(declarations)
    metrics.counts["scm_enum_declarations_excluding_error_code"] = (
        len(declarations) - len(error_code_enums)
    )
    metrics.counts["scm_enum_constant_names"] = len(vocabulary)
    metrics.lists["enums_per_domain"] = dict(
        sorted(Counter(_domain_of(p) for p in declarations.values()).items())
    )
    # 同一常量名由多个 Enum 声明：计划 §8 要查的「同一业务概念多个 Enum」的直接证据。
    shared = {name: sorted(owners) for name, owners in vocabulary.items() if len(owners) > 1}
    metrics.counts["scm_constants_declared_by_multiple_enums"] = len(shared)
    metrics.samples["constants_in_multiple_enums"] = dict(
        sorted(shared.items(), key=lambda kv: (-len(kv[1]), kv[0]))[:20]
    )
    metrics.samples["unused_enums"] = find_unused_enums(main, declarations)


def find_unused_enums(main: list[JavaSource], declarations: dict[str, str]) -> list[str]:
    """Enum types never referenced by simple name from any other SCM production file."""
    bodies = {source.relative_path: source.code for source in main}
    unused = []
    for name, declaring_file in sorted(declarations.items()):
        reference = re.compile(r"\b" + re.escape(name) + r"\b")
        used = any(
            reference.search(body)
            for path, body in bodies.items()
            if path != declaring_file
        )
        if not used:
            unused.append(name)
    return unused


# ---------------------------------------------------------------------- magic strings


STATUS_SETTER_WITH_LITERAL = re.compile(r"\bset(?:Status|Type|Source|Method|Direction|EntryType)\s*\(\s*\"")
BARE_DEPENDENCY_NAMES = guard.GENERIC_DEPENDENCY_NAMES


def measure_magic_strings(
    metrics: Metrics, main: list[JavaSource], vocabulary: dict[str, set[str]]
) -> None:
    """Category A is gated by the guard; B and C are reported for adjudication only.

    ``status_setters_passed_a_string_literal`` is the most legible evidence for
    category A: it counts the calls where an enum-typed-looking API was handed a
    raw literal instead of the constant.
    """
    category_a = Counter()
    category_b = Counter()
    literal_setters = 0
    for source in main:
        for literal in source.string_literals:
            if literal.text_block:
                continue
            value = literal.value
            if value in vocabulary:
                category_a[value] += 1
            elif ALL_CAPS_TOKEN.match(value) and len(value) > 2:
                category_b[value] += 1
        literal_setters += len(STATUS_SETTER_WITH_LITERAL.findall(source.code))
    metrics.counts["magic_string_with_existing_enum_occurrences"] = sum(category_a.values())
    metrics.counts["magic_string_with_existing_enum_distinct"] = len(category_a)
    metrics.counts["vocabulary_literal_without_enum_occurrences"] = sum(category_b.values())
    metrics.counts["vocabulary_literal_without_enum_distinct"] = len(category_b)
    metrics.counts["status_like_setters_passed_string_literal"] = literal_setters
    metrics.samples["vocabulary_literal_without_enum_top"] = dict(category_b.most_common(25))


# ------------------------------------------------------------------------ permissions


def measure_permissions(metrics: Metrics, main: list[JavaSource]) -> None:
    values: Counter[str] = Counter()
    annotation_count = 0
    files: set[str] = set()
    for source in main:
        code = source.code
        for annotation in guard.PERMISSION_ANNOTATION.finditer(code):
            annotation_count += 1
            found = guard.PERMISSION_LITERAL.findall(annotation.group("args"))
            if not found and "Constant" in annotation.group("args"):
                found = []
            for value in found:
                values[value] += 1
                files.add(source.relative_path)
    metrics.counts["permission_annotations"] = annotation_count
    metrics.counts["permission_literal_occurrences"] = sum(values.values())
    metrics.counts["permission_literal_distinct"] = len(values)
    metrics.counts["permission_files_with_literal"] = len(files)
    metrics.counts["controllers"] = sum(
        1 for source in main if source.relative_path.endswith("Controller.java")
    )
    metrics.counts["permission_values_used_more_than_once"] = sum(
        1 for count in values.values() if count > 1
    )
    metrics.lists["permission_prefixes"] = dict(
        sorted(Counter(value.split(":")[1] for value in values).items())
    )


# ------------------------------------------------------------------- bean validation


def _annotation_arguments(code: str, open_paren: int) -> str:
    """Read a balanced parenthesised argument list starting at ``open_paren``."""
    depth = 0
    for offset in range(open_paren, len(code)):
        char = code[offset]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return code[open_paren + 1 : offset]
    return code[open_paren + 1 :]


def measure_validation(metrics: Metrics, main: list[JavaSource]) -> None:
    """Count constraint annotations split by whether they carry a readable message.

    Scope is ``domain/form`` only: those are the API boundary objects Spring
    validates, and the plan (§10) frames the debt in exactly those terms.
    """
    forms = [s for s in main if "/domain/form/" in s.relative_path]
    per_annotation: dict[str, dict[str, int]] = {}
    enum_like_patterns = 0
    for source in forms:
        code = source.code
        for name in CONSTRAINT_ANNOTATIONS:
            pattern = re.compile(r"@" + name + r"\b")
            for match in pattern.finditer(code):
                end = match.end()
                arguments = ""
                while end < len(code) and code[end] in " \t\n\r":
                    end += 1
                if end < len(code) and code[end] == "(":
                    arguments = _annotation_arguments(code, end)
                entry = per_annotation.setdefault(name, {"total": 0, "with_message": 0})
                entry["total"] += 1
                if "message" in arguments:
                    entry["with_message"] += 1
                if name == "Pattern" and ENUM_LIKE_PATTERN.match(arguments):
                    enum_like_patterns += 1
    metrics.counts["form_classes"] = len(forms)
    metrics.counts["constraint_annotations_total"] = sum(v["total"] for v in per_annotation.values())
    metrics.counts["constraint_annotations_with_message"] = sum(
        v["with_message"] for v in per_annotation.values()
    )
    metrics.counts["constraint_annotations_without_message"] = (
        metrics.counts["constraint_annotations_total"]
        - metrics.counts["constraint_annotations_with_message"]
    )
    metrics.counts["pattern_repeating_enum_values"] = enum_like_patterns
    metrics.lists["constraint_annotations"] = dict(
        sorted(per_annotation.items(), key=lambda kv: -kv[1]["total"])
    )


# -------------------------------------------------------------------------- class size


def measure_classes(metrics: Metrics, main: list[JavaSource]) -> None:
    classes = []
    for source in main:
        if not source.relative_path.rsplit("/", 1)[-1].endswith(
            ("Service.java", "Manager.java", "Controller.java")
        ):
            continue
        public_methods = 0
        private_methods = 0
        dependencies = 0
        for line in source.code.splitlines():
            method = METHOD_DECLARATION.match(line)
            if method:
                if method.group("modifier") == "private":
                    private_methods += 1
                else:
                    public_methods += 1
                continue
            declaration = FIELD_DECLARATION.match(line)
            if declaration and "static" not in line.split(declaration.group("name"))[0]:
                dependencies += 1
        classes.append(
            ClassMetrics(
                source.relative_path,
                source.type_name,
                source.line_count,
                dependencies,
                public_methods,
                private_methods,
            )
        )
    flagged = [c for c in classes if c.review_triggers]
    metrics.counts["service_manager_controller_classes"] = len(classes)
    metrics.counts["classes_over_review_trigger"] = len(flagged)
    metrics.counts["classes_over_400_loc"] = sum(1 for c in classes if c.lines > LOC_REVIEW_TRIGGER)
    metrics.counts["classes_over_10_dependencies"] = sum(
        1 for c in classes if c.dependencies > DEPENDENCY_REVIEW_TRIGGER
    )
    metrics.counts["classes_over_15_methods"] = sum(
        1 for c in classes
        if c.public_methods + c.private_methods > METHOD_REVIEW_TRIGGER
    )
    metrics.top["largest_classes"] = [
        {
            "path": c.path,
            "lines": c.lines,
            "dependencies": c.dependencies,
            "methods": c.public_methods + c.private_methods,
            "triggers": c.review_triggers,
        }
        for c in sorted(flagged, key=lambda item: -item.lines)[:20]
    ]


# ---------------------------------------------------------------------------- naming


def measure_naming(metrics: Metrics, main: list[JavaSource]) -> None:
    """Abbreviation and single-letter measurements: audit input for Q2, not a gate.

    An automated rename gate has to decide which dropped prefixes are meaningful
    (``Scm`` in ``ScmDataScopeService`` is noise; ``Product`` in
    ``ProductCategoryService`` is the domain), and that judgement is per-field.
    Counting them is useful; blocking on the heuristic is not.
    """
    abbreviated: list[str] = []
    bare_named: list[str] = []
    single_letter_locals: list[str] = []
    single_letter_members: list[str] = []
    for source in main:
        for number, line in enumerate(source.code.splitlines(), start=1):
            declaration = FIELD_DECLARATION.match(line)
            if declaration:
                name = declaration.group("name")
                simple = declaration.group("type").rsplit(".", 1)[-1]
                expected = simple[0].lower() + simple[1:]
                suggestion = f"{source.type_name}#{name} -> {expected}"
                if (
                    len(name) > 1
                    and name[0].islower()
                    and expected != name
                    and expected.lower().endswith("_".join(name.split("_")).lower())
                    and len(expected) - len(name) >= 4
                ):
                    # The bare-role names are the guard's own subject; keeping them
                    # out of this list means "abbreviated" reports only what the
                    # gated rule does not already count.
                    (bare_named if name in BARE_DEPENDENCY_NAMES else abbreviated).append(suggestion)
                if len(name) == 1:
                    single_letter_members.append(f"{source.relative_path}:{number} {name}")
                continue
            local = SINGLE_LETTER_LOCAL.search(line)
            if local:
                single_letter_locals.append(f"{source.relative_path}:{number}")
                continue
            lambda_match = SINGLE_LETTER_LAMBDA.search(line)
            if lambda_match and (lambda_match.group("name") or lambda_match.group("bare")) not in {"i", "j"}:
                single_letter_locals.append(f"{source.relative_path}:{number}")

    metrics.counts["abbreviated_dependency_fields"] = len(abbreviated)
    metrics.counts["abbreviated_dependency_fields_including_bare"] = len(abbreviated) + len(bare_named)
    metrics.counts["single_letter_member_fields"] = len(single_letter_members)
    metrics.counts["single_letter_locals_or_lambdas"] = len(single_letter_locals)
    metrics.lists["abbreviated_dependency_fields_top"] = abbreviated[:30]
    metrics.lists["single_letter_locals_samples"] = single_letter_locals[:30]


# --------------------------------------------------------------------------- utilities


def measure_utilities(metrics: Metrics, main: list[JavaSource]) -> None:
    reinvented: list[str] = []
    wrapper_classes: list[str] = []
    for source in main:
        for number, line in enumerate(source.code.splitlines(), start=1):
            match = REINVENTED_HELPER.match(line)
            if match:
                reinvented.append(f"{source.relative_path}:{number} {match.group('name')}()")
        for match in FORBIDDEN_WRAPPER_UTILS.finditer(source.code):
            wrapper_classes.append(f"{source.relative_path} {match.group(0)}")
    metrics.counts["reinvented_framework_helpers"] = len(reinvented)
    metrics.counts["framework_wrapper_util_references"] = len(set(wrapper_classes))
    metrics.lists["reinvented_framework_helpers"] = reinvented
    metrics.lists["framework_wrapper_util_references"] = sorted(set(wrapper_classes))


# --------------------------------------------------------------------- package & docs


def measure_package_migration(metrics: Metrics) -> None:
    """Exact blast radius of the Q1 ``net.lab1024.sa.admin.module.scm -> com.xsy.scm`` move."""
    main_files = sorted(LEGACY_MAIN_ROOT.rglob("*.java"))
    test_files = sorted(LEGACY_TEST_ROOT.rglob("*.java"))
    # 迁移目录之外仍引用旧包名的生产/测试文件：迁包时必须一起改，否则编译失败。
    outside = [
        path
        for path in list((SA_ADMIN_MAIN).rglob("*.java"))
        if LEGACY_MAIN_ROOT not in path.parents
    ]
    outside_referencing = [
        path.relative_to(ROOT).as_posix()
        for path in outside
        if LEGACY_SCM_PACKAGE in path.read_text(encoding="utf-8", errors="replace")
    ]
    test_outside = [
        path
        for path in (SERVER / "sa-admin/src/test/java").rglob("*.java")
        if LEGACY_TEST_ROOT not in path.parents
    ]
    test_outside_referencing = [
        path.relative_to(ROOT).as_posix()
        for path in test_outside
        if LEGACY_SCM_PACKAGE in path.read_text(encoding="utf-8", errors="replace")
    ]

    mapper_files = sorted(MAPPER_ROOT.rglob("*.xml")) if MAPPER_ROOT.is_dir() else []
    fqn_in_xml: list[str] = []
    referencing_mapper_files = 0
    for xml in mapper_files:
        text = xml.read_text(encoding="utf-8", errors="replace")
        if LEGACY_SCM_PACKAGE not in text:
            continue
        referencing_mapper_files += 1
        fqn_in_xml.extend(re.findall(r"[\w.]*" + re.escape(LEGACY_SCM_PACKAGE) + r"[\w.]*", text))
    literal_files, literal_hits = [], 0
    for path in (SERVER / "sa-admin/src").rglob("*.java"):
        hits = re.findall(r'"[\w.]*' + re.escape(LEGACY_SCM_PACKAGE) + r'[\w.]*"',
                          path.read_text(encoding="utf-8", errors="replace"))
        if hits:
            literal_files.append(path.relative_to(ROOT).as_posix())
            literal_hits += len(hits)

    metrics.counts["package_migration_main_java_files"] = len(main_files)
    metrics.counts["package_migration_test_java_files"] = len(test_files)
    metrics.counts["package_migration_files_outside_scm_referencing_old_package"] = len(
        outside_referencing + test_outside_referencing
    )
    metrics.counts["package_migration_mapper_xml_files"] = referencing_mapper_files
    metrics.counts["package_migration_fqn_occurrences_in_xml"] = len(fqn_in_xml)
    metrics.counts["package_migration_distinct_fqn_in_xml"] = len(set(fqn_in_xml))
    metrics.counts["package_migration_reflection_string_files"] = len(literal_files)
    metrics.counts["package_migration_reflection_string_occurrences"] = literal_hits
    metrics.lists["package_migration_files_outside_scm"] = sorted(
        outside_referencing + test_outside_referencing
    )
    metrics.lists["package_migration_reflection_string_files"] = sorted(literal_files)


DOCS_FILES = (
    "AGENTS.md",
    "CLAUDE.md",
    "CONTRIBUTING.md",
    "README.md",
    "NOTICE.md",
    "SMARTADMIN_REFERENCE_RULES.md",
    "PROPOSAL-2026-09-18-团队技术提升方案.md",
    "docs/README.md",
    "docs/progress.md",
    "docs/decisions.md",
    "docs/quality/java-code-quality-remediation-plan.md",
    "docs/delivery-static-route-implementation.md",
    "docs/xsy-scm-wave1-8-audit-fix-plan.md",
)


def measure_docs(metrics: Metrics) -> None:
    rows = {}
    for name in DOCS_FILES:
        path = ROOT / name
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        rows[name] = {"lines": text.count("\n") + (0 if text.endswith("\n") else 1), "chars": len(text)}
    metrics.counts["root_markdown_files"] = len(list(ROOT.glob("*.md")))
    metrics.counts["docs_markdown_files"] = len(list((ROOT / "docs").rglob("*.md")))
    metrics.counts["docs_markdown_lines"] = sum(
        (ROOT / p).read_text(encoding="utf-8", errors="replace").count("\n")
        for p in (q.relative_to(ROOT).as_posix() for q in (ROOT / "docs").rglob("*.md"))
    )
    metrics.lists["document_sizes"] = rows


# ------------------------------------------------------------------------------ CLI


def build() -> Metrics:
    metrics = Metrics()
    main = guard.scm_main_sources()
    test = guard.scm_test_sources()
    metrics.scope = {
        "production": " + ".join(guard.relative(root) + "/**/*.java" for root in guard.package_roots(guard.MAIN_SOURCE_ROOT)),
        "tests": " + ".join(guard.relative(root) + "/**/*.java" for root in guard.package_roots(guard.TEST_SOURCE_ROOT)),
        "mapperXml": guard.relative(MAPPER_ROOT) + "/**/*.xml",
        "excluded": [
            "project-reference-examples/**",
            "xsy-scm-miniapp/**",
            "sa-base/** 与 module/system/**、module/support/**（SmartAdmin 底座，非本团队所有）",
            "target/**",
        ],
    }
    vocabulary = guard.enum_vocabulary(main)
    measure_structure(metrics, main, test)
    measure_enums(metrics, main)
    measure_magic_strings(metrics, main, vocabulary)
    measure_permissions(metrics, main)
    measure_validation(metrics, main)
    measure_classes(metrics, main)
    measure_naming(metrics, main)
    measure_utilities(metrics, main)
    measure_package_migration(metrics)
    measure_docs(metrics)

    findings = guard.collect(None).findings
    per_rule = Counter()
    for finding in findings:
        per_rule[finding.rule] += finding.count
    metrics.rules = dict(sorted(per_rule.items()))
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    args = parser.parse_args()

    metrics = build()
    if args.json:
        print(json.dumps(metrics.__dict__, ensure_ascii=False, indent=2))
        return 0
    for section, payload in metrics.__dict__.items():
        print("=" * 96)
        print(section.upper())
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
