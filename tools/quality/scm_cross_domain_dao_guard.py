#!/usr/bin/env python3
"""Keep cross-domain DAO access on an explicit, read-only allowlist."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

import quality_guard

ROOT = Path(__file__).resolve().parents[2]
ALLOWLIST = ROOT / "tools/quality/cross-domain-dao-allowlist.tsv"
IMPORT = re.compile(
    r"^\s*import\s+com\.xsy\.scm\.(?P<domain>[a-z][a-z0-9_]*)\.dao\.(?P<type>[A-Z]\w*|\*)\s*;",
    re.MULTILINE,
)
STRING = re.compile(r'"(?:\\.|[^"\\])*"')


@dataclass(frozen=True)
class Access:
    source: str
    target: str
    methods: frozenset[str]
    reason: str


def load_allowlist() -> dict[tuple[str, str], Access]:
    entries: dict[tuple[str, str], Access] = {}
    for line_number, line in enumerate(ALLOWLIST.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 4 or not all(fields):
            raise ValueError(f"{ALLOWLIST}:{line_number}: expected source, DAO, methods, reason")
        source, target, methods_text, reason = fields
        key = (source, target)
        if key in entries:
            raise ValueError(f"{ALLOWLIST}:{line_number}: duplicate allowlist entry {source} -> {target}")
        entries[key] = Access(source, target, frozenset(methods_text.split(",")), reason)
    return entries


def imported_accesses() -> dict[tuple[str, str], tuple[set[str], str]]:
    accesses: dict[tuple[str, str], tuple[set[str], str]] = {}
    for source in quality_guard.scm_main_sources():
        parts = source.package.split(".")
        if len(parts) < 4 or parts[:3] != ["com", "xsy", "scm"]:
            continue
        caller = parts[3]
        code = STRING.sub('""', source.code)
        for match in IMPORT.finditer(code):
            target_domain = match.group("domain")
            target_type = match.group("type")
            if target_domain in {caller, "common"}:
                continue
            relative = source.relative_path
            target = f"com.xsy.scm.{target_domain}.dao.{target_type}"
            key = (relative, target)
            if target_type == "*":
                accesses[key] = ({"wildcard-import"}, caller)
                continue
            field_pattern = re.compile(
                rf"^\s*(?:private|protected|public)\s+(?:final\s+)?"
                rf"{re.escape(target_type)}\s+(?P<name>[a-z]\w*)\s*(?:=[^;]*)?;\s*$",
                re.MULTILINE,
            )
            field_names = [match.group("name") for match in field_pattern.finditer(code)]
            methods: set[str] = set()
            if not field_names:
                methods.add("<field-not-found>")
            for field_name in field_names:
                call_pattern = re.compile(
                    rf"\b{re.escape(field_name)}\s*(?:\.\s*|::\s*)(?P<method>[A-Za-z_]\w*)"
                )
                methods.update(match.group("method") for match in call_pattern.finditer(code))
            accesses[key] = (methods, caller)
    return accesses


def check() -> int:
    try:
        allowlist = load_allowlist()
        accesses = imported_accesses()
    except (OSError, ValueError) as error:
        print(f"cross-domain DAO guard: {error}", file=sys.stderr)
        return 1

    failures: list[str] = []
    for key, (methods, caller) in sorted(accesses.items()):
        access = allowlist.get(key)
        if access is None:
            failures.append(f"unlisted {caller} -> {key[1]} from {key[0]}")
            continue
        unexpected = methods - access.methods
        if unexpected:
            failures.append(
                f"non-read-only calls {sorted(unexpected)} on {key[1]} from {key[0]}"
            )

    for key in sorted(allowlist.keys() - accesses.keys()):
        failures.append(f"stale allowlist entry {key[0]} -> {key[1]}")

    if failures:
        print("cross-domain DAO guard: FAIL")
        for failure in failures:
            print(f"  {failure}")
        return 1
    print(f"cross-domain DAO guard: PASS ({len(accesses)} read-only accesses explicitly allowed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(check())
