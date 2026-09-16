# W4 Sales Order 销售订单 · Legacy Audit（只读审计）

> 波次：W4 — Sales Order（销售订单）
> 日期：2026-09-16
> 状态：**只读审计完成；本文件不产生任何代码、DDL 或配置变更**
> 前置基线（永久固定）：SmartAdmin v3.31 / Java 21 / PostgreSQL / Flyway / Sa-Token / Vue3；
> W1 Product ✅、W2 Customer + Supplier ✅、W3 Pricing ✅、SmartAdmin PostgreSQL Closure ✅；
> **V1–V12 已冻结，禁止修改**。
> 配套文件：[`2026-09-16-w4-order-target-design.md`](./2026-09-16-w4-order-target-design.md)
> 前置报告：[`2026-09-16-smartadmin-postgresql-closure-report.md`](./2026-09-16-smartadmin-postgresql-closure-report.md)（GO）

---

## 0. 本文件的性质与三个前置裁决项

### 0.1 本文件是什么

这是一份**只读**审计：它穷尽列出 Order 域在三个来源中的既有资产、真实业务规则、缺陷与盲区，并明确哪些东西 V2 **已经提供、不允许再造第二套**。

本文件**不设计**。设计在 `2026-09-16-w4-order-target-design.md`。

### 0.2 三个必须在编码前由人工裁决的问题

审计过程发现三处**无法从现有证据自行判定**的分歧。它们不是「实现细节」，而是会改变 DDL、状态机与前端页面数量的**结构性问题**。目标设计按 §0.3 的推荐取值起草，但**编码前必须由用户明确确认**。

| 编号 | 问题 | 冲突双方 | 推荐 | 影响面 |
| --- | --- | --- | --- | --- |
| **Q1** | **W4 的范围到底是什么** | 仓库内全部波次文档写 **W4 = purchase**；本次指令写 **W4 = Sales Order** | **采本次指令**（Order），并把 purchase 顺延为 W5 | 全部 |
| **Q2** | **订单状态机取哪一套** | A 源 legacy **4 状态**（DRAFT/PENDING/CONFIRMED/CANCELLED）；C 源 **12 状态**（含采购中/待分拣/分拣中/配送中/已签收/已完成/退款中/已作废） | **A 源 4 状态为订单域本体 + C 的履约/结算状态拆到独立字段**（见 §9.2） | DDL、状态转换表、API、Vue 页面 |
| **Q3** | **金额精度取哪一套** | A 源 + V2 冻结约定 **NUMERIC(18,4) / HALF_UP scale 4 / 对外 4 位小数字符串**；C 源 **DECIMAL(18,2) / HALF_UP 到分** | **采 A 源 + V2 冻结约定**（4 位） | DDL、序列化、前端格式化 |

**Q1 的证据链**（这不是「文档没更新」，而是明确的书面冲突）：

```text
AGENTS.md:68-71                 → W3 Pricing implementation/verification COMPLETE
                                  The current task stops after W3. Do not start W4.
docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md:1007
                                → | W4 | purchase（含超收 UI 补齐） | W3 |
docs/architecture/2026-09-15-root-promotion-report.md:152
                                → W4  purchase                            NOT STARTED
docs/architecture/2026-09-15-w3-pricing-approval.md:16
                                → 本波次冻结，停止于 W3，不自动进入 W4。
docs/architecture/2026-09-16-smartadmin-postgresql-closure-report.md:5
                                → 本阶段不进入 W4。
本次用户指令                     → 提交后开始 W4 Sales Order Audit + Target Design
```

**结论**：W4 的**字母编号被复用**，语义由 purchase 改为 Sales Order。这在流程上必须显式记录，否则后续波次（W5/W6）的文档会与 `迁移审计报告` 的路线图表互相矛盾。本文件与目标设计**一律按「W4 = Sales Order」执行**，并把 purchase 标为「顺延，编号待定」。

### 0.3 本审计对 Q2 的立场（重要）

用户指令明确要求「**Order 与 Inventory 的边界**」与「**W4 不要偷偷实现完整库存**」。审计证据显示：

- **A 源 legacy 订单域对库存零引用**（`com/xianshuyuan/scm/order/**` 全量 70 文件，grep `stock`/`inventory`/`warehouse` 命中 0）。
- **C 源订单域反向**：`SaleOrderService.deliver()` **直接调用 `StockOperateService.saleOutbound()`**，把库存扣减写进订单发货事务（见 §5.3 H4）。

因此 C 的 12 状态机里 `PURCHASING` / `SORTING` / `SORTED` / `DELIVERING` 这些状态，**本质上是「库存与履约域的状态」，不是「订单域的状态」**。把履约状态塞进订单 `status` 单一字段，是 C 侧最根本的建模问题，也正是 legacy 只有 4 状态的原因。

→ **Q2 推荐**：订单 `status` 采用 A 源 4 状态（订单**自身生命周期**）；C 的履约/结算语义**不塞进同一个字段**，而以独立字段（`fulfillment_status` / `pay_status`）+ 独立域承接。目标设计 §3 给出具体方案，**Inventory 的正式实现留到后续波次**。

---

## 1. 审计范围与方法

### 1.1 范围

本审计覆盖 Sales Order 域的**全部**相关内容：

```text
订单聚合（Order aggregate）        Order / OrderItem
下单                                后台录单 / 商城下单 / 补单
订单命令                            创建 / 修改 / 提交 / 确认 / 取消
支付状态                            pay_status
履约状态                            发货 / 签收 / 完成（含 C 的分拣）
快照                                Price / Customer / Address / SKU
数量与重量                          ordered_quantity / actual_quantity / actual_weight
订单金额                            明细小计 / 订单总额 / 核算金额
备注                                remark / reason
状态转换                            state transition
幂等                                idempotency
乐观锁                              optimistic locking
操作日志                            操作日志 / 审计
退款与退货                          Refund / Return
权限与菜单                          permission / menu
前端页面                            Vue 页面 / 枚举 / API / 选择器
```

### 1.2 三个来源与方法

| 源 | 位置 | 性质 | 本次使用方式 |
| --- | --- | --- | --- |
| **A** | legacy 旧根实现，**最后包含订单域的提交 = `95a5423`**（即 `803a862` 的父提交） | **业务规则唯一事实源** | `git cat-file` 只读导出，逐文件阅读 |
| **B** | `xsy-scm-server/**`、`xsy-scm-web/**`（V2 正式工作区） | **正式契约**，已验收 | 只读，明确「已提供 / 不允许再造第二套」 |
| **C** | `project-reference-examples/xsy-scm/**` | **资产库，非主干** | 只读，仅吸收页面交互与 DTO 形状 |

**A 源的可达性（重要修正）**：`MEMORY.md` 曾记「legacy 仅存于 `803a862` 及更早的 git 历史」。实测：

```text
git cat-file -e 803a862:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/SalesOrderApplicationService.java
→ ABSENT          # 803a862 本身已不含 legacy 订单域
git cat-file -e 95a5423:.../SalesOrderApplicationService.java
→ EXISTS
git merge-base --is-ancestor 95a5423 803a862   → YES
git rev-list --count 95a5423..803a862          → 1
```

**正确表述**：legacy 订单域的**最后一个包含提交是 `95a5423`**（`803a862^`），`803a862` 已删除 `xsy-scm-server/` 整个 legacy 目录。本审计全部 A 源证据均取自 `95a5423`。该修正已回写 `MEMORY.md`。

### 1.3 方法

```text
1. git ls-tree / git cat-file   穷尽 A 源订单域文件（不抽样）
2. 逐文件通读                    枚举 → DDL → 状态机 → 金额 → 幂等 → 日志 → 错误码 → API → 测试
3. grep 反证                     对「legacy 没有 X」类结论做全量 grep 验证
4. 只读比对 B 源                 确认 V2 已提供的契约（不修改）
5. 只读比对 C 源                 提取缺陷、可复制页面、DTO 形状
6. 文档交叉                     docs/ 全量 + C 的 requirement/database 文档
7. 冲突与缺口登记                 K1–K16（冲突）、G1–G15（无事实源）
```

### 1.4 结论摘要（先看这 10 条）

1. **A 源订单域是完整的纵向实现**：70 个文件（58 主 + 12 测试），含聚合、状态机策略、金额计算器、幂等组件、操作日志、退货退款、错误码表。
2. **A 源订单状态机只有 4 状态**：`DRAFT→PENDING→CONFIRMED`，`DRAFT|PENDING→CANCELLED`。**无 履约/采购/分拣/配送 状态**。
3. **A 源对库存零引用**，并在设计规格里显式写「**不包含：采购、库存占用、退货入库和库存流水**」。
4. **A 源价格快照是双轨制**：`draft_*`（草稿可改）+ `locked_*`（提交时锁定），提交后价格不再随主数据变化。这与用户要求的「Order 创建时价格快照，后续不重算历史成交价」**语义一致，但时机是「提交锁价」而非「创建锁价」** → 见 §9.2 与 Q4。
5. **A 源没有「人工锁价」在 Pricing 域的概念**：`OVERRIDE` 只存在于 `sales_order_item`，`PriceSource` 枚举的 `OVERRIDE` 是订单域自己的值。**与 W3 已冻结的 `ScmPriceSourceEnum {AGREEMENT, CUSTOMER_TYPE, MARKET}` 不冲突**——W3 的 Resolver 输出三值，`OVERRIDE` 由 Order 域叠加。
6. **A 源的幂等是自研的完整组件**（`idempotency_record` 表 + 请求规范化哈希 + 重放）。**V2 目前没有任何通用幂等组件** → Order 域需要落地它（见 §6.5）。
7. **A 源的操作日志是「只追加 + JSONB before/after」**，且**记录真实操作者**（`operator`）。C 源的操作日志**不记操作者、不记 before/after**（§5.3 H10）。
8. **C 源订单域有 22 处缺陷**（H1–H22），其中 H1/H4/H5/H11 是功能性缺陷，**不可复制**。
9. **V2 已提供四个必须复用的契约**：`PriceResolver`（Pricing）、`CustomerService.requireTradable`（Customer）、`ProductSkuOptionDao/ProductSkuOptionVO`（Product）、`ScmCommonErrorCode.VERSION_CONFLICT` + `@Version`（基础设施）。**Order 不得自建价格解析、客户校验、SKU 查询、乐观锁机制**。
10. **C 的 `04-订单.sql` 不能执行也不能复制**：它是 MySQL→PG 自动转换产物，金额 `numeric(18,2)`、数量 `numeric(18,3)`，与 V2 冻结的 `NUMERIC(18,4)` 冲突；且带 `DROP TABLE`、`xsy_set_update_time()` 触发器函数、无 `version`、无 `deleted BOOLEAN`、有 `deleted_flag smallint`。**仅作字段清单参考**。

---

## 2. 事实源全景

### 2.1 源的可信度排序（本审计实际使用）

```text
1. A 源（legacy 代码 + legacy 迁移 + legacy 测试）    ← 业务规则唯一事实源
2. B 源（V2 已验收实现 + 冻结迁移）                    ← 正式契约，冲突时优先于 A 的「基础设施」部分
3. A 源设计规格（docs/superpowers/specs/…）           ← 设计意图，权威性低于代码（代码可能有实现缺口）
4. C 源 requirement 文档                              ← 需求描述，权威性最低
5. C 源代码                                           ← 仅页面交互 / DTO 形状
```

**冲突消解规则**（沿用 W3 既定原则）：

- **业务规则冲突 → 一律采 A 源**。
- **基础设施冲突 → 一律采 B 源**（SmartAdmin Native First）。
- **A 源与 A 源设计规格冲突 → 以代码为准**，并把差异登记为缺口。

### 2.2 A 源证据（legacy，`95a5423`）

