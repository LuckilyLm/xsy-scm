# V17 Flyway 版本冲突审计报告

> **后续仓库复核（2026-09-17）**：本文保留 V17/V18 修复与数据库实测的历史记录。
> §13.7b 的 SQL 换行分布和“必须重算清单”的判断已被后续逐文件验证修正；
> §2.1 / §13.6 的 packed-ref 根因归因尚无充分证据，手工双写引用不作为维护规范。
> 当前处理与独立 clone 验证见 [仓库整理与修复记录](../maintenance/2026-09-17-repository-cleanup.md)。


日期：2026-09-17
状态：**AUDIT COMPLETE → REPAIR IMPLEMENTED & VERIFIED**（审计结论 §0–§9 保持只读取证口径，§10 起的「已实施」部分由 §13 记录）
仓库：`xsy-scm`（GitHub `LuckilyLm/xsy-scm`）
审计基线：本地 `HEAD` = `origin/main` = `ls-remote` = **`48134bf`**（已对齐，见 §2.1）

> **本次审计的边界（严格遵守）**
> - **审计阶段未修改任何 migration 文件**（两个 V17 原样保留，字节未动）。
> - **全程未执行 `flyway repair`**，未手工改过 `flyway_schema_history` 的任何一行。
> - **未进入 W6**，未新增任何业务代码。
> - 所有 Flyway 探测均使用 `info()` / `validate()` 两个**只读** API。
> - §0–§9 是纯只读审计的原始产出，**保留当时结论不做事后修饰**；修复动作与实测结果统一记在 §13。
>
> **修复实施说明（2026-09-17，用户批准后）**
> 用户在读完本报告后给出决策：**采用推荐方案（`V17__scm_menu_icons.sql` → `V18__scm_menu_icons.sql`）
> = 是；新增迁移版本唯一性门禁 = 否；清理变更与修复分两次提交 = 分开；执行 fresh-DB 实测 = 是。**
> 据此实施了 §10.1 的 8 项配套改动，并完成 fresh-DB `migrate` 实测（结果见 §5.2 与 §13）。
> 除「改名」外，未对任何已上线的 migration 内容做改动，未执行 `repair`。

---

## 0. 结论摘要

**确认存在重复版本号，且它不是一个「静默分叉」，而是一个已经生效的硬阻断。**

```text
V17__scm_menu_icons.sql              ← 由 fdfd643 (W5.5) 引入，2026-09-17 16:01:33
V17__sa_config_file_upload_size.sql  ← 由 3f7754b (F0)  引入，2026-09-17 17:28:24
```

关键判定：

| 判定项 | 结果 |
| --- | --- |
| 是否重复版本 | **是**，两个文件都解析为 version 17 |
| 是否已形成 migration history fork | **尚未形成「双库分歧」**，但**已形成「无法迁移」状态** |
| 当前仓库能否启动 Flyway | **不能**。`info()` 与 `validate()` 双双抛 `FlywayException: Found more than one migration with version 17`（实测，见 §6） |
| 开发库现状 | `version 17` = **`V17__sa_config_file_upload_size.sql`**，checksum `998995225`，已成功应用（16:10:05） |
| `V17__scm_menu_icons.sql` 是否曾被应用 | **从未**。`t_menu.icon` 全为空（241 条菜单中 0 条 SCM 菜单有图标） |
| 修复是否需要 `repair` | **不需要**（推荐方案实测 `validate success=true`） |

**根因（我的责任）**：F0 的 migration 在 **15:15:51** 创建，当时工作区最大版本是 V16，选 V17 是对的；但 W5.5 的 `fdfd643` 在 **16:01:33** 已占用 V17 并推到远端。我的本地基线在 **17:24:55** 才通过 fast-forward 追上 `fdfd643`，**17:28:24 提交时未重新核对 migration 版本上限**，于是把第二个 V17 一起提交并推送。F0 的静态保护检查只钉了「V1–V16 哈希一致」，没有检查「V17 是否已被占用」——这是检查项的缺口。

**修复后的实测终态（2026-09-17，详见 §13）：**

| 指标 | 修复前 | 修复后（实测） |
| --- | --- | --- |
| migration 版本 | 两个 V17（重复） | V1–V18，**唯一且连续** |
| Flyway `info()`（当前仓库） | FAILED `Found more than one migration with version 17` | **OK**，`resolved=19` |
| Flyway `validate()`（开发库） | FAILED | **`success=true`**（零 repair） |
| 开发库 schema version | 17 | **18**（V18 已应用，`t_menu` 图标 37 → 71） |
| 全新空库 `migrate()` | 不可执行 | **`initialSchemaVersion=18` / `success=true` / 74 表 / 0 失败** |
| `ScmPurchaseMigrationIT` | — | **4 / 0 / 0 / 0** |

---

## 1. 取证方法与证据来源

所有数字均为本次实测，未复制历史文档。

| 证据 | 取法 |
| --- | --- |
| 提交归属 | `git log --follow -- <path>`、`git show --stat`、`git ls-tree` |
| 文件到达时间 | 工作区文件 `mtime`（`ls --time-style=full-iso`） |
| DB 现状 | `psql -h 127.0.0.1 -p 15432 -U xsy_scm_app -d xsy_scm` 直查 `xsy_v2.flyway_schema_history` / `t_menu` / `t_config` |
| Flyway 行为 | **自建只读探针**：从运行中的 fat jar 解出 `flyway-core-11.7.2.jar` + `flyway-database-postgresql-11.7.2.jar` + `postgresql-42.7.7.jar`，调用 `info()` / `validateWithResult()`。探针位于仓外 `D:/Browser Download/.runtime/v17probe/`，**未写入仓库** |
| Flyway 版本 | fat jar 内 `BOOT-INF/lib/flyway-core-11.7.2.jar`；错误串来自 `org/flywaydb/core/internal/resolver/CompositeMigrationResolver.class` |

---

## 2. Repo current state

### 2.1 同步与 ref 状态（本次已对齐）

```text
HEAD          = 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
refs/heads/main (loose)        = 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
refs/heads/main (packed-refs:5)= 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
origin/main (loose)            = 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
origin/main (packed-refs:6)    = 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
git ls-remote origin           = 48134bf2d87e50a9a05f6b7f34f25b9d7caf2fcb
ahead / behind                 = 0 / 0
```

> **⚠️ 本仓库特有陷阱（本次再次命中）**：`git fetch` 报告 `3f7754b..48134bf main -> origin/main`、`FETCH_HEAD` 也写对了，但 `git rev-parse origin/main` 仍返回陈旧的 `3f7754b`。原因是 `refs/remotes/origin/main` **没有 loose ref**，只剩 `packed-refs` 里的陈旧条目，陈旧值直接接管。
> 修复必须 **loose + packed 双写**：先写 `.git/refs/remotes/origin/main`，再改 `packed-refs` 对应行；只改一边会被下一次 `pack-refs` 抹掉。`packed-refs` 已在改动前备份到仓外 `.runtime/packed-refs.backup-<HHMMSS>`。

