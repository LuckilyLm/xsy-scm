#!/usr/bin/env python3
"""W7 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-*.spec.ts）：
    env: W7_E2E_NAME / W7_E2E_PASSWORD
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("W7_E2E_NAME", "")
impl.PASSWORD = os.environ.get("W7_E2E_PASSWORD", "")
impl.PREFIX = "w7_e2e_"


def _cli(flag: str) -> str:
    """允许 --name/--password 覆盖环境变量（同 w5_e2e_accounts.py）。"""
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
    # 只读伴随账号的名字在 e2e_accounts 导入期由 NAME 算出，那时 NAME 还是空串
    # （W7 的 env key 当时不在识别列表里）；这里显式重算，避免 setup 建出空 login_name。
    impl.READ_NAME = impl.NAME + "_read" if impl.NAME else ""
    if action == "setup":
        impl.setup()
    elif action == "cleanup":
        impl.cleanup()
    else:
        raise SystemExit(
            "用法: w7_e2e_accounts.py setup|cleanup [--name X --password Y]"
            "（或 W7_E2E_NAME/W7_E2E_PASSWORD）"
        )
