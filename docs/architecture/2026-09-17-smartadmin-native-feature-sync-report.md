# W5.5 SmartAdmin 原生功能同步报告

日期：2026-09-17
范围：`xsy-scm-server/sa-base/**`、`xsy-scm-server/sa-admin/**`、`xsy-scm-web/**`
对照：`project-reference-examples/xsy-scm/**`（只读，零修改）
配套文档：[`2026-09-17-smartadmin-native-feature-parity-audit.md`](./2026-09-17-smartadmin-native-feature-parity-audit.md)
提交建议：`feat(smartadmin): complete native feature parity sync`

---

## 0. 一句话结论

**本轮同步的实际代码增量为零，且这是正确结果。**

正式 V2 工作区在 W0–W5 阶段就已经承载了 SmartAdmin 的原生系统/支撑能力：24 个后端支撑模块
中 **22 个逐字节一致**（包名归一化后），仅有的 2 处差异均是有意为之的 PG 适配与业务范围裁剪；
前端同类页面已存在且 ID/主题自洽；菜单、权限、API、PostgreSQL 表四者闭环完整。
因此本轮工作不是"补代码"，而是**把"已经同步过了"这件事验证到可复核、可回归的程度**，
并顺手修掉了一批与本机环境绑定的硬编码路径。

按「Keep First, Prune Later」原则，本轮**没有裁剪任何原生功能**；
唯一未恢复的是 4 个 SmartAdmin 演示用 ERP 菜单（`47/48/78/79`），理由见 §6。

---

## 1. 统计总览

| 类别 | 数量 | 说明 |
| --- | --- | --- |
| 已存在、**无需动作**（`SAME`） | 22 个后端支撑模块 + 全部原生前端同类页面 | 逐字节/归一化后一致 |
| **PG 适配差异**（保留正式版） | 1 个文件 | `MapperVariableService.java`：`STRPOS` vs `INSTR` |
| **业务范围裁剪**（保留正式版） | 1 个文件 | `SerialNumberIdEnum.java`：18 → 2 个枚举 |
| 从 reference **新增搬运** | **0** | 无 `REFERENCE_ONLY` 项 |
| **新增迁移脚本** | **0** | V1–V16 冻结，未追加 V17 |
| **菜单修复** | **0** | 原生菜单已全部存在且可解析 |
| **权限修复** | **0** | 权限点齐备 |
| 本轮实际代码改动 | **8 个文件** | 全部为「去掉与本机绑定的硬编码路径」+ 1 行 `.gitignore` |
| 新增文件 | **3 项** | 1 份审计文档、1 套测试基类、1 个 Playwright 冒烟 |

---

## 2. 已存在、无需动作（Keep）

### 2.1 后端支撑模块（`sa-base/**/module/support`）

24 个模块全部在位，包名归一化后 **22 个逐字节一致**：

```
apiencrypt(8)  cache(3)     captcha(4)    changelog(9)   codegenerator(41)
config(9)      datamasking(3) datatracer(17) dict(14)    feedback(7)
file(14)       heartbeat(10)  helpdoc(22)   job(28)       loginlog(6)
mail(5)        message(11)    operatelog(8) redis(4)      reload(16)
repeatsubmit(5) securityprotect(11) serialnumber(17)       table(6)
```

> 括号内为文件数，正式侧与参考侧数量相同。

**执行纪律：`SAME` 一律不复制。** 逐字节相同的文件再复制一次，只会产生无意义的
diff 噪声，并在未来引入"两份真相"的维护负担。

### 2.2 代码生成器（工作令第 4 条重点）

工作令特别要求：代码生成器前端已存在，**不得再复制第二个页面**。核实结果：

| 检查项 | 结果 |
| --- | --- |
| 前端页面 | `xsy-scm-web/src/views/support/code-generator/**` 已存在，且被菜单引用 |
| 前端 API 文件 | 已存在，接口签名与后端一致 |
| 菜单 | `menu_id=151`，路径 `/support/code-generator`，可解析 |
| Mapper | **已 PostgreSQL 重写**：`current_schema()`、`obj_description(to_regclass(...))`、`col_description(...)`、`information_schema.table_constraints` + `key_column_usage` 取主键、`is_identity` / `column_default like 'nextval(%'` 判自增 |
| 运行验证 | 探针 3/3 接口 PASS（表列表 / 表字段 / 生成配置） |