### 2.2 对齐带来的远端增量（与本冲突无关）

`3f7754b..48134bf` 共 3 个提交，**均未触碰 `db/migration/`**：

```text
48134bf feat(scm): 统一时间显示格式，操作记录改用字段级差异表   (23 files, +802 −69)
b883d5f Merge branch 'main'
3546fef fix(role): 修正角色-员工分页查询的 roleId 类型
```

### 2.3 migration 目录现状

目录：`xsy-scm-server/sa-admin/src/main/resources/db/migration/`
文件总数 **18**（17 个版本 + 重复的 V17 一份）：

```text
V1__sa_system_poc_core.sql
V2__sa_system_login_rbac.sql
V3__sa_system_login_support_and_seed.sql
V4__sa_system_runtime_support.sql
V5__sa_system_remaining_tables.sql
V6__scm_product.sql
V7__scm_product_permissions.sql
V8__scm_customer_supplier.sql
V9__scm_customer_supplier_permissions.sql
V10__scm_pricing.sql
V11__scm_pricing_permissions.sql
V12__sa_oa_enterprise_employee_bigint.sql
V13__scm_sales_order.sql
V14__scm_sales_order_permissions.sql
V15__scm_purchase.sql
V16__scm_purchase_permissions.sql
V17__sa_config_file_upload_size.sql      ← F0（我的），275 B，mtime 2026-09-17 15:15:51
V17__scm_menu_icons.sql                  ← W5.5，    8089 B，mtime 2026-09-17 17:24:55
```

两个文件的 sha256：

```text
ed715accae11e4b44b6a0195b8e53b1fb60286d8e5236671106470e5e46da903 *V17__scm_menu_icons.sql
6d22bd494e1a17fbade70bb00b5742fc916e629be21457cf9bb1287fc07f6edd *V17__sa_config_file_upload_size.sql
```

### 2.4 两个 migration 的内容与性质

**`V17__sa_config_file_upload_size.sql`**（5 行，data-only）

```sql
-- F0 HD-2: data only. Preserve every other level3 protection setting and V1-V16.
UPDATE t_config
SET config_value = (config_value::jsonb || jsonb_build_object('maxUploadFileSizeMb', 20))::text,
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'level3_protect_config';
```

**`V17__scm_menu_icons.sql`**（85 行，data-only，幂等）

对 `xsy_v2.t_menu` 的 30 个菜单补 `icon`，全部带 `WHERE icon IS NULL OR icon = ''` 守卫；
不新增/删除菜单，不触碰权限字段。目标 `menu_id`：
`401–404 / 431–435 / 461–464 / 501–506 / 601–605 / 701–706 / 142 / 145 / 149 / 150`。

**两者语义完全独立、执行顺序无耦合**（一个改 `t_config`，一个改 `t_menu`），
因此「谁用哪个版本号」在业务上无约束——这让修复方案的选择空间很大。

### 2.5 关键提交归属（实测）

| 提交 | 时间 | 引入的 migration | 同批新增 |
| --- | --- | --- | --- |
| `fdfd643` feat(ui): apply default page config, SCM menu icons and 鲜蔬源 branding | **16:01:33** | `V17__scm_menu_icons.sql` | `docs/architecture/w5_5-applied-migrations.sha256` |
| `3f7754b` feat(f0): activate S3/MinIO object storage with per-folder read guard | **17:28:24** | `V17__sa_config_file_upload_size.sql` | 无 migration 清单 |

`fdfd643` 是 `3f7754b` 的**父提交**——即我提交 F0 时，仓库里**已经有** V17 了。

---

## 3. DB current state

目标库：`jdbc:postgresql://127.0.0.1:15432/xsy_scm`，schema `xsy_v2`。

### 3.1 `flyway_schema_history` 中 version 17

```text
 installed_rank | version |        description         |               script                | checksum  |        installed_on        | success | execution_time
----------------+---------+----------------------------+-------------------------------------+-----------+----------------------------+---------+----------------
             17 | 17      | sa config file upload size | V17__sa_config_file_upload_size.sql | 998995225 | 2026-09-17 16:10:05.159084 | t       |            241
```

**即：本开发库的 version 17 是 F0 的迁移。`V17__scm_menu_icons.sql` 从未在此库登记。**

### 3.2 全量历史（18 行 = 1 条基线 + 17 个版本）

```text
 rank | version |                  script                   |  checksum   | success
    0 |         | "xsy_v2"                                  |             | t
    1 | 1       | V1__sa_system_poc_core.sql                |  2020695199 | t
    2 | 2       | V2__sa_system_login_rbac.sql              |   664772474 | t
    3 | 3       | V3__sa_system_login_support_and_seed.sql  |   948915024 | t
    4 | 4       | V4__sa_system_runtime_support.sql         |   680414715 | t
    5 | 5       | V5__sa_system_remaining_tables.sql        |  1703536222 | t
    6 | 6       | V6__scm_product.sql                       |  1820501146 | t
    7 | 7       | V7__scm_product_permissions.sql           | -2047045211 | t
    8 | 8       | V8__scm_customer_supplier.sql             |  -456974555 | t
    9 | 9       | V9__scm_customer_supplier_permissions.sql |    81939250 | t
   10 | 10      | V10__scm_pricing.sql                      |   -83564911 | t
   11 | 11      | V11__scm_pricing_permissions.sql          | -1042005945 | t
   12 | 12      | V12__sa_oa_enterprise_employee_bigint.sql |   554708256 | t
   13 | 13      | V13__scm_sales_order.sql                  |  -945314735 | t
   14 | 14      | V14__scm_sales_order_permissions.sql      |  -160685528 | t
   15 | 15      | V15__scm_purchase.sql                     |  1541405941 | t
   16 | 16      | V16__scm_purchase_permissions.sql         |  1061512285 | t
   17 | 17      | V17__sa_config_file_upload_size.sql       |   998995225 | t
```

**0 条 failed，V1–V16 的 checksum 与脚本名完整无缺口。**

### 3.3 两个 migration 目标对象的实际数据

```text
-- F0 V17 的目标：已生效
t_config.level3_protect_config → maxUploadFileSizeMb = 20   （F0 从 30 改为 20）

-- W5.5 V17 的目标：完全未生效
t_menu 总数 = 241，有 icon = 37，无 icon = 204
抽查 W5.5 点名菜单：
  menu_id | menu_name  | icon
       142 | 公告详情   | (空)
       401 | 商品管理   | (空)
       431 | 客户管理   | (空)
       461 | 供应商管理 | (空)
       501 | 价格中心   | (空)
       601 | 销售订单   | (空)
       701 | 采购管理   | (空)
       706 | 仓库管理   | (空)
```

