# F0 Object Storage Activation 验收报告

日期：2026-09-17。状态：**COMPLETE**。

本轮由独立验收者执行 A–J 十个阶段，全部硬门禁**真实通过**（无 skip 计入 PASS，无复制的历史数字）。
实现代码未在本轮改动；仅做了使门禁可运行所必需的最小环境/夹具修复（见 §6）。

依据：[Target Design](./2026-09-17-f0-object-storage-target-design.md)、
[HD-1～HD-9 批准记录](./2026-09-17-f0-object-storage-approval.md)、
[设计评审](./2026-09-17-file-storage-design-review.md)。

---

## 勘误：V17 版本号冲突与重编号（2026-09-17 追加）

> 本节是**事后勘误**，不修改上文任何验收数字——那些数字在验收当时都是真实且正确的。
> 勘误只说明**版本号在合并 W5.5 之后发生的变化**。

**结论：F0 自己的 migration 号未变，仍是 `V17__sa_config_file_upload_size.sql`。**

```text
V17  V17__sa_config_file_upload_size.sql   F0    ← 本报告验收的这个，未改名、未改内容
V18  V18__scm_menu_icons.sql               W5.5  ← 由 W5.5 的 V17 重编号而来
```

**发生了什么**：F0 的这个 V17 创建于 15:15:51，当时工作区 migration 上限是 V16，选 V17 正确。
但 W5.5 波次的独立增量 `fdfd643` 在 16:01:33 已把一个 migration 命名为 `V17`
（`V17__scm_menu_icons.sql`，SCM 侧边栏图标）并推到远端。本验收报告执行期间（16:45–16:51）
本地基线尚未包含 `fdfd643`，所以当时仓库里只有一个 V17，**报告里的所有 V17 断言都成立**。
本地在 17:24:55 才 fast-forward 追上 `fdfd643`，随后提交时未复核上限，导致仓库同时存在两个 V17
（Flyway 11.7.2 在**解析期**即抛 `Found more than one migration with version 17`，
新库老库全部无法启动）。

**处置**：按「保留已被真实库应用过的版本号」原则，F0 的 V17 保持不变（开发库
`version 17` 的 checksum `998995225` 未变，无需 `repair`），把 W5.5 的
`V17__scm_menu_icons.sql` 改名为 `V18__scm_menu_icons.sql`（**内容字节不变**，
sha256 前后一致：`ed715acc…da903`）。

**因此本报告中以下表述需按下表理解（内容不变，只是编号环境变了）：**

| 报告原文位置 | 当时（仅 F0 的 V17） | 现在（V17 + V18 并存） |
| --- | --- | --- |
| §A 静态保护「仅新增 V17」 | 正确 | 仍正确（F0 只新增 V17）；W5.5 的增量另计为 V18 |
| §E「version=17 success=true」 | 正确 | 仍正确；`installed_on=16:10:05` 未变 |
| §E「总行数 18 = 17 条版本化 + 1 条基线」 | 正确 | 现为 **19 = 18 条版本化 + 1 条基线**（新增 V18） |
| §6「上限抬到 `"17"`」 | 正确 | 现已抬到 **`"18"`**（`ScmPurchaseMigrationIT`，实测 4/0/0/0） |

**F0 自身迁移的冻结清单**（本报告此前缺失，现补上）：
[`f0-applied-migrations.sha256`](./f0-applied-migrations.sha256) →
`6d22bd494e1a17fbade70bb00b5742fc916e629be21457cf9bb1287fc07f6edd *V17__sa_config_file_upload_size.sql`

完整审计、四场景只读实测、修复实施与 fresh-DB 实测见
[V17 Flyway 版本冲突审计报告](./2026-09-17-v17-migration-conflict-audit.md)。
**全程未执行 `flyway repair`，未改写 `flyway_schema_history` 任何一行。**

---

## 1. 验收环境（真实运行）

| 组件 | 实际形态 |
| --- | --- |
| PostgreSQL | 本机 18.3，端口 15432，schema `xsy_v2`，库 `xsy_scm` |
| Redis | 容器 `xsy-v2-redis`（redis:7-alpine），端口 6379 |
| MinIO | 容器 `xsy-scm-f0-minio-minio-1`，官方源码 tag 编译，宿主端口 **19000/19001** |
| 后端 | `sa-admin-dev-3.0.0.jar`，端口 18080；cloud 与 local 两轮分别启动 |
| 前端 | Vite dev，端口 18081 |
| 浏览器 | Playwright 1.62.1，Chromium，workers=1 |

