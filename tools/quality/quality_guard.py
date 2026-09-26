#!/usr/bin/env python3
"""SCM Java quality guard.

Enforces the rules Checkstyle structurally cannot see, on the principle set out
in ``docs/quality/java-code-quality-remediation-plan.md``: historical defects may
sit in a baseline, new defects must be zero, and a baseline may only shrink.

Checkstyle answers "is this identifier camelCase". It cannot answer whether
``ProductCategoryDao dao`` says what the field is, whether ``"ENABLED".equals``
ignores an enum that already exists, whether a permission code was pasted in
instead of referenced from a catalog, or whether a comment narrates a delivery
phase instead of a business invariant. Those questions are the rules here.

Detection runs on the segmented view from :mod:`java_source`, never on raw file
text: a status word inside a Javadoc paragraph is documentation, the same word as
a String literal in a comparison is a defect, and only a scanner that knows the
difference can be trusted with a blocking gate.

Modes
-----
``scan``     print current findings and counts; never writes anything.
``capture``  rewrite the baselines of the families that were scanned.
``check``    compare against the baselines and exit non-zero on any growth.

``capture`` refuses to enlarge a baseline unless ``--allow-growth`` is given, so
"a new rule fires everywhere, just baseline it" cannot happen by accident. A
family that was not scanned is never rewritten, so forgetting ``--checkstyle``
cannot silently empty that baseline.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from java_source import JavaSource

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "xsy-scm-server"
SCM_PACKAGE_PATH = Path("net/lab1024/sa/admin/module/scm")
SCM_MAIN_ROOT = SERVER / "sa-admin/src/main/java" / SCM_PACKAGE_PATH
SCM_TEST_ROOT = SERVER / "sa-admin/src/test/java" / SCM_PACKAGE_PATH
BASELINE_DIR = Path(__file__).resolve().parent / "baseline"
CHECKSTYLE_RESULT = SERVER / "target/checkstyle-result.xml"
IMPROVEMENT_PRINT_LIMIT = 20


def relative(path: Path) -> str:
    """Repo-relative POSIX path: the only form stable across checkouts."""
    return path.resolve().relative_to(ROOT).as_posix()


# --------------------------------------------------------------------------- findings


@dataclass(frozen=True)
class Finding:
    """One defect, or one aggregate measurement of a defect class.

    ``locator`` is part of the baseline identity and must not contain a line
    number: findings have to stay matched when unrelated edits shift lines,
    otherwise every baseline goes stale on the next commit. ``line`` is only for
    the human-readable report. ``count`` lets an aggregate rule (files under the
    legacy package, Checkstyle occurrences of one check in one file) contribute
    its real multiplicity without emitting thousands of identical findings.
    """

    rule: str
    path: str
    locator: str
    detail: str
    line: int = 0
    count: int = 1

    @property
    def identity(self) -> str:
        return f"{self.rule}\t{self.path}\t{self.locator}"


def counts_by_identity(findings: list[Finding]) -> Counter[str]:
    totals: Counter[str] = Counter()
    for finding in findings:
        totals[finding.identity] += finding.count
    return totals


def occurrences(findings: list[Finding]) -> int:
    return sum(finding.count for finding in findings)


def group_by_rule(findings: list[Finding]) -> dict[str, list[Finding]]:
    grouped: dict[str, list[Finding]] = {}
    for finding in findings:
        grouped.setdefault(finding.rule, []).append(finding)
    return grouped


# ------------------------------------------------------------------ enum vocabulary

_ENUM_DECLARATION = re.compile(r"\benum\s+(?P<name>[A-Z]\w*)[^{;]*\{")
_CONSTANT_NAME = re.compile(r"^\s*(?P<name>[A-Z][A-Z0-9_]*)\b")

# Error-code enums name program identifiers, not domain states: nobody writes
# "CATEGORY_NOT_FOUND" as a String literal, so keeping them in the vocabulary
# would only widen the false-positive surface of the magic-string rule.
_NON_VOCABULARY_ENUM = re.compile(r"ErrorCode$")


def _enum_constant_region(code: str, open_brace: int) -> str:
    """The depth-1 text of an enum body, up to the first ``;`` or the closing brace.

    Everything past that separator is fields and methods, where an ALL-CAPS
    identifier is no longer an enum constant.
    """
    depth = 1
    region: list[str] = []
    for char in code[open_brace + 1 :]:
        if char in "{([":
            depth += 1
        elif char in "})]":
            depth -= 1
            if depth == 0:
                break
        elif char == ";" and depth == 1:
            break
        region.append(char)
    return "".join(region)


def _strip_nested_groups(text: str) -> str:
    """Drop parenthesised constructor arguments and per-constant bodies."""
    kept: list[str] = []
    depth = 0
    for char in text:
        if char in "({[":
            depth += 1
        elif char in ")}]":
            depth -= 1
        elif depth == 0:
            kept.append(char)
    return "".join(kept)


def _constant_names(region: str) -> set[str]:
    body = re.sub(r"@\w+", " ", _strip_nested_groups(region))
    names = set()
    for constant in body.split(","):
        match = _CONSTANT_NAME.match(constant)
        if match:
            names.add(match.group("name"))
    return names


def enum_vocabulary(sources: list[JavaSource]) -> dict[str, set[str]]:
    """Map every SCM enum constant name to the enum types that declare it."""
    vocabulary: dict[str, set[str]] = {}
    for source in sources:
        for declaration in _ENUM_DECLARATION.finditer(source.code):
            enum_name = declaration.group("name")
            if _NON_VOCABULARY_ENUM.search(enum_name):
                continue
            # The declaration pattern ends on the opening brace itself.
            open_brace = declaration.end() - 1
            for constant in _constant_names(_enum_constant_region(source.code, open_brace)):
                vocabulary.setdefault(constant, set()).add(enum_name)
    return vocabulary


# ----------------------------------------------------------------------------- rules


def load_sources(root: Path) -> list[JavaSource]:
    if not root.is_dir():
        return []
    return [JavaSource.read(path, ROOT) for path in sorted(root.rglob("*.java"))]


_FIELD_DECLARATION = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:final\s+)?"
    r"(?P<type>[A-Z][\w.]*)\s+(?P<name>[a-z]\w*)\s*(?:=[^;]*)?;\s*$"
)

# 计划 §4.10：字段类型已经给出领域名称时，禁止再用这些裸角色名。
GENERIC_DEPENDENCY_NAMES = frozenset(
    {
        "dao",
        "service",
        "query",
        "manager",
        "validator",
        "repository",
        "mapper",
        "reader",
        "writer",
        "client",
    }
)


def generic_dependency_fields(source: JavaSource) -> list[Finding]:
    """Flag ``private final ProductCategoryDao dao;`` and its siblings.

    Only single-line declarations are matched: a field whose type and name are
    split across lines is rare enough that the added parser state is not worth
    the false-negative risk it would replace.
    """
    findings = []
    for number, line in enumerate(source.code.splitlines(), start=1):
        match = _FIELD_DECLARATION.match(line)
        if not match:
            continue
        name = match.group("name")
        if name not in GENERIC_DEPENDENCY_NAMES:
            continue
        simple_type = match.group("type").rsplit(".", 1)[-1]
        if simple_type.lower() == name:
            # `Dao dao` offers no domain name to spell out, so there is nothing to fix.
            continue
        findings.append(
            Finding(
                "generic-dependency-field",
                source.relative_path,
                f"{source.type_name}#{name}:{simple_type}",
                f"{match.group('type')} {name}",
                number,
            )
        )
    return findings


def magic_string_literals(source: JavaSource, vocabulary: dict[str, set[str]]) -> list[Finding]:
    """Flag a String literal that duplicates an existing SCM enum constant.

    Only that case is gated. An ALL-CAPS literal with no enum behind it may be a
    document-number prefix, a spreadsheet header or vocabulary that still needs a
    ruling; deciding that automatically would produce debt nobody can act on.
    Those are counted by ``scm_metrics.py`` instead.
    """
    findings = []
    for literal in source.string_literals:
        if literal.text_block or literal.value not in vocabulary:
            continue
        owners = sorted(vocabulary[literal.value])
        findings.append(
            Finding(
                "magic-string-domain-literal",
                source.relative_path,
                f'{source.type_name}#"{literal.value}"',
                f'"{literal.value}" is declared by {" / ".join(owners)}',
                literal.line,
            )
        )
    return findings


PERMISSION_ANNOTATION = re.compile(r"@SaCheckPermission\s*\((?P<args>[^)]*)\)")
PERMISSION_LITERAL = re.compile(r'"(?P<value>scm:[^"]+)"')


def raw_permission_literals(source: JavaSource) -> list[Finding]:
    """Flag permission codes pasted into an annotation instead of a catalog constant.

    ``FinanceConstant`` already holds the finance catalog, so the target shape is
    an established in-repo pattern rather than a new invention.
    """
    findings = []
    code = source.code
    for annotation in PERMISSION_ANNOTATION.finditer(code):
        line = code.count("\n", 0, annotation.start()) + 1
        for literal in PERMISSION_LITERAL.finditer(annotation.group("args")):
            value = literal.group("value")
            findings.append(
                Finding(
                    "raw-permission-literal",
                    source.relative_path,
                    value,
                    f'@SaCheckPermission("{value}")',
                    line,
                )
            )
    return findings


# 计划 §9：开发过程叙述属于 docs / decisions / ADR / git history，不属于生产代码注释。
# `提交` 与 `测试` 刻意不在表内 —— 业务文本里「提交订单」「APPROVED 提交前」是正常用语，
# 把它们当阶段流水会产生无法清理的误报。
STAGE_COMMENT_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("stage-code", re.compile(r"\bF\d+-\d+[A-Za-z]?\b")),
    ("wave", re.compile(r"\bWave\s+\d+")),
    ("decision-q", re.compile(r"\bQ\d+\b")),
    ("decision-d", re.compile(r"\bD-\d+\b")),
    ("design-doc-section", re.compile(r"§\s*\d+(?:\.\d+)*")),
    ("design-doc", re.compile(r"设计稿")),
    ("this-round", re.compile(r"本轮")),
    ("next-phase", re.compile(r"下一阶段")),
    ("this-change", re.compile(r"此次")),
)

# Section and wave numbers are renumbered by whoever edits the design doc;
# folding them keeps the baseline stable while the occurrence count still grows
# when a new narration is added.
_FOLDED_STAGE_TOKENS = {"design-doc-section": "§<n>", "wave": "Wave <n>"}


def stage_comments(source: JavaSource) -> list[Finding]:
    """Flag comments that narrate a delivery phase instead of a business rule."""
    findings = []
    for comment in source.comments:
        for label, pattern in STAGE_COMMENT_PATTERNS:
            for match in pattern.finditer(comment.text):
                findings.append(
                    Finding(
                        "stage-comment",
                        source.relative_path,
                        _FOLDED_STAGE_TOKENS.get(label, match.group(0)),
                        comment.text[:120],
                        comment.line,
                    )
                )
    return findings


def legacy_package_file_count(root: Path) -> list[Finding]:
    """Measure how many files still sit under the pre-Q1 SCM package.

    Reported as one aggregate finding per source root, so the baseline stores two
    numbers instead of 846 paths. The count may only fall; Q1 replaces this rule
    with an ArchUnit rule on ``com.xsy.scm..``.
    """
    if not root.is_dir():
        return []
    files = sum(1 for path in root.rglob("*.java") if path.is_file())
    return [
        Finding(
            "legacy-scm-package",
            relative(root),
            "java-file-count",
            "files under the pre-migration SCM package",
            count=files,
        )
    ]


def checkstyle_findings(result_file: Path) -> list[Finding]:
    """Fold a ``checkstyle-result.xml`` into one aggregate finding per file and check.

    The identity excludes the message on purpose: Checkstyle renders messages in
    the JVM locale, so a baseline captured on a zh_CN machine would read as
    entirely replaced on an en_US one.
    """
    findings = []
    for file_element in ET.parse(result_file).getroot():
        path = relative(Path(file_element.get("name")))
        counts: Counter[str] = Counter()
        first_line: dict[str, int] = {}
        for error in file_element:
            check = error.get("source", "").rsplit(".", 1)[-1].removesuffix("Check")
            counts[check] += 1
            first_line.setdefault(check, int(error.get("line", "0")))
        findings.extend(
            Finding("checkstyle", path, check, f"{check} x{count}", first_line[check], count)
            for check, count in counts.items()
        )
    return findings


# ----------------------------------------------------------------------- rule registry


@dataclass(frozen=True)
class RuleFamily:
    """One baseline file and the rule description recorded in its header."""

    name: str
    title: str
    scope: str
    detection: str


FAMILIES: tuple[RuleFamily, ...] = (
    RuleFamily(
        "generic-dependency-field",
        "语义贫乏的依赖字段名",
        "SCM 生产代码（src/main/java 下 module/scm）",
        "字段声明行匹配 private/protected [final] <Type> <name>;，name 属于 "
        "{dao,service,query,manager,validator,repository,mapper,reader,writer,client}，"
        "且类型简单名小写后不等于该字段名",
    ),
    RuleFamily(
        "magic-string-domain-literal",
        "已有 Enum 却硬编码的领域字面量",
        "SCM 生产代码",
        "非 text block 的字符串字面量，内容恰好等于某个 SCM enum 常量名；"
        "*ErrorCode 枚举不计入词汇表；测试源码、SQL、文档、JSON 快照不在扫描范围",
    ),
    RuleFamily(
        "raw-permission-literal",
        "裸权限串注解",
        "SCM 生产代码",
        "@SaCheckPermission(...) 参数里出现的 \"scm:...\" 字面量，每个字面量一条；"
        "权限目录常量类里的字面量定义不算（它不是注解）",
    ),
    RuleFamily(
        "stage-comment",
        "阶段流水注释",
        "SCM 生产代码",
        "注释文本命中 F<n>-<n> / Wave <n> / Q<n> / D-<n> / §<n> / 设计稿 / 本轮 / "
        "下一阶段 / 此次；`提交`、`测试` 不在规则内（业务用语，会误报）",
    ),
    RuleFamily(
        "legacy-scm-package",
        "迁移前 SCM 包下的 Java 文件数",
        "SCM main 与 test 源根，各一条聚合记录",
        "按源根统计 .java 文件数；只允许下降。Q1 迁包后改由 ArchUnit 的 "
        "com.xsy.scm.. 规则接管",
    ),
    RuleFamily(
        "checkstyle",
        "Checkstyle 违规",
        "由 mvn -N checkstyle:check 产出的 target/checkstyle-result.xml",
        "每个 (文件, check) 一条聚合记录，occurrences = 该 check 在该文件的违规数；"
        "消息文本不参与 identity，因为 Checkstyle 按 JVM locale 输出消息",
    ),
)

PYTHON_FAMILIES = tuple(family.name for family in FAMILIES if family.name != "checkstyle")


# ----------------------------------------------------------------------------- collect


@dataclass(frozen=True)
class Scan:
    """Findings plus the families actually scanned.

    A family that was not scanned must not be rewritten or reported: running
    ``capture`` without ``--checkstyle`` would otherwise empty that baseline.
    """

    findings: list[Finding]
    scanned: frozenset[str]


def collect(checkstyle_result: Path | None) -> Scan:
    main_sources = load_sources(SCM_MAIN_ROOT)
    vocabulary = enum_vocabulary(main_sources)

    findings: list[Finding] = []
    for source in main_sources:
        findings.extend(generic_dependency_fields(source))
        findings.extend(magic_string_literals(source, vocabulary))
        findings.extend(raw_permission_literals(source))
        findings.extend(stage_comments(source))
    findings.extend(legacy_package_file_count(SCM_MAIN_ROOT))
    findings.extend(legacy_package_file_count(SCM_TEST_ROOT))
    scanned = set(PYTHON_FAMILIES)

    if checkstyle_result is not None:
        findings.extend(checkstyle_findings(checkstyle_result))
        scanned.add("checkstyle")
    return Scan(findings, frozenset(scanned))


# ---------------------------------------------------------------------------- baseline


def baseline_path(family: str) -> Path:
    return BASELINE_DIR / f"{family}.txt"


def read_baseline(family: str) -> Counter[str]:
    """Read one baseline file into ``identity -> allowed occurrences``."""
    path = baseline_path(family)
    if not path.is_file():
        return Counter()
    allowed: Counter[str] = Counter()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        count, identity = line.split("\t", 1)
        allowed[identity] += int(count)
    return allowed


def write_baseline(family: RuleFamily, findings: list[Finding]) -> int:
    """Rewrite one baseline file; returns the recorded occurrence total."""
    counts = counts_by_identity(findings)
    body = "".join(f"{count}\t{identity}\n" for identity, count in sorted(counts.items()))
    header = "\n".join(
        (
            f"# rule      : {family.name} — {family.title}",
            f"# scope     : {family.scope}",
            f"# detection : {family.detection}",
            "# identity  : rule<TAB>repo-relative path<TAB>locator（不含行号：行移动不会让 baseline 失效）",
            "# format    : occurrences<TAB>identity。只允许下降，新增必须为 0",
            f"# captured  : {occurrences(findings)} occurrences in {len(counts)} identities",
            "",
        )
    )
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)
    baseline_path(family.name).write_text(header + body, encoding="utf-8")
    return occurrences(findings)


# --------------------------------------------------------------------------- reporting


def print_summary(findings: list[Finding], verbose: bool, limit: int) -> None:
    grouped = group_by_rule(findings)
    for rule in sorted(grouped, key=lambda item: (-occurrences(grouped[item]), item)):
        rule_findings = grouped[rule]
        print(
            f"{occurrences(rule_findings):6d}  {rule}"
            f"  ({len(rule_findings)} identities, {len({f.path for f in rule_findings})} files)"
        )
        if not verbose:
            continue
        for finding in sorted(rule_findings, key=lambda item: (item.path, item.line))[:limit]:
            position = f":{finding.line}" if finding.line else ""
            suffix = f"  x{finding.count}" if finding.count > 1 else ""
            print(f"          {finding.path}{position}{suffix}  [{finding.locator}]  {finding.detail}")
        if len(rule_findings) > limit:
            print(f"          ... and {len(rule_findings) - limit} more")


def command_scan(args: argparse.Namespace) -> int:
    scan = collect(args.checkstyle)
    print_summary(scan.findings, args.verbose, args.limit)
    print(f"\ntotal occurrences: {occurrences(scan.findings)}")
    return 0


def command_capture(args: argparse.Namespace) -> int:
    scan = collect(args.checkstyle)
    grouped = group_by_rule(scan.findings)
    grew: list[str] = []
    initialised: list[str] = []
    print(f"{'family':34s} {'before':>8s} {'after':>8s}")
    for family in FAMILIES:
        if family.name not in scan.scanned:
            print(f"{family.name:34s} {'-':>8s} {'-':>8s}  (not scanned, baseline untouched)")
            continue
        established = baseline_path(family.name).is_file()
        before = sum(read_baseline(family.name).values())
        after = write_baseline(family, grouped.get(family.name, []))
        note = f"({after - before:+d})" if established else "(INITIAL CAPTURE)"
        print(f"{family.name:34s} {before:8d} {after:8d}  {note}")
        if not established:
            # The ratchet needs a committed baseline to compare against; creating the
            # first one is not "growth". Deleting a baseline to re-capture it freely
            # is visible in review, which is the intended control.
            initialised.append(family.name)
        elif after > before:
            grew.append(family.name)
    sys.stdout.flush()
    if grew and not args.allow_growth:
        print(
            "\nERROR: baseline would grow for: " + ", ".join(grew) + "\n"
            "A baseline may only shrink. Fix the new findings, or re-run with\n"
            "--allow-growth and record the reason under docs/quality/.",
            file=sys.stderr,
        )
        return 1
    if initialised:
        print(
            "\ninitial capture (no previous baseline): " + ", ".join(initialised)
            + "\ncommit these files; from now on they may only shrink."
        )
    print(f"\nbaselines written to {relative(BASELINE_DIR)}/")
    return 0


def command_check(args: argparse.Namespace) -> int:
    scan = collect(args.checkstyle)
    current = counts_by_identity(scan.findings)
    detail_by_identity = {finding.identity: finding for finding in scan.findings}

    new_identities: list[str] = []
    grown: list[tuple[str, int, int]] = []
    improvements: list[tuple[str, int, int]] = []
    per_family: list[tuple[str, int, int]] = []

    for family in FAMILIES:
        if family.name not in scan.scanned:
            continue
        allowed = read_baseline(family.name)
        current_total = sum(
            count for identity, count in current.items() if identity.startswith(family.name + "\t")
        )
        per_family.append((family.name, current_total, sum(allowed.values())))
        for identity, count in current.items():
            if not identity.startswith(family.name + "\t"):
                continue
            if identity not in allowed:
                new_identities.append(identity)
            elif count > allowed[identity]:
                grown.append((identity, allowed[identity], count))
        improvements.extend(
            (identity, count, current.get(identity, 0))
            for identity, count in allowed.items()
            if current.get(identity, 0) < count
        )

    print("=== SCM Java Quality Guard ===")
    for family, current_total, baseline_total in per_family:
        print(
            f"{family:34s} current {current_total:6d}  baseline {baseline_total:6d}"
            f"  ({current_total - baseline_total:+d})"
        )

    if new_identities:
        print(f"\n--- NEW DEFECTS (blocking, {len(new_identities)}) ---")
        _print_identities(sorted(new_identities), detail_by_identity, args.limit)
    if grown:
        print(f"\n--- GROWN DEFECTS (blocking, {len(grown)}) ---")
        for identity, allowed_count, count in sorted(grown)[: args.limit]:
            print(f"  {detail_by_identity[identity].path}  [{identity.split(chr(9))[-1]}]"
                  f"  {allowed_count} -> {count}")
        if len(grown) > args.limit:
            print(f"  ... and {len(grown) - args.limit} more")
    if improvements:
        print(f"\n--- IMPROVEMENTS (baseline can shrink, {len(improvements)}) ---")
        for identity, allowed_count, count in sorted(improvements)[:IMPROVEMENT_PRINT_LIMIT]:
            print(f"  {identity.replace(chr(9), ' | ')}  {allowed_count} -> {count}")
        if len(improvements) > IMPROVEMENT_PRINT_LIMIT:
            print(f"  ... and {len(improvements) - IMPROVEMENT_PRINT_LIMIT} more")
        print("  re-run `capture` to record the smaller baseline")

    passed = not new_identities and not grown
    print("\nRESULT:", "PASS" if passed else "FAIL")
    return 0 if passed else 1


def _print_identities(
    identities: list[str], detail_by_identity: dict[str, Finding], limit: int
) -> None:
    for identity in identities[:limit]:
        finding = detail_by_identity[identity]
        position = f":{finding.line}" if finding.line else ""
        suffix = f"  x{finding.count}" if finding.count > 1 else ""
        print(f"  {finding.path}{position}{suffix}  [{finding.locator}]  {finding.detail}")
    if len(identities) > limit:
        print(f"  ... and {len(identities) - limit} more")


# -------------------------------------------------------------------------------- CLI


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    shared = argparse.ArgumentParser(add_help=False)
    shared.add_argument(
        "--checkstyle",
        nargs="?",
        const=CHECKSTYLE_RESULT,
        type=Path,
        default=None,
        metavar="XML",
        help=f"also ratchet Checkstyle results (default: {relative(CHECKSTYLE_RESULT)})",
    )
    shared.add_argument("--verbose", action="store_true", help="list findings, not just counts")
    shared.add_argument("--limit", type=int, default=40, help="max findings printed per section")

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("scan", parents=[shared], help="report current findings")
    capture = subparsers.add_parser(
        "capture", parents=[shared], help="rewrite the baselines of the scanned families"
    )
    capture.add_argument(
        "--allow-growth",
        action="store_true",
        help="permit a larger baseline (the reason must be recorded under docs/quality/)",
    )
    subparsers.add_parser("check", parents=[shared], help="fail on any new or grown finding")
    return parser


def resolve_checkstyle(requested: Path | None) -> Path | None:
    """Validate the Checkstyle result path, so a missing run reads as a setup error."""
    if requested is None:
        return None
    if requested.is_file():
        return requested
    print(
        f"ERROR: {requested} not found.\n"
        "       run `mvn -N checkstyle:check` in xsy-scm-server first, or drop --checkstyle.",
        file=sys.stderr,
    )
    raise SystemExit(2)


def main() -> int:
    args = build_parser().parse_args()
    args.checkstyle = resolve_checkstyle(args.checkstyle)
    if args.command == "scan":
        return command_scan(args)
    if args.command == "capture":
        return command_capture(args)
    return command_check(args)


if __name__ == "__main__":
    sys.exit(main())