> 37 个有图标的菜单全部是 SmartAdmin 原生菜单，**没有一个是 W5.5 迁移点名的**。
> 结论：`V17__scm_menu_icons.sql` **从未在任何库执行过**。

### 3.4 运行中的后端（重要边界条件）

```text
PID 15076  java -jar xsy-scm-server/sa-admin/target/sa-admin-dev-3.0.0.jar
启动时间   2026-09-17 16:54:21     监听 18080
```

该 jar 构建于 **16:25**，实测其 `BOOT-INF/classes/db/migration/` 内**只有 1 个 V17**
（`V17__sa_config_file_upload_size.sql`）——因为 16:25 时工作区还没拿到 `fdfd643`。

**所以：当前进程是「冲突前」的产物，跑得好好的；但任何一次从当前源码重启都会启动失败。**
这也解释了为什么这个冲突没有在 F0 阶段暴露：F0 的 IT（16:45–16:51）与运行实例都早于 17:24:55。

---

## 4. 时间线：重复版本是如何产生的

```text
15:15:51  我创建 V17__sa_config_file_upload_size.sql
          └─ 当时工作区最大版本 = V16 → 选 V17 正确

16:01:33  fdfd643 (W5.5) 提交，占用 V17__scm_menu_icons.sql，并推到远端
          └─ 我的本地 HEAD 尚未包含它

16:10:05  开发库应用我的 V17 → flyway_schema_history rank 17 落定
16:25     构建 sa-admin-dev-3.0.0.jar（只含我的 V17）
16:45–16:51  F0 的 PG IT 运行（首轮 ScmPurchaseMigrationIT 失败 = 旧断言只写到 V16；修好后 187/0/0/5 通过）
          └─ 此时工作区仍只有一个 V17，所以 Flyway 未报重复
16:54:21  启动 dev 后端（PID 15076），至今正常

17:24:55  git fetch + fast-forward 追上 fdfd643
          └─ ★ 重复版本在此刻进入工作区（V17__scm_menu_icons.sql mtime = 17:24:55）

17:28:24  提交 3f7754b（含两个 V17）→ 17:2x 推送 fdfd643..3f7754b
          └─ ★ 冲突被发布到远端
```

**责任归属：** 我的 F0 提交是**把重复版本发布出去**的那一步。F0 的静态保护只钉了
「V1–V16 哈希 28/28 一致」，检查项缺少「新版本号是否已被占用」这一条——
在基线落后于远端时，这个缺口必然漏掉冲突。

---

## 5. Fresh DB expected behavior

### 5.1 当前仓库（两个 V17）→ **无法迁移**

Flyway 的重复版本检测发生在 **`CompositeMigrationResolver.resolveMigrations()`**，
即**迁移解析阶段**，**早于任何与数据库的比对**。因此：

```text
结果与 DB 状态无关 —— 空库、半新库、已迁移库，全部同样失败。
```

实测（见 §6 场景 1）：`info()` 与 `validate()` 都在解析阶段抛出
`org.flywaydb.core.api.FlywayException: Found more than one migration with version 17`，
并列出两个 Offenders。**新库连 V1 都跑不到。**

错误串来自实际使用的 Flyway 版本：

```text
BOOT-INF/lib/flyway-core-11.7.2.jar
org/flywaydb/core/internal/resolver/CompositeMigrationResolver.class
  → "Found more than one migration with version %s. Offenders: -> %s (%s) -> %s (%s)"
```

### 5.2 采用推荐修复后（`V17__scm_menu_icons.sql` → `V18__scm_menu_icons.sql`）→ **顺序安全**

新库会按 V1→V18 顺序执行。**V18 的写目标是 `t_menu` 中一批已存在的行**，
而这些行由更早的迁移创建——实测确认：

```text
menu_id 401 → 由 V7__scm_product_permissions.sql 创建
menu_id 431 → 由 V9__scm_customer_supplier_permissions.sql 创建
menu_id 461 → 由 V9__scm_customer_supplier_permissions.sql 创建
menu_id 501 → 由 V11__scm_pricing_permissions.sql 创建
menu_id 601 → 由 V14__scm_sales_order_permissions.sql 创建
menu_id 701 → 由 V16__scm_purchase_permissions.sql 创建
menu_id 706 → 由 V16__scm_purchase_permissions.sql 创建
menu_id 142 → 由 V3__sa_system_login_support_and_seed.sql 创建
```

**全部 < 18**，所以 V18 在新库上一定能命中目标行；且该迁移是幂等的（`icon IS NULL OR icon=''` 守卫），
即使某库已手工补过图标也不会覆盖。F0 的 V17 同样是 data-only，且 `t_config` 的那一行由 V1–V5 建立。

> **审计阶段未实测项（诚实声明）**：审计当时我没有真的建一个空库跑完整迁移（那属于写操作，超出
> 只读审计范围）。上表是「解析顺序 + 目标行来源」的静态推演，结论是顺序安全。

#### 5.2.1 实测补充（2026-09-17，用户批准后执行）

用户确认「4 是」后，我用**完全空白的独立测试库** `xsy_scm_test`（迁移前仅 `public` schema、0 张表，
无 `xsy_v2`，故与生产库零耦合）跑了一次真实 `migrate()`，migration 目录为修复后的 V1–V18：

```text
初始状态          空库（无 xsy_v2 schema）
执行              Flyway 11.7.2 migrate()  →  pending 列表完整列出 v1 … v18
结果              initialSchemaVersion = 18
                  migrationsExecuted   = 1（首轮）/ 0（幂等复跑）
                  success              = true
```

空库建出的 `xsy_v2.flyway_schema_history` 全量记录（**实测导出**）：

```text
rank  version   script                                    checksum      success
0     (基线)    << Flyway Schema Creation >>  ("xsy_v2")  —             t
1..16 1 … 16    V1__… … V16__scm_purchase_permissions.sql  逐条一致      t
17    17        V17__sa_config_file_upload_size.sql        998995225    t
18    18        V18__scm_menu_icons.sql                   -628217405    t
```

**关键对照：全新库 vs 存量开发库，结构完全收敛**

| 指标 | 全新库 `xsy_scm_test`（V1→V18 一次跑完） | 存量库 `xsy_scm`（V1→V16 → V17 → V18 增量演进） |
| --- | --- | --- |
| `xsy_v2` 表数 | 74 | 74 |
| 历史表行数 | 19 | 19 |
| 失败条数 | 0 | 0 |
| 基线条数（version 为空） | 1 | 1 |
| 最大版本 | 18 | 18 |
| `t_menu` 行数 | 241 | 241 |
| `t_menu` 有图标行数 | **71** | **71** |

