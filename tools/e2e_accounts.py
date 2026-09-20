#!/usr/bin/env python3
"""
W1–W5 e2e 临时账号工具（共用实现）
===============================================================================
为 Playwright 用例创建/回收一个**隔离的临时超级管理员员工账号**。

为什么需要：Playwright 必须走真实登录链路（Sa-Token + SM4 传输加密 + 验证码），
所以不能在浏览器里伪造 token，必须有真实账号。为避免污染种子管理员
（employee_id = 1），每次运行前新建一个临时员工，运行后按 login_name 精确清理。

SmartAdmin 口令规则（从正式源码反推，勿凭记忆猜）：
  net.lab1024.sa.admin.module.system.employee.service.EmployeeService
    · employeeUid = UUID(true).toString(true)          → 无连字符小写 32 位十六进制
    · saltPwd     = password + "_" + UID.upper() + "_" + UID.lower()
    · loginPwd    = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(saltPwd)
      （= Argon2id, saltLen=16, hashLen=32, parallelism=1, memory=16384, iterations=2）

  登录时 LoginService 用同一套 generateSaltPassword 复算后做 matches()，
  因此这里必须写**一样的摘要**，否则登录失败。

数据库：本机 docker `xsy-pg-v2`（127.0.0.1:15432），db=xsy_scm，schema=xsy_v2。
可用环境变量覆盖：XSY_V2_PG_CONTAINER / XSY_V2_PG_DB / XSY_V2_PG_SCHEMA / XSY_V2_PG_USER

命令行：
    python tools/e2e_accounts.py setup   --prefix w1_e2e_ --name NAME --password PASS
    python tools/e2e_accounts.py cleanup --prefix w1_e2e_ --name NAME
或（供 Playwright execFileSync 使用的简化形式，通过环境变量传参）：
    W1_E2E_NAME / W1_E2E_PASSWORD  + argv[1] = setup|cleanup

安全约束：
  · login_name 必须以 `w<wave>_e2e_` 开头（`_e2e_` 是硬性标记），否则拒绝执行；
  · 永不触碰种子管理员行（login_name = 'admin' / employee_id = 1）；
  · cleanup 只按 login_name 精确删除，不做任何前缀通配删除。
"""

import os
import subprocess
import sys

CONTAINER = os.environ.get("XSY_V2_PG_CONTAINER", "xsy-pg-v2")
DB = os.environ.get("XSY_V2_PG_DB", "xsy_scm")
SCHEMA = os.environ.get("XSY_V2_PG_SCHEMA", "xsy_v2")
USER = os.environ.get("XSY_V2_PG_USER", "postgres")

SEED_LOGIN_NAME = "admin"     # V3 播种的管理员账号，任何情况下都不允许被本脚本触碰
SEED_EMPLOYEE_ID = 1          # 与上面同一行记录的 employee_id


# ---------------------------------------------------------------------------
# CLI 解析：同时支持 --name/--password 与环境变量（Playwright 用环境变量）
# ---------------------------------------------------------------------------
def _arg(flag: str, env_keys: list[str], default: str = "") -> str:
    for i, token in enumerate(sys.argv):
        if token == flag and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    for key in env_keys:
        if os.environ.get(key):
            return os.environ[key]
    return default


ACTION = sys.argv[1] if len(sys.argv) > 1 else ""
NAME = _arg("--name", ["E2E_NAME", "W1_E2E_NAME", "W2_E2E_NAME", "W3_E2E_NAME",
                       "W4_E2E_NAME", "W5_E2E_NAME"])
PASSWORD = _arg("--password", ["E2E_PASSWORD", "W1_E2E_PASSWORD", "W2_E2E_PASSWORD",
                               "W3_E2E_PASSWORD", "W4_E2E_PASSWORD", "W5_E2E_PASSWORD"])
PREFIX = _arg("--prefix", ["E2E_PREFIX"], "")

# 伴随账号有两种，语义互斥，不能共用一个：
#   `<NAME>_read`：挂只读角色（页面菜单 + 只读按钮），用于「页面能打开、写入按钮被隐藏、写接口 30005」；
#   `<NAME>_none`：**不挂任何角色**，用于「菜单不下发 → 深链落到 404、连查询都被拒」。
# 只读角色一旦授了 `:query` 按钮，就再也没法验证后一种场景，所以两者分开建。
READ_SUFFIX = "_read"
NONE_SUFFIX = "_none"
READ_NAME = NAME + READ_SUFFIX if NAME else ""


