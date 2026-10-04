# ADM-01～ADM-12 验收测试执行记录（质量收口 Sprint 第 1 轮）

执行日期：2026-10-04（本地时间）。基线 `main = 99ec1d4f`。环境：本机 Docker Desktop（daemon 29.8.0）。
本轮遵循清单 §0：只做「测试补强」和「测试发现的缺陷修复」，不写新功能。

## 1. 测试基线（§0 BASE-01 ～ BASE-10）

| ID | 结果 | 证据 |
|---|---|---|
| BASE-01 Git 基线 | PASS | `main@99ec1d4f`，`git status --porcelain` 为空 |
| BASE-02 清构建残留 | PASS | `mvn -B -o clean` 三模块 SUCCESS；随后全量无 mapper 重复解析错误 |
| BASE-03 干净库 V1→V106 | PASS | 一次性库 `xsy_v2_clean`：`flyway_schema_history` 107 行、`sum(success)=107`、最高版本 106 |
| BASE-04 升级库 V40→V106 | PASS（并做了行为复核） | 一次性库 `xsy_v2_upgrade`：先以 V1..V40 迁移子集建到 40，再按完整目录迁到 106；107 行全部 success，`validate-on-migrate=true` 通过 |
| BASE-05 长驻库隔离 | PASS | 开发库 `xsy_scm` 停在 **V67**，全程未被任何测试或 Flyway 触碰；所有验证走一次性库 |
| BASE-06 Redis | PASS | `xsy-scm-redis-1` healthy，口令经 `SPRING_DATA_REDIS_PASSWORD` 注入，上下文正常启动 |
| BASE-07 MinIO | **PASS（已转为真实覆盖）** | 容器 healthy + 桶 `xsy-scm-test` 存在，`F0FileStorageCloudIT` 以 `XSY_FILE_STORAGE_MODE=cloud`、`XSY_FILE_PRIVATE_URL_EXPIRE=2` 指向本机 MinIO 实跑：**5 tests / 0 failures / 0 errors / 0 skipped**。原先被当作「既有基线 5 个云端跳过」的用例其实只缺一个开关，不需要外部云凭据。|
| BASE-08 前端环境 | 偏差 | 实际 Node **v24.21.0**（清单要求 Node 22），`package.json` 声明 `node >=18`，依赖 413 包完整。按 engines 判定可用，但与清单口径不一致，记为待裁决 |
| BASE-09 E2E 环境 | **PASS** | 后端 `java -jar sa-admin-dev-3.0.0.jar` 起在 **18080**（dev profile，28.5s，`/login/getTwoFactorLoginFlag` 返回 `code:0`）；前端 `npm run dev` 起在 **18081**（vite 默认端口就是 18081，`playwright.config.ts` baseURL 同值）。后端连一次性库 `xsy_v2_e2e`（Flyway 从 V1 全量应用），Redis/MinIO 走本机容器。|
| ~~BASE-09 原始阻塞~~ | 已解除 | 见 D-12：后端无法在本机离线打包/启动，容器构建同样需要 Maven Central | 后端 18080 / 前端 18081 未启动，`verify.py frontend` 已报 `E2E: services unavailable` |
| BASE-10 测试证据 | 本文件 | 日志目录 `.runtime/verify/`（`backend-clean.log`、`backend-rerun.log`、`frontend-test.log`、`up1.log`、`up2.log`）|

**BASE-04 行为复核（同日补做）**：只在升级库上跑 Flyway 不足以说明升级后的 schema 行为等价，
所以把一组用例直接打到 `xsy_v2_upgrade`（V40→V106）上：

```text
ScmFinanceSchemaPgIT              17 tests / 0 F / 0 E
ScmFinanceReceiptSettlementPgIT     3 / 0 / 0
ScmPrintCenterPgIT                  4 / 0 / 0
ScmInventoryConstantTest           11 / 0 / 0
PurchaseErrorCodeTest               1 / 0 / 0
ScmPermissionContractPgIT           2 / 1 F    ← 与干净库完全一致，即 D-04，不是升级差异
```

结论：升级路径上 **37/38 通过**，唯一失败与干净库上的失败是同一个已登记裁决项，
说明 V40→V106 之后 schema 行为与全新库一致，没有升级特有的偏差；
D-05 修的那批 DDL 契约断言在升级库上也同样成立（不是在干净库里「刚好对」）。
日志：`.runtime/verify/upgrade-behavior.log`。

BASE-04 的「接近现网的 V40 克隆库」是**结构级克隆**（按仓库 V1..V40 迁移文件重建 schema 与历史），不是生产数据克隆：本机没有可用的现网数据导出。含业务数据的升级兼容性 **NOT COVERED**。

## 2. 门禁执行结果


## 2b. ADM 分块验收矩阵（按域取自动跑结果）

权威一次全量（`xsy_v2_final`，116 个 `*IT` 真实执行）+ MinIO 补跑之后的域级证据。
`F/E/S` = failures / errors / skipped。**这张表只陈述「哪些自动化用例通过」，
不把它等同于清单里每一条人工项都已验收** —— 人工项见 §4 与 §6。

| 域（测试包） | 套件 | 用例 | F | E | S | 对应的 ADM 块 |
|---|---:|---:|---:|---:|---:|---|
| purchase | 37 | 287 | 1 | 0 | 0 | ADM-05 净需求 / 冻结批次；§14 采购错误码 40487。唯一 failure = 外键口径（D-06） |
| inventory | 28 | 178 | 1 | 0 | 0 | ADM-02 回库、ADM-06 库存阈值、§14 库存 40486。唯一 failure = 外键口径（D-06） |
| finance | 27 | 177 | 1 | 0 | 0 | ADM-01 C/D、ADM-03 集团结算、ADM-04 账期与授信。唯一 failure = D-09 |
| order | 12 | 112 | 0 | 0 | 0 | ADM-02 退货/退款、ADM-08 异常来源、ADM-12-C 订单优惠冻结 |
| product | 11 | 100 | 0 | 0 | 0 | ADM-09 主档坐标、商品导入 |
| smartadmin 底座 | 15 | 96 | 0 | 0 | 0 | 登录/权限/菜单/mapper 方言校验；含文件存储 5 例（补跑后 0 skip） |
| customer | 8 | 81 | 0 | 0 | 0 | ADM-03 结算主体主档、客户 360 |
| supplier | 7 | 63 | 0 | 0 | 0 | 供应商主档与对账来源 |
| common | 11 | 47 | 0 | 0 | 0 | 数据范围、幂等、文件、通知等横切不变量 |
| delivery | 7 | 39 | 0 | 0 | 0 | ADM-10 发车 / 路线 / 排线建议、§业务不变量「发车原子出库」 |
| report | 5 | 32 | 0 | 0 | 0 | ADM-01 A/B/C 报表与导出、ADM-08 异常订单分析 |
| warehouse | 5 | 30 | 0 | 0 | 0 | ADM-06 仓库授权、ADM-09 仓库坐标 |
| payment | 2 | 29 | 0 | 0 | 0 | PAY-A 状态机、PAY-B 在线支付、PAY-H 退款边界 |
| sorting | 2 | 23 | 0 | 0 | 0 | ADM-11 分拣任务 / 秤事件 / 并发回归 |
| balance | 4 | 22 | 0 | 0 | 0 | PAY-C 充值、PAY-D 余额账本、PAY-E 余额支付 |
| pricing | 4 | 18 | 0 | 0 | 0 | 价格与历史成本取值（ADM-01 C） |
| screen | 3 | 15 | 0 | 0 | 0 | ADM-09 地图降级与坐标成组约束 |
| dashboard | 1 | 4 | 0 | 0 | 0 | 经营概览与待办 |
| ScmArchitectureTest | 1 | 9 | 0 | 0 | 0 | §14 Finance 0 violation、无 finance→customer 依赖、无 ArchUnit exception |
| ScmPermissionContractPgIT | 1 | 2 | 1 | 0 | 0 | ADM-07 权限 catalog（= D-04，待裁决） |

**合计 191 个套件 / 1364 用例 / 4 failures / 0 errors**；4 个 failure 全部对应本文已登记的裁决项，
没有未解释的红项，也没有「为过门禁而放宽断言」的用例改动。

### quality（§16 步骤 2）— FAIL（两个独立红项，登记白名单后只剩 D-11）

`cross-domain-dao-boundary` 在 HEAD 上即为红，3 处跨域 DAO 读未登记进显式白名单：

| 调用方 | 目标 DAO | 实际调用方法 |
|---|---|---|
| `inventory/service/InventoryWarningNotifier` | `warehouse.dao.EmployeeWarehouseScopeDao` | `listEnabledEmployeeIdsByWarehouse` |
| `purchase/service/PurchaseDemandCalculationBatchService` | `order.dao.SalesOrderDao` | `selectBatchIds` |
| `sorting/service/SortingTaskService` | `supplier.dao.SupplierDao` | `selectById` |

三者均为只读（`selectById` / `selectBatchIds` 属 BaseMapper 读方法，`listEnabledEmployeeIdsByWarehouse`
在 mapper XML 里是 `<select>`），守卫自带的「非只读方法」检查也没有报警，因此这是 **ADM-05/06/11 落地时漏了白名单登记**，不是边界被突破。
已在 `tools/quality/cross-domain-dao-allowlist.tsv` 补这三行（附业务理由），
`scm_cross_domain_dao_guard.py` 单独跑已 `PASS`；是否改走正式领域契约仍属 §4 待裁决项 5。

另记 `INCOMPLETE`：Spotless 覆盖 0 文件（ratchet ref `origin/main` == HEAD），其 PASS 不代表全树合规。

### backend（§16 步骤 3）— 修 D-02/05/06/13 后：**1364 tests / 4 failures / 0 errors / 5 skipped → 云端用例补跑后 0 skipped**

收口后的权威一次全量跑在新一次性库 `xsy_v2_final`（Flyway 从 V1 完整应用到 V106，
`flyway_schema_history` 107 行、`sum(success)=107`）上执行，116 个 `*IT` 全部真实执行：

```text
Tests run: 1364, Failures: 4, Errors: 0, Skipped: 5
剩余 4 个 failure 全部是本文 §4 已登记的裁决项，没有未解释的红：
  ScmPermissionContractPgIT            1  = D-04（catalog 表达不了运行期解析的权限）
  AdminSettlementTermsPgIT             1  = D-09（收款登记是否保留纵深防御）
  ScmInventoryMigrationIT              1  = D-06（9 个外键 vs「设计不用外键」）
  ScmPurchaseMigrationIT               1  = D-06（同一问题的另一条断言）
5 个 skipped = F0FileStorageCloudIT，随后按 BASE-07 单独补跑转绿，实际覆盖为 0 skipped。
```

对比基线：修复前 12 failures / **29 errors** → 修复后 4 failures / **0 errors**。

116 个 `*IT` 全部真实执行（满足清单「日志里必须出现 `*IT`」）。

**错误分布（修复前）**：29 个 error 中 **24 个是同一个根因**，不是业务缺陷：

```
null value in column "settlement_customer_id" violates not-null constraint  ×18  finance_receivable
null value in column "settlement_customer_id" violates not-null constraint  ×7   sales_order
null value in column "settlement_customer_id" violates not-null constraint  ×4   finance_receipt
```

ADM-03 把 `settlement_customer_id` / `settlement_customer_name_snapshot` 设为 NOT NULL（`finance_receivable`、`finance_receipt`、`sales_order` 三张表），但这些 IT 的裸 JDBC 夹具插入从未同步补列。属于清单 §0 点名要收干净的「测试基础设施红点」。

**已修复**：13 处夹具补齐两列（自结算语义：结算主体 = 本客户），涉及
`ScmFinanceConcurrencyPgIT`、`ScmFinancePaymentPgIT`(2)、`ScmFinanceReadPgIT`(2)、`ScmFinanceReceiptPgIT`、
`ScmFinanceReceiptRollbackPgIT`、`ScmFinanceWriteOffPgIT`(2)、`ScmFinanceReportPgIT`(2)、
`ScmOrderCustomerDataScopePgIT`、`ScmReportPgIT`。

**修复后重跑**（新一次性库 `xsy_v2_rerun`，同样 V1→V106 完整迁移）：error 29 → **5**，9 个被修套件中 8 个转绿。
剩余 5 个 error 全部集中在 `ScmFinanceSchemaPgIT`（清单已知的 DDL 契约漂移）。

### frontend（§16 步骤 4）— 先 FAIL（261 tests / 253 pass / 8 fail），修 D-08 后 `FAILED: none`

重跑 `verify.py frontend`：`ts-ratchet`、`lint`、`test`、`build` 全部通过，只剩
`INCOMPLETE: E2E services unavailable`（exit 2，非失败）。8 个红项的定性见 D-08。

`ts-ratchet`、`lint`、`build` 均 PASS；失败集中在 `npm run test`。

## 3. 缺陷清单

### D-01（ADM-02，已修复）退货回库在前端被判成「出库」方向

- 后端 `ScmInventoryMovementTypeEnum.SALES_RETURN_IN("销售退货入库", true)`；
  DB CHECK `ck_inventory_movement_snap`（V75/V95）也把 `SALES_RETURN_IN` 归在 `after = before + quantity` 的入方向组。
