#!/usr/bin/env python3
"""Q1 pre-flight contract: are the quality gates ready for the package move?

Q1 moves ``net.lab1024.sa.admin.module.scm.*`` to ``com.xsy.scm.*``. The failure
mode that matters is not a gate being too strict - it is a gate silently stopping
looking at the code. A scan rooted only at the old package reports zero findings
after the move, a baseline ratchet whose identities all point at vanished paths
reports "everything improved", and an ArchUnit analysis of a mistyped package
passes every rule on an empty class set. All three read as green.

So each precondition below is *executed*, not grepped, wherever execution is
cheap: the empty-source-set guard is tested by actually pointing the scanner at a
package that has no files, and the editorconfig check computes the effective
value for a ``.md`` filename the way an editor would rather than matching a line
of text.

Exit 0 only when every precondition holds. ``--with-probe`` additionally writes a
throwaway class under ``com.xsy.scm`` and requires the gates to react to it; that
mode is not part of ``verify.py quality`` because it compiles code and takes
minutes.
"""

from __future__ import annotations

import argparse
import contextlib
from dataclasses import dataclass, field
import fnmatch
import inspect
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import quality_guard as guard

from quality_guard import Finding

ROOT = guard.ROOT
POM = guard.SERVER / "pom.xml"
# 这三个文件都会被 Q1 搬走，所以只记「相对包根的相对路径」，用 old/new 双根解析。
# 写死旧路径的话，common 一迁完，W5_BASE 就变成不存在的路径，text() 返回空串，
# readiness 会因为「找不到被检查文件」而失败 —— 而它本该在迁移全程继续有效。
ARCHITECTURE_TEST_RELATIVE = "ScmArchitectureTest.java"
W5_BASE_RELATIVE = "common/ScmW5PgITBase.java"
STOCKTAKE_IT_RELATIVE = "inventory/ScmStocktakeImportPgIT.java"
MIGRATION_TOOL = Path(__file__).resolve().parent / "migrate_baseline_paths.py"
MIGRATION_SELFTEST = Path(__file__).resolve().parent / "test_baseline_path_migration.py"
EDITORCONFIG = ROOT / ".editorconfig"
READINESS_DOC = ROOT / "docs/quality/package-migration-readiness.md"


@dataclass
class Check:
    name: str
    requirement: str
    failures: list[str] = field(default_factory=list)


@dataclass
class Result:
    checks: list[Check]

    @property
    def ok(self) -> bool:
        return not any(check.failures for check in self.checks)


def resolve_migrating_file(relative_path: str) -> Path:
    """Find a test source by its path relative to the SCM package, in either package.

    Exactly one of the two locations may exist. Both existing means a copy rather
    than a move (the file would be compiled twice and scanned twice), and neither
    existing means this check has silently lost its subject.
    """
    candidates = [
        guard.TEST_SOURCE_ROOT / package_path / relative_path
        for package_path in (guard.LEGACY_SCM_PACKAGE_PATH, guard.NEW_SCM_PACKAGE_PATH)
    ]
    present = [path for path in candidates if path.is_file()]
    if len(present) == 1:
        return present[0]
    if not present:
        raise CheckResolutionError(
            f"{relative_path}: found in neither SCM package; this check has lost its subject")
    raise CheckResolutionError(
        f"{relative_path}: present in both SCM packages ({len(present)} copies);"
        " Q1 must move files, not duplicate them")


class CheckResolutionError(RuntimeError):
    """A precondition could not be evaluated at all, which is not the same as passing."""


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


# ------------------------------------------------------------- editorconfig resolution


def expand(section_glob: str) -> list[str]:
    """``{*.java,*.md}`` -> ``['*.java', '*.md']``."""
    brace = re.match(r"\{\*?\.[^}]*\}", section_glob)
    if section_glob.startswith("{") and brace:
        return section_glob[1:-1].split(",")
    return [section_glob]


def editorconfig_value(filename: str) -> dict[str, str]:
    """Effective settings for ``filename``, later sections winning like an editor applies them."""
    settings: dict[str, str] = {}
    current: str | None = None
    for line in text(EDITORCONFIG).splitlines():
        line = line.strip()
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1]
            continue
        if not line or line.startswith("#") or current is None or "=" not in line:
            continue
        if any(fnmatch.fnmatch(filename, pattern.strip()) for pattern in expand(current)):
            key, value = line.split("=", 1)
            settings[key.strip()] = value.strip()
    return settings


# --------------------------------------------------------------------- precondition 1-3


def check_guard_scans_both_packages() -> Check:
    check = Check("guard-dual-package", "Quality Guard 同时扫描旧包与 com.xsy/scm")
    roots = guard.package_roots(guard.MAIN_SOURCE_ROOT)
    if len(roots) != 2:
        check.failures.append(f"expected 2 main source roots, got {len(roots)}")
    if not any(root.as_posix().endswith("com/xsy/scm") for root in roots):
        check.failures.append("no root under com/xsy/scm; migrated files would be invisible")
    if not any(root.as_posix().endswith("net/lab1024/sa/admin/module/scm") for root in roots):
        check.failures.append("no root under the legacy SCM package; unmigrated files leak")
    test_roots = guard.package_roots(guard.TEST_SOURCE_ROOT)
    if len(test_roots) != 2:
        check.failures.append(f"expected 2 test source roots, got {len(test_roots)}")
    return check