def companion_names() -> tuple[str, str]:
    """按当前 NAME 现算伴随账号名。

    w1..w6 薄封装在导入后才设置 impl.NAME，模块级的 READ_NAME 那时还是空串；
    setup/cleanup 一律走这里，避免封装脚本各修各的。
    """
    return (NAME + READ_SUFFIX if NAME else "", NAME + NONE_SUFFIX if NAME else "")


# ---------------------------------------------------------------------------
# psql
# ---------------------------------------------------------------------------
def psql(sql: str, *, value_only: bool = False, allow_error: bool = False) -> str:
    args = ["docker", "exec", "-i", CONTAINER, "psql", "-U", USER, "-d", DB]
    if value_only:
        args += ["-tA"]
    args += ["-v", "ON_ERROR_STOP=1", "-c", sql]
    proc = subprocess.run(args, capture_output=True, text=True)
    if proc.returncode != 0:
        if allow_error:
            return ""
        raise SystemExit(f"[e2e-accounts] psql 执行失败:\n{proc.stdout}\n{proc.stderr}")
    return proc.stdout.strip()


def q(value: str) -> str:
    """SQL 字符串字面量转义。"""
    return "'" + str(value).replace("'", "''") + "'"


# ---------------------------------------------------------------------------
# Argon2id 摘要（参数与 Spring Security defaultsForSpringSecurity_v5_8 一致）
# ---------------------------------------------------------------------------
def argon2_encode(salt_password: str) -> str:
    try:
        from argon2 import PasswordHasher  # type: ignore
    except ImportError:
        raise SystemExit(
            "[e2e-accounts] 缺少 python 包 argon2-cffi。请执行：\n"
            "  C:/Users/chenk/.workbuddy-ai/binaries/python/envs/default/Scripts/pip.exe install argon2-cffi"
        )

    hasher = PasswordHasher(
        time_cost=2,          # iterations
        memory_cost=16384,    # KB
        parallelism=1,
        hash_len=32,
        salt_len=16,
    )
    return hasher.hash(salt_password)


# ---------------------------------------------------------------------------
# 守卫
# ---------------------------------------------------------------------------
def guard() -> None:
    if not NAME or (ACTION == "setup" and not PASSWORD):
        raise SystemExit(
            "[e2e-accounts] 缺少账号参数。请提供 --name/--password（cleanup 只需 --name），或设置 "
            "W{1..5}_E2E_NAME / W{1..5}_E2E_PASSWORD 环境变量。"
        )
    if not NAME.startswith("w") or "_e2e_" not in NAME:
        raise SystemExit(
            f"[e2e-accounts] 拒绝执行：login_name 必须是形如 w1_e2e_xxx 的临时账号，当前为 {NAME!r}"
        )
    if PREFIX and not NAME.startswith(PREFIX):
        raise SystemExit(f"[e2e-accounts] 拒绝执行：login_name 必须以 {PREFIX!r} 开头，当前为 {NAME!r}")


