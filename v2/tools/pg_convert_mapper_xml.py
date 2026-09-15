#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmartAdmin V2 baseline: MySQL -> PostgreSQL mapper XML conversion.

Transformations (mechanical, semantics-preserving):
  1. INSTR(a, b)                      -> STRPOS(a, b) > 0
     MySQL INSTR returns 1-based position (0 = not found) and is used as a
     truthy boolean. PostgreSQL requires a real boolean, so we add "> 0".
  2. DATE_FORMAT(a, '%Y-%m-%d')       -> CAST(a AS DATE)
     Both MySQL forms are used for date-granularity comparison; CAST(... AS DATE)
     is the PostgreSQL equivalent and works for column refs and bound params.
"""
import re
import sys
from pathlib import Path

ROOT = Path(r"D:/Browser Download/xsy-scm/v2/xsy-scm-v2-server")

TARGET_DIRS = [
    ROOT / "sa-base/src/main/resources",
    ROOT / "sa-admin/src/main/resources",
]

# INSTR(a, b) where a and b contain no parentheses
RE_INSTR = re.compile(r"INSTR\(([^()]*)\)")
# DATE_FORMAT(a, '%Y-%m-%d')
RE_DATE_FORMAT = re.compile(r"DATE_FORMAT\(([^()]*?),\s*'%Y-%m-%d'\)")


def main(apply: bool):
    total_files = 0
    total_instr = 0
    total_datefmt = 0

    for base in TARGET_DIRS:
        if not base.exists():
            continue
        for xml in sorted(base.rglob("*.xml")):
            raw = xml.read_bytes()
            text = raw.decode("utf-8")
            original = text

            n_instr = len(RE_INSTR.findall(text))
            n_datefmt = len(RE_DATE_FORMAT.findall(text))

            if n_instr == 0 and n_datefmt == 0:
                continue

            text = RE_INSTR.sub(lambda m: "STRPOS(%s) > 0" % m.group(1).strip(), text)
            text = RE_DATE_FORMAT.sub(lambda m: "CAST(%s AS DATE)" % m.group(1).strip(), text)

            total_files += 1
            total_instr += n_instr
            total_datefmt += n_datefmt

            rel = xml.relative_to(ROOT).as_posix()
            print(f"{rel}: INSTR={n_instr} DATE_FORMAT={n_datefmt}")

            if apply and text != original:
                xml.write_bytes(text.encode("utf-8"))

    print("-" * 60)
    print(f"files={total_files} INSTR={total_instr} DATE_FORMAT={total_datefmt}")
    print("APPLIED" if apply else "DRY RUN (no files written)")


if __name__ == "__main__":
    main(apply="--apply" in sys.argv)
