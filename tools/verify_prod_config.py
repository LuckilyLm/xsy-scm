#!/usr/bin/env python3
"""Check that production configuration has no example credentials or API docs enabled."""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "xsy-scm-server" / "sa-base" / "src" / "main" / "resources" / "prod" / "sa-base.yaml"

FORBIDDEN = (
    "lab1024@163.com",
    "LAB1024LAB",
    "username: druid",
    "password: 1024",
)
REQUIRED = (
    "  swagger-ui:\n    enabled: false",
    "  api-docs:\n    enabled: false",
    "knife4j:\n  enable: false",
    "host: ${XSY_MAIL_HOST}",
    "username: ${XSY_MAIL_USERNAME}",
    "password: ${XSY_MAIL_PASSWORD}",
)


def main() -> int:
    text = CONFIG.read_text(encoding="utf-8")
    errors = []
    for value in FORBIDDEN:
        if value in text:
            errors.append(f"forbidden production credential/example: {value}")
    for value in REQUIRED:
        if value not in text:
            errors.append(f"missing production security setting: {value}")
    if errors:
        print("Production config check: FAIL")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("Production config check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