MinIO 宿主端口使用 19000/19001 而非默认 9000/9001：本机 Windows 排除端口范围
8908–9007 覆盖 9000/9001，绑定会被拒绝。容器内仍为 9000/9001，`.env` 中 endpoint
相应指向 `http://127.0.0.1:19000`。**这是本机约束，不是产品约束**。

---

## 2. 阶段结果总览

| 阶段 | 门禁 | 实测结果 | 判定 |
| --- | --- | --- | --- |
| A | 静态保护检查 | `git diff --check` 干净；V1–V16 哈希 28/28 一致；仅新增 V17；reference/前端 `src`/W1–W5 SCM 目录零改动；无 `product_media`；无 W6 | PASS |
| B | MinIO 兼容性 | 官方源码 tag `RELEASE.2025-10-15T17-29-55Z` 编译成功；bootstrap 退出码 0；bucket `xsy-scm-dev`；匿名策略 `custom`（仅 `s3:GetObject` on `public/*`） | PASS |
| C | Cloud 环境自检 | MODE=cloud、ENDPOINT=19000、PATH_STYLE=true、SEND_ACL=false、EXPIRE=2；签名 URL 可生成 | PASS |
| D | 后端专项测试 | **39 / 0 / 0 / 0**（零 skip，真实 MinIO+PG+Redis） | PASS |
| E | V17 验证 | `version=17 success=true`；配置仅 `maxUploadFileSizeMb` 30→20；`fileDetectFlag` 保持 true；JSON 键数仍 9 | PASS |
| F | Cloud Playwright | **7 passed / 0 failed / 0 skipped**（11.6s） | PASS |
| G | 真实页面 + 读取门禁 | 4 个页面零 console 错误、零 HTTP≥400；读取门禁矩阵 **mismatches=0** | PASS |
| H | Local Regression | 见 §3；后端 351/0/0/0、IT 187/0/0/5、前端 50/0/0、Playwright 40/0/0/0 | PASS |
| I | 最终复核 | 冻结迁移、reference、W1–W5、`product_image`、无 `product_media`、未进 W6 全部复核通过 | PASS |
| J | 报告更新 | 本文件 | 完成 |

### 2.1 桶策略匿名行为（实测复核，无凭据 HTTP）

复核时间：报告定稿时重新跑过一遍，对象为临时探针，测完即删。

```text
mc anonymous get dev/xsy-scm-dev                    → custom
anon GET  public/verify/policy-probe.txt            → 200   ← 匿名可读 public/*
anon GET  private/verify/policy-probe.txt           → 403   ← private/* 不得匿名
anon ListBucket (?list-type=2)                      → 403
anon ListBucket (?list-type=2&prefix=public/)       → 403
anon PUT  public/verify/anon-put.txt                → 403
anon DELETE public/verify/policy-probe.txt          → 403
GET /minio/health/live                              → 200
```

> 口径提醒：`anon GET .../public/`（不带 `list-type`）返回 **404**，那是「按对象 key 取一个不存在的
> `public/` 对象」，**不是列桶**；列桶请求（带 `list-type=2`）才是 403。两者不要混为一谈。

---

## 3. H 阶段 Local Regression 实测数字

### 后端（`XSY_FILE_STORAGE_MODE` 未设 → 默认 `local`）

| 门禁 | 实测 | 历史基线 | 说明 |
| --- | --- | --- | --- |
| `mvn -B -pl sa-admin -am test` | **351 / 0 / 0 / 0** BUILD SUCCESS | 317 | +34 = F0 新增 3 个测试类（24+6+4），差额完全吻合 |
| `mvn ... test -Dtest=*IT` | **187 / 0 / 0 / 5** BUILD SUCCESS | 182 | 182 个非 F0 IT 全部执行且通过；5 个 skip 见下 |
| `mvn ... package -DskipTests` | BUILD SUCCESS，jar 已 repackage | — | 源文件与 `target/classes` 中比 jar 新的文件数 = 0 |

`Skipped: 5` 全部来自 `F0FileStorageCloudIT`：该套件以
`@EnabledIfEnvironmentVariable(XSY_FILE_STORAGE_MODE=cloud)` 显式门控，local 模式下
按设计禁用。这 5 个用例已在 **D 阶段**以 cloud 模式真实执行并 5/0/0/0 通过，
**本报告不把这 5 个 skip 计为 PASS**。