两条完全不同的演进路径得到**逐项相同**的终态，且 `V18` 在空库上一次性命中全部 34 条 UPDATE
（`t_menu` 有图标 37 → 71，与存量库一致）。**§5.2 的静态推演由此升级为实测结论：顺序安全，且
V18 的 `WHERE icon IS NULL OR icon=''` 守卫在两种路径下都正确工作。**

> 测试库处置：`xsy_scm_test` 未被执行后自动清空（见 §13.4），当前保留为「V1–V18 已验证的
> fresh-build 参照库」，可用于后续波次的结构 diff。若需要再次作为空库目标，先
> `DROP SCHEMA xsy_v2 CASCADE` 即可。

---

## 6. Existing DB behavior（四场景实测矩阵）

用与生产完全相同的 Flyway 11.7.2，对**同一批 migration 文件**的四种排列做只读探测。
`info()` 触发解析；`validate()` 只读比对历史表 checksum。

| 场景 | migration 目录 | `info()` | `validate()` | 判定 |
| --- | --- | --- | --- | --- |
| **1. 当前仓库** | 两个 V17 并存 | **FAILED**<br>`Found more than one migration with version 17` | **FAILED**<br>同上 | **硬阻断，不可用** |
| **2. 对照**：只留 F0 的 V17 | 17 个文件 | OK，`schemaVersion=17`，`resolved=18` | **OK，`success=true`** | 与开发库完全一致，**零修复** |
| **3. 反向**：只留 menu_icons 作 V17 | 17 个文件 | OK，`schemaVersion=17` | **FAILED**<br>`VALIDATE_ERROR` | **破坏现有库** |
| **4. 推荐**：menu_icons 改名 V18 | 18 个文件 | OK，`schemaVersion=17`，`resolved=19` | `success=false`，但**仅为 pending**；加 `*:pending` 后 **`success=true`** | **正确** |

场景 3 的失败详情（这是「反向方案为什么不行」的铁证）：

```text
errorCode = VALIDATE_ERROR
msg       = Migrations have failed validation
invalid v17 sa config file upload size
   -> Migration checksum mismatch for migration version 17
   -> Applied to database : 998995225
   -> Resolved locally    : -628217405
   -> Either revert the changes to the migration, or run repair to update the schema history.
```

场景 4 的 `success=false` 详情（**这不是错误，是 pending 的正常报告**）：

```text
invalid v18 scm menu icons
   -> Detected resolved migration not applied to database: 18.
   -> To fix this error, either run migrate, or set -ignoreMigrationPatterns='*:pending'.
```

`validate-on-migrate: true` 在 `migrate()` 内部会自动忽略 pending，因此场景 4 在真实启动时
**直接放行**，下次 `migrate` 应用 V18 后即全绿。

**小结：**
- 场景 1 = 现状 → 所有环境（新库 / 老库）**全部起不来**。
- 场景 2 = 说明「保留 F0 的 V17」与开发库 100% 自洽。
- 场景 3 = 说明「改 F0 的版本号」会迫使**每个已有库**做 `repair`。
- 场景 4 = 唯一一个「新库能建、老库不用 repair、两个迁移都能生效」的方案。

---

## 7. Affected tests

### 7.1 必须改（1 个）

| 文件 | 位置 | 现状 | 影响 |
| --- | --- | --- | --- |
| `xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/purchase/ScmPurchaseMigrationIT.java` | `flywayHistoryIsAppendOnly()`，L155–170 | `assertThat(versions).containsExactly("1" … "17")`；`@DisplayName("flyway_schema_history：V1–V17 全部 success …")`；注释写「上限随获批的新迁移追加而抬升」 | 若采用推荐方案（新增 V18），**必须把上限抬到 `"18"`** 并同步 DisplayName/注释，否则该用例失败 |

> 注意：该用例只查 `flyway_schema_history`（DB 侧），**不扫描磁盘上的 migration 文件**，
> 所以它**没有能力**发现「两个 V17」——这正是冲突从测试网里漏过去的原因。
> 见 §10.4 的加固建议。

### 7.2 不受影响（已逐一核对）

其余 6 个 migration IT 都用**具体版本的 `IN (...)`**，不设上限，因此对 V17/V18 的增删不敏感：

```text
ScmProductMigrationIT                 → version IN ('6','7')
ScmCustomerSupplierMigrationIT        → version IN ('8','9') / IN ('6','7')
ScmPricingMigrationIT                 → version IN ('10','11')
ScmOrderMigrationIT                   → 断言 DAO 方法集，不涉及版本
ScmPurchasePermissionMigrationIT      → 断言 menu_id 集合（702–706），不涉及版本
SmartAdminOaEnterpriseEmployeeMigrationIT → version = '12'
```

`ScmPurchasePermissionMigrationIT` 另需注意：它断言 V16 授予的菜单 id 集合。
W5.5 的 menu_icons 迁移**只改 `icon`、不增删菜单、不动权限**，所以**不受影响**。

### 7.3 顺带暴露的测试网缺口

`tools/` 下有 `verify_w1_legacy.py` … `verify_w5_legacy.py`，**但没有 `verify_w5_5_legacy.py`**，
也没有任何脚本读取 `w5_5-applied-migrations.sha256`（见 §8.2）。
即 W5.5 的迁移清单**没有任何自动化消费者**。

---

## 8. Affected manifests

### 8.1 清单盘点

```text
docs/architecture/pg-closure-applied-migrations.sha256
docs/architecture/v3-applied-migrations.sha256
docs/architecture/w1-applied-migrations.sha256
docs/architecture/w2-applied-migrations.sha256
docs/architecture/w4-applied-migrations.sha256
docs/architecture/w5-applied-migrations.sha256      → V15, V16        （LF，校验通过）
docs/architecture/w5_5-applied-migrations.sha256    → V17__scm_menu_icons.sql （CRLF，校验失败）
```

**F0 没有对应的 `f0-applied-migrations.sha256`** —— F0 新增了 V17 却没有留下迁移清单，
这本身就是本次冲突未被发现的结构性原因之一。

### 8.2 `w5_5-applied-migrations.sha256` 有两个独立缺陷

```text
① 它是孤儿清单：全仓 grep 不到任何消费者
   $ grep -rn "w5_5-applied-migrations" --include=*.py --include=*.java --include=*.mjs --include=*.ts .
   → 无命中（没有 verify_w5_5_legacy.py）

② 它是 CRLF 行尾，sha256sum -c 直接读不出文件名
   $ od -c docs/architecture/w5_5-applied-migrations.sha256 | tail -2
     ... i c o n s . s q l \r \n        ← 91 字节，CRLF
   $ (cd .../db/migration && sha256sum -c .../w5_5-applied-migrations.sha256)
     sha256sum: 'V17__scm_menu_icons.sql'$'\r': No such file or directory
     V17__scm_menu_icons.sql: FAILED open or read
   （对照：w5-applied-migrations.sha256 是 LF，V15/V16 校验 OK）
```

