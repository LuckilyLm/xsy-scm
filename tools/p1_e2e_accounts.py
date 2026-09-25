#!/usr/bin/env python3
"""P1 分拣 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

本波次要同时挂两个**正式业务角色**：SCM_SORTER（分拣员，只能处理派给自己的任务）与
SCM_STOREKEEPER_LEAD（仓库主管，队列管理权）。分拣范围是「授权仓 ∩ 受指派人」，
超管账号跑绿不构成证据，因此正反例都必须用 administrator_flag=false 的角色账号来证。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-sorting.spec.ts）：
    env: P1_E2E_NAME / P1_E2E_PASSWORD，可选 E2E_BUSINESS_ROLES=SCM_SORTER,SCM_STOREKEEPER_LEAD
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("P1_E2E_NAME", "")
impl.PASSWORD = os.environ.get("P1_E2E_PASSWORD", "")
impl.PREFIX = "p1_e2e_"


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
        raise SystemExit("用法: p1_e2e_accounts.py setup|cleanup --name X --password Y --roles A,B"
                         "（或 P1_E2E_*/E2E_BUSINESS_ROLES）")
