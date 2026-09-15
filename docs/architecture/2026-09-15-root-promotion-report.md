# Root Promotion 报告（V2 工作区扶正到仓库根目录）

> 日期：2026-09-15
> 迁移前稳定回退点：`803a8620b0fd36613db86f0346082a47ea29d2c9`（`803a862 feat(v2): establish SmartAdmin SCM baseline and W3 pricing`）
> 范围：**仅工程目录与路径扶正**。不重构 PostgreSQL SQL、不新增业务功能、不修改业务行为、不进入 W4、不执行 PostgreSQL Full Compatibility。
> 状态：**通过**。

---

## 0. 结论摘要

| 项目 | 结果 |
| --- | --- |
| 目录移动 | 5 组前缀、**1655 个文件**全部 1:1 迁移，0 遗漏、0 多余 |
| `v2/` | 已删除，且不再重建（见 §11 遗留进程处置） |
| symlink / 兼容副本 / 第二套正式代码 | **无** |
| 路径硬编码修复 | 35 个文件内容变更（10 处原地修改 + 25 处「移动并修改」） |
| V1–V11 migration | **暂存区 blob 与 HEAD 逐一相同**（零修改） |
| `project-reference-examples/xsy-scm/**` | **零修改**（1997 文件，树哈希见 §9） |
| 后端单测 | 120 / 120 通过 |
| PostgreSQL IT | 94 / 94 通过 |
| Flyway validate | `Successfully validated 12 migrations` |
| 前端单测 | 20 / 20 通过 |
| ESLint | 0 error / 3 warning（上游既有） |
| 前端构建 | 成功，产物与 HEAD 逐字节一致（369 / 369） |
| TS baseline ratchet | **PASS**（new 0 / SCM 0 / total 1973 ≤ 1974） |
| Playwright W1 / W2 / W3 | 8 / 8 通过 |
| V6–V11 SHA-256 | 6 / 6 与 `docs/architecture/v3-applied-migrations.sha256` 一致 |

---

## 1. old path → new path

| 旧路径（迁移前） | 新路径（迁移后） | 文件数 |
| --- | --- | --- |
| `v2/xsy-scm-v2-server/**` | `xsy-scm-server/**` | 833 |
| `v2/xsy-scm-v2-web/**` | `xsy-scm-web/**` | 791 |
| `v2/tools/**` | `tools/**` | 12 |
| `v2/docs/**` | `docs/**` | 19（含 4 个非 ASCII 文件名，`git status` 以八进制转义显示） |
| `v2/.runtime/**` | `.runtime/**` | 1（`.runtime/debug-drawer.png`，见 §11） |
| **合计** | | **1655** |

校验方式：把 `git status` 中全部 ` D `（deleted）条目按上表前缀映射到新路径，逐一验证目标文件存在。

```text
total deleted: 1655
mapped & present: 1655
missing: 0
unmapped: 0
```

`git add -A` 后 git 的重命名识别结果：

```text
R100   1629
R098      9
R099      5
R095      2
R092      2
R091      3
R097      1
R083      1
R086      1
R089      1
M        10   (原地修改)
D         1   (v2/xsy-scm-v2-web/.runtime/debug-drawer.png)
```

> 说明：`docs/` 目录在根目录原本已存在（Sprint 期文档），因此 `v2/docs/**` 是**合并进**根 `docs/`，而非整体替换；原有根文档按需就地修订（见 §3）。

---

## 2. 修复的路径引用

### 2.1 原地修改（10 个文件，未移动）