```text
代码       xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/**        58 文件
测试       xsy-scm-server/src/test/java/com/xianshuyuan/scm/order/**        12 文件
迁移       V4__create_sales_order_schema.sql
           V5__create_order_after_sales_schema.sql
           V30__add_mall_order_source_and_address_snapshot.sql
规格       docs/superpowers/specs/2026-09-03-sprint-2-sales-order-design.md
计划       docs/superpowers/plans/2026-09-04-task15-purchase-demand-order.md
           docs/superpowers/plans/2026-09-11-sprint5-price-center-spec-plan.md
验证       docs/superpowers/verification/2026-09-11-sprint5-integrated-quality-gate.md
邻接       customer/service/OrderableSkuQueryService.java + customer/vo/OrderableSkuResponse.java
           mall/**（MallOrderController / MallOrderService / MallOrderAddressEntity / …）
           marketing/row/ReorderItemRow.java + marketing/vo/ReorderItemResponse.java
```

### 2.3 B 源证据（V2 正式工作区）

```text
后端  xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/**
      └─ common/      ScmErrorCode / ScmCommonErrorCode / ScmBusinessException / ScmExceptionHandler
                      ScmFixedScale4Serializer / ScmDecimalStrings / ScmOperator / JsonbStringMapTypeHandler
      └─ product/     ProductSkuOptionDao / ProductSkuOptionVO（W1，只读）
      └─ customer/    CustomerService / CustomerValidator / CustomerDetailVO / CustomerErrorCode（W2）
      └─ pricing/     PriceResolver / ResolvedPriceVO / ScmPriceSourceEnum / PricingErrorCode（W3）
迁移  V1–V12（冻结）
前端  xsy-scm-web/src/{api,views,constants,components}/**/scm/**
```

### 2.4 C 源证据（`project-reference-examples/xsy-scm/`）

```text
后端  xsy-scm-server/xsy-scm-server/src/main/java/com/xsy/scm/admin/module/business/order/**
      constant 8 枚举 / controller 4 / service 4 / manager 4 / dao 4 / entity 4 / form 11 / vo 4
      mapper/business/order/ 4 XML
前端  xsy-scm-web/src/api/business/order/*.ts      4 文件
      xsy-scm-web/src/views/business/order/*.vue   4 文件（1567 行）
      xsy-scm-web/src/constants/business/order/order-const.ts（9 枚举）
      xsy-app/src/pages/order*/**（移动端，另有）
SQL   postgresql/04-订单.sql（189 行，MySQL→PG 自动转换）
      docs/database/03-订单.sql（107 行，MySQL 原版）
文档  docs/requirement/04-订单管理.md（183 行）
      docs/requirement/12-订单助手.md
```

### 2.5 文档源

```text
AGENTS.md                                        §7.3 Orders 域规则（权威）
SMARTADMIN_REFERENCE_RULES.md                    SmartAdmin 底座边界
docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md   § 路线图（W4=purchase，见 Q1）
docs/architecture/2026-09-15-w3-pricing-*.md     W3 契约与 legacy 不变量（P1–P27）
docs/architecture/2026-09-15-w2-customer-supplier-*.md       W2 契约与 legacy 不变量
docs/architecture/2026-09-16-smartadmin-postgresql-closure-report.md  PG 收口（GO）
docs/architecture/smartadmin-code-conventions.md  编码约定
docs/architecture/xsy-vs-smartadmin-conventions.md 差异约定
```

---

## 3. A 源（legacy）订单域资产清单

### 3.1 分层文件（legacy 包结构，V2 不沿用）

```text
controller/   SalesOrderController / OrderReturnController / OrderRefundController
converter/    SalesOrderConverter
dto/          16 个（SalesOrderSaveRequest / SalesOrderItemSaveRequest / OrderVersionRequest /
              CancelOrderRequest / ActualQuantityRequest / SalesOrderPageQuery /
              OrderReturnCreateRequest / OrderReturnApproveRequest / OrderReturnDecisionRequest /
              OrderReturnPageQuery / OrderRefundCompleteRequest / OrderRefundPageQuery / …）
entity/       12 个（SalesOrderEntity / SalesOrderItemEntity / OrderOperationLogEntity /
              IdempotencyRecordEntity / OrderReturnEntity / OrderReturnItemEntity /
              OrderRefundEntity + OrderStatus / OrderSource / PriceSource / QuantitySource /
              OrderReturnStatus / OrderRefundStatus）
mapper/       7 个
service/      17 个（应用服务 2 / 查询服务 2 / 纯策略 5 / 幂等 4 / 错误码 2 / 异常 2）
vo/           5 个
```

**V2 落点**（沿用 W1/W2/W3 的 `module/scm/<domain>/**` 结构，**不沿用 legacy 的 `service/` 平铺**）：

```text
module/scm/order/
├─ constant/     枚举 + OrderErrorCode
├─ controller/   SalesOrderController / SalesOrderItemController? / OrderReturnController / OrderRefundController
├─ dao/          SalesOrderDao / SalesOrderItemDao / OrderOperationLogDao / OrderReturn*Dao / OrderRefundDao / IdempotencyRecordDao
├─ domain/entity / domain/form / domain/vo
├─ manager/      OrderStateMachine / OrderAmountCalculator / SalesOrderValidator / OrderSnapshotFactory
└─ service/      SalesOrderService / SalesOrderQueryService / OrderReturnService / OrderRefundService
```

### 3.2 枚举（逐值）

**`OrderStatus`（A 源，4 值）** — `entity/OrderStatus.java`：

```java
public enum OrderStatus { DRAFT, PENDING, CONFIRMED, CANCELLED }
```

DDL 约束（`V4`）：

```sql
CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'PENDING', 'CONFIRMED', 'CANCELLED'))
```

**`OrderSource`（A 源，3 值）** — `NORMAL` / `SUPPLEMENT` / `MALL`（`MALL` 由 `V30` 追加）：

```sql
-- V4 原始
CONSTRAINT ck_sales_order_source CHECK (order_source IN ('NORMAL', 'SUPPLEMENT'))
-- V30 追加 MALL（DROP 后重建，不修改已应用迁移的语义，只放宽白名单）
ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_source;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_source CHECK (order_source IN ('NORMAL', 'SUPPLEMENT', 'MALL'));
```

**`PriceSource`（A 源，4 值）** — `AGREEMENT` / `CUSTOMER_TYPE` / `MARKET` / `OVERRIDE`：

```sql
CONSTRAINT ck_sales_order_item_draft_source CHECK (draft_price_source IN ('MARKET', 'AGREEMENT', 'OVERRIDE'))
CONSTRAINT ck_sales_order_item_locked_source CHECK (locked_price_source IS NULL OR locked_price_source IN ('MARKET', 'AGREEMENT', 'OVERRIDE'))
```

> **注意**：A 源 DDL 的 `draft_price_source` 白名单**漏了 `CUSTOMER_TYPE`**，但 Java 枚举含 `CUSTOMER_TYPE`，且 `SalesOrderApplicationService` 会写入 `PriceSource.valueOf(p.source().name())`——当 W3 Resolver 返回 `CUSTOMER_TYPE` 时会**违反 CHECK 约束**。这是 A 源的一个**真实缺陷**（登记为 A-D1，见 §7.2）。W3 文档已识别并指出「白名单已含 `CUSTOMER_TYPE`」，**该表述与 A 源实际 DDL 不符**（`V4` 里没有），但 `V34`（客户类型价 schema）之后是否补过白名单需在目标设计中按 V2 正式模型重新定义。

**`QuantitySource`（A 源，2 值）** — `SYSTEM` / `MANUAL`：

```sql
CONSTRAINT ck_sales_order_item_actual_source CHECK (actual_quantity_source IS NULL OR actual_quantity_source IN ('SYSTEM', 'MANUAL'))
```

**`OrderReturnStatus`（A 源，4 值）** — `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED`。
**`OrderRefundStatus`（A 源，2 值）** — `PENDING` / `COMPLETED`。

### 3.3 DDL（以 V4 / V5 / V30 为准）

#### 3.3.1 `sales_order`（订单主表，V4）

| 列 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK |
| `order_no` | VARCHAR(64) | 展示单号，`SO` + `yyyyMMdd` + 6 位序列 |
| `customer_id` | BIGINT | 下单客户 |
| `customer_code_snapshot` | VARCHAR(64) | **客户编码快照** |
| `customer_name_snapshot` | VARCHAR(150) | **客户名称快照** |
| `order_source` | VARCHAR(20) | `NORMAL`/`SUPPLEMENT`/`MALL` |
| `original_order_id` | BIGINT | 补单关联的原订单 |
| `supplement_reason` | VARCHAR(500) | 补单原因 |
| `status` | VARCHAR(20) | `DRAFT`/`PENDING`/`CONFIRMED`/`CANCELLED` |
| `ordered_total_amount` | NUMERIC(18,4) | **下单金额**（≥0） |
| `settlement_total_amount` | NUMERIC(18,4) | **核算金额**（可空，确认时写入，≥0） |
| `cancel_reason` | VARCHAR(500) | 取消原因 |
| `submitted_at` / `confirmed_at` / `cancelled_at` | TIMESTAMPTZ | 三个时间戳，与状态一一对应 |
| `version` | INTEGER | 乐观锁（≥0） |
| `deleted` | BOOLEAN | 逻辑删除 |
| `created_at` / `updated_at` | TIMESTAMPTZ | 审计 |
| `created_by` / `updated_by` | VARCHAR(64) | 审计（A 源实际写 `'SYSTEM'`） |

**A 源的表级 CHECK（业务规则硬编码在 DDL 里，必须保留）**：

```sql
-- 补单一致性：普通订单不得有原订单/补单原因；补单必须有原因
CONSTRAINT ck_sales_order_supplement CHECK (
    (order_source = 'NORMAL' AND original_order_id IS NULL AND supplement_reason IS NULL)
    OR (order_source = 'SUPPLEMENT' AND supplement_reason IS NOT NULL AND btrim(supplement_reason) <> '')
)
```

> **A-D2（缺陷）**：该 CHECK 在 `V30` 加入 `MALL` 后**没有同步更新**。`MALL` 订单既不是 `NORMAL` 也不是 `SUPPLEMENT`，因此**插入 `MALL` 订单会直接违反 CHECK**。这是 A 源的真实缺陷，目标设计必须修正为三分支白名单。

**索引**：

```sql
CREATE UNIQUE INDEX uk_sales_order_no_active ON sales_order (order_no) WHERE deleted = FALSE;
CREATE INDEX idx_sales_order_customer_created ON sales_order (customer_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_sales_order_status_created   ON sales_order (status, created_at DESC)     WHERE deleted = FALSE;
CREATE INDEX idx_sales_order_original_order_id ON sales_order (original_order_id) WHERE original_order_id IS NOT NULL;
```

#### 3.3.2 `sales_order_item`（订单明细，V4）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK |
| `order_id` | BIGINT | 订单 |
| `spu_id` / `sku_id` | BIGINT | 商品 / 规格 |
| `spu_code_snapshot` | VARCHAR(64) | **SPU 编码快照** |
| `product_name_snapshot` | VARCHAR(150) | **商品名快照** |
| `sku_code_snapshot` | VARCHAR(64) | **SKU 编码快照** |
| `spec_name_snapshot` | VARCHAR(150) | **规格名快照** |
| `spec_values_snapshot` | JSONB | **规格值快照**（必须为 object） |
| `sale_unit_snapshot` | VARCHAR(32) | **销售单位快照** |
| `product_type_snapshot` | VARCHAR(20) | `STANDARD` / `NON_STANDARD` |
| `ordered_quantity` | NUMERIC(18,4) | 下单数量（>0） |
| `actual_quantity` | NUMERIC(18,4) | 实数量（可空，>0） |
| `actual_quantity_source` | VARCHAR(20) | `SYSTEM` / `MANUAL` |
| `actual_quantity_reason` | VARCHAR(500) | 实重修正原因 |
| `draft_unit_price` | NUMERIC(18,4) | **草稿单价**（≥0） |
| `draft_price_source` | VARCHAR(20) | `MARKET`/`AGREEMENT`/`OVERRIDE`（**缺 `CUSTOMER_TYPE`**，见 A-D1） |
| `draft_price_source_id` | BIGINT | 来源记录 ID |
| `manual_price_override` | BOOLEAN | 人工改价标记 |
| `manual_price_reason` | VARCHAR(500) | 人工改价原因 |
| `locked_unit_price` | NUMERIC(18,4) | **锁定单价**（可空，≥0） |
| `locked_price_source` | VARCHAR(20) | 锁价来源 |
| `locked_price_source_id` | BIGINT | 锁价来源记录 ID |
| `ordered_line_amount` | NUMERIC(18,4) | 下单行小计（≥0） |
| `settlement_line_amount` | NUMERIC(18,4) | 核算行小计（可空，≥0） |
| `sort_order` | INTEGER | 明细排序 |
| `version` / `deleted` / 审计四件套 | | 同主表 |

