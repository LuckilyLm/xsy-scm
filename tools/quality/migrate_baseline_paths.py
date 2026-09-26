#!/usr/bin/env python3
"""Rewrite the path field inside quality-guard baselines.

Why this exists: a baseline identity is ``rule<TAB>path<TAB>locator``. When Q1
``git mv``s a file from the legacy SCM package to ``com.xsy.scm``, the recorded
defect does not change - its *address* does. Without a path-only rewrite the
guard would read that as one defect fixed and one defect introduced, so the
migration would both shrink and grow the baseline in the same commit, and the
"new defects must be zero" rule would block a rename that changes nothing.

The tool is deliberately narrower than a re-scan. It only substitutes a path
prefix; it never recomputes findings from the source tree. That distinction is
the whole point: ``capture`` after a move would wash any *genuinely new* magic
string, bad name, raw permission or stage comment into the baseline as well,
because from the guard's perspective they are all "current findings". A
deterministic path rewrite cannot hide them - those identities would be new, and
``check`` still fails on them.

Safety properties, each of which is asserted rather than assumed:

* only the path field is rewritten - rule, locator, occurrence and family survive;
* the set of identities is isomorphic before and after, so the identity count
  cannot change;
* two different old identities collapsing onto one new identity is a hard failure
  (a silent merge would delete recorded debt);
* total occurrences are identical before and after;
* a rewritten path that already exists in the baseline is a hard failure;
* dry-run is the default, ``--apply`` is the only writing mode.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from pathlib import Path
import sys

BASELINE_DIR = Path(__file__).resolve().parent / "baseline"
FIELD_COUNT = 4  # occurrences, rule, path, locator

# The two rewrites Q1 needs. Kept as data so the dry-run rehearsal and the real
# migration use the same strings instead of retyping them.
DEFAULT_MAPPINGS = (
    (
        "xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/",
        "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/",
    ),
    (
        "xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/",
        "xsy-scm-server/sa-admin/src/test/java/com/xsy/scm/",
    ),
)


@dataclass
class Report:
    """Everything ``check``-style judgement on a migration run depends on."""

    affected: int = 0
    unchanged: int = 0
    occurrences_before: int = 0
    occurrences_after: int = 0
    identities_before: int = 0
    identities_after: int = 0
    collisions: list[str] = field(default_factory=list)
    collapsed: list[str] = field(default_factory=list)
    invalid_lines: list[str] = field(default_factory=list)
    files: list[Path] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not (
            self.collisions
            or self.collapsed
            or self.invalid_lines
            or self.occurrences_before != self.occurrences_after
            or self.identities_before != self.identities_after
        )


def parse_line(line: str) -> tuple[int, str, str, str] | None:
    """Split a baseline record into (occurrences, rule, path, locator)."""
    if not line.strip() or line.startswith("#"):
        return None
    parts = line.split("\t")
    if len(parts) != FIELD_COUNT:
        raise ValueError(f"expected {FIELD_COUNT} tab-separated fields")
    occurrences, rule, path, locator = parts
    return int(occurrences), rule, path, locator


def migrate_line(line: str, mappings: tuple[tuple[str, str], ...]) -> tuple[str, bool]:
    """Return ``(line, rewritten)`` for one baseline record.

    Comments and blank lines pass through byte-for-byte: the header carries the
    rule description, which is not a path and not this tool's business.
    """
    parsed = parse_line(line)
    if parsed is None:
        return line, False
    occurrences, rule, path, locator = parsed
    for from_prefix, to_prefix in mappings:
        if path.startswith(from_prefix):
            new_path = to_prefix + path[len(from_prefix):]
            return "\t".join((str(occurrences), rule, new_path, locator)), True
    return line, False


def read_records(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def migrate_file(path: Path, mappings: tuple[tuple[str, str], ...], report: Report) -> list[str]:
    """Migrate one baseline file, recording every guardrail outcome on ``report``."""
    identities_before: set[str] = set()
    identities_after: set[str] = set()
    targets: dict[str, list[str]] = {}
    output: list[str] = []

    for number, line in enumerate(read_records(path), start=1):
        try:
            parsed = parse_line(line)
        except ValueError as error:
            report.invalid_lines.append(f"{path.name}:{number} {error}")
            output.append(line)
            continue
        if parsed is None:
            output.append(line)
            continue
        occurrences, rule, identity_path, locator = parsed
        identity_before = f"{rule}\t{identity_path}\t{locator}"
        identities_before.add(identity_before)
        report.occurrences_before += occurrences

        migrated, rewritten = migrate_line(line, mappings)
        if rewritten:
            report.affected += 1
            new_occurrences, new_rule, new_path, new_locator = parse_line(migrated)
            identity_after = f"{new_rule}\t{new_path}\t{new_locator}"
            # A prefix rewrite must be injective: two old files folding into one
            # new identity would silently delete recorded debt.
            targets.setdefault(identity_after, []).append(identity_before)
            if new_occurrences != occurrences or new_rule != rule or new_locator != locator:
                report.invalid_lines.append(f"{path.name}:{number} non-path field changed")
        else:
            report.unchanged += 1
            identity_after = identity_before

        identities_after.add(identity_after)
        report.occurrences_after += occurrences
        output.append(migrated)

    for identity_after, sources in targets.items():
        if len(sources) > 1:
            report.collapsed.append(f"{path.name}: {len(sources)} -> {identity_after}")
        if identity_after in identities_before:
            report.collisions.append(f"{path.name}: target already present: {identity_after}")

    report.identities_before += len(identities_before)
    report.identities_after += len(identities_after)
    return output


def baseline_files() -> list[Path]:
    return sorted(BASELINE_DIR.glob("*.txt"))


def print_report(report: Report, mappings: tuple[tuple[str, str], ...], applied: bool) -> None:
    print("=== Baseline path migration ===")
    for from_prefix, to_prefix in mappings:
        print(f"  {from_prefix}\n    -> {to_prefix}")
    print(f"  files                : {len(report.files)}")
    print(f"  identities before    : {report.identities_before}")
    print(f"  identities after     : {report.identities_after}")
    print(f"  occurrences before   : {report.occurrences_before}")
    print(f"  occurrences after    : {report.occurrences_after}")
    print(f"  rewritten records    : {report.affected}")
    print(f"  unchanged records    : {report.unchanged}")
    print(f"  collisions           : {len(report.collisions)}")
    print(f"  collapsed identities : {len(report.collapsed)}")
    print(f"  invalid lines        : {len(report.invalid_lines)}")
    for label, items in (
        ("COLLISION", report.collisions),
        ("COLLAPSE", report.collapsed),
        ("INVALID", report.invalid_lines),
    ):
        for item in items[:20]:
            print(f"  {label}: {item}")
        if len(items) > 20:
            print(f"  ... and {len(items) - 20} more {label.lower()}")
    print(f"\nmode: {'APPLIED' if applied else 'DRY RUN (nothing written)'}")
    print("RESULT:", "PASS" if report.ok else "FAIL")


def run(mappings: tuple[tuple[str, str], ...], apply_changes: bool) -> Report:
    report = Report()
    rendered: list[tuple[Path, list[str]]] = []
    for path in baseline_files():
        output = migrate_file(path, mappings, report)
        report.files.append(path)
        rendered.append((path, output))
    if report.ok and apply_changes:
        for path, output in rendered:
            path.write_text("\n".join(output) + "\n", encoding="utf-8")
    elif apply_changes:
        print(
            "ERROR: refusing to write; the migration is not identity-preserving.\n"
            "       Fix the prefix mapping - do not hand-edit baselines to make this pass.",
            file=sys.stderr,
        )
    return report


def parse_mappings(pairs: list[str], to: list[str]) -> tuple[tuple[str, str], ...]:
    if not pairs and not to:
        return DEFAULT_MAPPINGS
    if len(pairs) != len(to) or not pairs:
        raise SystemExit(
            "ERROR: --from-prefix and --to-prefix must be given in equal numbers "
            "(or neither, to use the default SCM main+test mapping)"
        )
    return tuple(zip(pairs, to))


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--from-prefix", action="append", default=[], metavar="PATH")
    parser.add_argument("--to-prefix", action="append", default=[], metavar="PATH")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="default: report only")
    mode.add_argument("--apply", action="store_true", help="write the migrated baselines")
    args = parser.parse_args()

    mappings = parse_mappings(args.from_prefix, args.to_prefix)
    report = run(mappings, apply_changes=args.apply)
    print_report(report, mappings, applied=bool(args.apply))
    return 0 if report.ok else 1


if __name__ == "__main__":
    sys.exit(main())