`CodeGeneratorMapper.xml` 头部原有注释写着"已转换但未做功能级验证"——
**本轮把这条缺口补上了**：探针实测该 Mapper 的 PG 查询在生产库上可执行并返回正确结构。

结论：**未复制第二份页面，符合工作令要求。**

### 2.3 前端原生同类页面

前端逐文件比对（参考 424 / 正式 434）：`MISSING 86` / `EXTRA 96` / `CHANGED 40`。

这 86 个 `MISSING` **不是缺口**，而是 W1–W5 的有意重编排：

```
MISSING（参考侧路径）                       正式侧对应
business/{customer,product,supplier,...}  →  business/scm/**(同语义新结构)
xsy-scm-logo*.png                         →  smart-admin-logo*.png
theme/xsy-scm.less                        →  theme/smart-admin.less
```

40 个 `CHANGED` 全部是**外观/品牌层**差异，无一处语义变更：

- DOM id 重命名：`xsyScmMenu` → `smartAdminMenu` 等
- logo 文件名与 `websiteName`（`'SmartAdmin 3.X'` vs `'xsy-scm 管理后台'`）
- 补充 `defineOptions({name})`
- `reactive([])` → `ref([])`（等价写法）
- 移除 `<router-link tag="a">`、移除 `'category-tree'` 异步组件映射

**DOM id 自洽性已单独核对**：`layout-const.ts` 是唯一定义源，
所有 Layout 消费它，全仓 `xsyScm*` 残留引用为 **0**。

---

## 3. PostgreSQL 适配（保留正式版，不覆盖）

### 3.1 代码生成器 Mapper 变量服务

| | 参考侧 | 正式侧 |
| --- | --- | --- |
| 模糊查询 SQL | `INSTR(col, #{val})` | `STRPOS(col, #{val}) > 0` |
| 结论 | — | **保留正式版** |

覆盖会把 MySQL 专有函数带回运行期，直接破坏 PG 兼容性。

### 3.2 `CodeGeneratorMapper.xml`

已完整 PG 化（见 §2.2 表格）。本轮**零修改**，仅做功能级验证。

### 3.3 单号枚举裁剪

`SerialNumberIdEnum`：参考 18 个枚举值 → 正式 2 个（`ORDER(1)`、`CONTRACT(2)`）。

这是 W5 的既有决定：采购单号走 `PurchaseNumberGenerator`，不复用单号中心的枚举。
**保留正式版，不预先恢复死枚举**——恢复会引入永远不被调用的代码路径。

---

## 4. 新增迁移：0

| 检查项 | 结果 |
| --- | --- |
| V1–V16 是否被修改 | **否**（`git diff HEAD` 对整个 migration 目录为空） |
| 是否追加 V17+ | **否**（工作令要求仅在缺表/缺列/缺菜单时追加，本轮无缺口） |
| 菜单 ID 是否猜测 | **否**（未新增菜单，无 ID 分配风险） |
| PostgreSQL 表是否齐备 | 是，全部原生支撑表已在 V3–V5 建立 |
| 是否执行过参考侧 MySQL SQL | **否** |

### 4.1 迁移哈希校验（一个重要澄清）

工作令要求"V1–V16 hash 不变"。校验时发现按天真的 `sha256sum -c` 会报 7 个文件"不匹配"。
**根因已定位，不是真实变更，而是换行符编码的历史遗留**：

`docs/architecture/*.sha256` 是在不同阶段生成的，当时工作区的换行符不统一：

| 换行符 | 文件 | 与 manifest 的匹配方式 |
| --- | --- | --- |
| CRLF | V3, V6, V7, V10, V11, V13, V14（7 个） | 工作区原文**直接匹配** |
| LF | V1, V2, V4, V5, V8, V9, V12, V15, V16（9 个） | 工作区转 LF 后匹配 |

按各自记录的编码复核，**16/16 全部一致，无一处漂移**。

且 `git diff HEAD -- xsy-scm-server/sa-admin/src/main/resources/db/migration/` 输出为**空**，
即工作区文件与已提交内容完全一致。

> **复核方法（可复现）**：对每个迁移文件同时计算
> `sha256(工作区原文)` 与 `sha256(工作区转 LF)`，任一命中 manifest 即视为通过。
> 不要只用 `sha256sum -c`，否则会在 Windows 换行符上产生假告警。

---

## 5. 菜单与权限修复：0

原生菜单**已全部存在且可解析**。实测 `xsy_v2.t_menu` 关键节点：

