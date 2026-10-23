#!/usr/bin/env python3
"""E2E 后端夹具统一入口：临时账号、报表夹具、三类导入模板填值。

原先分散在 e2e_accounts.py、e2e_report_fixture.py、patch_product_create_xlsx.py、
patch_product_update_xlsx.py、fill_stocktake_template.py；它们服务于同一轮 E2E 的
前置数据，只是生命周期不同，因此收敛为一个入口，内部仍按职责分节：

    python tools/e2e_fixtures.py account-setup   --wave w1
    python tools/e2e_fixtures.py account-cleanup --wave w1
    python tools/e2e_fixtures.py report unused-report-dates|backdate-purchase-order|cleanup-daily-report-fixture --wave w6 [--name X]
    python tools/e2e_fixtures.py product-create --in template.xlsx --out create.xlsx --row "列名=值;列名=值"
    python tools/e2e_fixtures.py product-update [--template] --in in.xlsx --out out.xlsx --set 列名=值 [--blank 列名]
    python tools/e2e_fixtures.py stocktake --in template.xlsx --out filled.xlsx [--delta 1]

各节之间不共用列假设：账号只碰 t_employee / t_role / t_role_menu，报表只碰报表与采购单
回填，三个 xlsx 分别对齐各自模板的列契约与受保护列。数据库操作仍要求显式
XSY_V2_PG_DB，且只按带 `_e2e_` 标记的 login_name 精确删除。
"""
import argparse
import json
import os
import re
import subprocess
import sys
from datetime import date
from decimal import Decimal, InvalidOperation


# ===========================================================================
# 临时账号
# ===========================================================================
# 临时账号：按波次创建管理员 / 只读 / 零角色 / 扣权 / 业务角色 / 第二管理员账号。
#
# 为什么需要：Playwright 必须走真实登录链路（Sa-Token + SM4 传输加密 + 验证码），
# 所以不能在浏览器里伪造 token，必须有真实账号。为避免污染种子管理员
# （employee_id = 1），每次运行前新建一个临时员工，运行后按 login_name 精确清理。
#
# SmartAdmin 口令规则（从正式源码反推，勿凭记忆猜）：
#   net.lab1024.sa.admin.module.system.employee.service.EmployeeService
#     · employeeUid = UUID(true).toString(true)          → 无连字符小写 32 位十六进制
#     · saltPwd     = password + "_" + UID.upper() + "_" + UID.lower()
#     · loginPwd    = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(saltPwd)
#       （= Argon2id, saltLen=16, hashLen=32, parallelism=1, memory=16384, iterations=2）
#
#   登录时 LoginService 用同一套 generateSaltPassword 复算后做 matches()，
#   因此这里必须写**一样的摘要**，否则登录失败。
#
# 数据库：本机 docker Compose 的 Postgres 容器（默认 `xsy-scm-postgres-1`，127.0.0.1:15432），schema=xsy_v2；
# **库名必须由 XSY_V2_PG_DB 显式给出**，不给即拒绝执行（默认值会把账号建到过期快照库，登录只报「登录名或密码错误」）。
# 可用环境变量覆盖：XSY_V2_PG_CONTAINER / XSY_V2_PG_DB（必填）/ XSY_V2_PG_SCHEMA / XSY_V2_PG_USER
#
# 安全约束：
#   · login_name 必须以 `<波次>_e2e_` 开头（`_e2e_` 是硬性标记，波次如 w1 / w8 / f0），否则拒绝执行；
#   · 永不触碰种子管理员行（login_name = 'admin' / employee_id = 1）；
#   · cleanup 只按 login_name 精确删除，不做任何前缀通配删除。

CONTAINER = os.environ.get("XSY_V2_PG_CONTAINER", "xsy-scm-postgres-1")
DB = os.environ.get("XSY_V2_PG_DB", "")
SCHEMA = os.environ.get("XSY_V2_PG_SCHEMA", "xsy_v2")
USER = os.environ.get("XSY_V2_PG_USER", "xsy_scm_app")

SEED_LOGIN_NAME = "admin"     # V3 播种的管理员账号，任何情况下都不允许被本脚本触碰
SEED_EMPLOYEE_ID = 1          # 与上面同一行记录的 employee_id


# ---------------------------------------------------------------------------
# 账号参数：同时支持命令行与 <WAVE>_E2E_* 环境变量（Playwright 用后者）
# ---------------------------------------------------------------------------
ACTION = WAVE = NAME = PASSWORD = PREFIX = DENY = BUSINESS_ROLES = SECOND_ADMIN = ""
READ_SUFFIX = "_read"
NONE_SUFFIX = "_none"
DENY_SUFFIX = "_deny"
SECOND_ADMIN_SUFFIX = "_two"
# t_employee.login_name 是 VARCHAR(30)，角色短名按剩余长度截断，宁可难看也不能溢出列宽
LOGIN_NAME_MAX = 30