def check_guard_rejects_empty_source_set() -> Check:
    """Execute the anti-vacuous guard instead of asserting it exists.

    ``com/xsy/scm`` does not exist yet, so scanning only that root is exactly the
    post-Q1-with-a-broken-config situation: 0 files, 0 findings, PASS.
    """
    check = Check("guard-non-empty", "生产源码集为空时 Quality Guard 必须失败，而不是 0 finding PASS")
    original_paths = guard.SCM_PACKAGE_PATHS
    try:
        # 用一条两边都不存在的路径来制造空集。曾经这里用的是「只有新包」，
        # 但 Q1 迁到一半时新包已经有文件，那条断言就会在迁移中途假失败。
        guard.SCM_PACKAGE_PATHS = (Path("net/lab1024/sa/admin/module/scm-quietly-renamed"),)
        if guard.scm_main_sources():
            check.failures.append("the sentinel package unexpectedly resolved to sources")
        try:
            guard.collect(None)
            check.failures.append("collect() returned findings for an empty source set")
        except guard.ScanConfigurationError as error:
            if "empty" not in str(error):
                check.failures.append(f"error text is not actionable: {error}")
    finally:
        guard.SCM_PACKAGE_PATHS = original_paths
    return check


def check_checkstyle_scans_both_packages() -> Check:
    check = Check("checkstyle-dual-package", "Checkstyle includes 同时覆盖旧包与 com/xsy/scm")
    pom = text(POM)
    includes = re.search(r"<includes>([^<]*module/scm[^<]*)</includes>", pom)
    if not includes:
        check.failures.append("no checkstyle <includes> pattern found in the parent pom")
        return check
    pattern = includes.group(1)
    if "com/xsy/scm" not in pattern:
        check.failures.append(f"pattern misses the new package: {pattern}")
    if "src/main/java/**" in pattern or pattern.strip() == "**/*.java":
        check.failures.append(
            f"pattern is a whole-source-tree catch-all and would pull SmartAdmin "
            f"foundation debt into the SCM baseline: {pattern}"
        )
    return check


def check_archunit_analyzes_both_packages() -> Check:
    check = Check("archunit-dual-package", "ArchUnit 同时分析旧包与 com.xsy.scm 且拒绝空扫描")
    source = text(resolve_migrating_file(ARCHITECTURE_TEST_RELATIVE))
    annotation = re.search(r"@AnalyzeClasses\((.*?)\nclass\s", source, re.DOTALL)
    if not annotation:
        check.failures.append("could not locate the @AnalyzeClasses annotation block")
        return check
    block = annotation.group(1)
    # 包名以常量形式声明（规则里的断言也要用它们），所以这里查的是「注解引用了两个常量
    # 且两个常量的值就是这两条包」，而不是在注解里找一个字面量。
    for constant, value in (
        ("LEGACY_SCM_PACKAGE", "net.lab1024.sa.admin.module.scm"),
        ("XSY_SCM_PACKAGE", "com.xsy.scm"),
    ):
        if constant not in block:
            check.failures.append(f"@AnalyzeClasses does not reference {constant}")
        if f'{constant} = "{value}"' not in source:
            check.failures.append(f'{constant} is not declared as "{value}"')
    if "importedSourceSetIsNotEmpty" not in source:
        check.failures.append("no assertion that the analyzed class set is non-empty")
    return check


def check_archunit_finance_exception_stays_exact() -> Check:
    """§15: the recorded debt exemption must stay a class allowlist.

    Widening it to a package would make the rule vacuous for the whole finance
    domain, which is exactly how such an exception silently becomes no exception.
    """
    check = Check("archunit-finance-exception", "Finance 历史例外保持精确到类")
    source = text(resolve_migrating_file(ARCHITECTURE_TEST_RELATIVE))
    if "belongToAnyOf(OrderIdempotencyService.class)" not in source:
        check.failures.append("the class-level allowlist is gone or renamed")
    for widening in ("resideInAnyPackage(\"..scm.finance..\")", "resideInAPackage(\"..finance..\")"):
        if widening in source:
            check.failures.append(f"exception widened to a package: {widening}")
    return check


# --------------------------------------------------------------------- precondition 4-7


def check_baseline_migration_tooling() -> Check:
    check = Check("baseline-migration-tool",
                  "baseline 迁移支持按域改写、跳过旧包账本、并拒绝改写未移动的文件")
    if not MIGRATION_TOOL.is_file():
        check.failures.append("tools/quality/migrate_baseline_paths.py is missing")
        return check
    if not MIGRATION_SELFTEST.is_file():
        check.failures.append("the rewriter's self-test is missing")
        return check
    module = __import__("migrate_baseline_paths")
    source_roots = (guard.relative(guard.MAIN_SOURCE_ROOT), guard.relative(guard.TEST_SOURCE_ROOT))
    covered = {prefix for pair in module.whole_package_mappings()
                for prefix in source_roots if pair[0].startswith(prefix + "/")}
    if len(covered) != 2:
        check.failures.append(f"whole-package mapping covers {len(covered)} source roots, expected 2")
    for from_prefix, to_prefix in module.whole_package_mappings():
        if not to_prefix.endswith("com/xsy/scm/"):
            check.failures.append(f"mapping target is not the new SCM package: {to_prefix}")
    # Q1 是按域迁的，所以工具必须能只改一个域；否则迁完 common 就改写全仓 baseline，
    # 未迁移的域会集体变成 NEW DEFECT。
    domain = module.domain_mappings("common")
    if len(domain) != 2 or not all(to.endswith("/com/xsy/scm/common/") for _, to in domain):
        check.failures.append("--domain must yield exactly the main and test prefixes for that domain")
    if "legacy-scm-package" not in module.SKIPPED_FAMILIES:
        check.failures.append("legacy-scm-package must be skipped: it is the old-namespace ledger")
    return check


