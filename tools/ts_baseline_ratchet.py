#!/usr/bin/env python3
"""TypeScript Baseline Ratchet for xsy-scm-web.

Purpose
-------
SmartAdmin upstream ships with a large pre-existing ``vue-tsc`` error surface.
We must never make it worse, and SCM-owned code must stay at zero errors.

This tool has two modes:

1. ``capture`` -- run ``vue-tsc --noEmit``, parse the diagnostics into a
   structured baseline (file / line / column / TS code / message) and write it
   to the baseline JSON file.
2. ``check``   -- re-run ``vue-tsc --noEmit`` and compare against the baseline.
   Exit code 0 only when:
     * no NEW error appears (identity = file + TS code + message),
     * the SCM-owned path prefix has zero errors,
     * the total error count did not grow.
   Baseline entries may disappear (that is an improvement, and it is reported
   so the baseline can be re-captured / shrunk).

Usage
-----
    python ts_baseline_ratchet.py capture
    python ts_baseline_ratchet.py check
    python ts_baseline_ratchet.py check --raw <already-captured-output.txt>

Notes
-----
* Identity deliberately excludes line/column so that harmless line shifts in
  upstream files do not register as "new" errors. Line/column are still stored
  in the baseline for traceability.
* Absolute paths quoted inside a message are masked to ``<path>`` / ``<webDir>``:
  without that, the same upstream error recorded under another checkout directory
  would show up as one "fixed" plus one "new" error, so ``check`` could never pass.
* ``SCM_PREFIXES`` is the ratchet's "must always be zero" zone.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from collections import Counter
from pathlib import Path

# ---------------------------------------------------------------- configuration

ROOT = Path(__file__).resolve().parents[1]
WEB_DIR = ROOT / "xsy-scm-web"
BASELINE_PATH = WEB_DIR / "quality" / "ts-baseline.json"

# Paths owned by the SCM delivery team. These must stay at zero errors.
SCM_PREFIXES = (
    "src/utils/scm-amount.ts",
    "src/api/business/scm/",
    "src/components/business/scm/",
    "src/constants/business/scm/",
    "src/types/business/scm/",
    "src/views/business/scm/",
)

DIAG_RE = re.compile(
    r"^(?P<file>[^(]+?)\((?P<line>\d+),(?P<col>\d+)\): "
    r"error (?P<code>TS\d+): (?P<msg>.*)$"
)

# vue-tsc quotes resolved module paths (TS7016) and imported types (TS7053), so the
# same diagnostic reads differently on another checkout directory.
QUOTED_ABS_PATH_RES = (
    re.compile(r"'(?:[A-Za-z]:[\\/]|/)[^']*'"),
    re.compile(r'"(?:[A-Za-z]:[\\/]|/)[^"]*"'),
)
ABS_DRIVE_PATH_RE = re.compile(r"[A-Za-z]:[\\/][^\s\"']+")


def normalize_message(message: str) -> str:
    """Mask absolute filesystem paths so identities stay portable across checkouts."""
    # A quoted span may contain spaces, which the bare-path pattern below cannot cross.
    for pattern in QUOTED_ABS_PATH_RES:
        message = pattern.sub(lambda m: m.group(0)[0] + "<path>" + m.group(0)[-1], message)
    message = ABS_DRIVE_PATH_RE.sub("<path>", message)
    for variant in (str(WEB_DIR), str(WEB_DIR).replace("\\", "/")):
        message = message.replace(variant, "<webDir>")
    return message


# ------------------------------------------------------------------- diagnostics


class Diagnostic:
    __slots__ = ("file", "line", "col", "code", "message")

    def __init__(self, file: str, line: int, col: int, code: str, message: str):
        self.file = file
        self.line = line
        self.col = col
        self.code = code
        self.message = message

    @property
    def identity(self) -> str:
        """Stable identity: file + TS code + message (line/col excluded)."""
        return f"{self.file}|{self.code}|{self.message}"

    def to_json(self) -> dict:
        return {
            "file": self.file,
            "line": self.line,
            "column": self.col,
            "code": self.code,
            "message": self.message,
        }


def parse_tsc_output(raw: str) -> list[Diagnostic]:
    """Parse ``vue-tsc`` stdout into diagnostics.

    Multi-line diagnostics (e.g. overload resolution notes) are appended to the
    message of the preceding error so that the message stays self-contained.
    """
    out: list[Diagnostic] = []
    for line in raw.splitlines():
        line = line.rstrip("\r")
        m = DIAG_RE.match(line)
        if m:
            out.append(
                Diagnostic(
                    file=m.group("file").strip(),
                    line=int(m.group("line")),
                    col=int(m.group("col")),
                    code=m.group("code"),
                    message=normalize_message(m.group("msg").strip()),
                )
            )
        elif out and line.strip() and not line.startswith("error TS"):
            # continuation of the previous diagnostic
            prev = out[-1]
            prev.message = normalize_message(f"{prev.message} / {line.strip()}")
    return out


def run_typecheck() -> tuple[str, int]:
    """Run ``vue-tsc --noEmit`` inside the V2 web workspace."""
    node = shutil.which("node")
    if not node:
        raise RuntimeError("node is not on PATH")
    vue_tsc = str(WEB_DIR / "node_modules" / "vue-tsc" / "bin" / "vue-tsc.js")
    proc = subprocess.run(
        [node, vue_tsc, "--noEmit", "--pretty", "false"],
        cwd=str(WEB_DIR),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        shell=False,
    )
    raw = (proc.stdout or "") + (proc.stderr or "")
    if proc.returncode not in (0, 1, 2) or (proc.returncode != 0 and not parse_tsc_output(raw)):
        raise RuntimeError(f"vue-tsc failed without usable diagnostics (exit {proc.returncode}):\n{raw}")
    validate_output(raw)
    return raw, proc.returncode


def validate_output(raw: str) -> None:
    # Global/configuration errors have no source position and cannot be baselined.
    if re.search(r"^(?:error )?TS\d+:", raw, re.MULTILINE):
        raise RuntimeError(f"Unscoped TypeScript error:\n{raw}")


# ------------------------------------------------------------------- ratchet


def is_scm(file: str) -> bool:
    norm = file.replace("\\", "/")
    return any(norm.startswith(p) for p in SCM_PREFIXES)


def summarize(diags: list[Diagnostic]) -> dict:
    scm = [d for d in diags if is_scm(d.file)]
    by_code = Counter(d.code for d in diags)
    by_dir = Counter(d.file.split("/")[0] + "/" + "/".join(d.file.split("/")[1:3]) for d in diags)
    return {
        "total": len(diags),
        "scm": len(scm),
        "by_code": dict(sorted(by_code.items(), key=lambda kv: (-kv[1], kv[0]))),
        "top_dirs": dict(sorted(by_dir.items(), key=lambda kv: (-kv[1], kv[0]))[:15]),
    }


def cmd_capture(args) -> int:
    if args.raw:
        raw = Path(args.raw).read_text(encoding="utf-8", errors="replace")
        exit_code = None
    else:
        raw, exit_code = run_typecheck()

    diags = parse_tsc_output(raw)
    validate_output(raw)
    if any(is_scm(d.file) for d in diags):
        raise RuntimeError("SCM errors cannot be captured into the baseline")
    payload = {
        "generatedAt": args.stamp or "",
        "tool": "vue-tsc --noEmit",
        "webDir": "xsy-scm-web",
        "identityRule": "file + TS code + message (line/column excluded)",
        "scmMustBeZeroPrefixes": list(SCM_PREFIXES),
        "summary": summarize(diags),
        "diagnostics": sorted(
            (d.to_json() for d in diags),
            key=lambda d: (d["file"], d["line"], d["column"], d["code"]),
        ),
    }
    BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
    BASELINE_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    s = payload["summary"]
    print(f"baseline written : {BASELINE_PATH}")
    print(f"total errors     : {s['total']}")
    print(f"scm errors       : {s['scm']}")
    print(f"vue-tsc exit code: {exit_code}")
    print("top TS codes     :")
    for code, n in list(s["by_code"].items())[:12]:
        print(f"  {code:10s} {n}")
    return 0


def cmd_check(args) -> int:
    if args.raw:
        raw = Path(args.raw).read_text(encoding="utf-8", errors="replace")
    else:
        raw, _ = run_typecheck()

    if not BASELINE_PATH.exists():
        print(f"ERROR: baseline not found at {BASELINE_PATH}; run `capture` first.")
        return 2

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    validate_output(raw)
    base_ids: dict[str, dict] = {}
    for d in baseline["diagnostics"]:
        ident = f"{d['file']}|{d['code']}|{normalize_message(d['message'])}"
        base_ids.setdefault(ident, d)

    current = parse_tsc_output(raw)
    cur_ids: dict[str, dict] = {}
    for d in current:
        cur_ids.setdefault(d.identity, d.to_json())

    new_ids = [k for k in cur_ids if k not in base_ids]
    base_counts = Counter(
        f"{d['file']}|{d['code']}|{normalize_message(d['message'])}" for d in baseline["diagnostics"]
    )
    increased = Counter(d.identity for d in current) - base_counts
    fixed_ids = [k for k in base_ids if k not in cur_ids]

    base_total = baseline["summary"]["total"]
    cur_total = len(current)
    scm_now = [d for d in current if is_scm(d.file)]

    print("=== TypeScript Baseline Ratchet ===")
    print(f"baseline total : {base_total}")
    print(f"current  total : {cur_total}  (delta {cur_total - base_total:+d})")
    print(f"scm errors     : {len(scm_now)}  (must be 0)")
    print(f"new errors     : {len(new_ids)}")
    print(f"fixed errors   : {len(fixed_ids)}")

    if new_ids:
        print("\n--- NEW ERRORS (blocking) ---")
        for k in sorted(new_ids)[:80]:
            d = cur_ids[k]
            print(f"  {d['file']}({d['line']},{d['column']}): {d['code']}: {d['message'][:160]}")
        if len(new_ids) > 80:
            print(f"  ... and {len(new_ids) - 80} more")

    if scm_now:
        print("\n--- SCM ERRORS (blocking) ---")
        for d in sorted(scm_now, key=lambda x: (x.file, x.line)):
            print(f"  {d.file}({d.line},{d.col}): {d.code}: {d.message[:160]}")

    if fixed_ids:
        print("\n--- IMPROVEMENTS (baseline can be shrunk) ---")
        for k in sorted(fixed_ids)[:40]:
            d = base_ids[k]
            print(f"  {d['file']}: {d['code']}: {d['message'][:120]}")
        if len(fixed_ids) > 40:
            print(f"  ... and {len(fixed_ids) - 40} more")

    print(f"new error occurrences: {sum(increased.values())}")
    ok = not increased and not scm_now and cur_total <= base_total
    print("\nRESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="mode", required=True)

    pc = sub.add_parser("capture", help="capture a new baseline")
    pc.add_argument("--raw", help="parse an existing vue-tsc output file instead of re-running")
    pc.add_argument("--stamp", help="timestamp string to embed")
    pc.set_defaults(func=cmd_capture)

    pk = sub.add_parser("check", help="check current output against baseline")
    pk.add_argument("--raw", help="parse an existing vue-tsc output file instead of re-running")
    pk.set_defaults(func=cmd_check)

    args = p.parse_args()
    try:
        return args.func(args)
    except (OSError, RuntimeError, ValueError, KeyError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