即：**即使有人想用这个清单去校验，也校验不了。**

### 8.3 修复时需要动的清单

| 清单 | 是否需改 | 原因 |
| --- | --- | --- |
| `w5_5-applied-migrations.sha256` | **需要** | 若改名 V18，文件名要同步；顺手修 CRLF |
| `w5-applied-migrations.sha256` | 不需要 | 只含 V15/V16，实测校验通过 |
| 其余清单 | 不需要 | 均不含 V17 |
| 新增 `f0-applied-migrations.sha256` | **建议新增** | 补上 F0 缺失的迁移清单（含 V17） |

**修复后实测状态：**

```text
$ cd xsy-scm-server/sa-admin/src/main/resources/db/migration
$ sha256sum -c <repo>/docs/architecture/f0-applied-migrations.sha256
  V17__sa_config_file_upload_size.sql: OK
$ sha256sum -c <repo>/docs/architecture/w5_5-applied-migrations.sha256
  V18__scm_menu_icons.sql: OK
$ ls *.sql | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n -u | wc -l   → 18
$ ls *.sql | wc -l                                               → 18
  → 版本号 1–18，唯一且连续，无重复、无缺口
```

> 注意：两份清单记录的是**文件名**（相对路径），必须在 migration 目录下执行
> `sha256sum -c` 才能通过；在 `docs/architecture/` 下直接跑会报 `No such file or directory`。

---

## 9. Affected docs

| 文档 | 位置 | 现有表述 | 问题 |
| --- | --- | --- | --- |
| `docs/architecture/2026-09-17-smartadmin-native-feature-sync-report.md` | L1 标题、L34、L128、L132–133、L341、L346 | 标题即「**W5.5** SmartAdmin 原生功能同步报告」；`\| 新增迁移脚本 \| **0** \| V1–V16 冻结，未追加 V17 \|`；`## 4. 新增迁移：0`；`\| 是否追加 V17+ \| **否** \|`；「零新增迁移」 | **与该波次之后落地的 `fdfd643` 直接矛盾**：`fdfd643` 新增了 `V17__scm_menu_icons.sql`，却把清单命名为 `w5_5-*`。W5.5 的「零迁移」承诺与 W5.5 命名的迁移清单**同时存在**，文档层面已自相矛盾 |
| `docs/00-文档总览与索引.md` | L35 | 「[原生功能同步报告]：**W5.5 结论与证据**；本轮代码增量为零，零新增迁移、零菜单与权限改动。」 | 同上：把 W5.5 归给「零迁移」的那一波，掩盖了 `w5_5-applied-migrations.sha256` 的存在 |
| `AGENTS.md` | L72、L82–91、L99–100 | `F0 Object Storage Activation COMPLETE`；「F0 appended only the data-only V17」；「W4 adds V13/V14 and W5 adds V15/V16 normally; V1–V16 remain immutable」 | 「F0 appended **the** V17」在事实上变成了「F0 appended **a second** V17」。需在修复定案后改写为 V17/V18 的准确表述 |
| `docs/architecture/2026-09-17-f0-object-storage-approval.md` | L13（HD-2） | 「仅新增 **V17** data-only migration … V1–V16 禁止修改」 | 该批准是**基于「上限 = V16」的前提**做出的；前提已被 `fdfd643` 推翻，批准需要重新确认版本号 |
| `docs/architecture/2026-09-17-f0-object-storage-验收报告.md` | L35、L39、L111–121、L267–276 | A 阶段「V1–V16 哈希 28/28 一致；**仅新增 V17**」；E 阶段「V17 验证 … maxUploadFileSizeMb 30→20」；§6.3 记录把 IT 上限抬到 `"17"` 的最小修复 | 该报告把「V1–V16 不变」当作完整保护证明，**漏掉了「V17 已被占用」**。修复定案后需追加勘误说明，并补一条「新版本号占用检查」到检查项 |

**处置状态（2026-09-17 修复后）：**

| 文档 | 处置 | 备注 |
| --- | --- | --- |
| `smartadmin-native-feature-sync-report.md` | **未改动**（历史报告） | 其「零新增迁移」在**自身范围内**仍成立（只覆盖 `663d354`）；歧义由 `00-文档总览与索引.md` 的入口说明消除 |
| `00-文档总览与索引.md` | ✅ 已修正 | L35 明确限定「仅覆盖 native-parity 同步 `663d354`」，并指向 `w5_5-applied-migrations.sha256`；另新增本审计报告条目与迁移清单说明 |
| `AGENTS.md` | ✅ 已改写 | 迁移行显式列出 V17/V18 + 勘误块 + 「选版本号前必须先同步远端」硬规则 |
| `f0-object-storage-approval.md` | ✅ 已补记 | HD-2 补记「版本号在合并 W5.5 后重定」，明确 F0 的 V17 未动 |
| `f0-object-storage-验收报告.md` | ✅ 已追加勘误 | 顶部新增勘误节，逐条对照「当时 vs 现在」，并补上 `f0-applied-migrations.sha256` |

---

## 10. Recommended repair

### 10.1 推荐方案：把 `V17__scm_menu_icons.sql` 改名为 `V18__scm_menu_icons.sql`

```text
xsy-scm-server/sa-admin/src/main/resources/db/migration/
  V17__scm_menu_icons.sql  →  V18__scm_menu_icons.sql        （仅改名，内容不动）
  V17__sa_config_file_upload_size.sql                          （保持不变）
```

**为什么是这一个：**

1. **现有库不需要 `repair`。** 开发库的 `version 17` 已经是 `V17__sa_config_file_upload_size.sql`
   （checksum `998995225`）。保留它 → 场景 2/4 实测 `validate success=true`。
2. **改 F0 的版本号代价高得多。** 场景 3 实测：一旦让 menu_icons 占住 V17，
   每个已有库都会报 `Migration checksum mismatch for migration version 17`
   （Applied `998995225` vs Resolved `-628217405`），必须逐库 `flyway repair`——
   而那会**改写 schema history**，正是本次审计被要求避免的动作。
3. **被改名的那个从未被任何库应用。** 实测 `t_menu.icon` 中 0 条 SCM 菜单有图标，
   该迁移「未执行」的状态使改名**零成本、零历史包袱**。
4. **顺序安全**（§5.2）：V18 的目标行全部由 V3/V7/V9/V11/V14/V16 创建，均 < 18。
5. **语义无耦合**：两个迁移改的是不同表（`t_config` vs `t_menu`），先后无影响。

**配套改动（同样需要你批准后才做）：**