首轮 IT 运行出现 **1 个真实失败**，见 §6.3。

### 前端

| 门禁 | 实测 | 历史基线 | 说明 |
| --- | --- | --- | --- |
| `npm run lint`（`eslint src`） | exit 0，无告警输出 | — | PASS |
| `npm run typecheck`（`vue-tsc --noEmit`） | exit 2，**1973 条既有诊断 / 227 个文件** | 1974 | 既有基线，非本轮引入；按棘轮规则判定 |
| `python ../tools/ts_baseline_ratchet.py check` | **RESULT: PASS**，baseline 1974 → current 1973（delta −1），**scm errors 0**、**new errors 0**、fixed 1 | — | 权威判定 |
| `npm run test` | **50 pass / 0 fail / 0 skipped / 0 todo** | 50 | PASS |
| `npm run build` | **exit 0**，5117 modules transformed，422 个产物文件 | — | 见 §5.1 的执行方式差异 |
| Playwright（native + W1–W5） | **40 passed / 0 failed / 0 skipped**（2.5 min） | native 17 / W1–W5 23 | JSON：`expected=40, unexpected=0, skipped=0, flaky=0` |

typecheck 的 1973 条诊断是 SmartAdmin 上游既有基线（主要是 `TS7006` 839、
`TS2339` 404、`TS6133` 142），**不声称为零错误**；判定依据是仓库现行的 TS 棘轮规则。

---

## 4. D / E / F / G 阶段细节

### D 阶段：后端专项测试（39 / 0 / 0 / 0）

```text
F0FileStorageCloudIT              5 / 0 / 0 / 0   (52.72 s, 真实 MinIO)
FileAccessGuardTest              24 / 0 / 0 / 0
FileConfigTest                    6 / 0 / 0 / 0
FileStorageCloudServiceImplTest   4 / 0 / 0 / 0
合计                             39 / 0 / 0 / 0
```

### E 阶段：V17 运行结果

```text
flyway_schema_history: version=17  success=true  installed_on=2026-09-17 16:10:05
总行数 18 = 17 条版本化迁移 + 1 条 << Flyway Schema Creation >> 基线(version IS NULL)
t_config 文件上传配置（JSON 键数 9，其余 7 键完好）：
  maxUploadFileSizeMb: 20        ← 由 30 改为 20（V17 唯一变更）
  fileDetectFlag: true           ← 保持不变
```

应用启动时 `validate-on-migrate: true` 通过 ⇒ V1–V16 的 checksum 未被改写。

### F 阶段：Cloud Playwright（7 / 7）

7 个场景全部真实通过：文件管理 UI 上传并出现在列表、私有图片经 presigned URL 预览 +
页面下载字节一致、Notice/HelpDoc 附件 API 对他人开放但对匿名拒绝、COMMON/feedback 归属拒绝、
畸形前缀与混合 key 批量失败关闭、>20 MiB 上传被拒、Tika 拒绝伪装成 PNG 的 HTML。

### G 阶段：真实页面与读取门禁矩阵

四个真实页面（Notice 详情、HelpDoc 用户视图、Feedback 列表、OA 企业证照）：
`consoleErrors=[]`、`http>=400=[]`、附件预览 `previewNaturalWidth=1`（真实加载，非占位）。

读取门禁矩阵（`GET /support/file/getFileUrl`），**mismatches = 0**：

| 资源 | 普通员工 | 管理员 | 有 `support:file:query` 的员工 | 匿名 |
| --- | --- | --- | --- | --- |
| `private/notice/`（creator=admin） | allow | allow | allow | deny (200/30007) |
| `private/help-doc/`（creator=admin） | allow | allow | allow | deny (200/30007) |
| `private/common/`（creator=admin） | **deny (403/30005)** | allow | allow | deny (200/30007) |
| `private/feedback/`（creator=employee） | allow | allow | allow | deny (200/30007) |

口径说明：匿名调用的实际观测是 **HTTP 200 + 业务码 30007**（SmartAdmin 鉴权失败的
既有 envelope 形态），只有 `/support/file/*` 的 `FileAccessGuard.AccessDenied`
返回 **HTTP 403 + code 30005**。两者不可混为一谈。