def _arg(flag: str, env_keys: list[str], default: str = "") -> str:
    for i, token in enumerate(sys.argv):
        if token == flag and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    for key in env_keys:
        if os.environ.get(key):
            return os.environ[key]
    return default


def configure_accounts(action: str) -> None:
    """按当前 sys.argv / 环境变量填充账号参数；每个进程只执行一次。"""
    global ACTION, WAVE, NAME, PASSWORD, PREFIX, DENY, BUSINESS_ROLES, SECOND_ADMIN
    ACTION = action
    WAVE = _arg("--wave", ["E2E_WAVE"], "")
    if WAVE and not re.fullmatch(r"(?:w[1-9]|f0|p1)", WAVE):
        raise SystemExit("[e2e-accounts] --wave 仅支持 w1…w9、f0、p1")
    wave_env = WAVE.upper() if WAVE else ""
    NAME = _arg("--name", ([f"{wave_env}_E2E_NAME"] if wave_env else []) + ["E2E_NAME"])
    PASSWORD = _arg("--password", ([f"{wave_env}_E2E_PASSWORD"] if wave_env else []) + ["E2E_PASSWORD"])
    PREFIX = f"{WAVE}_e2e_" if WAVE else _arg("--prefix", ["E2E_PREFIX"], "")
    DENY = _arg("--deny", ["E2E_DENY"], "")
    BUSINESS_ROLES = _arg("--roles", ["E2E_BUSINESS_ROLES"], "")
    SECOND_ADMIN = _arg("--second-admin", ["E2E_SECOND_ADMIN"], "")


def business_role_codes() -> list[str]:
    """本次要挂的正式角色码；空列表表示不建业务角色账号。"""
    return [token.strip() for token in BUSINESS_ROLES.split(",") if token.strip()]


def business_role_login_name(role_code: str) -> str:
    """角色码 → 伴随账号 login_name；去掉 SCM_ 前缀后按列宽截断。"""
    short = role_code[4:] if role_code.startswith("SCM_") else role_code
    short = short.lower()
    budget = LOGIN_NAME_MAX - len(NAME) - 1
    return f"{NAME}_{short[:max(budget, 1)]}"


def deny_codes() -> list[str]:
    """本次要扣掉的权限码；空列表表示不建扣权账号。"""
    return [token.strip() for token in DENY.split(",") if token.strip()]


def companion_names() -> tuple[str, str]:
    """按当前 NAME 现算伴随账号名。

    setup/cleanup 均从当前 NAME 派生，避免调用方各自维护伴随账号规则。
    """
    return (NAME + READ_SUFFIX if NAME else "", NAME + NONE_SUFFIX if NAME else "")