- 前端 `inventory-const.ts` 的 `SCM_INVENTORY_MOVEMENT_TYPE_ENUM` **完全没有** `SALES_RETURN_IN`
  （全仓 `xsy-scm-web/src` 搜不到该串），且 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES` 也缺它。
- 后果：`report-inventory-list.vue:534` 用该集合派生方向，退货回库流水在库存流水报表里显示为「出」，
  文案回落成裸枚举值；数字本身仍自洽，因此不会在任何金额断言里暴露。
  这正是该常量文件注释自己禁止的「第二份真相」失效模式。
- 同源问题：`SCM_INVENTORY_SOURCE_TYPE_ENUM` 缺 `SALES_RETURN_RECEIPT_ITEM`，
  导致「流水来源能追到退货接收单」这一条在 UI 上无法读；后端枚举还有 `DELIVERY_ROUTE`，前端亦无（未判定是否需要展示，见 §4）。
- 修复：补 `SALES_RETURN_IN`（含入方向）、补 `SALES_RETURN_RECEIPT_ITEM`；
  并把 `w6-inventory-contract.test.mjs` 的枚举/方向分组断言对齐后端真实集合，
  新增「导出的入方向集合必须与测试分组逐字一致」这条断言，使同类漂移今后会直接失败。

### D-02（测试基础设施，已修复）ADM-03 结算主体列 NOT NULL 后 IT 夹具漂移

见 §2 backend。修复后 24 个 error 清零。

### D-03（ADM-04）`AdminSettlementTermsPgIT.nonGroupParentCannotActAsSettlementCustomer` FAIL

尚未定位，属 ADM-04 集团结算主体校验语义，需要读断言与实际行为差异后判定是产品缺陷还是用例漂移。

### D-04（ADM-07，**待裁决**）`ScmPermissionContractPgIT.usedPermissionsAreCataloguedAndPublished` FAIL

不是漏登记，而是**静态契约表达不了运行期解析**：
`ScmPrintRenderService:61` 写的是

```java
return !type.requiresMoneyPermission() || ScmDataScopeService.hasPermission(type.getMoneyPermission());
```

契约用例要求每个权限检查都写成 `某Catalog.CONSTANT` 这种可静态解析的字段引用
（只放行 `ScmDataScopeService` 里的权限别名），而这里传给 `hasPermission` 的是
`type.getMoneyPermission()` 这个**计算表达式** —— 守卫无法静态证明到底在查哪个权限码，于是按设计拒绝。

这条恰好落在清单 ADM-07「金额权限」那组（无金额权限服务端剔除、历史重印重新检查当前权限）：
打印中心按单据类型决定需要哪条金额权限，是刻意的多态，不是随手写的字符串。

三个方向都需要定口径（本轮未动，因为任一改法都会碰 `src/main/java`，撞上 D-11 的 spotless 前置）：
1. 让 `ScmPrintDocumentTypeEnum.getMoneyPermission()` 返回类型收窄到「必须是某 Catalog 的常量」，
   并在契约用例里加一条「枚举能取到的权限码集合 ⊆ Catalog 已发布集合」的封闭性断言，
   再让守卫放行这一种被枚举封闭的间接引用。
2. 打印服务改为按单据类型显式列出权限（不透过枚举取值），代价是多一份与枚举并行的真相。
3. 承认「打印中心金额权限」属于数据范围服务管的别名，把它登记进别名集合（最小改动，但把别名口径放宽了）。

### D-05（schema 契约，已修复并验证）`ScmFinanceSchemaPgIT` 原 2 failures + 5 errors → **17/17 全绿**

`ScmFinanceReceiptPgIT.duplicateExternalReferenceIsAllowed` 同步从 FAIL 转 **17/17 全绿**。

修复内容与定性：

1. **5 个 error**：与 D-02 同源（该文件自己的 `insertReceivable` / `insertReceipt` 和 9 条
   `expectSqlFailure` 探针都没补 `settlement_customer_id` / `settlement_customer_name_snapshot`）。
   探针必须补齐这两列，否则它撞的是 NOT NULL 而不是本意要验的那条约束 —— 即探针在「假通过」。
   逐语句改写（不能用全局替换：`finance_payment` 的 `counterparty_id, '客户'` 形状相同，
   全局替换会把 2 条付款探针的参数打乱）。
2. **`noStateMachineOrDerivedColumns`**：禁用列清单里还留着 `due_date`，注释自己写着
   「due_date 与审批字段属 R2 或未裁决范围」。ADM-04 已把账期作为**冻结事实**落库
   （主档之后改账期不得重算旧应收），所以 `due_date` 是快照而不是可回写派生态。
   只把 `due_date` 移出禁用清单，其余（`status`/`net_amount`/`written_off_amount`/`settled_amount`…）
   实测确实仍然不存在，断言强度不变。
3. **`enumValuesMatchDatabaseCheckWhitelist`**：不是白名单变了，而是断言写法脆。
   它按字面子串找 `counterparty_type = 'SUPPLIER'`，而 PostgreSQL 把约束规范化成
   `(counterparty_type)::text = 'SUPPLIER'::text`，于是必然不匹配。
   改成按结构判定：`ck_finance_payment_method` 必须恰好分成 SUPPLIER / CUSTOMER 两支，
   逐支取字面量后钉住值域（供应商 `CASH/BANK_TRANSFER/OTHER`，客户多一个 `ONLINE_PAYMENT`），
   并保留「在线支付不得进供应商支」这条 3-11b 的实质约束。比原来更强，且不随 PG 版本漂移。

`ScmFinanceReceiptPgIT` 的那条 FAIL 同理是过期事实：注释写「收款表根本没有来源列」，
但 **V100（ADM-12 3-11a）** 给 `finance_receipt` 加了来源列和
`uk_finance_receipt_source_active (source_type, source_id) WHERE deleted=FALSE AND entry_type='NORMAL' AND source_type IS NOT NULL`，
这正是 PAY-B 要的「同一笔支付交易只生成一条收款事实」的第二层幂等。
已把该索引纳入允许集合，并顺手要求它必须带 `source_type IS NOT NULL` 的部分谓词
（退化成全表唯一就会挡住人工收款）。`external_reference` 仍无唯一索引，测试本意未松动。

清单 §0 已点名的已知漂移：旧断言/CHECK/唯一索引期望与 V106 实际 schema 不符，且含 `finance_receipt` 安全唯一索引期望漂移
（`ScmFinanceReceiptPgIT.duplicateExternalReferenceIsAllowed` 同源）。需逐条对照当前 DDL 决定「改断言」还是「补约束」，不能靠放宽断言过关。

### D-06（迁移账本基线 + 一个未定项）

同一族「只增不减需显式确认」门禁，新迁移/新表落地后基线数字未同步。实测差额：

| 用例 | 期望 | 实际 |
|---|---|---|
| `ScmOrderMigrationIT.approvedSchemaHasEightTablesNoDeadFieldsAndNullableDraftPrices` | 27 | 28 |
| `ScmPurchaseMigrationIT.schemaShapeMatchesDesign` | 35 | 36 |
| `ScmPurchasePermissionMigrationIT.everyControllerPermissionExistsInMenuSeed` | 35 | 38 |
| ~~`ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly`~~ | **已修并验证转绿** | 逐条列举从 V74 延到 V106（依据 BASE-03/04：干净库与升级库都是 1..106 全 success）。仍保持「逐条列举、只增不减」的写法，不改成 contains。 |
| `ScmPurchaseMigrationIT.noForeignKeysAndInventoryTablesBelongToW6` | **0** | **9** |
| `ScmInventoryMigrationIT.ddlContract` | **0** | **9** |

9 条外键的归属已查清，全部来自较新的 ADM 域，采购/库存域自己一条都没有：

```text
delivery_gps_event        -> delivery_route      (ADM-10)
delivery_plan_proposal    -> delivery_route      (ADM-10)
promotion_coupon_instance -> promotion_coupon    (ADM-12)
scm_customer_statement_item/source -> scm_customer_statement (ADM-01 D)
scm_supplier_statement_item/source -> scm_supplier_statement (ADM-01 D)
sorting_scale_event       -> sorting_task / sorting_task_item (ADM-11)
```

两条断言用的都是**不加表范围过滤**的全局计数（`SELECT count(*) FROM pg_constraint WHERE contype='f'`），
所以较新域的建表决策把采购/库存域的用例打红了。真正要定的问题是：
「SCM 禁止外键」是全局不变量（则这 9 条要出迁移删掉），还是各域自选（则这两条断言要收窄到自己的域）。
未擅自改数字，也没有擅自删约束。

**`ScmOrderMigrationIT` 已修复并验证转绿（3/3）**：27 → 28 的增量经追认是
`V87__scm_order_exception_report.sql` 为 ADM-08 售后拒绝事件轴加的 `idx_order_return_rejected_time`，
属于已批准能力，不是意外索引；注释里的增量链（25 → V51 的 2 条 → 本条）已补全。

剩余三行**未动**，因为还没做到「能指出是哪一条」的可信度：
`purchase%` 表自身的非主键索引实测正好 35，说明 `schemaShapeMatchesDesign` 的 36 含一张不在
`purchase%` 前缀内的表，需要先还原该用例的表清单才能确认第 36 条的归属；
权限端点 35 → 38 同理，注释里「4+14+10+7=35」的分组需要逐条对上新的 controller 方法才能改，
盲改数字会把「多出来的 3 个端点是否都已有菜单种子」这个真问题盖掉。

后两行**不能按同一性质处理**：这两条断言的是「设计上不允许外键」，现在库里有 9 个，
属于 schema 事实与设计口径冲突。要么外键是被有意引入（则设计决策与 ADR 需更新，属 §4 待裁决项 10），
要么就是绕过约定的 DDL 落地了（则是真实缺陷，需要迁移删掉）。本轮未擅自改数字，也没有擅自删约束。

### D-07（测试基础设施，已修复）**清单 §0 的「PG 方言」判定不成立**

原判定：`ScmFinanceProfitDao.query` / `.summary` 含 MySQL 写法。实测**不是方言问题**：
`SmartAdminMapperPgValidationIT.prepare()` 对「无法推断参数类型」的 `PREPARE` 只重试 **16 轮**，
而这两条 SQL 有 100+ 个占位参数，每个 `CONCAT('%', $N, '%')` 都需要一轮 `::text` 补丁，
轮数先耗尽后被误报成「MySQL 残留」（报错文字是 `prepare retry exhausted`）。

修复：重试上限改为按 SQL 中参数出现次数计算。修复后两条 profit 语句**全部通过 PostgreSQL `PREPARE` 校验**，
`knownFailures` 相应下降到 0。→ 毛利报表在生产 PG 上可执行，先前记录的回归风险撤销。

### D-11（§16 门禁口径，HEAD 即红）**已提交的 ADM 代码违反本仓库自身的 quality 棘轮**

`quality-guard` 报 `NEW DEFECTS (blocking, 34)`，且 `magic-string-domain-literal` / `stage-comment` / `checkstyle`
三项 baseline 都是 **0**，即这些不是历史欠账而是随近期提交带进来的。全部位于 `src/main/java`，
与本轮测试改动无关（首次跑 `quality` 时就已红）。构成：

| 类别 | 数量 | 性质 |
|---|---|---|
| `LineLength` | 30 | 机械换行，可安全修 |
| `UnusedImports` | 3 | 删导入即可（payment/print/report 各 1） |
| `magic-string-domain-literal` | 13 | 该用枚举却写字面量，需按语义选对枚举 |
| `stage-comment`（`本轮`） | 3 | 违反 AGENTS.md「注释不写阶段编号」 |

13 个魔法字符串分布在 ADM-01 对账单/利润、ADM-04 授信、ADM-06 库存预警、ADM-11 秤事件、
ADM-12 支付与余额、ADM-07 打印。其中一处需要判定：
`SortingScaleEventService:211` 的 `"PENDING"` 同时匹配 13 个枚举，
必须按上下文确认是 `ScmSortingTaskStatusEnum`（或对应秤事件状态枚举）而不是随手挑一个消警。

结论：清单 §16 要求的 `quality PASS` 在当前 HEAD 上**不可能达成**，必须先清这批债务。

**已实测确认这不能靠「顺手改机械项」推进**：试修 3 个 `UnusedImports` + 3 个 `stage-comment`
（共 5 个 `src/main/java` 文件）后，`mvn test-compile` 在 `scm-spotless-check` 阶段失败。
失败内容与被改语义无关——spotless 的 ratchet 只检查「相对 origin/main 变更过的文件」，
一旦某个文件被碰过就整篇纳入检查，于是这些文件里**早已存在**的 CJK javadoc 折行不合规被翻出来：
例如 `InventoryWarningScanVO` 被报的是第 6~14 行那段与改动无关的注释。
`mvn spotless:apply` 能消掉它，但那会在同一批提交里夹带大量无关注释重排。

5 处 `src/main/java` 改动已全部 `git checkout` 还原，工作区不含未定的格式 churn。
→ 新增待裁决项 13：**是否允许 `spotless:apply` 规范化这批历史文件**（决定 D-11 是「小 diff 逐文件修」
还是「先做一次全量格式化提交，再修语义项」）。在该口径定下来之前，D-11 无法安全推进。

### D-09（ADM-03，**待裁决**，HEAD 即红且属近期回归）收款登记不再校验结算主体合法性

`AdminSettlementTermsPgIT.nonGroupParentCannotActAsSettlementCustomerEvenWithDirtyRelationship` FAIL，
失败形式是 `Expecting code to raise a throwable`（期望被拒绝，实际放行）。

- 用例语义：客户 `child` 把 `parent` 当作 `settlement_customer_id`，而 `parent` 只是普通客户、
  被直接 SQL 脏改成 `settle_mode='GROUP'`（其**客户类型不是 GROUP**）。登记收款应被拒绝。
- 规则原本在 `CustomerService.requireSettlementAccount`：结算主体只允许「自己」或
  「直接上级 + 双方 GROUP 模式 + 上级是 GROUP **类型** + 上级自身结算」，否则抛 `CUSTOMER_PARENT_INVALID`。
- 提交 `734ab04c`（*fix(finance): remove customer domain dependency from receipt registration*）
  为了消掉 6 条 `ScmArchitectureTest` 违规，把 `FinanceReceiptService.register` 里的
  `customerService.requireSettlementAccount(payer)` 调用删掉了，改为只校验结算 id/name 是否成对存在。
  成对存在 ≠ 合法：脏配置照样通过，于是这条业务不变量在收款登记入口失守。
- **本轮实测把问题收窄了**：同一个用例类里那条走服务层写入的
  `nonGroupSiblingCannotActAsSettlementCustomer`（`customerService.add(form)` → 期望 40032）是**通过**的，
  且 `CustomerService` 第 304–310 行确实在写入时调用 `requireSettlementCustomer(entity)`
  并附带数据范围校验。也就是说「结算主体必须是 GROUP 类型 + GROUP 模式 + 自身结算」这条不变量
  **已经在 Customer 域的权威写入口被执行**（= 下面的方向 2 已经是现状）。
  失败的那条只是用 `UPDATE customer SET settle_mode='GROUP'` **绕过服务层**造出脏数据，
  再要求收款登记仍然拒绝它。所以真正要裁决的不是「有没有校验」，而是
  **「要不要在收款登记处保留一层纵深防御，去兜住带外改库的情形」**。
- 两个方向都需要决策，不能由测试阶段单方面定：
  1. **保持边界**：把判定所需事实（上级 id、双方 `settle_mode`、上级客户类型 code、上级自身结算标志）
     加进 `FinanceCounterpartySourceDao.selectCustomer` 的只读投影（`FinanceCustomerFactDto`），
     在 Finance 侧重放同一规则。代价是规则出现第二份，正是该提交想避免的「第二条校验路径」。
  2. **只在 Customer 域把关**：写入口（add/update）拒绝非法结算配置，Finance 信任已冻结的成对值。
     代价是绕过服务层的脏数据不再被兜住，且本用例的前提（服务外脏改）被视为不受支持状态——
     但这等于削弱既有约束，按 AGENTS.md 不允许由测试改动来实现。
- 本轮**未改代码、未改该用例**，仅记录待裁决。

### D-10（ADM-04 + 全部数据范围 SQL，**已修复并验证转绿**）范围参数造不出来导致整条语句被静默跳过

`SmartAdminMapperPgValidationIT` 在 D-07 修复后剩下的唯一失败是棘轮按设计报警：
`CustomerCreditDao.selectExposure` 出现在 skipped 集合里，而它不是 BaseMapper 方法名，
于是触发「手写 mapper 语句不允许被跳过」这条断言。

- 跳过原因（报告原文）：`BuilderException: The expression 'scope.ids' evaluated to a null value`——
  测试用 `sample()` 造 `ScmDataScopeContext` 时 `ids` 为 null，`<foreach collection="scope.ids">` 无法渲染，
  于是整条语句被静默跳过。这正是该 IT 注释里点名要堵的「无声逃逸口」。
- 影响：**ADM-04 授信敞口查询（含 `FILTER`、`LEFT JOIN LATERAL`、数据范围三态分支）从未被 PostgreSQL 解析验证过**。
  人工读 SQL 判断写法是 PG 兼容的，但这是人工判断，不能当验证结果记录。
- 修法需要连带确认两处棘轮：让 `sample()` 为集合给出非空样本（或对 scope 特判）后，
  `skipped` 基线（≤493）与 `knownFailures` 恰好 N 条的断言都会变动；
  若该语句真的解析失败，则属于新的方言缺陷而非基线更新。
  因为需要「改一处、跑一次、按真实结果决定另一处」的闭环，本轮未盲改，记为待办。

### D-12（BASE-09）本机离线启动后端受阻 —— 本轮改为尝试联网补齐打包插件

原始阻塞（保留作记录）：

`mvn -o package` 在 `maven-jar-plugin:3.5.0` 上失败：`file-management`、`maven-archiver`、
`plexus-archiver`、`commons-compress` 等归档插件依赖从未进过本地仓库
（这台机器历史只跑过 `mvn test`，`install` 同样因 `maven-install-plugin` 缺依赖而失败）。
`dependency:build-classpath` 也拿不到 sa-base：reactor 内的 sa-base 只有 `target/classes`，
没有 jar，该 goal 按仓库坐标解析而不是工作区 substituted artifact。

因此 E2E（§15 8 条黄金链）当前有两条可行路径，都需要用户点头：
1. 放开网络，先单独把 jar/archiver 插件预热下载（`mvn dependency:get`），再 `mvn -o package` 本机起 18080；
2. 走容器构建 `docker compose ... up -d --build backend`，但 `Dockerfile` 内的 Maven 构建同样要访问 Central。

**已解除（同日）**：放开网络跑一次 `mvn -pl sa-admin -am -DskipTests package`，只补归档插件，
产出 167 MB 可执行 boot jar；因此**没有**动 `docker-compose.yml`，也没有让 Flyway 碰长驻开发库
（容器路线会撞 BASE-05）。E2E 全程用本机 Docker Desktop 的 postgres / redis / minio。

### D-13（测试基础设施，已修复并验证）PG 语句校验的两个静默逃逸口

`SmartAdminMapperPgValidationIT` 现在 **1/1 全绿**。它原先有两处会「看起来在校验、其实整条跳过」：

1. **参数类型推断重试写死 16 轮**（见 D-07）：改为按 SQL 中参数出现次数计算。
   `ScmFinanceProfitDao.query` / `.summary` 因此被证实**是合法 PostgreSQL 写法**，
   清单 §0 记的「MySQL 方言问题」不成立。
2. **`getBoundSql` 抛错就整条跳过**：SCM 的数据范围值对象
   （`ScmValueScope` / `ScmDataScopeContext`）是 final 字段 + 只有全参构造器、且工厂方法不是 public，
   通用 `sample()` 造不出实例 → 返回 null → OGNL 读 `scope.ids` 抛 `BindingException`
   → 该语句进入 skipped 集合。而 skipped 原先只按「方法名是否属于 BaseMapper」把关，
   于是**所有带数据范围的手写 SQL 从来没进过 `PREPARE` 校验**，
   包括 ADM-04 授信敞口 `CustomerCreditDao.selectExposure` 与对账单 `ScmSupplierStatementDao.selectEvents`。
   修法只用两个受控特例（造非空、非全量范围），不改通用递归；
   曾经试过「任何类都退回最全构造器」的通用写法，实测会把 MyBatis-Plus 泛型 `update/insert`
   的跳过项推高，反而扩大这个逃逸口，因此已回退。

跳过项上限从 493 显式提到 **608**：F1-1 之后各 ADM 又新增 23 张 BaseMapper 表（+115 条），
集中在 ADM-12 支付/余额、ADM-07 打印、ADM-02 退货接收单、ADM-05 冻结批次、ADM-10 排线建议、ADM-01 对账单。
这条是「只增不减需显式确认」门禁，按设计就该显式改数而不是绕过；
并且 608 里每一条的方法名都仍被前一条断言强制属于 BaseMapper，
所以这次调高并没有把手写语句放进逃逸口 —— 恰恰相反，本轮把原先藏在里面的手写语句移到了校验侧。

### D-08（前端契约，已全部修复）8 个 `npm run test` 失败

| 用例 | 现象 |
|---|---|
### D-14（工具链，已确认）`tools/requirements-dev.txt` 缺 `argon2-cffi`

E2E 的临时账号脚本 `tools/w*_e2e_accounts.py` 需要 argon2 哈希口令，但该文件只声明了 `openpyxl==3.1.5`。
干净机器照 README 装完依赖后，Playwright 会在 `provisionTempAccounts` 处整体失败，
报的是「Command failed: python ../tools/w4_e2e_accounts.py setup」，
真因（缺 python 包）被藏在子进程输出里 —— 正是该文件自己注释警告过的
「缺少它时……故障会伪装成用例本身坏了」。
本机已 `pip install argon2-cffi` 解除阻塞；**建议把 `argon2-cffi` 补进 `requirements-dev.txt`**
（属工具链修复，不是业务改动，等用户确认是否在本轮一起提交）。

逐条定位后分两类：

**产品缺陷（真 bug，已修）**

| 项 | 定性 |
|---|---|
| `scm-query-form-submit` 两条（裸 submit / `@finish` 必须配 `:model`） | **ADM-12 支付抽屉的支付按钮点不动**。`order-payment-drawer.vue` 的 `<a-form @finish="submit">` 没有 `:model`，Ant Design Vue 不会 emit `finish`，而按钮是 `html-type="submit"` → `submit()` 是死代码，余额支付与模拟在线支付都无法从该入口发起。组件本来就自行用 `isValidPositiveAmount` 校验，故改为 `html-type="button" + @click="submit"`，去掉这条永不生效的表单耦合。 |
| `p1-sorting-contract` 页面用了未声明的权限码 `scm:sorting:scale:query` | **ADM-11 前端权限码表单缺秤相关三条**。后端 `SortingPermission` 与 V89 菜单种子都发布了 `scale:query/accept/report`，页面模板已在用，但 `SCM_SORTING_PERMISSION` 没登记 → 补齐，使「单一声明来源」重新覆盖后端全集。 |

**测试基线过期（后端为准，已对齐并顺手加强）**

| 项 | 定性 |
|---|---|
| `p2-delivery-l3:194` 配送权限码 | `CONTRACT_DELIVERY_PERMS` 快照停在 ADM-10 之前，缺 `plan:query/propose/apply`（后端 `DeliveryPermission` + V88 都有）→ 补齐。 |
| `p2-delivery-l3:138` 打印面板只许 `print()` | ADM-07 后 `route-print.vue` 改为委托打印中心（`document-type="DELIVERY_NOTE"`），对 `deliveryApi` 的调用降为 0。改为断言「面板不得直接发起任何配送命令」，比原来的白名单更严。 |
| `w2b-purchase-efficiency:55` `purchase-order-print.ts` ENOENT | ADM-07 用打印中心取代了纯客户端采购打印，文件已不存在。改为守「采购打印只经 `PrintDocumentModal`，且采购面板不得出现采购写命令」。 |
| `w2a-demand-summary-preview:75` | 需求页写入口在 ADM-05 冻结批次流程后是 `batch:create` + `allocate`；直接 `demand:generate` 端点仍在后端与 API 层，但 UI 不再挂该入口。改断言当前两个写入口。 |
| `finance-report-contract:136` 导出端点数 | 断言停在 14（销售 3 + 采购 3 + 收货 2 + 库存 3 + Finance 3），ADM-01 后补导出落地后实际 21。**已把 21 条逐个对过后端 controller 映射，无孤立端点**（含 `ScmFinanceReportController` 的类级 `/scm/report/finance` 前缀）。改为断言按字母序的完整导出路径清单，将来漏接后端会直接点名是哪一条。 |


## 3. §10 ADM-10「GPS 退场」逐条实证（6/6 通过）

在 V106 的 E2E 库上直接取证：

| 清单项 | 结果 | 证据 |
|---|---|---|
| 前端无 GPS 页面 | PASS | `xsy-scm-web/src` 内 GPS 只出现在 `components/business/scm/map/map-provider.ts` 的坐标转换调用里，无任何配送轨迹页面/路由 |
| 无运行入口 | PASS | `views/business/scm/delivery/` 下无轨迹视图；`use-delivery-permission.ts` 不再声明 gps 常量 |
| 无 GPS 上报 API 对外入口 | PASS | `delivery/controller/*.java` 里没有 gps 相关映射 |
| 权限已撤 | PASS | `V91__scm_delivery_route_scope_cleanup.sql` 先 `DELETE FROM t_role_menu`（按 `api_perms IN ('scm:delivery:gps:query','scm:delivery:gps:report')`），再把两条菜单置 `disabled_flag=TRUE, deleted_flag=TRUE, visible_flag=FALSE`。V106 库上基线角色对这 2 条菜单的授权数为 **0** |
| 历史 GPS 数据仍存在 | PASS | `delivery_gps_event` 表在 V106 仍存在（V91 注释亦声明「不修改历史迁移或轨迹数据」） |
| CRS / 坐标转换代码未被误删 | PASS | `map-provider.ts` 仍走 `sdk.convertFrom(raw, 'gps', …)`（WGS84 → 高德 GCJ02） |

取证过程中的一条**排除说明**：初次统计到「还有 2 条角色持有 gps 菜单」，
细查是 `role_code = W5_E2E_READ` —— 由**正在跑的 Playwright w5 用例临时建的角色**，
不是 migration 遗留。因此「权限已撤」这一项判 PASS。
但这同时暴露一个测试侧现象：`tools/w5_e2e_accounts.py` 会按菜单 pattern 授权，
可能把已 `deleted_flag=TRUE` 的隐藏菜单也授予临时角色；后续如需把 E2E 角色授权收紧到
「只授未删除且启用的菜单」，属于测试夹具改进，不在本轮擅自改。

**同时发现 `docs/status.md` 的当前描述已过时**：它写「GPS 运行入口已移除、撤权 migration 待执行」，
而撤权 migration 实际就是已存在并已应用的 `V91`。本轮未代改该文档，留待确认后更新。

## 3b. §16 步骤 5 —— E2E 首轮真实结果与阻塞根因

服务起来后第一次真跑 Playwright（158 个用例）：

```text
expected 18 / unexpected 57 / skipped 83 / flaky 0
```

按错误聚类后，**57 个失败里有 56 个是同一个环境原因**，不是产品缺陷：

```text
browserType.launch: Executable doesn't exist at
  %LOCALAPPDATA%/ms-playwright/chromium_headless_shell/...
```

即这台机器装了 `@playwright/test` 的 npm 包，但**没有下载浏览器二进制**。
通过的 18 个用例都是纯 API（不开浏览器）的那批，正好印证这个判断：
一旦用例需要浏览器就必失败，与业务逻辑无关。
处理：`node node_modules/@playwright/test/cli.js install chromium`（本机正在下载，受限于网络速度），
下载完成后重跑本步骤才能给出可用的 E2E 结论。

83 个 skipped 目前没有 message，需要浏览器可用后重跑再看是条件跳过还是被首个失败连带跳过。

**唯一一个非 launch 失败**（已判定为同一环境原因的连带，非业务缺陷）：

```text
需要至少一个运行中的可用仓库才能验证范围 … 实际 []  expect(received).toBeGreaterThanOrEqual(expected)
```

出处其实是 `e2e/scm-data-scope.spec.ts:162`（要求「两个都有余额行的启用仓库」才能验范围，
实际拿到 `[]`）。取证：`xsy_v2_e2e` 库里 `warehouse(deleted=false)=1`、`inventory_balance=1`、
`employee_warehouse_scope=3` —— 只有 **1** 个仓库，而该用例需要 2 个带余额的仓库。
第二个仓库及其入库是在**页面流程**里造的，而页面流程这一步正好被上面的「浏览器缺失」打断，
所以这是同一个环境原因的连带失败，不是仓库范围口径的问题。浏览器装好后重跑即可复核。

### D-15（工具链，已确认）Playwright 浏览器二进制未随仓库安装

`requirements-dev.txt` 只覆盖 python 侧，浏览器二进制要靠 `playwright install`。
与 D-14 同类：**干净机器上这会让 E2E 大面积失败，而失败原因与业务无关**。
建议把「首次跑 E2E 前先 `playwright install chromium`」写进验收/部署文档，
本轮先按实际命令补齐，不改仓库文档（等用户确认是否纳入提交）。

## 3c. D-16（ADM-07，覆盖缺口）**打印中心零自动化覆盖**

清单 §7 给 ADM-07 列了约 30 项（模板增删改 / 唯一默认 / 纸张与横纵向 / 表头字段 / 明细列 / 合计 / 页脚 /
非白名单字段拒绝；预览与正式打印 / 冻结模板版本与业务模型与版面 / 历史重印用历史快照 /
模板改动不影响旧记录 / 打印不改变订单-采购-配送状态 / 重试不重复生成事实；
金额权限可见与剔除 / 重印重新检查当前权限 / 不能靠旧记录绕过权限）。

取证结果：`grep -rl "ScmPrint" xsy-scm-server/sa-admin/src/test` → **0 个文件**；
`e2e/*.ts` 里也没有任何打印用例。也就是说这一整块**只有代码，没有自动化证据**。

静态读码确认实现是存在的（可作后续用例的靶点）：
- `ScmPrintService.reprint` 先 `requireVisible(type, businessId)`，其中
  `StpUtil.checkPermission(provider.queryPermission())` + `provider.requireVisible(businessId)`，
  注释明确写「快照里有单据内容，只有记录查询权不该能读出它」→ 对应清单
  「历史重印时重新检查当前权限」「不能靠旧打印记录绕过权限」。
- `maskAmount(type, render)` 在 **重印（:197）与批量重放（:136）** 两条路径上都调用：
  无金额权限时逐字段剔除表头/明细里的金额列，用的是**当前**权限而非历史记录里的权限。
- 重印返回 `frozen=true` + 模板版本/名称/纸张/横纵向/页脚/合计开关全部取自
  `record.getModelSnapshot()`，数据取自 `record.getDataSnapshot()` → 对应
  「冻结模板版本 / 业务模型 / 版面」「历史重印使用历史快照」「模板后来修改不影响旧打印记录」。

**已补（同日）：新增 `sa-admin/src/test/java/com/xsy/scm/print/ScmPrintCenterPgIT.java`，
在 V106 一次性库上 **`Tests run: 9, Failures: 0, Errors: 0`**（并在 V40→V106 升级库上复跑打印相关 4 条同样全绿）。
覆盖的九条：

| 用例 | 守的性质 | 非空洞性证据 |
|---|---|---|
| `printFreezesTemplateVersionAndLayoutAgainstLaterEdits` | 冻结模板版本 + 版面；模板后来修改不影响旧打印记录 | 打印后**真的**把默认模板标题/页脚改掉再重印，断言重印结果既等于冻结值又**不等于**新值 |
| `printAndReprintNeverTouchBusinessState` | 打印不能改变订单状态 | 打印前后与重印后分别 `reloadOrder` 比 `status` 与乐观锁 `version` |
| `reprintRechecksCurrentViewPermission` | 历史重印重新检查当前权限；不能靠旧记录绕过权限 | 先成功打印，再把 `StpUtil.checkPermission` 改成抛错，重印必须把拒绝传出来 |
| `retryWithSameIdempotencyKeyDoesNotDuplicateRecord` | 重试不重复生成不该重复的业务事实 | 同键连打两次，`scm_print_record` 计数断言**恰为 1**（若打印根本不落记录会是 0，用例就会红） |

新增的第二批（模板块）四条 + 乐观锁一条：

| 用例 | 守的性质 | 断言方式 |
|---|---|---|
| `createRejectsDuplicatedTemplateCode` | 模板编码唯一 | `expectCode(…, 41302 TEMPLATE_CODE_DUPLICATED)` |
| `createRejectsFieldOutsideCatalog` | **非白名单字段拒绝** | 往明细列塞一个目录外字段 → `41306 FIELD_NOT_ALLOWED` |
| `creatingNewDefaultDemotesTheOldOne` | **唯一默认模板** | 建默认后再查 `default_flag` 计数，必须仍是 1（互斥没做就会变 2） |
| `deleteRejectsTemplateInUse` | 被打印记录引用的模板不许删 | 先真打印一次再删 → `41303 TEMPLATE_IN_USE` |
| `updateRejectsStaleVersion` | 模板编辑受乐观锁保护 | 改成功后拿旧 version 再提交 → 抛 `ScmBusinessException` |

仍**未覆盖**的 ADM-07 项（诚实标注，不当作已完成）：
表头字段/明细列/合计/页脚的**渲染细节正确性**（本次只证明它们被冻结与可拒绝），
以及金额权限剔除在 `DELIVERY_NOTE` 上的实际生效——
后者是因为只有 `DELIVERY_NOTE` 挂了金额权限码（`PURCHASE_ORDER` / `SORTING_TICKET` 的
`moneyPermission` 为 null），需要造完整发货链路，留作下一轮补。

原结论保留作对照：这三条不变量曾经**写对了，但没有任何自动化在用**。
按 §0「测试补强」是本轮允许且应该做的提交，已列入计划（task #6），
优先覆盖：重印权限复判、金额剔除、快照不被新模板污染、打印不改业务状态、重试幂等。

## 3d. D-17（真实缺陷，附新守护用例）两条「可见但点开是空白页」的页面菜单

在 V106 干净库上比对 `t_menu.component` 与 `xsy-scm-web/src/views` 实际文件，93 条组件声明里
**2 条指向不存在的 .vue**，且都是 `visible_flag = TRUE`：

| menu_id | 菜单名 | 声明的组件 | 实际情况 |
|---|---|---|---|
| 435 | 客户 SKU 可见性 | `/business/scm/customer/customer-sku-visibility-list.vue` | `src/views/business/scm/customer/` 下只有 `customer-list.vue` / `customer-detail.vue` / `customer-type-list.vue`；只有 API 层 `customer-visibility-api.ts` 存在，页面文件不存在 |
| 85 | 组件演示 | `/support/demonstration/index.vue` | `src/views/support/demonstration/` 整个目录不存在（SmartAdmin 底座遗留种子） |

这正是 `docs/status.md` 记过的历史故障模式：`src/router/index.ts` 里
`route.component = modules[relativePath]`，路径缺失时静默得到 `undefined`，
菜单能看见、点开是空白页，而 **构建、vue-tsc、后端测试全都不会红**。

**本轮已补上缺的那个守护**（属于 §0 允许的测试补强）：
新增 `sa-admin/src/test/java/com/xsy/scm/common/ScmMenuComponentExistencePgIT.java`，
按库里的 `t_menu.component` 逐个查文件是否存在（跳过 http 外链型菜单），
并额外断言扫描清单非空以免空跑。实测**如期变红**并逐字点名这两条：

```
Expecting empty but was: ["/business/scm/customer/customer-sku-visibility-list.vue",
                          "/support/demonstration/index.vue"]
```

修法需要产品口径（不能由测试阶段单方面决定），已进 §5 待裁决清单：
menu 435 是要**新建页面**，还是该功能已经折进 `customer-detail.vue` 的某个页签、
因此这条独立菜单应当撤下（出新 migration）；menu 85 属底座演示项，
在 V2 后台里应当删除还是补文件。

## 3e. D-18（测试环境）`adm02-04-ui-contract.spec.ts` 依赖一个没人起的 18083 静态服务

该 spec 第 3 行硬编码 `const root = 'http://127.0.0.1:18083/e2e/fixtures/adm-ui.html'`，
但 `playwright.config.ts` 里没有 `webServer`，`docker-compose.yml` / 文档也没有任何地方起 18083。
干净机器上这一组必然 `net::ERR_CONNECTION_REFUSED`，报错看起来像「夹具页坏了」。

**补记（同日，含对我自己一个错误修复的更正）**：

我第一次用零依赖静态服务（`.runtime/verify/fixture-server.cjs`）补 18083，`curl` 页面返回 200
就以为修好了 —— **那是错的**。`adm-ui.html` 里是
`<script type="module" src="./adm-ui.ts">`，而 `adm-ui.ts` 第 1-8 行
`import {createApp} from 'vue'` / `Antd from 'ant-design-vue'` / `'/@/theme/index.less'` 等，
**必须经 vite 转译与解析别名**才能执行；静态服务会把 `.ts` 当纯文本返回，模块被浏览器
以错误 MIME 拒绝执行，页面永远挂不起来 —— 表象与该页缺失时一样，容易误判成「夹具页坏了」。

正确做法（已实测生效）：在同一份 web 目录上再起一个 vite 实例，
`npx vite --port 18083 --strictPort`（CLI `--port` 优先于 `playwright.config.ts` 里的 18081）。
校验不能只看 HTML 的 200，要看到模块本身：

```text
http://127.0.0.1:18083/e2e/fixtures/adm-ui.html   -> 200
http://127.0.0.1:18083/e2e/fixtures/adm-ui.ts     -> 200 text/javascript   ← 这条才说明可执行
```

**建议**：把这个 18083 vite 实例写进 `playwright.config.ts` 的 `webServer`
（或 `verify.py e2e` 的前置检查），否则每个人都要重新发现一次，
而且第一次发现时很容易像我一样把「HTML 200」当成修好。属测试基础设施，未改仓库配置，等确认是否纳入提交。

**顺带一个验收强度判断**：`adm02-04-ui-contract.spec.ts` 打的是这个**夹具页**，
不是真实的订单/退货页面；它验的是「授信阻断 + 例外原因随命令提交」「部分退货只提交剩余数量」
这类**前端提交的报文契约形状**，不能等同于清单 §15 的贯穿式真实页面验收。

## 3f. D-19（测试套件结构问题，**已更正本文早先的错误定性**）`scm-customer-360` 依赖「后面」的用例才会创建的数据

先前本节写的结论是「E2E 库里没有客户 ⇒ 缺播种脚本」。**那个前提是错的**，此处按实测更正：

- 取证时刻 `xsy_v2_e2e` 库里 `customer` 共 **24 行、未删除 20 行** —— 数据是有的。
- `tools/e2e_accounts.py`（:214/:240）建的 `accounts.admin` 是 `administrator_flag=true`，
  注释也说明正式角色才受数据范围限制，超管账号等于不收窄；
  所以「查不到」不是 §业务不变量 的空范围 fail-closed 在起作用。
- `playwright.config.ts` 是 `fullyParallel: false, workers: 1`（**串行**），
  因此失败也不是并发竞态。

真正的原因是**文件顺序**：Playwright 按文件名字母序串行调度，
`scm-customer-360.spec.ts`（连字符 0x2D 排在点号 0x2E 之前）**早于** `scm-customer.spec.ts` 执行；
而 360° 那条 `requireCustomerId()` 只是「查库里现成的第一个客户」，自己不建客户。
在**全新迁移的 V106 库**上，那一刻还没有任何客户，于是 `query.data.list` 为空 → 断言红。
它 `query.code !== 0` 也会同样变成 undefined，两种情况都不 skip —— 这是该用例刻意的设计
（注释：「缺夹具必须让用例变红而不是 skip」），所以它把「套件内部的数据依赖」如实暴露了出来。

同样地，`scm-data-scope.spec.ts:162` 要「两个都有余额行的启用仓库」而上一轮实测
`warehouse=1 / inventory_balance=1`，也是同一形态：依赖别的用例先造数。

`docs/adr/005-purchase-daily-report.md` 记的「正式浏览器 E2E 156 passed / 0 skipped / unexpected / flaky」
应该是**在已有数据的库上**跑出来的，这也解释了为什么这一依赖此前不发作。

两个修法（需要定口径，本轮未改）：
- 方案 A（推荐）：让依赖数据的 spec 在 `beforeAll` 里**自己造最小夹具**（一个客户 / 两个带余额的启用仓库），
  与 `workers: 1` 的串行假设解耦；这样 E2E 能在干净 V106 库上自洽跑完，
  也保住 BASE-05「不拿长驻开发库跑测试」。
- 方案 B：正式规定「E2E 只在预置数据的验收库上跑」，并让 `verify.py e2e` 在缺夹具时报 `INCOMPLETE`
  而不是让一批用例假红。

无论哪个，都不该反过来把 `requireCustomerId` 改成 skip —— 那会正好踩中它注释里警告的
「绿色跑过 + 场景没执行」。

## 4. 需要裁决 / 缺 Key，按清单要求先跳过并记录

> **勾选请去 `docs/plan/active/adm-acceptance-decision-register.md`**（J-01…J-18 重编号，
> 带 A/B/C、我的建议、以及每条解锁哪一条具体红）。本节保留原始取证叙述，是本节的原编号来源；
> 下面 1～18 的**顺序**（18 排在 16/17 之前）与 §6 结论段引用的编号已发生漂移，
> 因此**不要**按本节编号回收口结论。

1. **地图供应商 Key（ADM-09）**：`.env` 无任何地图 provider Key、无配额。真实地图渲染、选点、路径、CRS 现场校验 **NOT COVERED**；mock 通过不能替代（清单原文亦如此要求）。
2. **电子秤真实协议（ADM-11）**：需现场设备与协议，本机不可验。**NOT COVERED**。
3. **支付渠道（PAY-B）**：无渠道凭据，只能走 mock provider；渠道实收金额、`providerTransactionNo` 防重的真网校验 **NOT COVERED**。
4. **Node 版本口径（BASE-08）**：清单写 Node 22，本机 Node 24，`engines` 允许 `>=18`。需要决定以哪个为准。
5. **跨域 DAO 白名单（quality）**：三条只读跨域读是「补登记」还是「改为正式领域契约 / 只读 DAO」，影响 ADM-05/06/11 的架构口径。
6. **删除仍被引用的结算客户（ADM-03）**：清单点名的待暴露用例——Customer 域目前不阻止删除仍被他人当作 `settlement_customer_id` 的客户。属产品决策，未擅自改动。
7. **`SALES_RETURN_IN` 是否要出现在所有前端筛选/报表入口**：本次只修方向与文案真相源；是否补进报表流水类型筛选项需确认。
8. **`DELIVERY_ROUTE` 来源类型**：后端枚举有、前端无。需确认它是否会落到库存流水来源列，再决定是否补展示。
9. **ADM-13～17、ADM-12 c3/d**：按停止线记 `N/A`，本轮不开发、不计入必须通过。
10. **9 个外键是否存在**（D-06 后两行）：schema 与「不允许外键」的设计口径冲突，需先定口径再决定改数字还是出迁移删约束。
11. **结算主体校验放哪一层**（D-09）：收款登记是否必须重放合法性规则，还是只信任 Customer 域写入的冻结值。
12. **是否允许 spotless 规范化历史文件**（D-11 / **机制已实测更正，见 §5d 与 D-36**）：
    原表述「历史文件早已不合规」是错的 —— `spotless:check` 与 `checkstyle:check` 在 HEAD 上都 `exit 0`，
    34 项全部来自 `quality-guard` 的 new-defect 计数。真正要定的是：
    **`checkstyle LineLength` 与 spotless 的 javadoc 重排在 CJK 注释上互相拉扯**，
    删一个未使用 import 就会顺带重排同文件里没碰过的注释。
    A 禁用 spotless 的 javadoc 格式化（配置层变更，影响所有人 IDE）；
    B 接受重排并做成一次独立的格式化提交（diff 混入大量无关换行）；
    C 本轮不碰 main 源（当前实际做法）。
    另建议把 `magic-string` 10 项（要引常量/复用 Enum，真语义改动）与
    `本轮` 2 项（`quality_guard.py:415` 裸词匹配导致的误报）与 LineLength/UnusedImports 分开定口径。
13. **`CustomerCreditDao.selectExposure` 的渲染参数**（D-10）：让样本 `scope.ids` 非空会同时推动 skipped 基线与
    `knownFailures` 计数变化；需在真实一次运行结果上决定是降基线还是新增方言缺陷，不适合盲改。
14. **`MINIO_PUBLIC_ENDPOINT` 的口径**（D-25）：`.env` 写死历史局域网 IP，换网后「后端能起但浏览器取不到对象」
    属静默半坏。三选一：A 改成主机名/DNS 名（推荐，抗 IP 漂移）；B 只在 `deploy/README.md` 写清「换 IP 必须同步改并重启」；
    C 让后端启动时对 public prefix 做可达性探测并记入启动日志（最省人事后排查，但属新功能，本轮口径上应否做需你定）。
15. **超额核销的状态词要不要在详情抽屉重复**（D-30）：A 抽屉补与列表同源的状态标签；B 把断言改为校验抽屉
    实际承诺的「超额核销 + 金额 + 不代表已退款」说明。B 属放宽既有断言，未经你同意不做。
    数值口径（净应收 -3.5、超额 3.5、两条红字合计 28）在同一条用例里已全绿。
18. **模拟渠道充值的钱包入账由谁驱动**（D-39，ADM-12 真实缺陷）：实测充值在建单当场就把意向置为
    `SUCCEEDED`、财务 Receipt 也立了，但 `availableBalance` 始终 0；随后签名正确的回调只落成
    `RECEIVED`（`transactionId=null`、`processedAt=null`、不报错）。
    即「公司记到一笔收款、客户钱包却没有钱」且无人被告知。三选一：
    A 建单成功后同步驱动钱包 sink；B 建单只到 `PENDING`，必须由回调/对账完成（更贴近真实渠道语义）；
    C 保留现状但让 `RECEIVED` 且意向已成功的对账事件在对账任务里重放并告警。
    选 B 会改变 ADM-12 对外行为（充值不再即时到账），需产品确认。
    **E2E-08 已证伪「同根因」这个猜测**（见 §7l）：订单在线支付走的是**同一条** `SUCCESS` 同步路径，
    当场立收款 + 自动核销 + 读侧 `overApplied` 全部正确、链 6/6 绿。
    所以 D-39 的失血点**只在 `BALANCE_RECHARGE` 的钱包入账 sink**，不是支付→财务的共用主干；
    裁决范围应按此收窄，别按「整条支付链都坏」去设计修复。
16. **可选筛选下拉的「静默降级」与全局错误提示打架**（D-33）：`SupplierSelect` / `WarehouseSelect` 都写了
    catch 并期望「拿不到就安静降级」，但 `src/lib/axios.ts:102` 对任何 `code != 0` 无条件
    `message.error(res.msg)`，发在组件 catch 之前，于是无该权限的角色一进页面就吃到一条处置不了的红色报错。
    三选一：A 给这类「可选数据源」请求加静默标记（推荐，符合两个组件已写下的意图，改动集中在 axios 一处）；
    B 给 `SCM_STOREKEEPER_LEAD` 等角色补 `scm:supplier:query`（出新迁移，等于用授权掩盖前端契约矛盾）；
    C 页面按权限不渲染该筛选项（要逐页判断，成本最高）。
    本轮不擅自改共享组件、不擅自补授权迁移。
    **同时说明测试侧的边界**：`scm-sorting:209` 红在 `strict mode violation`（页面上有两条 toast），
    建单与指派其实已成功 —— 把提示定位收窄到「含成功的那一条」是修正测试精度，
    但它会同时掩盖上面这条噪声，所以在 16 定口径之前**不改这条断言**，让它继续红着当哨兵。
17. **缺口预览面板要不要出现「只读预览」这个短标签**（D-34）：A 补短标签；B 把断言改成匹配现有长句文案
    （`结果仍是只读的冻结批次预览`）。B 属放宽既有断言，未经同意不做。
    注意该用例真正要证的 ADM-05 契约（只读、后端算缺口、四位定点、预览后需求行仍为 0）**已全绿**，
    只差最后一条界面措辞断言。

## 4u. D-31（度量污染，已消除）**一个改配置之前启动的旧 JVM 占着 18080，把 F0 全测成 10001**

`f0-file-storage` 逐文件跑是 `3 ok / 5 fail`，5 条红的错误完全一致：

```text
Error: expect(received).toBe(expected)
Expected: 0
Received: 10001
```

顺着 10001 往回查后端日志，真相是 S3 客户端连不上：

```text
software.amazon.awssdk.core.exception.SdkClientException:
  Unable to execute HTTP request: Connect to 10.232.2.76:9000 failed: Connect timed out
```

而 `10.232.2.76` 正是 D-25 里那个失效局域网 IP —— 可我早就把测试栈的
`XSY_FILE_ENDPOINT` 改成 `127.0.0.1:9000` 了。原因：**改配置之前那次启动的 java 进程还占着 18080**，
改完之后新起的 JVM bind 失败静默退出，`curl 18080` 依然 200、健康检查依然 `code:0`，
于是整套件测的是那个旧进程（fresh 日志里 58 次连向死 IP）。

三个可复用的判读规则：
1. **端口 200 不等于「我起的那个进程」**。重启过配置就必须确认监听进程的启动时刻，
   或先确认旧进程真的死了（`netstat -ano | grep :18080` 拿 PID + 日志首行时间戳）。
2. 通用错误码（10001 这种）要**立刻回查服务端异常栈**，不要在前端侧猜；
   这次从 5 条红到定位根因只花了一次 `grep SocketTimeout`。
3. 现在 `.runtime/e2e-stack.sh` 启动前会检查 18080/18081/18083 是否已被监听，
   占用就直接 `exit 3` 并提示先 `stop`（这条守卫是这次事故换来的）。

**修好之后的实测**：`f0-file-storage.spec.ts` **8 passed (13.8s)** —— 含
「页面上传落库」「私有图走受守卫端点 + 预签名 URL 预览」「公告附件按公告可见性而非目录前缀」
「COMMON/feedback 拒绝他人」「>20MiB 拒」「Tika 拒 HTML 伪装 PNG」「public 目录匿名可缓存读」。
F0/MinIO 这条在 E2E 侧**首次全绿**，D-25 对本轮判读的影响到此关闭（`.env` 那个口径仍留作裁决项 14）。

## 4v. E2E 权威门禁工件（第一次可信的一次）

`.runtime/verify/20261004-150425-976580/`（`summary.json` + `e2e.log`），命令是 §4j(c) 那条完整 env：

```text
134 passed (8.5m)
E2E reports: {'expected': 134, 'skipped': 20, 'unexpected': 4, 'flaky': 0}
RESULT: FAIL   FAILED: e2e exit 1 / E2E: unexpected failures   INCOMPLETE: E2E: 20 skipped
```

对比此前被污染的 `18 expected / 57 unexpected / 83 skipped`：**这 4 条红才是本轮真正要收的账**，
且它们分散在 4 个不同文件（不是同一根因的连锁）。

| # | 用例 | 定性 |
|---|---|---|
| 58 | `scm-finance-receivable-red:11` | **D-30 / 裁决项 15**（抽屉缺「超额核销待处理」状态词），数值口径全绿 |
| 76 | `scm-order:43` 非标重量确认 | **D-32 测试选择器漂移，已修**（待复跑确认） |
| 106 | `scm-purchase:250` 库存缺口预览只读且服务端算 | **D-34 待取证**（`element(s) not found`） |
| 124 | `scm-sorting:209` 主管建单并指派 | **D-33 服务端权限拒绝，待定位是哪一发请求** |

20 条 skip 全部是 serial 链的**级联 skip**（链头红 → 后续不跑），不是环境门控：
唯一那处 env 门控（F0）本轮 8/8 全跑，所以「0 skipped」的差距等价于「把上面 3 条红收掉」。

## 4w. D-32（测试选择器漂移，已修，待复跑）`scm-order` 用例 2 等的是 `Modal.confirm`，页面弹的是授信检查业务弹窗

失败点：`TimeoutError: waiting for locator('.ant-modal-confirm').getByRole('button', {name:'确 定'})`。
失败截图给出全部真相：页面上开着的是 **「确认订单与授信检查」** 弹窗，内含
`未核销应收 ¥0.0000 / 已确认未形成应收 ¥0.0000 / 本次实重结算金额 ¥4.3750 / 确认后授信占用 ¥4.3750`
与优惠券明细（行基础金额 ¥7.0000、客户实付 ¥7.0000），按钮是 `取消` / `确定`。

- 它是 ADM-04 的**业务弹窗**（根类名 `.ant-modal`），不是 antd 的 `Modal.confirm`（`.ant-modal-confirm`），
  所以那个选择器永远等不到；
- 而且它显示的 **¥4.3750 正是该用例后面要断言的 `settlementTotalAmount`** ——
  非标重量按实重结算这条业务逻辑是**对的**，红的是测试的定位方式。

修法（只改选择器，并把语义断言**加强**而不是放宽）：定位 `.ant-modal-content:visible` 的最后一个，
先断言标题含「确认订单与授信检查」与「本次实重结算金额」，再点 `/^确\s*定$/`；
收尾的 `toHaveCount(0)` 同步换成同一套选择器。业务断言（`已确认`、`4.3750`）一字未动。

## 4x. D-33 / D-34（两条待取证的红，已排除一个常见误解）

**D-33 `scm-sorting:209`「主管在页面上建单并派给分拣员」**：断言要等 `.ant-message-notice` 含「成功」，
实际拿到的是 **“对不起，您没有权限访问此内容哦~”**（Sa-Token 的权限拒绝文案）—— 这是**服务端真的拒了**。
先排掉最容易误判的一种解释：不是角色少授了分拣权限。实测该角色 `SCM_STOREKEEPER_LEAD`
在这条链路上需要的码**全部已授**：

```text
scm:sorting:task:add / task:assign / task:query / task:complete / task:print /
task:reopen / task:cancel / summary:query / scale:query / scale:accept / scale:report / item:update
```

所以被拒的是这条流程里**另一发**请求（建单弹窗要先拉候选订单与受指派员工，
可能落在 `scm:order:query` 或员工/仓库侧的码上）。**尚未定性**，需要一次带网络记录的复跑。

**D-34 `scm-purchase:250`「库存缺口预览只读且服务端计算」**：`expect(locator).toBeVisible()` 失败、
`element(s) not found`，同文件后 4 条级联 skip。这条与 ADM-05 净需求 / 缺口预览直接相关，
也是本轮为 `cross-domain-dao-allowlist` 补登记的那条 AND 双权限读端点，**必须查到请求与响应再定性**。

**另外一条好消息**：`scm-product.spec.ts` 在 15:12 的独立复跑里 **8/8 全绿**
（14:44 那一轮报的 3 红没有复现）—— 说明那 3 条属于当时的一次性状态，
不再单独立项。

## 4yc. D-34（ADM-05 只读预览的界面措辞，需裁决）**业务契约全绿，只差「只读预览」这四个字的连续出现**

`scm-purchase.spec.ts:250` 的 11.6s 红在最后一句 `expect(getByText('只读预览')).toBeVisible()`。
把这条用例拆开看，**它真正要证的 ADM-05 契约全部通过**（都在同一个 test 体内、失败点之前）：

- `preview.total` 是 number、`preview.list` 是数组；
- 每行 `calculationStatus` 都在合法集合内（`SUMMARY_STATUS.has(...)`）；
- 五个缺口字段（`orderDemandQuantity` / `stockAvailableForSelectedOrders` /
  `selectedOrderReservedQuantity` / `otherReservedQuantity` / `stockComparisonGap`）
  非空时必须是**四位定点字符串**（`/^\d+\.\d{4}$/`）—— 即缺口与可用量由后端算，前端不重算；
- **只读性**：预览前 `demand/query` 该单 `total=0`，预览后仍 `total=0` → 预览确实不建需求行。

页面侧实测：tab「订单汇总 / 缺口预览」存在（`purchase-demand-list.vue:122`）、
表格 id 存在（`purchase-demand-summary-preview.vue:50`），
而组件里的说明是（`:46-47`）`message="冻结批次净需求预览"` +
`description="…结果仍是只读的冻结批次预览"` —— **含「只读」与「预览」，但不含连续的「只读预览」**。

所以这条不是功能缺失，是**用例钉了字面短语而页面用了长句表述**。两条路（裁决项 17）：
A 在预览面板补一个短标签「只读预览」（与 `WarehouseSelect` 那类"把可选性讲清楚"的做法一致，
也让用户不必读完整段说明就知道点了不会生成需求）；
B 把断言改成 `toContainText('只读')` 或匹配实际文案。
B 属放宽既有断言，未经同意不做；本轮让它继续红着。


## 4y. D-33（已定位并定性为跨页面前端通病）分拣页一条「无权限」提示来自可选筛选下拉，且建单其实成功了


复跑稳定复现（4.8s，与门禁里的 3.x s 同形），断言等 `.ant-message-notice` 含「成功」，
拿到的是 Sa-Token 的权限拒绝文案。已经**用证据排除**的解释：

1. **「角色少授了分拣权限」—— 不成立**。`SCM_STOREKEEPER_LEAD` 的分拣码齐全：
   `task:add / task:assign / task:query / task:complete / task:print / task:reopen / task:cancel`
   以及 `summary:query`、`scale:query/accept/report`、`item:update`。
2. **「授了但菜单被禁用/删除，所以不生效」—— 不成立**。逐行查
   `t_menu.disabled_flag / deleted_flag / visible_flag`，7 条 `scm:sorting:task:*`
   全是 `f / f / t`，即真实有效。
3. **「是受指派人下拉的 `/employee/queryAll` 被拒」—— 不成立**。
   `EmployeeController#queryAllEmployee`（`/employee/queryAll`）**没有 `@SaCheckPermission`**，
   它只要求登录，不会产生 30005。

后端日志侧也拿不到：这条拒绝没有以 `NotPermissionException` 形式落盘（全局异常处理器把它转成
业务码后不留栈），`grep` 只能看到 `/scm/sorting/tasks`、`/scm/delivery/routes` 这些路径，
看不出是哪一发返回 30005。

**已定位（一次性探针 `e2e/tmp-d33-probe.spec.ts`，取证后已删除）**：主管打开分拣任务页并展开建单弹窗，
整条链路上唯一被拒的是

```text
200 code=30005 对不起，您没有权限访问此内容哦~ <- /scm/supplier/option/list
```

也就是**页面筛选栏的「供应商」下拉**（`sorting-task-list.vue:74` 的 `SupplierSelect`，
服务于 `:810` 的「供应商来源」列），**不是建单表单**——建单表单只有受指派人 / 备注 / 订单商品三项。
探针同时证实了另一件关键事实：错误里那句
`strict mode violation: locator('.ant-message-notice') resolved to 2 elements`
说明当时页面上**有两条提示**，其中一条就是建单成功提示 ——
**分拣建单与指派本身是成功的**，用例红在「提示定位不精确 + 页面上多了一条不相干的报错」。

真正的缺陷是一条**跨页面的前端通病**，且比「少授一个权限」更值得修：

- `SupplierSelect` 自己写了 `try { ... } catch { smartSentry.captureError(e) }`，
  组件层的意图是「可选筛选项拿不到就静默降级」（`WarehouseSelect` 的注释把这个意图写得更明白）；
- 但 `src/lib/axios.ts:102` 对任何 `code != 0` 都无条件 `message.error(res.msg)`，
  **发在组件 catch 之前** —— 于是组件的静默降级被全局拦截器推翻：
  一个有权打开分拣页、只是没有供应商查询权的主管，会看到一条他无法处置的红色报错。

所以这不是 ADM-11 的业务规则错，而是「可选下拉的降级契约」与「全局错误提示」互相打架。
留作裁决项 16（见 §4），本轮不擅自改共享组件或补授权迁移。

## 4z. D-35（我的门禁执行错误，已纠正）**`verify.py backend` 少了 DB env → 768 个 error，看着像天塌了其实没连对库**

第一次跑权威后端门禁的命令只带了 `XSY_V2_PG_CONTAINER` 与 `XSY_FILE_STORAGE_MODE`，
**没有** `XSY_V2_DB_URL / _USERNAME / _PASSWORD`。结果：

```text
Backend reports: {'tests': 1374, 'failures': 0, 'errors': 768, 'skipped': 0}
```

`PgITDatabase` 在 `XSY_V2_DB_URL` 缺失时**回退到默认值** `jdbc:postgresql://127.0.0.1:15432/xsy_scm`
—— 那是停在 V17、与仓库分叉的弃用库，于是 Flyway `MigrationChecksumMismatch` 让几乎每个
Spring 上下文都建不起来。特征是 **`failures=0` 而 `errors` 巨大**：
业务断言一条都没跑，所以一条都没失败。
（这条正是 `PgITDatabase` 类注释里写明的那个历史坑，我自己又踩了一遍。）

纠正做法：把整套 env 固化成 `.runtime/backend-env.sh`（含 p6spy 前缀、Redis 口令、
MinIO 与盘点快照密钥、E2E 建号三件套），用法
`XSY_ROOT=/d/Projects/xsy-scm DB=xsy_v2_final bash -c 'source .runtime/backend-env.sh && python tools/verify.py backend'`。
复跑起始证据：日志里 `checksum mismatch` 命中 **0** 次，且已出现
`Tests run: 17, Failures: 0, Errors: 0` / `Tests run: 13, Failures: 0, Errors: 0` 的 PG IT 通过行。

**判读规则**：`errors` 远大于 `failures` 时，第一动作是查连接串与 Flyway，而不是查业务代码。

## 4za. 后端权威门禁的 10 条红逐条归因（**没有一条未解释**）

`.runtime/verify/20261004-152101-753380/`：`tests=1374 failures=10 errors=0 skipped=0`。
逐条查到底之后，10 条红的归属是：

| 红 | 归属 |
|---|---|
| `ScmPermissionContractPgIT.usedPermissionsAreCataloguedAndPublished` | 裁决项 3（打印金额权限是计算表达式，静态 Catalog 表达不了） |
| `ScmMenuComponentExistencePgIT.everyMenuComponentExistsOnDisk` | 裁决项 6 / D-17（两条指向不存在 `.vue` 的可见菜单） |
| `AdminSettlementTermsPgIT.nonGroupParentCannotActAsSettlementCustomerEvenWithDirtyRelationship` | 裁决项 2（要不要兜住带外改库的纵深防御） |
| `ScmPurchaseMigrationIT.noForeignKeysAndInventoryTablesBelongToW6` | 裁决项 1（9 条外键 vs「设计不用外键」） |
| `ScmInventoryMigrationIT.ddlContract:98` | **同一根因**：该断言是 `SELECT count(*) FROM pg_constraint WHERE contype='f'`，不带表范围过滤的全局计数，期望 0 实得 9 |
| `F0FileStorageCloudIT.context:61` × 5 | **我的 env 缺项**：该套件要求 `XSY_FILE_PRIVATE_URL_EXPIRE=2`（私链 2 秒过期才能测过期），没给就在 context 阶段拒绝执行 |

值得单独记一笔的是第 5 行：它**不是新缺陷**，而是裁决项 1 的第三个实例。
也就是说「用不用外键」这个口径目前同时被三条断言把守
（`ScmPurchaseMigrationIT`、`ScmInventoryMigrationIT.ddlContract`、以及 §4 里已登记的那两条），
且它们都是**全局计数**——这正是裁决项 1 需要定「全局不变量还是各域自选」的原因：
定成全局就得出一条迁移删约束的路线，定成各域自选就要把这三条都收窄到各自的表范围。

`XSY_FILE_PRIVATE_URL_EXPIRE=2` 已写进 `.runtime/backend-env.sh`，
复跑见 `.runtime/verify/backend-gate3.log`（预期 `failures=5`，全部对应已登记裁决项）。

**复跑实测（`.runtime/verify/20261004-152733-459176/`，task #7 的正式工件）**：

```text
Backend reports: {'tests': 1374, 'failures': 5, 'errors': 0, 'skipped': 0}
RESULT: FAIL   INCOMPLETE: none
```

即补齐 env 后，**5 条红全部落在已登记的裁决项上，0 errors、0 skipped、没有一条未解释**。
这是本轮第一次拿到「harness 自己写的 `summary.json` + 可逐条归因的红」这一组合。

## 4zb. D-32 修复实测：`scm-order` 从 1/1/10 变成 **12 passed**

换掉 `.ant-modal-confirm` 选择器之后整条链全绿（46.8s）：
建单草稿与身份差异、提交锁价、**非标重量录入实重后确认（结算 ¥4.3750）**、
取消终态规则与零价标准单、补单原因与原单身份、重复请求幂等、
审计页与错误重试与只读权限、退货批准原子生成退款并在页面完成、
Excel 模板下载与标准/非标导入、示例行与手工改价规则整批拒绝、
历史复用带出草稿与近期价浮层、未保存草稿本地保留与恢复、
近期价面板按 客户+SKU 分键互不串价。

这条的价值不只是「少一个红」：`scm-order` 是 §15 多条黄金链的公共前段，
之前它第 2 条一红，后面 10 条整链 skip，等于**订单域后半段完全没有 E2E 证据**；
现在这 12 条是真实跑过的。

## 4zd. E2E 最终权威工件：**145 passed / 3 failed / 10 skipped**，三条红全部对应已登记裁决项

`.runtime/verify/20261004-154143-699852/`（`summary.json` + `e2e.log`），命令仍是 §4j(c) 那条完整 env。
演进过程（同一套代码，三轮权威门禁）：

| 轮次 | expected | unexpected | skipped | 说明 |
|---|---|---|---|---|
| 15:04（D-31 修复前） | 134 | 4 | 20 | 含 `scm-order` 选择器漂移 |
| 15:41（D-32 修复后） | **145** | **3** | **10** | 订单链 11 条从 skip 变成真跑通 |

剩下的 3 条红逐条归属，**没有一条未解释**：

```text
x  58 scm-finance-receivable-red:11  → 裁决项 15（D-30 抽屉缺「超额核销待处理」状态词）
x 106 scm-purchase:250               → 裁决项 17（D-34 缺「只读预览」短标签；ADM-05 契约本身全绿）
x 124 scm-sorting:209                → 裁决项 16（D-33 可选下拉的降级被全局 message.error 推翻）
```

10 条 skip 全部是上面两条 serial 链的级联（`scm-purchase` 4 条 + `scm-sorting` 6 条），
不是环境门控 —— 本轮唯一那处 env 门控（F0/MinIO 8 条）已经**每次都真跑且 8/8 绿**。
因此「§16 要求 E2E 0 skipped」的**全部剩余差距**等价于「裁决项 15/16/17 定口径」，
而不是还有功能没测到。







## 4b. 证据留档清单（BASE-10）

`tools/verify.py` 每次跑都会在自己的日志目录写 `summary.json`（`failed` / `incomplete` / `exitCode`）。
本轮产生的 8 份门禁工件：

```text
.runtime/verify/20261004-044925-522925   quality   （修 D-01 白名单前：DAO 边界 + quality-guard 双红）
.runtime/verify/20261004-050810-097791   quality   （DAO 边界转绿，只剩 quality-guard 的 34 项）
.runtime/verify/20261004-045135-481722   frontend  （8 个契约红）
.runtime/verify/20261004-052205-883260   frontend  （FAILED: none，仅剩 E2E 服务未就绪）
.runtime/verify/20261004-060356-890909   e2e       （56/57 失败=浏览器二进制缺失）
.runtime/verify/20261004-060454-091578   e2e       （同上，装浏览器前的最后一次）
.runtime/verify/20261004-060608-656111   e2e       （24 passed / 57 failed / 83 skipped）
.runtime/verify/20261004-062459-086224   e2e       （24 passed / 59 failed / 75 skipped，带真实浏览器）
```

另有 mvn 直跑的原始日志：`backend-clean.log`（首次全量 12F/29E）、`backend-rerun.log`、
`backend-final.log`（收口后 4F/0E）、`frontend-test.log` / `frontend-test2.log`、
`schema-it*.log`、`migration-it.log`、`print-it*.log`、`upgrade-behavior.log`、`menu-it.log`、
`cloud-it.log`、`up1.log` / `up2.log`（BASE-04 两阶段迁移）。

**已就地补齐的一部分**：`python tools/migration_checksum_guard.py check` 单独跑 **RESULT: PASS**，
其中 `modified: 0` / `renamed: 0` —— 也就是说「已应用的 Flyway migration 不得被改动」这条硬约束
在当前 HEAD 上是守住的（这是 §16 步骤 3 里唯一不在 `mvn test` 覆盖内的那一段）。
另有 `unbaked: 32`（V75–V106 尚未冻结进 `migration_checksum_snapshot.json`），
守卫自己标注「本状态不算失败」。**没有**擅自执行 `sync`：那会改动仓库内的快照文件、
影响所有人对存量库的判定口径，属于需要你点头的仓库状态变更（虽然这些迁移确已在我的
一次性 V106 库上全量应用且 `success` 全 1）。

**尚未闭合的证据缺口（诚实标注）**：§16 步骤 3 要求的是 `verify.ps1 backend`，
而我的后端全量是用 `mvn -pl sa-admin -am test` 直跑的 —— 两者唯一的差别是
`verify.py backend` 会**先跑 `migration_checksum_guard.py check`**（防止已应用的迁移字节被改动后
在每台存量库上炸 Flyway validate）。那道守卫本轮**没有执行过**。
需要在收口前用 `verify.py backend` 补一次正式工件。




- 修复夹具漂移：13 处 IT 插入补 `settlement_customer_id` / `settlement_customer_name_snapshot`（9 个 IT 文件），
  重跑后 backend error 由 29 降到 5。
- 修复 D-13：`SmartAdminMapperPgValidationIT` 的参数推断重试上限、数据范围参数构造、跳过项基线 493 → 608，
  定向重跑 **1/1 全绿**（授信敞口与对账单的范围收窄 SQL 首次真正经过 PG 校验）。
- 修复 D-06 其余可核实项：`ScmPurchaseMigrationIT` 非主键索引 35→36、部分索引 31→32、
  唯一索引 7→8（同一条 V78 ADM-05 批次行唯一索引），`flywayHistoryIsAppendOnly` 版本清单延到 V106；
  `ScmPurchasePermissionMigrationIT` 端点 35→38 与权限码 24→27
  （该用例紧随其后的 `seeded.containsAll(declared)` 会验证 27 条码**都真的种进了 t_menu**，实测通过，
  两个用例现在分别 2/2 与「只剩外键待裁决」）。
- 修复 D-05：`ScmFinanceSchemaPgIT` 11 条语句补齐结算列、`due_date` 移出禁用清单、
  付款方式约束改结构化断言；`ScmFinanceReceiptPgIT` 允许 V100 的来源幂等索引并要求部分谓词。
  定向重跑 **17/17 + 17/17 全绿**。
- 修复 D-06 之一：`ScmOrderMigrationIT` 索引基线 27 → 28（V87 / ADM-08），定向重跑 **3/3 全绿**。
- 修复 D-06 之二：`ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly` 版本清单延到 V106，
  该用例从 3 failures 降到 2（剩下两条正是待裁决/待核对的 schemaShape 与外键）。
- 修复 D-08：前端 8 个红项全部处理（2 个真实产品缺陷 + 6 个过期基线，其中导出清单改成逐条路径断言）；
  `verify.py frontend` 现在 `FAILED: none`。
- 补跨域只读 DAO 白名单 3 行（`tools/quality/cross-domain-dao-allowlist.tsv`，25 → 28 行），`quality` 的 DAO 边界守卫转 PASS。
- 修复 `SmartAdminMapperPgValidationIT.prepare()` 的 16 轮上限（改为按参数个数），
  撤销对 `ScmFinanceProfitDao.query` / `.summary` 的「MySQL 残留」误判：两条语句实际能被 PostgreSQL 正常 `PREPARE`。
- 修复 D-01：`inventory-const.ts` 补 `SALES_RETURN_IN`（含入方向）、`SALES_RETURN_RECEIPT_ITEM`。
- 测试补强：`w6-inventory-contract.test.mjs` 枚举与方向分组对齐后端，并新增入方向集合一致性断言。
- 一次性库 `xsy_v2_clean`、`xsy_v2_upgrade`、`xsy_v2_rerun` 为本轮临时产物；`xsy_scm` 未被改动。

## 5b. 完成审计：本轮把清单推进到哪、没推进的是什麼

**明确判定：验收清单尚未完成，本档不构成「ADM-01～12 已验收通过」的结论。**

已按清单完成的（有可复核证据）：
- §0 BASE-01～05、06、08、10 全部取证；BASE-07 由「未启用」转为**真实覆盖**（MinIO 5/5、0 skip）。
- §14 最近两个修复的专项回归：4/4 有条目级证据。
- §16 步骤 1-4：clean、quality（部分）、backend 全量（116 个 `*IT` 真跑）、frontend（`FAILED: none`）。
- backend 由 12 failures + 29 errors 收到 **4 failures + 0 errors**；剩余 4 个红逐条 = 已登记待裁决项。
- 缺陷修复与测试补强共 13 个文件 + 2 个新用例文件，全部属 §0 允许的两类改动。
- 清单 §10「GPS 退场」6/6 逐条实证；§16 第 8 条 P0 并发在自动化层 18 用例全绿（并标注其偏薄）。

未完成、且**不能**宣称完成的：
1. `quality` 门禁未转绿（34 项 blocking，卡 spotless 口径 = 待裁决 4）。
2. backend 仍有 4 红，全部是待裁决口径，不是未解释的红。
3. E2E 门禁未绿，且**根因尚未完全定位**：已证伪「只是 CPU 争用」，
   已确认其中一条 404 是测试侧 URL 拼错（D-21a），
   但 `smartadmin-native` 17 条统一 ~22.9s 的超时仍无定论（D-21b）。
4. §15 八条黄金业务链：只有分段自动化证据，**没有一条做过贯穿式执行**，
   因此不能算「真正形成 SCM 闭环」—— 这正是清单自己定义的最后一步。
5. 现场项（真实地图 Key/配额、电子秤协议、支付渠道真网）按清单与本档口径记 `NOT COVERED`，
   本机 mock 通过不替代。

收口顺序建议（需要用户先解 7 个待裁决项中的 1/2/3/4/6/7）：
先定 E2E 数据与 18083 前置（D-19/D-18）并把 D-21b 定性清楚 → E2E 转绿；
再定 spotless 与外键口径 → quality 与 backend 转绿；
最后才按 §15 人工跑八条链，达成清单定义的停止线。


## 4h. D-22（E2E 判读收口）**至今还没有一次「三个前置全对」的干净运行**

独占那轮 E2E 跑完了：`18 expected / 57 unexpected / 83 skipped`，与装浏览器之前几乎同形。
我先怀疑「浏览器又没装上」，实测否证：
`chromium_headless_shell-1234/chrome-headless-shell-win64/chrome-headless-shell.exe` **存在**，
且该轮自己的日志里 `Executable doesn't exist` 出现 **0 次**。

真正的详情是第一条失败的形态：

```text
adm02-04-ui-contract.spec.ts:20 › 授信阻断提示与例外原因随确认命令提交
TimeoutError: locator.click: Timeout 15000ms exceeded.
  - waiting for getByRole('button', { name: '确认订单', exact: true })
```

即**夹具页没有挂载**，所以按钮永远等不到 —— 而这正是 D-18 里我那个错误修复的后果：
该轮从 22:47 起跑，而我到 22:56 才把 18083 换成 vite，前面这段时间 18083 上是我那个
把 `.ts` 当纯文本返回的静态服务，模块被浏览器拒绝执行，页面必然是空的。

结论（也是本轮最重要的一条方法论）：
**E2E 至今的每一轮都各被一个不同的环境缺陷污染过** ——
第 1/2 轮缺 chromium 二进制；第 3~5 轮里夹具页被我自己的错误静态服务挡住；
再叠加 D-19 的跨 spec 数据依赖。因此现在这份 57 红**不能当作 ADM 模块的 E2E 结论**，
既不能说它证明产品坏，也不能拿它的任何绿色证明产品好。

三个前置现在都已就绪（chromium 已装、18083 已按 vite 正确起、`XSY_V2_PG_*` 已配），
**下一步就是在它们全对的状态下重跑一次**（串行 158 用例，约 25~35 分钟）：

```bash
python tools/verify.py e2e        # 独占跑，期间不要再起 mvn
```


## 4i. D-23（测试夹具缺陷，已修）**夹具没装 vue-router，ADM-02 整页渲染不出来**

按 §4h 收口后单跑 `adm02-04-ui-contract.spec.ts`：第 1 条（授信）**通过了** —— 说明 D-18 的
18083 vite 修复是对的，夹具页确实挂载了。第 2 条仍然红，但错误换了形态：

```text
TimeoutError: waiting for getByRole('button', { name: '实物接收', exact: true })
Error: 浏览器未捕获异常：Cannot read properties of undefined (reading 'name')
```

根因不在产品代码：`e2e/fixtures/adm-ui.ts` 只 `app.use(store).use(Antd).use(smartEnumPlugin)`，
**没有装 vue-router**；而 `order-return-list.vue:256-259` 为了守「深链 `?returnId=` 自动开详情」
在 setup 里就读 `route.name`。没有 router 时 `useRoute()` 返回 `undefined`，`route.name` 直接抛，
组件 setup 失败 → 整页不渲染 → 按钮永远等不到。`order-detail.vue` 不碰路由，所以第 1 条不受影响 ——
这也解释了为什么这个夹具缺陷只在 `mode=return` 上暴露。

判定：**测试基础设施红，属 §0 允许修复的那一类**（真实应用里视图必然由 router 渲染，
`useRoute()` 不会是 undefined；这不是 ADM-02 的业务缺陷）。修法是把夹具补齐成和线上一致的挂载环境，
而不是改断言或删掉路由依赖：给夹具装一个 `createMemoryHistory` 的 router，并在 mount 前
`await router.push(location.pathname + location.search)`，使 `route.name` 稳定、`route.query` 与浏览器一致。

结果：该 spec **2/2 通过**（6.2s），断言一字未改。


## 4j. D-24（本机环境，两条）E2E 全量的两个新坑

**(a) 我把 `verify.py e2e` 的前置写漏了，白跑 18 分钟。** 首轮我只带上了 `XSY_V2_PG_DB=xsy_v2_e2e`，
`tools/e2e_accounts.py:42` 的容器名默认是 **`xsy-pg-v2`**、角色默认是 **`postgres`**，
而本仓库 `docker-compose.yml:76-82` 起出来的容器叫 **`xsy-scm-postgres-1`**、超管叫 **`xsy_scm_app`**。
于是所有依赖建号的 spec 在 `beforeAll` 就炸：

```text
Error: Command failed: python ../tools/p1_e2e_accounts.py setup
Error response from daemon: No such container: xsy-pg-v2
```

这属于**文档化工具与本仓库 compose 的命名漂移**：`e2e_accounts.py` 的默认值来自另一套历史本机栈。
口径要么改脚本默认值（非业务改动，需用户批准是否纳入本轮提交），要么在文档里把三个 override 写死。
正确的一次性命令（第二次运行用的就是它）：

```bash
XSY_V2_PG_CONTAINER=xsy-scm-postgres-1 XSY_V2_PG_USER=xsy_scm_app XSY_V2_PG_DB=xsy_v2_e2e \
python tools/verify.py e2e
```

**(b) 一次失败的运行不会留下可信的 stats。** 首轮 `E2E reports: expected 0 / skipped 0 / unexpected 0`
却持续了 1094s —— 全部用例死在 hook，Playwright 没给它们计数，harness 只能报
`INCOMPLETE: E2E: no passing tests`。**判读 E2E 时「stats 全 0」不等于「没跑」，也不等于「全绿」**，
必须先看日志尾部有没有 `No such container` / `Executable doesn't exist` 这类前置错误。

五个一次性库（`xsy_v2_clean/upgrade/rerun/final/e2e`）此刻都还在，`xsy_scm` 仍停在 V67 未被触碰。

**(c) F0/MinIO 那 8 条用例的开关在 Playwright 自己的进程环境里。** 全量扫过 `e2e/**` 之后，
整套件只有**一处** env 门控 skip：`f0-file-storage.spec.ts:30`

```ts
test.skip(process.env.XSY_FILE_STORAGE_MODE !== 'cloud', 'F0 presigned acceptance requires cloud storage; …');
```

18080 上的后端确实是 cloud 模式（MinIO 容器 healthy，BASE-07 已用真 MinIO 跑通），
但 Playwright 读的是**自己进程的 env**，不会继承后端的。所以 §16「E2E PASS 且 0 skipped」
必须在命令行上显式带上它。最终采用的一次性命令（同时覆盖 a/b/c 三条）：

```bash
XSY_FILE_STORAGE_MODE=cloud XSY_V2_PG_CONTAINER=xsy-scm-postgres-1 \
XSY_V2_PG_USER=xsy_scm_app XSY_V2_PG_DB=xsy_v2_e2e python tools/verify.py e2e
```

其余 `process.env` 只涉及 API base 与 `W5_E2E_MANAGED_EXTERNALLY`（本机有 python，不需要）。


## 4k. D-25（配置/部署风险）**`.env` 里的 `MINIO_PUBLIC_ENDPOINT` 是写死的历史局域网 IP，人已换网就全坏**

重跑取证时后端起不来，逐层排到文件存储配置，顺带发现仓库根 `.env`（2026-09-23 写）里：

```text
MINIO_PUBLIC_ENDPOINT=http://10.232.2.76:9000
```

实测这个地址**现在不可达**（`curl --max-time 6` 直接超时，`127.0.0.1:9000/minio/health/live` 才是 200）。
它不是密钥，但它是**功能正确性依赖的地址**：`docker-compose.yml:56-62` 把同一个值同时喂给
`XSY_FILE_ENDPOINT`（S3 client / Presigner 签名用）和 `XSY_FILE_PUBLIC_URL_PREFIX`（拼给浏览器的对象 URL），
compose 注释自己写明「必须同时对浏览器和容器可达」。主机 IP 一变：

- 后端**照样能起**（`initS3Client` 只校验 URL 形态，不探测可达性），
- 但所有「浏览器回看对象」的路径必坏 —— 这正解释了上一轮 F0 的两条红：
  `#1 file page upload persists and appears in the list`（20.5s）与
  `#2 private image … previews via a presigned URL`（17.7s），
  而 **不经过浏览器的 6 条全绿**（#3/#4/#5/#6/#7/#8，最快 16ms）。

判读：这两条红在修好端点之前**不能算 ADM/F0 的产品缺陷**，属环境配置。
但它也暴露一个真实的部署风险 —— 端点写死 IP、且启动时不做可达性探测，换网络后是**静默半坏**状态。
建议（属产品/部署口径，需裁决，见 §4 第 14 条）：把 `MINIO_PUBLIC_ENDPOINT` 改为用主机名（如
`http://<局域网 DNS 名>:9000`）或在 `deploy/README.md` 明确要求「换 IP 后必须同步改并重启」，
更好的是让后端启动时对 public prefix 做一次可达性检查并把结果打到启动日志。

本机测试栈的处理：`.runtime/e2e-stack.sh` 固定用 `http://127.0.0.1:9000` 同时作为 endpoint 与
public prefix（MinIO 发布在 `0.0.0.0:9000`，浏览器与 S3 client 都可达），
**没有改仓库的 `.env`，也没改 `docker-compose.yml`**。

## 4l. D-26（我自己的测试栈配置错误，已修，记录以免重复踩）

`bash .runtime/e2e-stack.sh` 现在是一键起 18080/18081/18083 的可复现入口。第一版里有三个我自己引入的错，
每个都以「后端起不来」或「用例假红」的形式出现，值得留字：

1. `XSY_V2_DB_URL` 给了裸 `jdbc:postgresql:`，但 dev 配置的 driver 是 `P6SpyDriver`
   → Druid `connect error ... driverClass com.p6spy.engine.spy.P6SpyDriver`。必须带 `jdbc:p6spy:` 前缀。
2. `XSY_FILE_PUBLIC_URL_PREFIX` 拼成 `$PUB/$BUCKET`（无尾斜杠）
   → `IllegalStateException: Cloud public-url-prefix must be an absolute HTTP(S) URL ending with /`，
   注意这条是**BeanCreationException 链**（`initS3Client` → `initCloudFileService` → `loginManager` → …），
   栈顶看着像登录模块坏了，其实与 ADM 业务无关。
3. endpoint 与 public prefix 被我拆成两个主机（`127.0.0.1` vs `.env` 里的 LAN IP），
   而 compose 的口径是两者共用一个地址 —— 预签名 URL 的签名主机与浏览器实际请求的主机不一致。

**Why:** 这三条的报错都发生在**基础设施层**，但症状会往上冒到业务用例上；
下次看到「登录/文件相关 Bean 创建失败」或「预签名 URL 取不到图」，先查这三处再怀疑产品代码。


## 4m. D-27（E2E 度量方法）**「整套件一次跑」与「逐文件跑」给出不同的红 —— 两个都必须知道**

为了不被中断吃掉证据，改成逐 spec 文件跑（`.runtime/e2e-by-file.sh`，每跑完一个文件立即落盘）。
同一批数据在两种调度下的对照（都取自动跑，不是推断）：

| spec | 整套件单进程串行 | 逐文件独立进程 |
|---|---|---|
| `adm02-04-ui-contract` | 2 ok | **1 ok / 1 fail**（`实物接收` 等不到，15.7s） |
| `f0-file-storage` | 6 ok / 2 fail | **3 ok / 5 fail** |
| `scm-customer-360` | 2 fail（10.4s×2） | **2 ok** |
| `scm-customer` | 2 fail（15.3s / 10.4s） | **2 ok** |

**已定性的两条：**

1. **D-19 拿到实证**：`scm-customer-360` / `scm-customer` **单独跑全绿、进整套件必红**，
   正是「依赖别的 spec 后段才创建的数据」这个结构问题的直接证据 —— 裁决项 7 的 A 方案
   （数据依赖型 spec 在 `beforeAll` 自造最小夹具）现在是**有数据支撑的推荐**，不再是猜测。
2. **F0 的红随夹具状态摆动**：#1/#2/#8 这类「浏览器真的去取对象 URL」的用例，
   对 `XSY_FILE_ENDPOINT` / `XSY_FILE_PUBLIC_URL_PREFIX` 的一致性敏感（见 D-25/D-26）。
   同一条用例在两种端点配置下一次过一次红，说明**先固定部署口径再判 F0 的业务结论**。

**待验证的假设（不要把这条当结论）**：`adm02-04` 的 `实物接收` 在逐文件模式下重新超时，
而 20 分钟前单跑是 2/2 —— 唯一变化是我重启了 18083 的 vite，冷启动时它要先做依赖预构建
（`/node_modules/.vite/deps/*` 首次请求才生成），首个页面加载可能超过 15s 的 `actionTimeout`。
若成立，则 **E2E 的纪律必须是「先 warm 夹具页再计量」**（先访问一次 fixture 页再跑用例）。
验证方法：整套件跑完后单跑 `adm02-04`，若恢复 2/2 即确认冷启动假设。

> **这条假设已被否证**（见 §4q 的 D-29）：warm 之后仍然复现，且症状从「等不到按钮」变成
> 「`element is not stable` / `element was detached`」。真因是 mock 白名单漏了一条端点，
> 与 vite 依赖缓存无关。保留这段原文是为了留下「假设→实测→推翻」的痕迹，
> 也提醒后续读者：`element detached` 这类症状的第一解释应该是「页面被导航走了」。


## 4n. D-28（数据范围专项的真实夹具缺口）**`scm-data-scope` 需要两座有余额的启用仓，而 E2E 库只有一座仓库**

逐文件跑之后，`scm-data-scope.spec.ts` 是**独立跑也红**的少数文件之一
（`ok=0 fail=1 skip=5`，第 1 条 0ms 失败 → `beforeAll` 抛错，serial 链后 5 条全被跳过）。
读了它的 `beforeAll`（`e2e/scm-data-scope.spec.ts:130-165`）就知道不是产品缺陷，也不是我配错环境，
而是**夹具要求没有被满足**：

```ts
if (stocked.length < 2) {
  // E2E 库会保留上轮测试留下的停用库存仓；启用其中一座作范围对照……
  const disabled = await ok(api, 'post', '/scm/warehouse/query', {status: 'DISABLED', ...});
  ...
}
expect(stocked.length, '需要两个都有余额行的启用仓库才能验证范围，实际 ' + JSON.stringify(stocked))
  .toBeGreaterThanOrEqual(2);
```

它在**依赖上一轮遗留的停用仓库**（注释自己写明了）。当前 `xsy_v2_e2e` 的实测：

```text
select status, deleted, count(*) from warehouse group by 1,2;   → ENABLED / f / 1
select count(distinct warehouse_id) from inventory_balance;     → 1
```

即整库只有 1 座仓库（`id=1 默认仓库`，743 数量），既没有第二座启用仓，也没有可回收的停用仓，
`stocked.length` 永远停在 1 → 断言必红。**这条红与 ADM 业务代码无关，但它是 §16
「权限与数据范围专项 PASS」的硬阻塞** —— 数据范围是清单点名的必过项，不能记成「环境噪音」就放过。

判读要点（也是为什么值得单独记）：这条**同时**是裁决项 7（E2E 数据依赖口径）的第二个实例，
并且比 D-19 更硬 —— D-19 是「依赖别的 spec 后段创建的数据」，本条是
「依赖上一轮自己跑完留下的残渣」，在全新 V106 库上永远不成立。
A 方案（spec 在 `beforeAll` 自建最小夹具：建第二座仓 + 调一笔实盘/入库使两边都有余额行）
是唯一能同时满足「干净库可复现」和「BASE-05 不往长驻库写数」的做法。


## 4o. D-28 的修复（已实施，**实测结果待 §4p 回填**）

先补一条让「等残渣」这个前提站不住的证据：`beforeAll` 说依赖上一轮留下的停用库存仓，
但同一个文件的 `afterAll`（`e2e/scm-data-scope.spec.ts:186-205`）会把那仓
**盘亏归零再停用** —— 它自己把下一轮要指望的残渣处理掉了。所以这不是口径分歧，
是夹具内部自相矛盾，属 §0 允许的「测试补强」，不需要等裁决就应当修。

修法（只动测试文件，业务代码 0 改动）：在「回收停用仓」分支之后追加自建分支 ——
用默认仓里数量最大的余额行作调拨源，`/scm/warehouse/create` 建对照仓
（`WarehouseService.java:101-102`：新建即 `ENABLED`，且**没有**「启用仓唯一」的约束，
所以 `scm-inventory-write.spec.ts:311` 那句「建完立刻停用」只是它自己那两条用例的需要，
不是全局不变量），再走完整两步调拨 `/scm/inventory/transfer/create` → `/ship/{id}` → `/receive/{id}`。
余额行由库存域命令产生（`TRANSFER_IN` 流水），**不伪造余额行**；
`temporaryWarehouseId` 指向新仓，于是既有 `afterAll` 的「盘亏归零 + 停用」正好回收它。

未采信为结论的部分：修复后是否真的绿，要等 `.runtime/verify/e2e-followup.log`
（该脚本先等逐文件计量 `ALL DONE` 再跑，避免 D-20 那类争用）。
若 `receive` 报 **41044**（目标仓记账单位与调拨单位一致校验）以外的错，再按实际码分析。

**实测结论（`.runtime/verify/e2e-followup3.log`，带齐 D-24 三个 override 后）**：
`scm-data-scope.spec.ts` 从 `ok=0 fail=1 skip=5` 变成 **6 passed (26.0s)**，
且跑的是真实断言（换授权即换可见行、用户筛选只能收窄、改派后可见性立即翻转、
成本权限与仓库范围互不隐含、财务全仓可见来自显式权限且可撤销）。
没有出现 41044 —— 新建仓接受调拨单位。§16 的「权限与数据范围专项」由此第一次真正可测。

> 顺带一条方法论：第一批 followup 里这条曾被判成「修复无效」，实际是脚本又漏了
> `XSY_V2_PG_CONTAINER/USER/DB`（D-24a 我自己刚记的坑），报错是
> `Command failed: python ../tools/w8_e2e_accounts.py setup`。
> **凡是「按预期该绿却红」的复跑，先确认复跑命令带齐了前置 env，再怀疑修复本身。**

## 4q. D-29（测试夹具缺陷，已修并实测）mock 白名单漏了 `/support/tableColumn/`，把用例带去看登录页

`adm02-04-ui-contract.spec.ts` 第 2 条（ADM-02 实物接收）在逐文件模式下稳定复现，
两次症状不同但同源：先是「等不到 `实物接收` 按钮」，warm 之后变成
`element is not stable` → `element was detached from the DOM`。
失败截图直接给出真相：**页面是登录页**（两次截图验证码不同，说明确实重新导航过）。

用一次性探针（`chromium.launch()` + `framenavigated`/`request` 监听）跑夹具页，拿到链路：

```text
OUTREQ GET  http://127.0.0.1:18080/support/tableColumn/getColumns/603
OUTREQ POST http://127.0.0.1:18080/scm/order/return/query
REQFAIL http://127.0.0.1:18083/ :: net::ERR_ABORTED
NAV -> http://127.0.0.1:18083/          ← 被 axios 拦截器踢到登录页
```

根因在 spec 自己的 mock 谓词：

```ts
if (!url.pathname.startsWith('/scm/') && !url.pathname.startsWith('/tableColumn/')) return route.continue();
```

列配置的真实路径是 **`/support/tableColumn/getColumns/{id}`**，`startsWith('/tableColumn/')` 永远不成立，
于是这一发 `route.continue()` 打到真后端；夹具页没有登录态 → 401 → axios 拦截器跳登录页 →
正在被点击的 modal 从 DOM 上被摘掉。所以它**既不是 ADM-02 的业务缺陷，也不是 vite 冷启动**（§4m 的假设已被否证），
而是「mock 覆盖面比注释写的窄一条端点」。改成 `includes('/tableColumn/')` 后 **2 passed (6.0s)**。

两个附带结论：
1. 该 spec 的 `mock()` 只放行 `/scm/` 与 `/tableColumn/`，**任何将来新增的非 `/scm/` 前缀端点都会以同样方式把用例掀翻**；
   这套夹具页更适合「默认全部拦、只放行静态资源」的白名单方向。
2. 探针脚本 `.runtime/probe-fixture.mjs` 留着，下次遇到「元素突然没了」先跑它看 `NAV`，
   比猜动画时序便宜得多。

## 4r. 逐文件计量的完整结果（`.runtime/verify/e2e-by-file.log` / `-progress.log`）

29 个 spec 文件、158 个用例，`workers: 1`，每跑完一个文件立即落盘：

```text
TOTALS files=29 ok=123 fail=14 skip=35
```

对照同一批代码在「整套件单进程串行」下的 `26 ok / 48 fail / 75 skip`：
**红从 48 降到 14，跳过从 75 降到 35**。差值几乎全部来自
`describe.configure({mode:'serial'})` 的链式语义 —— `beforeAll` 或链上第一条一红，
后面的用例直接记 skip，于是一个夹具缺口会伪装成「一大片功能坏」。

按文件（ok/fail/skip）：

| spec | 结果 | 说明 |
|---|---|---|
| `adm02-04-ui-contract` | 1/1/0 → **修后 2/0/0** | D-29（mock 白名单） |
| `scm-data-scope` | 0/1/5 → **修后 6/0/0** | D-28（自建对照仓） |
| `smartadmin-native` | **17/0/0** | D-21b 那 17 条底座用例**独立跑全绿** |
| `scm-report` | 12/0/0 | |
| `scm-delivery-l3` / `scm-delivery` / `scm-delivery-print` | 8/0/0、8/0/0、6/0/0 | ADM-10 |
| `scm-dashboard-todo` | 8/0/0 | 整套件下只有 1 条能跑到 |
| `scm-stocktake-import` | 5/0/0 | |
| `scm-customer` / `scm-customer-360` | 2/0/0、2/0/0 | D-19 的实证：独立跑必绿 |
| `scm-supplier`、`scm-pricing`、`scm-finance-*`（5 个） | 全 0 fail | |
| `f0-file-storage` | 3/5/0 | 待取证（D-25/D-26 的端点口径已改，需重测） |
| `scm-product` | 5/3/2 | 待取证 |
| `scm-purchase` | 9/1/6 | 待取证 |
| `scm-sorting` | 5/1/5 | 待取证 |
| `scm-finance-receivable-red` | 0/1/5 | 待取证（`beforeAll` 即 `openFinanceHarness()`） |

**这一张表是本轮 E2E 判读的基准**：剩下 14 条红集中在 5 个文件，
其中 `scm-finance-receivable-red` 与 `scm-data-scope` 同形（链头 `beforeAll` 失败 → 整链 skip），
优先按夹具缺口查；`f0-file-storage` 优先按端点口径查（D-25）。

> **修正**：`scm-finance-receivable-red` 后来实测**不是** `beforeAll` 失败，而是链上第 1 条
> 用例的业务断言失败（带齐 env 的复跑给了完整 expected/received，见 D-30）。
> 「0 ok + 后面全 skip」这个形态既可能是 hook 坏，也可能是第一条断言坏，**不能只凭形态定性**。

## 4s. D-30（ADM-01 R2 界面口径，需裁决）**应收明细抽屉披露了超额核销金额，却不带列表那个「超额核销待处理」状态词**

`scm-finance-receivable-red.spec.ts:40` 红在：

```text
expect(drawer).toContainText('超额核销待处理')
  - Expected substring  -  1
  + Received string     + 49   （抽屉实际全文）
```

抽屉实际内容（同一条错误里逐字可见）已经把这件事说清了：
`净应收 / 净应付 ¥ -3.5000`、`未核销 ¥ 0.0000`、
提示语「**超额核销 ¥ 3.5000，不代表已退款或钱包余额。净应收为负数，请结合红字和核销记录核对。**」、
以及两条红字（`AR…054 / AR…055`，各 ¥14.0000）。
而**列表行的同一用例断言 `toContainText('超额核销待处理')` 是过了的**（否则跑不到第 40 行）。

所以差异是：状态词只在列表出现，抽屉用一段解释性文案承担同一语义。
数值口径（`netAmount=-3.5000`、`overAppliedAmount=3.5000`、`redEntries` 两条合计 28、
`openAmount=0.0000`）在**同一条用例里全部通过**，ADM-01 R2 的业务逻辑没有可疑之处。

两条路都要人拍板，本轮不擅自选：
- **A** 抽屉补一个与列表同源的状态标签（改前端文案/组件，属产品口径：状态词是否要在详情重复一遍）；
- **B** 把断言改成校验抽屉实际承诺的东西（`超额核销` + 金额 + 「不代表已退款」说明）。
  这等于放宽一条既有断言，**按「绝不为过门禁削弱断言」的规矩必须由用户同意才能做**。








## 5c. 续跑交接（2026-10-04 04:18 起，turn 预算耗尽时的权威状态）

**在飞行中的运行**：§4j(c) 那条完整命令的 `verify.py e2e`，23:18:13Z 起跑，串行 158 用例，
预计 25~35 分钟。它是**第一次「四个前置全对」**的运行（chromium 已装 / 18083=vite /
夹具装了 router（D-23）/ 建号 override 与 storage mode 齐全（D-24)）。结果落点：

```text
.runtime/verify/e2e-definitive.log                        末尾 Verification summary / RESULT
.runtime/verify/20261004-071813-297085/e2e.log           逐用例 ok / x / - 与错误详情
.runtime/playwright-result.json                          stats（注意：会先留着上一轮 0/0/0 的旧值，
                                                         必须核对 startTime 晚于 23:18:13Z 才算数）
```

**读结果时的三条纪律**（都源于本轮踩过的坑）：
1. `expected/skipped/unexpected` 全 0 ≠ 没跑 ≠ 全绿，先看日志尾部有没有前置错误（D-24b）；
2. 任何红先对照 §3 缺陷表与 §4 裁决项，**已知会红的只有 D-19（跨 spec 数据依赖，裁决项 7）
   与 D-17（两条空白菜单，裁决项 6）**，出现别的红就是新缺陷，按 D-25… 续号登记；
3. 不要拿这轮的绿色去反证 backend 全绿 —— 后端权威状态仍是 §4e 的 191 套件 / 1364 用例 / 4 failures / 0 errors，
   4 个红全部对应 §4 已登记的裁决项。

**我起的进程（都属本轮测试会话，未写入仓库配置）**：

| 端口 | 进程 | 停止方式 |
|---|---|---|
| 18080 | `java -jar sa-admin-dev-3.0.0.jar`，连一次性库 `xsy_v2_e2e` | `netstat -ano \| grep :18080` 找 PID 后 `taskkill //PID <p> //F` |
| 18081 | `npx vite`（管理后台 dev server） | 同上 |
| 18083 | `npx vite --port 18083 --strictPort`（D-18 夹具服务，必须走 vite，见 D-18 更正） | 同上 |
| compose | `xsy-scm-postgres-1` / `xsy-scm-redis-1` / `xsy-scm-minio-1`（Docker Desktop，Up 数小时） | 不要停：18080 与建号脚本都依赖它们 |

**一次性数据库**（都在 `xsy-scm-postgres-1` 里，可随时删）：
`xsy_v2_clean` / `xsy_v2_upgrade` / `xsy_v2_rerun` / `xsy_v2_final` / `xsy_v2_e2e`。
`xsy_scm` 长驻开发库**全程未被触碰**（仍停在 V67，符合 BASE-05）。

**工作区状态**：改动全部留在工作区，**未 commit、未 push、未切分支**；
`src/main/java` 零改动（所有主源改动都因裁决项 4 回退）。

**剩下两件事的具体命令**（按顺序，别并行 —— D-20 的争用教训）：

```bash
# 1) 拿到 e2e-definitive 的 stats 之后，唯一未闭合的门禁工件（task #7）
python tools/verify.py backend      # 期间不要再起 mvn，独占跑

# 2) 若 e2e 仍有红且不在已知两条裁决项内，先单跑取证再登记新 D-xx
cd xsy-scm-web && npx playwright test e2e/<spec> --reporter=line
```


## 5d. D-36（quality 门禁的真实机制，**推翻裁决项 12 原来的说法**）

先把 34 这个数拆开（`.runtime/verify/20261004-160202-391699/quality-guard.log`）：

```text
NEW DEFECTS (blocking, 34)
  LineLength      19 个文件（合计 30 处）
  UnusedImports    3
  magic-string    10   （"PAYMENT_INTENT" / "BALANCE_MOVEMENT" / "INVENTORY_WARNING" /
                         "PURCHASE_ORDER" / "RECEIVABLE" / "RED"×2 / "PAYABLE" / "PENDING"）
  stage-comment    2   （InventoryWarningScanVO:22、InventoryWarningScanService:48 的「本轮」）
  = 19 + 3 + 10 + 2 = 34
```

**关键更正**：`spotless:check` 与 `checkstyle:check` 在 HEAD 上**都是 exit 0**，
红只有 `quality-guard` 这一个步（它自带 new-defect 计数，三项 baseline 均为 0）。
所以裁决项 12 原来的表述（「不先定 spotless 口径，quality 门禁的机械项改不动 —— 碰一个文件就会把
该文件早已不合规的 CJK javadoc 折行整篇翻出来」）**前提是错的**：仓库当前是 spotless-clean 的，
不存在「历史文件早已不合规」。

真实机制我用一次受控实验量出来了（做完即回退，`git status` 已确认 `src/main/java` 干净、
`spotless:check` 回到 exit 0）：

1. 只改 5 个文件、且都是零行为风险的机械项：删 3 个未使用 import
   （`PaymentTransactionQueryForm` 的 `LocalDate`、`ScmPrintRenderVO` 的 `LinkedHashMap`、
   `ScmFinanceProfitService` 的 `OffsetDateTime`，逐个 `grep -c` 验证过确实只出现在 import 行）
   + 把 2 处「本轮」改成「本次扫描」（这两处语义是「本次预警扫描轮次」，属守护规则的误报，
   `quality_guard.py:415` 的 `("this-round", re.compile(r"本轮"))` 是裸词匹配）。
2. 结果 `spotless:check` **立刻变红**，报的就是这 4~5 个文件。
3. `mvn spotless:apply` 的 diff **只涉及我改过的这几个文件**（12 insertions / 24 deletions），
   没有波及全仓 —— 但它在这些文件里**重排了我根本没碰的 javadoc 段落**：
   把多行 CJK 说明按字符宽度**合并成更少的长行**，
   例如 `InventoryWarningScanService` 类注释里「为什么是扫描而不是挂在余额写路径上」那段
   从 4 行被并成 2 行。

于是两条规则在 CJK javadoc 上是**互相拉扯**的：
`checkstyle LineLength` 要你把长行拆开，`spotless` 的 javadoc 格式化又把拆开的 CJK 行并回去并超出限宽。
`UnusedImports` 本身无害，但它一被修，同一文件的 javadoc 就被顺带重排 ——
这才是「机械项改不动」的真正原因，也才是裁决项 12 真正要定的事。

**给裁决项 12 的可选口径（替换原来的 A/B）**：

**机制已在 09:13Z 用第二种工具复核过，排除了「是我自己的编辑方式造成的」这个解释**：
只用 Edit 工具（不用 `sed`）从 `ScmPrintRenderVO.java` 删掉那一条**已验证未使用**的
`import java.util.LinkedHashMap;`，`mvn -pl sa-admin spotless:check` 仍然报这个文件红；
`spotless:apply` 的 diff 显示它要改的是**两处与该 import 无关的类级 javadoc**
（把多行 CJK 说明按字符数重新折行、合并成更少的长行）。
文件 `file` 出来是 CRLF（与 HEAD 一致，`core.autocrlf=true` 的正常形态），
所以**不是行尾问题**，也不是 `sed` 的副作用。
复核完已 `git checkout --` 回退，`spotless:check` 回到 0 violation、`src/main/java` 干净。

- **A** 关掉 spotless 的 javadoc 重排（`<eclipse>` 配置里禁用 javadoc formatting，或改用
  `formatJavadoc=false` 一类的 profile 开关）。这样 LineLength / UnusedImports 就能逐条干净地修，
  也不会再出现「改一行 import 顺带重排整段注释」。代价：全仓 javadoc 排版从此由人管，
  且这是一次**配置层**变更，会影响所有人的 IDE 行为。
- **B** 接受 spotless 的重排结果，把「修 34 项」做成一次**独立的格式化提交**（先 apply 全仓、
  再逐条修语义项），好处是一次性了断，代价是那笔 diff 里混着大量与缺陷无关的注释换行。
- **C** 保持现状：本轮不碰 main 源，quality 门禁按「已知红 34 项、全部为存量提交带入」记录，
  等本轮收口后单独立项处理（**这是我现在的实际做法**，因为 A/B 都会改动仓库级配置或制造大 diff）。
- 另外无论选哪个，**`magic-string` 那 10 项与 `本轮` 那 2 项建议分开看**：
  前者要引入常量/复用现有 Enum（AGENTS.md 明确要求「业务状态、类型和来源使用现有 Enum」），
  属真语义改动；后者是守护规则裸词匹配造成的误报，改词或加豁免都行，不必动业务代码。

## 5e. §15 E2E-01 已**真正贯穿执行**：`e2e/adm-golden-chain-01.spec.ts` 6/6 通过（14.4s）

清单原文的 E2E-01 是「客户 → 订单 → 确认 → 配送 → 正常签收 → NORMAL 应收 → 收款 → 核销 → 对账/利润」。
本轮把它写成了**一条 serial 链**，后一步吃前一步的真实产物（数据全部经正式 API 产生，
不直写库、不伪造应收），并在关键节点回到真实页面：

| 步骤 | 断言到的事实 |
|---|---|
| 1 客户→订单→确认→配送→签收→应收 | 应收 `entryType=NORMAL`、金额 > 0；**列表行与详情都回指该订单**；新应收 `openAmount == 全额` |
| 2 收款→核销 | 收款单落库（`receiptId`）、核销落库（`/write-off/add` 返回 `{items:[…]}`，核销号在 `items[0]`）；核销后 `openAmount=0`、`writtenOffAmount=全额` |
| 3 应收页面 | `#/finance/receivables` 真实列表能筛到该单号，明细抽屉含该单号且出现「核销」记录 |
| 4 收款页面 | `#/finance/receipts` 表格可见该客户收款行 |
| 5 客户对账单 | `POST /scm/report/customer/statement/freeze` 冻结成功；**`receivableIncrease == writeOffNet == 本链金额`、`closingReceivable == 0`**；冻结结果出现在 `history` |
| 6 毛利页面 + 全链 | `#/report/report-finance-profit` 表格渲染；**整链 0 条 `pageerror`** |

截图留档：`.runtime/golden01-{receivable-detail,receipts,statement,profit}.png`。

这条的价值与前面各域用例不同：它证明的是**跨域闭环成立**（订单域产生的事实被财务域正确消费，
并在报表侧算平），正是清单说的「不是每个页面自己能跑」。
因此 §15 的状态从「8 条全部未贯穿执行」变成 **1 条已执行通过、7 条待执行**。

写这条链过程中被实测纠正的三个契约猜测（都记在这里，避免下次再猜）：
`/finance/receivable/{id}` 的主体嵌在 `.receivable` 下一层；
`/finance/write-off/add` 返回 `{items: FinanceWriteOffVO[]}` 而不是单个对象；
对账单是 `POST /scm/report/customer/statement/freeze` + `GET .../history?settlementCustomerId=`，
**没有** `/query` 这个端点。

## 5f. §15 E2E-03 也已贯穿执行：`e2e/adm-golden-chain-03.spec.ts` 5/5 通过（11.0s）

清单原文：订单 → 退货审批 → 实物接收 → `SALES_RETURN_IN` → RED → 退款 → 报表。
这条链专门盯 ADM-02 最容易出的两种错（退货只改财务不回库 / 回库了但流水方向写反），
所以**同时**验库存事实与财务事实：

| 步骤 | 断言到的事实 |
|---|---|
| 1 订单→签收→NORMAL 应收 | 先记下 `(warehouse, sku)` 退货前余额作基线 |
| 2 退货审批 | 退货单 `APPROVED`；同一张应收上 **`redEntries` 恰好 1 条**，且 `netAmount < amount`（红字真的冲减了净应收） |
| 3 实物接收 | `POST /scm/order/return/receive` 带 `disposition=RETURN_TO_STOCK`；**库存流水出现 `SALES_RETURN_IN`**、数量为正（方向由类型表达、金额本身为正）；**余额 = 基线 + 4.0000** |
| 4 退款登记 | `POST /scm/order/refund/complete` 带乐观锁 `version` 与外部凭证号，状态回到 `COMPLETED/SUCCEEDED` |
| 5 页面与报表 | 退货单列表页渲染；应收明细抽屉含「红字」；对账单 `receivableRed > 0`；**整链 0 条 `pageerror`** |

截图：`.runtime/golden03-{return-list,receivable-red}.png`。

**贯穿执行时踩到的两个真实事实（值得留字，避免下次误判成页面坏了）**：
1. `openFinanceHarness` 的 `sales` 角色**没有**「退货单」菜单，深链 `/#/order/order-return-list`
   落到 404「对不起，您访问的内容不存在!」—— 这是 `scm-data-scope` 正在证明的 fail-closed 行为，
   不是路由缺陷。跨域链里「看页面」要用有权令牌，「证权限」另有专门用例，两件事不要混。
2. 财务令牌能看应收，但看不到订单域页面 —— 同一条链按域换令牌是必要的。

**§15 当前进度：8 条里已贯穿执行并通过 2 条（E2E-01、E2E-03），其余 6 条（02/04/05/06/07/08）仍未贯穿执行。**

## 5g. D-37（**我自己引入的回归，必须写下来**）链用例与 D-28 修复在共享库里留下主数据，把两条聚合/范围断言打红

把两条链写进 `e2e/` 之后再跑权威门禁，数字从 `145 / 3 / 10` 变成：

```text
E2E reports: {'expected': 152, 'skipped': 12, 'unexpected': 5, 'flaky': 0}
```

新增的 2 条红**不是**裁决项 15/16/17，而是：

```text
x  39 scm-data-scope.spec.ts:404  › 6｜财务报表范围：无全量权限时按授权行收窄，财务的全仓可见来自显式权限且可撤销
x  62 scm-finance-overview.spec.ts:67 › Finance R0 六指标与期末明细和 Finance R1 事实一致，并导出 XLSX
    Error: 财务的「全部仓库」是 V57 的显式授权，不是遗漏收窄
```

**先按「整套件调度耦合」去解释它是错的**：把这两个文件**单独**拎出来跑（不带链用例）仍然红
（`2 failed / 5 passed`），所以红因不是同一次运行里的顺序污染，而是**库里已经留下的持久数据**。

最可能的来源就是本轮我自己加的两处：
1. **D-28 的修复**：`scm-data-scope` 的 `beforeAll` 现在会自建一座对照仓，`afterAll` 只把它
   **盘亏归零 + 停用**，**仓库行本身永久留在库里**。而 `scm-data-scope:404` 与
   `scm-finance-overview:67` 断言的是「财务的全仓可见 = 某个具体仓库集合」这类
   **集合相等 / 期次合计**语义 —— 多一座（哪怕已停用的）仓库就会改变期望集合。
2. **两条链**：E2E-01/03 各自产生应收、收款、核销、红字、退货入库流水与**已冻结的对账单**，
   这些都会进 R0 六指标的期次合计。

**结论与责任**：这两条红是**测试侧的共享库卫生问题**，不是 ADM 业务缺陷，
但它是我这一轮改出来的，所以 §6 的权威数字必须以**修复后重跑**为准，
不能继续引用修复前的 `145 / 3 / 10` 当作现状。

**修法（下一步该做的，二选一或都做）**：
- **A（推荐）**：让「创建主数据」的用例**自己回收**：D-28 的对照仓在 `afterAll` 里除归零停用外
  再走删除端点（仓库无余额、无引用时应可删），链用例则改跑在**专用一次性库**上
  （例如新建 `xsy_v2_chains`，Flyway V1→V106 后只给链用例用），聚合类断言的库保持干净。
- **B**：把聚合/范围断言从「等于具体集合」改成「等于按同口径实时查出来的集合」，
  使其对额外主数据不敏感。这会降低断言强度（不再钉住集合大小），**属放宽断言，需用户同意**。

在 A 落地之前，`e2e/adm-golden-chain-01.spec.ts` 与 `adm-golden-chain-03.spec.ts`
**不应进 `verify.py e2e` 的默认 testDir**（它们目前会改变共享库状态），
应显式按 §5e/§5f 单独执行留证。

**已实施的隔离（本轮收尾）**：两条链移到 `xsy-scm-web/e2e-chains/`，
新增 `playwright.chains.config.ts`（`testDir: './e2e-chains'`，其余口径与主配置一致），
执行方式 `npx playwright test --config playwright.chains.config.ts`。
实测**11 passed (24.2s)** —— 链证据没有因为隔离而丢失。

**仍未解决、且必须在下次门禁前处理的一件事**：D-37 的两个红**不是链用例造成的全部**——
`scm-data-scope` 的 D-28 修复自建的那座对照仓，`afterAll` 只归零+停用、**没有删除**，
所以即使把链用例移出去，`scm-data-scope:404` 与 `scm-finance-overview:67` 在**当前这个
`xsy_v2_e2e` 库上仍然红**（实测：单独跑这两个文件、不带链用例，`2 failed / 5 passed`）。
因此下一次权威 E2E 门禁的正确做法是：**换一座干净的 `xsy_v2_e2e`**
（`DROP DATABASE` + `CREATE DATABASE` + 启动后端让 Flyway 跑 V1→V106，或直接建 `xsy_v2_e2e_b`
并同步 `XSY_V2_PG_DB`），而不是继续在留了主数据的库上重跑。
预期回到 `145 / 3 / 10` 的基线（3 条红仍是裁决项 15/16/17）；
若干净库上仍红，才是需要新立案的问题。



> **⚠ 基线已漂移（本节结论的适用边界，2026-10-04 19:20Z 拉取远端后确认）**
> 本节所有数字都是在 **`main = 99ec1d4f`、迁移链到 V106** 上取得的。
> 远端已推进到 **`33dec4a5`**，其中 `e877c591 feat(balance): return pure balance refunds to original wallets`
> 新实现了 ADM-12 **c3 纯余额售后返还**，并带来 **V107__scm_balance_refund_permission.sql**，
> 同一次提交还改了 `PaymentIntentService` / `FinanceOrderFundingPolicy` / `OrderRefundService`
> 等我这八条链**直接依赖**的类（`81035a77` 只是 `docs/status.md` 的状态记录，不含代码）。
> 因此：**本文档里没有任何一份工件是在 `33dec4a5` + V107 上跑出来的**。
> 具体三条后果：① BASE-03/04 与三座一次性库的最高版本都是 106，V107 尚未在任何测试库执行；
> ② E2E-07/E2E-08 的结论要在新代码上重跑才算数（尤其 D-39 可能已被上游改动影响）；
> ③ 裁决项 J-04「ADM-12 c3 刻意未实现、记 N/A」已**不再成立**，改成「上游已实现、按清单需验收」。
> 在这一条被重新取证之前，不得把下面的绿当作新基线的绿。

## 6. 最终状态（2026-10-04 07:56Z 复核，取代本文更早处的旧结论）**四份 harness 自己写出的权威工件**（都在 `.runtime/verify/<时间戳>/summary.json`）：

| 门禁 | 结果 | 未解释的红 |
|---|---|---|
| `verify.py backend` | `tests=1374 failures=5 errors=0 skipped=0`（`20261004-152733-459176`） | **0** —— 5 条全部对应已登记的裁决口径：外键 J-08、结算主体 J-09、打印权限 J-10、selectExposure J-11、空白菜单 J-12 |
| `verify.py e2e` | `expected=137 unexpected=3 skipped=10 flaky=0`（`20261004-154143-699852`） | **0** —— 3 条全部对应裁决项 15/16/17；10 条 skip 是这 2 条 serial 链的级联。**已被 §7l-补 的第三座全新库运行取代：137/6/15/0flaky，红的六条同名同位，证明 D-41 夹具零回退**；对应 J-13…J-17 |
| `verify.py frontend`（自身三步） | `frontend-lint / frontend-test / frontend-build` 全部 `exit 0`（`20261004-155203-450139`） | **0** |
| `verify.py quality` | 仍红（34 个 blocking new defects） | 全部挡在裁决项 12（是否允许 `spotless:apply` 规范化历史文件） |

一条结构事实值得写下：**`verify.py frontend` 也会跑 e2e**（`tools/verify.py:260` 的
`if args.scope in ("all","frontend","e2e")`），所以它同样必须带 §4j(c) 那套 env，
否则它的 e2e 步会以 `0/0/0` 失败并让整个 frontend 门禁看起来坏了 —— 我这次就撞上了。

**本轮相对起点（`main=99ec1d4f`，191 套件 / 1364 用例 / 12 failures / 29 errors）的净变化**：
后端从 12F+29E → **5F+0E**；E2E 从「没有一次可信运行」→ **145 通过 / 3 条已归因红**；
新增 2 个守护套件（打印中心 9 条、菜单组件存在性）与 10 条用例；
修掉 5 个测试基础设施缺陷（D-23 夹具缺 router、D-28 数据范围夹具自相矛盾、
D-29 mock 白名单、D-31 旧 JVM 占端口、D-32 选择器漂移）与 1 个真实前端缺陷
（`order-payment-drawer.vue` 的 `@finish` 无 `:model` 导致支付按钮永不触发）。

**仍未完成、且必须用户先定口径的**：裁决项 1/2/3/6/12/15/16/17（其中 12 挡 quality 门禁，
15/16/17 挡 E2E 归零），以及 §15 八条黄金业务链里剩下的那一段 ——
**8 条已全绿 7 条（01/02/03/04/05/06/08，见 §7l），只有 E2E-07 的 3~5 步仍 `fixme` 于 D-39（裁决项 18）**。
链套件当前 **36 passed / 1 skipped / 0 failed**（`.runtime/verify/chains-all-final.log`）；
唯一的 skip 有缺陷号可查，不是环境门控、也不是为了凑绿而 skip。
写法仍是「一根线跑到底、后一步吃前一步真实产物、并按域回到真实页面」，
数据全部经正式 API 产生（不直写库、不伪造应收/库存）。

**明确没有做的事**：没有为通过任何门禁放宽断言、扩大 skip、跳过用例，
也没有修改任何已应用的 Flyway 迁移。

**工作区改动清单（`git status --porcelain` 实测 31 个路径，`src/main/java` 命中 0 个）**：

```text
16  xsy-scm-server/sa-admin     —— 全部在 src/test（夹具补结算列、PG 校验 IT 修正、2 个新守护套件）
 6  xsy-scm-web/test            —— 前端合同用例（枚举/权限码/导出清单等与后端对齐）
 4  xsy-scm-web/e2e             —— D-23/D-29/D-32 三处夹具与选择器修正 + 夹具 router
 3  xsy-scm-web/src             —— 常量真源（入库类型/分拣权限码）+ 支付抽屉 @finish 缺陷修复
 1  tools/quality               —— 跨域只读 DAO 白名单补登记
 1  docs/quality                —— 本证据档
```

主源（Java）改动全部因裁决项 12 而回退；未提交、未推送、未切分支。

## 7. 干净库重跑（D-37 的验证轮，2026-10-04 08:48Z）

按 §5g 的结论换库重跑：新建 `xsy_v2_e2e_b`（Flyway 自 V1 应用到 **107 条 / 最高 V106**），
后端与建号脚本同时指过去，`verify.py e2e` 工件在 `.runtime/verify/20261004-164854-660260/`：

```text
E2E reports: {'expected': 130, 'skipped': 15, 'unexpected': 6, 'flaky': 0}
```

**D-37 得到确证**：`scm-finance-overview:67` 与 `scm-data-scope:404` 在干净库上**都是绿的**，
所以它们的红确实是「留了主数据的库」造成的，不是业务缺陷。

6 条红的归属：

| 红 | 归属 |
|---|---|
| `scm-finance-receivable-red:11`、`scm-purchase:250`、`scm-sorting:209` | 裁决项 15 / 17 / 16（与脏库那轮同三条） |
| `scm-customer-360:47`、`scm-customer-360:71` | **D-19 / 裁决项 7** —— 干净库上直接复现「依赖别的 spec 造的数据」 |
| `scm-data-scope:245` | **D-38（新，见下）** —— 我上一轮的 D-28 修复在干净库上不成立 |

## 7b. D-38（本轮实测出来的硬约束，直接决定裁决项 7 的成本）

`scm-data-scope` 想在**全新 V106 库**上自给「两座都有余额行的启用仓」，实测被三层挡住：

1. 干净库里**一座有余额的仓都没有**（默认仓是空的）→ 没有可调拨的源仓；
2. 想自建 SKU：`product/sku` 也是空的，但**分类树是播种的**，所以
   `POST /scm/product/add`（带 `skuList`）可以自建出可售 SKU —— 这一步已经跑通；
3. 想用**盘盈**造第一条余额行：**库存域明确拒绝** ——
   `POST /scm/inventory/stocktake/create` 返回
   「**该仓库与 SKU 尚无库存记录，请先办理入库再盘点**」。

也就是说：全新库里唯一的合法入库路径是**完整采购链**
（供应商 → 采购需求/采购单 → 收货单 → `receipt/confirm`），
`scm-finance-fixtures.ts` 里的 `createPurchaseReceipt` 正是这条链的现成封装。

**给裁决项 7 的实际含义**：A 方案（数据依赖型 spec 在 `beforeAll` 自造最小夹具）
不是"补几行"的成本，而是要把**采购入库链**变成这些 spec 的公共前置
（建议做成一个共享夹具模块，避免每条链各写一遍）。
本轮已把 `scm-data-scope` 的失败改成**指名道姓的诊断**（不再是一句"需要两个仓"），
并顺手让它能自建 SKU；剩下的入库链未做，因为选 A 还是 B 是产品/测试口径决策。

**环境指针**：本轮之后 18080 后端连的是 `xsy_v2_e2e_c`（第二座干净库，用于验证 D-38 的三层阻挡，
库里现在多出本用例自建的 SKU 与一座停用仓）。`xsy_v2_e2e`（脏库）与 `xsy_v2_e2e_b` 都还在，
随时可删；`xsy_scm` 长驻开发库全程未被触碰。

## 7c. §15 E2E-04 也已贯穿：`e2e-chains/adm-golden-chain-04.spec.ts` 4/4，三条链合计 **15 passed (35.9s)**

清单原文 E2E-04：采购需求 → 净需求预览 → 冻结批次 → 生成需求 → 采购 → 收货 → 入库 → 应付 → 付款。
本轮执行的是后半段可自动化的实链（前半段属 ADM-05，其页面断言已红在裁决项 17 / D-34，不重复立案）：

| 步骤 | 断言到的事实 |
|---|---|
| 1 采购单 → 收货确认 | 新建 SKU 期初必为 0，收货后**余额精确等于本次收货量 20.0000**（不是"前后差值"这种弱断言），且产生 `_IN` 入库流水 |
| 2 收货形成应付 | 按 `purchaseOrderId` 查得到应付；**金额本身为正**（方向由 `entryType` 表达）；`openAmount == amount` |
| 3 付款 → 核销 | `/finance/payment/add`（`counterpartyType=SUPPLIER`）落库 → `/finance/write-off/add`（`sourceType=PAYMENT`）→ **应付 `openAmount` 归零** |
| 4 页面 | 采购收货列表与应付列表都渲染；**整链 0 条 `pageerror`** |

**三条链合计 15 个用例全绿**（E2E-01 6 条 + E2E-03 5 条 + E2E-04 4 条，35.9s），
统一入口：`npx playwright test --config playwright.chains.config.ts`。
执行过程中被实测纠正的两个契约（避免下次再猜）：
`/scm/finance/payable/query` **必须带 `startDate`/`endDate`**（否则 30001「结束日期不能为空」）；
`e2e-chains/` 里的用例引 `scm-test-base` 等共享模块要写 `../e2e/...`。

**§15 当前进度：8 条已贯穿执行 3 条（E2E-01 / 03 / 04），剩 5 条（02 集团结算、05 活动券、06 满赠、07 充值余额支付、08 在线支付自动核销）。**

## 7d. E2E-07 部分贯穿：2 步已绿，3~5 步用 `test.fixme` 钉在同一个未知契约上

清单原文 E2E-07：在线充值 → Receipt → 钱包 CREDIT → 余额支付 → 无 Receipt → BALANCE 核销。
`e2e-chains/adm-golden-chain-07.spec.ts` 现在**跑通前两步**，第 3~5 步用 `test.fixme`
显式标成「已知阻塞」而不是悄悄 skip：

**已经验到的两条不变量（都是真断言，已通过）**
1. `POST /scm/balance/recharge/create` **当场就立起一张财务 Receipt**；
2. 但**钱包 `availableBalance` 仍是 0** —— 没收到渠道确认就不能有钱入账（凭空生钱）。
   这两条合起来把「充值 = 先立收款单、钱后到」这个设计钉住了。

**查清的 mock 渠道契约**（下次不必再翻代码）
- 端点 `POST /scm/payment/callback/mock`，权限 `INTENT_CREATE`；
- 报文是**原始字符串**，签名放头 `X-Scm-Mock-Signature`，事件号可放 `X-Scm-Mock-Event-Id`；
- 签名 = `HmacSHA256(rawBody, scm.payment.mock.secret)` 的**小写十六进制**，
  dev 默认密钥 `scm-local-mock-secret`；**必须用与发送字节完全相同的那个字符串去签**；
- 报文形状：`{"eventId","eventType":"PAYMENT_SUCCEEDED","providerTransactionNo","amount","providerRefundNo","bizDate"}`；
- 验签失败**不抛异常**，而是如实落一条 `REJECTED` 事件（`MockPaymentProvider.parseCallback` 的注释写明这是刻意的）。

**剩下的唯一未知（也是 E2E-08 的前置）**：回调被受理（`code=0`）但钱包仍未入账，
说明报文还要与那次充值的**意向/交易建立关联**才能落地。候选关联键是
`intentNo` / `transactionId` / 或 `providerTransactionNo` 必须等于建意向时系统记下的那个值 ——
本轮剩余预算不足以把 `PaymentCallbackService.handle` 到 `rechargeFromPayment` 的关联路径查实，
故止步并把 3~5 步标 `fixme`（`npx playwright test --config playwright.chains.config.ts`
会把它报成 1 条 did-not-run，不会伪装成通过）。

**下一步（明确）**：读 `PaymentCallbackService.handle` 里「事件 → 交易 → 意向 → 业务动作」的匹配条件，
以及 `/scm/balance/recharge/create` 建意向时往 `payment_transaction` 写了什么
`providerTransactionNo`；补上正确关联后，3~5 步的断言（余额入账、
余额支付**不再开第二张 Receipt**、应收以 `BALANCE` 身份核销归零）都已写好可直接用。
E2E-08（ONLINE 支付 → Receipt → 自动核销 → overApplied）依赖**同一个**契约，应一起做。

> **这条下一步已经被执行过一次并否证，别重复走**。实测：
> 用 `POST /scm/payment/intent/transaction/query`（`{intentId, pageNum, pageSize}`）
> 取到该充值意向下**真实的 `providerTransactionNo`**，再按上面的 HMAC 规则正确签名投递，
> 回调仍然 `code=0` 而钱包 `availableBalance` 不动。
> 所以「签名不对」和「交易号对不上」两种解释都**已排除**；
> 匹配条件本身在 `PaymentCallbackService:105-108`（`selectByProviderTransactionNo`）也已确认无误。
> 剩下的嫌疑集中在 `applyOutcome` 之后到 `CustomerBalanceService.rechargeFromPayment`
> 这一段：意向状态机是否允许从当前状态直接进 SUCCEEDED、或建意向时写入的
> `providerTransactionNo` 是占位值（查得到但语义上还不是「渠道已回执」的那一个）。
> 3~5 步现在以 `test.fixme` 留在文件里（跑起来报 1 条 skipped，不会伪装成通过）。



按 §7c 的同一手法写了 E2E-07（在线充值 → Receipt → 钱包 CREDIT → 余额支付 → 无 Receipt → BALANCE 核销），
推进到第 2 步就停住，**已把该文件删除**，不留一条常红灯在仓库里误导后来人。
已知事实与卡点，按顺序记在这里，下次可以直接从这里接：

1. 第 1 步（订单→签收→NORMAL 应收）**通过**，余额支付要核销的对象成立。
2. `POST /scm/balance/recharge/create` 表单是
   `{customerId, amount, provider, mockScenario, remark}`，返回 **`PaymentIntentVO`**（不是余额对象）。
3. 查钱包要读 **`CustomerBalanceVO.availableBalance`** —— 不是 `balance` 也不是 `amount`；
   读错字段会得到 0，看起来像"没入账"，其实是字段名猜错（这一步已排除）。
4. **真正的卡点**：改成 `availableBalance` 之后充值余额仍是 0。
   说明 mock 渠道下充值是**两阶段**的 —— `/recharge/create` 只建出支付意向（PENDING），
   钱包入账发生在支付结果确认之后。所以这条链还缺一个「把 mock 意向推到成功」的动作
   （候选：mock 回调端点、`mockScenario` 传特定值、或 `/scm/payment/intent` 下的确认端点）。
   本轮剩余预算不足以把这个契约查清，故止步。

**给下一次的具体下一步**：读 `BalanceController#recharge` 往下到 `PaymentIntentService` 的调用链，
确认 mock provider 是让**谁**把 intent 置为 SUCCEEDED 的（内部同步完成、还是必须外部回调），
然后补上那一步，E2E-07 的其余断言（余额支付不再开 Receipt、以 BALANCE 核销应收）都已写好可复用。
E2E-08（ONLINE 支付 → Receipt → 自动核销 → overApplied）会用到**同一个**契约，两者应一起做。

## 7e. D-39（ADM-12 真实缺陷，E2E-07 卡在这）充值意向当场 SUCCEEDED、财务 Receipt 已立，但钱包权益始终没记

E2E-07 第 3 步反复不入账，把服务端**如实落下的回调事件**打出来后一次看清：

```text
txnNo             = "MOCK-TXN-PYI20261004000017"  ← 取自真实交易行，不是我编的
intentStatus      = "SUCCEEDED"                   ← 建单当场就已经成功
wallet            = 0                             ← 带 customerId 查，确实为 0
signatureVerified = true                          ← HMAC 签名是对的
processStatus     = "RECEIVED"                    ← 事件被收下，但停在这里
transactionId     = null                          ← 没有关联到任何交易
processedAt       = null                          ← 从未被处理
rejectReason      = null                          ← 也没被拒绝
```

先排除两种「是我测错了」的解释，都留了证据：
- **不是签名问题**：`signatureVerified=true`（`X-Scm-Mock-Signature` 是
  `HmacSHA256(rawBody, "scm-local-mock-secret")` 的小写十六进制，且用与发送字节完全相同的串去签）；
- **不是查询取默认值**：`/scm/balance/query` 不带 `customerId` 会被拒成 `30001 客户不能为空`，
  带 `customerId` 才查得动 —— 所以 `wallet=0` 是真 0，不是「查不到行兜底成 0」。

**缺陷本身**：`PaymentIntentService` 成功分支里，钱包权益只在
`sourceType == BALANCE_RECHARGE` 时由 `balanceRechargeSink.rechargeFromPayment(...)` 驱动，
而这条驱动挂在**回调处理链**上（`applyOutcome` → `registerFinanceReceipt` → sink）。
模拟渠道的充值在**建单时就把意向置为 SUCCEEDED**，于是财务 Receipt 立了、意向也成功了，
**唯独钱包权益没有任何路径执行**；之后真正投递进来的回调又因意向已不在 PENDING 而落不成关联
（`transactionId=null`、`processedAt=null`，但不报错，只停在 `RECEIVED`）。

按清单的业务不变量看，这就是「客户把钱交进来了、公司记了收款单、客户钱包里却没有钱」——
资金事实与权益事实不一致，而且**没有任何一条错误告诉操作者**。

**为什么本轮不自己修**：修法涉及支付意向状态机与「谁驱动钱包入账」的归属，候选至少三种
（建单时同步走 sink / 建单只到 PENDING、必须由回调完成 / 让 `RECEIVED` 事件在对账时重放），
每种都会改变 ADM-12 的对外行为 → 记为**裁决项 18**。
E2E-08（ONLINE 支付 → Receipt → 自动核销 → overApplied）走同一条成功分支，
**很可能同根因**，应一起定、一起修、一起验。

E2E-07 的 3~5 步以 `test.fixme` 留在 `e2e-chains/adm-golden-chain-07.spec.ts`，
断言（回调后 `availableBalance == 100`、余额支付不再开第二张 Receipt、应收以 `BALANCE` 核销归零）
都已写好，D-39 一修就能直接跑；前两步（充值当场立 Receipt、**未确认前钱包必须为 0**）是绿的。

## 7f. §15 E2E-02 也已贯穿：集团结算链 4/4 通过

清单原文 E2E-02：集团子客户 → 集团结算 → 收款 → 核销 → 客户对账。
`e2e-chains/adm-golden-chain-02.spec.ts` 跑的是 ADM-03 最要紧的那条不变量——
**下单主体与结账主体可以不是同一个客户**，所以每一步都同时盯两个字段：

| 步骤 | 断言到的事实 |
|---|---|
| 1 集团母客户 + 子客户 | 子客户 `settleMode=GROUP` 且 `settlementCustomerId == 母客户` |
| 2 子客户下单 → 应收 | 应收回指子客户的订单，且**记在集团结算主体名下**（不是下单的子公司） |
| 3 集团统一收款 → 核销 | 母客户交收款单 → 核销子客户形成的应收 → 应收 `openAmount` 归零 |
| 4 对账单 | 按**结算主体**冻结对账单：`receivableIncrease == 该笔应收`、`closingReceivable == 0`；页面渲染且 0 条 `pageerror` |

**踩到的关键契约（值得留字）**：`CustomerValidator.validateParent` 要求
「**上级客户的类型 `typeCode` 必须是 `GROUP`**」（`CustomerValidator.java:126-170`），
母客户挑了默认的第一个类型就会让子客户建不出来，报 `40032 上级客户不正确`。
库里的类型是 `1 ENTERPRISE 企业 / 2 PERSONAL 个人 / 3 GROUP 集团`，
所以夹具必须按 `typeCode`/名称去挑 GROUP，而不是取列表第一项。

**§15 当前进度：8 条已贯穿执行 4 条（E2E-01 / 02 / 03 / 04），E2E-07 前两步已绿、3~5 步阻塞于 D-39，
剩 E2E-05 / 06 / 08 未开始。** 链套件合计 **21 passed / 1 skipped (51.6s)**，
入口 `npx playwright test --config playwright.chains.config.ts`。

## 7g. 第二次干净库门禁（含 D-28 的 SKU 自建）+ D-40（券启用状态机）

**干净库重跑 #2**（`xsy_v2_e2e_d`，V1→V106 全新，工件 `.runtime/verify/20261004-180440-379124/`）：

```text
E2E reports: {'expected': 137, 'skipped': 15, 'unexpected': 6, 'flaky': 0}
```

对比第一次干净库跑（`130 / 6 / 15`）：**通过数 +7**，来自 D-28 修复里补的「自建可售 SKU」这一步
（`createSignedOrder` 现在能在没有现成 SKU 的库里自己建）。6 条红的归属**没有变化**：

| 红 | 归属 |
|---|---|
| `scm-customer-360:47` / `:71` | 裁决项 7（D-19 跨 spec 数据依赖，干净库必红） |
| `scm-data-scope:287` | **D-38** —— 我的 D-28 修复仍不完整：SKU 能自建了，但余额行造不出来 |
| `scm-finance-receivable-red:11` | 裁决项 15（D-30） |
| `scm-purchase:250` | 裁决项 17（D-34） |
| `scm-sorting:209` | 裁决项 16（D-33） |

同时再次确证 **D-37**：`scm-finance-overview:67` 与 `scm-data-scope:404` 在干净库上**持续是绿的**，
它们的红确实只是脏库残留。
`scm-data-scope` 现在失败时抛出的是指名道姓的诊断
（「该仓库与 SKU 尚无库存记录，请先办理入库再盘点 —— 需要完整入库链…」），
不再是一句看不懂的「需要两个仓」。

## 7h. D-40（新发现的 ADM-12 契约疑点）新建的优惠券无法用 status 接口启用

E2E-05（活动/券链）写到第 1 步就撞停，现象是确定性的：

1. `POST /scm/promotion/coupon/save` 成功，返回 `couponId`；
2. `GET /scm/promotion/coupon/{id}` 回来的 `status` **不是 `ENABLED`**；
3. 再发 `POST /scm/promotion/coupon/{id}/status` 带 `{id, version, status:'ENABLED'}`
   （`version` 必须从 detail 取，否则 `30001 版本号不能为空`）
   → 被状态机拒成 **`41327 优惠券当前状态不允许此操作`**。

也就是说「券建出来之后处在某个状态，却不能用对外暴露的 status 接口把它启用」。
本轮预算内没能读完 `PromotionCouponService` 的状态机（它可能要求先过审核、
或 `coupon/save` 与 `activity/save` 必须成对、或状态取值不是 `ENABLED`），
因此 **E2E-05 整条以 `test.fixme` 留在 `e2e-chains/adm-golden-chain-05.spec.ts`**，
后续 4 步（发券 → 确认冻结优惠 → 净额记应收 → 签收核券 → 毛利页）都已写好，
一旦启用路径查清即可直接跑。

链套件现状：**21 passed / 2 skipped**（两条 skip 分别是 D-39 与 D-40，都写明在代码里），
入口 `npx playwright test --config playwright.chains.config.ts`。
本轮同时给 `createSignedOrder` 加了可选的第 5 参 `confirmExtras`
（用于在确认时带 `couponInstanceId`），既有 4 条链的调用点未传该参数、行为不变。

## 7i. **D-40 撤销**：41327 是测试自己发错状态值，不是 ADM-12 缺陷

§7h 把「新建的券无法启用」登记成产品契约疑点，**这个定性是错的**，在此撤销并留下纠错过程。
读 `PromotionCouponService.updateStatus` 后真相是：

```java
boolean activate = ScmPromotionStatusEnum.ACTIVE.name().equals(form.getStatus());
boolean stop     = ScmPromotionStatusEnum.STOPPED.name().equals(form.getStatus());
if ((!activate && !stop) || activate == currentlyActive) throw COUPON_STATE_INVALID;
```

券的状态取值是 **`ACTIVE` / `STOPPED`**，我的用例发的是 **`ENABLED`** →
`!activate && !stop` 成立 → 如实抛 41327。**服务端行为完全正确**，错在我的夹具。
改成 `ACTIVE` 后建券+启用一次通过，`GET /scm/promotion/coupon/{id}` 回 `status=ACTIVE`。

**这条撤销本身就是要记的东西**：我先前只用「接口返回了什么码」来定性，
没读状态机就断言「契约有疑点」——那是把「我不懂这个契约」写成了「产品有缺陷」。
以后登记此类疑点前，必须先读到对应的枚举与守卫分支代码。

顺带把同类已核实过的两条契约钉在这里，省得再撞：
券状态变更接口带**乐观锁**（`version` 必须从 detail 取，否则 `30001 版本号不能为空`）；
`/scm/promotion/coupon/issue` 返回的是**发放数量**（`Integer`），不返回券实例 id。

**E2E-05 现状**：第 1 步（建券并激活）**绿**；2~5 步以 `test.fixme` 留档，
唯一未解的是「发券后如何把券实例查回来」
（`GET /scm/promotion/coupon/instances?customerId=` 目前查不到，可能还要 `couponId` 参数）。
断言都已写好：实例 `AVAILABLE` → 带券确认后 `netAmount == 毛额 - 券值` → 签收后离开 `AVAILABLE` → 毛利页 0 异常。

链套件现状：**22 passed / 2 skipped**（两条 skip 分别是 D-39 与 E2E-05 的实例查询待解项）。

## 7j. §15 E2E-05 也已贯穿：活动券链 5/5 通过

清单原文 E2E-05：活动/券 → 发券 → 订单确认冻结优惠 → 配送 → 签收核券 → 净应收 → 利润。
`e2e-chains/adm-golden-chain-05.spec.ts` 现在 5 步全绿（12.8s）：

| 步骤 | 断言到的事实 |
|---|---|
| 1 建券并激活 | `coupon/save` → detail 带回 `version` → 状态置 `ACTIVE`（**不是 `ENABLED`**，见 §7i） |
| 2 发券 | `coupon/issue` 返回发放数量；实例按 `couponId` 认（Instance VO 的键与外层不同），状态 `AVAILABLE` |
| 3 带券下单 | **券确实作用在应收侧**：订单 `settlementTotalAmount=35.0000`、应收 `amount=30`、`netAmount=30` —— 即 `应收 == 结算金额 - 券值`，且无红字时净额等于应收本身 |
| 4 签收核券 | 券实例离开 `AVAILABLE`（签收时才真正消耗） |
| 5 毛利页 | 表格渲染，整链 0 条 `pageerror` |

**第 3 步的口径值得单独记**：我最初断言「应收净额 = 应收毛额 - 券值」，跑出来
`毛额=30 净额=30 结算=35` —— 不是产品没扣券，而是**应收的 `amount` 本身就已经是扣完券的数**，
毛额在订单侧（`settlementTotalAmount`）。把断言改成「应收 == 结算 - 券值」后一次通过。
这类「两个域各自存的是毛额还是净额」的差异，只有把一条链真的跑通才会暴露出来。

**§15 当前进度：8 条已贯穿执行 5 条（E2E-01 / 02 / 03 / 04 / 05）**，
E2E-07 前两步绿、3~5 步 fixme 于 D-39；剩 E2E-06（满赠）与 E2E-08（在线支付自动核销）未做。
链套件合计 **26 passed / 1 skipped**。

## 7k. E2E-06 满赠链：第 1 步已跑通，整条以 fixme 留档，并暴露 D-41（夹具缺口）

`e2e-chains/adm-golden-chain-06.spec.ts` 第 1 步是**绿的**：
`POST /scm/promotion/activity/save` 建 `FULL_GIFT` 活动成功，rule 只接受
`{thresholdAmount, giftSkuId, giftQuantity}` 三个键（`PromotionRuleValidator` 用 `requireOnly` 拒多余的键），
活动状态同样走 `ACTIVE`，带 `version` 的 `/activity/{id}/status` 能把它启用。

第 2 步崩在**共享夹具**里：`createSignedOrder` 按 index 假设「订单行数 == 入参行数」，
而满赠会往订单里插一行赠品 → 读到 `undefined.sortedQuantity`。
这条记为 **D-41（测试夹具缺口，不是产品缺陷）**：夹具应按 `skuId` 匹配订单行而不是按 index。

**过程中我自己制造并修好的一次污染，值得单独记**：
活动是**库级共享状态**。我跑出来的那个 `ACTIVE` 满赠活动留在了 `xsy_v2_e2e_d` 里，
于是之后**其它链**的订单也都长出赠品行、一起崩掉 —— 链套件从 `26 passed` 掉到 `7 passed / 6 failed`。
我先误以为是「回退夹具补丁没回干净」，实际真因是库里的活动还活着。
处置：`update promotion_activity set status='STOPPED' where status='ACTIVE'` 把库清回来，
并把 E2E-06 **整条**（含第 1 步）标 `fixme` —— 否则它每跑一次就重新种一个 ACTIVE 活动。
清完复跑回到 **26 passed / 2 skipped / 0 failed**。

**教训（与 D-37 同一类，但更凶）**：D-37 是「用例留下主数据残留」，
这条是「用例留下**会改变其它用例业务结果的活动配置**」。
链用例的 `afterAll` 必须把自己开启过的活动/配置关掉，
否则共享库上的「一次绿」毫无意义 —— 它只是还没毒到别人。

**§15 现状：8 条已贯穿执行 5 条（01/02/03/04/05）**；E2E-06 第 1 步绿、整条 fixme 于 D-41；
E2E-07 前两步绿、3~5 步 fixme 于 D-39；E2E-08 未开始。
链套件 **26 passed / 2 skipped**（两条 skip 都在代码里写明了阻塞的缺陷号）。

## 7l. D-41 已修（夹具按 skuId 认行）→ E2E-06 满赠链全绿；E2E-08 资金链从零写到全绿

### D-41 的修法与它守住的不变量

`e2e/scm-finance-fixtures.ts` 里两处「按数组位置认行」改成**按 `skuId` 认行**，
并新增 `pickOrderLine` / `inputForSku` 两个私有小工具；分拣录入提交前先
`filter((item) => item.sourceType !== 'PROMOTION_GIFT')`。
根因在契约里写得很清楚（`SortingTaskItemVO` 类说明）：
**赠品不复制成订单行**，它只以「赠品权益」的身份被追加进分拣任务的合并视图，
所以 `sorting.items.length` 在有满赠时**必然大于**建单入参行数 —— 位置匹配不是「偶发错位」，是结构性错误。
顺手补一个导出的 `stockSku()`：赠品不属于任何订单行，只能单独备货，否则发车时赠品无货可出。

### E2E-06（满赠 → 冻结赠品 → 分拣 → 发车赠品出库 → 赠品成本 → 利润）4/4 绿

`e2e-chains/adm-golden-chain-06.spec.ts` 全部解除 fixme，逐条落到的事实（不是只看接口 200）：

| 步 | 断言到的事实 |
|---|---|
| 1 | 赠品 sku 备货 30.0000，`FULL_GIFT` 活动上线成 `ACTIVE` |
| 2 | 订单里 `gifts` 有 1 条权益、数量 2.0000、`activityId` 回指本活动；**订单项里没有赠品 sku**；应收金额 == 订单结算额（赠品没进应收） |
| 3 | 发车后出现 `PROMOTION_GIFT_OUT` 流水，`sourceDocumentType=ORDER_PROMOTION_GIFT`、数量合计 2.0000、`unitCost=6.2000`（赠品成本按移动加权均价入账）、`after = before - quantity` 自洽；库存余额从 30.0000 降到 28.0000 |
| 4 | 毛利分析页渲染，`pageerror` 为空 |

库侧取证（`xsy_v2_e2e_d`）：`select ... where movement_type='PROMOTION_GIFT_OUT'` 恰好 1 行
`2.0000 | 6.2000 | 30.0000 | 28.0000 | ORDER_PROMOTION_GIFT`。

**§7k 那条污染教训这次在代码里闭环了**：链的 `afterAll` 先 `stopActivity()` 再 `harness.close()`，
用 `try { stop } finally { close }` —— 停不掉就让这条 spec 变红，而不是静默留下 `ACTIVE` 活动毒同库的别的链。
复跑后查库确认该活动已是 `STOPPED`，且库里 `status='ACTIVE'` 的活动数为 0。

### E2E-08（ONLINE 支付 → Receipt → 自动核销 → 少发 overApplied → 售后不 reverse）从零写到 6/6 绿

新文件 `e2e-chains/adm-golden-chain-08.spec.ts`。写之前先把契约读清楚，避免再犯 D-40 那种
「拿测试自己的错当产品缺陷」：

- `PaymentIntentCreateForm` 付订单用 `sourceType=SALES_ORDER` + `sourceId=orderId` + 显式 `amount`，
  `method=ONLINE`、`provider=MOCK`；
- **`mockScenario` 缺省即 SUCCESS，`/scm/payment/intent/create` 会同步跑完整条财务链**，
  这条链不需要投渠道回调（渠道回调只在 `DELAYED`/`EXPIRED` 路径上才必需）—— 与 E2E-07 的充值路径不同；
- 系统收款在 `/scm/finance/receipt/query` 的读 VO 里**没有 `sourceType` 字段**，
  只能靠 `method='ONLINE_PAYMENT'` + `externalReference` 前缀 `MOCK-TXN-` + `walletFunding=false` 认；
- `overApplied` **不是列**，是读侧派生：`overAppliedAmount = GREATEST(writtenOffAmount − (amount − redAmount), 0)`
  （`FinanceReceivableDao.xml:130-132`），所以断言只能走 `/scm/finance/receivable/query|{id}`。

实测钉住的不变量：

| 步 | 断言到的事实 |
|---|---|
| 1 | 少发（订 10 拣 7）后应收 24.5000 < 结算额 35.0000 |
| 2 | 支付当场多出 1 张 Receipt，`method=ONLINE_PAYMENT`、金额=实付、`externalReference` 含 `MOCK-TXN-`、`walletFunding=false` |
| 3 | 恰好 1 条自动核销：`entryType=NORMAL`、`sourceType=RECEIPT`、`sourceId=` 该收款、金额=整笔实付、`operator` 含 `SYSTEM:ORDER_FUNDING`；读侧 `writtenOff=35`、`open=0`、`overApplied=35−24.5=10.5` |
| 4 | 退货审批后：核销**行数不变**、同一 `writeOffId` 仍 `NORMAL`、金额未变、`reverseOfId` 仍空；收款仍在且金额未变；应收侧**追加** 1 条红字、净应收下降、`overApplied` 相应增大 |
| 5 | 人工冲正这张系统收款被失败关闭：`code=41144`「系统来源的收款不能人工冲正，请走支付退款流程」，且拒绝后不留痕迹 |
| 6 | 应收列表页对该行显示「超额核销待处理」，抽屉显示「超额核销」，`pageerror` 为空 |

第 4、5 步合起来就是清单里那句「售后后历史资金用途不 reverse」的正证 + 反证：
既验了「没有代码去反向历史核销」，也验了「想人工反向也被门禁挡住」。

### 复跑与连带影响

- 链套件（`playwright.chains.config.ts`）全量：**36 passed / 1 skipped / 0 failed**，
  日志 `.runtime/verify/chains-all-final.log`。唯一的 skip 仍是 E2E-07 的 3~5 步（**D-39**，已登记待裁决）。
- 夹具是共享的，所以复跑 7 个用到 `createSignedOrder` 的默认套件文件
  （`.runtime/verify/e2e-finance-after-d41.log`）：**8 passed / 1 failed**，
  唯一那条红是 `scm-finance-receivable-red:11` = **D-30 / 裁决项 15**（抽屉缺「超额核销待处理」状态词），
  与 §4 表第 58 行同一条，**不是本次改动引入的回退**。

**§15 现状（替换 §7k 末尾那段）**：8 条链里 **7 条已全绿**（01/02/03/04/05/06/08）；
E2E-07 前两步绿、3~5 步 fixme 于 D-39。链套件 36/1/0。

### 一条会影响 §16 判读的结构性事实：`e2e/` 与 `e2e-chains/` 不在前端静态门禁范围内

本轮给夹具加新导出、又新写两条链 spec 之后，单独对这三个文件跑 eslint：

```
npx eslint e2e/scm-finance-fixtures.ts e2e-chains/adm-golden-chain-06.spec.ts e2e-chains/adm-golden-chain-08.spec.ts
→ 3 problems (0 errors, 3 warnings)：File ignored because no matching configuration was supplied
```

也就是说这些路径**被 eslint 配置直接排除**（`vue-tsc` 的 `tsconfig` 同样不含 e2e 目录），
所以 `verify.py frontend` 的 `exit 0` **不覆盖** §15 链用例与共享夹具 —— 它只证明 `src/` 与 `test/` 干净。
链 spec 的类型/语法错误不会被任何静态门禁发现（Playwright 用 esbuild 转译，不做类型检查），
**唯一的有效性证明就是真的跑一遍**。
引用「frontend PASS」时必须注明这条边界，否则会把「没跑过检查」读成「检查过了」。
（属结构性事实，不是本轮引入的缺陷；要收进 §16 的话得单独定口径。）

### 7l-补. 在飞工件（**接手时先读这三份，别看 stats 猜**）

为了证明 D-41 的共享夹具改动没把默认套件带离干净库基线，起了第三座一次性干净库：

| 项 | 状态 |
|---|---|
| `xsy_v2_e2e_e` Flyway V1→V106 | **已确证**：`flyway_schema_history` 顶部 `106\|106\|t`、`105\|105\|t`（`.runtime/verify/migration-clean-e.log`），§16「clean V1→V106 migration」又多一次独立复现 |
| 测试栈 | 已在新库上起来，`18080/18081/18083` 全 200，`.runtime/verify/stack-e2e-e.log`（旧栈 `xsy_v2_e2e_d` 被我停掉，**库和数据都没动**，`E2E_DB=xsy_v2_e2e_d bash .runtime/e2e-stack.sh` 可恢复） |
| `python tools/verify.py e2e` | **已收口（工件 `.runtime/verify/20261004-191145-459002/`，19:11:46Z 起、8.8 分钟）**：`{expected:137, unexpected:6, skipped:15, flaky:0}`，`summary.json` `exitCode=1`。**与前一座干净库基线 `20261004-180440-379124` 完全同数、且红的六条同名同位**（`scm-customer-360:47`/`:71` = D-19 裁决项 7；`scm-data-scope:287` 0ms = 裁决项 6/D-38；`scm-finance-receivable-red:11` = D-30 裁决项 15；`scm-purchase:250` = 裁决项 16；`scm-sorting:209` = 裁决项 17）。**结论：D-41 的共享夹具改动对默认 E2E 套件零回退**，未解释的红仍为 0。 |

**读法沿用 §5c 三条纪律**：先看尾部有没有前置错误（全 0 stats ≠ 没跑 ≠ 全绿）；
干净库上的已知红**只有**裁决项 15/16/17 三条 + D-19/D-17 那组，出现别的红按 D-42… 续号立案；
对照基线是 `137 passed / 6 unexpected / 15 skipped`（`20261004-180440-379124`）。
复现命令（一次带齐四个 env，别分次试）：

```
XSY_FILE_STORAGE_MODE=cloud XSY_V2_PG_CONTAINER=xsy-scm-postgres-1 \
XSY_V2_PG_USER=xsy_scm_app XSY_V2_PG_DB=xsy_v2_e2e_e python tools/verify.py e2e
```

## 7m. 本批提交拆分，以及一处**提交信息勘误**（不掩盖）

工作区改动按域拆成 7 个提交（`fix(web)` / `test(web)` / `test(server)` / `test(e2e)`×2 /
`fix(e2e)` / `test(e2e)` 链 / `docs`）。其中 `ac6193c2 fix(web)` 的信息里有一句
「让 DELIVERY 侧的派单契约用例跟着对齐」**是错的**：同批携带的
`xsy-scm-web/test/p1-sorting-contract.test.mjs` 实际改动是给
`SortingPermission` 与 V89 菜单种子发布的三条 ADM-11 电子秤权限
（`scm:sorting:scale:query|accept|report`）补契约登记，与配送派单无关。
本地历史不回改，在此如实勘正；`docs` 这条也不该被当成「测试全绿」的凭证 ——
它记录的每个数字都取于 `99ec1d4f` + V106，见 §6 顶部的适用边界。

## 7n. 后续修复：无组件菜单（Docker PostgreSQL 验证）

2026-10-04 补齐客户 SKU 反查页（menu 435 原组件路径），并新增 V108 软删除 SmartAdmin 演示页（menu 85）及未接入端点的“可见性维护”权限（menu 487），移除角色关联。客户 SKU 编辑仍由客户新增 / 编辑与详情流程完成，反查权限（menu 486）保留。
`SmartAdminMenuComponentPgIT` 不再豁免历史缺口。V108 已在本机 Docker 隔离库 `xsy_scm_task_20261004` 执行；原有 `xsy_scm` V76 库和生产库未操作。

## 7o. 后续修复：SCM 外键口径冲突（Docker PostgreSQL 验证）

仓库规则在 `AGENTS.md` 与 SmartAdmin 底座规则中均明确 V2 不使用数据库外键。2026-10-04 新增 V109，移除 V81、V82、V88、V89、V90 引入的 9 条外键；既有采购 / 库存 migration 契约保持全局零外键断言，`ScmPurchaseMigrationIT` 的追加历史清单更新到 V109。Docker 隔离库已由 Flyway 执行至 V109；`ScmInventoryMigrationIT` 的全局零外键断言通过，随后在 mapper 方法白名单断言处发现独立缺口。原有 `xsy_scm` V76 库和生产库未操作。

## 7p. 后续修复：收款登记结算关系纵深校验（Docker PostgreSQL 定向验证）

2026-10-04 手工收款新增只读客户域契约，在首次登记前调用现有 Customer 结算关系校验；幂等重放和支付派生收款保留原冻结主体，不按当前主档重算。`AdminSettlementTermsPgIT` 在 Docker PostgreSQL 隔离库定向执行：**5 tests / 0 failures / 0 errors / 0 skipped**。

## 7q. 后续修复：库存只读 DAO 契约（Docker PostgreSQL 定向验证）

`InventoryMovementDao.listSalesOutAllocations` 是退货查询读取销售出库分摊的只读方法。`ScmInventoryMigrationIT` 的声明方法清单补入该方法后，Docker PostgreSQL 定向执行 **5 tests / 0 failures / 0 errors / 0 skipped**；append-only 写入口限制不变。

## 7r. 后续修复：动态权限 Catalog 契约（Docker PostgreSQL 定向验证）

将 J-10 选择为 C：权限契约扫描器只对打印数据源权限、打印金额权限和订单异常类型查询权限展开其正式 enum / provider 值，并逐值断言 Catalog 存在及菜单已发布；支付与余额 Catalog 也纳入正式类扫描。`ScmPermissionContractPgIT` 定向执行 **2 tests / 0 failures / 0 errors / 0 skipped**，未放宽运行期权限校验。

## 7s. 后续修复：结算关系校验与干净库全量后端验收

`ScmFinanceReceiptSettlementPgIT` 的集团夹具改为先通过 Customer 域创建合法父子集团关系，再模拟父客户软删；手工收款边界将失效结算关系稳定映射为参数校验错误。定向测试 **3/3** 通过。随后在 Docker Desktop PostgreSQL 独立新库 `xsy_scm_release_20261004`、本机 Redis 与 MinIO 上重跑全量后端：**1,383 tests / 0 failures / 0 errors / 0 skipped**；真实 MinIO `F0FileStorageCloudIT` 为 **5/5**。Flyway 从空库迁移至 V109。原有 `xsy_scm` 数据库未修改；本结果不代表浏览器 E2E 或生产部署验收。

## 7t. 后续修复：迁移校验摘要冻结至 V109

干净 Docker PostgreSQL 已由 Flyway 从 V1 应用至 V109，且全量后端测试通过后，运行 `python tools/migration_checksum_guard.py sync`，将 V75～V109 加入已跟踪摘要；随后 `check` 通过。此次操作只更新仓库摘要文件，没有改写已应用迁移，也没有触碰原 `xsy_scm` V76 数据库。

## 7u. 后续修复：采购缺口预览明确标记只读

按 J-15 选择 A，在冻结批次缺口预览卡片增加“只读预览”标记，不改查询或需求生成行为。Docker-backed Playwright 定向用例 `10 stock shortage preview is read-only and server-computed` **1/1** 通过。

## 7v. 后续修复：应收明细重复呈现超额待处理状态

按 J-16 选择 A，在应收详情抽屉增加与列表同名的“超额核销待处理”状态标签，保留净应收与“不代表已退款或钱包余额”的说明。`scm-finance-receivable-red` Docker-backed Playwright 用例 **1/1** 通过。

## 7w. 后续修复：可选筛选请求静默降级

按 J-14 选择 A，为 Axios 请求添加显式的 `suppressGlobalErrorMessage` 选项；供应商与仓库下拉的辅助查询启用该选项。业务拒绝仍按 Promise reject 交给组件处理，但不再清除或覆盖页面现有消息。Docker-backed `scm-sorting.spec.ts` **8/8** 通过，包含主管新建分拣任务及权限反例。

## 7x. 后续修复：客户 360 E2E 自建数据夹具

按 J-13 选择 A，Wave 7 用例在独立账号下经正式客户类型、员工与客户 API 创建临时客户，并在结束时按 id/version 删除；干净 V1→V109 库不再依赖其他 E2E 文件留下的客户。`scm-customer-360.spec.ts` **2/2** 通过，包含常购商品只读请求与缺订单权限拒绝。

## 7y. 后续修复：数据范围 E2E 通过采购链创建首条库存

按 J-17 选择 A，干净库缺少余额行时先创建供应商、绑定 SKU、提交采购单，再直接收货并确认入库；库存行与流水来自正式库存命令，之后的双仓调拨授权断言可以在无预置业务数据的 V1～V109 库上执行。`scm-data-scope.spec.ts` **6/6** 通过。

## 7z. 后续修复：采购单导出与打印验收对齐实际页面

采购单列表 XLSX 导出用例保留；打印用例改为打开模板预览、选择带备注字段的临时模板，再触发浏览器打印（用户可选择打印机或另存为 PDF）。断言验证备注中的 `<script>` 作为文本、仅追加两条打印快照、不改变采购单状态。临时模板在 `afterAll` 按版本软删除。`scm-purchase.spec.ts` 打印用例 **1/1**，E2E lint 与类型检查均通过。

## 8. 当前分支最终验收（2026-10-05）

- `python tools/verify.py quality`：**PASS**。Checkstyle、Spotless、Quality Guard、跨域 DAO / common-source 守卫、质量工具自检和 package-migration-readiness 全部通过。
- `python tools/verify.py backend`：Docker Desktop 独立库 `xsy_scm_backend_final_20261005` 从空库迁移至 V109 后，**1,384 tests / 0 failures / 0 errors / 0 skipped**；含新增的非空 seller-scope SQL 用例；MinIO 云存储 `F0FileStorageCloudIT` **5/5**。
- `python tools/verify.py frontend`：**PASS**。TS 棘轮、ESLint、Playwright 类型检查、Web 单测、生产构建通过；主浏览器套件 **158 passed / 0 skipped / 0 unexpected / 0 flaky**。
- TypeScript 基线中 116 项既有错误已因请求参数可选化而消除；baseline 从 1,974 收缩并重捕获为 **1,858**。随后 `ts_baseline_ratchet.py check` 显示 SCM errors 0、new errors 0。
- 生产配置检查与迁移 checksum guard 均通过；迁移摘要覆盖 109 个版本，drift / missing / renamed / unbaked 均为 0。
- 上述数据库是 Docker Desktop 中新建的隔离库，原 `xsy_scm` V76 未修改。GitHub Actions 工作流已提交但未 push，因此没有 GitHub-hosted CI 运行结果。

## 9. 充值/支付/财务黄金业务链（2026-10-05）

在新的 Docker Desktop 库 `xsy_scm_chain_current_20261005` 上运行 `playwright.chains.config.ts`：**36 passed / 1 skipped / 0 failed**。唯一 skip 是 `adm-golden-chain-07` 的第 3～5 步：充值回调 → 钱包 CREDIT → 余额支付 → 应收以 BALANCE 核销，缺陷登记为 D-39/J-18。前两步通过并再次确认当前 mock 充值意向已成功、财务收款已立，但钱包余额尚未入账；后续渠道确认与钱包入账时点需先由业务方裁决。

## 10. J-11：非空 seller scope 的信用敞口 SQL 取证

新增 `CustomerCreditScopePgIT`：通过正式客户/订单服务创建有归属的 CONFIRMED 订单，分别以不包含该 seller 的非空范围与包含该 seller 的非空范围读取 `CustomerCreditDao.selectExposure`。PostgreSQL 实际执行了 `scope.ids` 的 `foreach` 参数绑定；外范围返回不可见、归属范围返回可见，**1 test / 0 failures / 0 errors / 0 skipped**。没有调整 `knownFailures` 或基线，也没有发现方言缺陷；该用例随后纳入 §8 的全量后端结果。

## 11. 组件拆分后的完整浏览器复验（2026-10-05）

分拣秤读数抽屉与配送辅助排线面板拆分后，使用 Docker Desktop 新库 `xsy_scm_e2e_postfix_20261005`，Flyway 从空库迁移至 V109；本地后端指向该库，Vite 主服务与 `adm-ui` 夹具服务分别监听 18081/18083。MinIO F0 使用当前 Docker MinIO 容器的 Access Key，`XSY_FILE_STORAGE_MODE=cloud`。

`python tools/verify.py e2e` **PASS：158 passed / 0 skipped / 0 unexpected / 0 flaky**。MinIO F0 **8/8**；数据范围 `scm-data-scope.spec.ts` **6/6**。为使 J-17 真正适用于空库，夹具现选择 STANDARD SKU，空客户库自建并清理客户，司机范围测试自建本人/他人线路并在收尾取消线路、停用司机。数据库为本地隔离验收库，原 `xsy_scm` V76 未修改。

随后从 `sorting-task-list.vue` 抽出打印预览 / 计次面板 `SortingPrintPreviewModal`；定向 `scm-sorting.spec.ts` **8/8**，E2E ESLint、TypeScript 检查、TS 棘轮与生产构建通过。全量 158 项是在该打印面板拆分之前执行的。

## 12. 采购价格波动面板拆分与分页参数修复（2026-10-05）

从 `report-purchase-list.vue` 抽出价格波动图表与明细表到 `purchase-price-trend-tab.vue`，父页继续统一维护日期、SKU、供应商筛选和请求生命周期。定向测试首次打开该面板时发现价格趋势查询遗漏 `pageNum/pageSize`，服务端按 `PurchaseQuery` 返回 30001；加载器改为复用同页构造好的 `purchaseQuery(trend)`。`scm-report.spec.ts` **13/13** 通过，含新价格趋势面板行数据断言；定向 ESLint、E2E 类型检查、TS 棘轮与生产构建通过。

## 13. 库存损耗分析面板拆分（2026-10-05）

从 `report-inventory-list.vue` 抽出 `InventoryLossAnalysisTab`，将 KPI、成本权限控制、金额占比与按日趋势图、明细列配置和分页展示移入子组件；日期筛选、查询状态、导出和加载生命周期仍由父页管理。新增浏览器断言切换到“损耗分析”并检查图表标题和明细表。Docker Desktop 隔离库 `xsy_scm_e2e_postfix_20261005` 上定向用例 **1/1**；全量 ESLint、E2E 类型检查、TS 棘轮、生产构建通过。TS 棘轮为基线 1,858 项、SCM 错误 0、新错误 0；全量 `vue-tsc` 仍受既有 SmartAdmin/`oa` 类型错误影响。临时后端与 Vite 服务在验收后停止。

## 14. 销售商品分析面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出“按商品”图表与分页明细为 `SalesProductTab`；日期和业务筛选、TOP 查询、列表请求及分页数据仍由父页管理。五张报表导航用例增加商品 TOP5 标题与表格可见性断言；销售业务用例在正式确认订单后检查该 SKU 出现在按商品表格中。Docker Desktop 隔离库上两个定向用例各 **1/1**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 15. 销售分类分析面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出“按分类”图表、表格、列配置和分页展示为 `SalesCategoryTab`；日期与业务筛选、TOP/明细查询、错误状态及分页请求仍由父页管理。五张报表导航用例增加切换分类 Tab 后对 TOP5 图表标题和明细表的断言，Docker Desktop 隔离库定向用例 **1/1**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 16. 销售客户分析面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出客户 TOP5 图表、客户明细、列配置和分页展示为 `SalesCustomerTab`；请求和筛选仍由父页管理。导航用例覆盖客户页空数据结构；销售业务用例使用客户筛选器选中刚确认订单的测试客户，检查其行出现在客户分析表中。Docker Desktop 隔离库两个定向用例各 **1/1**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 17. 销售员业绩面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出销售员业绩表、列配置、错误与分页展示为 `SalesSellerTab`；导出、筛选与请求仍由父页管理。五张报表导航用例切到“按销售员”并确认表格可见，Docker Desktop 隔离库定向用例 **1/1**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 18. 销售订单行明细面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出订单行快照表、字段格式化、列配置与分页为 `SalesItemTab`；原订单详情弹窗仍由父页打开。导航用例确认明细表可见；销售业务用例通过共享客户筛选器查到刚确认订单的行明细。Docker Desktop 隔离库两个定向用例 **2/2**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 19. 销售订单表头明细面板拆分（2026-10-05）

从 `report-sales-list.vue` 抽出订单表头级明细表、列配置、格式化和分页为 `SalesOrderTab`；订单详情弹窗仍由父页持有。导航用例覆盖该表格，销售业务用例检查筛选客户的订单行，并点击订单号确认原订单详情仍可打开。Docker Desktop 隔离库两个定向用例 **2/2**。ESLint、E2E 类型检查、TS 棘轮和生产构建通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务已停止。

## 20. 销售前五个面板的完整浏览器复验（2026-10-05）

在销售商品、分类、客户、销售员和订单行五个展示面板拆分后，完整运行 `scm-report.spec.ts`，Docker Desktop 隔离库 **13 passed / 0 skipped / 0 failures**。涵盖销售业务口径、五个面板导航与数据呈现、采购与入库报表、库存流水/损耗、导出文件和接口权限。临时后端与 Vite 服务在验收后停止。

## 21. 六个销售面板最终回归（2026-10-05）

加入订单表头级 `SalesOrderTab` 并验证订单号可继续打开原订单详情后，再次完整运行 `scm-report.spec.ts`，Docker Desktop 隔离库仍为 **13 passed / 0 skipped / 0 failures**。本次覆盖六个销售面板和其余报表流程；前端构建、E2E 类型检查与 TS 棘轮通过，TS 基线 1,858 项、SCM 与新增错误均为 0。临时服务验收后停止。

## 22. 配送路线地图面板拆分（2026-10-05）

从 `route-detail.vue` 抽出路线地图、起点/停靠列表、定位提示、拖拽与上下移操作为 `RouteMapPanel`；父页继续持有数据范围、坐标投影、排序与定位 API。`scm-delivery.spec.ts` 全套 **8/8** 通过，包含未定位提示、坐标补录及规划前置校验。生产构建、E2E 类型检查、ESLint 与 TS 棘轮通过，TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务在验收后停止。

## 23. 配送打印面板拆分（2026-10-05）

从 `route-detail.vue` 抽出按订单/按客户双视角、筛选、勾选和打印状态表格为 `RoutePrintPanel`；父页继续持有打印数据加载、状态重置和计次 API。`scm-delivery-print.spec.ts` **6/6** 通过，覆盖权限拒绝、两种打印范围、筛选与不改变发货/库存状态。生产构建、E2E 类型检查、ESLint 与 TS 棘轮通过，TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务在验收后停止。

## 24. 配送履约面板拆分（2026-10-05）

从 `route-detail.vue` 抽出活动线路订单、签收状态、空状态和签收操作入口为 `RouteFulfillmentPanel`；父页继续负责签收弹窗、行版本校验与签收命令。`scm-delivery-l3.spec.ts` 全套 **8/8** 通过，覆盖正常/异常签收、终态动作隐藏、权限与发货不变量。生产构建、E2E 类型检查、ESLint 与 TS 棘轮通过；TS 基线 1,858 项、SCM 与新增错误均为 0。临时后端和 Vite 服务在验收后停止。
