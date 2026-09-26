#!/usr/bin/env python3
"""Rewrite the path field inside quality-guard baselines, per migrated domain.

Why this exists: a baseline identity is ``rule<TAB>path<TAB>locator``. When Q1
``git mv``s a file to the new package, the recorded defect does not change - its
*address* does. Without a path-only rewrite the guard reads that as one defect
fixed plus one defect introduced, so a pure rename would be blocked by its own
ledger. The tool only substitutes a path prefix; it never re-scans the tree.

That distinction is the whole point: ``capture`` right after a move would write
every *current* finding into the baseline, including a genuinely new magic string
or raw permission added during the same commit - it would launder real new debt.
A path rewrite cannot hide those, because their identities would be new and
``check`` still fails on them.

Q1 migrates domain by domain, so the rewrite must be scoped to the domain that
actually moved. Rewriting the whole package after moving only ``common`` would
leave every unmigrated domain's baseline pointing at a path that has no file
behind it, and the next scan would report those unmigrated findings as NEW
DEFECTS - the first domain would blow up the ledger. Two independent protections:

* ``--domain`` builds main+test prefixes for exactly that domain;
* each rewritten record is verified against the working tree - the old path must
  be gone and the new path must exist - so an over-broad mapping fails instead of
  silently running ahead of the move.

Safety properties, asserted rather than assumed:

* only the path field changes - rule, locator, occurrence and family survive;
* identity count and total occurrences are identical before and after;
* two old identities collapsing onto one new identity is a hard failure, because
  that silently deletes recorded debt;
* a rewritten path that already exists in the baseline is a hard failure;
* records belonging to families that intentionally track the *old* namespace
  (``legacy-scm-package``) are skipped, never rewritten;
* dry-run is the default; ``--apply`` is the only writing mode, and it refuses to
  write when any check failed.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

import quality_guard as guard

ROOT = guard.ROOT
BASELINE_DIR = Path(__file__).resolve().parent / "baseline"
FIELD_COUNT = 4  # occurrences, rule, path, locator

MAIN_LEGACY = f"{guard.relative(guard.MAIN_SOURCE_ROOT)}/{guard.LEGACY_SCM_PACKAGE_PATH.as_posix()}/"
MAIN_NEW = f"{guard.relative(guard.MAIN_SOURCE_ROOT)}/{guard.NEW_SCM_PACKAGE_PATH.as_posix()}/"
TEST_LEGACY = f"{guard.relative(guard.TEST_SOURCE_ROOT)}/{guard.LEGACY_SCM_PACKAGE_PATH.as_posix()}/"
TEST_NEW = f"{guard.relative(guard.TEST_SOURCE_ROOT)}/{guard.NEW_SCM_PACKAGE_PATH.as_posix()}/"

# The two source roots move in lockstep, so every mapping is really a pair of
# prefixes and nobody has to retype them by hand.
ROOT_PAIRS = ((MAIN_LEGACY, MAIN_NEW), (TEST_LEGACY, TEST_NEW))

# ``legacy-scm-package`` is the ledger of files that are *still* in the old
# namespace; rewriting its paths would delete the anti-reflow guarantee. It
# shrinks by itself as files move out, and ``capture`` trims it per domain.
SKIPPED_FAMILIES = frozenset({"legacy-scm-package"})

# A baseline file is named after its family, so the family list is also the
# whitelist of files this tool may touch.
FAMILY_NAMES = frozenset(family.name for family in guard.FAMILIES)


@dataclass
class Report:
    """Everything the pass/fail judgement on a migration run depends on."""

    affected: int = 0
    unchanged: int = 0
    skipped_family: int = 0
    unmoved: list[str] = field(default_factory=list)
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
            self.unmoved
            or self.collisions
            or self.collapsed
            or self.invalid_lines
            or self.occurrences_before != self.occurrences_after
            or self.identities_before != self.identities_after
        )


def domain_mappings(domain: str) -> tuple[tuple[str, str], ...]:
    """Build the main + test prefix pair for one migrated domain.

    The domain is accepted if it lives under *either* package root. Requiring only
    the legacy one would reject the exact moment this tool exists for: after the
    ``git mv`` the old directory is gone, and a mistyped domain matches nothing in
    either tree, so the rewrite would silently do nothing.
    """
    mappings = tuple((f"{legacy}{domain}/", f"{target}{domain}/")
                     for legacy, target in ROOT_PAIRS)
    for legacy_prefix, target_prefix in mappings:
        legacy_dir, target_dir = ROOT / legacy_prefix, ROOT / target_prefix
        if not legacy_dir.is_dir() and not target_dir.is_dir():
            raise SystemExit(
                f"ERROR: no such domain in either SCM package: {domain}\n"
                f"       looked for {legacy_prefix} and {target_prefix}")
    return mappings


def whole_package_mappings() -> tuple[tuple[str, str], ...]:
    """The big-bang mapping. Only correct once *every* SCM file has moved."""
    return ROOT_PAIRS


def parse_line(line: str) -> tuple[int, str, str, str] | None:
    """Split a baseline record into (occurrences, rule, path, locator)."""
    if not line.strip() or line.startswith("#"):
        return None
    parts = line.split("\t")
    if len(parts) != FIELD_COUNT:
        raise ValueError(f"expected {FIELD_COUNT} tab-separated fields, got {len(parts)}")
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


def moved_on_disk(old_path: str, new_path: str) -> tuple[bool, str]:
    """Confirm the file really moved, so the ledger cannot run ahead of Q1.

    Absent source plus present target is a move. Anything else means the rewrite
    would be describing a file that is not there yet.
    """
    source, target = ROOT / old_path, ROOT / new_path
    if source.exists():
        return False, f"still at the old path: {old_path}"
    if not target.exists():
        return False, f"nothing at the new path either: {new_path}"
    return True, ""


def migrate_file(path: Path, mappings: tuple[tuple[str, str], ...], report: Report,
               verify_moves: bool = True) -> list[str]:
    """Migrate one baseline file, recording every guardrail outcome on ``report``."""
    identities_before: set[str] = set()
    identities_after: set[str] = set()
    targets: dict[str, list[str]] = {}
    output: list[str] = []

    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        try:
            parsed = parse_line(line)
        except ValueError as error:
            report.invalid_lines.append(f"{path.name}:{number} {error}")
            output.append(line)
            continue
        if parsed is None:
            output.append(line)
            continue

        occurrences, rule, recorded_path, locator = parsed
        identity_before = f"{rule}\t{recorded_path}\t{locator}"
        identities_before.add(identity_before)
        report.occurrences_before += occurrences

        if rule in SKIPPED_FAMILIES:
            # Skipped means "kept as recorded", so it must count on both sides of
            # the preservation check; leaving it out would make a correct run look
            # like it lost occurrences.
            report.skipped_family += occurrences
            report.occurrences_after += occurrences
            identities_after.add(identity_before)
            output.append(line)
            continue

        migrated, rewritten = migrate_line(line, mappings)
        if not rewritten:
            report.unchanged += 1
            report.occurrences_after += occurrences
            identities_after.add(identity_before)
            output.append(line)
            continue

        new_occurrences, new_rule, new_path, new_locator = parse_line(migrated)
        identity_after = f"{new_rule}\t{new_path}\t{new_locator}"
        report.affected += 1
        # A prefix rewrite must be injective: two old files folding into one new
        # identity would silently delete recorded debt.
        targets.setdefault(identity_after, []).append(identity_before)
        if (new_occurrences, new_rule, new_locator) != (occurrences, rule, locator):
            report.invalid_lines.append(f"{path.name}:{number} a non-path field changed")
        if verify_moves:
            moved, reason = moved_on_disk(recorded_path, new_path)
            if not moved:
                report.unmoved.append(f"{path.name}:{number} {reason}")

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
    """The rule baseline files, never other ``.txt`` ledgers that live beside them.

    ``domain-counts.txt`` holds ``domain<TAB>corner<TAB>count`` - three fields, no
    path - and is a different kind of ledger. Picking it up here would parse it as a
    malformed baseline record and fail every migration run, which is precisely the
    outcome that looks like "the tool is broken" rather than "the glob is too wide".
    """
    return sorted(path for path in BASELINE_DIR.glob("*.txt")
                  if path.stem in FAMILY_NAMES)


def run(mappings: tuple[tuple[str, str], ...], apply_changes: bool,
    verify_moves: bool = True) -> Report:
    report = Report()
    rendered: list[tuple[Path, list[str]]] = []
    for path in baseline_files():
        output = migrate_file(path, mappings, report, verify_moves)
        report.files.append(path)
        rendered.append((path, output))
    if report.ok and apply_changes:
        for path, output in rendered:
            path.write_text("\n".join(output) + "\n", encoding="utf-8", newline="\n")
    elif apply_changes:
        print(
            "ERROR: refusing to write; the migration is not identity-preserving.\n"
            "       Narrow the mapping (use --domain <domain>) or complete the git mv first.\n"
            "       Do not hand-edit baselines to make this pass.",
            file=sys.stderr,
        )
    return report


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
    print(f"  skipped (own ledger) : {report.skipped_family}")
    print(f"  unmoved files        : {len(report.unmoved)}")
    print(f"  collisions           : {len(report.collisions)}")
    print(f"  collapsed identities : {len(report.collapsed)}")
    print(f"  invalid lines        : {len(report.invalid_lines)}")
    for label, items in (
        ("UNMOVED", report.unmoved),
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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    scope = parser.add_mutually_exclusive_group(required=True)
    scope.add_argument(
        "--domain",
        help="migrate one Q1 domain, e.g. common / product; main and test are derived together",
    )
    scope.add_argument(
        "--all",
        action="store_true",
        help="whole-package mapping; correct only after every SCM file has moved",
    )
    parser.add_argument("--from-prefix", action="append", default=[], metavar="PATH",
                        help="extra mapping source, paired positionally with --to-prefix")
    parser.add_argument("--to-prefix", action="append", default=[], metavar="PATH",
                        help="extra mapping target")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="default: report only")
    mode.add_argument("--apply", action="store_true", help="write the migrated baselines")
    return parser


def resolve_mappings(args: argparse.Namespace) -> tuple[tuple[str, str], ...]:
    mappings: list[tuple[str, str]] = []
    if args.domain:
        mappings.extend(domain_mappings(args.domain))
    elif args.all:
        mappings.extend(whole_package_mappings())
    if len(args.from_prefix) != len(args.to_prefix):
        raise SystemExit("ERROR: --from-prefix and --to-prefix must be given in equal numbers")
    mappings.extend(zip(args.from_prefix, args.to_prefix))
    return tuple(mappings)


def main() -> int:
    args = build_parser().parse_args()
    mappings = resolve_mappings(args)
    report = run(mappings, apply_changes=args.apply)
    print_report(report, mappings, applied=bool(args.apply))
    return 0 if report.ok else 1


if __name__ == "__main__":
    sys.exit(main())
