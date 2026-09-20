#!/usr/bin/env python3
"""W5 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-*.spec.ts）：
    env: W5_E2E_NAME / W5_E2E_PASSWORD
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("W5_E2E_NAME", "")
impl.PASSWORD = os.environ.get("W5_E2E_PASSWORD", "")
impl.PREFIX = "w5_e2e_"


def _cli(flag: str) -> str:
    """允许 --name/--password 覆盖环境变量。

    注意：不要直接用 impl._arg()——它在 import 时已经跑过一遍，
    那时 sys.argv 还没被这里读取，且下面的赋值会无条件覆盖环境变量。
    """
    for i, token in enumerate(sys.argv):
        if token == flag and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return ""


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if _cli("--name"):
        impl.NAME = _cli("--name")
    if _cli("--password"):
        impl.PASSWORD = _cli("--password")
    if action == "setup":
        impl.setup()
    elif action == "cleanup":
        impl.cleanup()
    else:
        raise SystemExit("用法: w5_e2e_accounts.py setup|cleanup [--name X --password Y]（或 W5_E2E_NAME/W5_E2E_PASSWORD）")
