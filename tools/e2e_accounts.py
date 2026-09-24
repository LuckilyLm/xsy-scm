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

数据库：本机 docker `xsy-pg-v2`（127.0.0.1:15432），schema=xsy_v2；**库名必须由 XSY_V2_PG_DB 显式给出**，
不给即拒绝执行（默认值会把账号建到过期快照库，登录只报「登录名或密码错误」）。
可用环境变量覆盖：XSY_V2_PG_CONTAINER / XSY_V2_PG_DB（必填）/ XSY_V2_PG_SCHEMA / XSY_V2_PG_USER

命令行：
    python tools/e2e_accounts.py setup   --prefix w1_e2e_ --name NAME --password PASS
    python tools/e2e_accounts.py cleanup --prefix w1_e2e_ --name NAME
或（供 Playwright execFileSync 使用的简化形式，通过环境变量传参）：
    W1_E2E_NAME / W1_E2E_PASSWORD  + argv[1] = setup|cleanup

安全约束：
  · login_name 必须以 `<波次>_e2e_` 开头（`_e2e_` 是硬性标记，波次如 w1 / w8 / f0），否则拒绝执行；
  · 永不触碰种子管理员行（login_name = 'admin' / employee_id = 1）；
  · cleanup 只按 login_name 精确删除，不做任何前缀通配删除。
"""

import os
import re
import subprocess
import sys

CONTAINER = os.environ.get("XSY_V2_PG_CONTAINER", "xsy-pg-v2")
DB = os.environ.get("XSY_V2_PG_DB", "")
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
NAME = _arg("--name", ["E2E_NAME"] + [f"W{i}_E2E_NAME" for i in range(1, 8)])
PASSWORD = _arg("--password", ["E2E_PASSWORD"] + [f"W{i}_E2E_PASSWORD" for i in range(1, 8)])
PREFIX = _arg("--prefix", ["E2E_PREFIX"], "")

# 伴随账号有两种，语义互斥，不能共用一个：
#   `<NAME>_read`：挂只读角色（页面菜单 + 只读按钮），用于「页面能打开、写入按钮被隐藏、写接口 30005」；
#   `<NAME>_none`：**不挂任何角色**，用于「菜单不下发 → 深链落到 404、连查询都被拒」。
# 只读角色一旦授了 `:query` 按钮，就再也没法验证后一种场景，所以两者分开建。
READ_SUFFIX = "_read"
NONE_SUFFIX = "_none"
READ_NAME = NAME + READ_SUFFIX if NAME else ""

# 第三个伴随账号（按需创建）：`<NAME>_deny` —— 授权面与只读账号一致，但**显式扣掉**指定权限码。
# 为什么需要它：AND 模式的双权限读端点（采购缺口预览要求「需求查询 ∧ 库存余额查询」，
# 客户 360 常购商品要求「客户查询 ∧ 订单查询」）在通用只读角色下两个权限都有，
# 「缺其中一项必须被拒」这条反例只能由一个「有 A 没 B」的角色来证。
# 触发方式：`--deny` 或环境变量 `E2E_DENY`，多个权限码用逗号分隔；不传则完全不建这个账号。
DENY_SUFFIX = "_deny"
DENY = _arg("--deny", ["E2E_DENY"], "")


# 正式业务角色账号（按需创建）：`<NAME>_<角色短名>`，administrator_flag=false 且挂 V56 种下的正式角色。
# 为什么不能拿上面的只读夹具代替：只读角色是**功能权限**反例用的合成角色——它持有所有以
# `:query` 结尾的按钮权限，因此 `scm:*:scope:all:query` 也在内，数据范围对它等于不收窄。
# 数据范围的正反例只能由「真实角色 + 真实授权行」的账号来证（裁决第 5 条：超管通过不算证据）。
BUSINESS_ROLES = _arg("--roles", ["E2E_BUSINESS_ROLES"], "")

# t_employee.login_name 是 VARCHAR(30)，角色短名按剩余长度截断，宁可难看也不能溢出列宽
LOGIN_NAME_MAX = 30


def business_role_codes() -> list[str]:
    """本次要挂的正式角色码；空列表表示不建业务角色账号。"""
    return [token.strip() for token in BUSINESS_ROLES.split(",") if token.strip()]


# 第二个管理员账号（按需创建）：`<NAME>_two`。
# 为什么需要它：报损报溢 / 规格转换现在禁止「录单人自己审批」（裁决第 8 条），
# 单个管理员账号既录单又审批会被 41065 挡下，审批类浏览器用例因此必须有第二个人。
# 刻意仍用 administrator_flag=true 而不是正式角色：正式仓管角色没有仓库授权行，
# 会被同一轮的写侧范围守卫挡住，那样测的就不是「换人审批」而是范围守卫了。
SECOND_ADMIN = _arg("--second-admin", ["E2E_SECOND_ADMIN"], "")
SECOND_ADMIN_SUFFIX = "_two"


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
            "W{1..7}_E2E_NAME / W{1..7}_E2E_PASSWORD 环境变量。"
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


if __name__ == "__main__":
    if ACTION == "setup":
        setup()
    elif ACTION == "cleanup":
        cleanup()
    else:
        raise SystemExit(
            "用法: e2e_accounts.py setup|cleanup "
            "[--name NAME] [--password PASS] [--prefix PREFIX]\n"
            "  或通过环境变量 W{1..7}_E2E_NAME / W{1..7}_E2E_PASSWORD 传参"
        )