# 伴随账号有两种，语义互斥，不能共用一个：
#   `<NAME>_read`：挂只读角色（页面菜单 + 只读按钮），用于「页面能打开、写入按钮被隐藏、写接口 30005」；
#   `<NAME>_none`：**不挂任何角色**，用于「菜单不下发 → 深链落到 404、连查询都被拒」。
# 只读角色一旦授了 `:query` 按钮，就再也没法验证后一种场景，所以两者分开建。
# 第三个伴随账号 `<NAME>_deny`：授权面与只读账号一致，但**显式扣掉**指定权限码；
# 用于 AND 双权限读端点的「缺其中一项必须被拒」反例。
# 正式业务角色账号 `<NAME>_<角色短名>`：administrator_flag=false，挂 V56 种下的正式角色；
# 数据范围正反例只能用这种账号证（超管通过不算证据）。
# 第二管理员 `<NAME>_two`：报损报溢 / 规格转换禁止录单人自审，审批链必须有第二个人。

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
            "  python -m pip install argon2-cffi"
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
    if not DB:
        raise SystemExit(
            "[e2e-accounts] 必须显式设置 XSY_V2_PG_DB 为后端实际连接的库名。"
            "写错库时账号照样建成功，但登录只会报「登录名或密码错误」，排查方向会被带偏，因此不接受默认值。"
        )
    if not NAME or (ACTION == "setup" and not PASSWORD):
        raise SystemExit(
            "[e2e-accounts] 缺少账号参数。请提供 --name/--password（cleanup 只需 --name），或设置 "
            "<WAVE>_E2E_NAME / <WAVE>_E2E_PASSWORD 环境变量，并指定 --wave。"
        )
    # 波次令牌可以是 w1…w9（按交付波次）或 f0（对象存储取证），但 `_e2e_` 这个硬标记必须有：
    # 它是「临时账号」与正式账号之间唯一的判据，cleanup 按精确前缀删人，缺了这道标记
    # 一个写错的 --name 就能把正式员工的登录名扫进删除范围。
    if not re.match(r"^[a-z][a-z0-9]*_e2e_", NAME):
        raise SystemExit(
            "[e2e-accounts] 拒绝执行：login_name 必须是形如 <波次>_e2e_xxx 的临时账号"
            f"（波次如 w1 / w8 / f0），当前为 {NAME!r}"
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

    grant_read_only_menus(role_id)
    granted = psql(
        f"SELECT count(*) FROM {SCHEMA}.t_role_menu WHERE role_id = {role_id}::bigint;",
        value_only=True,
    )
    print(f"[e2e-accounts] read-only role ready  role_code={role_code}  granted_menus={granted}")
    return role_id


def grant_read_only_menus(role_id: str, deny: list[str] | None = None) -> None:
    """把「页面菜单 + 只读按钮」整套授权写进指定角色，可选再扣掉若干权限码。

    授权：全部门级菜单 + 权限码动作段以 `query` 结尾的按钮。
    这里刻意用白名单而不是写动词黑名单：本仓库所有只读权限码的动作段都以 query
    结尾，而写命令（print / plan / edit / ship / receive / release / reject / putaway /
    allocate / enable 等）都不以它结尾。黑名单靠枚举动词，漏一个词就会把写权限当成
    只读面授出去，而用例仍会报告「只读账号被正确拒绝」。
    `deny` 只在这个基础上再挖洞（用于「有 A 没 B」的 AND 权限反例），不放宽任何一条只读规则。
    """
    deny_predicate = ""
    if deny:
        like = " OR ".join(
            "lower(coalesce(web_perms, '')) LIKE " + "'%' || " + q(token.lower()) + " || '%'"
            for token in deny
        )
        deny_predicate = f"\n           AND NOT ({like})"
    psql(
        f"""
        DELETE FROM {SCHEMA}.t_role_menu WHERE role_id = {role_id}::bigint;
        INSERT INTO {SCHEMA}.t_role_menu (role_id, menu_id, update_time, create_time)
        SELECT {role_id}::bigint, menu_id, now(), now()
          FROM {SCHEMA}.t_menu m
         WHERE (menu_type IN (1, 2)
                OR (m.menu_type = 3
                    AND NOT EXISTS (
                        SELECT 1 FROM regexp_split_to_table(lower(coalesce(m.web_perms, '')), ',') c
                         WHERE c <> '' AND c NOT LIKE '%:query')))  -- 空 web_perms 的按钮行没有权限语义，一并跳过
        {deny_predicate};
        """
    )


def ensure_deny_role() -> str:
    """建（或复用）「只读面 − 指定权限码」角色，供 `<NAME>_deny` 账号挂。

    用途见 DENY 的注释：AND 双权限读端点必须证明「只缺一项也会被整条拒掉」，
    而通用只读角色两项都有，构不出这个反例。
    """
    deny = deny_codes()
    if not deny:
        raise SystemExit("[e2e-accounts] ensure_deny_role 需要非空 DENY 权限码列表")
    role_code = f"{PREFIX.upper()}DENY" if PREFIX else "E2E_DENY"
    role_name = "E2E扣权角色"

    psql(
        f"""
        INSERT INTO {SCHEMA}.t_role (role_name, role_code, remark, update_time, create_time)
        VALUES ({q(role_name)}, {q(role_code)}, 'e2e 临时扣权角色，脚本自动创建，可随时清理',
                now(), now())
        ON CONFLICT (role_code) DO UPDATE SET update_time = now();
        """
    )
    role_id = psql(
        f"SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)} LIMIT 1;",
        value_only=True,
    )
    if not role_id:
        raise SystemExit(f"[e2e-accounts] 无法建立扣权角色 {role_code}")
    grant_read_only_menus(role_id, deny)
    granted = psql(
        f"SELECT count(*) FROM {SCHEMA}.t_role_menu WHERE role_id = {role_id}::bigint;",
        value_only=True,
    )
    print(f"[e2e-accounts] deny role ready  role_code={role_code}  denied={deny}  granted_menus={granted}")
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

    done = f"[e2e-accounts] setup done  admin={NAME}  read_only={read_name}  no_role={none_name}"
    if deny_codes():
        deny_name = NAME + DENY_SUFFIX
        deny_role_id = ensure_deny_role()
        ensure_account(deny_name, PASSWORD, administrator=False, role_id=deny_role_id)
        done += f"  denied={deny_name}"

    role_summary = []
    for role_code in business_role_codes():
        role_id = psql(
            f"SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)} LIMIT 1;",
            value_only=True,
        )
        if not role_id:
            raise SystemExit(
                f"[e2e-accounts] 拒绝执行：角色 {role_code} 不存在。正式业务角色由 Flyway 种子建立，"
                f"缺角色即说明 {DB} 还没应用到那个版本；此时建出来的账号没有任何权限，"
                f"用例只会得到「全部 30005」的假绿。"
            )
        role_login = business_role_login_name(role_code)
        ensure_account(role_login, PASSWORD, administrator=False, role_id=role_id)
        role_summary.append(f"{role_code}={role_login}")
    if role_summary:
        done += "  roles=" + ",".join(role_summary)
    if SECOND_ADMIN:
        second_name = NAME + SECOND_ADMIN_SUFFIX
        ensure_account(second_name, PASSWORD, administrator=True, role_id=admin_role_id)
        done += f"  second_admin={second_name}"
    print(done)



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
    # 主账号和它带的伴随账号一起清理，避免残留。
    # 扣权账号**无条件**清理：cleanup 调用方漏传 --deny 时也不能把夹具留在库里
    # （没建过就是 0 行删除，无副作用）。
    read_name, none_name = companion_names()
    cleanup_quiet(NAME)
    for companion in (read_name, none_name, NAME + DENY_SUFFIX):
        if companion:
            cleanup_quiet(companion)
    # 业务角色伴随账号的名字由角色码派生，cleanup 不重新派生（清理时角色清单未必传），
    # 因此按本轮随机 NAME 精确前缀删除：NAME 含时间戳且不可猜，也不是通配匹配。
    psql(
        f"""
        DELETE FROM {SCHEMA}.t_role_employee
         WHERE employee_id IN (SELECT employee_id FROM {SCHEMA}.t_employee
                                WHERE employee_id <> {SEED_EMPLOYEE_ID}
                                  AND left(login_name, {len(NAME) + 1}) = {q(NAME + '_')});
        DELETE FROM {SCHEMA}.t_employee
         WHERE employee_id <> {SEED_EMPLOYEE_ID}
           AND left(login_name, {len(NAME) + 1}) = {q(NAME + '_')};
        """
    )
    # 临时角色及其授权也要一起回收，否则会在 t_role / t_role_menu 里越积越多。
    role_codes = [f"{PREFIX.upper()}READ" if PREFIX else "E2E_READ",
                  f"{PREFIX.upper()}DENY" if PREFIX else "E2E_DENY"]
    for role_code in role_codes:
        psql(
            f"""
            DELETE FROM {SCHEMA}.t_role_menu
             WHERE role_id IN (SELECT role_id FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)});
            DELETE FROM {SCHEMA}.t_role WHERE role_code = {q(role_code)};
            """
        )
    print(f"[e2e-accounts] cleanup ok  login_name={NAME} (+{READ_SUFFIX}, +{NONE_SUFFIX}, +{DENY_SUFFIX}), roles={role_codes}")

# ===========================================================================
# 报表日期与采购单夹具
# ===========================================================================
# 报表 E2E 的受限日期与采购单夹具操作；账号由本文件的 account-setup/account-cleanup 管理。

def _cli(flag: str) -> str:
    """读取单个夹具参数。"""
    for i, token in enumerate(sys.argv):
        if token == flag and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return ""


def _all_cli(flag: str) -> list[str]:
    return [sys.argv[i + 1] for i, token in enumerate(sys.argv[:-1]) if token == flag]


def _schema() -> str:
    schema = SCHEMA
    if not re.fullmatch(r"[a-z_][a-z0-9_]*", schema):
        raise SystemExit("[w6-e2e] 非法 PostgreSQL schema 名称")
    return schema


def _unused_report_dates() -> None:
    guard()
    count = int(_cli("--count") or "2")
    if count < 1 or count > 10:
        raise SystemExit("[w6-e2e] --count 必须在 1 到 10 之间")
    schema = _schema()
    sql = f"""
        SELECT candidate.report_date::text
          FROM (
              SELECT (timezone('Asia/Shanghai', CURRENT_TIMESTAMP)::date - day_offset)::date AS report_date
                FROM generate_series(2, 90) AS day_offsets(day_offset)
          ) candidate
         WHERE NOT EXISTS (
                   SELECT 1 FROM {schema}.xsy_report_purchase_daily daily
                    WHERE daily.report_date = candidate.report_date
               )
           AND NOT EXISTS (
                   SELECT 1 FROM {schema}.xsy_purchase_order purchase_order
                    WHERE purchase_order.deleted = FALSE
                      AND purchase_order.submitted_at >= candidate.report_date::timestamp AT TIME ZONE 'Asia/Shanghai'
                      AND purchase_order.submitted_at < (candidate.report_date + 1)::timestamp AT TIME ZONE 'Asia/Shanghai'
                      AND purchase_order.status IN ('SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED')
               )
         ORDER BY candidate.report_date DESC
         LIMIT {count};
    """
    values = [line.strip() for line in psql(sql, value_only=True).splitlines() if line.strip()]
    if len(values) != count:
        raise SystemExit(f"[w6-e2e] 最近 90 天内只找到 {len(values)} 个可用每日清单日期")
    print("\n".join(values))


def _backdate_purchase_order() -> None:
    guard()
    try:
        report_date = date.fromisoformat(_cli("--report-date"))
    except ValueError as error:
        raise SystemExit("[w6-e2e] --report-date 必须是 yyyy-MM-dd") from error
    remark = _cli("--remark")
    ids = [int(value) for value in _all_cli("--order-id")]
    if not remark or not ids or any(order_id <= 0 for order_id in ids) or len(ids) != len(set(ids)):
        raise SystemExit("[w6-e2e] 回填提交时间需要唯一的 --order-id 列表和 --remark")
    schema = _schema()
    id_list = ",".join(str(order_id) for order_id in ids)
    sql = f"""
        UPDATE {schema}.xsy_purchase_order
           SET submitted_at = (DATE {q(report_date.isoformat())} + TIME '12:00:00') AT TIME ZONE 'Asia/Shanghai'
         WHERE id IN ({id_list})
           AND remark = {q(remark)}
           AND deleted = FALSE
           AND status IN ('SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED')
        RETURNING id;
    """
    updated = {
        int(line.strip())
        for line in psql(sql, value_only=True).splitlines()
        if line.strip().isdigit()
    }
    if updated != set(ids):
        raise SystemExit(f"[w6-e2e] 只回填了 {len(updated)} / {len(ids)} 张指定且有效的采购单")


def _cleanup_daily_report_fixture() -> None:
    guard()
    schema = _schema()
    report_dates: list[str] = []
    for value in _all_cli("--report-date"):
        try:
            report_dates.append(date.fromisoformat(value).isoformat())
        except ValueError as error:
            raise SystemExit("[w6-e2e] --report-date 必须是 yyyy-MM-dd") from error
    ids = [int(value) for value in _all_cli("--order-id")]
    remark = _cli("--remark")
    if not report_dates or len(report_dates) != len(set(report_dates)):
        raise SystemExit("[w6-e2e] 清理每日清单 fixture 需要唯一的 --report-date 列表")
    if ids and (not remark or any(order_id <= 0 for order_id in ids) or len(ids) != len(set(ids))):
        raise SystemExit("[w6-e2e] 清理采购单需要唯一的 --order-id 列表和 --remark")
    date_list = ",".join(f"DATE {q(value)}" for value in report_dates)
    statements = [
        "BEGIN",
        f"DELETE FROM {schema}.xsy_report_purchase_daily_item WHERE report_date IN ({date_list})",
        f"DELETE FROM {schema}.xsy_report_purchase_daily WHERE report_date IN ({date_list})",
    ]
    if ids:
        id_list = ",".join(str(order_id) for order_id in ids)
        owner_clause = f"id IN ({id_list}) AND remark = {q(remark)}"
        statements.extend([
            f"DELETE FROM {schema}.xsy_purchase_order_item WHERE purchase_order_id IN "
            f"(SELECT id FROM {schema}.xsy_purchase_order WHERE {owner_clause})",
            f"DELETE FROM {schema}.xsy_purchase_order WHERE {owner_clause}",
        ])
    statements.append("COMMIT")
    psql(";\n".join(statements) + ";")

# ===========================================================================
# 商品新增导入模板
# ===========================================================================
# 把商品「新增导入」模板填成可提交的新增件（仅供 E2E / 手工验收使用）。
#
# 为什么需要：Wave 1 的浏览器验收要求真实跑一遍「下载新增模板 → 填一行多 SKU → 页面以新增模式上传 →
# 列表见新商品」，而前端不解析 xlsx（导入是整文件上传给后端校验），所以待上传文件必须由测试侧生成。
#
# 做法与约束（对齐 `ProductImportService` 的新增读法）：
#   · 输入只能是 `GET /scm/product/import/template?mode=CREATE`：新增与更新是**不同列集合**
#     （更新模板多四列定位键），喂错模板应由这里显性报错，而不是等后端回 HEADER_INVALID；
#   · 每个 `--row` 生成一个数据行，同一 SPU 的多行即「一个商品多个 SKU」，这是新增模式独有的组织方式；
#     后端要求同一 SPU 各行的 商品名称 / 分类编码 / 商品上下架 / 储存方式 / 标签编码 完全一致，
#     所以这些 SPU 级列要在每一行都重复给出；
#   · 未被任何 `--row` 提到的列一律留空：新增语义里的空白就是「不提供该字段」，与更新语义的
#     「空白=保持原值」相反，因此这里没有 `--blank`；
#   · 「模板版本」取模板自带值、拒绝调用方改写，测试不硬编码服务端常量；
#   · 只按表头名取列，不按下标写死：列顺序变化应由表头对不上而显性报错，而不是静默改错列；
#   · 值以字符串写入，避免 openpyxl 把数字转成数值单元格后与后端的文本正则不一致。
#
# 用法：
#     python tools/e2e_fixtures.py product-create --in template.xlsx --out create.xlsx \
#         --row "SPU编码=SPU0001;商品名称=示例蔬菜;分类编码=FRESH;商品上下架=ON_SHELF;SKU编码=SKU0001;规格名称=500g/份;销售单位=份;商品类型=STANDARD;市场价=9.9000;SKU上下架=ON_SHELF;默认SKU=是" \
#         --row "SPU编码=SPU0001;SKU编码=SKU0002;商品名称=示例蔬菜;分类编码=FRESH;商品上下架=ON_SHELF;规格名称=1kg/箱;销售单位=箱;商品类型=STANDARD;市场价=18.8000;SKU上下架=ON_SHELF;默认SKU=否"
# 输出（stdout，单行 JSON）：
#     {"rows": 2, "templateVersion": "1.0"}

TEMPLATE_VERSION_COLUMN = "模板版本"
# 更新模板独有：出现在新增输入里说明下载时选错了模式。
UPDATE_ONLY_COLUMNS = ("SPU ID", "SPU版本", "SKU ID", "SKU版本")


class InputError(Exception):
    """调用方传参或输入文件不符合契约；主流程统一转成退出码 1。"""


def read_headers(sheet) -> dict:
    headers = {}
    for cell in sheet[1]:
        if cell.value is not None:
            headers[str(cell.value).strip()] = cell.column
    return headers


def parse_row(spec: str) -> dict:
    edits = {}
    for pair in spec.split(";"):
        if not pair.strip():
            continue
        if "=" not in pair:
            raise InputError(f"--row 必须是 列名=值;列名=值：{pair}")
        column, value = pair.split("=", 1)
        edits[column.strip()] = value
    if not edits:
        raise InputError("--row 至少要有一个 列名=值")
    if TEMPLATE_VERSION_COLUMN in edits:
        raise InputError(f"「{TEMPLATE_VERSION_COLUMN}」保留模板自带值，不能由调用方改写")
    return edits


def build_rows(sheet, headers, rows) -> str:
    missing = [name for name in headers if name in UPDATE_ONLY_COLUMNS]
    if missing:
        raise InputError(f"输入含更新定位键列（{', '.join(missing)}），那是 mode=UPDATE 的模板")
    unknown = sorted({name for row in rows for name in row} - set(headers))
    if unknown:
        raise InputError(f"表头里没有这些列：{', '.join(unknown)}")
    version_column = headers[TEMPLATE_VERSION_COLUMN]
    # 版本串由服务端生成，调用方不硬编码；缺失即说明输入不是可用模板。
    template_version = sheet.cell(row=2, column=version_column).value
    if template_version in (None, ""):
        raise InputError(f"模板第 2 行的「{TEMPLATE_VERSION_COLUMN}」为空，无法生成新增文件")
    # 样例行的编码、分类、单价一旦被当成真实数据提交，会静默造出一条脏商品，所以整行重建。
    for index, row in enumerate(rows):
        number = 2 + index
        for column in headers.values():
            sheet.cell(row=number, column=column).value = None
        for name, value in row.items():
            sheet.cell(row=number, column=headers[name]).value = value
        sheet.cell(row=number, column=version_column).value = str(template_version)
    return str(template_version)


def product_create_main() -> int:
    parser = argparse.ArgumentParser(description="填写商品新增导入模板")
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    parser.add_argument("--row", dest="row_specs", action="append", default=[], metavar="列名=值;列名=值")
    args = parser.parse_args()

    try:
        if not args.row_specs:
            raise InputError("至少要给一个 --row")
        rows = [parse_row(spec) for spec in args.row_specs]
        book = _load_openpyxl().load_workbook(args.src)
        sheet = book.active
        version = build_rows(sheet, read_headers(sheet), rows)
        book.save(args.dst)
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps({"rows": len(rows), "templateVersion": version}, ensure_ascii=False))
    return 0