| # | 改动 | 说明 | 状态 |
| --- | --- | --- | --- |
| 1 | `V17__scm_menu_icons.sql` → `V18__scm_menu_icons.sql` | 只改名，字节不动 | ✅ 已实施 |
| 2 | `docs/architecture/w5_5-applied-migrations.sha256` | 文件名同步为 `V18__...`；**顺手把 CRLF 改回 LF**，让 `sha256sum -c` 可用 | ✅ 已实施 |
| 3 | `ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly` | 上限 `"17"` → `"18"`，同步 `@DisplayName` 与注释 | ✅ 已实施 |
| 4 | `docs/architecture/2026-09-17-f0-object-storage-验收报告.md` | 追加勘误：F0 的版本号在合并 W5.5 后需要重编号说明 | ✅ 已实施 |
| 5 | `AGENTS.md` | 迁移行改为准确表述（V17 = F0 文件上传大小，V18 = W5.5 菜单图标） | ✅ 已实施 |
| 6 | `docs/architecture/2026-09-17-f0-object-storage-approval.md` | HD-2 补记：版本号在 W5.5 合并后重定 | ✅ 已实施 |
| 7 | 新增 `docs/architecture/f0-applied-migrations.sha256` | 补上 F0 缺失的迁移清单（含 V17） | ✅ 已实施 |
| 8 | `docs/00-文档总览与索引.md` L35 | 修正 W5.5 归属表述，指向真实清单 | ✅ 已实施 |

> **加固项 2/3（迁移版本唯一性门禁 + IT 增强）按用户决策「否」未实施**——
> 详见 §10.4，缺口仍然存在，建议在 W6 开始前补上。

**不需要做的事：** 不执行 `flyway repair`；不改 `flyway_schema_history` 任何一行；
不需要重建开发库；不需要停掉 PID 15076（改名后重启即可生效）。

### 10.2 备选方案（不推荐）

| 方案 | 后果 | 评价 |
| --- | --- | --- |
| 把 F0 的迁移改名为 V18，让 menu_icons 保留 V17 | 每个已有库 checksum mismatch → 必须 `repair`（实测场景 3） | 不可接受：强制改写历史表，且与「V17 已被真实应用」的事实相反 |
| 删除 `V17__scm_menu_icons.sql`，图标手工补库 | 迁移丢失可复现性；`w5_5-applied-migrations.sha256` 变成悬空引用 | 不可接受：违背「Flyway 是唯一 schema 演进机制」 |
| 把两个迁移合并进一个 V17 文件 | 两个文件的 sha256 都会变 → 现有库 checksum mismatch → 仍需 `repair` | 不可接受 |
| 给 Flyway 开 `outOfOrder` / 放宽校验 | 掩盖问题，且重复版本是**解析期**错误，配置项无法绕过 | 无效 |

### 10.3 推荐修复后的预期状态（实测口径）

```text
migration 文件数        18（V1…V18，无重复，版本号连续无缺口）
现有开发库 validate     success=true（无需 repair）        ← 实测
现有开发库下次启动      migrate 应用 V18（补 34 条菜单图标）→ 全绿   ← 实测，已应用
新库                    按 V1→V18 顺序全量迁移，顺序安全      ← 实测，74 表 / 0 失败
ScmPurchaseMigrationIT  上限抬到 "18" 后通过                ← 实测 4/0/0/0
```

**实测数字汇总（2026-09-17 终态）：**

| 项 | 值 |
| --- | --- |
| 开发库 schema version | 17 → **18** |
| V18 applied time | 173 ms |
| 开发库 `flyway_schema_history` | `total=19 / failed=0 / baseline=1` |
| V18 checksum | `-628217405`（改名前后**不变**，因内容字节未动） |
| `V17__scm_menu_icons.sql` 改名前 sha256 | `ed715accae11e4b44b6a0195b8e53b1fb60286d8e5236671106470e5e46da903` |
| `V18__scm_menu_icons.sql` 改名后 sha256 | `ed715accae11e4b44b6a0195b8e53b1fb60286d8e5236671106470e5e46da903`（**一致**） |
| 开发库 `t_menu` 有图标 | 37 → **71** |
| 全新库 `xsy_scm_test` | 74 表 / 19 历史行 / 0 失败 / max=18 |
| 清单校验 | `w5_5-applied-migrations.sha256` 与 `f0-applied-migrations.sha256` 均 `sha256sum -c` **OK** |

### 10.4 加固建议（防止同类问题再次发生）

1. **F0 的教训：基线落后时不得选版本号。** 选 `V<N>` 之前必须先
   `git fetch` 并确认 `ls-remote` 的 `HEAD` 已是本地的祖先；否则应以 `ls-remote` 的树为准。
2. **补一条迁移版本唯一性门禁**（当前完全缺失）：在 CI / 验收脚本里加
   「migration 目录内 version 必须唯一，且必须连续无缺口」的检查。
   本次这个冲突**能通过 187 个 IT、能通过 build、能通过 lint**，因为没有任何检查项覆盖它。
3. **`ScmPurchaseMigrationIT` 增强**：除查 DB 历史外，增加一条对**磁盘目录**的断言
   （版本唯一 + 数量与历史一致），否则它永远发现不了「文件层重复版本」。
4. **清单必须可机器校验**：所有 `*-applied-migrations.sha256` 统一 LF；
   并让每波都有对应的 `verify_w<N>_legacy.py` 消费它（W5.5 目前缺失）。
5. **F0 报告 / AGENTS.md 的「V1–V16 哈希一致」不足以证明冻结**——
   应升级为「V1–V<当前上限> 全部哈希一致 **且** 无重复版本」。

---

## 11. 审计阶段的边界（当时未做 / 不做的事）

> 本节记录**只读审计阶段**（截至 §0–§10 产出时）的边界。修复动作与实测见 §13。

```text
✅ 已做（只读）
   - git fetch + fast-forward 对齐到 48134bf（含 packed-refs 双写修复）
   - 查 flyway_schema_history / t_menu / t_config
   - 用 Flyway 11.7.2 探针做 info() + validate() 四场景实测
   - 全仓搜索 V17 / 清单 / 测试 / 工具 / 文档引用

❌ 审计阶段未做（等用户确认）
   - 未改名任何 migration
   - 未执行 flyway repair
   - 未修改 flyway_schema_history
   - 未修改 ScmPurchaseMigrationIT
   - 未修改任何文档（本审计报告除外）
   - 未进入 W6
   - 未提交、未推送任何东西
```

**工作区状态提醒（审计时）**：`git status` 当时有 **371 项未提交变更**，与本次冲突**无关**——
其中 370 项是上一轮「清理项目日志与垃圾文件」的产物（`.gitignore` 加 `**/dist-verify/`
+ 369 项 `dist-verify` 索引移除），另 1 项是本审计报告（未跟踪的新文件）。
按用户决策「**3 分开**」，这 370 项已作为**提交 1** 独立提交
（`b6c4b43 chore(repo): stop tracking dist-verify build output`，370 files changed, +3/−579），
与本次修复**互不混合**。