**A 源的表级 CHECK（重点三条）**：

```sql
-- 人工改价一致性：改价必须有原因，非改价不得有原因；且改价行的来源必须是 OVERRIDE
CONSTRAINT ck_sales_order_item_manual_price CHECK (
    (manual_price_override = FALSE AND draft_price_source <> 'OVERRIDE' AND manual_price_reason IS NULL)
    OR (manual_price_override = TRUE AND draft_price_source = 'OVERRIDE' AND manual_price_reason IS NOT NULL AND btrim(manual_price_reason) <> '')
)
-- 数量必须为正
CONSTRAINT ck_sales_order_item_ordered_quantity CHECK (ordered_quantity > 0)
CONSTRAINT ck_sales_order_item_actual_quantity  CHECK (actual_quantity IS NULL OR actual_quantity > 0)
-- 规格值快照必须是 JSON object
CONSTRAINT ck_sales_order_item_spec_values CHECK (jsonb_typeof(spec_values_snapshot) = 'object')
```

**索引**：

```sql
CREATE INDEX idx_sales_order_item_order_id ON sales_order_item (order_id, sort_order) WHERE deleted = FALSE;
CREATE INDEX idx_sales_order_item_sku_id   ON sales_order_item (sku_id)                 WHERE deleted = FALSE;
```

#### 3.3.3 `order_operation_log`（操作日志，V4）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK |
| `order_id` | BIGINT | 订单 |
| `operation_type` | VARCHAR(40) | `CREATE`/`UPDATE`/`SUBMIT`/`ACTUAL_QUANTITY`/`CONFIRM`/`CANCEL` |
| `operator` | VARCHAR(64) | **真实操作者**（A 源实际写 `'SYSTEM'`，因为当时无 RBAC） |
| `reason` | VARCHAR(500) | 操作原因 |
| `before_data` / `after_data` | JSONB | **完整前后快照**（必须为 object 或 NULL） |
| `created_at` / `created_by` | TIMESTAMPTZ / VARCHAR(64) | |

```sql
CREATE INDEX idx_order_operation_log_order_created ON order_operation_log (order_id, created_at DESC);
```

**只追加、不修改**（A 源无 update/delete 路径）。

#### 3.3.4 `idempotency_record`（幂等，V4）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK |
| `operation_scope` | VARCHAR(100) | 作用域，如 `ORDER_CREATE`、`ORDER_SUBMIT:{id}` |
| `idempotency_key` | VARCHAR(200) | 客户端 key |
| `request_hash` | VARCHAR(128) | **规范化请求 SHA-256** |
| `result_type` | VARCHAR(100) | 结果类型 |
| `result_id` | BIGINT | 结果 ID |
| `result_data` | JSONB | 结果载荷（`{"value": …}`） |
| `version` / `deleted` / 审计四件套 | | |

```sql
CREATE UNIQUE INDEX uk_idempotency_record_scope_key_active
    ON idempotency_record (operation_scope, idempotency_key) WHERE deleted = FALSE;
```

#### 3.3.5 `order_return` / `order_return_item` / `order_refund`（售后，V5）

- `order_return`：`return_no`（`RET`+日期+序列）、`order_id`、`customer_id`、`status`（4 值）、`reason`、`decision_reason`、`approved_amount`、`approved_at`/`rejected_at`/`cancelled_at`、`version`、`deleted`。
  - **CHECK**：`ck_order_return_reason CHECK (btrim(reason) <> '')`；`ck_order_return_decision_reason`（驳回/取消必须有 decision_reason）。
- `order_return_item`：`return_id`、`order_item_id`、`requested_quantity`、`approved_quantity`、`locked_unit_price`、`approved_amount`。
  - **CHECK**：`requested_quantity > 0`；`approved_quantity` 可空且 `0 ≤ x ≤ requested_quantity`。
  - **唯一索引**：`uk_order_return_item_active ON (return_id, order_item_id) WHERE deleted = FALSE`。
- `order_refund`：`refund_no`、`return_id`、`order_id`、`customer_id`、`refund_amount`（>0）、`status`（`PENDING`/`COMPLETED`）、`external_reference`、`completed_at`。
  - **CHECK**：`ck_order_refund_completion`（`PENDING` ↔ `completed_at IS NULL`，`COMPLETED` ↔ `completed_at IS NOT NULL`）。
  - **三个唯一索引**：`uk_order_refund_no_active`、`uk_order_refund_return_active`（**一个退货单只能有一个退款单**）、`uk_order_refund_external_reference_active`（部分索引，外部凭证唯一）。

#### 3.3.6 `mall_order_address`（地址快照，V30）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY | PK |
| `order_id` | BIGINT | 订单 |
| `customer_id` | BIGINT | 客户 |
| `receiver_name` | VARCHAR(100) | 收货人 |
| `phone` | VARCHAR(32) | 电话 |
| `region` | VARCHAR(200) | 省市区 |
| `detail_address` | VARCHAR(300) | 详细地址 |
| `created_at` / `created_by` | | |

```sql
CREATE UNIQUE INDEX uk_mall_order_address_order ON mall_order_address (order_id);
CREATE INDEX idx_mall_order_address_customer ON mall_order_address (customer_id, created_at DESC);
```

> **A-D3**：该表**没有 `version` / `deleted` / `updated_at`**——是 A 源里唯一的「不可变追加表」。语义上是**地址快照**（只写一次、永不改），这个设计是**正确的**，但命名 `mall_order_address` 把它绑到了「商城」来源。V2 应改为来源无关的命名（如 `order_address_snapshot`），因为后台录单同样需要地址快照。

### 3.4 状态机（A 源，逐条）

`service/OrderStateTransitionPolicy.java`（**纯策略类，无 Spring 依赖，可单测**）：

```java
private static final Set<Transition> ALLOWED_TRANSITIONS = Set.of(
        new Transition(OrderStatus.DRAFT,   OrderStatus.PENDING),
        new Transition(OrderStatus.DRAFT,   OrderStatus.CANCELLED),
        new Transition(OrderStatus.PENDING, OrderStatus.CONFIRMED),
        new Transition(OrderStatus.PENDING, OrderStatus.CANCELLED)
);
public static boolean canTransition(OrderStatus from, OrderStatus to) {
    return ALLOWED_TRANSITIONS.contains(new Transition(from, to));
}
```

**状态转换矩阵**：

| from \ to | DRAFT | PENDING | CONFIRMED | CANCELLED |
| --- | :-: | :-: | :-: | :-: |
| **DRAFT** | — | ✅ | ✖ | ✅ |
| **PENDING** | ✖ | — | ✅ | ✅ |
| **CONFIRMED** | ✖ | ✖ | — | ✖ |
| **CANCELLED** | ✖ | ✖ | ✖ | — |

**关键语义（A 源设计规格 §4 原文）**：

```text
DRAFT     : 可编辑头和订单行，可保存人工改价
PENDING   : 已重新校验并锁价，仅允许非标品实重录入、确认或取消
CONFIRMED : 结算完成，订单编辑终止，可创建退货
CANCELLED : 终止状态，保留取消原因和历史快照
```

**状态只能通过显式命令端点转换**，A 源**不提供通用状态更新接口**。

> **A-D4（缺口）**：`OrderStateTransitionPolicy` **只在 `cancel()` 里被调用**；`submit()` 和 `confirm()` 用 `requireState(o, OrderStatus.DRAFT / PENDING)` 硬编码，**没有走策略表**。这是「策略类与实现不一致」——策略表声明了 4 条边，但实际只有 2 条边（DRAFT→CANCELLED、PENDING→CANCELLED）经过它。目标设计必须让**所有**状态转换统一走策略表。

### 3.5 金额与精度（A 源）

`service/OrderAmountCalculator.java`（**纯函数，可单测**）：

```java
private static final int SCALE = 4;
private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

public static BigDecimal lineAmount(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(SCALE, ROUNDING_MODE);
}
public static BigDecimal orderAmount(List<BigDecimal> lineAmounts) {
    return lineAmounts.stream().reduce(ZERO, BigDecimal::add).setScale(SCALE, ROUNDING_MODE);
}
```

**契约**：

```text
行小计  = round(quantity × unitPrice, 4, HALF_UP)
订单总额 = round(Σ 行小计, 4, HALF_UP)
DB       NUMERIC(18,4)
Java     BigDecimal
API      十进制字符串（4 位定点）
```

**A 源设计规格 §5 原文**：「数量、单价和金额均使用四位小数。Java 使用 `BigDecimal`，PostgreSQL 使用 `NUMERIC(18,4)`，API 使用十进制字符串。服务端计算统一使用 `HALF_UP`。」

→ **与 V2 冻结约定完全一致**（`ScmFixedScale4Serializer` SCALE=4、`ScmDecimalStrings` SCALE=4、`NUMERIC(18,4)`）。**Q3 因此无实质分歧**：C 源的「2 位到分」是 C 独有的偏离，不采。

**两条金额轨**：

```text
ordered_*    : 下单金额（草稿价 / 锁定价 × 下单数量）
settlement_* : 核算金额（锁定价 × 实际数量）——仅在 CONFIRM 时写入
```

### 3.6 幂等（A 源，完整组件）

四个类 + 一张表：

```text
IdempotencyService       claim / complete / replay
IdempotencyRequestHasher 规范化 JSON + SHA-256
IdempotencyGuard         requireMatching（不一致 → 抛 IdempotencyConflictException）
IdempotencyConflictException
```

**`claim(scope, key, request)` 的精确语义**：

```java
// 1. key 为空 → IDEMPOTENCY_KEY_REQUIRED(40029)
// 2. key 长度 > 200 → IDEMPOTENCY_KEY_INVALID(40030)
// 3. 尝试 INSERT（靠 uk_idempotency_record_scope_key_active 唯一索引）
//    INSERT 成功（返回 1）→ 首次请求，replay=false
//    INSERT 失败（返回 0）→ 已存在：
//        取 FOR UPDATE，比对 request_hash
//        不一致 → IdempotencyConflictException（HTTP 409）
//        一致   → replay = (result_data != null)
```

**请求哈希规范化**（`IdempotencyRequestHasher`）：

```text
1. 对象键按字典序排序（递归）
2. 数字 → stripTrailingZeros
3. 形如数字的字符串 → 转 BigDecimal stripTrailingZeros 再转字符串
   （即 "1.50" 与 "1.5" 视为同一请求）
4. SHA-256 → 十六进制
```

**作用域命名（A 源实际使用）**：

```text
ORDER_CREATE                      创建
ORDER_SUBMIT:{orderId}            提交
ORDER_ACTUAL:{orderId}:{itemId}   实重录入
ORDER_CONFIRM:{orderId}           确认
ORDER_CANCEL:{orderId}            取消
```

**哪些命令要求幂等**（A 源设计规格 §6 原文）：「创建文档和造成状态转换或副作用的命令必须携带 `Idempotency-Key`。」

→ 因此 `update()` **不需要** key（不创建文档、不转换状态），其余 5 个命令都需要。

### 3.7 操作日志（A 源）

**`log(orderId, operationType, reason, before, after)` 记录**：

```text
operation_type : CREATE / UPDATE / SUBMIT / ACTUAL_QUANTITY / CONFIRM / CANCEL
operator       : 'SYSTEM'（当时无 RBAC；V2 必须替换为 ScmOperator.current()）
reason         : 取消原因 / 实重修正原因（其余为 null）
before_data    : auditSnapshot(order, items) —— 完整订单头 + 明细数组
after_data     : auditSnapshot(order, items)
```

`auditSnapshot` 的结构：