| menu_id | 名称 | 路径 |
| --- | --- | --- |
| 151 | 代码生成 | `/support/code-generator` |
| 133 | 缓存管理 | `/support/cache/cache-list` |
| 221 | 定时任务 | `/job/list` |
| 109 | 参数配置 | `/config/config-list` |
| 110 | 数据字典 | `/setting/dict` |
| 193 | 文件管理 | `/support/file/file-list` |
| 143 | 登录登出记录 | `/support/login-log/login-log-list` |
| 81 | 用户操作记录 | `/support/operate-log/operate-log-list` |
| 206 | 心跳监控 | `/support/heart-beat/heart-beat-list` |
| 117 | Reload | `/hook` |
| 130 | 单号管理 | `/support/serial-number/serial-number-list` |
| 152 | 更新日志 | `/support/change-log/change-log-list` |
| 147 | 帮助文档 | `/help-doc/help-doc-manage-list` |
| 250 | 三级等保设置 | `/support/level3protect/level3-protect-config-index` |
| 251 | 敏感数据脱敏 | `/support/level3protect/data-masking-list` |
| 26 | 菜单管理 | `/menu/list` |
| 300 | 消息管理 | `/message` |
| 148 | 意见反馈 | `/feedback/feedback-list` |

**因此：未新增菜单、未猜测 menu_id、未改动 W1–W5 菜单结构。**

前端路由核心（`router/index.ts`、`router/routers.ts`）diff 为**空**——
动态菜单机制足以承载全部原生功能，无需改路由核心。

---

## 6. 未同步项及原因

| 项 | 原因 |
| --- | --- |
| **菜单 47 / 48 / 78 / 79**（SmartAdmin 演示用 ERP 树） | **有意裁剪**。① 其视图资源在正式前端不存在，恢复即产生 404；② 其数据模型是 SmartAdmin 的演示商品模型，恢复会把一套演示商品域叠加到 W1–W5 已验收的 SCM 商品域上，违反"不覆盖 SCM 业务模型"的硬约束；③ 纯演示性质，无业务价值。演示页 `85`（组件演示）/`138`（功能Demo）**已保留** |
| 参考侧 MySQL DDL / 方言 | **禁止引入**。运行期 MySQL 依赖 = 0、MySQL 方言 = 0 |
| `SerialNumberIdEnum` 其余 16 个枚举 | 见 §3.3，W5 已决定的业务范围裁剪，恢复只会产生死代码 |
| 参考侧整体目录 | **零修改**（`git status` 对 `project-reference-examples/` 为空） |

---

## 7. 本轮实际改动清单（8 个文件）

全部改动都指向同一个问题：**基线里写死了另一台开发机的绝对路径 `D:/Browser Download/...`**。
本机只有 C 盘，该路径不存在，导致文件上传类集成测试必然失败（与业务代码无关）。

| 文件 | 改动 |
| --- | --- |
| `sa-base/src/main/resources/dev/sa-base.yaml` | `upload-path` 改为 `${XSY_V2_UPLOAD_PATH:../.runtime/upload/}` |
| `sa-admin/src/main/resources/dev/application.yaml` | `log-directory` 改为 `${XSY_V2_LOG_DIR:../.runtime/logs}/...` |
| `sa-admin/src/test/.../test/PgITPaths.java` | **新增**。以 `interface` 提供编译期常量（可被注解引用），值为仓库相对的 `../.runtime/...` |
| `ScmW2PgITBase.java` | 引用 `PgITPaths` 替换 `D:` 字面量 |
| `ScmW3PgITBase.java` | 同上 |
| `ScmW5PgITBase.java` | 同上 |
| `ProductPgIT.java` | 同上 |
| `SmartAdminMapperPgValidationIT.java` | 同上 |
| `.gitignore` | 新增 `.vscode` 一行（与既有 `.idea/` 对齐；IDE 元数据不应入库） |

> 附带清理：`xsy-scm-server/pom.xml` 一度被加入了冗余的 surefire `systemPropertyVariables`
> 与两个 `xsy.v2.*` 属性（含 `D:` 硬编码）。因 `PgITPaths` 已用仓库相对路径解决问题，
> 这些内容**已全部移除，pom.xml 现与基线逐字节一致**。

**未触碰**：W1–W5 任何业务代码、迁移脚本、路由核心、参考目录。

---

## 8. 测试结果

### 8.1 本轮实测（本机复现）