---

## 12. 决策记录（用户 2026-09-17 答复）

用户对 §12 原四问的答复原文：**「1是 2否 3分开 4是」**

| # | 问题 | 答复 | 落地结果 |
| --- | --- | --- | --- |
| 1 | 是否采用推荐方案（`V17__scm_menu_icons.sql` → `V18__scm_menu_icons.sql` + §10.1 的 8 项配套改动）？ | **是** | 8 项全部实施，见 §13.1 |
| 2 | 是否同时执行 §10.4 的加固项 2/3（迁移版本唯一性门禁 + IT 增强）？ | **否** | **未实施**。缺口仍在，见 §10.4，建议 W6 前补上 |
| 3 | 上一轮清理的 370 项未提交变更，与本次修复一起提交还是分开提交？ | **分开** | 提交 1 = 清理（`b6c4b43`）；提交 2 = 本次修复，见 §13.5 |
| 4 | 是否需要修复批准后用一次 fresh-DB `migrate` 实测确认新库顺序安全？ | **是** | 已完成，见 §5.2.1 |

**明确不做**：不执行 `flyway repair`；不改 `flyway_schema_history` 任何一行；
不重建开发库；不进入 W6；不新增业务代码。

---

## 13. 修复实施记录（2026-09-17）

### 13.1 已实施的 8 项配套改动

| # | 文件 | 具体动作 | 验证 |
| --- | --- | --- | --- |
| 1 | `xsy-scm-server/sa-admin/src/main/resources/db/migration/V18__scm_menu_icons.sql` | `git mv` 自 `V17__scm_menu_icons.sql`，**内容字节不变** | 前后 sha256 均为 `ed715acc…da903` |
| 2 | `docs/architecture/w5_5-applied-migrations.sha256` | 引用名同步 `V18__…`；**CRLF → LF**（91 → 90 字节） | `sha256sum -c` **OK**（修复前因 CRLF 报 `No such file or directory`） |
| 2b | **新增 `.gitattributes`**（`*.sha256 -text`）+ 把 `v3` / `w1` / `w4` 三份清单规范化为 LF | **item 2 的扩展**：单把 `w5_5` 改成 LF 是**不持久**的——`core.autocrlf=true` 会在下次 checkout 时再转回 CRLF。见 §13.7 | 8 份清单**全部 OK**（合计 30 项哈希）；`git ls-files --eol` 显示 `attr/-text`，索引 blob **零变更** |
| 3 | `ScmPurchaseMigrationIT.java` | 上限 `"17"` → `"18"`；`@DisplayName` 改为 `V1–V18`；注释说明 V17=F0 / V18=W5.5，并**显式声明该用例不扫描磁盘**（无法发现文件层重复版本） | 定向跑 **4 / 0 / 0 / 0**（23.46s，surefire 报告确认） |
| 4 | `docs/architecture/2026-09-17-f0-object-storage-验收报告.md` | 追加勘误块：F0 的 V17 在合并 W5.5 后重编号为 V18 邻位，F0 自身迁移号不变 | 见该文件 |
| 5 | `AGENTS.md` | 迁移行改为显式列出 `V17 V17__sa_config_file_upload_size.sql F0` / `V18 V18__scm_menu_icons.sql W5.5`；加「V17/V18 版本号勘误（2026-09-17）」块与「选版本号前必须先同步远端」硬规则 | 见该文件 |
| 6 | `docs/architecture/2026-09-17-f0-object-storage-approval.md` | HD-2 补记版本号重定 | 见该文件 |
| 7 | `docs/architecture/f0-applied-migrations.sha256` | **新建**，补上 F0 缺失的清单：`6d22bd49…f6edd *V17__sa_config_file_upload_size.sql` | `sha256sum -c` **OK** |
| 8 | `docs/00-文档总览与索引.md` | 修正 W5.5 归属表述，指向真实清单 `w5_5-applied-migrations.sha256` | 见该文件 |

### 13.2 开发库应用 V18（等价于后端下次启动行为，**非 repair**）

用与生产同款的 Flyway 11.7.2 调用 `migrate()`（探针位于仓外 `.runtime/v17probe/`）：

```text
initialSchemaVersion = 17
targetSchemaVersion  = 18
migrationsExecuted   = 1
success              = true
applied: v18 scm menu icons  time = 173 ms
```

### 13.3 开发库核验（实测）

```text
flyway_schema_history:  total=19  failed=0  baseline=1  max_version=18
rank 18: V18__scm_menu_icons.sql  checksum=-628217405  success=t
t_menu:                 241 行；有图标 37 → 71（34 条 UPDATE 全部命中）
抽查 142 / 401 / 431 / 501 / 601 / 701 / 706：图标均已落库
xsy_v2 表数:            74
```

### 13.4 fresh-DB 实测（用户决策 4）

```text
目标库:   xsy_scm_test（迁移前仅 public schema、0 表，与生产零耦合）
执行:     Flyway 11.7.2 migrate()，migration 目录 = 修复后 V1–V18
结果:     initialSchemaVersion = 18 / migrationsExecuted = 0（首轮已完成）/ success = true
终态:     74 表 / 19 历史行 / 0 失败 / baseline=1 / max=18 / t_menu 有图标 71
对照结论: 与存量开发库**逐项一致** → 顺序安全，实测确认
```

**测试库处置**：`xsy_scm_test` 当前**保留**为「V1–V18 已验证的 fresh-build 参照库」，
未自动清空（清空属破坏性操作，未在授权范围内）。它不被任何 IT 使用——
`ScmW5PgITBase` 走 `@SpringBootTest(classes = AdminApplication.class)` 主配置，指向开发库。
如需再次作为空库目标：`DROP SCHEMA xsy_v2 CASCADE`。

### 13.5 提交拆分（用户决策 3）

```text
提交 1（清理，已完成）
  b6c4b43  chore(repo): stop tracking dist-verify build output
           370 files changed, +3 / −579

提交 2（本次修复，见下）
  fix(scm): renumber duplicate V17 migration to V18
           - migration 改名（V17__scm_menu_icons.sql → V18__scm_menu_icons.sql，内容不变）
           - 清单：w5_5 同步 V18 + 转 LF；新增 f0-applied-migrations.sha256
           - 清单可校验性：新增 .gitattributes（*.sha256 -text）+ v3/w1/w4 转 LF
           - IT：ScmPurchaseMigrationIT 上限抬到 18
           - 文档：AGENTS.md / 验收报告 / approval / 文档总览 / 本审计报告
```

### 13.6 遗留事项与最终状态