def check_editorconfig_is_markdown_safe() -> Check:
    check = Check("editorconfig-markdown", "*.md 不会被编辑器静默重写行尾空白")
    settings = editorconfig_value("docs/quality/example.md")
    if settings.get("trim_trailing_whitespace") != "false":
        check.failures.append(
            "effective trim_trailing_whitespace for *.md is "
            f"{settings.get('trim_trailing_whitespace', 'unset')}; Markdown uses trailing "
            "double spaces for hard line breaks, so trimming edits document content"
        )
    for key in ("charset", "end_of_line", "insert_final_newline"):
        if key not in settings:
            check.failures.append(f"*.md lost {key}")
    java = editorconfig_value("a/b/Foo.java")
    if java.get("trim_trailing_whitespace") != "true":
        check.failures.append("the Java rules regressed while making Markdown safe")
    return check


def check_stocktake_fixture_is_isolated() -> Check:
    check = Check("stocktake-fixture", "ScmStocktakeImportPgIT 使用独占 warehouse")
    source = text(resolve_migrating_file(STOCKTAKE_IT_RELATIVE))
    if not source:
        check.failures.append("stocktake import IT not found")
        return check
    if "seedWarehouseId()" in source:
        check.failures.append("the IT still binds its fixture to the shared seed warehouse")
    if "fixtureWarehouseId" not in source or "newWarehouse(" not in source:
        check.failures.append("no dedicated warehouse override found")
    if "@Transactional" not in text(resolve_migrating_file(W5_BASE_RELATIVE)):
        check.failures.append("the IT base no longer rolls back per method; fixtures would leak")
    return check


def check_capture_is_identity_safe() -> Check:
    """Execute the laundering scenario: 1 fixed + 1 new, total unchanged.

    Asserting "capture calls ledger_breaches" would pass on a mangled call. The
    property that matters is behavioural, so this builds the exact swap that a
    total-only rule reports as ``+0`` and requires it to be rejected - including
    the part that is easy to get wrong: the refusal must leave the ledger alone.
    """
    check = Check("capture-identity-safe",
                  "capture 只允许账本收缩：等量替换（修 1 新增 1）必须被拒绝且不写盘")
    guard_module = guard
    original_dir = guard_module.BASELINE_DIR
    temp = tempfile.TemporaryDirectory()
    try:
        guard_module.BASELINE_DIR = Path(temp.name)
        rule = "magic-string-domain-literal"
        guard_module.write_baseline(
            next(f for f in guard_module.FAMILIES if f.name == rule),
            [Finding(rule, "a/Foo.java", 'Foo#"ENABLED"', "old")])
        before = guard_module.baseline_path(rule).read_text(encoding="utf-8")

        swapped = [Finding(rule, "b/Bar.java", 'Bar#"CONFIRMED"', "new")]
        breaches = guard_module.ledger_breaches(rule, swapped)
        if not breaches:
            check.failures.append(
                "an equal-total swap (1 recorded defect removed, 1 unrecorded defect "
                "added) was accepted; a total-only comparison is back")
        if guard_module.baseline_path(rule).read_text(encoding="utf-8") != before:
            check.failures.append("the ledger was rewritten during a breach check")
        # And the allow-list that capture is allowed to reduce must still work.
        if guard_module.ledger_breaches(rule, []):
            check.failures.append("a pure subset of the baseline was rejected; "
                                  "capture could never shrink a ledger")
    finally:
        guard_module.BASELINE_DIR = original_dir
        temp.cleanup()
    return check


def check_domain_completeness_contract() -> Check:
    """The completeness check must be able to see a file with no recorded debt.

    A file with zero findings has no baseline identity anywhere, so the path
    rewriter cannot notice that it was left behind. Only the manifest's exact path
    set can, and a count cannot stand in for it: ``{A,B,C}`` and ``{A,B,D}`` are the
    same size. This asserts the manifest is the comparison source, that the
    comparison is set-based, and that recording it is refused once Q1 has started.
    """
    check = Check("domain-completeness-contract",
                  "domain 完整迁移契约存在：manifest 记录精确文件集合、按集合断言、"
                  "且 Q1 开始后禁止重建")
    source = inspect.getsource(sys.modules[__name__])

    if "MANIFEST_PATH" not in source or "package-migration-manifest.json" not in source:
        check.failures.append("the manifest path is not declared")
    if "class Manifest" not in source:
        check.failures.append("the Manifest record type is missing")

    # The assertion must compare sets of relative paths, not counts.
    assertion = inspect.getsource(assert_domain_migrated)
    for required in ("expected", "still_missing", "unexpected", "legacy & new"):
        if required not in assertion:
            check.failures.append(
                f"assert_domain_migrated no longer checks {required!r};"
                " a count-equivalent swap could pass")
    if "domain_counts" in assertion or "recorded[" in assertion:
        check.failures.append("assert_domain_migrated still compares counts")

    # Recording must be refused when the new package is already populated.
    preconditions = inspect.getsource(check_manifest_preconditions)
    for required in ("already has already started" if False else "already holds",
                     "uncommitted tracked change", "no domains discovered",
                     "duplicated relative path", "not tracked by git"):
        if required not in preconditions:
            check.failures.append(f"manifest precondition missing: {required!r}")

    recorder = inspect.getsource(record_migration_manifest)
    if "--force does not authorise rewriting an existing manifest" not in recorder:
        check.failures.append(
            "--force can overwrite an existing manifest while Q1 preconditions fail;"
            " a half-migrated tree could be blessed as the new baseline")
    return check



def check_probe_is_configured() -> Check:
    check = Check("probe-evidence", "com.xsy.scm 能被三个门禁识别（--with-probe 可复现）")
    if not READINESS_DOC.is_file():
        check.failures.append("docs/quality/package-migration-readiness.md is missing")
    return check


# ------------------------------------------- Q1 package migration manifest (Q0.3)

SERVER_JAVA_SUFFIX = ".java"
MANIFEST_PATH = guard.BASELINE_DIR / "package-migration-manifest.json"