| 文件 | 修复内容 |
| --- | --- |
| `.gitignore` | `v2/.runtime` → `**/.runtime/`（覆盖根与各子工作区的运行目录） |
| `AGENTS.md` | 文件头工作区说明；§2 仓库结构树；§3 源优先级中的 `v2/` 过渡条目；§10 前端实现基准；§35.2 前端边界；新增 §「Current delivery status (2026-09-15)」 |
| `CLAUDE.md` | V2 baseline notice 删除「暂存于 `v2/`」；波次表 W0–W3 状态修正；`Verification artifacts` 段落改写 |
| `CONTEXT.md` | 工程工作区约定：`xsy-scm-server/` + `xsy-scm-web/` + `tools/` + `docs/` 为正式工作区，`project-reference-examples/xsy-scm/` 只读 |
| `README.md` | 顶部横幅：V2 状态表改为根目录四目录；删除「迁移过渡来源」行 |
| `SMARTADMIN_REFERENCE_RULES.md` | §0 第 11 条；§1.1 目录树（并删除「（代码移动目标）」）；§1.2 状态表；§1.3 禁止事项；§6 迁移波次与当前进度 |
| `docs/00-文档总览与索引.md` | 迁移期横幅：正式工作区与只读参考声明 |
| `docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md` | §11.2 P0 步骤 1、§13.1 目录骨架、§13 Q1 决策、§阶段划分 W3 = Pricing；结论状态改为「历史审计已执行」 |
| `docs/architecture/2026-09-14-w0-验收报告.md` | 报告头工作区描述、验收表行、规则文档修订表；§2 标题改为「根目录 V2 工作区结构」 |
| `docs/design/xsy-scm-UIUX设计与前端开发指导-v1.1.md` | 「具体 Vue3 页面约定以 `xsy-scm-web` 既有实现为准」 |

### 2.2 移动并修改（25 个文件）

#### 后端配置与测试基类（5）

| 新路径 | 修复内容 |
| --- | --- |
| `xsy-scm-server/sa-admin/src/main/resources/dev/application.yaml` | `project.log-directory` 默认值 `…/v2/.runtime/logs` → `…/.runtime/logs` |
| `xsy-scm-server/sa-base/src/main/resources/dev/sa-base.yaml` | `file.storage.local.upload-path` 默认值 `…/v2/.runtime/upload/` → `…/.runtime/upload/` |
| `xsy-scm-server/sa-admin/src/test/java/…/scm/common/ScmW2PgITBase.java` | 测试属性 `project.log-directory`、`file.storage.local.upload-path` 两处绝对路径 |
| `xsy-scm-server/sa-admin/src/test/java/…/scm/common/ScmW3PgITBase.java` | 同上两处 |
| `xsy-scm-server/sa-admin/src/test/java/…/scm/product/ProductPgIT.java` | 同上两处 |

#### 工程工具（7）

| 新路径 | 修复内容 |
| --- | --- |
| `tools/ts_baseline_ratchet.py` | `ROOT = parents[1]`；`WEB_DIR = ROOT/"xsy-scm-web"`；`BASELINE_PATH = ROOT/"docs/quality/ts-baseline.json"`；文档字符串 |
| `tools/verify_w1_legacy.py` | `ROOT = parents[1]`；`allowed` 改为 `**/.runtime/`；`migration_root` → `xsy-scm-server/…`；manifest → `docs/architecture/…`；输出目录 → `.runtime` |
| `tools/verify_w2_legacy.py` | 同上（W2 manifest） |
| `tools/verify_w3_legacy.py` | `REPO = ROOT`；`migration_root` → `xsy-scm-server/…` |
| `tools/gen_v3_migration.py` | `V2` → `ROOT`；`DUMP` 相对路径；`OUT` → `xsy-scm-server/…/V3__…sql` |
| `tools/gen_v5_migration.py` | 生成器头部注释模板 `v2/tools/…` → `tools/…`（**生成物 V5 本身未改动**，见 §10） |
| `tools/pg_convert_mapper_xml.py` | `ROOT` 绝对路径 → `…/xsy-scm-server` |

#### 基线数据（1）

| 新路径 | 修复内容 |
| --- | --- |
| `docs/quality/ts-baseline.json` | `webDir` 字段 `…\v2\xsy-scm-v2-web` → `…\xsy-scm-web`；83 条 TS7016 诊断消息内的绝对路径随目录变更（错误集合本身未重置，见 §7.7） |

#### 波次文档（12）

`docs/architecture/` 下 W1 / W2 / W3 的 approval、legacy-audit、target-design、验收报告共 12 份，随移动同步更新其中引用的工作区路径与「旧 `v2/` 路径不再存在」说明。

### 2.3 无需修改（已是新根相对路径）