---

## 5. 仓库卫生问题（既有，非 F0 引入）

### 5.1 `xsy-scm-web/dist-verify/` 被 git 跟踪且内容陈旧

- `dist-verify` **有 369 个文件在版本库中**，而 `.gitignore` 只忽略 `dist`、`dist-ssr`，
  **不含 `dist-verify`**。
- 该目录内容停留在 **W3 时期**：`js/order-*` = 0 个、`js/purchase-*` = 0 个；
  当前源码构建产出 **422 个文件**，其中 `order-*` 15 个、`purchase-*` 17 个，
  并新增 `favicon*.ico`、`logo.png`。
- 后果：`npm run build`（`--emptyOutDir` 指向 `dist-verify`）会重写这 369 个已跟踪文件并新增约 53 个。
  本轮**没有**执行该写入（见下），也未提交任何内容。

本轮 `npm run build` 的等效执行方式：沙箱的批量删除保护会拦截 vite 清空 `dist-verify`
（`SAFE_DELETE_BULK_CONFIRM_REQUIRED`，count 84 > threshold 50），因此改为在仓外临时
outDir 执行同一条 `vite build --mode production`：

```text
npx vite build --mode production --outDir <repo-external-scratch> --emptyOutDir
→ exit 0，5117 modules transformed，1m59s，422 个产物文件
```

F0 对 `xsy-scm-web/src` 的改动为 **0**，所以该构建验证了「当前源码可产出生产包」这一门禁本身。

**建议（未执行，需另行决策）**：把 `dist-verify` 加入 `.gitignore` 并 `git rm --cached -r`，
或明确将其作为「每轮验收后刷新的证据目录」并接受它每次都产生大 diff。当前状态两者皆非。

### 5.2 Playwright 的文件过滤参数在 1.62.1 下不生效

```text
npx playwright test --list 'scm-.*\.spec\.ts'   →  Total: 47 tests in 8 files   ← 未过滤
npx playwright test --list e2e/smartadmin-native.spec.ts  →  Total: 17 tests in 1 file
```

位置参数被当作**路径过滤器**，正则形式不生效（会把全部 8 个 spec 都跑起来）。
本报告与后续波次应改用**显式枚举文件路径**：

```bash
npx playwright test e2e/smartadmin-native.spec.ts e2e/scm-customer.spec.ts e2e/scm-order.spec.ts \
  e2e/scm-pricing.spec.ts e2e/scm-product.spec.ts e2e/scm-purchase.spec.ts e2e/scm-supplier.spec.ts
```

### 5.3 `smartadmin-native.spec.ts` 的账号来源与注释不符

该 spec 头部注释写「复用 W5 的临时管理员脚本（tools/e2e_accounts.py）」，但：

- `tools/e2e_accounts.py` **不存在**；
- 代码调用的是 `tools/w5_e2e_accounts.py`，它创建的是 **非管理员**、仅持有业务菜单
  （401–753）的员工账号。

后果：该 spec 默认路径下 17 个原生页面全部落到 404，用例以 20s 等待超时失败
（本轮实测每例固定 ~22.3s）。正确做法（与本仓库既有约定一致、也是本轮最终通过的方式）是：

```bash
python tools/smartadmin_probe_account.py setup      # 需要 SA_PROBE_NAME/SA_PROBE_PASSWORD
W5_E2E_NAME=<sa_audit_账号> W5_E2E_PASSWORD=<密码> W5_E2E_MANAGED_EXTERNALLY=1 npx playwright test ...
python tools/smartadmin_probe_account.py cleanup
```

即原生冒烟必须使用 `administrator_flag=TRUE` 的 `sa_audit_*` 账号。**建议后续波次修正
该 spec 的注释与默认账号策略**，本轮未改动（避免顺手重构）。

### 5.4 运行 Playwright 前必须归档 `outputDir`

`playwright.config.ts` 的 `outputDir` 为 `../.runtime/playwright-results`。Playwright
每次启动会清空该目录，而沙箱批量删除保护会拦截（实测 count 384 > 50）。
每次运行前需 `mv` 归档该目录（移动不是删除，可正常通过）。

### 5.5 `.runtime/upload/` 的本地落点