```json
{
  "id": 1, "customerId": 9, "status": "PENDING", "source": "NORMAL",
  "originalOrderId": null, "supplementReason": null,
  "items": [
    { "id": 11, "skuId": 33, "orderedQuantity": "10.0000", "actualQuantity": "10.0000",
      "draftUnitPrice": "5.2000", "lockedUnitPrice": "5.2000",
      "manualPriceOverride": false, "manualPriceReason": null }
  ]
}
```

**注意**：`before`/`after` 是**全量快照**（不是 diff）。金额用 `setScale(4).toPlainString()` 写入。

### 3.8 错误码（A 源，逐条）

**`OrderErrorCodes`（20 条）**：

| 码 | 常量 | 消息 |
| --- | --- | --- |
| 40420 | `ORDER_NOT_FOUND` | 销售订单不存在 |
| 40421 | `ITEM_NOT_FOUND` | 订单行不存在 |
| 40920 | `VERSION_CONFLICT` | 销售订单版本冲突 |
| 40922 | `ITEM_VERSION_CONFLICT` | 订单行版本冲突 |
| 40923 | `INVALID_STATE` | 当前订单状态不允许此操作 |
| 40020 | `SUPPLEMENT_REASON_REQUIRED` | 补单原因不能为空 |
| 40021 | `INVALID_SUPPLEMENT` | 普通订单不能关联原订单或补单原因 |
| 40924 | `ORIGINAL_ORDER_INVALID` | 关联原订单必须已确认且客户一致 |
| 40022 | `DUPLICATE_SKU` | 订单行 SKU 不能重复 |
| 40023 | `INVALID_QUANTITY` | 数量必须大于零 |
| 40024 | `OVERRIDE_REASON_REQUIRED` | 人工改价必须填写价格和原因 |
| 40025 | `INVALID_PRICE_OVERRIDE` | 非人工改价行不能指定价格 |
| 40026 | `INVALID_PRICE` | 价格不能小于零 |
| 40925 | `ACTUAL_ONLY_NON_STANDARD` | 只有待审核非标品订单行可录入实数量 |
| 40027 | `ACTUAL_REASON_REQUIRED` | 实重修改原因不能为空 |
| 40028 | `CANCEL_REASON_REQUIRED` | 取消原因不能为空 |
| 40926 | `ACTUAL_QUANTITY_REQUIRED` | 确认前所有订单行必须具有有效实数量 |
| 40029 | `IDEMPOTENCY_KEY_REQUIRED` | Idempotency-Key 不能为空 |
| 40030 | `IDEMPOTENCY_KEY_INVALID` | Idempotency-Key 长度不能超过 200 个字符 |

**`AfterSalesErrorCodes`（10 条）**：

| 码 | 常量 | 消息 |
| --- | --- | --- |
| 40450 | `RETURN_NOT_FOUND` | 退货单不存在 |
| 40451 | `REFUND_NOT_FOUND` | 退款单不存在 |
| 40950 | `ORDER_NOT_CONFIRMED` | 仅已确认订单可申请退货 |
| 40951 | `RETURN_STATUS_INVALID` | 当前退货状态不允许此操作 |
| 40952 | `REFUND_STATUS_INVALID` | 当前退款状态不允许此操作 |
| 40953 | `RETURN_QUANTITY_EXCEEDED` | 退货数量超过可退数量 |
| 40954 | `VERSION_CONFLICT` | 数据已被其他操作修改，请刷新后重试 |
| 40050 | `ORDER_ITEM_INVALID` | 退货行不属于原订单或订单行不可退 |
| 40051 | `APPROVAL_INVALID` | 批准数量无效或未批准任何商品 |
| 40052 | `IDEMPOTENCY_REQUIRED` | 缺少 Idempotency-Key |

**A-D5（缺陷）**：`OrderErrorCodes.VERSION_CONFLICT = 40920`，`AfterSalesErrorCodes.VERSION_CONFLICT = 40954`，**同一语义两个码**；且 **40920 与 V2 冻结的 `ScmCommonErrorCode.VERSION_CONFLICT = 40921` 不同**。另外 `OrderErrorCodes` 内部有**码值复用**：`40030`（`IDEMPOTENCY_KEY_INVALID`）与 `V2 PricingErrorCode.PRICE_INVALID(40030)` 撞码；`40021`（`INVALID_SUPPLEMENT`）与 `SalesOrderItemChangeSet.VERSION_REQUIRED(40021)` 撞码；`40921`（`SalesOrderItemChangeSet.NOT_OWNED`）与 V2 的 `VERSION_CONFLICT(40921)` 撞码。

→ **结论**：A 源的错误码**不能照搬**。V2 必须在**未被占用的码段**重新分配（见目标设计 §5）。

**V2 已占用的码（审计基线）**：

```text
40000  VALIDATION_ERROR            ScmCommonErrorCode
40921  VERSION_CONFLICT            ScmCommonErrorCode（+ ProductErrorCode 重声明）
40030  PRICE_INVALID               PricingErrorCode
40031  PERIOD_INVALID              PricingErrorCode
40032  CUSTOMER_PARENT_INVALID     CustomerErrorCode
40033  VISIBILITY_POLICY_CONFLICT  CustomerErrorCode
40034  VISIBILITY_ITEM_INVALID     CustomerErrorCode
40035  PRICE_BATCH_ROW_INVALID     PricingErrorCode
40036  PRICE_RESOLVE_CUSTOMER_TYPE_MISSING  PricingErrorCode
40037  VISIBILITY_SKU_NOT_SELLABLE CustomerErrorCode
40430  CUSTOMER_NOT_FOUND          CustomerErrorCode
40431  CUSTOMER_TYPE_NOT_FOUND     CustomerErrorCode
40432  AGREEMENT_PRICE_NOT_FOUND   PricingErrorCode
40433  CUSTOMER_TYPE_PRICE_NOT_FOUND PricingErrorCode
40930  CUSTOMER_NOT_TRADABLE       CustomerErrorCode
40932  VISIBILITY_NOT_OWNED        CustomerErrorCode
40933  AGREEMENT_PRICE_OVERLAP     PricingErrorCode
40935  CUSTOMER_TYPE_PRICE_OVERLAP PricingErrorCode
40936  CUSTOMER_CODE_DUPLICATE     CustomerErrorCode
40937  CUSTOMER_TYPE_CODE_DUPLICATE CustomerErrorCode
40938  CUSTOMER_TYPE_IN_USE        CustomerErrorCode
40939  CUSTOMER_REFERENCED         CustomerErrorCode
40948  PRICE_BATCH_KEY_DUPLICATE   PricingErrorCode
40949  SKU_NOT_SELLABLE            PricingErrorCode
```

**W1 占用**（`ProductErrorCode`，需在目标设计阶段 grep 确认后落定）。

→ **Order 域可用码段**：`40460–40479`（404*）、`40060–40079`（400*）、`40960–40979`（409*）。目标设计 §5 给出分配。

### 3.9 API 契约（A 源，`/api` 前缀）

```http
GET    /api/orders                                       分页（page/pageSize/keyword/status/customerId）
GET    /api/orders/{id}                                  详情
GET    /api/orders/{id}/logs                             操作日志
POST   /api/orders                     Idempotency-Key   创建（返回 Long id）
PUT    /api/orders/{id}                                  修改（无 key）
POST   /api/orders/{id}/submit         Idempotency-Key   提交（返回详情）
POST   /api/orders/{id}/items/{itemId}/actual-quantity  Idempotency-Key  实重录入
POST   /api/orders/{id}/confirm        Idempotency-Key   确认
POST   /api/orders/{id}/cancel         Idempotency-Key   取消

POST   /api/order-returns                                 创建退货
POST   /api/order-returns/{id}/approve                    批准
POST   /api/order-returns/{id}/reject                     驳回
POST   /api/order-returns/{id}/cancel                     取消
POST   /api/order-refunds/{id}/complete                   完成退款
```

响应形状（A 源）：`ApiResponse<PageData<T>>` / `ApiResponse<T>`。分页 `{records, page, pageSize, total}`。

**A-D6（缺陷）**：`PUT /api/orders/{id}` **没有 `Idempotency-Key`**，但 `update()` 会做**全量差量同步**（插入 / 更新 / 软删明细）。在客户端重试场景下**不幂等**（第二次重试会因 `version` 已变而 409，属「意外安全」，但语义不是幂等）。目标设计需明确：`update` 靠 `version` 乐观锁保证「最多成功一次」，**显式声明为「非幂等，靠 version 保护」**。

### 3.10 测试覆盖（A 源，12 个测试类）

```text
controller/AfterSalesControllerTest          Web 层
controller/SalesOrderControllerTest          Web 层
entity/OrderEnumsTest                        枚举
service/AfterSalesRulesTest                  纯规则
service/IdempotencyRequestHasherTest         规范化哈希
service/IdempotencyServiceTest               幂等
service/OrderAmountCalculatorTest            金额计算
service/OrderStateTransitionPolicyTest       状态矩阵
service/SalesOrderApplicationServiceTest     应用服务
service/SalesOrderItemChangeSetTest          差量同步
service/SalesOrderNumberGeneratorTest        单号生成
service/SalesOrderValidatorTest              校验
```

**覆盖到的**：状态矩阵、金额精度、差量同步、幂等哈希与冲突、退货额度、编号生成、校验规则。
**未覆盖的**（A 源盲区，登记为 G 系列）：真实 PG 并发下的额度安全、锁价稳定性、跨事务回滚、`ORDER_CANCEL` 幂等重放、`ACTUAL_QUANTITY` 前后值审计。

---

## 4. 必须保留的业务不变量（O1–O24）

> 格式沿用 W3：每条给出**定义 → A 源定位 → V2 处置**。

### 4.1 聚合与写入口

| # | 不变量 | A 源定位 | V2 处置 |
| --- | --- | --- | --- |
| **O1** | `SalesOrder` 是聚合根；`SalesOrderItem` **只能**通过订单应用服务写入，无独立写入口 | `SalesOrderApplicationService` 是唯一写路径；A 源**无** `SaleOrderItemController` | 保留。V2 **不提供**独立 item 写端点（C 源提供了，见 H20） |
| **O2** | 订单行更新采用**差量同步**，保留已有 ID/version；跨订单行 ID、缺 version、过期 version 一律拒绝 | `SalesOrderItemChangeSet.between()` | 保留，并把三条错误分化为独立错误码 |
| **O3** | 订单行 **SKU 不得重复** | `SalesOrderValidator.validateDraft` → `DUPLICATE_SKU(40022)` | 保留；V2 额外加 **DB 级部分唯一索引**（A 源只在应用层校验，并发下可绕过） |
| **O4** | 明细有稳定 `sort_order` | `materialize()` 递增赋值 | 保留 |

### 4.2 价格快照（**本波次核心**）

| # | 不变量 | A 源定位 | V2 处置 |
| --- | --- | --- | --- |
| **O5** | 订单明细持久化**商品/SKU/规格/单位/类型快照**，主数据变更后历史仍可读 | `materialize()` 写 7 个 snapshot 列 | 保留（这是用户明确要求的 SKU Snapshot） |
| **O6** | 订单持久化**客户编码与名称快照** | `create()` 写 `customer_code_snapshot` / `customer_name_snapshot` | 保留（Customer Snapshot） |
| **O7** | 地址以**不可变追加表**持久化（唯一索引 `(order_id)`） | `mall_order_address`（V30） | 保留，**改名去掉 `mall_`**，改为来源无关（A-D3） |
| **O8** | **双轨价格**：`draft_*` 草稿可改；`locked_*` 提交时锁定 | `draft_unit_price` / `locked_unit_price` | 保留 |
| **O9** | 提交时**重新解析**所有非人工改价行，并把结果**复制**为 `locked_*`；提交后价格不再随主数据改变 | `submit()` 调 `pricing.resolve()` 后写 `locked_*` | 保留，**但改调 V2 的 `PriceResolver`**（见 §6.1） |
| **O10** | 人工改价行**不重新解析**，`locked_unit_price := draft_unit_price`，`locked_price_source := OVERRIDE` | `submit()` 的 `manualPriceOverride` 分支 | 保留 |
| **O11** | 人工改价必须**同时**给价格与原因；非改价行**不得**给价格 | `SalesOrderValidator` + `ck_sales_order_item_manual_price` | 保留 |
| **O12** | `ordered_quantity` **永不**被实重覆盖 | `submit()` 只写 `actual_quantity`，不碰 `ordered_quantity` | 保留 |
| **O13** | 标品提交时 `actual_quantity := ordered_quantity`（来源 `SYSTEM`）；非标品提交时 `actual_quantity := NULL` | `submit()` 的 `productTypeSnapshot` 分支 | 保留 |

