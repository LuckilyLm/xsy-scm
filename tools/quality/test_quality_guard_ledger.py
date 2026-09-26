#!/usr/bin/env python3
"""Self-test for the baseline ledger's identity safety.

Run: ``python tools/quality/test_quality_guard_ledger.py``

``capture`` exists to shrink a ledger. The failure this file pins down is that a
shrinking ledger can still *gain* a defect: fix one recorded defect, introduce one
new defect, and the occurrence total is unchanged, so a rule that only compares
totals reports ``+0`` and writes the new defect into the baseline. The following
``check`` then passes as well, because the new debt is now part of the baseline.

None of these cases needs a compiled tree: they build findings directly and
compare against a temporary ledger, because the property under test is the
comparison itself - "every current identity is recorded, and no count grew" -
not the scanners that produce the findings.
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import quality_guard as guard
from quality_guard import Finding


def finding(rule: str, path: str, locator: str, count: int = 1) -> Finding:
    return Finding(rule, path, locator, detail=locator, count=count)


class LedgerBreachTest(unittest.TestCase):
    """The identity comparison that decides whether a baseline may be written."""

    def setUp(self) -> None:
        self._real_dir = guard.BASELINE_DIR
        self._temp = tempfile.TemporaryDirectory()
        guard.BASELINE_DIR = Path(self._temp.name)

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real_dir
        self._temp.cleanup()

    def write(self, family: str, findings: list[Finding]) -> None:
        guard.baseline_path(family).write_text(
            "".join(f"{count}\t{identity}\n"
                    for identity, count in sorted(guard.counts_by_identity(findings).items())),
            encoding="utf-8")

    def test_equal_totals_with_a_swap_is_a_breach(self) -> None:
        """The whole bug: 1 fixed + 1 new, total unchanged, must not be allowed."""
        old = finding("magic-string-domain-literal", "a/Foo.java", 'Foo#"ENABLED"')
        new = finding("magic-string-domain-literal", "b/Bar.java", 'Bar#"CONFIRMED"')
        self.write("magic-string-domain-literal", [old])

        breaches = guard.ledger_breaches("magic-string-domain-literal", [new])

        self.assertEqual(len(breaches), 1, "the swapped-in defect must be reported")
        self.assertEqual(breaches[0].kind, "new-identity")
        # The guard against regressing to a total-only rule: the totals match.
        self.assertEqual(guard.occurrences([old]), guard.occurrences([new]))

    def test_fixing_two_and_adding_one_is_still_a_breach(self) -> None:
        """A *falling* total hides a new defect just as well as an unchanged one."""
        old_one = finding("stage-comment", "a/Foo.java", "Wave <n>")
        old_two = finding("stage-comment", "a/Bar.java", "本轮")
        new = finding("stage-comment", "b/Baz.java", "下一阶段")
        self.write("stage-comment", [old_one, old_two])

        breaches = guard.ledger_breaches("stage-comment", [new])

        self.assertEqual([breach.kind for breach in breaches], ["new-identity"])
        self.assertLess(guard.occurrences([new]), guard.occurrences([old_one, old_two]))

    def test_a_pure_subset_is_allowed(self) -> None:
        """What capture is actually for: dropping records, not swapping them."""
        old = finding("stage-comment", "a/Foo.java", "Wave <n>")
        kept = finding("stage-comment", "a/Bar.java", "本轮")
        self.write("stage-comment", [old, kept])

        self.assertEqual(guard.ledger_breaches("stage-comment", [kept]), [])

    def test_an_empty_baseline_blocks_everything(self) -> None:
        """A wiped ledger must not be re-seeded one finding at a time."""
        breaches = guard.ledger_breaches(
            "stage-comment", [finding("stage-comment", "a/Foo.java", "本轮")])
        self.assertEqual([breach.kind for breach in breaches], ["new-identity"])

    def test_a_grown_count_is_a_breach(self) -> None:
        grew = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=3)
        self.write("checkstyle", [finding("checkstyle", "a/Foo.java", "AvoidStarImport")])

        breaches = guard.ledger_breaches("checkstyle", [grew])

        self.assertEqual(len(breaches), 1)
        self.assertEqual(breaches[0].kind, "grown-count")
        self.assertEqual((breaches[0].allowed, breaches[0].current), (1, 3))

    def test_a_repeated_checkstyle_line_is_not_a_breach(self) -> None:
        """One file can legitimately report the same check twice; count is the point."""
        allowed = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=2)
        self.write("checkstyle", [allowed])

        self.assertEqual(
            guard.ledger_breaches("checkstyle",
                                  [finding("checkstyle", "a/Foo.java",
                                           "AvoidStarImport", count=2)]),
            [])


class CaptureRefusalTest(unittest.TestCase):
    """``capture`` must refuse, and must write nothing, when a ledger would grow."""

    def setUp(self) -> None:
        self._real_dir = guard.BASELINE_DIR
        self._temp = tempfile.TemporaryDirectory()
        guard.BASELINE_DIR = Path(self._temp.name)

    def tearDown(self) -> None:
        guard.BASELINE_DIR = self._real_dir
        self._temp.cleanup()

    def test_capture_does_not_write_even_the_clean_families(self) -> None:
        """A half-applied capture is worse than none: the ledger would be part-new.

        ``stage-comment`` is clean (a subset) while ``checkstyle`` grew, so the
        run must abort *before* writing anything - including the clean family.
        """
        import argparse
        import contextlib
        import io

        old_stage = finding("stage-comment", "a/Foo.java", "本轮")
        kept = finding("stage-comment", "a/Bar.java", "下一阶段")
        guard.write_baseline(
            next(f for f in guard.FAMILIES if f.name == "stage-comment"), [old_stage, kept])
        guard.write_baseline(
            next(f for f in guard.FAMILIES if f.name == "checkstyle"),
            [finding("checkstyle", "a/Foo.java", "AvoidStarImport")])
        before_stage = guard.baseline_path("stage-comment").read_text(encoding="utf-8")

        grown = finding("checkstyle", "a/Foo.java", "AvoidStarImport", count=9)
        fake_scan = guard.Scan([kept, grown], frozenset({"stage-comment", "checkstyle"}))

        original = guard.collect
        guard.collect = lambda checkstyle: fake_scan
        try:
            args = argparse.Namespace(checkstyle=None, verbose=False, limit=10,
                                      allow_growth=False)
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                code = guard.command_capture(args)
        finally:
            guard.collect = original

        self.assertEqual(code, 1, "capture must fail when a ledger would grow")
        self.assertEqual(
            guard.baseline_path("stage-comment").read_text(encoding="utf-8"), before_stage,
            "the clean family must not be rewritten by a run that failed elsewhere")


if __name__ == "__main__":
    unittest.main(verbosity=2)