# ===========================================================================
# 商品更新导入模板
# ===========================================================================
# 生成商品「更新导入」用的 xlsx（仅供 E2E / 手工验收使用）。
#
# 为什么需要：Wave 1 的浏览器验收要真实跑一遍「以更新模式上传 → 页面见新值 → 旧文件重放被拒」，
# 而前端不解析 xlsx（导入是整文件上传给后端校验），所以待上传文件必须由测试侧生成。
#
# 两种输入（普通列表导出与更新模板是**不同列集合**：导出含分类路径与标签名称供人看，模板要分类编码与标签编码）：
#   · `--template`：输入 `GET /scm/product/import/template?mode=UPDATE`，把第 2 行占位样例换成真实定位键；
#   · 默认：输入列表导出文件，只改业务列。
#
# 做法与约束（对齐 `ProductImportService` 的更新读法）：
#   · 默认模式下定位键四列（SPU ID / SPU版本 / SKU ID / SKU版本）**永不改动**——它们既是行的定位键，
#     也是乐观锁版本，重写它等于伪造并发基线；
#   · `--template` 模式是唯一例外：模板自带样例 ID 与库里无关，必须换成调用方从详情接口取到的真实 ID 与版本；
#     该行未被 `--set` 指定的列一律清空（空白=保持原值，正是更新语义），「模板版本」保留模板自带值，
#     让测试不硬编码服务端常量；
#   · 只按表头名取列，不按下标写死：列顺序变化应由表头对不上而显性报错，而不是静默改错列；
#   · `--blank` 用来证明「空白列保持原值」这条更新语义（不是抹掉），被清空的列原值回写到 JSON 里，
#     由调用方去库里断言原值仍在；
#   · 值以字符串写入，避免 openpyxl 把数字/日期转成数值单元格后与后端的文本正则不一致。
#
# 用法：
#     python tools/e2e_fixtures.py product-update --template --in template.xlsx --out update.xlsx \
#         --set "商品ID=123" --set 商品版本=0 --set "商品规格ID=456" --set 商品规格版本=0 --set 别名=新别名
#     python tools/e2e_fixtures.py product-update --in export.xlsx --out update.xlsx \
#         --set 别名=新别名 --blank 品牌
# 输出（stdout，单行 JSON）：
#     {"rows": 1, "template": true, "sets": {"别名": "新别名"}, "templateVersion": "1.0"}
#     {"rows": 2, "sets": {"别名": "新别名"}, "originals": {"别名": "旧值", "品牌": "旧品牌"}}

