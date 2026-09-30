#!/usr/bin/env python3
"""Prevent common Java source from depending on SCM business domains."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import quality_guard

ROOT = Path(__file__).resolve().parents[2]
COMMON_SOURCE_ROOT = ROOT / "xsy-scm-server" / "sa-admin" / "src" / "main" / "java" / "com" / "xsy" / "scm" / "common"

FORBIDDEN_DOMAINS = (
    "product", "customer", "supplier", "pricing", "order", "purchase", "inventory", "sorting",
    "delivery", "finance", "report", "dashboard", "screen", "warehouse",
)
_DOMAIN = "|".join(re.escape(domain) for domain in FORBIDDEN_DOMAINS)
IMPORT = re.compile(
    rf"^\s*import\s+(?:static\s+)?com\.xsy\.scm\.(?P<domain>{_DOMAIN})\."
    r"(?:[A-Za-z_$][\w$]*|\*)+(?:\.(?:[A-Za-z_$][\w$]*|\*))?\s*;",
    re.MULTILINE,
)
QUALIFIED_REFERENCE = re.compile(
    rf"\bcom\.xsy\.scm\.(?P<domain>{_DOMAIN})\."
    r"(?:[A-Za-z_$][\w$]*|\*)(?:\.(?:[A-Za-z_$][\w$]*|\*))*"
)
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')


def violations_in_source(text: str, relative_path: str = "Fixture.java") -> list[str]:
    """Return forbidden references found in Java code, ignoring comments and strings."""
    source = quality_guard.JavaSource(Path(relative_path), relative_path, text)
    code = STRING_LITERAL.sub('""', source.code)
    violations: set[tuple[int, str, str]] = set()

    for match in IMPORT.finditer(code):
        line = code.count("\n", 0, match.start()) + 1
        violations.add((line, match.group("domain"), "import"))
    for match in QUALIFIED_REFERENCE.finditer(code):
        line = code.count("\n", 0, match.start()) + 1
        kind = "fully-qualified reference"
        line_start = code.rfind("\n", 0, match.start()) + 1
        prefix = code[line_start:match.start()]
        if re.search(r"\bimport\s+(?:static\s+)?$", prefix):
            kind = "import"
        violations.add((line, match.group("domain"), kind))

    return [
        f"{relative_path}:{line}: forbidden {kind} to com.xsy.scm.{domain}"
        for line, domain, kind in sorted(violations)
    ]


def scan_tree(common_source_root: Path = COMMON_SOURCE_ROOT) -> list[str]:
    if not common_source_root.is_dir():
        raise OSError(f"SCM common production source root is missing: {common_source_root}")
    source_files = sorted(common_source_root.rglob("*.java"))
    if not source_files:
        raise ValueError(f"SCM common production source root is empty: {common_source_root}")
    findings: list[str] = []
    for path in source_files:
        relative_path = path.relative_to(common_source_root.parent).as_posix()
        findings.extend(violations_in_source(path.read_text(encoding="utf-8", errors="replace"), relative_path))
    return findings


def check() -> int:
    try:
        findings = scan_tree()
    except (OSError, ValueError) as error:
        print(f"common source guard: {error}", file=sys.stderr)
        return 1
    if findings:
        print("common source guard: FAIL")
        for finding in findings:
            print(f"  {finding}")
        return 1
    print("common source guard: PASS (no business-domain references from common)")
    return 0


if __name__ == "__main__":
    raise SystemExit(check())