### 4.3 状态与命令

| # | 不变量 | A 源定位 | V2 处置 |
| --- | --- | --- | --- |
| **O14** | 状态只能经**显式命令端点**转换，**无通用状态更新接口** | 无 `PUT /status` | 保留（强化） |
| **O15** | 4 条合法转换边（DRAFT→PENDING / DRAFT→CANCELLED / PENDING→CONFIRMED / PENDING→CANCELLED） | `OrderStateTransitionPolicy` | 保留，但**所有**转换统一走策略表（修 A-D4） |
| **O16** | 取消**必须**填原因 | `CANCEL_REASON_REQUIRED(40028)` + `validator.requireReason` | 保留 |
| **O17** | 实重录入**只允许** `PENDING` 状态的**非标品**行，**必须**填原因 | `actualQuantity()` + `ACTUAL_ONLY_NON_STANDARD(40925)` | 保留 |
| **O18** | 确认前**所有**行必须有 `> 0` 的实数量 | `confirm()` → `ACTUAL_QUANTITY_REQUIRED(40926)` | 保留 |
| **O19** | 确认时按 `actual_quantity × locked_unit_price` 重算 `settlement_line_amount` 与 `settlement_total_amount` | `confirm()` | 保留 |
| **O20** | 补单必须有原因；可**选**关联一个**已确认且客户一致**的原订单 | `validateOriginal()` → `ORIGINAL_ORDER_INVALID(40924)` | 保留 |
| **O21** | 普通订单**不得**有 `original_order_id` 或 `supplement_reason` | `INVALID_SUPPLEMENT(40021)` + `ck_sales_order_supplement` | 保留（并修 A-D2 的 MALL 分支） |

### 4.4 幂等、并发与审计

| # | 不变量 | A 源定位 | V2 处置 |
| --- | --- | --- | --- |
| **O22** | 创建文档 / 状态转换 / 有副作用的命令**必须**携带 `Idempotency-Key`；相同 key + 相同请求 → 重放原结果；相同 key + 不同请求 → 冲突 | `IdempotencyService.claim()` | 保留（用户明确要求） |
| **O23** | 订单与明细均用 `version` 乐观锁；冲突 → 版本冲突错误码 | `@Version` + `requireVersion` | 保留，**统一用 `ScmCommonErrorCode.VERSION_CONFLICT(40921)`** |
| **O24** | 操作日志**只追加**，含 `operator` + JSONB `before_data`/`after_data` | `order_operation_log` + `log()` | 保留；`operator` 必须改为 **`ScmOperator.current()`**（A 源写死 `'SYSTEM'`） |

### 4.5 不变量总表（W4 验收对账用）

| 组 | 编号 | 条数 |
| --- | --- | --- |
| 聚合与写入口 | O1–O4 | 4 |
| 价格快照 | O5–O13 | 9 |
| 状态与命令 | O14–O21 | 8 |
| 幂等/并发/审计 | O22–O24 | 3 |
| **合计** | **O1–O24** | **24** |

---

## 5. C 源订单资产审计（`project-reference-examples/xsy-scm/`）

### 5.1 资产清单

```text
后端  xsy-scm-server/xsy-scm-server/src/main/java/com/xsy/scm/admin/module/business/order/
      constant/  8 枚举（OrderStatusEnum / PayStatusEnum / OrderSourceEnum / SettleTypeEnum /
                 OrderItemStatusEnum / OrderOperateTypeEnum / RefundStatusEnum / RefundTypeEnum）
      controller/  SaleOrderController / SaleOrderItemController / SaleOrderLogController / SaleRefundController
      service/     SaleOrderService / SaleOrderItemService / SaleOrderLogService / SaleRefundService
      manager/     SaleOrderManager / SaleOrderItemManager / SaleOrderLogManager / SaleRefundManager
      dao/         4
      entity/      4
      form/        11
      vo/          4
      mapper/business/order/  4 XML
前端  xsy-scm-web/src/api/business/order/{order,order-item,order-log,order-refund}-api.ts      51 行
      xsy-scm-web/src/views/business/order/order-list.vue          445 行
      xsy-scm-web/src/views/business/order/order-item-list.vue     359 行
      xsy-scm-web/src/views/business/order/order-log-list.vue      250 行
      xsy-scm-web/src/views/business/order/order-refund-list.vue   344 行
      xsy-scm-web/src/constants/business/order/order-const.ts      118 行（9 枚举）
SQL   postgresql/04-订单.sql        189 行（MySQL→PG 自动转换，**不可执行、不可复制**）
      docs/database/03-订单.sql     107 行（MySQL 原版，仅字段清单参考）
文档  docs/requirement/04-订单管理.md  183 行（需求主文档，12 状态机）
      docs/requirement/12-订单助手.md
移动端 xsy-app/src/pages/order*/**、xsy-app/src/pages/order-detail/**
```

### 5.2 C 的 12 状态机 vs A 源的 4 状态

**C 的 `OrderStatusEnum`（12 值）**：

| 值 | 名称 | C 的 desc |
| --- | --- | --- |
| 1 | `DRAFT` | 草稿 |
| 2 | `PENDING` | 待确认 |
| 3 | `CONFIRMED` | 已确认 |
| 4 | `PURCHASING` | 采购中 |
| 5 | `SORTING` | 待分拣 |
| 6 | `SORTED` | 分拣中 |
| 7 | `DELIVERING` | 配送中 |
| 8 | `SIGNED` | 已签收 |
| 9 | `COMPLETED` | 已完成 |
| 10 | `REFUNDING` | 退款中 |
| 11 | `CANCELLED` | 已取消 |
| 12 | `INVALID` | 已作废 |

**C 的实际实现（`SaleOrderService`）只有 3 个转换方法，且全是硬编码**：

```java
confirm(orderId)   // DRAFT|PENDING → CONFIRMED   （硬编码 2 值判断）
deliver(orderId)   // 放行 3/5/6 → DELIVERING     （黑名单 + 黑名单，共 9 值判断）
sign(orderId)      // 只放行 7 → SIGNED           （硬编码单值判断）
```

→ **C 的 12 状态里，只有 4 个（1/2/3/7）真正被写入过**；4/5/6/8/9/10/11/12 **没有任何代码路径能到达**。这是一个**「枚举写了、状态机没写」**的典型：`OrderStatusEnum` 的注释声称「已签收(8) 时生成应收」，但 `sign()` 把订单置为 `SIGNED(8)` 后就**没有任何路径推进到 `COMPLETED(9)`**。

**与 A 源的对比**：

| 维度 | A 源 | C 源 |
| --- | --- | --- |
| 状态数 | 4 | 12 |
| 转换边数（声明） | 4 | 12（文档表） |
| 转换边数（实现） | 4（但仅 2 条走策略表，A-D4） | 3（全部硬编码） |
| 是否含履约状态 | ✖ | ✅（4/5/6/7/8/9） |
| 是否含库存副作用 | ✖ | ✅（`deliver` 扣库存） |
| 是否有终态 | ✅（CANCELLED） | ✅（CANCELLED/INVALID） |
| 是否有作废 | ✖ | ✅（12） |

→ **采 A 源 4 状态**（Q2）。C 的履约语义以独立字段承接，见目标设计 §3。

### 5.3 C 的缺陷清单（H1–H22，**W4 不复制**）

> 严重度：**S1** = 功能性缺陷（会写错数据）/ **S2** = 一致性缺陷 / **S3** = 规范缺陷。

| # | 严重度 | 缺陷 | 证据 |
| --- | --- | --- | --- |
| **H1** | S1 | `SaleRefundService.add` 违反 `t_refund.refund_no NOT NULL`——不生成退款单号 | `SaleRefundService`（未调用 `SerialNumberService`） |
| **H2** | S1 | `SaleOrderService.add` **不写任何明细、不算任何金额**，`total_amount` 恒为 0；「下单」实际是空单 | `add()` 只 `save(saleOrderEntity)` |
| **H3** | S1 | `add` 不解析价格、不写 `snapshot_price` → **价格快照机制形同虚设**（`snapshot_price` 默认 0.0000） | `add()` 无 price 调用 |
| **H4** | S1 | `deliver` 放行 `SORTING(5)`/`SORTED(6)`，且**在订单事务内直接扣库存**（`StockOperateService.saleOutbound`）——违反 Order/Inventory 边界 | `deliver()` 逐行调 `stockOperateService.saleOutbound` |
| **H5** | S1 | `update` **无状态检查**——已确认/已取消/已签收的订单都能被改 | `update()` 直接 `saleOrderManager.update` |
| **H6** | S1 | `update` **不写操作日志**，`SaleOrderLogAddForm` 的 `before_value`/`after_value` 从不填充 | `update()` 无 `logService.add` |
| **H7** | S1 | `sign` 生成应收（`receivableService.generateFromOrder`），但 `t_order.status` 到 `SIGNED` 后**无路径到 `COMPLETED`**；`REFUNDING(10)` 也永远进不去 | `sign()` 只设 `SIGNED` |
| **H8** | S1 | **无取消端点**（`SaleOrderController` 无 cancel），`CANCELLED(11)`/`INVALID(12)` 不可达 | `SaleOrderController` 4 个命令端点 |
| **H9** | S1 | **无改价端点**，`OrderOperateTypeEnum.CHANGE_PRICE(3)` 永不产生；`snapshot_price` 无法被人工覆盖 | 同上 |
| **H10** | S2 | `t_order_log` **不记操作者**（`operate_by` 可空且不写）、**不记前后值**（`before_value`/`after_value` 可空且不写）→ 审计不成立 | `SaleOrderLogService.add` |
| **H11** | S1 | `SaleOrderMapper.xml` 用 MySQL 方言 **`INSTR(order_no, #{...})`**——PG 无 `INSTR`，**该查询在 PG 上直接报错** | `SaleOrderMapper.xml` `<if test="queryForm.orderNo…">AND INSTR(...)` |
| **H12** | S2 | `SaleOrderMapper.xml` 用 `SELECT *` + `resultType`（自动驼峰）——列名与 VO 属性不匹配时**静默丢值** | 同文件 `SELECT *` |
| **H13** | S2 | `delete` / `batchDelete` **物理语义混乱**：`batchUpdateDeleted` 直改 `deleted_flag`，绕过状态机（注释自承「业务上应走取消/作废状态流转」） | `SaleOrderMapper.xml` `<update id="batchUpdateDeleted">` |
| **H14** | S3 | 订单菜单**未授予任何角色**——无 `t_role_menu` 授权 | C 侧菜单 seed |
| **H15** | S3 | 菜单 `component` 路径与实际文件不匹配 | C 侧菜单 seed |
| **H16** | S2 | `t_order` **无 `version`** → 无乐观锁；并发改单会互相覆盖 | `04-订单.sql` |
| **H17** | S2 | `t_order` / `t_order_item` 用 `deleted_flag smallint`（非 BOOLEAN），且**无 `uk_*` 部分唯一索引** | `04-订单.sql` |
| **H18** | S2 | 金额 `numeric(18,2)`、数量 `numeric(18,3)` → 与 V2 冻结的 `NUMERIC(18,4)` **冲突** | `04-订单.sql` |
| **H19** | S2 | **无幂等机制**：无 `Idempotency-Key`、无 `idempotency_record` 表 | C 全量 grep 命中 0 |
| **H20** | S2 | 提供**独立订单明细写端点**（`SaleOrderItemController`）→ 破坏 O1（聚合根唯一写入口） | `SaleOrderItemController` |
| **H21** | S3 | 建表脚本带 **`DROP TABLE IF EXISTS`** + `xsy_set_update_time()` 触发器函数 + `ON UPDATE` 触发器 → 与 V2 的 `updated_at` 由应用维护的约定冲突 | `04-订单.sql` |
| **H22** | S3 | **无地址快照、无客户快照、无 SKU 名称快照**（只有 `snapshot_price` 一个快照） | `04-订单.sql` 4 张表 |