LOCATION_KEYS = ("商品ID", "商品版本", "商品规格ID", "商品规格版本")


def parse_edits(args) -> dict:
    edits = {}
    for item in args.sets:
        if "=" not in item:
            raise InputError(f"--set 必须是 列名=值：{item}")
        column, value = item.split("=", 1)
        edits[column.strip()] = value
    for column in args.blanks:
        edits[column.strip()] = None
    if not edits:
        raise InputError("至少要给一个 --set 或 --blank")
    touched = set(edits) & set(LOCATION_KEYS)
    if touched and not args.template:
        raise InputError(f"定位键列不可改写：{', '.join(sorted(touched))}")
    return edits


def check_columns(headers, edits, template_mode: bool) -> None:
    missing = [name for name in edits if name not in headers]
    if missing:
        raise InputError(f"表头里没有这些列：{', '.join(missing)}")
    if template_mode and TEMPLATE_VERSION_COLUMN not in headers:
        raise InputError(f"更新模板缺少「{TEMPLATE_VERSION_COLUMN}」列，文件不可用")


def rewrite_template_row(sheet, headers, edits) -> str:
    version_column = headers[TEMPLATE_VERSION_COLUMN]
    # 版本串由服务端生成，调用方不硬编码；缺失即说明输入不是可用模板。
    template_version = sheet.cell(row=2, column=version_column).value
    if template_version in (None, ""):
        raise InputError(f"模板第 2 行的「{TEMPLATE_VERSION_COLUMN}」为空，无法生成更新文件")
    # 样例行的 SPU编码/名称/单位等一旦被当成真实数据提交，会静默覆盖线上字段，所以整行重建。
    for column in headers.values():
        sheet.cell(row=2, column=column).value = None
    for name, value in edits.items():
        if name == TEMPLATE_VERSION_COLUMN:
            raise InputError(f"「{name}」保留模板自带值，不能由调用方改写")
        sheet.cell(row=2, column=headers[name]).value = value
    sheet.cell(row=2, column=version_column).value = str(template_version)
    return str(template_version)


