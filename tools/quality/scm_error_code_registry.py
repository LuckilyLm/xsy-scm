"""Keep SCM error-code collisions at the recorded compatibility baseline.

Published numbers are not renumbered by this guard. Every new enum under the
production SCM package is scanned from source, so a new domain cannot disappear
behind a stale classpath fallback list.
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm"
BASELINE = ROOT / "tools/quality/baseline/scm-error-code-collisions.json"
ENTRY = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\((\d{5}),", re.MULTILINE)


def collect() -> dict[str, list[str]]:
    entries: dict[str, list[str]] = defaultdict(list)
    for path in sorted(SOURCE.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        if not re.search(r"\bimplements\s+ScmErrorCode\b", source):
            continue
        for name, code in ENTRY.findall(source):
            entries[code].append(f"{path.stem}.{name}")
    if not entries:
        raise RuntimeError(f"SCM error-code source set is empty: {SOURCE}")
    return {code: sorted(names) for code, names in sorted(entries.items()) if len(names) > 1}


def main() -> int:
    expected = json.loads(BASELINE.read_text(encoding="utf-8"))
    actual = collect()
    if actual != expected:
        for code in sorted(set(actual) | set(expected)):
            if actual.get(code) != expected.get(code):
                print(f"{code}: baseline={expected.get(code)} current={actual.get(code)}")
        print("Update the compatibility plan before changing published error codes.", file=sys.stderr)
        return 1
    print(f"SCM error-code registry: {len(actual)} recorded legacy collisions, no drift")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