### 5.4 C 的可复制资产（**仅前端**）

按「Copy First + Adapt」原则，C 的前端页面**是骨架来源**：

| C 文件 | 行数 | 可复制部分 | 必须剪枝 | 必须适配 |
| --- | --- | --- | --- | --- |
| `views/business/order/order-list.vue` | 445 | 表格骨架、筛选栏、列布局、操作按钮区 | 裸 ID 列；`resizable`/`@resizeColumn`/`handleResizeColumn`；12 状态枚举；`deliver` 按钮（W4 无发货） | API `/order/*` → `/scm/order/*`；权限 `order:*` → `scm:order:*`；枚举 12→4；金额格式化 |
| `views/business/order/order-item-list.vue` | 359 | 明细表格骨架 | 独立明细写入口（H20）；`resizable` | 改为**订单详情内嵌只读明细** |
| `views/business/order/order-log-list.vue` | 250 | 日志列表骨架 | `resizable`；`operate_by` 裸 ID 列 | 补 `operator` 显示；补 before/after 展示 |
| `views/business/order/order-refund-list.vue` | 344 | 退款列表骨架 | `resizable`；4 状态（C）→ V2 的 2 状态退款 + 4 状态退货 | API `/order-refund/*` → `/scm/order/refund/*` |
| `api/business/order/*.ts` | 51 | 请求函数形状 | 全部 URL、全部字段 | 重写为 V2 `scm/order` 契约 |
| `constants/business/order/order-const.ts` | 118 | `SmartEnum` 写法 | 9 枚举全部需要重定义（值域与 V2 不同） | 字符串枚举（V2 约定）；注册进 `constants/index.ts` |

> **关键**：C 的 `order-list.vue` 里 `ORDER_STATUS_ENUM` 是 **12 值数字枚举**，V2 是 **4 值字符串枚举**——这不是「改个值」，而是**筛选器选项数从 12 降到 4**。因此「不重新设计同功能页面」的边界是：**布局与交互不变，枚举与 API 必须按 V2 契约重写**。

---

## 6. V2 已提供的正式契约（**不允许再造第二套**）

### 6.1 Pricing（W3，`module/scm/pricing/**`）

**入口**：`PriceResolver`（`@Service`，**直接注入，不走 HTTP**）。

```java
public PriceResolveResultVO preview(Long customerId, List<Long> ids, OffsetDateTime at)
public List<ResolvedPriceVO> resolve(Long customerId, List<Long> ids, OffsetDateTime at)
public List<ResolvedPriceVO> requireResolvable(Long customerId, List<Long> ids, OffsetDateTime at)
```

**`ResolvedPriceVO` 形状**：

```java
Long skuId; String skuCode; String productName; String specName;
BigDecimal unitPrice;                 // 4 位定点字符串序列化，null → JSON null
ScmPriceStatusEnum priceStatus;       // PRICED / UNPRICED
ScmPriceSourceEnum priceSource;       // AGREEMENT / CUSTOMER_TYPE / MARKET
Long sourceRecordId;
ScmUnpricedReasonEnum unpricedReason; // NO_PRICE_SOURCE
boolean sellable;
ScmUnavailableReasonEnum unavailableReason;  // SKU_NOT_FOUND/SKU_OFF_SHELF/SPU_OFF_SHELF/CATEGORY_DISABLED/NOT_VISIBLE
```

**关键语义（W3 冻结）**：

```text
优先级        AGREEMENT → CUSTOMER_TYPE → MARKET，命中即返回，逐 SKU 独立，不叠加
0 元 ≠ UNPRICED   0 是合法价格；UNPRICED 仅当无任何来源
可售性独立     eligibility 不短路取价、不擦除合法 0 元（Q2 人工修订）
resolve        软模式（返回 UNPRICED / sellable=false，不抛）
requireResolvable  严模式（任一 UNPRICED 或 !sellable → SKU_NOT_SELLABLE(40949)）
```

**W3 明确留给 W4 的消费点**（`2026-09-15-w3-pricing-target-design.md`）：

```text
P13  UNPRICED 消费方语义        → W3 只保证契约，消费方在 W4/W6
P18  可见性影响范围             → W3 只落读能力，消费点在 W4/W6
Q3   取价试算端点入 W3          → 让 Resolver 可被 W4 的 Order 消费
```

**W3 已识别的 W4 接口**（`2026-09-15-w3-pricing-legacy-audit.md:257`）：「`sales_order_item` 已经有 `draft_price_source` / `locked_price_source` / `locked_price_source_id` / `manual_price_override` / `manual_price_reason` / `locked_unit_price` 列，且白名单已含 `CUSTOMER_TYPE` 与 `OVERRIDE`。这是 W3 交付 Resolver 后 W4 能直接落地的既有接口。」

> **修正**：该表述的后半句**与 A 源实际 DDL 不符**——A 源 `V4` 的 `ck_sales_order_item_draft_source` 只含 `('MARKET','AGREEMENT','OVERRIDE')`，**没有 `CUSTOMER_TYPE`**（见 §3.2 与 A-D1）。**不要按 W3 文档的这句表述去假设白名单已就绪**；V2 的 V13 必须显式写全 `('AGREEMENT','CUSTOMER_TYPE','MARKET','OVERRIDE')`。

**Order 侧的取价策略**（目标设计 §6 展开）：

```text
草稿保存/修改  →  resolve()          软模式（允许 UNPRICED，前端显示「未定价」）
提交（锁价）    →  requireResolvable() 严模式（有 UNPRICED 就拒绝提交，40949）
人工改价行      →  不调 Resolver，locked := draft，source := OVERRIDE
```

### 6.2 Customer（W2，`module/scm/customer/**`）

```java
CustomerEntity requireTradable(Long customerId)   // 不存在 → 40430；不可交易 → 40930
CustomerEntity require(Long customerId, Integer version)
CustomerDetailVO  // 含 customerCode / name / customerTypeId / customerTypeName /
                  //   address（**单一 VARCHAR 字段**）/ contactName / contactPhone /
                  //   settleMode / creditLimit / sellerId / parentCustomerId / visibilityPolicy
```

**关键**：

- **`requireTradable` 就是「能否下单」的唯一判定入口**（W2 注释原文：「作为 W3 唯一允许的『能否下单』判定入口（legacy 不变量 C4）」）。
- **客户地址在 V2 是单字段 `customer.address`**，不是地址簿。→ **地址快照的来源就是这个字段**；C 的 `mall_order_address` 那种多字段结构（receiver/phone/region/detail）在 V2 **没有对应源**。目标设计必须处理这个差异（G 系列）。
- **`CustomerErrorCode.CUSTOMER_REFERENCED(40939)`** 是 W2 预留给下游的引用检查位，注释原文：「W2 没有任何下游业务表引用客户，因此当前恒通过；检查位先落地，**W3 接入定价 / 订单后启用**」。→ **W4 是启用它的时机**（客户被订单引用后不可删）。

### 6.3 Product（W1，只读）

```java
ProductSkuOptionDao.selectByIds(List<Long>)  →  List<ProductSkuOptionVO>
ProductSkuOptionVO {
    Long skuId; Long spuId; Long categoryId;
    String skuCode; String productName; String specName;
    Map<String,String> specValues;
    String saleUnit; String productType;      // STANDARD / NON_STANDARD
    String status; String spuStatus; String categoryStatus;
    BigDecimal marketPrice;                   // 4 位定点字符串
}
```

**边界声明（W3 已建立）**：W3 只允许**新增**只读 SKU option 查询，**零修改既有文件**。W4 沿用——**不得修改 `module/scm/product/**`**。

→ **快照来源就绪**：`specValues`（→ `spec_values_snapshot` JSONB）、`saleUnit`（→ `sale_unit_snapshot`）、`productType`（→ `product_type_snapshot`）、`skuCode`/`productName`/`specName`/`spuId` 全部齐备。**Order 不需要新增任何 Product 侧查询。**

### 6.4 基础设施（SmartAdmin Native First）

| 能力 | V2 提供者 | Order 使用方式 |
| --- | --- | --- |
| 认证 | Sa-Token 1.44.0 + Redis（Bearer） | 直接复用 |
| 权限注解 | `@SaCheckPermission` | `scm:order:*` |
| 操作日志 | `@OperateLog` | 用于**系统级**审计；**订单业务日志另用 `order_operation_log`**（两套并存，不冲突） |
| 统一响应 | `ResponseDTO` / `PageResult` | 直接复用 |
| 全局异常 | `GlobalExceptionHandler` + `ScmExceptionHandler` | `ScmBusinessException` → HTTP 200 + `ResponseDTO.code` |
| 员工上下文 | `SmartRequestUtil.getRequestUser()` → `ScmOperator.current()` | **必须替换 A 源的 `'SYSTEM'`** |
| 字典 | SmartAdmin dict | 结算方式/来源可用 dict，但**优先用枚举**（W1–W3 惯例） |
| 文件 | SmartAdmin file | W4 暂不需要 |
| 分页 | `PageParam` / `PageResult` | 直接复用 |
| 乐观锁 | MyBatis-Plus `@Version` → `ScmCommonErrorCode.VERSION_CONFLICT(40921)` | 直接复用 |
| 校验 | Spring Validation + `ScmErrorCode` | 直接复用 |
| 序列化 | `ScmFixedScale4Serializer` + `ScmDecimalStrings` | 直接复用 |
| JSONB | `JsonbStringMapTypeHandler` | 用于 `spec_values_snapshot` |

### 6.5 V2 **没有**的能力（Order 必须自建，但要按 V2 规范）

| 缺口 | 说明 | W4 处置 |
| --- | --- | --- |
| **通用幂等组件** | V2 **没有**任何 `Idempotency*` 类或表 | Order 域自建，**但表落在 SCM 迁移里、类落在 `module/scm/order/**`**（不污染 SmartAdmin 底座） |
| **业务操作日志表** | V2 只有 SmartAdmin 的 `t_operate_log`（系统级），**没有**业务级 before/after | Order 自建 `order_operation_log` |
| **编号生成** | V2 有 SmartAdmin `SerialNumberService`（`t_serial_number`），但 **W3 已注意到 `SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` 是 dead code**（PG 收口遗留） | **优先复用 SmartAdmin `SerialNumberService`**（与 C 的做法一致，且 A 源用 PG sequence + 日期前缀）。目标设计 §4 需裁决：**PG sequence（A 源）vs SmartAdmin SerialNumberService** |
| **订单聚合框架** | 无 | Order 自建应用服务 |

> **编号生成裁决提示**：A 源用 `CREATE SEQUENCE sales_order_no_seq` + `"SO" + yyyyMMdd + %06d`。SmartAdmin 有 `SerialNumberService`（`SerialNumberIdEnum`）能产出 `XSD + yyyyMMdd + 4 位流水`（C 的 `04-订单.sql` 注释写的正是 `XSD + yyyyMMdd + 4 位流水（G-01）`）。**两条路都可行**；W2 事实里已记「菜单 id：基础 ≤300」，说明 SmartAdmin 的编号枚举可扩展。**该选择影响「是否新增 PG sequence」**，需在目标设计里明确并请用户确认。

---

## 7. 文档事实源与冲突（K1–K16）

### 7.1 冲突清单