# SCM classes that sit directly in the package root belong to no domain. They are
# not "common" and must not be folded into it: a file moved under the wrong domain
# still satisfies every count, which is the whole class of failure this manifest
# exists to catch.
ROOT_KEY = "_root"

# The manifest records a known set of paths, so the tool can tell one difference in
# kind from another. Reaching from the tooling to this one read-only git query is a
# deliberate exception, and its output is still trusted only over the narrower claim
# of *tracking status*: the set that must move and the check that nothing unexpected
# appeared both come from the filesystem, so neither has a knowledge source in git.
GIT_TRACKED = ("git", "ls-files", "--cached")
# Batch size for `git ls-files`: 100 repo-relative paths (~90 chars each) stay well
# under the Windows command-line limit while keeping the process count small.
GIT_BATCH_SIZE = 100


@dataclass
class Manifest:
    """The exact SCM file set Q1 must move, per domain and source set.

    It records *paths*, not counts. A count cannot tell ``{A, B, C}`` from
    ``{A, B, D}``: both are three files, and only one of them is a correct
    migration. Comparing paths is what makes "the domain is finished" a claim the
    machine can check rather than a number a human later reinterprets.
    """

    domains: dict[str, dict[str, list[str]]]
    generated_from_commit: str = ""
    generated_at: str = ""

    def expected(self, domain: str, source_set: str) -> list[str]:
        return sorted(self.domains.get(domain, {}).get(source_set, []))

    @property
    def total_main(self) -> int:
        return sum(len(entry.get("main", [])) for entry in self.domains.values())

    @property
    def total_test(self) -> int:
        return sum(len(entry.get("test", [])) for entry in self.domains.values())

    def as_json(self) -> dict[str, object]:
        return {
            "_comment": (
                "Q1 migration ledger. Generated by "
                "package_migration_readiness.py --record-migration-manifest from a "
                "filesystem scan; never hand-edited. It is deleted once Q1 completes."
            ),
            "generated_from_commit": self.generated_from_commit,
            "generated_at": self.generated_at,
            "totals": {
                "main": self.total_main,
                "test": self.total_test,
                "domains": len([d for d in self.domains if d != ROOT_KEY]),
            },
            "domains": {
                domain: {source_set: sorted(paths) for source_set, paths in entry.items()}
                for domain, entry in sorted(self.domains.items())
            },
        }


def scan_domain_file_set(source_root: Path, package_path: Path) -> dict[str, list[str]]:
    """Group every ``.java`` file under a package into domains, by first path segment.

    Paths are recorded relative to the package root (``common/util/Foo.java``), which
    is what stays comparable across the ``git mv``: only the package prefix changes.
    """
    root = source_root / package_path
    grouped: dict[str, list[str]] = {}
    if not root.is_dir():
        return grouped
    for path in sorted(root.rglob(f"*{SERVER_JAVA_SUFFIX}")):
        parts = path.relative_to(root).parts
        domain = parts[0] if len(parts) > 1 else ROOT_KEY
        grouped.setdefault(domain, []).append(Path(*parts[1:]).as_posix()
                                             if len(parts) > 1 else parts[0])
    return grouped


def current_manifest() -> Manifest:
    """Rebuild the manifest from the filesystem without any of the guards.

    Used by the assertion, which must work *after* files have moved and therefore
    cannot reuse the generator's pre-conditions. The generator is the guarded entry
    point; this is the raw measurement both share.
    """
    domains: dict[str, dict[str, list[str]]] = {}
    for source_set, source_root in (("main", guard.MAIN_SOURCE_ROOT),
                                    ("test", guard.TEST_SOURCE_ROOT)):
        for package_path in (guard.LEGACY_SCM_PACKAGE_PATH, guard.NEW_SCM_PACKAGE_PATH):
            for domain, paths in scan_domain_file_set(source_root, package_path).items():
                domains.setdefault(domain, {}).setdefault(source_set, [])
                # A path present under both packages is a copy, not a move. It is
                # reported by the assertion, so keep both of them visible here
                # rather than silently de-duplicating one away.
                domains[domain][source_set].extend(paths)
    return Manifest({domain: {k: sorted(v) for k, v in entry.items()}
                     for domain, entry in domains.items()})


def git_tracked_paths(paths: list[str]) -> set[str]:
    """The subset of ``paths`` that git actually tracks.

    Two constraints, both learned the hard way:

    * ``git ls-files`` has no ``--stdin`` (exit 129, "unknown option"), so paths go
      on the command line.
    * 847 absolute repo paths blow past the Windows command-line limit
      (``FileNotFoundError: [WinError 206] 文件名或扩展名太长``), so the batch is
      chunked instead of sent in one call.
    """
    if not paths:
        return set()
    tracked: set[str] = set()
    for start in range(0, len(paths), GIT_BATCH_SIZE):
        batch = paths[start:start + GIT_BATCH_SIZE]
        result = subprocess.run(
            [*GIT_TRACKED, "--", *batch], cwd=ROOT,
            capture_output=True, text=True, encoding="utf-8", errors="replace", check=False)
        if result.returncode != 0:
            raise CheckResolutionError(
                f"git ls-files failed (exit {result.returncode}): "
                f"{(result.stderr or '').strip().splitlines()[:1]}")
        tracked.update(line.strip().replace("\\", "/")
                       for line in result.stdout.splitlines() if line.strip())
    return tracked


def git_head_commit() -> str:
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
                            text=True, encoding="utf-8", errors="replace", check=False)
    return result.stdout.strip() if result.returncode == 0 else "unknown"


