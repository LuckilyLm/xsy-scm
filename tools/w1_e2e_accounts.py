#!/usr/bin/env python3
"""W1 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-*.spec.ts）：
    env: W1_E2E_NAME / W1_E2E_PASSWORD
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("W1_E2E_NAME", "")
impl.PASSWORD = os.environ.get("W1_E2E_PASSWORD", "")
impl.PREFIX = "w1_e2e_"

if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if action == "setup":
        impl.setup()
    elif action == "cleanup":
        impl.cleanup()
    else:
        raise SystemExit("用法: w1_e2e_accounts.py setup|cleanup（通过 W1_E2E_NAME/W1_E2E_PASSWORD 传参）")