| 文件 | 说明 |
| --- | --- |
| `xsy-scm-web/playwright.config.ts` | `outputDir`/`outputFile` 使用 `../.runtime/…`，在 `xsy-scm-web/` 下解析到仓库根 `.runtime/`，移动后仍正确 |
| `xsy-scm-web/e2e/*.spec.ts` | fixture 调用 `../tools/w*.py`、截图 `../.runtime/*.png`，移动后仍正确 |

### 2.4 有意保留的引用

| 位置 | 内容 | 原因 |
| --- | --- | --- |
| `xsy-scm-server/sa-admin/src/main/resources/db/migration/V5__sa_system_remaining_tables.sql:6` | 注释「由 `v2/tools/gen_v5_migration.py` 生成」 | **migration 内容冻结**。改动会改变 Flyway checksum 并使已应用数据库 validate 失败；本次约束「不修改任何 V1–V11 migration 内容」优先 |
| `xsy-scm-web/package.json` / `package-lock.json` | npm 包名 `xsy-scm-v2-web` | 包标识（identifier）而非路径，不在本次 5 组前缀范围内；改动会牵动 lockfile 一致性 |
| `xsy-scm-server/pom.xml` | Maven `<name>xsy-scm-v2-parent</name>`（`artifactId` 仍为 SmartAdmin 原生 `sa-parent`） | 同上，构建标识而非路径 |

---

## 3. 规则文档修订要点

- 正式工作区：`/xsy-scm-server`、`/xsy-scm-web`、`/tools`、`/docs`；只读参考：`/project-reference-examples/xsy-scm`。
- **删除「v2 是过渡工作区」的全部现行描述**（`AGENTS.md`、`CLAUDE.md`、`CONTEXT.md`、`README.md`、`SMARTADMIN_REFERENCE_RULES.md`、`docs/00-…`、迁移审计报告、W0 报告）。
- 波次状态修正：

```text
W0  baseline                            COMPLETE
W1  Product                             COMPLETE
W2  Customer + Supplier                 COMPLETE
W3  Pricing implementation/verification COMPLETE
W4  purchase                            NOT STARTED
```

- 不再出现 `W3 = order + inventory`。
- 架构契约：

```text
Frontend               = SmartAdmin Base + SCM Vue (Copy First + Adapt)
Backend Infrastructure = SmartAdmin Native First
Backend SCM Business   = SmartAdmin Structure + confirmed SCM business rules
```

- 前端迁移原则明确为 **Copy First + Adapt**（复制 → 剪枝 → 适配 → 补测试），禁止重新生成 C 已有的同功能 SCM Vue 页面。
- 未重新规划 W4 之后的 Roadmap。

---

## 4. 最终目录树

```text
xsy-scm/
├─ AGENTS.md                     # 规则：正式工作区 / 边界 / 波次
├─ CLAUDE.md
├─ CONTEXT.md
├─ README.md
├─ SMARTADMIN_REFERENCE_RULES.md
├─ .gitignore
├─ package-lock.json
├─ xsy-scm-server/               # 正式后端（833 文件）
│  ├─ pom.xml
│  ├─ sa-base/
│  └─ sa-admin/                  #   └ src/main/resources/db/migration/V1..V11
├─ xsy-scm-web/                  # 正式后台（427 源文件 + 369 构建证据）
│  ├─ package.json / vite.config.ts / tsconfig.json / playwright.config.ts
│  ├─ e2e/                       #   scm-product / scm-customer / scm-supplier / scm-pricing
│  ├─ src/                       #   api views components layout router store …
│  ├─ test/                      #   4 个 *.test.mjs
│  └─ dist-verify/               #   构建验证产物（与 HEAD 逐字节一致）
├─ tools/                        # 正式工程工具（12）
│  ├─ ts_baseline_ratchet.py
│  ├─ verify_w1_legacy.py  verify_w2_legacy.py  verify_w3_legacy.py
│  ├─ gen_v3_migration.py  gen_v5_migration.py  pg_convert_mapper_xml.py  pg_seed_convert.py
│  ├─ w1_e2e_accounts.py  w2_e2e_accounts.py  w3_e2e_accounts.py
│  └─ Sm4Encrypt.java
├─ docs/                         # 正式文档（70）
│  ├─ 00-文档总览与索引.md
│  ├─ architecture/              #   W0–W3 审计 / 设计 / 验收 / migration sha256 / 本报告
│  ├─ quality/ts-baseline.json
│  ├─ design/  requirements/  roadmap/  references/  UI/  superpowers/
├─ xsy-scm-miniapp/              # LEGACY，冻结只读（69）
├─ project-reference-examples/xsy-scm/   # 上游只读参考（1997）
└─ .runtime/                     # 运行与验证证据（git 忽略）
```

