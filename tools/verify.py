#!/usr/bin/env python3
"""Shared verification runner. Exit 0 = complete, 1 = failed, 2 = incomplete."""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "xsy-scm-server"
WEB = ROOT / "xsy-scm-web"


class Verification:
    def __init__(self):
        self.failed = []
        self.incomplete = []
        self.logs = ROOT / ".runtime" / "verify" / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        self.logs.mkdir(parents=True, exist_ok=True)

    def run(self, label, command, cwd):
        executable = shutil.which(command[0])
        if not executable:
            self.failed.append(f"{label}: missing {command[0]}")
            return False
        log = self.logs / f"{label}.log"
        print(f"[{label}] {' '.join(command)}\n  log: {log}", flush=True)
        try:
            with log.open("w", encoding="utf-8") as output:
                result = subprocess.run([executable, *command[1:]], cwd=cwd,
                                        stdout=output, stderr=subprocess.STDOUT, check=False)
        except OSError as exc:
            self.failed.append(f"{label}: {exc}")
            return False
        print(f"[{label}] exit {result.returncode}", flush=True)
        if result.returncode:
            self.failed.append(f"{label}: exit {result.returncode}; {log}")
            tail = "\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-20:])
            encoding = getattr(sys.stdout, "encoding", None) or "utf-8"
            print(tail.encode(encoding, errors="replace").decode(encoding))
        return result.returncode == 0

    def backend(self):
        started = time.time()
        self.run("backend", ["mvn", "-B", "-pl", "sa-admin", "-am", "test"], SERVER)
        suites = []
        for report in SERVER.glob("*/target/surefire-reports/TEST-*.xml"):
            if report.stat().st_mtime < started:
                continue
            try:
                suites.append(ET.parse(report).getroot())
            except (ET.ParseError, OSError) as exc:
                self.failed.append(f"Invalid test report: {report.name}: {exc}")
        if not suites:
            self.incomplete.append("Backend: no fresh Surefire reports")
            return
        totals = {key: sum(int(s.get(key, "0")) for s in suites)
                  for key in ("tests", "failures", "errors", "skipped")}
        print(f"Backend reports: {totals}", flush=True)
        if totals["failures"] or totals["errors"]:
            self.failed.append("Backend reports contain failures/errors")
        if not any(s.get("name", "").endswith("IT") and
                   int(s.get("tests", "0")) > int(s.get("skipped", "0")) for s in suites):
            self.incomplete.append("Backend: no executed *IT suite")
        for suite in suites:
            if int(suite.get("skipped", "0")):
                self.incomplete.append(f"{suite.get('name')}: {suite.get('skipped')} skipped")

    def frontend(self):
        # Run vue-tsc once; the ratchet rejects new diagnostics and all SCM errors.
        self.run("ts-ratchet", [sys.executable, str(ROOT / "tools/ts_baseline_ratchet.py"), "check"], ROOT)
        for script in ("lint", "test", "build"):
            self.run(f"frontend-{script}", ["npm", "run", script], WEB)

    def e2e(self):
        # Local account provisioning remains a prerequisite, including on clean clones.
        helpers = [f"w{wave}_e2e_accounts.py" for wave in range(1, 7)]
        missing = [name for name in helpers if not (ROOT / "tools" / name).is_file()]
        if missing:
            self.incomplete.append("E2E: local account helpers missing: " + ", ".join(missing))
            return
        endpoints = {"http://127.0.0.1:18081"}
        endpoints.update(os.environ.get(f"W{wave}_E2E_API_BASE", "http://127.0.0.1:18080")
                         for wave in range(1, 7))
        endpoints.add(os.environ.get("F0_E2E_API_BASE", "http://127.0.0.1:18080"))
        unavailable = []
        for endpoint in sorted(endpoints):
            try:
                parsed = urlparse(endpoint)
                with socket.create_connection((parsed.hostname, parsed.port or
                                               (443 if parsed.scheme == "https" else 80)), timeout=2):
                    pass
            except (OSError, ValueError):
                unavailable.append(endpoint)
        if unavailable:
            self.incomplete.append("E2E: services unavailable: " + ", ".join(unavailable))
            return
        started = time.time()
        self.run("e2e", ["node", "node_modules/@playwright/test/cli.js", "test"], WEB)
        report = ROOT / ".runtime/playwright-result.json"
        if not report.exists() or report.stat().st_mtime < started:
            self.incomplete.append("E2E: no fresh JSON report")
            return
        try:
            stats = json.loads(report.read_text(encoding="utf-8"))["stats"]
            print(f"E2E reports: {stats}", flush=True)
            if stats.get("skipped", 0):
                self.incomplete.append(f"E2E: {stats['skipped']} skipped")
            if stats.get("unexpected", 0):
                self.failed.append("E2E: unexpected failures")
            if stats.get("flaky", 0):
                self.incomplete.append(f"E2E: {stats['flaky']} flaky")
            if stats.get("expected", 0) == 0:
                self.incomplete.append("E2E: no passing tests")
        except (OSError, ValueError, KeyError) as exc:
            self.failed.append(f"Invalid E2E report: {exc}")

    def summary(self):
        print("\nVerification summary", flush=True)
        for label, items in (("FAILED", self.failed), ("INCOMPLETE", self.incomplete)):
            print(f"{label}: " + ("none" if not items else "\n  " + "\n  ".join(items)))
        code = 1 if self.failed else 2 if self.incomplete else 0
        print(f"RESULT: {['PASS', 'FAIL', 'INCOMPLETE'][code]} (exit {code})")
        (self.logs / "summary.json").write_text(json.dumps({
            "failed": self.failed, "incomplete": self.incomplete, "exitCode": code
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scope", nargs="?", choices=("all", "backend", "frontend", "e2e"), default="all")
    args = parser.parse_args()
    verification = Verification()
    if args.scope in ("all", "backend"):
        verification.backend()
    if args.scope in ("all", "frontend"):
        verification.frontend()
    if args.scope in ("all", "frontend", "e2e"):
        verification.e2e()
    return verification.summary()


if __name__ == "__main__":
    sys.exit(main())