def rewrite_export_rows(sheet, headers, edits) -> int:
    rows = 0
    for row in range(2, sheet.max_row + 1):
        if all(sheet.cell(row=row, column=col).value in (None, "") for col in headers.values()):
            continue    # 导出末尾可能留有空行，空行不参与更新也不计数
        for name, value in edits.items():
            sheet.cell(row=row, column=headers[name]).value = value
        rows += 1
    if rows == 0:
        raise InputError("导出文件里没有数据行，无法生成更新文件")
    return rows


def product_update_main() -> int:
    parser = argparse.ArgumentParser(description="填写商品更新导入文件")
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    parser.add_argument("--set", dest="sets", action="append", default=[], metavar="列名=值")
    parser.add_argument("--blank", dest="blanks", action="append", default=[], metavar="列名")
    parser.add_argument("--template", action="store_true",
                        help="输入是更新模板：第 2 行样例换成真实定位键，未指定的列清空（=保持原值）")
    args = parser.parse_args()

    try:
        edits = parse_edits(args)
        book = _load_openpyxl().load_workbook(args.src)
        sheet = book.active
        headers = read_headers(sheet)
        check_columns(headers, edits, args.template)
        if args.template:
            payload = {"rows": 1, "template": True,
                       "sets": {k: v for k, v in edits.items() if v is not None},
                       "templateVersion": rewrite_template_row(sheet, headers, edits)}
        else:
            originals = {name: sheet.cell(row=2, column=headers[name]).value for name in edits}
            payload = {"rows": rewrite_export_rows(sheet, headers, edits),
                       "sets": {k: v for k, v in edits.items() if v is not None},
                       "originals": originals}
        book.save(args.dst)
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps(payload, ensure_ascii=False))
    return 0