`v2/` 已不存在。

---

## 5. 回归验证结果

### 5.1 后端全量单元测试

```text
mvn test   （xsy-scm-server，profile dev，JDK 21.0.12.1，Maven 3.9.16）
Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 5.2 PostgreSQL 集成测试

```text
mvn test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖 12 个 `*IT`：`ProductPgIT`、`CustomerServiceIT`、`CustomerQueryServiceIT`、`CustomerTypeServiceIT`、`SupplierServiceIT`、`SupplierSkuServiceIT`、`SupplierQueryServiceIT`、`PricingIT`、`ScmProductMigrationIT`、`ScmCustomerSupplierMigrationIT`、`ScmCustomerSupplierOptimisticLockIT`、`ScmPricingMigrationIT`。

### 5.3 Flyway validate

应用启动（`spring-boot:run -pl sa-admin`，dev profile）日志：

```text
Database: jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2 (PostgreSQL 18.3)
Successfully validated 12 migrations (execution time 00:00.256s)
Tomcat started on port 18080 (http) with context path '/'
Started AdminApplication in 21.359 seconds
```

`xsy_v2.flyway_schema_history` 中 V1–V11 全部 `success = t`，checksum 未变。

### 5.4 前端单测

```text
npm test  →  node --experimental-strip-types --test test/*.test.mjs
# tests 20  # pass 20  # fail 0
```

### 5.5 ESLint

```text
npm run lint  →  eslint src
✖ 3 problems (0 errors, 3 warnings)
```

3 条 warning 为上游 SmartAdmin 文件中的无用 `eslint-disable` 指令（`src/constants/regular-const.ts`、`src/lib/highlight-line-number.ts`），非本次引入。

### 5.6 前端构建

```text
npm run build  →  vite build --mode production --outDir dist-verify --emptyOutDir
✓ built in 1m 30s   (exit 0)
```

产物可复现性：新构建的 `xsy-scm-web/dist-verify/**` 与 HEAD 提交中的 `v2/xsy-scm-v2-web/dist-verify/**` **369/369 文件逐字节相同**。

### 5.7 TS baseline ratchet

```text
python tools/ts_baseline_ratchet.py check
baseline total : 1974
current  total : 1973  (delta -1)
scm errors     : 0  (must be 0)
new errors     : 0
fixed errors   : 1
RESULT: PASS
```

**baseline 未被借机重置**。移动前后的基线对比：

| 指标 | HEAD | 现在 | 判定 |
| --- | --- | --- | --- |
| `summary.total` | 1974 | 1974 | 相同 |
| `summary.scm` | 0 | 0 | 相同 |
| `summary.by_code` | 23 项 | 完全一致 | 相同 |
| `summary.top_dirs` | 15 项 | 完全一致 | 相同 |
| `scmMustBeZeroPrefixes` | 6 项 | 完全一致 | 相同 |
| 诊断 `(file, TS code)` 多重集 | 678 文件 | **完全一致** | 相同 |
| `webDir` | `…\v2\xsy-scm-v2-web` | `…\xsy-scm-web` | 路径字段，预期变更 |
| TS7016 诊断消息内嵌绝对路径 | 旧根 | 新根 | 消息文本，预期变更（83 条） |

即：错误集合语义完全保持，仅绝对路径字段随目录更新。

### 5.8 Playwright（W1 / W2 / W3）