| 门禁 | 结果 | 对比 W5 基线 |
| --- | --- | --- |
| 后端单元测试 | **317 / 0 / 0 / 0** BUILD SUCCESS | 一致 ✓ |
| PostgreSQL 集成测试 | **19/19**（本轮定向重跑 `*PgIT`）、全量 **182 / 0 / 0 / 0** BUILD SUCCESS | 一致 ✓ |
| PG Mapper 解析 | **validated 1764 / failed 0 / knownFailure 1 / skipped 321** | 完全一致 ✓ |
| 前端单元测试 | **50 / 50 PASS** | 一致 ✓ |
| ESLint | **0 error**（3 条均为既有 warning，未新增） | 一致 ✓ |
| TS 基线棘轮 | SCM 新增 **0**；全仓 1973 ≤ 1974 | 一致 ✓ |
| Vite 构建 | **✓ built**（`--mode localhost`，产物含 `VITE_APP_API_URL=127.0.0.1:18080`） | ✓ |
| SmartAdmin 原生 Playwright 冒烟 | **17 / 17 PASS** | 新增门禁 ✓ |
| W1–W5 Playwright 全回归 | **23 / 23 PASS** | 一致 ✓ |
| SmartAdmin 功能探针 | **18 / 18 功能、26 / 26 接口，全部 PASS** | 新增门禁 ✓ |
| Flyway history | **V1–V16 全部 applied、0 failed** | 一致 ✓ |
| 迁移哈希 | **16 / 16 一致**（按各自记录换行格式） | 一致 ✓ |

功能探针逐项结果：

```
代码生成 151(3) 缓存管理 133(2) 定时任务 221(2) 系统配置 109(1) 数据字典 110(3)
文件管理 193(1) 登录日志 143(1) 操作日志 81(1)  心跳监控 206(1) Reload 117(1)
单号管理 130(2) 更新日志 152(1) 文档中心 218(1) 登录失败锁定 214(1)
三级等保 250(1) 消息通知 149(2) 意见反馈 148(1) 数据追踪 114(1)
```

### 8.1.1 SmartAdmin 原生 Playwright 冒烟覆盖（工作令第 9 条要求项）

`xsy-scm-web/e2e/smartadmin-native.spec.ts`，共 **17 个用例 / 全部 PASS**：

| # | 覆盖模块 | 路径 | 断言 |
| --- | --- | --- | --- |
| 1 | 代码生成 | `/support/code-generator` | 页面可达、表列表接口 `code=0` |
| 2 | 监控服务·心跳 | `/support/heartbeat` | 页面可达、接口 `code=0` |
| 3 | 定时任务 | `/support/job` | 页面可达、任务列表 `code=0` |
| 4 | 缓存管理 | `/support/cache` | 页面可达、缓存列表 `code=0` |
| 5 | 系统配置 | `/support/config` | 页面可达、配置列表 `code=0` |
| 6 | 数据字典 | `/setting/dict` | 页面可达、字典列表 `code=0` |
| 7 | 文件管理 | `/support/file` | 页面可达、文件列表 `code=0` |
| 8 | 登录登出记录 | `/support/login-log` | 页面可达、列表 `code=0` |
| 9 | 用户操作记录 | `/support/operate-log` | 页面可达、列表 `code=0` |
| 10 | 文档中心 | `/support/help-doc` | 页面可达、列表 `code=0` |
| 11 | 网络安全·三级等保 | `/support/security-protect` | 页面可达、列表 `code=0` |
| 12 | 网络安全·敏感数据脱敏 | `/support/data-tracer` | 页面可达、脱敏开关读取 `code=0` |
| 13 | 单号管理 | `/support/serial-number` | 页面可达、列表 `code=0` |
| 14 | 更新日志 | `/support/change-log` | 页面可达、列表 `code=0` |
| 15 | 意见反馈 | `/support/feedback` | 页面可达、列表 `code=0` |
| 16 | 消息管理 | `/support/message` | 页面可达、列表 `code=0` |
| 17 | 全局 | 全部页面 | 无 404 / 403 / 500，无 `pageerror` |

> **踩坑记录（已修复，值得留档）**：前端路由是**由登录用户的角色菜单树动态注册**的，
> 而 `router/index.ts` 的 `beforeEach` **不阻塞导航等待菜单拉取**。因此直接深链
> `/#/<path>` 会在 `routerMap` 尚未填充时命中 404 页
> （"对不起，您访问的内容不存在!"）。用例中必须先
> `page.goto('/#/home')` + `waitForLoadState('networkidle')` 完成动态路由注册，再跳转目标路由。
> 这不是缺陷，是既有的产品契约；已在 spec 中以注释固化。