| # | 冲突 | A 源 / B 源立场 | C 源 / 文档立场 | W4 采信 |
| --- | --- | --- | --- | --- |
| **K1** | **W4 范围** | 仓库文档：W4 = purchase | 本次指令：W4 = Sales Order | **本次指令**（Q1） |
| **K2** | **状态数量** | 4（DRAFT/PENDING/CONFIRMED/CANCELLED） | 12（含履约） | **A 源 4 + 独立履约字段**（Q2） |
| **K3** | **金额精度** | `NUMERIC(18,4)` / HALF_UP scale 4 | `DECIMAL(18,2)` / HALF_UP 到分 | **A 源 + V2 冻结**（Q3） |
| **K4** | **数量精度** | `NUMERIC(18,4)` | `numeric(18,3)` | **A 源 4 位** |
| **K5** | **逻辑删除表示** | `deleted BOOLEAN` | `deleted_flag smallint` | **A 源 BOOLEAN** |
| **K6** | **乐观锁** | `version INTEGER` + `@Version` | 无 | **A 源** |
| **K7** | **单号格式** | `SO` + `yyyyMMdd` + 6 位 | `XSD` + `yyyyMMdd` + 4 位 | **需裁决**（见 §6.5；两源都是「前缀+日期+流水」） |
| **K8** | **锁价时机** | **提交时**锁价（`submit`） | C 的 `add` 直接写 `snapshot_price` | **A 源：提交锁价**（但见 Q4） |
| **K9** | **价格来源值域** | `MARKET/AGREEMENT/CUSTOMER_TYPE/OVERRIDE` | `BASE/CUSTOMER_LEVEL/CURRENT/AGREEMENT`（4 值，语义完全不同） | **A 源 + W3 冻结三值 + OVERRIDE** |
| **K10** | **补单建模** | `order_source='SUPPLEMENT'` + `original_order_id` + `supplement_reason`（**同表**） | `source=3 补单`（无 `original_order_id`） | **A 源** |
| **K11** | **支付状态** | A 源**没有** `pay_status` | 有 `pay_status`（3 值） | **需裁决**（见 G 系列；A 源无事实源） |
| **K12** | **履约/发货** | A 源**没有**发货/签收 | 有 `deliver`/`sign` + 库存扣减 | **W4 不做履约**（Q2 + 用户「不要偷偷实现完整库存」） |
| **K13** | **退款 vs 退货** | A 源**两表**：`order_return`（4 状态）+ `order_refund`（2 状态，由 return 批准时生成） | C **单表** `t_refund`（4 状态，混合了退货/退款语义） | **A 源两表**（W2/W3 已有「C 三个建模冲突一律采 legacy」的先例） |
| **K14** | **退款审核流** | `PENDING → APPROVED/REJECTED/CANCELLED`，批准时**原子创建** `PENDING` 退款 | `PENDING → APPROVED → REFUNDED / REJECTED`（4 值单表） | **A 源** |
| **K15** | **退款是否需要外部凭证** | `external_reference`（可选，**部分唯一索引**） | 无 | **A 源** |
| **K16** | **操作日志是否记操作者/前后值** | ✅ 都记 | ✖ 都不记 | **A 源** |

### 7.2 A 源自身缺陷（代码 vs DDL vs 规格不一致）

| # | 缺陷 | 定位 | 处置 |
| --- | --- | --- | --- |
| **A-D1** | `ck_sales_order_item_draft_source` 白名单**漏 `CUSTOMER_TYPE`**，但 Java 会写入该值 → 违反 CHECK | `V4` vs `PriceSource` 枚举 | V2 的 V13 写全 4 值 |
| **A-D2** | `ck_sales_order_supplement` **未覆盖 `MALL`** → 插入 MALL 订单违反 CHECK | `V4` CHECK vs `V30` 白名单 | V2 修正为三分支 |
| **A-D3** | 地址快照表名绑死 `mall_`，后台录单无地址快照 | `V30` `mall_order_address` | V2 改名为来源无关 |
| **A-D4** | `OrderStateTransitionPolicy` 只在 `cancel()` 被调用；`submit`/`confirm` 硬编码状态判断 → 策略表与实现不一致 | `SalesOrderApplicationService` | V2 让所有转换统一走策略 |
| **A-D5** | 错误码撞码：`OrderErrorCodes.VERSION_CONFLICT(40920)` vs `AfterSalesErrorCodes.VERSION_CONFLICT(40954)` vs V2 `40921`；`40030`/`40021`/`40921` 与 V2 已占用码冲突 | `OrderErrorCodes` / `AfterSalesErrorCodes` / `SalesOrderItemChangeSet` | V2 在 40460+/40060+/40960+ 段重新分配 |
| **A-D6** | `PUT /api/orders/{id}` 无 `Idempotency-Key` 但做全量差量同步 | `SalesOrderController.update` | V2 显式声明「非幂等，靠 version 保护」 |
| **A-D7** | `operator` 全量写死 `'SYSTEM'`（因当时无 RBAC） | `SalesOrderApplicationService.log()` | V2 改为 `ScmOperator.current()` |
| **A-D8** | `OrderOperationLogEntity` 的 `operator` 有 `DEFAULT 'SYSTEM'` 且应用层不覆盖 | `V4` DDL | V2 去掉默认值，强制显式写入 |
| **A-D9** | `SalesOrderItemChangeSet` 的 `NOT_OWNED` 用 **40921**（与 V2 `VERSION_CONFLICT` 同码） | `SalesOrderItemChangeSet` | V2 用独立码 |
| **A-D10** | 明细快照**不含客户/地址**；客户/地址快照在**订单头**与**独立地址表** → 一个订单只有一组客户/地址快照，**无法支持「同一订单多收货地址」** | 结构层面 | 记录为已知限制，W4 按「一单一地址」实现，写入 `待人工确认项` |
| **A-D11** | `actual_quantity_reason` 在 DDL 里**可空**，但 `actualQuantity()` 强制 `validator.requireReason` → DDL 未约束 | `V4` vs service | V2 加 CHECK：`actual_quantity_source='MANUAL'` 时 reason 非空 |
| **A-D12** | `order_operation_log` **无 `version`**（只追加，正确）但**无 `order_id` 外键、无归档策略** → 长期会无限增长 | 结构层面 | 记录为已知限制（非阻塞） |

---

## 8. 盲区与无事实源（G1–G15）

> 「A 源无事实源」= **A 源代码与迁移里都不存在该能力**。这些项**不能靠推测实现**，必须显式裁决或明确排除。

| # | 项 | A 源 | B 源 | C 源 | W4 处置 |
| --- | --- | --- | --- | --- | --- |
| **G1** | **支付状态** `pay_status` | ✖ 无 | ✖ 无 | ✅ 3 值 | **需裁决**：W4 是否引入。推荐 **不引入**（无支付渠道、无收款域，引入即死字段） |
| **G2** | **结算方式** `settle_type` | ✖ 无 | ✅ `ScmSettleModeEnum`（客户级，W2） | ✅ 4 值（订单级） | **复用 W2 的客户级 `settleMode`**，订单**不存**副本？或存快照？→ **需裁决**（推荐：存快照，因为客户可改） |
| **G3** | **优惠金额** `discount_amount` | ✖ 无（A 源只有 `ordered_total_amount`） | ✖ 无 | ✅ 有 | **不引入**（无促销域；用户未要求） |
| **G4** | **应付金额** `payable_amount` | ✖ 无 | ✖ 无 | ✅ 有 | **不引入**（= `ordered_total_amount`，无优惠时冗余） |
| **G5** | **期望配送时间** `expect_delivery_time` | ✖ 无 | ✖ 无 | ✅ 有 | **需裁决**；推荐**引入**（纯输入字段，无副作用，且商城下单必需） |
| **G6** | **归属业务员** `seller_id` | ✖ 无（但 `sales_order` 有 `created_by`） | ✅ `customer.sellerId`（W2） | ✅ 订单级 | **需裁决**：订单级快照 vs 从客户读。推荐 **订单级快照**（客户可改归属） |
| **G7** | **订单备注** `remark` | ✖ 无 | ✖ 无 | ✖ 无（只有 `refund_reason`） | **需裁决**；用户明确要求调查 `remark` → 推荐**引入**（`remark VARCHAR(500)`） |
| **G8** | **支付渠道调用 / 真实收款** | ✖ 无（规格 §2.2 明确排除） | ✖ 无 | ✖ 无 | **排除**（用户「Refund boundary」要求界定，不实现） |
| **G9** | **库存占用 / 可用量契约** | ✖ 无（规格 §2.2 明确排除「库存占用」） | ✖ 无 | ✅ `deliver` 直接扣减（H4） | **W4 只定义 contract，不实现**（用户明确要求）。见目标设计 §8 |
| **G10** | **退货入库 / 库存流水** | ✖ 无（规格 §2.2 明确排除） | ✖ 无 | ✅ 有 | **排除** |
| **G11** | **分拣 / 实重回写** | A 源有 `actual_quantity` **人工录入**；**无**电子秤/分拣域 | ✖ 无 | ✅ `actual_weight` | **W4 只做人工实重录入**（A 源口径）；**电子秤接入排除**（AGENTS.md 归 `xsy-device-agent`） |
| **G12** | **异常订单分析报表** | ✖ 无 | ✖ 无 | ✅ 文档 §5.5 有需求，**无实现** | **排除**（无阈值定义，需求文档 §8 自列 04-07 待确认） |
| **G13** | **消息提醒** | ✖ 无（规格 §2.2 排除） | ✅ SmartAdmin 有通知机制 | ✅ 文档有需求 | **排除**（用户未要求；需求文档 §8 自列 04-06 待确认） |
| **G14** | **订单审批** | ✖ 无 | ✖ 无 | ✖ 无（需求文档 §8 自列 04-01 待确认） | **排除**（无阈值定义） |
| **G15** | **多收货地址 / 地址簿** | ✖ 无（A-D10） | ✖ 无（`customer.address` 单字段） | ✅ `mall_order_address`（但仍是「一单一地址」） | **W4 实现「一单一地址快照」**；地址簿排除 |

### 8.1 需求文档自认的待确认项（C 侧 `04-订单管理.md` §8）

```text
04-01  订单是否需要审批（超过金额或账期超限）        → W4 排除（G14）
04-02  实重核算与下单金额差异的处置（多退少补规则）   → W4 需明确：A 源口径是「确认时按实重重算 settlement_*」，
                                                        但**不做自动多退少补**（差异体现在 settlement vs ordered 两个金额上）
04-03  补单实现口径：追加明细还是生成子单            → **A 源已定：生成新订单 + original_order_id 关联**（K10）
04-04  退货是否必须与原订单关联，是否允许无源退货     → **A 源已定：必须关联**（`order_return.order_id NOT NULL` + 行必须属原订单）
04-05  订单号规则与前缀                             → **需裁决**（K7）
04-06  消息提醒渠道                                  → W4 排除（G13）
04-07  异常订单的判定阈值                            → W4 排除（G12）
```

→ **04-03 / 04-04 已被 A 源回答**，不是缺口。**04-02 需要明确写进目标设计**。**04-05 需要裁决**。

---

## 9. 三个前置裁决项与推荐取值

### 9.1 Q1 — W4 范围（推荐：采本次指令）

**推荐**：`W4 = Sales Order`。`purchase` 顺延，编号待定（建议 W5）。

**理由**：Sales Order 在业务链上位于 Pricing 之后、Purchase 之前（`AGENTS.md` 的业务链：`Product / Customer Pricing → Order → Order Aggregation → Purchase Demand → Purchase Order`）。**W1/W2/W3 已把 Order 的全部上游依赖做完**（Product SKU、Customer + 可见性、Pricing Resolver），此时做 Order 是**依赖闭合**的；跳过 Order 直接做 purchase 会导致「采购需求从哪来」无源。

**必须同步的动作**：更新 `AGENTS.md` 与路线图文档，把 `W4 = purchase` 改为 `W4 = Sales Order`、`W5 = purchase`。**否则后续波次文档会自相矛盾。**

### 9.2 Q2 — 状态机（推荐：A 源 4 状态 + 履约字段拆分）

**推荐方案**：

```text
订单域本体 status（4 值，A 源）：
    DRAFT → PENDING → CONFIRMED
       \        \
        -------→ CANCELLED

履约语义（独立字段，不在 status 里）：
    fulfillment_status（W4 只落枚举与只读展示，不做状态推进）
        NONE → 预留
    （发货/签收/分拣/采购 由后续波次推进）
```

**理由**：

1. **A 源是业务规则唯一事实源**，且 `AGENTS.md §7.3` 明确要求「订单状态变更必须显式且可审计」「不得从无关模块直接修改订单状态」——把采购/分拣/配送状态塞进订单 `status`，正是「无关模块改订单状态」的温床（C 的 `deliver` 就是例证）。
2. **用户明确要求 Order/Inventory 边界**，而 C 的履约状态与库存扣减是**同一段代码**（H4）。拆分状态即拆分边界。
3. **W4 不实现履约**（用户：「不要偷偷实现完整库存」），因此履约状态在 W4 只能是**预留**。
4. A 源 `OrderStateTransitionPolicy` 是干净的 4 状态策略类，**已有单测**，可直接移植。

