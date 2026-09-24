#!/usr/bin/env python3
"""W8 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

本波次（P0 基线收口）除了管理员 / 只读 / 零角色三个标准账号外，还要挂**正式业务角色**：
只读夹具持有全部 `:query` 权限，因此也持有 `scm:*:scope:all:query`，数据范围对它等于不收窄，
所以数据范围的正反例必须用 V56 种下的真实角色 + administrator_flag=false 的账号来证。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-*.spec.ts）：
    env: W8_E2E_NAME / W8_E2E_PASSWORD，可选 E2E_BUSINESS_ROLES=SCM_STOREKEEPER,SCM_SALES,...
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("W8_E2E_NAME", "")
impl.PASSWORD = os.environ.get("W8_E2E_PASSWORD", "")
impl.PREFIX = "w8_e2e_"


def _cli(flag: str) -> str:
    """允许 --name/--password/--roles 覆盖环境变量（不要在 import 前赋值，见 w5 同款注释）。"""
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
    if _cli("--roles"):
        impl.BUSINESS_ROLES = _cli("--roles")
    if action == "setup":
        impl.setup()
    elif action == "cleanup":
        impl.cleanup()
    else:
        raise SystemExit("用法: w8_e2e_accounts.py setup|cleanup [--name X --password Y --roles A,B]（或 W8_E2E_*/E2E_BUSINESS_ROLES）")