```text
Running 8 tests using 1 worker
ok 1  scm-customer.spec.ts  live customer pilot: type, credit period, status, search, deep link and deletion
ok 2  scm-customer.spec.ts  read-only role cannot mutate customers and buttons are hidden
ok 3  scm-pricing.spec.ts   pricing resolver keeps price and sale eligibility independent
ok 4  scm-pricing.spec.ts   read-only role cannot mutate pricing and successful batch keys are protected
ok 5  scm-product.spec.ts   live product pilot: categories, SKU delta, SPU images, search, deep link and deletion
ok 6  scm-product.spec.ts   read-only role cannot mutate products and buttons are hidden
ok 7  scm-supplier.spec.ts  live supplier pilot: SKU relations with R12 defaults, whole-table clear, status and deletion
ok 8  scm-supplier.spec.ts  read-only role cannot mutate suppliers and buttons are hidden
8 passed (1.1m)
```

环境：后端 18080、前端 dev 18081、Redis `xsy-v2-redis`、PostgreSQL 15432。

---

## 6. Migration hash

`docs/architecture/v3-applied-migrations.sha256` 逐项复核（工作区实际文件）：

| 文件 | SHA-256 | 结果 |
| --- | --- | --- |
| `V6__scm_product.sql` | `b97089324ea808de5fb98fea9b39c691dd77c6064aa6eae823788bb8ea709e8d` | ✅ |
| `V7__scm_product_permissions.sql` | `f85d0042d537067ebec48e66e3d456fc5b13d632f0ef1d280ee2357a8da6b9d7` | ✅ |
| `V8__scm_customer_supplier.sql` | `0a83720ed4b93f780b7a230b68d3778e55301eb816fc3566099a14042ccf6ed5` | ✅ |
| `V9__scm_customer_supplier_permissions.sql` | `0cf02ca7e63734f8936d5d8668d7e6dade87133c39ece310f080cbd22638c48b` | ✅ |
| `V10__scm_pricing.sql` | `cb314708bff5ecbcd3b7eb9f907acbfba8883eb4456f2b0d39669e7a800f6c30` | ✅ |
| `V11__scm_pricing_permissions.sql` | `3b74f754236841404f1c5510bd12a895a09f116514e205becb4ecf92865252e5` | ✅ |

另：`w1-applied-migrations.sha256`（V6/V7）、`w2-applied-migrations.sha256`（V6–V9）亦一致。

**V1–V11 零修改（更强证据）**：暂存区 blob 与 HEAD blob 逐一比对。

```text
IDENTICAL  V1__sa_system_poc_core.sql
IDENTICAL  V2__sa_system_login_rbac.sql
IDENTICAL  V3__sa_system_login_support_and_seed.sql
IDENTICAL  V4__sa_system_runtime_support.sql
IDENTICAL  V5__sa_system_remaining_tables.sql
IDENTICAL  V6__scm_product.sql
IDENTICAL  V7__scm_product_permissions.sql
IDENTICAL  V8__scm_customer_supplier.sql
IDENTICAL  V9__scm_customer_supplier_permissions.sql
IDENTICAL  V10__scm_pricing.sql
IDENTICAL  V11__scm_pricing_permissions.sql
```

> 说明：V3/V6/V7/V10/V11 在工作区为 CRLF、在索引中为 LF，属既有行尾状态，**索引 blob 与 HEAD 完全相同**，因此提交不改变 migration 内容，Flyway checksum 不变。

`tools/verify_w3_legacy.py` 输出 `migration_failures: []`。

---

## 7. Reference hash / status

`project-reference-examples/xsy-scm/**`（`.gitignore` 忽略，未被 git 跟踪）：

```text
files                : 1997
tree sha256          : 10bf630c101c140723443c591ef5e59defe0311878c96a9cf5ee146e98d303ea
                      （算法：对每个文件取 sha256，按相对路径字典序拼接 "hash  relpath\n" 后再取 sha256）
total bytes          : 35,250,241
newest mtime         : 2026-09-15 11:00（早于本次扶正开始时间 17:30）
files modified after 2026-09-15 17:30 : 0
subdirs modified after 2026-09-15 17:30 : 0
```

结论：**零修改**。

---

## 8. git status