**不推荐的方案**：直接采 C 的 12 状态。理由：会把 W4 拖入采购/分拣/配送/库存四个未启动的域。

**若用户坚持 12 状态**：目标设计需重做（状态转换表 12×12、Vue 页面从 4 个变 8+ 个、Playwright 用例翻倍），且**必须同时实现库存占用**，与「W4 不要偷偷实现完整库存」冲突。

### 9.3 Q3 — 精度（推荐：A 源 + V2 冻结，无实质分歧）

**推荐**：`NUMERIC(18,4)` + `BigDecimal` + `HALF_UP scale 4` + 对外 4 位定点字符串。

**理由**：A 源设计规格、A 源代码、V2 冻结约定（`ScmFixedScale4Serializer.SCALE=4`、`ScmDecimalStrings.SCALE=4`、V1–V12 全部 `NUMERIC(18,4)`）**三方一致**。C 的「2 位到分」是 C 的独有偏离。

**唯一需要明确的**：C 的 `04-订单.sql` 注释与需求文档都写「最终金额 `HALF_UP` 四舍五入到分（G-04）」。**V2 不采**——V2 的最终金额也是 4 位。若要「展示时到分」，那是**前端格式化问题**，不是存储精度问题。目标设计 §5 需明确：**存储 4 位、传输 4 位、前端可选展示 2 位**。

### 9.4 Q4 — 锁价时机（**新增裁决项，审计过程中发现**）

用户指令写：「**Pricing Resolver ↓ Order 创建时价格快照 ↓ 后续订单不能因为价格中心变动而重算历史成交价**」。

但 A 源的实际语义是 **「提交时锁价」**，不是「创建时锁价」：

```text
create()   → resolve() 软模式 → 写 draft_unit_price（**可被后续 update 覆盖**）
update()   → resolve() 重新取价 → 覆盖 draft_unit_price（**草稿价会随价格中心变动**）
submit()   → resolve() 严模式 → 写 locked_unit_price（**此后不再变**）
confirm()  → 用 locked_unit_price × actual_quantity 算 settlement_*
```

**这是一个真实的语义差异**，必须裁决：

| 选项 | 语义 | 后果 |
| --- | --- | --- |
| **(A) 提交锁价（A 源口径，推荐）** | 草稿价随价格中心变动；提交后冻结 | 符合 A 源；「历史成交价」= 已提交订单的价格，**不会被重算** ✅ |
| **(B) 创建锁价** | 创建即冻结，草稿也不变 | 与 A 源冲突；草稿改价需另开机制；`draft_*`/`locked_*` 双轨失去意义 |

**推荐 (A)**。理由：用户的核心诉求是「**后续订单不能因为价格中心变动而重算历史成交价**」——A 源口径**完全满足**这一点（`locked_*` 一旦写入，`update`/`submit` 都不会再解析非人工改价行）。而 (B) 会破坏 A 源的草稿语义，且与 W3 Resolver 的「实时取价」定位冲突。

**需在目标设计里明确写出**：`draft_*` 是「当前时点取价」，**会随价格中心变动**；`locked_*` 是「成交价快照」，**永不重算**。前端必须**视觉区分**两者（A 源规格 §9 原文：「提交前提示将刷新并锁定价格」）。

---

## 10. 审计结论

### 10.1 可复用性总评

| 来源 | 可复用程度 | 说明 |
| --- | --- | --- |
| **A 源（legacy）** | **高（业务规则）/ 中（代码）** | 业务规则、DDL 形状、状态机、金额契约、幂等设计、审计设计**全部可移植**；但代码需按 V2 分层重写（`module/scm/order/**` + SmartAdmin 基础设施） |
| **B 源（V2）** | **高（基础设施 + 上游契约）** | 认证/权限/响应/异常/乐观锁/序列化/分页**直接复用**；Product/Customer/Pricing **直接注入** |
| **C 源** | **低（后端）/ 中（前端）** | 后端**不可复制**（22 缺陷 + MySQL 方言 + 建模冲突）；前端**骨架可复制**（4 页面，1567 行），枚举与 API 必须重写 |

### 10.2 W4 的边界（一句话）

> **W4 = 把 A 源的订单聚合（4 状态、双轨价格快照、差量同步、幂等、审计）用 V2 的 SmartAdmin 基础设施 + W1/W2/W3 上游契约重建为正式 Sales Order 域；履约/库存/支付/报表/通知一律排除，只留契约。**

### 10.3 交付物

```text
本文件                                  docs/architecture/2026-09-16-w4-order-audit.md
目标设计                                docs/architecture/2026-09-16-w4-order-target-design.md
Migration（设计，不执行）                V13__scm_sales_order.sql
                                       V14__scm_sales_order_permissions.sql
```

### 10.4 编码前必须完成

```text
1. 用户对 Q1 / Q2 / Q3 / Q4 明确拍板
2. 用户对 §6.5「编号生成：PG sequence vs SmartAdmin SerialNumberService」拍板
3. 用户对 G1 / G2 / G5 / G6 / G7 五个字段的取舍拍板
4. 用户确认「W4 = Sales Order」写入 AGENTS.md 与路线图
```

---

## 附录 A：审计证据索引

### A.1 A 源（legacy `95a5423`）—— 命令可复现

```bash
# 订单域文件全集（70 个）
git ls-tree -r --name-only 95a5423 | grep -E 'com/xianshuyuan/scm/order/'

# 迁移
git cat-file blob 95a5423:xsy-scm-server/src/main/resources/db/migration/V4__create_sales_order_schema.sql
git cat-file blob 95a5423:xsy-scm-server/src/main/resources/db/migration/V5__create_order_after_sales_schema.sql
git cat-file blob 95a5423:xsy-scm-server/src/main/resources/db/migration/V30__add_mall_order_source_and_address_snapshot.sql

# 状态机 / 金额 / 校验 / 差量 / 幂等
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/OrderStateTransitionPolicy.java
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/OrderAmountCalculator.java
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/SalesOrderValidator.java
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/SalesOrderItemChangeSet.java
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/IdempotencyService.java

# 应用服务（核心）
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/SalesOrderApplicationService.java

# 错误码
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/OrderErrorCodes.java
git cat-file blob 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/order/service/AfterSalesErrorCodes.java

# 设计规格
git cat-file blob 95a5423:docs/superpowers/specs/2026-09-03-sprint-2-sales-order-design.md

# 可达性证明
git merge-base --is-ancestor 95a5423 803a862   # → YES
git cat-file -e 803a862:.../order/service/SalesOrderApplicationService.java  # → ABSENT
```

### A.2 B 源（V2）关键路径

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/
  common/error/ScmErrorCode.java
  common/error/ScmCommonErrorCode.java
  common/exception/ScmBusinessException.java
  common/handler/ScmExceptionHandler.java
  common/json/ScmFixedScale4Serializer.java
  common/json/JsonbStringMapTypeHandler.java
  common/util/ScmDecimalStrings.java
  common/constant/ScmOperator.java
  pricing/service/PriceResolver.java
  pricing/domain/vo/ResolvedPriceVO.java
  pricing/constant/{ScmPriceSourceEnum,ScmPriceStatusEnum,ScmUnpricedReasonEnum,ScmUnavailableReasonEnum,PricingErrorCode}.java
  customer/service/CustomerService.java
  customer/domain/vo/CustomerDetailVO.java
  customer/constant/CustomerErrorCode.java
  product/domain/vo/ProductSkuOptionVO.java
xsy-scm-server/sa-admin/src/main/resources/db/migration/V1..V12（冻结）
xsy-scm-web/src/{api,views,constants,components}/**/scm/**
```

### A.3 C 源关键路径

```text
project-reference-examples/xsy-scm/xsy-scm-server/xsy-scm-server/src/main/java/com/xsy/scm/admin/module/business/order/
  constant/OrderStatusEnum.java（12 值）
  service/SaleOrderService.java（3 个硬编码转换 + deliver 扣库存）
  service/SaleRefundService.java（H1）
  mapper XML: SaleOrderMapper.xml（H11 INSTR / H12 SELECT *）
project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/*.vue（4 文件 1567 行）
project-reference-examples/xsy-scm/postgresql/04-订单.sql（H16–H18, H21, H22）
project-reference-examples/xsy-scm/docs/requirement/04-订单管理.md（183 行，12 状态机）
project-reference-examples/xsy-scm/docs/database/03-订单.sql（MySQL 原版）
```

### A.4 仓库内文档

```text
AGENTS.md                                                      §7.3 Orders
docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md       路线图（W4=purchase）
docs/architecture/2026-09-15-root-promotion-report.md           W4 NOT STARTED
docs/architecture/2026-09-15-w3-pricing-legacy-audit.md         §2.3.7 sales_order_item 价格列
docs/architecture/2026-09-15-w3-pricing-target-design.md        P13/P18/Q3
docs/architecture/2026-09-16-smartadmin-postgresql-closure-report.md  GO
```

---

## 附录 B：不变量覆盖清单（W4 验收对账用）

| 组 | 不变量 | A 源证据 | 目标设计章节 | 测试层 |
| --- | --- | --- | --- | --- |
| 聚合 | O1 聚合根唯一写入口 | `SalesOrderApplicationService` | §2.2 / §6.1 | 单测 + Web |
| 聚合 | O2 差量同步 + version | `SalesOrderItemChangeSet` | §6.4 | 单测 + PG IT |
| 聚合 | O3 SKU 不重复 | `DUPLICATE_SKU(40022)` | §4 / §6.4 | 单测 + PG IT |
| 聚合 | O4 sort_order | `materialize()` | §4 | 单测 |
| 快照 | O5 SKU/规格/单位/类型快照 | 7 个 snapshot 列 | §4 | PG IT |
| 快照 | O6 客户编码/名称快照 | `customer_*_snapshot` | §4 | PG IT |
| 快照 | O7 地址不可变追加表 | `mall_order_address` | §4 | PG IT |
| 快照 | O8 双轨价格 | `draft_*` / `locked_*` | §4 / §6.3 | 单测 + PG IT |
| 快照 | O9 提交重解析并锁定 | `submit()` | §6.3 | PG IT |
| 快照 | O10 人工改价不重解析 | `manualPriceOverride` 分支 | §6.3 | 单测 |
| 快照 | O11 改价必须带原因 | `ck_sales_order_item_manual_price` | §4 | 单测 + PG IT |
| 快照 | O12 下单量不被实重覆盖 | `submit()` | §6.5 | PG IT |
| 快照 | O13 标品/非标品实重分支 | `submit()` | §6.5 | 单测 |
| 状态 | O14 无通用状态端点 | 无 `PUT /status` | §5.1 | Web |
| 状态 | O15 4 条转换边 | `OrderStateTransitionPolicy` | §3.2 | 单测 |
| 状态 | O16 取消必填原因 | `CANCEL_REASON_REQUIRED` | §5.2 | 单测 + Web |
| 状态 | O17 实重仅 PENDING 非标品 | `ACTUAL_ONLY_NON_STANDARD` | §5.2 | 单测 + PG IT |
| 状态 | O18 确认前实重必须齐备 | `ACTUAL_QUANTITY_REQUIRED` | §5.2 | 单测 |
| 状态 | O19 确认重算核算金额 | `confirm()` | §6.5 | 单测 + PG IT |
| 状态 | O20 补单关联校验 | `ORIGINAL_ORDER_INVALID` | §5.2 | 单测 |
| 状态 | O21 普通订单不得有补单字段 | `ck_sales_order_supplement` | §4 | PG IT |
| 幂等 | O22 key 语义 + 重放 + 冲突 | `IdempotencyService.claim` | §7 | 单测 + PG IT |
| 幂等 | O23 双 version 乐观锁 | `@Version` | §7 | PG IT |
| 审计 | O24 只追加 + operator + before/after | `order_operation_log` | §7 | PG IT + Web |
| **合计** | **O1–O24** | | | |

---

**审计完成。等待 §10.4 的裁决项确认后进入目标设计评审。**