# ---------------------------------------------------------------------------
# setup
# ---------------------------------------------------------------------------
def ensure_account(
    login_name: str,
    password: str = "",
    *,
    administrator: bool,
    role_id: str | None,
    reuse: bool = True,
) -> str:
    """建（或复用）一个临时员工账号，返回 employee_id。

    - administrator=True → administrator_flag=true，@SaCheckPermission 直接放行；
    - administrator=False 且 role_id=None → 不挂任何角色，权限断言才会真的 30005。
    """
    existing_id = None
    if reuse:
        existing_id = psql(
            f"SELECT employee_id FROM {SCHEMA}.t_employee "
            f"WHERE login_name = {q(login_name)} AND employee_id <> {SEED_EMPLOYEE_ID} "
            f"ORDER BY employee_id LIMIT 1;",
            value_only=True,
        )

    uid = psql("SELECT lower(replace(gen_random_uuid()::text, '-', ''));", value_only=True)
    if not uid:
        raise SystemExit("[e2e-accounts] 无法生成 employee_uid")

    salt_password = f"{password}_{uid.upper()}_{uid.lower()}"
    login_pwd = argon2_encode(salt_password)

    if existing_id:
        # 复用：换 uid / 密码 / 管理员标记，重置可用状态，其余字段不动。
        psql(
            f"""
            UPDATE {SCHEMA}.t_employee
               SET login_pwd = {q(login_pwd)},
                   employee_uid = {q(uid)},
                   administrator_flag = {"true" if administrator else "false"},
                   disabled_flag = false,
                   deleted_flag = false,
                   update_time = now()
             WHERE employee_id = {existing_id}::bigint;
            """
        )
        employee_id = existing_id
        action = "reused"
    else:
        # 部门：复用序号最小的部门（V3 已播种），避免新建部门。
        # 注意 t_department 没有 deleted_flag（只有 department_id/name/manager_id/parent_id/sort/时间）。
        department_id = psql(
            f"SELECT department_id FROM {SCHEMA}.t_department ORDER BY department_id LIMIT 1;",
            value_only=True,
        )
        if not department_id:
            raise SystemExit("[e2e-accounts] 库中没有可用部门，请先应用 V3 迁移")

        raw = psql(
            f"""
            INSERT INTO {SCHEMA}.t_employee
                (login_name, login_pwd, employee_uid, actual_name, phone, email, gender,
                 department_id, disabled_flag, administrator_flag, deleted_flag,
                 remark, update_time, create_time)
            VALUES
                ({q(login_name)}, {q(login_pwd)}, {q(uid)}, {q(login_name)}, '13800000000',
                 'e2e@example.invalid', 1, {department_id}::bigint,
                 false, {"true" if administrator else "false"}, false,
                 'e2e 临时账号，脚本自动创建，可随时清理', now(), now())
            RETURNING employee_id;
            """
        )

        # psql 的 RETURNING 输出形如 "2\nINSERT 0 1"，取第一行纯数字
        employee_id = ""
        for line in raw.splitlines():
            line = line.strip()
            if line.isdigit():
                employee_id = line
                break

        if not employee_id:
            raise SystemExit(f"[e2e-accounts] 员工插入失败，psql 输出: {raw!r}")

        if int(employee_id) == SEED_EMPLOYEE_ID:
            raise SystemExit(
                f"[e2e-accounts] 拒绝：新建员工 id={employee_id} 与种子管理员冲突，数据库序列异常，已中止"
            )
        action = "created"

    if role_id:
        psql(
            f"""
            INSERT INTO {SCHEMA}.t_role_employee (role_id, employee_id, update_time, create_time)
            VALUES ({role_id}::bigint, {employee_id}::bigint, now(), now())
            ON CONFLICT DO NOTHING;
            """
        )

    # 清掉该账号的登录失败记录，避免被锁定（调试期反复失败会留下计数）。
    psql(
        f"DELETE FROM {SCHEMA}.t_login_fail WHERE login_name = {q(login_name)};",
        allow_error=True,
    )

    print(
        f"[e2e-accounts] ok ({action})  login_name={login_name}  employee_id={employee_id}  "
        f"admin={'yes' if administrator else 'no'}"
    )
    return employee_id


def ensure_read_only_role(read_login_name: str) -> str:
    """建（或复用）一个「只读」角色，并把只读账号挂上去。

    为什么需要它：前端菜单是**按角色授权动态下发**的。
    如果只读账号一个角色都不挂，菜单树是空的 → 前端根本没有目标路由 → 深链直接落到 404，
    用例的「页面能打开 + 写入按钮被隐藏」两个断言都无法成立（这不是权限被拒，而是页面不存在）。

    只读角色的授权范围：
      - 所有**页面类**菜单（menu_type 1/2）→ 保证列表页/详情页可达；
      - 只带 `:query` 的**按钮类**菜单（menu_type 3）→ 保证「查询」按钮渲染。
    刻意**不授** add/update/delete/status 等写权限，这样前端 v-privilege 会隐藏写入按钮，
    后端 @SaCheckPermission 也会真的返回 30005。
    """
    role_code = f"{PREFIX.upper()}READ" if PREFIX else "E2E_READ"
    # 注意 t_role.role_name 是 varchar(20)，不能塞进 login_name 之类的长字符串。
    role_name = f"E2E只读角色"

    psql(
        f"""
        INSERT INTO {SCHEMA}.t_role (role_name, role_code, remark, update_time, create_time)
        VALUES ({q(role_name)}, {q(role_code)}, 'e2e 临时只读角色，脚本自动创建，可随时清理',
                now(), now())
        ON CONFLICT (role_code) DO UPDATE SET update_time = now();
        """
    )
    role_id = psql(
        f"SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)} LIMIT 1;",
        value_only=True,
    )
    if not role_id:
        raise SystemExit(f"[e2e-accounts] 无法建立只读角色 {role_code}")

    # 授权：全部门级菜单 + 只有 query 语义的按钮。
    # 判定「只读」用权限码里不含写动作关键词，避免误授。batch 必须列进来：
    # 商品批量维护与协议价批量调价都是纯写命令，权限码里却不含 add/update/delete 等词。
    psql(
        f"""
        DELETE FROM {SCHEMA}.t_role_menu WHERE role_id = {role_id}::bigint;
        INSERT INTO {SCHEMA}.t_role_menu (role_id, menu_id, update_time, create_time)
        SELECT {role_id}::bigint, menu_id, now(), now()
          FROM {SCHEMA}.t_menu
         WHERE menu_type IN (1, 2)
            OR (menu_type = 3
                AND lower(coalesce(web_perms, '')) NOT SIMILAR TO '%(add|update|delete|status|replace|import|export|submit|approve|cancel|confirm|audit|generate|batch)%');
        """
    )
    granted = psql(
        f"SELECT count(*) FROM {SCHEMA}.t_role_menu WHERE role_id = {role_id}::bigint;",
        value_only=True,
    )
    print(f"[e2e-accounts] read-only role ready  role_code={role_code}  granted_menus={granted}")
    return role_id


