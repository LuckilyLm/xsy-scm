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
MAPPER_NAMESPACE = re.compile(r'<mapper\s+namespace="(?P<name>[^"]+)"')
MAPPER_STATEMENT = re.compile(r'<(?P<kind>select|insert|update|delete)\s+id="(?P<id>[A-Za-z_]\w*)"')
BASE_MAPPER_READ_METHOD = re.compile(r"^(?:select|exists)\w*$")


@dataclass(frozen=True)
class Access:
    source: str
    target: str
    methods: frozenset[str]
    reason: str


def load_allowlist(allowlist_path: Path = ALLOWLIST) -> dict[tuple[str, str], Access]:
    entries: dict[tuple[str, str], Access] = {}
    for line_number, line in enumerate(allowlist_path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 4 or not all(fields):
            raise ValueError(f"{allowlist_path}:{line_number}: expected source, DAO, methods, reason")
        source, target, methods_text, reason = fields
        key = (source, target)
        if key in entries:
            raise ValueError(f"{allowlist_path}:{line_number}: duplicate allowlist entry {source} -> {target}")
        entries[key] = Access(source, target, frozenset(methods_text.split(",")), reason)
    return entries


def imported_accesses(root: Path = ROOT) -> dict[tuple[str, str], tuple[set[str], str]]:
    accesses: dict[tuple[str, str], tuple[set[str], str]] = {}
    source_root = root / "xsy-scm-server" / "sa-admin" / "src" / "main" / "java" / "com" / "xsy" / "scm"
    if not source_root.is_dir():
        raise OSError(f"SCM production source root is missing: {source_root}")
    source_files = sorted(source_root.rglob("*.java"))
    if not source_files:
        raise ValueError(f"SCM production source root is empty: {source_root}")
    for path in source_files:
        source = quality_guard.JavaSource.read(path, root)
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


def mapper_operations(target: str, root: Path = ROOT) -> dict[str, str]:
    """Return the SQL operation declared for each custom DAO method."""
    type_name = target.rsplit(".", 1)[-1]
    operations: dict[str, str] = {}
    mapper_root = root / "xsy-scm-server" / "sa-admin" / "src" / "main" / "resources" / "mapper"
    for mapper_file in mapper_root.rglob("*.xml"):
        source = mapper_file.read_text(encoding="utf-8")
        namespace = MAPPER_NAMESPACE.search(source)
        if namespace is None or namespace.group("name") != target:
            continue
        operations.update({match.group("id"): match.group("kind") for match in MAPPER_STATEMENT.finditer(source)})
    return operations


def non_read_only_methods(access: Access, observed_methods: set[str], root: Path = ROOT) -> list[str]:
    operations = mapper_operations(access.target, root)
    failures: list[str] = []
    for method in sorted(observed_methods):
        operation = operations.get(method)
        if operation == "select" or (operation is None and BASE_MAPPER_READ_METHOD.fullmatch(method)):
            continue
        if operation is None:
            failures.append(f"cannot prove SQL operation for {method}")
        else:
            failures.append(f"{method} maps to <{operation}>")
    return failures


def collect_failures(root: Path = ROOT, allowlist_path: Path = ALLOWLIST) -> list[str]:
    try:
        allowlist = load_allowlist(allowlist_path)
        accesses = imported_accesses(root)
    except (OSError, ValueError) as error:
        return [str(error)]

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
            continue
        for violation in non_read_only_methods(access, methods, root):
            failures.append(f"non-read-only SQL {violation} on {key[1]} from {key[0]}")

    for key in sorted(allowlist.keys() - accesses.keys()):
        failures.append(f"stale allowlist entry {key[0]} -> {key[1]}")

    return failures


def check() -> int:
    failures = collect_failures()
    if failures:
        print("cross-domain DAO guard: FAIL")
        for failure in failures:
            print(f"  {failure}")
        return 1
    print("cross-domain DAO guard: PASS (all observed cross-domain DAO calls are explicitly allowed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(check())