用户二次决策：**推送两个提交 / 删探针保留测试库 / 迁移 .sql 混合 EOL 先不处理**。

| # | 事项 | 状态 |
| --- | --- | --- |
| 1 | §10.4 加固项 2/3（迁移版本唯一性门禁 + IT 增强） | ❌ **未实施**（用户答复「2 否」）。全仓仍无版本唯一性检查，`ScmPurchaseMigrationIT` 仍只查 DB 历史、不扫描磁盘，同类事故理论上可再次发生 |
| 2 | `xsy_scm_test` | ⏸️ **保留**为「V1–V18 已验证的 fresh-build 参照库」（见 §13.4）。需再次作空库：`DROP SCHEMA xsy_v2 CASCADE` |
| 3 | 仓外探针目录 `D:/Browser Download/.runtime/v17probe/` | ✅ **已删除**（5.3 MB / 64 文件，含 52 个迁移副本）。探针源码可由技能 §3.3 重建 |
| 4 | 推送 | ✅ **已完成**：`48134bf..0682427 main -> main`。推送后复核发现 packed-ref 陈旧（第三次命中），loose + packed 双写后 HEAD = origin/main = ls-remote = `0682427` |
| 5 | 迁移 `.sql` 混合 EOL（§13.7b） | ❌ **未处理**（用户选择「先不处理，记入待办」）。**建议 W6 前单独批准处理** |
| 6 | `2026-09-16-smartadmin-postgresql-closure-report.md` L468「`xsy_scm_test` 无 `xsy_v2` schema」 | 该句在本次实测后**已过时**；属历史报告（记录当时事实），**未改动**，仅在此标注 |

**最终终态核验（全部通过）：**

```text
V18 sha256            ed715accae11e4b44b6a0195b8e53b1fb60286d8e5236671106470e5e46da903（与改名前一致）
工作区                git status 干净
迁移版本号            1–18 唯一且连续，无重复无缺口
8 份清单              8/8 通过（合计 30 项哈希）
开发库                max_version=18 / failed=0 / baseline=1 / t_menu 有图标 71
HEAD                  0682427 == origin/main == ls-remote（已同步，无 ahead/behind）
```

**⚠️ 陈旧 packed-ref 陷阱（本次第三次命中）**：`git push` 报告成功、`ls-remote` 返回 `0682427`，
但 `git rev-parse origin/main` 仍返回 `48134bf`。根因同 §2.1——`.git/refs/remotes/origin/` 目录为空
（无 loose ref），`packed-refs` 第 5/6 行的陈旧条目接管。修复方式为 **loose + packed 双写**。
**教训：本仓库每次 push/fetch 后都应复核 `git rev-parse origin/main` vs `ls-remote`；
`git push` 的成功输出不代表本地 remote-tracking ref 已更新。**

### 13.7 顺带发现的仓库级缺陷：换行符（EOL）不一致

在验证清单时发现两个**相互独立**的换行符缺陷。**(a) 已修，(b) 只报告不动手。**

#### (a) 清单文件自身 EOL 不一致 —— 已修

`core.autocrlf=true`（Windows 默认）且仓库**没有 `.gitattributes`**，导致清单被检出为 CRLF 时，
行尾多出的 `\r` 会被 `sha256sum` 当作**文件名的一部分**：

```text
$ sha256sum -c docs/architecture/w5_5-applied-migrations.sha256
sha256sum: 'V17__scm_menu_icons.sql'$'\r': No such file or directory
V17__scm_menu_icons.sql: FAILED open or read
```

修复前实测（cwd = migration 目录）：

| 清单 | 修复前 | 修复后 |
| --- | --- | --- |
| `v3-applied-migrations.sha256` | **FAILED**（清单 CRLF，580 B） | OK（6 项） |
| `w1-applied-migrations.sha256` | **FAILED**（清单 CRLF，186 B） | OK（2 项） |
| `w4-applied-migrations.sha256` | **FAILED**（清单 CRLF，196 B） | OK（2 项） |
| `w2` / `w5` / `w5_5` / `f0` / `pg-closure` | OK | OK |
| 合计 | 5/8 通过 | **8/8 通过，共 30 项哈希** |

处置：新增 `.gitattributes`（`*.sha256 -text`，关闭换行符转换）+ 把 `v3`/`w1`/`w4`
规范化为 LF。**索引 blob 零变更**（索引原本已是 LF，只改了工作区），
`git ls-files --eol` 现显示 `attr/-text`。

> **为什么必须加 `.gitattributes`**：只把 `w5_5` 改成 LF 是**不持久**的——
> 下次 `git checkout` 时 `core.autocrlf=true` 会再把它转回 CRLF，问题复发。
> 所以 item 2 必须配 `.gitattributes` 才真正落地。

#### (b) 迁移 `.sql` 的 EOL 是混合的 —— 只报告，未动

18 个 migration 文件在工作区的换行符**不一致**（字节计数法实测）：

```text
LF   ：V15__scm_purchase.sql、V17__sa_config_file_upload_size.sql
CRLF ：V1–V14、V16、V18（共 16 个）
```

后果分两层：

| 层面 | 是否受影响 | 说明 |
| --- | --- | --- |
| **Flyway checksum** | **不受影响** | Flyway 的 `ChecksumCalculator` 逐行 `readLine()` 后**统一插入 `\n`**，CRLF 与 LF 算出同一个 checksum。这也是开发库 `validate` 能通过的原因 |
| **sha256 清单** | **受影响** | sha256 按**字节**计算。清单记录的是「生成当时工作区的字节」，因此 EOL 一变，哈希就变 |
| **fresh clone 可复现性** | **受影响** | `core.autocrlf=true` 会把**全部** `.sql` 检出为 CRLF，于是 `w5`（V15）与 `f0`（V17）这两份「LF 基线」的清单在新克隆上会 FAILED |

**为什么本次不修**：两条修法都会超出本次批准范围，且都要动「冻结证据」：

| 方案 | 代价 |
| --- | --- |
| 全部规范化为 LF | 16 个迁移文件字节变更 → `v3`/`w1`/`w4`/`w2`/`pg-closure` 的清单哈希需**全部重算** |
| 加 `-text` 冻结当前混合状态 | 需把 CRLF 字节重新写进索引 → `git diff` 会显示 **16 个迁移文件"被修改"**，与「V1–V16 冻结」的既有保护检查直接冲突，噪声极大 |

**建议**（另开一次明确批准的任务处理）：倾向「全部规范化 LF + 重算受影响清单」，
并在 `.gitattributes` 中加 `xsy-scm-server/**/db/migration/*.sql text eol=lf`，
使新克隆的字节与仓库一致。注意这与 `docs/00-文档总览与索引.md` 中
「`architecture/` 中的 migration 哈希是冻结证据，不重新生成」的口径需要一并协调。