`dev/sa-base.yaml` 的 `upload-path` 默认 `../.runtime/upload/`，相对**进程工作目录**。
从仓库根启动后端时实际落点是**仓库父目录**下的 `.runtime/upload/`，不是仓内同名目录。
本轮已在父目录清理 H 阶段产生的 2 个文件。

---

## 6. 本轮的最小修复（真实 bug 才修）

原则：先测试，发现真实问题才做最小修复，然后重跑对应门禁。全部修复已重跑验证。

### 6.1 环境/工具链修复（非产品代码）

| # | 问题 | 修复 |
| --- | --- | --- |
| 1 | Docker Hub 的 `minio/mc`、`minio/minio` 仓库已被移除（API 返回 HTTP 404） | `deploy/minio/compose.yaml`、`README.md`、Target Design、Approval 的 mc 镜像统一改为官方 registry `quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z`，固定 tag 与摘要不变 |
| 2 | `proxy.golang.org` 不可达，官方源码镜像无法构建 | `deploy/minio/Dockerfile` 增加可注入的 `ARG GOPROXY`（默认值不变），本机以 `--build-arg GOPROXY=https://goproxy.cn,direct` 构建 |
| 3 | 9000/9001 被 Windows 排除端口范围 8908–9007 占用 | compose 宿主端口改为 `${MINIO_API_PORT:-9000}` / `${MINIO_CONSOLE_PORT:-9001}`（默认值不变），本机用 19000/19001 |
| 4 | `docker compose` 插件在 Git Bash 下不可见，回退 legacy builder 后 `--mount=type=cache` 报错 | 将 `docker-compose.exe`、`docker-buildx.exe` 放入 `~/.docker/cli-plugins/`（仓外，不涉及版本库） |
| 5 | PATH 上的 Python 缺 `argon2`，`tools/w{2,5}_e2e_accounts.py` 建号失败 | 使用既有 managed venv `C:/Users/17757/.workbuddy-ai/binaries/python/envs/default/Scripts`（内含 `argon2-cffi 25.1.0`）；仓外，不涉及版本库 |

### 6.2 测试夹具修复（测试资产）

**`xsy-scm-web/e2e/f0-file-storage.spec.ts`（3 处）** —— 由真实失败定位，非猜测：

```ts
// 1) Ant Design 图标把自己的 aria-label（cloud-upload）并进按钮可访问名，
//    exact:true 永远匹配不到。
await page.getByRole('button', { name: /上传文件/ }).click();
// 2) 同上（图标 aria-label=search）；antd 还会给双中文字符标签插空格。
await page.getByRole('button', { name: /查\s*询/ }).click();
// 3) 该应用渲染的 antd 图片预览没有关闭按钮，且 Escape 实测无效
//    （按 Escape 后 imgVisible 仍为 true、遮罩 display:block 且覆盖全屏）；
//    点击遮罩才是真实关闭手段，且必须显式等待隐藏，否则遮罩持续拦截全页点击。
await page.locator('.ant-image-preview-wrap').click({ position: { x: 20, y: 20 } });
await expect(page.locator('.ant-image-preview-img')).toBeHidden();
```

`查看` / `下载` 按钮无图标，`exact:true` 经验证仍有效，**未改动**。
第 3 项属上游 antd/SmartAdmin 行为、非 F0 引入，按「不顺手重构」未改前端，只改测试的关闭手段。

### 6.3 `ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly` —— 真实陈旧断言

首轮 IT 运行 **1 failure**：

```text
ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly:160
assertThat(versions).containsExactly("1"..."16")
```

该用例把迁移上限**逐条硬编码到 V16**。F0 按批准追加 V17 后，`flyway_schema_history`
多出 `"17"`，断言立即失败。这是 V17 追加暴露的真实陈旧断言（其余 4 个迁移 IT 均使用
`IN (...)` 计数，不受追加影响；已全量排查，仅此一处）。

最小修复：把上限抬到 `"17"`，并同步 `@DisplayName` 与注释，说明上限随获批新迁移追加而抬升、
V1–V16 仍被逐条钉死。

```java
// 上限随获批的新迁移追加而抬升：F0 追加了 V17（仅数据，sa_config 文件上传大小），
// V1–V16 的内容与顺序仍被逐条钉死，任何回改/重排都会立刻失败。
assertThat(versions).containsExactly(
        "1", ..., "16", "17");
```

重跑：定向 `ScmPurchaseMigrationIT` **4 / 0 / 0 / 0** BUILD SUCCESS；随后完整 IT 套件
**187 / 0 / 0 / 5** BUILD SUCCESS。