def setup() -> None:
    guard()

    # 主账号：管理员（administrator_flag = true，@SaCheckPermission 直接放行）。
    # 挂 SUPER_ADMIN 角色（V3 播种，role_id = 1），与种子管理员判定路径一致。
    admin_role_id = psql(
        f"SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = 'SUPER_ADMIN' LIMIT 1;",
        value_only=True,
    )
    ensure_account(NAME, PASSWORD, administrator=True, role_id=admin_role_id)

    # 伴随只读账号：`<NAME>_read`。
    # W1–W6 的 e2e 用例统一用它验证两件事：
    #   1) 页面还能正常打开（所以要授页面菜单）；
    #   2) 写入动作被拒（所以要**不**授写权限，且不能是 administrator）。
    # 伴随零权限账号：`<NAME>_none`，不挂任何角色，用于「深链落到 404 + 查询接口也 30005」。
    read_name, none_name = companion_names()
    read_role_id = ensure_read_only_role(read_name)
    ensure_account(read_name, PASSWORD, administrator=False, role_id=read_role_id)
    ensure_account(none_name, PASSWORD, administrator=False, role_id=None)

    print(f"[e2e-accounts] setup done  admin={NAME}  read_only={read_name}  no_role={none_name}")



# ---------------------------------------------------------------------------
# cleanup
# ---------------------------------------------------------------------------
def cleanup_quiet(name: str) -> None:
    """删除指定 login_name 的临时员工及其角色关联（仅供 setup 幂等复用）。

    双重保护：按 login_name 精确匹配 + 排除种子管理员行；
    不做任何前缀 / 通配删除。
    """
    psql(
        f"""
        DELETE FROM {SCHEMA}.t_role_employee
         WHERE employee_id IN (SELECT employee_id FROM {SCHEMA}.t_employee
                                WHERE login_name = {q(name)}
                                  AND employee_id <> {SEED_EMPLOYEE_ID});
        DELETE FROM {SCHEMA}.t_employee
         WHERE login_name = {q(name)} AND employee_id <> {SEED_EMPLOYEE_ID};
        """
    )


def cleanup() -> None:
    guard()
    # 主账号和它带的两个伴随账号一起清理，避免残留。
    read_name, none_name = companion_names()
    cleanup_quiet(NAME)
    for companion in (read_name, none_name):
        if companion:
            cleanup_quiet(companion)
    # 只读角色及其授权也要一起回收，否则会在 t_role / t_role_menu 里越积越多。
    role_code = f"{PREFIX.upper()}READ" if PREFIX else "E2E_READ"
    psql(
        f"""
        DELETE FROM {SCHEMA}.t_role_menu
         WHERE role_id IN (SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)});
        DELETE FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)};
        """
    )
    print(f"[e2e-accounts] cleanup ok  login_name={NAME} (+{READ_SUFFIX}, +{NONE_SUFFIX}), role={role_code}")


if __name__ == "__main__":
    if ACTION == "setup":
        setup()
    elif ACTION == "cleanup":
        cleanup()
    else:
        raise SystemExit(
            "用法: e2e_accounts.py setup|cleanup "
            "[--name NAME] [--password PASS] [--prefix PREFIX]\n"
            "  或通过环境变量 W{1..5}_E2E_NAME / W{1..5}_E2E_PASSWORD 传参"
        )