# ===========================================================================
# 盘点快照导入模板
# ===========================================================================
# 把盘点快照导入模板填成「可成功导入」的实盘件（仅供 E2E / 手工验收使用）。
#
# 为什么需要：Wave 6 的浏览器验收要求真实跑一遍「导出模板 → 填实盘 → 导入建草稿」，
# 而前端页面本身不解析 xlsx（导入是整文件上传给后端），所以“填好的文件”必须由测试侧生成。
#
# 做法与约束（对齐 `InventoryStocktakeImportService` 的读法）：
#   · 只写「实盘数量」列（第 7 列），快照凭证 / SKU 编码 / 账面快照等受保护列**逐字节不动**——
#     凭证是 HMAC 签名的字符串单元格，重写它等于伪造来源集合；
#   · 填的是「账面快照 + delta」，确保确认阶段真的产生盘盈/盘亏流水（漂移反例依赖这一点）；
#   · 以字符串写入：后端正则 `[0-9]{1,14}(\\.[0-9]{1,4})?` 按文本校验，数值单元格会经
#     `NumberToTextConverter` 转换，留成字符串可避免浮点尾巴；
#   · 结果为负时夹到 0（实盘量不允许负数）。
#
# 用法：
#     python tools/e2e_fixtures.py stocktake --in template.xlsx --out filled.xlsx [--delta 1]
# 输出（stdout，单行 JSON）：
#     {"rows": 3, "actuals": {"SKU-A": "12.5", ...}}

