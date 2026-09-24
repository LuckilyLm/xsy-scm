#!/usr/bin/env python3
"""F0 对象存储 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

文件读权限判定用的是「登录 + 关系/前缀/归属」，不是功能权限点（`/support/file/getFileUrl`
上没有 `@SaCheckPermission`），所以本波次只需要标准三件套：
临时管理员（签发出纳类私有附件）、只读账号、零角色账号 ——
后者就是「另一个已登录员工」，用来证「不是上传者、也没有业务对象授权时必须被拒」。

Playwright 调用约定（见 xsy-scm-web/e2e/f0-file-storage.spec.ts）：
    env: F0_E2E_NAME / F0_E2E_PASSWORD
    args: setup | cleanup
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("F0_E2E_NAME", "")
impl.PASSWORD = os.environ.get("F0_E2E_PASSWORD", "")
impl.PREFIX = "f0_e2e_"


def _cli(flag: str) -> str:
    """允许 --name/--password 覆盖环境变量（不要在 import 前赋值，见 w5 同款注释）。"""
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
        raise SystemExit("用法: f0_e2e_accounts.py setup|cleanup [--name X --password Y]（或 F0_E2E_NAME/F0_E2E_PASSWORD）")