提交前 `git status --porcelain` 汇总：

```text
R  1654    重命名（含 25 处伴随内容修订）
M    10    原地修改
D     1    v2/xsy-scm-v2-web/.runtime/debug-drawer.png
```

非重命名条目全量：

```text
M  .gitignore
M  AGENTS.md
M  CLAUDE.md
M  CONTEXT.md
M  README.md
M  SMARTADMIN_REFERENCE_RULES.md
M  docs/00-文档总览与索引.md
M  docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md
M  docs/architecture/2026-09-14-w0-验收报告.md
M  docs/design/xsy-scm-UIUX设计与前端开发指导-v1.1.md
D  v2/xsy-scm-v2-web/.runtime/debug-drawer.png
```

暂存统计：

```text
1665 files changed, 292 insertions(+), 290 deletions(-)
```

（1654 条重命名不产生行变更；292/290 为规则文档与脚本的路径修订。）

`v2/` 目录已删除，工作区与索引中均无残留。

---

## 9. 有意为之 / 已知残留

1. **`v2/xsy-scm-v2-web/.runtime/debug-drawer.png` 被删除（1 个跟踪文件）**
   `.gitignore` 由 `v2/.runtime` 改为 `**/.runtime/` 后，所有工作区下的 `.runtime/` 均被忽略。该文件是调试截图运行产物，非源码；新规则是本次明确要求的修复项（`v2/.runtime` 属待修复硬编码之一）。W2 的 `.gitignore` 放行白名单本就把 `**/.runtime/` 列为「已批准新增」，语义一致。

2. **`V5` 迁移注释仍写 `v2/tools/gen_v5_migration.py`**
   migration 内容冻结，不可改。生成器 `tools/gen_v5_migration.py` 的模板注释已更新为新路径，供后续波次使用；V5 本身保持历史原文。

3. **npm 包名 `xsy-scm-v2-web` / Maven `<name>xsy-scm-v2-parent</name> 未改**
   属构建标识而非路径，不在本次 5 组前缀内；改动会牵动 `package-lock.json` 一致性，收益为零。

4. **`tools/verify_w1_legacy.py`、`tools/verify_w2_legacy.py` 保持波次封版语义**
   两个脚本内置 `HEAD = '95a5423…'`（W2 提交）与固定的 `.gitignore` 哈希，是 W1/W2 封版时的一次性证据生成器，按设计锚定历史提交，对本次扶正提交不适用。其真正有效的部分——migration 哈希与 legacy 清单校验——已由 `tools/verify_w3_legacy.py` 覆盖并执行（`migration_failures: []`）。未改写历史封版门禁。

5. **`verify_w3_legacy.py` 的 legacy 清单包含用户已删除的旧根目录**
   清单含旧根 `xsy-scm-server/**`（602）+ `xsy-scm-web/**`（162）共 764 个 legacy React 实现文件，用户已主动删除且本任务明确「不恢复旧实现」，故报告 `legacy_missing`。同时报告 `legacy_changed` 为本任务有意修订的根规则文档。`migration_failures` 为空。该脚本按设计不修改、不恢复任何文件。

6. **遗留进程处置**：上一轮扶正尝试遗留的旧后端进程（PID 17792，17:31 启动，classpath 仍为 `v2/xsy-scm-v2-server`，上传路径默认值仍为 `…/v2/.runtime/upload/`）会持续重建 `v2/.runtime/upload/private/common`。已终止该进程，随后删除 `v2/`，并确认不再重建。

---

## 10. 未执行项（按指令）

- ❌ PostgreSQL Full Compatibility 新一轮改造
- ❌ MySQL → PostgreSQL 改造
- ❌ W4 及之后的 Roadmap 规划
- ❌ 任何业务功能新增或业务行为变更
- ❌ V1–V11 migration 内容修改
- ❌ `project-reference-examples/xsy-scm/**` 修改
- ❌ 保留 symlink、兼容副本或第二套正式代码

---

## 11. 提交

```text
refactor(repo): promote V2 workspaces to repository root
```

父提交：`803a8620b0fd36613db86f0346082a47ea29d2c9`