**产品代码在本轮零改动。**

---

## 7. 发现的既有缺陷（与 F0 无关，未修）

| # | 缺陷 | 证据 | 影响 |
| --- | --- | --- | --- |
| 1 | `t_help_doc_catalog.parent_id` 为 NOT NULL（来自冻结 V5），但上游 DAO 的 insert 只写 `name, sort` | 在 PG 上通过页面/API 新建帮助文档目录必然失败 | 既有 PG 兼容缺陷。G 阶段改用 SQL 夹具建目录绕开 |
| 2 | antd 图片预览无关闭按钮，且 Escape 不生效 | 实测：Escape 后 `imgVisible=true`、遮罩 `display:block` 且 `pointer-events:auto` 覆盖全屏；点遮罩才 `display:none` | 上游 antd/SmartAdmin 行为；已反映到 F0 spec 的关闭手段 |
| 3 | 上述 §5.1–§5.5 五项仓库卫生问题 | 见 §5 | 不影响 F0 正确性，但会持续干扰后续波次的验收 |

---

## 8. F0-DEBT-01：必须保留的边界

`@JsonSerialize(FileKeyVoSerializer)` 在**服务端展开附件**，链路为
`FileKeyVoSerializer → FileService.getFileList() → IFileStorageService.getFileUrl()`，
**全程不做任何按用户的权限过滤**，直接对任意 fileKey 签名。

因此：**通过业务字段（Notice/HelpDoc/Enterprise 等 VO 上的附件字段）返回的附件 URL，
绕过 F0 的 Controller 层读取门禁**。F0 的门禁只覆盖显式的
`/support/file/getFileUrl`、`/support/file/downLoad`、`/support/file/queryPage` 路径。

**强制约束（不变）**：正式引入任何非管理员业务角色之前，OA 企业证照等 COMMON 私密资产
必须迁到「业务 permission + ownership/relation + FileService」的读取方式。
禁止为兼容页面重新放宽 `private/common/`，禁止把 Notice/HelpDoc 的登录豁免变成桶匿名授权。

---

## 9. 本轮清理

| 对象 | 数量 | 结果 |
| --- | --- | --- |
| 临时身份 `f0_e2e_admin` / `f0_e2e_emp` / `f0_e2e_emp_file` | 3 | 已删（含 role / role_menu / role_employee） |
| 被中断运行遗留的 `w5_e2e_native_*` | 2 | 已删 |
| 管理员夹具 `sa_audit_f0h1` | 1 | 已由脚本 cleanup 删除 |
| `t_file` 元数据（`private/%`，本会话创建） | 47 | 已删 |
| MinIO 对象（`xsy-scm-dev/private/`） | 46 | 已删 |
| MinIO 验证探针（`private/verify/` + `public/verify/`） | 4 | 已删（含 B 阶段遗留的 `public/verify/probe.txt`，以及 §2.1 策略实测用的 2 个临时探针） |
| 本地模式上传残留（仓外 `.runtime/upload/`） | 2 | 已删 |
| 业务夹具：notice 4 / help_doc 2 / catalog 1 / enterprise 2 / feedback 2 | 11 | 已删 |

清理后残留核对：临时身份 **0**、临时角色 **0**、`t_file` 残留 **0**、业务夹具残留 **0**、
MinIO `xsy-scm-dev/` **两个前缀均为 0 个对象**（`private/` 与 `public/` 逐项 `mc ls --recursive` 复核），
bucket 策略 `custom` 保持不变（匿名只读仍只作用于 `public/*`，见 §2.1）。

> 清理范围严格限定为本会话创建的测试夹具，使用精确 `WHERE` 条件，未做任何前缀级批量删除。

---

## 10. 停止边界

- **未提交、未推送**，分支保持 `main`。
- **未进入 W6**：无 inventory / sorting / delivery / finance / traceability 的任何迁移或代码。
- 不新增 `product_media`；`product_image` 保持为正式商品图片模型（V6 冻结 + 实体/DAO/Manager 完整）。
- 不新增 Maven / resources profile，不改 pom，不新增表或菜单/角色种子权限。
- `deploy/minio/.env` 命中现有 `.gitignore`；未生成或输出任何真实凭据、token 或 presigned URL。

W6 保持 **NOT STARTED**。