### 8.2 不变量核验

| 不变量 | 结果 |
| --- | --- |
| W1–W5 回归 PASS | ✓ |
| SCM TS 新增错误 = 0 | ✓ |
| 运行期 MySQL 依赖 = 0 | ✓（pom 中仅剩一个**未被引用的** `mysql-connector-j.version` 属性；依赖块已被"V2 基线为 PostgreSQL"的注释取代） |
| 运行期 MySQL 方言 = 0 | ✓（`IFNULL`/`DATE_FORMAT`/`GROUP_CONCAT`/反引号 的命中**全部位于解释性注释内**） |
| 参考目录零修改 | ✓ |
| V1–V16 hash 不变 | ✓（16/16） |
| 前端路由核心未改 | ✓（diff 为空） |

---

## 9. 交付物

| 文件 | 说明 |
| --- | --- |
| `docs/architecture/2026-09-17-smartadmin-native-feature-parity-audit.md` | 逐特性 × 10 列对照矩阵（本报告的依据） |
| `docs/architecture/2026-09-17-smartadmin-native-feature-sync-report.md` | 本文件 |
| `xsy-scm-web/e2e/smartadmin-native.spec.ts` | SmartAdmin 原生 Playwright 冒烟：断言页面可达、关键查询接口 `code=0`、无 404/403/500、无 `pageerror` |
| `sa-admin/src/test/.../test/PgITPaths.java` | 消除集成测试对机器盘符的依赖 |
| `sa-admin/src/test/.../test/Sm4EncryptCli.java` | 复用项目自身 SM4 实现，供测试脚本生成登录密文 |

> `tools/` 目录在 `.gitignore` 中（`/tools/`），故本轮重建的
> `smartadmin_parity_audit.py`、`smartadmin_feature_probe.mjs`、`e2e_accounts.py` 及 5 个
> 波次包装脚本均为**本机工具，不入库**。
>
> 本轮同时**补齐了 W1–W5 只读账号机制**：W1–W5 的 e2e 用例以 `<主账号>_read` 登录，
> 该账号绑定一个只读角色（授予全部目录/菜单 + 非写入类按钮，实测 128 项授权，
> 对照 `SUPER_ADMIN` 的 241 项）。若缺该角色，账号菜单树为空 → 前端无路由 → 深链 404，
> 表现为"查询按钮找不到"。属于测试夹具缺失，而非产品回归。用例结束后夹具全部清理，
> 数据库中只剩种子 `admin` 与 `SUPER_ADMIN`（241 项授权原样保留）。

### 9.1 本机环境适配（不改变任何业务语义）

| 问题 | 处置 |
| --- | --- |
| `dev/application.yaml` 日志目录硬编码 `D:/Browser Download/...` | 改为 `${XSY_V2_LOG_DIR:../.runtime/logs}/...` |
| `dev/sa-base.yaml` 上传目录同样硬编码盘符 | 改为 `${XSY_V2_UPLOAD_PATH:../.runtime/upload/}` |
| 5 个 PG IT 基类各自硬编码 `D:` 路径 | 抽出 `test/PgITPaths.java`，统一走仓库相对路径 |
| 宿主 npm install 反复失败 | 改用 Docker Desktop 的 `node:22-alpine` 容器执行安装（宿主的批量删除/写入保护会打断 npm reify） |
| 容器内 Vite dev server 无法监听端口 | 改为构建静态产物 + `npx serve` 托管，再让**宿主**执行 Playwright（浏览器已预装） |

> `xsy-scm-server/pom.xml` 曾一度加入与上述等价的 surefire `systemPropertyVariables`，
> 但那会把 `D:` 盘符又写回去。已在**验证 `PgITPaths` 单独即可满足**之后将该改动
> 完整回滚——`git diff xsy-scm-server/pom.xml` 当前为空。

---

## 10. 结论

1. **SmartAdmin 原生能力已在正式 V2 中完整在位**，本轮无需搬运任何代码。
2. **零新增迁移、零菜单改动、零权限改动**——闭环本就完整。
3. 唯一实质收益是**消除了与开发机绑定的硬编码路径**，让集成测试在任意机器上可跑。
4. 4 个演示 ERP 菜单为**有理有据的有意裁剪**，符合"不覆盖 SCM 业务模型"的约束。
5. 全部门禁通过，且与 W5 验收基线**逐项一致**。

**W5.5 完成。停止。不进入 W6 Inventory。**