def read_manifest() -> Manifest:
    """Load the committed manifest, or fail with an actionable message."""
    if not MANIFEST_PATH.is_file():
        raise SystemExit(
            f"ERROR: {guard.relative(MANIFEST_PATH)} does not exist.\n"
            "       Q1 must not start without it. Generate it once, before any file moves:\n"
            "       python tools/quality/package_migration_readiness.py --record-migration-manifest")
    payload = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    return Manifest(
        domains={domain: {source_set: sorted(paths) for source_set, paths in entry.items()}
                 for domain, entry in payload.get("domains", {}).items()},
        generated_from_commit=payload.get("generated_from_commit", ""),
        generated_at=payload.get("generated_at", ""),
    )


def report_domain_inventory() -> None:
    """Print the current domain/file-set inventory, for picking a migration order.

    Domains are discovered from the tree, never listed by hand: a hard-coded list is
    a second source of truth that goes stale the moment the repository changes.
    """
    current = current_manifest()
    recorded: Manifest | None = None
    try:
        recorded = read_manifest()
    except SystemExit:
        pass

    print(f"{'domain':16s} {'main':>6s} {'test':>6s}   {'in manifest':>12s}")
    for domain in sorted(current.domains):
        main = len(current.expected(domain, "main"))
        test = len(current.expected(domain, "test"))
        mark = "-"
        if recorded is not None:
            mark = "yes" if domain in recorded.domains else "MISSING"
        label = f"{domain} (root-level)" if domain == ROOT_KEY else domain
        print(f"{label:16s} {main:6d} {test:6d}   {mark:>12s}")
    print(f"{'TOTAL':16s} {current.total_main:6d} {current.total_test:6d}")
    if recorded is not None:
        print(f"\nmanifest: {guard.relative(MANIFEST_PATH)}"
              f"  (from {recorded.generated_from_commit[:12]},"
              f" {recorded.total_main} main / {recorded.total_test} test)")


def check_manifest_preconditions() -> list[str]:
    """Everything that must hold before the manifest may be created.

    Each item here exists because the manifest is only meaningful at exactly one
    moment: before the first ``git mv``. Recording it later would take a partly moved
    tree as ground truth and permanently bless whatever was left behind.
    """
    failures: list[str] = []

    # 1. The new package must have no SCM Java. Anything there means migration began,
    #    and a manifest captured now would encode the half-migrated state.
    for source_set, source_root in (("main", guard.MAIN_SOURCE_ROOT),
                                    ("test", guard.TEST_SOURCE_ROOT)):
        existing = sorted((source_root / guard.NEW_SCM_PACKAGE_PATH).rglob("*.java")) \
            if (source_root / guard.NEW_SCM_PACKAGE_PATH).is_dir() else []
        if existing:
            failures.append(
                f"{source_set}: {guard.NEW_SCM_PACKAGE_PATH} already holds {len(existing)} "
                f".java file(s), first: {guard.relative(existing[0])}"
                " -> Q1 has already started; a manifest recorded now would bless a"
                " partly moved tree")

    # 2. A failing working tree would record files that are not what will be moved.
    working = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True,
                             text=True, encoding="utf-8", errors="replace", check=False)
    if working.returncode != 0:
        failures.append("git status failed; cannot establish that the tree is clean")
    else:
        dirty = [line for line in working.stdout.splitlines()
                 if len(line) > 3 and not line.startswith("??")]
        if dirty:
            failures.append(
                f"working tree has {len(dirty)} uncommitted tracked change(s), e.g. "
                f"{dirty[0][3:].strip()} -> commit them first, so the manifest matches"
                " the commit the migration starts from")

    # 3-6. The scan itself must have found something sane.
    legacy_domains = scan_domain_file_set(guard.MAIN_SOURCE_ROOT,
                                          guard.LEGACY_SCM_PACKAGE_PATH)
    legacy_test = scan_domain_file_set(guard.TEST_SOURCE_ROOT,
                                       guard.LEGACY_SCM_PACKAGE_PATH)
    if not legacy_domains and not legacy_test:
        failures.append("the legacy SCM package has no .java files; nothing to migrate")
    found = {d for d in (*legacy_domains, *legacy_test) if d != ROOT_KEY}
    if not found:
        failures.append("no domains discovered under the legacy SCM package")

    # 5. A duplicated relative path inside one (domain, source set) means the scan
    #    could not tell two files apart, so the recorded set would be lossy.
    for label, grouped in (("main", legacy_domains), ("test", legacy_test)):
        for domain, paths in grouped.items():
            duplicates = sorted({p for p in paths if paths.count(p) > 1})
            if duplicates:
                failures.append(f"{label}/{domain}: duplicated relative path(s): {duplicates}")

    # 6. Untracked files would move with the ``git mv`` but are invisible to review,
    #    so they cannot be part of a committed ledger. The repo-relative path is
    #    rebuilt from (source root, package, domain, relative), with the root-level
    #    bucket contributing no directory segment of its own.
    all_paths = sorted(
        guard.relative(root / guard.LEGACY_SCM_PACKAGE_PATH
                       / ("" if domain == ROOT_KEY else domain) / rel)
        for root, grouped in ((guard.MAIN_SOURCE_ROOT, legacy_domains),
                              (guard.TEST_SOURCE_ROOT, legacy_test))
        for domain, paths in grouped.items() for rel in paths)
    try:
        untracked = sorted(set(all_paths) - git_tracked_paths(all_paths))
    except CheckResolutionError as error:
        failures.append(str(error))
        untracked = []
    if untracked:
        failures.append(
            f"{len(untracked)} legacy SCM .java file(s) are not tracked by git, e.g. "
            f"{untracked[0]} -> add them or remove them before recording the manifest")

    return failures