ACTUAL_COLUMN = 7      # 1-based，对应表头「实盘数量」
BOOK_COLUMN = 6        # 1-based，对应表头「账面数量快照」


def stocktake_main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    parser.add_argument("--delta", default="1")
    args = parser.parse_args()

    try:
        from openpyxl import load_workbook  # type: ignore
    except ImportError:
        print("[fill-stocktake] 缺少 python 包 openpyxl。请执行：\n"
              "  python -m pip install openpyxl", file=sys.stderr)
        return 2

    try:
        delta = Decimal(args.delta)
    except InvalidOperation:
        print(f"[fill-stocktake] --delta 不是合法数字: {args.delta!r}", file=sys.stderr)
        return 2

    # data_only=False：必须保留公式与全部原始单元格的值，凭证列不能被“计算结果”替换。
    book = load_workbook(args.src)
    sheet = book.active
    actuals: dict[str, str] = {}
    for row in range(2, sheet.max_row + 1):
        sku_code = sheet.cell(row=row, column=2).value
        if sku_code is None or str(sku_code).strip() == "":
            continue  # 空行：模板末尾可能有装饰性空行，不能给它填值
        snapshot = str(sheet.cell(row=row, column=BOOK_COLUMN).value or "0").strip()
        try:
            value = Decimal(snapshot) + delta
        except InvalidOperation:
            print(f"[fill-stocktake] 第 {row} 行账面快照不是数字: {snapshot!r}", file=sys.stderr)
            return 2
        if value < 0:
            value = Decimal(0)
        text = format(value.normalize(), "f")
        sheet.cell(row=row, column=ACTUAL_COLUMN).value = text
        actuals[str(sku_code).strip()] = text

    if not actuals:
        print("[fill-stocktake] 模板没有可填的数据行（该仓库可能没有库存余额）", file=sys.stderr)
        return 2

    book.save(args.dst)
    print(json.dumps({"rows": len(actuals), "actuals": actuals}, ensure_ascii=False))
    return 0

def _load_openpyxl():
    """按需导入 openpyxl：账号/报表命令不应该因为没装 xlsx 依赖就跑不起来。"""
    try:
        import openpyxl  # type: ignore
    except ImportError:
        print("[e2e-fixtures] 缺少 python 包 openpyxl。请执行：\n"
              "  python -m pip install -r tools/requirements-dev.txt", file=sys.stderr)
        raise SystemExit(2)
    return openpyxl




# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
REPORT_ACTIONS = {
    "unused-report-dates": _unused_report_dates,
    "backdate-purchase-order": _backdate_purchase_order,
    "cleanup-daily-report-fixture": _cleanup_daily_report_fixture,
}


def _usage() -> str:
    return __doc__.split("用法：")[0].strip() if "用法：" in __doc__ else __doc__


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    # 调用方（E2E）按 UTF-8 解 stdout JSON，而 Windows 默认控制台是 GBK：中文列名会被编成
    # GBK 字节再按 UTF-8 解码，键名对不上等于静默丢数据，所以在这里钉死输出编码。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    if not argv:
        print(_usage(), file=sys.stderr)
        return 2
    command, rest = argv[0], argv[1:]
    if command in ("account-setup", "account-cleanup"):
        sys.argv = [sys.argv[0], *rest]
        configure_accounts("setup" if command == "account-setup" else "cleanup")
        (setup if command == "account-setup" else cleanup)()
        return 0
    if command == "report":
        action = rest[0] if rest else ""
        if action not in REPORT_ACTIONS:
            print(_usage(), file=sys.stderr)
            return 2
        sys.argv = [sys.argv[0], *rest[1:]]
        configure_accounts(action)
        if WAVE != "w6":
            raise SystemExit("[e2e-report-fixture] 必须指定 --wave w6")
        REPORT_ACTIONS[action]()
        return 0
    if command in ("product-create", "product-update", "stocktake"):
        sys.argv = [sys.argv[0], *rest]
        return {"product-create": product_create_main,
                "product-update": product_update_main,
                "stocktake": stocktake_main}[command]()
    print(_usage(), file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
