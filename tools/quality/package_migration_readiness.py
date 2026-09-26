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
import re
import shutil
import subprocess
import sys
from pathlib import Path

import quality_guard as guard

ROOT = guard.ROOT
POM = guard.SERVER / "pom.xml"
ARCHITECTURE_TEST = (
    guard.SERVER / "sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/ScmArchitectureTest.java"
)
STOCKTAKE_IT = (
    guard.SERVER
    / "sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/inventory/ScmStocktakeImportPgIT.java"
)
W5_BASE = guard.SERVER / "sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/common/ScmW5PgITBase.java"
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
        guard.SCM_PACKAGE_PATHS = (guard.NEW_SCM_PACKAGE_PATH,)
        if guard.scm_main_sources():
            check.failures.append("com/xsy/scm unexpectedly already holds production sources")
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
    source = text(ARCHITECTURE_TEST)
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
    source = text(ARCHITECTURE_TEST)
    if "belongToAnyOf(OrderIdempotencyService.class)" not in source:
        check.failures.append("the class-level allowlist is gone or renamed")
    for widening in ("resideInAnyPackage(\"..scm.finance..\")", "resideInAPackage(\"..finance..\")"):
        if widening in source:
            check.failures.append(f"exception widened to a package: {widening}")
    return check


# --------------------------------------------------------------------- precondition 4-7


def check_baseline_migration_tooling() -> Check:
    check = Check("baseline-migration-tool", "baseline 路径迁移工具存在且映射覆盖 main+test")
    if not MIGRATION_TOOL.is_file():
        check.failures.append("tools/quality/migrate_baseline_paths.py is missing")
        return check
    if not MIGRATION_SELFTEST.is_file():
        check.failures.append("the rewriter's self-test is missing")
        return check
    module = __import__("migrate_baseline_paths")
    if len(module.DEFAULT_MAPPINGS) != 2:
        check.failures.append("expected exactly one mapping per SCM source root (main + test)")
    source_roots = (guard.relative(guard.MAIN_SOURCE_ROOT), guard.relative(guard.TEST_SOURCE_ROOT))
    covered: set[str] = set()
    for from_prefix, to_prefix in module.DEFAULT_MAPPINGS:
        if not to_prefix.endswith("com/xsy/scm/"):
            check.failures.append(f"mapping target is not the new SCM package: {to_prefix}")
        matched = [root for root in source_roots if from_prefix.startswith(root + "/")]
        if len(matched) != 1:
            check.failures.append(f"mapping source matches {len(matched)} source roots: {from_prefix}")
            continue
        covered.add(matched[0])
    for root in source_roots:
        if root not in covered:
            check.failures.append(f"no prefix mapping covers {root}; migrated files there would look new")
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
    source = text(STOCKTAKE_IT)
    if not source:
        check.failures.append("stocktake import IT not found")
        return check
    if "seedWarehouseId()" in source:
        check.failures.append("the IT still binds its fixture to the shared seed warehouse")
    if "fixtureWarehouseId" not in source or "newWarehouse(" not in source:
        check.failures.append("no dedicated warehouse override found")
    if "@Transactional" not in text(W5_BASE):
        check.failures.append("the IT base no longer rolls back per method; fixtures would leak")
    return check


def check_probe_is_configured() -> Check:
    check = Check("probe-evidence", "com.xsy.scm 能被三个门禁识别（--with-probe 可复现）")
    if not READINESS_DOC.is_file():
        check.failures.append("docs/quality/package-migration-readiness.md is missing")
    return check


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
    return [
        check_guard_scans_both_packages(),
        check_guard_rejects_empty_source_set(),
        check_checkstyle_scans_both_packages(),
        check_archunit_analyzes_both_packages(),
        check_archunit_finance_exception_stays_exact(),
        check_baseline_migration_tooling(),
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
    args = parser.parse_args()

    checks = build_checks()
    print("=== Q1 package migration readiness ===")
    for check in checks:
        status = "PASS" if not check.failures else "FAIL"
        print(f"[{status}] {check.name}: {check.requirement}")
        for failure in check.failures:
            print(f"       - {failure}")

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