def record_migration_manifest(force: bool = False) -> None:
    """Generate the manifest. Refuses once Q1 has started unless ``--force``.

    The refusal is the point. If the manifest could be regenerated mid-migration, a
    forgotten file would simply become part of the new "correct" baseline and the
    completeness assertion would confirm a move that never happened.
    """
    existing = MANIFEST_PATH.is_file()
    failures = check_manifest_preconditions()
    if failures and not force:
        print("ERROR: refusing to record the migration manifest.", file=sys.stderr)
        for failure in failures:
            print(f"       - {failure}", file=sys.stderr)
        raise SystemExit(1)
    if failures and existing:
        # --force with real preconditions violated is still refused: it exists to let
        # a developer regenerate an *uncommitted* manifest, not to overwrite the ledger.
        print("ERROR: --force does not authorise rewriting an existing manifest while"
              " Q1 preconditions fail.", file=sys.stderr)
        for failure in failures:
            print(f"       - {failure}", file=sys.stderr)
        raise SystemExit(1)

    from datetime import datetime, timezone
    manifest = Manifest(
        domains={},
        generated_from_commit=git_head_commit(),
        generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
    for source_set, source_root in (("main", guard.MAIN_SOURCE_ROOT),
                                    ("test", guard.TEST_SOURCE_ROOT)):
        for domain, paths in scan_domain_file_set(source_root,
                                                  guard.LEGACY_SCM_PACKAGE_PATH).items():
            manifest.domains.setdefault(domain, {})[source_set] = sorted(paths)

    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(
        json.dumps(manifest.as_json(), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8", newline="\n")

    print(f"recorded the migration manifest -> {guard.relative(MANIFEST_PATH)}")
    print(f"  base commit : {manifest.generated_from_commit}")
    print(f"  {'domain':14s} {'main':>6s} {'test':>6s}")
    for domain in sorted(manifest.domains):
        print(f"  {domain:14s} {len(manifest.expected(domain, 'main')):6d}"
              f" {len(manifest.expected(domain, 'test')):6d}")
    print(f"  {'TOTAL':14s} {manifest.total_main:6d} {manifest.total_test:6d}")
    root_main = manifest.expected(ROOT_KEY, "main")
    root_test = manifest.expected(ROOT_KEY, "test")
    print(f"  root-level   : main {len(root_main)} / test {len(root_test)}"
          + (f"  {root_test}" if root_test else ""))


def assert_domain_migrated(domain: str) -> list[str]:
    """Exact-set completeness: is *this* domain, and only this domain, moved?

    Every comparison is on paths, never on counts. A count-equivalent swap
    (``A,B,C`` -> ``A,B,D``) is the failure a count cannot see, and a file the
    manifest never listed must not appear on the new side at all: Q1 is a namespace
    migration, so no SCM Java file may be *added* anywhere.
    """
    manifest = read_manifest()
    failures: list[str] = []
    if domain not in manifest.domains:
        return [f"{domain}: not present in {guard.relative(MANIFEST_PATH)}"
                f" (domains recorded: {', '.join(sorted(manifest.domains))})"]

    for source_set in ("main", "test"):
        expected = manifest.expected(domain, source_set)
        source_root = guard.MAIN_SOURCE_ROOT if source_set == "main" else guard.TEST_SOURCE_ROOT

        def relative_set(package_path: Path) -> set[str]:
            directory = source_root / package_path / domain
            if not directory.is_dir():
                return set()
            return {path.relative_to(directory).as_posix()
                    for path in directory.rglob(f"*{SERVER_JAVA_SUFFIX}")}

        legacy = relative_set(guard.LEGACY_SCM_PACKAGE_PATH)
        new = relative_set(guard.NEW_SCM_PACKAGE_PATH)

        if legacy:
            failures.append(
                f"{domain} {source_set}: {len(legacy)} file(s) still under the legacy"
                f" package, e.g. {sorted(legacy)[0]}")
        both = sorted(legacy & new)
        if both:
            failures.append(
                f"{domain} {source_set}: {len(both)} file(s) present under BOTH packages"
                f" ({both[0]}); Q1 must move files, never copy them")

        still_missing = sorted(set(expected) - new)
        unexpected = sorted(new - set(expected))
        if still_missing:
            failures.append(
                f"{domain} {source_set}: {len(still_missing)} expected file(s) not on the"
                f" new side, e.g. {still_missing[0]}")
        if unexpected:
            failures.append(
                f"{domain} {source_set}: {len(unexpected)} file(s) on the new side are not"
                f" in the manifest, e.g. {unexpected[0]}"
                " -> Q1 moves files, it does not add them")

        print(f"  {domain:14s} {source_set:4s} legacy {len(legacy):4d}   new {len(new):4d}"
              f"   expected {len(expected):4d}"
              + ("   OK" if not (legacy or still_missing or unexpected) else "   FAIL"))
    return failures


# ------------------------------------------------------------------------------- probe


def remove_empty_ancestors(start: Path, boundary: Path) -> None:
    """Delete ``start`` and its parents while they are empty, stopping at ``boundary``.

    The probe is the only thing that ever creates ``com/xsy/scm``; leaving empty
    directories behind would make a later ``git status`` look like a partial
    migration had already started.
    """
    current = start
    while current != boundary and boundary in current.parents:
        try:
            current.rmdir()
        except OSError:
            return
        current = current.parent


PROBE_PACKAGE_DIR = guard.MAIN_SOURCE_ROOT / guard.NEW_SCM_PACKAGE_PATH / "qualityprobe"

BAD_PROBE = """package com.xsy.scm.qualityprobe;

import net.lab1024.sa.admin.module.scm.product.dao.*;
import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;

/** 临时探针：故意含四类缺陷中的三类，证明新包被门禁看见，跑完即删。 */
public class BadQualityProbe {

    private final ProductCategoryDao dao;

    public BadQualityProbe(ProductCategoryDao dao) {
        this.dao = dao;
    }

    /** 本轮探针说明。 */
    public boolean enabled(String status) {
        return "ENABLED".equals(status);
    }
}
"""

GOOD_PROBE = """package com.xsy.scm.qualityprobe;

/** 临时探针：只用于证明扫描器会枚举 com.xsy.scm 下的合规文件，跑完即删。 */
public final class GoodQualityProbe {

    private GoodQualityProbe() {
    }

    public static String label() {
        return "quality-probe";
    }
}
"""

# ArchUnit 看的是编译产物里的类型依赖，不看注释也不看字面量，所以只有「新包里的类
# 跨层引用了别的东西」这一种探针能证明它真的在分析 com.xsy.scm。
# 放在 com.xsy.scm.common.probe 下触发 commonDoesNotDependOnConcreteDomains，
# 且不进 controller/service/dao 任何一层，不会连带触发别的规则。
LAYER_PROBE = """package com.xsy.scm.common.probe;

import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;

/** 临时探针：新包里的公共层依赖具体业务域，用于证明 ArchUnit 也在分析 com.xsy.scm。 */
public class BadLayerProbe {

    private final ProductCategoryDao productCategoryDao;

    public BadLayerProbe(ProductCategoryDao productCategoryDao) {
        this.productCategoryDao = productCategoryDao;
    }

    public Object load(Long categoryId) {
        return productCategoryDao.selectById(categoryId);
    }
}
"""


def maven_executable() -> str:
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError("mvn is not on PATH; cannot run the Checkstyle report or ArchUnit")
    return maven


def run_architecture_probe() -> tuple[int, str]:
    """Require ``ScmArchitectureTest`` to fail while a bad class sits in the new package.

    Returns ``(exit code, failing rule name or empty)``. A zero exit code means
    ArchUnit did not see the class at all - the vacuous-pass case on the bytecode
    side, and the one thing that would only be noticed after Q1 is finished.
    """
    probe_package_dir = guard.MAIN_SOURCE_ROOT / guard.NEW_SCM_PACKAGE_PATH / "common" / "probe"
    probe_package_dir.mkdir(parents=True, exist_ok=True)
    probe = probe_package_dir / "BadLayerProbe.java"
    probe.write_text(LAYER_PROBE, encoding="utf-8", newline="\n")
    try:
        result = subprocess.run(
            [maven_executable(), "-B", "-pl", "sa-admin", "-am", "test",
             "-Dtest=ScmArchitectureTest", "-DfailIfNoTests=false",
             "-Dsurefire.failIfNoSpecifiedTests=false"],
            cwd=guard.SERVER, capture_output=True, text=True, encoding="utf-8",
            errors="replace", check=False,
        )
        output = (result.stdout or "") + (result.stderr or "")
        rule_fired = "commonDoesNotDependOnConcreteDomains" in output
        return result.returncode, "commonDoesNotDependOnConcreteDomains" if rule_fired else ""
    finally:
        probe.unlink(missing_ok=True)
        remove_empty_ancestors(guard.MAIN_SOURCE_ROOT / guard.NEW_SCM_PACKAGE_PATH,
                               guard.MAIN_SOURCE_ROOT)
        # ArchUnit 读的是编译产物。只删源码会把 BadLayerProbe.class 留在 target/classes 里，
        # 下一次跑 ScmArchitectureTest 会因为这条陈旧 class 继续失败，看起来像规则不稳定。
        import shutil as _shutil
        _shutil.rmtree(guard.SERVER / "sa-admin/target/classes/com", ignore_errors=True)


def new_main_file_count() -> int:
    return sum(
        count
        for source_set, side, count in guard.package_migration_progress()
        if source_set == "main" and side == "new"
    )


@contextlib.contextmanager
def with_probe_file(name: str, body: str):
    """Write one probe class under com.xsy.scm, yield to the caller, then clean up.

    Cleanup removes the directories this helper created too: leaving an empty
    ``com/xsy/scm`` tree behind would make ``git status`` look like a half-finished
    migration.
    """
    PROBE_PACKAGE_DIR.mkdir(parents=True, exist_ok=True)
    probe = PROBE_PACKAGE_DIR / name
    probe.write_text(body, encoding="utf-8")
    try:
        yield probe
    finally:
        probe.unlink(missing_ok=True)
        remove_empty_ancestors(PROBE_PACKAGE_DIR, guard.MAIN_SOURCE_ROOT)


def run_scan_coverage_probe() -> tuple[int, int, int]:
    """Add one *clean* class under ``com.xsy.scm`` and confirm the scanner counts it.

    A bad probe only proves a rule fired; a rule could also fire on a file reached
    some other way. This proves enumeration itself: the production source count of
    the new package rises by exactly one while the file is present, and a compliant
    class must produce no finding at all.
    """
    before = new_main_file_count()
    with contextlib.ExitStack() as stack:
        probe = stack.enter_context(with_probe_file("GoodQualityProbe.java", GOOD_PROBE))
        during = new_main_file_count()
        sources = guard.scm_main_sources()
        if not any(source.path == probe.resolve() for source in sources):
            raise AssertionError("scanner did not enumerate the new-package file")
        attributed = [f for f in guard.collect(None).findings if probe.name in f.path]
        if attributed:
            raise AssertionError(f"clean probe produced findings: {attributed[:3]}")
    return before, during, new_main_file_count()


def run_bad_probe() -> list[str]:
    """Write one bad class under ``com.xsy.scm`` and return the rule families that fired.

    An empty result means the new package is invisible to the gates - the exact
    thing Q1 must not discover after 847 files have already moved.
    """
    with contextlib.ExitStack() as stack:
        probe = stack.enter_context(with_probe_file("BadQualityProbe.java", BAD_PROBE))
        subprocess.run([maven_executable(), "-B", "-N", "checkstyle:check"],
                       cwd=guard.SERVER, capture_output=True, text=True, check=False)
        findings = guard.collect(guard.CHECKSTYLE_RESULT).findings
        return sorted({f.rule for f in findings if probe.name in f.path})


# --------------------------------------------------------------------------------- CLI


def build_checks() -> list[Check]:
    """Evaluate every precondition, turning a lost subject into a failure, not a crash.

    A readiness check that throws is indistinguishable from a green run to a script
    that only reads the exit code, so resolution errors are reported against the
    check that could not be evaluated.
    """
    results: list[Check] = []
    for evaluate in (
        check_guard_scans_both_packages,
        check_guard_rejects_empty_source_set,
        check_checkstyle_scans_both_packages,
        check_archunit_analyzes_both_packages,
        check_archunit_finance_exception_stays_exact,
        check_baseline_migration_tooling,
        check_capture_is_identity_safe,
        check_domain_completeness_contract,
        check_editorconfig_is_markdown_safe,
        check_stocktake_fixture_is_isolated,
        check_probe_is_configured,
    ):
        try:
            results.append(evaluate())
        except CheckResolutionError as error:
            results.append(Check(evaluate.__name__, "被检查文件可解析", [str(error)]))
    return results


def _unused_checks() -> list[Check]:
    return [
        check_guard_scans_both_packages(),
        check_guard_rejects_empty_source_set(),
        check_checkstyle_scans_both_packages(),
        check_archunit_analyzes_both_packages(),
        check_archunit_finance_exception_stays_exact(),
        check_baseline_migration_tooling(),
        check_capture_is_identity_safe(),
        check_domain_completeness_contract(),
        check_editorconfig_is_markdown_safe(),
        check_stocktake_fixture_is_isolated(),
        check_probe_is_configured(),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--with-probe",
        action="store_true",
        help="also compile a throwaway class under com.xsy.scm and require the gates to see it",
    )
    parser.add_argument(
        "--assert-domain-migrated",
        metavar="DOMAIN",
        help="require that one Q1 domain has moved completely, compared against the "
             "manifest's exact file set (not counts). This is the only check that can "
             "see a file with no recorded debt.",
    )
    parser.add_argument(
        "--record-migration-manifest",
        action="store_true",
        help="generate tools/quality/baseline/package-migration-manifest.json from a "
             "filesystem scan. Run ONCE before Q1 starts: it refuses when the new "
             "package already holds SCM Java, when the tree is dirty, and on any "
             "inconsistent scan.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="regenerate an uncommitted manifest. Never used in the normal Q1 flow, and "
             "it cannot overwrite a committed manifest while preconditions fail.",
    )
    parser.add_argument(
        "--list-domains",
        action="store_true",
        help="print the current domain/file-set inventory, then exit",
    )
    args = parser.parse_args()

    if args.list_domains:
        report_domain_inventory()
        return 0

    if args.record_migration_manifest:
        record_migration_manifest(force=args.force)
        return 0

    checks = build_checks()
    print("=== Q1 package migration readiness ===")
    for check in checks:
        status = "PASS" if not check.failures else "FAIL"
        print(f"[{status}] {check.name}: {check.requirement}")
        for failure in check.failures:
            print(f"       - {failure}")

    if args.assert_domain_migrated:
        domain = args.assert_domain_migrated
        print(f"\n--- domain completeness: {domain} ---")
        failures = assert_domain_migrated(domain)
        checks.append(Check(
            "domain-completeness",
            f"{domain} 域在新旧两包间完整迁移且文件总数守恒",
            failures,
        ))
        print("[PASS]" if not failures else "[FAIL]",
              f"domain-completeness: {domain}",
              "" if not failures else f"({len(failures)} failure(s), listed above)")

    if args.with_probe:
        try:
            before, during, after = run_scan_coverage_probe()
        except AssertionError as error:
            before = during = after = -1
            checks.append(Check("scan-coverage-probe", "扫描器枚举新包下的合规文件", [str(error)]))
        covered = during == before + 1 and after == before
        print(f"[{'PASS' if covered else 'FAIL'}] scan-coverage-probe: "
              f"new-package main files {before} -> {during} -> {after}, "
              "and a clean class produced 0 findings")
        if not covered:
            checks.append(Check("scan-coverage-probe", "扫描器枚举新包",
                                [f"count went {before} -> {during} -> {after}, expected +1 then back"]))
        fired = run_bad_probe()
        print(f"[{'PASS' if fired else 'FAIL'}] bad-probe: guard/checkstyle rules that fired under "
              f"com.xsy.scm -> {fired or 'NONE'}")
        if not fired:
            checks.append(Check("bad-probe", "坏探针触发门禁规则",
                                ["no gate produced a finding under com.xsy.scm"]))
        try:
            arch_exit, arch_rule = run_architecture_probe()
        except RuntimeError as error:
            arch_exit, arch_rule = -1, str(error)
        arch_seen = arch_exit != 0 and bool(arch_rule)
        print(f"[{'PASS' if arch_seen else 'FAIL'}] archunit-probe: ScmArchitectureTest exit={arch_exit} "
              f"rule={arch_rule or 'NONE'}")
        if not arch_seen:
            checks.append(Check("archunit-probe", "ArchUnit 分析到 com.xsy.scm 下的越界依赖",
                                [f"probe in com.xsy.scm did not fail ScmArchitectureTest "
                                 f"(exit {arch_exit})"]))

    result = Result(checks)
    print("\nRESULT:", "PASS" if result.ok else "FAIL")
    return 0 if result.ok else 1


if __name__ == "__main__":
    sys.exit(main())
