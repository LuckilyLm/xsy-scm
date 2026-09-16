# W4 Sales Order 销售订单 · Target Design（目标设计）

> 波次：W4 — Sales Order（销售订单）
> 日期：2026-09-16
> 状态：**最终裁决已批准（2026-09-16）；实施与验收完成，停止于 W4。**
> 前置基线（永久固定）：SmartAdmin v3.31 / Java 21 / PostgreSQL / Flyway / Sa-Token / Vue3；
> W1 Product ✅、W2 Customer + Supplier ✅、W3 Pricing ✅、SmartAdmin PostgreSQL Closure ✅；
> **V1–V12 已冻结，禁止修改**。
> 配套文件：[`2026-09-16-w4-order-audit.md`](./2026-09-16-w4-order-audit.md)（只读审计）
> 前置报告：[`2026-09-16-smartadmin-postgresql-closure-report.md`](./2026-09-16-smartadmin-postgresql-closure-report.md)（GO）

---

## 0. 本文件的状态与前置裁决

### 0.1 本文件的定位

本文件是**设计**，不是实现计划。它把审计发现的 A 源业务规则、B 源正式契约、C 源页面骨架，收敛成一份**可直接执行**的规格。

**本设计已获编码授权；仅新增 V13/V14，V1–V12 与上游域保持冻结。**

### 0.2 四个前置裁决项（沿用审计 §9）

| 编号 | 问题 | 本设计的取值 | 状态 |
| --- | --- | --- | --- |
| **Q1** | W4 范围 | **W4 = Sales Order**；purchase 顺延 | **已批准：Purchase 顺延 W5** |
| **Q2** | 状态机 | **A 源 4 状态**（DRAFT/PENDING/CONFIRMED/CANCELLED）；W4 不建履约字段 | **已批准** |
| **Q3** | 精度 | **`NUMERIC(18,4)` / HALF_UP scale 4 / 对外 4 位定点字符串** | **已批准** |
| **Q4** | 锁价时机 | **提交时锁价**（A 源口径）；`draft_*` 随价格中心变动，`locked_*` 永不重算 | **已批准** |

### 0.3 本设计的三个硬约束（来自用户指令）

```text
1. 不允许重新造第二套 Product / Customer / Pricing 契约  → 全部注入复用（§6.3 / §6.12）
2. 不偷偷实现完整库存                                    → 只定义 contract（§7）
3. 不重新设计同功能页面                                  → Copy First + Adapt（§9 / §10）
```

### 0.4 与 legacy 的一句话对应关系

```text
legacy（A 源，95a5423）          →   V2 正式域（本设计）
com.xianshuyuan.scm.order.**     →   net.lab1024.sa.admin.module.scm.order.**
sales_order / sales_order_item   →   sales_order / sales_order_item
order_operation_log              →   order_operation_log
idempotency_record               →   idempotency_record
order_return / _item / _refund   →   order_return / _item / order_refund
mall_order_address               →   order_address_snapshot（改名，来源无关）
```

---

## 1. 范围（Scope）

### 1.1 做

```text
1. 订单聚合         sales_order（订单头）+ sales_order_item（订单明细），聚合根唯一写入口
2. 下单             后台录单（ADMIN）；预留 MALL 来源；补单（SUPPLEMENT）关联已确认原订单
3. 订单命令         创建 / 修改（差量同步）/ 提交（锁价）/ 确认（核算）/ 取消
4. 数量与实重        ordered_quantity（下单量）+ actual_quantity（实数量，标品自动/非标品人工）
5. 订单金额         ordered_line_amount / ordered_total_amount / settlement_line_amount / settlement_total_amount
6. 快照             Price Snapshot（双轨）+ Customer Snapshot + Address Snapshot + SKU Snapshot
7. 状态机           4 状态 + 显式命令端点 + 统一策略表
8. 幂等             Idempotency-Key + idempotency_record（SCM 自建，域无关）
9. 乐观锁           @Version → ScmCommonErrorCode.VERSION_CONFLICT(40921)
10. 操作日志        order_operation_log（只追加 + operator + JSONB before/after）
11. 退款与退货      order_return（4 状态）+ order_return_item + order_refund（2 状态，批准时原子生成）
12. 权限与菜单       V14：22 条 t_menu + t_role_menu
13. 前端            Copy First + Adapt：4 个页面 + 详情 + 表单 model + errors
14. 测试            单测 / PG IT / Web Test / 前端单测 / Playwright
```

### 1.2 不做（用户锁定 + legacy 事实源）

| 项 | 依据 |
| --- | --- |
| **完整库存**（余额表 / 流水 / 占用 / 可用量实现） | 用户明确「W4 不要偷偷实现完整库存」；A 源订单域对库存**零引用**；规格 §2.2 明确排除「库存占用、退货入库和库存流水」 |
| **发货 / 签收 / 分拣 / 配送** | 属履约与库存域，W4 未启动。A 源无此能力（审计 §5.2） |
| **采购中 / 采购需求汇总** | 属 purchase 域，W4 未启动 |
| **支付渠道 / 真实收款 / 对账** | 规格 §2.2 明确排除（审计 G8） |
| **应收生成** | C 的 `sign()` 生成应收，W4 无签收 → 无应收（审计 G9/H7） |
| **优惠 / 促销 / 优惠券** | A 源无 `discount_amount`（审计 G3） |
| **订单审批** | 无阈值定义（审计 G14，需求文档 04-01 自列待确认） |
| **异常订单报表** | 无阈值定义（审计 G12，需求文档 04-07 自列待确认） |
| **消息提醒** | 用户未要求；规格 §2.2 排除（审计 G13） |
| **多收货地址 / 地址簿** | V2 客户只有单一 `address` 字段（审计 G15 / A-D10） |
| **电子秤 / 设备接入** | 归 `xsy-device-agent`；W4 只做**人工**实重录入 |
| **微服务 / MQ / 分布式事务 / 分布式锁** | AGENTS.md §5 |
| **修改 V1–V12** | 用户明确冻结 |
| **修改 `module/scm/product/**`** | W1 边界（W3 已建立，W4 沿用） |
| **修改 `sa-base` 的既有行为** | SmartAdmin Native First；仅允许**追加**枚举值（§5.3 说明） |

### 1.3 交付物

```text
后端  xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/**（新增）
      xsy-scm-server/sa-admin/src/main/resources/mapper/scm/order/**（新增）
      xsy-scm-server/sa-admin/src/main/resources/db/migration/V13__scm_sales_order.sql（新增）
      xsy-scm-server/sa-admin/src/main/resources/db/migration/V14__scm_sales_order_permissions.sql（新增）
前端  xsy-scm-web/src/api/business/scm/order-*.ts（新增）
      xsy-scm-web/src/constants/business/scm/order-const.ts（新增，追加注册 src/constants/index.ts）
      xsy-scm-web/src/views/business/scm/order/**（新增）
      xsy-scm-web/e2e/scm-order.spec.ts（新增）
文档  本文件 + 审计文件 + 验收报告（编码后）
```

---

## 2. 目标架构

### 2.1 模块落点

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/
├─ constant/
│   ├─ ScmOrderStatusEnum.java            4 值：DRAFT / PENDING / CONFIRMED / CANCELLED
│   ├─ ScmOrderSourceEnum.java            3 值：ADMIN / MALL / SUPPLEMENT
│   ├─ ScmOrderPriceSourceEnum.java       4 值：AGREEMENT / CUSTOMER_TYPE / MARKET / OVERRIDE
│   ├─ ScmOrderQuantitySourceEnum.java    2 值：SYSTEM / MANUAL
│   ├─ ScmOrderProductTypeEnum.java       2 值：STANDARD / NON_STANDARD（**映射 W1 的 ProductSkuOptionVO.productType**）
│   ├─ ScmOrderOperationTypeEnum.java     6 值：CREATE / UPDATE / SUBMIT / ACTUAL_QUANTITY / CONFIRM / CANCEL
│   ├─ ScmOrderReturnStatusEnum.java      4 值：PENDING / APPROVED / REJECTED / CANCELLED
│   ├─ ScmOrderRefundStatusEnum.java      2 值：PENDING / COMPLETED
│   └─ OrderErrorCode.java                错误码表（§6.6）
├─ controller/
│   ├─ SalesOrderController.java          订单：查询 / 详情 / 日志 / 创建 / 修改 / 提交 / 确认 / 取消 / 实重
│   ├─ OrderReturnController.java         退货：查询 / 详情 / 创建 / 批准 / 驳回 / 取消
│   └─ OrderRefundController.java         退款：查询 / 详情 / 完成
├─ dao/
│   ├─ SalesOrderDao.java
│   ├─ SalesOrderItemDao.java
│   ├─ OrderAddressSnapshotDao.java
│   ├─ OrderOperationLogDao.java
│   ├─ OrderReturnDao.java
│   ├─ OrderReturnItemDao.java
│   ├─ OrderRefundDao.java
│   └─ IdempotencyRecordDao.java
├─ domain/
│   ├─ entity/   SalesOrderEntity / SalesOrderItemEntity / OrderAddressSnapshotEntity /
│   │            OrderOperationLogEntity / OrderReturnEntity / OrderReturnItemEntity /
│   │            OrderRefundEntity / IdempotencyRecordEntity
│   ├─ form/     SalesOrderAddForm / SalesOrderUpdateForm / SalesOrderQueryForm /
│   │            SalesOrderItemForm / OrderActualQuantityForm / OrderCancelForm /
│   │            OrderReturnAddForm / OrderReturnApproveForm / OrderReturnDecisionForm /
│   │            OrderReturnQueryForm / OrderRefundCompleteForm / OrderRefundQueryForm
│   └─ vo/       SalesOrderVO / SalesOrderDetailVO / SalesOrderItemVO /
│                 OrderAddressSnapshotVO / OrderOperationLogVO /
│                 OrderReturnVO / OrderReturnDetailVO / OrderReturnItemVO / OrderRefundVO
├─ manager/
│   ├─ OrderStateMachine.java            纯策略：4 状态 × 4 条转换边（可单测，无 Spring 依赖）
│   ├─ OrderAmountCalculator.java        纯函数：SCALE=4 / HALF_UP
│   ├─ OrderSnapshotFactory.java         从 CustomerDetailVO / ProductSkuOptionVO 造快照
│   ├─ OrderValidator.java               表单级校验（补单 / 重复 SKU / 数量 / 改价）
│   └─ SalesOrderItemChangeSet.java      差量同步（inserted / updated / removedIds）
├─ service/
│   ├─ SalesOrderService.java            写路径（创建/修改/提交/确认/取消/实重）
│   ├─ SalesOrderQueryService.java       读路径（分页/详情/日志）
│   ├─ OrderReturnService.java           退货写路径
│   ├─ OrderRefundService.java           退款写路径
│   ├─ OrderIdempotencyService.java      claim / complete / replay
│   └─ OrderNumberGenerator.java         单号（PG sequence，见 §14 已批准 U4）
└─ support/
    ├─ OrderIdempotencyRequestHasher.java 规范化 JSON + SHA-256
    └─ OrderIdempotencyConflictException.java
```

**Mapper XML**：

```text
xsy-scm-server/sa-admin/src/main/resources/mapper/scm/order/
├─ SalesOrderMapper.xml
├─ SalesOrderItemMapper.xml
├─ OrderOperationLogMapper.xml
├─ OrderReturnMapper.xml
├─ OrderReturnItemMapper.xml
├─ OrderRefundMapper.xml
└─ IdempotencyRecordMapper.xml
```

### 2.2 分层职责（对齐 SmartAdmin ERP 范式与 W1/W2/W3）

```text
Controller   只做参数绑定 + @SaCheckPermission + 调用 Service + 返回 ResponseDTO
Service      事务边界（@Transactional）+ 聚合装配 + 调用 Manager / 上游 Service
Manager      纯业务规则（无事务、无 IO，可 100% 单测）
Dao          单表 CRUD + 分页；复杂查询写在 Mapper XML
```

**聚合根唯一写入口（O1）**：`SalesOrderItem` **没有独立 Controller、没有独立 Service 写方法**。所有明细写入只经 `SalesOrderService.create/update/submit/confirm`。

**与 legacy 的差异**：legacy 用 `SalesOrderApplicationService` 一个类扛全部命令（17KB）；V2 拆为 `SalesOrderService`（写）+ `SalesOrderQueryService`（读）+ `OrderReturnService` / `OrderRefundService`（售后），并把纯逻辑抽到 `manager/`（**可直接移植 legacy 的纯类并单测**）。

### 2.3 与上游域的关系

```text
                    ┌───────────────────────────────┐
                    │  W1 Product（只读，零修改）      │
                    │  ProductSkuOptionDao           │
                    │    .selectByIds(skuIds)        │
                    │  → ProductSkuOptionVO          │
                    └───────────────┬───────────────┘
                                    │ 快照来源
┌───────────────────────────┐       │       ┌────────────────────────────────┐
│ W2 Customer（只读注入）     │       │       │ W3 Pricing（只读注入）           │
│ CustomerService            │       │       │ PriceResolver                  │
│  .requireTradable(id)      │       │       │  .resolve(...)     软模式       │
│  .require(id, version)     │       │       │  .requireResolvable(...) 严模式  │
│ CustomerDetailVO           │       │       │ → List<ResolvedPriceVO>         │
└───────────┬───────────────┘       │       └────────────┬───────────────────┘
            │ 客户快照 + 地址快照     │                    │ 价格快照
            ▼                       ▼                    ▼
        ┌──────────────────────────────────────────────────────────┐
        │  W4 Sales Order（本波次）                                  │
        │  sales_order / sales_order_item / order_address_snapshot  │
        │  order_operation_log / idempotency_record                 │
        │  order_return / order_return_item / order_refund          │
        └──────────────────────────┬───────────────────────────────┘
                                   │ 只定义 contract，不实现
                                   ▼
                    ┌──────────────────────────────┐
                    │  Inventory（后续波次）          │
                    │  OrderInventoryContract 接口   │
                    │  默认 NoOp 实现（W4）           │
                    └──────────────────────────────┘
```

**关键**：Order **只读**上游三个域，**不写**任何上游表。因此不存在跨域死锁。

---

## 3. 状态机

### 3.1 订单状态（4 值，A 源）

| 值 | 名称 | 语义（A 源原文） | 终态 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | 可编辑头和订单行，可保存人工改价 | ✖ |
| `PENDING` | 待确认 | 已重新校验并锁价，仅允许非标品实重录入、确认或取消 | ✖ |
| `CONFIRMED` | 已确认 | 结算完成，订单编辑终止，可创建退货 | ✖（业务终态） |
| `CANCELLED` | 已取消 | 终止状态，保留取消原因和历史快照 | ✅ |

**状态转换图**：

```text
        ┌─────────┐  submit   ┌──────────┐  confirm  ┌────────────┐
        │  DRAFT  ├──────────►│ PENDING  ├──────────►│ CONFIRMED  │
        └────┬────┘           └────┬─────┘           └────────────┘
             │ cancel              │ cancel
             │                     │
             ▼                     ▼
        ┌──────────────────────────────────┐
        │           CANCELLED              │
        └──────────────────────────────────┘
```

### 3.2 状态转换表（完整规格）

| # | from | to | 命令 | 端点 | 前置条件 | 副作用 | 幂等 | 错误码 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T1 | — | `DRAFT` | 创建 | `POST /scm/order/create` | 客户可交易；SKU 可售（软）；补单校验 | 生成单号；写客户/地址/SKU/价格快照；算 `ordered_*`；写 `CREATE` 日志 | ✅ 必须 | 40430 / 40930 / 40060 / 40061 / 40961 |
| T2 | `DRAFT` | `DRAFT` | 修改 | `POST /scm/order/update` | 状态 = DRAFT；version 匹配 | 差量同步明细；重取价；重算 `ordered_*`；写 `UPDATE` 日志 | ✖（靠 version） | 40960 / 40921 / 40964 / 40965 |
| T3 | `DRAFT` | `PENDING` | 提交 | `POST /scm/order/submit` | 状态 = DRAFT；version 匹配；**严模式取价通过** | 重解析非改价行 → 写 `locked_*`；标品 `actual := ordered`；非标品 `actual := NULL`；重算 `ordered_*`；写 `SUBMIT` 日志 | ✅ 必须 | 40960 / 40921 / 40949 / 40963 |
| T4 | `PENDING` | `PENDING` | 实重录入 | `POST /scm/order/item/actual-quantity` | 状态 = PENDING；行是非标品；行 version 匹配；reason 非空；qty > 0 | 写 `actual_quantity` / `actual_quantity_source=MANUAL` / `actual_quantity_reason`；写 `ACTUAL_QUANTITY` 日志（含前后值） | ✅ 必须 | 40960 / 40962 / 40965 / 40063 / 40067 |
| T5 | `PENDING` | `CONFIRMED` | 确认 | `POST /scm/order/confirm` | 状态 = PENDING；version 匹配；**所有行 `actual_quantity > 0`** | 每行 `settlement_line_amount := round(actual × locked, 4)`；`settlement_total_amount := Σ`；写 `CONFIRM` 日志 | ✅ 必须 | 40960 / 40921 / 40963 |
| T6 | `DRAFT` | `CANCELLED` | 取消 | `POST /scm/order/cancel` | 策略表允许；version 匹配；reason 非空 | 写 `cancel_reason` / `cancelled_at`；写 `CANCEL` 日志 | ✅ 必须 | 40960 / 40921 / 40068 |
| T7 | `PENDING` | `CANCELLED` | 取消 | 同上 | 同上 | 同上 | ✅ 必须 | 同上 |

**非法转换（必须拒绝）**：

```text
DRAFT      → CONFIRMED       40960 ORDER_STATE_INVALID
PENDING    → DRAFT           40960 ORDER_STATE_INVALID
CONFIRMED  → 任意（含 CANCELLED）  40960 ORDER_STATE_INVALID
CANCELLED  → 任意            40960 ORDER_STATE_INVALID
任意       → 任意（无端点）    —（不存在通用状态端点）
```

**实现要求（修 A-D4）**：`T3`–`T7` **全部**经 `OrderStateMachine.canTransition(from, to)` 判定，**禁止**任何 `requireState(o, X)` 式的硬编码状态比较。

### 3.3 履约边界（预留，W4 不推进）

C 源的 `PURCHASING(4)` / `SORTING(5)` / `SORTED(6)` / `DELIVERING(7)` / `SIGNED(8)` / `COMPLETED(9)` / `REFUNDING(10)` / `INVALID(12)` **不进入 W4 的 `status`**。

**V13 不创建 `fulfillment_status`**。W4 无履约能力，不预埋只有 NONE 值的死字段。后续 Fulfillment / Inventory 波次通过新的 Flyway migration 新增履约字段与状态。W4 不实现余额、占用、流水或出库，只定义未来集成契约。

### 3.4 退货与退款状态机（A 源）

**`order_return`（4 状态）**：

```text
                 approve
    PENDING ──────────────────► APPROVED ──► （生成 PENDING 退款单）
       │
       ├──────► REJECTED（必须填 decision_reason）
       └──────► CANCELLED（必须填 decision_reason）
```

| # | from | to | 端点 | 前置条件 | 副作用 |
| --- | --- | --- | --- | --- | --- |
| R1 | — | `PENDING` | `POST /scm/order/return/create` | 原订单 = `CONFIRMED`；行属原订单；数量 ≤ 可退额度 | 生成 `return_no`；写退货行（含 `locked_unit_price` 快照） |
| R2 | `PENDING` | `APPROVED` | `POST /scm/order/return/approve` | 状态 = PENDING；至少一行批准数量 > 0；≤ requested | 写 `approved_quantity` / `approved_amount` / `approved_at`；**原子创建 1 个 `PENDING` 退款单** |
| R3 | `PENDING` | `REJECTED` | `POST /scm/order/return/reject` | 状态 = PENDING；`decision_reason` 非空 | 写 `decision_reason` / `rejected_at` |
| R4 | `PENDING` | `CANCELLED` | `POST /scm/order/return/cancel` | 状态 = PENDING；`decision_reason` 非空 | 写 `decision_reason` / `cancelled_at` |

**可退额度公式（A 源 `AfterSalesRules`）**：

```text
remaining = originalActualQuantity - pendingReserved - approvedQuantity
要求：requested > 0 且 requested ≤ remaining
```

**`order_refund`（2 状态）**：

| # | from | to | 端点 | 前置条件 | 副作用 |
| --- | --- | --- | --- | --- | --- |
| F1 | — | `PENDING` | （由 R2 内部创建，**无独立端点**） | — | 金额 = Σ `approved_amount` |
| F2 | `PENDING` | `COMPLETED` | `POST /scm/order/refund/complete` | 状态 = PENDING；`external_reference` 可选且唯一 | 写 `completed_at` / `external_reference` |

**边界（用户要求明确）**：`F2` **只改状态与凭证，不调用支付渠道、不产生库存动作、不生成会计凭证**。

---

## 4. 数据表与字段快照

### 4.1 表清单（8 张，全部新增）

| # | 表 | 用途 | 来源 |
| --- | --- | --- | --- |
| 1 | `sales_order` | 订单头 | A 源 `V4` + 修正 A-D2 |
| 2 | `sales_order_item` | 订单明细 | A 源 `V4` + 修正 A-D1 / A-D11 |
| 3 | `order_address_snapshot` | 地址快照 | A 源 `V30` `mall_order_address`（改名 + 适配 V2 客户单地址模型） |
| 4 | `order_operation_log` | 业务操作日志 | A 源 `V4` |
| 5 | `idempotency_record` | 幂等记录（SCM 共享） | A 源 `V4` |
| 6 | `order_return` | 退货单 | A 源 `V5` |
| 7 | `order_return_item` | 退货明细 | A 源 `V5` |
| 8 | `order_refund` | 退款单 | A 源 `V5` |

**通用列约定（可变业务表；地址与操作日志的例外见各自 DDL）**：

```text
id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
version       INTEGER  NOT NULL DEFAULT 0        CHECK (version >= 0)     -- 除 order_operation_log
deleted       BOOLEAN  NOT NULL DEFAULT FALSE                             -- 除 order_operation_log
created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
created_by    VARCHAR(64)
updated_by    VARCHAR(64)
```

**禁止**：外键、`ON UPDATE CURRENT_TIMESTAMP` 触发器、`DROP TABLE`、`deleted_flag smallint`、`DATETIME`、`AUTO_INCREMENT`、MySQL 方言函数。

### 4.2 `sales_order`

```sql
CREATE TABLE sales_order (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_code_snapshot VARCHAR(64) NOT NULL,
    customer_name_snapshot VARCHAR(150) NOT NULL,
    order_source VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    original_order_id BIGINT,
    supplement_reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ordered_total_amount NUMERIC(18,4),
    settlement_total_amount NUMERIC(18,4),
    settle_mode_snapshot VARCHAR(20) NOT NULL,
    expect_delivery_time TIMESTAMPTZ,
    seller_id BIGINT,
    remark VARCHAR(500),
    cancel_reason VARCHAR(500),
    submitted_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_sales_order_source CHECK (order_source IN ('ADMIN', 'MALL', 'SUPPLEMENT')),
    CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_sales_order_supplement CHECK (
        (order_source <> 'SUPPLEMENT' AND original_order_id IS NULL AND supplement_reason IS NULL)
        OR (order_source = 'SUPPLEMENT' AND supplement_reason IS NOT NULL AND btrim(supplement_reason) <> '')
    ),
    CONSTRAINT ck_sales_order_ordered_total_amount CHECK (ordered_total_amount >= 0),
    CONSTRAINT ck_sales_order_settlement_total_amount CHECK (settlement_total_amount IS NULL OR settlement_total_amount >= 0),
    CONSTRAINT ck_sales_order_cancel_reason CHECK (status <> 'CANCELLED' OR (cancel_reason IS NOT NULL AND btrim(cancel_reason) <> '')),
    CONSTRAINT ck_sales_order_version CHECK (version >= 0)
);
```

**对 A 源的修正**：

| 修正 | 说明 |
| --- | --- |
| `ck_sales_order_source` 含 `ADMIN`/`MALL`/`SUPPLEMENT` | A 源用 `NORMAL`；V2 改 `ADMIN` 更贴合语义（「后台录单」）。U5 已批准 ADMIN/MALL/SUPPLEMENT |
| `ck_sales_order_supplement` 改为 `<> 'SUPPLEMENT'` 分支 | **修 A-D2**：A 源的 `= 'NORMAL'` 会让 `MALL` 订单违反 CHECK |
| 不建 `fulfillment_status` | 后续履约波次新增 migration |
| 新增 `settle_mode_snapshot` / `expect_delivery_time` / `seller_id` / `remark` | U2/U3 已批准；结算方式与业务员在创建时从客户快照，后两类输入可空，业务员可空 |
| 新增 `ck_sales_order_cancel_reason` | A 源只在应用层校验取消原因；V2 加 DB 级兜底 |
| **不含** `pay_status` / `actual_weight` / `discount_amount` / `payable_amount` / `actual_amount` | U2/U3 已批准；重量使用 actual_quantity + sale_unit_snapshot |

### 4.3 `sales_order_item`

```sql
CREATE TABLE sales_order_item (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    spu_code_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(150) NOT NULL,
    sku_code_snapshot VARCHAR(64) NOT NULL,
    spec_name_snapshot VARCHAR(150) NOT NULL,
    spec_values_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
    sale_unit_snapshot VARCHAR(32) NOT NULL,
    product_type_snapshot VARCHAR(20) NOT NULL,
    ordered_quantity NUMERIC(18,4) NOT NULL,
    actual_quantity NUMERIC(18,4),
    actual_quantity_source VARCHAR(20),
    actual_quantity_reason VARCHAR(500),
    draft_unit_price NUMERIC(18,4),
    draft_price_source VARCHAR(20),
    draft_price_source_id BIGINT,
    manual_price_override BOOLEAN NOT NULL DEFAULT FALSE,
    manual_price_reason VARCHAR(500),
    locked_unit_price NUMERIC(18,4),
    locked_price_source VARCHAR(20),
    locked_price_source_id BIGINT,
    ordered_line_amount NUMERIC(18,4),
    settlement_line_amount NUMERIC(18,4),
    sort_order INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_sales_order_item_spec_values CHECK (jsonb_typeof(spec_values_snapshot) = 'object'),
    CONSTRAINT ck_sales_order_item_product_type CHECK (product_type_snapshot IN ('STANDARD', 'NON_STANDARD')),
    CONSTRAINT ck_sales_order_item_ordered_quantity CHECK (ordered_quantity > 0),
    CONSTRAINT ck_sales_order_item_actual_quantity CHECK (actual_quantity IS NULL OR actual_quantity > 0),
    CONSTRAINT ck_sales_order_item_actual_source CHECK (actual_quantity_source IS NULL OR actual_quantity_source IN ('SYSTEM', 'MANUAL')),
    -- 修 A-D11：MANUAL 必须有 reason；非 MANUAL 不得有 reason
    CONSTRAINT ck_sales_order_item_actual_reason CHECK (
        (actual_quantity_source = 'MANUAL' AND actual_quantity_reason IS NOT NULL AND btrim(actual_quantity_reason) <> '')
        OR (actual_quantity_source IS DISTINCT FROM 'MANUAL' AND actual_quantity_reason IS NULL)
    ),
    CONSTRAINT ck_sales_order_item_draft_price CHECK (draft_unit_price >= 0),
    -- 修 A-D1：白名单补全 CUSTOMER_TYPE（A 源 V4 漏了）
    CONSTRAINT ck_sales_order_item_draft_source CHECK (draft_price_source IN ('AGREEMENT', 'CUSTOMER_TYPE', 'MARKET', 'OVERRIDE')),
    CONSTRAINT ck_sales_order_item_draft_pair CHECK ((draft_unit_price IS NULL) = (draft_price_source IS NULL)),
    CONSTRAINT ck_sales_order_item_draft_amount CHECK ((draft_unit_price IS NULL) = (ordered_line_amount IS NULL)),
    CONSTRAINT ck_sales_order_item_manual_price CHECK (
        (manual_price_override = FALSE AND draft_price_source IS DISTINCT FROM 'OVERRIDE' AND manual_price_reason IS NULL)
        OR (manual_price_override = TRUE AND draft_unit_price IS NOT NULL AND draft_price_source IS NOT NULL AND draft_price_source = 'OVERRIDE'
            AND manual_price_reason IS NOT NULL AND btrim(manual_price_reason) <> '')
    ),
    CONSTRAINT ck_sales_order_item_locked_price CHECK (locked_unit_price IS NULL OR locked_unit_price >= 0),
    CONSTRAINT ck_sales_order_item_locked_source CHECK (locked_price_source IS NULL OR locked_price_source IN ('AGREEMENT', 'CUSTOMER_TYPE', 'MARKET', 'OVERRIDE')),
    CONSTRAINT ck_sales_order_item_ordered_line_amount CHECK (ordered_line_amount >= 0),
    CONSTRAINT ck_sales_order_item_settlement_line_amount CHECK (settlement_line_amount IS NULL OR settlement_line_amount >= 0),
    CONSTRAINT ck_sales_order_item_version CHECK (version >= 0)
);
```

**对 A 源的修正**：

| 修正 | 说明 |
| --- | --- |
| `draft_price_source` 补 `CUSTOMER_TYPE` | **修 A-D1**（A 源 `V4` 漏了，W3 文档的「白名单已含」表述与 A 源实际不符） |
| `locked_price_source` 补 `CUSTOMER_TYPE` | 同上 |
| 新增 `ck_sales_order_item_actual_reason` | **修 A-D11** |
| 新增 `uk_sales_order_item_order_sku_active` 部分唯一索引 | **修 A 源只在应用层查重**（O3）—— 见 §5.4 |
| `actual_weight` **不引入** | 审计 G11：A 源无 `actual_weight`（只有 `actual_quantity`）；C 有 `actual_weight`。**用户要求调查 `重量`** → 见 §14 U2 |

### 4.4 `order_address_snapshot`

```sql
CREATE TABLE order_address_snapshot (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    address VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)
);
```

**设计说明**：

- **来源**：V2 客户的 `CustomerDetailVO.contactName` / `contactPhone` / `address`（**单一地址字段**，审计 §6.2）。
- **不可变**：**无 `version` / `deleted` / `updated_at`**（沿用 A 源 `mall_order_address` 的不可变追加语义，审计 §3.3.6 / A-D3）。
- **一单一地址**：`uk_order_address_snapshot_order` 唯一索引（见 §5.4）。多地址是已知限制（A-D10 / G15）。
- **改名理由**：A 源叫 `mall_order_address`，把快照绑死在「商城」来源；V2 后台录单同样需要地址快照（审计 A-D3）。
- **创建时机**：与订单同事务创建，**永不更新**。若客户后续改地址，历史订单地址不变。

### 4.5 `order_operation_log`

```sql
CREATE TABLE order_operation_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    reason VARCHAR(500),
    before_data JSONB,
    after_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT ck_order_operation_log_type CHECK (
        operation_type IN ('CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY', 'CONFIRM', 'CANCEL')
    ),
    CONSTRAINT ck_order_operation_log_before_data CHECK (before_data IS NULL OR jsonb_typeof(before_data) = 'object'),
    CONSTRAINT ck_order_operation_log_after_data CHECK (after_data IS NULL OR jsonb_typeof(after_data) = 'object')
);
```

**对 A 源的修正**：

| 修正 | 说明 |
| --- | --- |
| `operator VARCHAR(64) NOT NULL`（**去掉 A 源的 `DEFAULT 'SYSTEM'`**） | **修 A-D8**：A 源有默认值且应用层写死 `'SYSTEM'`（A-D7），导致审计失效 |
| `operator` 写入 `ScmOperator.current()` | **修 A-D7**：格式 `{userType}:{userId}`，与 W2/W3 的 `created_by` 约定一致 |
| 新增 `ck_order_operation_log_type` | A 源无类型白名单（`VARCHAR(40)` 裸字段） |
| **无 `version` / `deleted`** | 只追加，正确（沿用 A 源） |

### 4.6 `idempotency_record`

```sql
CREATE TABLE idempotency_record (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    operation_scope VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    result_type VARCHAR(100),
    result_id BIGINT,
    result_data JSONB,
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_idempotency_record_result_data CHECK (result_data IS NULL OR jsonb_typeof(result_data) = 'object'),
    CONSTRAINT ck_idempotency_record_version CHECK (version >= 0)
);
```

**定位**：SCM **域无关**的幂等基础设施（V2 没有通用幂等组件，审计 §6.5）。表名不带 `order_` 前缀，后续波次可直接复用。

### 4.7 售后三表

```sql
CREATE TABLE order_return (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NOT NULL,
    decision_reason VARCHAR(500),
    approved_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    approved_at TIMESTAMPTZ, rejected_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_order_return_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_order_return_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_order_return_decision_reason CHECK (
        status NOT IN ('REJECTED', 'CANCELLED') OR (decision_reason IS NOT NULL AND btrim(decision_reason) <> '')
    ),
    CONSTRAINT ck_order_return_approved_amount CHECK (approved_amount >= 0),
    CONSTRAINT ck_order_return_version CHECK (version >= 0)
);

CREATE TABLE order_return_item (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    requested_quantity NUMERIC(18,4) NOT NULL,
    approved_quantity NUMERIC(18,4),
    locked_unit_price NUMERIC(18,4) NOT NULL,
    approved_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_order_return_item_requested_quantity CHECK (requested_quantity > 0),
    CONSTRAINT ck_order_return_item_approved_quantity CHECK (approved_quantity IS NULL OR (approved_quantity >= 0 AND approved_quantity <= requested_quantity)),
    CONSTRAINT ck_order_return_item_locked_price CHECK (locked_unit_price >= 0),
    CONSTRAINT ck_order_return_item_approved_amount CHECK (approved_amount >= 0),
    CONSTRAINT ck_order_return_item_version CHECK (version >= 0)
);

CREATE TABLE order_refund (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    return_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    refund_amount NUMERIC(18,4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_reference VARCHAR(128),
    completed_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64), updated_by VARCHAR(64),
    CONSTRAINT ck_order_refund_amount CHECK (refund_amount > 0),
    CONSTRAINT ck_order_refund_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_order_refund_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_order_refund_version CHECK (version >= 0)
);
```

**与 A 源完全一致**（A 源 `V5` 的 CHECK 设计质量很高，无需修正）。

### 4.8 快照矩阵（用户明确要求调查的四类快照）

| 快照 | 落地位置 | 字段 | 来源 | 时机 | 可变性 |
| --- | --- | --- | --- | --- | --- |
| **Price Snapshot** | `sales_order_item` | `draft_unit_price` / `draft_price_source` / `draft_price_source_id` | `PriceResolver.resolve()` / 人工改价 | 创建 / 修改 / 提交 | `draft_*` 随价格中心变动；`locked_*` 冻结 |
| **Price Snapshot（锁定）** | `sales_order_item` | `locked_unit_price` / `locked_price_source` / `locked_price_source_id` | 提交时 `requireResolvable()` | **提交** | **永不重算** |
| **Price Snapshot（改价）** | `sales_order_item` | `manual_price_override` / `manual_price_reason` | 用户输入 | 创建 / 修改 | 提交前可改 |
| **Customer Snapshot** | `sales_order` | `customer_id` / `customer_code_snapshot` / `customer_name_snapshot` / `settle_mode_snapshot` / `seller_id` | `CustomerService.requireTradable()` → `CustomerEntity` | 创建 | 永不重算 |
| **Address Snapshot** | `order_address_snapshot` | `receiver_name` / `receiver_phone` / `address` | `CustomerDetailVO.contactName/contactPhone/address` | 创建 | **不可变追加表** |
| **SKU Snapshot** | `sales_order_item` | `spu_id` / `sku_id` / `spu_code_snapshot` / `product_name_snapshot` / `sku_code_snapshot` / `spec_name_snapshot` / `spec_values_snapshot` / `sale_unit_snapshot` / `product_type_snapshot` | `ProductSkuOptionDao.selectByIds()` → `ProductSkuOptionVO` | 创建 / 修改 | 提交后不再重解析 |

**「历史成交价不重算」的精确保证（Q4）**：

```text
不变量：一旦 status ∈ {PENDING, CONFIRMED}，则：
  - locked_unit_price 不再被任何代码路径写入
  - ordered_quantity 不再被任何代码路径写入
  - ordered_line_amount / ordered_total_amount 不再被写入
  - settlement_* 只在 CONFIRM 时写入一次
证据：SalesOrderService 中所有写 locked_* 的代码只存在于 submit()；
      所有写 ordered_quantity 的代码只存在于 create()/update()（两者都要求 status=DRAFT）
```

---

## 5. Migration 设计

### 5.1 命名与版本

```text
现状：V1–V12 已应用并冻结（next Flyway version = V13，实测确认）
新增：
  V13__scm_sales_order.sql                 8 张表 + 索引 + 3 个序列 + 约束
  V14__scm_sales_order_permissions.sql     22 条 t_menu + t_role_menu + setval
```

**编号占用检查（实测）**：

```text
ls xsy-scm-server/sa-admin/src/main/resources/db/migration/
V1  V2  V3  V4  V5  V6  V7  V8  V9  V10  V11  V12   ← 无 V13 占用
```

**若执行前发现 V13/V14 已被占用**：顺延到实际 next（V15/V16…），**不修改 V1–V12**。

**冻结要求**：V13/V14 首次成功应用后计算 SHA-256，追加进 `docs/architecture/pg-closure-applied-migrations.sha256` 同级的**新阶段 manifest**（`docs/architecture/w4-applied-migrations.sha256`），**不污染** `pg-closure-applied-migrations.sha256`（该文件记录 V1–V12 的 PG 收口状态）。

### 5.2 `V13__scm_sales_order.sql` 结构

```sql
-- W4 approved Sales Order aggregate. Immutable after first successful Flyway application.
-- PostgreSQL only. No foreign keys. No triggers. No DROP.

-- 0) 单号序列（见 §14 U4：本设计采 PG sequence，不用 SmartAdmin SerialNumberService）
CREATE SEQUENCE sales_order_no_seq  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE order_return_no_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE order_refund_no_seq START WITH 1 INCREMENT BY 1;

-- 1) sales_order                     §4.2
-- 2) sales_order_item                §4.3
-- 3) order_address_snapshot          §4.4
-- 4) order_operation_log             §4.5
-- 5) idempotency_record              §4.6
-- 6) order_return                    §4.7
-- 7) order_return_item               §4.7
-- 8) order_refund                    §4.7

-- 9) 索引（§5.4）
-- 10) COMMENT ON TABLE / COLUMN（全部中文注释，与 W1/W2/W3 一致）
```

**PostgreSQL 规范符合性自检**：

| 规范 | 落实 |
| --- | --- |
| `IDENTITY` | 全部 8 表用 `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` |
| `BOOLEAN` | `deleted` / `manual_price_override` 全部 `BOOLEAN`（**不用 `smallint`**） |
| `TIMESTAMPTZ` | 全部时间列 `TIMESTAMPTZ`（**不用 `timestamp` / `DATETIME`**） |
| `NUMERIC` | 金额与数量统一 `NUMERIC(18,4)` |
| `JSONB` | `spec_values_snapshot` / `before_data` / `after_data` / `result_data` —— **确有快照/审计需要**，非滥用 |
| explicit index | §5.4 逐条给理由 |
| partial unique/index | §5.4，**确有语义时才用**（`WHERE deleted = FALSE` 与 `WHERE original_order_id IS NOT NULL`） |
| 无外键 | 全部关系靠服务事务 + 索引校验（AGENTS.md 禁止外键） |
| 无触发器 | `updated_at` 由 MyBatis-Plus `MetaObjectHandler` 维护（SmartAdmin 原生） |
| 幂等 DDL | `CREATE TABLE` / `CREATE INDEX` / `CREATE SEQUENCE` 均无 `IF NOT EXISTS`（Flyway 保证只跑一次） |

### 5.3 `V14__scm_sales_order_permissions.sql` 结构

**菜单 ID 分配（601+，实测 601 起未占用）**：

| menu_id | menu_name | menu_type | parent_id | path | component | api_perms / web_perms |
| --- | --- | --- | --- | --- | --- | --- |
| **601** | 销售订单 | 1 | 0 | `/order` | NULL | — |
| **602** | 订单列表 | 2 | 601 | `/order/order-list` | `/business/scm/order/order-list.vue` | — |
| **603** | 退货单 | 2 | 601 | `/order/order-return-list` | `/business/scm/order/order-return-list.vue` | — |
| **604** | 退款单 | 2 | 601 | `/order/order-refund-list` | `/business/scm/order/order-refund-list.vue` | — |
| **605** | 订单操作日志 | 2 | 601 | `/order/order-log-list` | `/business/scm/order/order-log-list.vue` | — |
| **611** | 查询 | 3 | 602 | NULL | NULL | `scm:order:query` |
| **612** | 新建 | 3 | 602 | NULL | NULL | `scm:order:add` |
| **613** | 编辑 | 3 | 602 | NULL | NULL | `scm:order:update` |
| **614** | 提交 | 3 | 602 | NULL | NULL | `scm:order:submit` |
| **615** | 确认 | 3 | 602 | NULL | NULL | `scm:order:confirm` |
| **616** | 取消 | 3 | 602 | NULL | NULL | `scm:order:cancel` |
| **617** | 实重录入 | 3 | 602 | NULL | NULL | `scm:order:actual-quantity` |
| **618** | 改价 | 3 | 602 | NULL | NULL | `scm:order:price-override` |
| **619** | 删除 | 3 | 602 | NULL | NULL | `scm:order:delete` |
| **621** | 查询 | 3 | 603 | NULL | NULL | `scm:order:return:query` |
| **622** | 新建 | 3 | 603 | NULL | NULL | `scm:order:return:add` |
| **623** | 批准 | 3 | 603 | NULL | NULL | `scm:order:return:approve` |
| **624** | 驳回 | 3 | 603 | NULL | NULL | `scm:order:return:reject` |
| **625** | 取消 | 3 | 603 | NULL | NULL | `scm:order:return:cancel` |
| **631** | 查询 | 3 | 604 | NULL | NULL | `scm:order:refund:query` |
| **632** | 完成 | 3 | 604 | NULL | NULL | `scm:order:refund:complete` |
| **641** | 查询 | 3 | 605 | NULL | NULL | `scm:order:log:query` |

**共 22 条 `t_menu`**。

**SQL 形状**（沿用 V11 的写法，逐条 `ON CONFLICT (menu_id) DO NOTHING`）：

```sql
-- W4 approved menu and permissions. Immutable after first successful application.
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,context_menu_id,visible_flag,create_user_id)
VALUES (601,'销售订单',1,0,601,'/order',NULL,NULL,NULL,NULL,NULL,true,1) ON CONFLICT (menu_id) DO NOTHING;
-- … 其余 21 条 …

INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1,m.menu_id FROM t_menu m
WHERE m.menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id=1 AND r.menu_id=m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
```

**`sort` 约定**：L1 = `601`；L2 用自身 id；L3 用自身 id（沿用 V11 的 `sort=menu_id` 惯例）。

**修 H14 / H15（C 缺陷）**：
- **H14**：C 的订单菜单**未授予任何角色**。V14 **必须**写 `t_role_menu`（上面的 SQL 已包含）。
- **H15**：C 的菜单 `component` 路径与实际文件不匹配。V14 的 `component` **必须**与实际 `src/views/**` 文件同名 —— 该一致性由 `tools/verify_w4_legacy.py` 断言（§10.3 断言 F）。

**不新增 `sa-base` 枚举值**：本设计**不需要**修改 `SerialNumberIdEnum`（因为用 PG sequence，§14 U4）。

### 5.4 PostgreSQL 索引设计（逐条给理由）

**`sales_order`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `uk_sales_order_no_active` | UNIQUE `(order_no) WHERE deleted = FALSE` | 单号唯一。**部分唯一**：软删后允许单号复用（虽然业务上不会删单，但保持与 A 源/W2/W3 一致的语义） |
| `idx_sales_order_customer_created` | `(customer_id, created_at DESC) WHERE deleted = FALSE` | 客户维度订单列表（最高频查询） |
| `idx_sales_order_status_created` | `(status, created_at DESC) WHERE deleted = FALSE` | 状态筛选 + 时间倒序（列表默认排序） |
| `idx_sales_order_original_order_id` | `(original_order_id) WHERE original_order_id IS NOT NULL` | 补单反查原订单。**部分索引**：绝大多数订单 `original_order_id IS NULL`，条件索引显著减小体积 |
| `idx_sales_order_source_created` | `(order_source, created_at DESC) WHERE deleted = FALSE` | **W4 新增**：来源筛选（`ADMIN`/`MALL`/`SUPPLEMENT`）。A 源无此索引，但 C 的列表支持 `source` 筛选（`SaleOrderMapper.xml` 有 `<if test="queryForm.source != null">`） |

**`sales_order_item`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `idx_sales_order_item_order_id` | `(order_id, sort_order) WHERE deleted = FALSE` | 按订单取明细并保序（`order_id` 是唯一访问路径） |
| `idx_sales_order_item_sku_id` | `(sku_id) WHERE deleted = FALSE` | 反查「某 SKU 被哪些订单引用」（库存/报表波次需要） |
| `uk_sales_order_item_order_sku_active` | UNIQUE `(order_id, sku_id) WHERE deleted = FALSE` | **修 O3**：A 源只在应用层查重（`DUPLICATE_SKU`），并发下可绕过。**部分唯一索引**是这里唯一正确的语义表达（「同一订单内未删除的 SKU 不得重复」）。**注意**：与 `idx_sales_order_item_order_id` 有前缀重叠，但后者含 `sort_order` 且不是唯一约束，两者职责不同，均保留 |

**`order_address_snapshot`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `uk_order_address_snapshot_order` | UNIQUE `(order_id)` | 一单一地址（不可变追加表的自然键）。A 源同构（`uk_mall_order_address_order`） |
| `idx_order_address_snapshot_customer` | `(customer_id, created_at DESC)` | 客户地址历史（客诉排查）。A 源同构 |

**`order_operation_log`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `idx_order_operation_log_order_created` | `(order_id, created_at DESC)` | 订单详情内按时间倒序展示日志。A 源同构 |
| `idx_order_operation_log_type_created` | `(operation_type, created_at DESC)` | **W4 新增**：按操作类型审计（「本周所有改价」）。无 `deleted` 条件（该表无软删） |

**`idempotency_record`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `uk_idempotency_record_scope_key_active` | UNIQUE `(operation_scope, idempotency_key) WHERE deleted = FALSE` | **幂等的唯一正确实现**：靠唯一索引做 INSERT 竞争，而不是「先查后插」（有 TOCTOU 竞态）。A 源同构 |
| `idx_idempotency_record_created` | `(created_at DESC)` | **W4 新增**：过期清理（未来波次可加 TTL 任务）。A 源无此索引 |

**`order_return` / `order_return_item` / `order_refund`**：

| 索引 | 定义 | 理由 |
| --- | --- | --- |
| `uk_order_return_no_active` | UNIQUE `(return_no) WHERE deleted = FALSE` | 退货单号唯一 |
| `idx_order_return_order_id` | `(order_id, created_at DESC) WHERE deleted = FALSE` | 按订单查退货 |
| `idx_order_return_status_created` | `(status, created_at DESC) WHERE deleted = FALSE` | 待审核退货列表 |
| `uk_order_return_item_active` | UNIQUE `(return_id, order_item_id) WHERE deleted = FALSE` | 同一退货单内同一订单行只能出现一次 |
| `idx_order_return_item_return_id` | `(return_id) WHERE deleted = FALSE` | 按退货单取行 |
| `idx_order_return_item_order_item_id` | `(order_item_id) WHERE deleted = FALSE` | **额度计算的关键索引**：按订单行汇总 `pendingReserved` + `approvedQuantity` |
| `uk_order_refund_no_active` | UNIQUE `(refund_no) WHERE deleted = FALSE` | 退款单号唯一 |
| `uk_order_refund_return_active` | UNIQUE `(return_id) WHERE deleted = FALSE` | **一个退货单只能有一个退款单**（A 源设计核心） |
| `uk_order_refund_external_reference_active` | UNIQUE `(external_reference) WHERE deleted = FALSE AND external_reference IS NOT NULL` | 外部凭证唯一（防止同一笔支付渠道退款单被重复登记）。**部分唯一**：可空列必须排除 NULL |
| `idx_order_refund_order_id` | `(order_id, created_at DESC) WHERE deleted = FALSE` | 按订单查退款 |
| `idx_order_refund_status_created` | `(status, created_at DESC) WHERE deleted = FALSE` | 待完成退款列表 |

**索引总数**：`sales_order` 5 + `sales_order_item` 3 + `order_address_snapshot` 2 + `order_operation_log` 2 + `idempotency_record` 2 + `order_return` 3 + `order_return_item` 3 + `order_refund` 5 = **25 个索引**（含 20 个部分索引 / 部分唯一索引）。

### 5.5 不引入的 DB 级约束（**明确记录为已知风险**）

| 不引入 | 理由 | 风险 | 缓解 |
| --- | --- | --- | --- |
| **外键**（`sales_order_item.order_id` → `sales_order.id` 等 8 处） | AGENTS.md 明确禁止外键 | 可能出现孤儿行 | 所有写入都在服务事务内；`order_id` 只由 `SalesOrderService` 写入 |
| **`CHECK (status = 'DRAFT' OR locked_unit_price IS NOT NULL)`** | 需要跨列 + 状态的条件，PG CHECK 无法表达「状态机语义」 | 理论上可写入「PENDING 但未锁价」的行 | `submit()` 是唯一写 `locked_*` 的路径，且同事务内更新状态；由 PG IT 断言 |
| **`CHECK` 保证「确认时所有行都有实数量」** | 跨行约束（需要子查询），PG CHECK 不支持 | 理论上可确认一个有空实重行的订单 | `confirm()` 在事务内遍历校验（O18）；由单测 + PG IT 断言 |
| **唯一索引保证「退货额度不超」** | 需要聚合约束（`SUM`），PG 唯一索引不支持 | 并发下可能超额退货 | **靠行锁**：`create` / `approve` 时 `SELECT ... FOR UPDATE` 锁定原订单行 + 该订单行已有的退货行（§6.8 锁序）；由 PG IT 并发用例断言 |
| **`NOT NULL` 于 `settlement_*`** | 确认前合法为 NULL | — | 无风险 |

---

## 6. 后端设计

### 6.1 API 契约（统一 `/scm/order/**`，SmartAdmin 风格）

**订单**：

| 方法 | 路径 | 权限 | 幂等 | 说明 |
| --- | --- | --- | --- | --- |
| `POST` | `/scm/order/query` | `scm:order:query` | — | 分页查询（`PageParam` + `PageResult`） |
| `GET` | `/scm/order/detail/{orderId}` | `scm:order:query` | — | 详情（头 + 明细 + 地址快照） |
| `POST` | `/scm/order/log/query` | `scm:order:log:query` | — | 操作日志分页 |
| `POST` | `/scm/order/create` | `scm:order:add` | ✅ header | 创建草稿，返回 `{ orderId, orderNo }` |
| `POST` | `/scm/order/update` | `scm:order:update` | ✖ | 修改草稿（差量同步，靠 `version`） |
| `POST` | `/scm/order/submit` | `scm:order:submit` | ✅ header | 提交（严模式取价 + 锁价） |
| `POST` | `/scm/order/item/actual-quantity` | `scm:order:actual-quantity` | ✅ header | 实重录入 |
| `POST` | `/scm/order/confirm` | `scm:order:confirm` | ✅ header | 确认（核算） |
| `POST` | `/scm/order/cancel` | `scm:order:cancel` | ✅ header | 取消 |
| `POST` | `/scm/order/delete` | `scm:order:delete` | ✖ | 逻辑删除（**仅 `DRAFT`**） |
| `POST` | `/scm/order/batch-delete` | `scm:order:delete` | ✖ | 批量逻辑删除（**仅 `DRAFT`**） |
| `POST` | `/scm/order/price/preview` | `scm:order:query` | — | **取价试算**（录单页实时显示价格与可售性） |

**退货**：

| 方法 | 路径 | 权限 | 幂等 |
| --- | --- | --- | --- |
| `POST` | `/scm/order/return/query` | `scm:order:return:query` | — |
| `GET` | `/scm/order/return/detail/{returnId}` | `scm:order:return:query` | — |
| `POST` | `/scm/order/return/create` | `scm:order:return:add` | ✅ header |
| `POST` | `/scm/order/return/approve` | `scm:order:return:approve` | ✅ header |
| `POST` | `/scm/order/return/reject` | `scm:order:return:reject` | ✅ header |
| `POST` | `/scm/order/return/cancel` | `scm:order:return:cancel` | ✅ header |

**退款**：

| 方法 | 路径 | 权限 | 幂等 |
| --- | --- | --- | --- |
| `POST` | `/scm/order/refund/query` | `scm:order:refund:query` | — |
| `GET` | `/scm/order/refund/detail/{refundId}` | `scm:order:refund:query` | — |
| `POST` | `/scm/order/refund/complete` | `scm:order:refund:complete` | ✅ header |

**幂等 header 名**：`Idempotency-Key`（沿用 A 源）。缺失 → `40069`。

**响应形状**：`ResponseDTO<T>` / `ResponseDTO<PageResult<T>>`（SmartAdmin 原生）。业务错误 → **HTTP 200 + `ResponseDTO.code`**（W1/W2/W3 约定）。

**与 C 源的 API 差异**：C 用 `/order/query`、`/order/add`、`/order/update`、`/order/delete/{id}`、`/order/batchDelete`、`/order/deliver/{id}`、`/order/confirm/{id}`、`/order/sign/{id}`（**path 参数**）。V2 统一为 `/scm/order/*` + **body 参数**（SmartAdmin 惯例）+ `POST /query`（不是 `GET`）。`deliver` / `sign` **不存在**。

### 6.2 Form / VO 字段

**`SalesOrderAddForm`**：

```java
Long customerId;                        @NotNull
String orderSource;                     @NotBlank   ADMIN / MALL / SUPPLEMENT
Long originalOrderId;                   // 仅 SUPPLEMENT
String supplementReason;                @Size(max=500)
String remark;                          @Size(max=500)
OffsetDateTime expectDeliveryTime;       // 可空
OrderAddressForm address;               @Valid @NotNull   // 见下
List<SalesOrderItemForm> items;         @Valid @NotEmpty
```

**`SalesOrderItemForm`**：

```java
Long itemId;                            // null = 新增；非 null = 保留
Integer version;                        // 保留行必须携带
Long skuId;                             @NotNull
String orderedQuantity;                 @NotBlank @Pattern(4 位定点字符串)
Boolean manualPriceOverride;            @NotNull
String unitPrice;                       // 仅 manualPriceOverride=true
String overrideReason;                  @Size(max=500)
Integer sortOrder;
```

**`OrderAddressForm`**：

```java
String receiverName;                    @NotBlank @Size(max=100)
String receiverPhone;                   @NotBlank @Size(max=32)
String address;                         @NotBlank @Size(max=500)
```

**`SalesOrderUpdateForm`**：`Long orderId; Integer version;` + `SalesOrderAddForm` 的全部字段。

**`OrderActualQuantityForm`**：`Long orderId; Long itemId; Integer version; String actualQuantity; String reason;`

**`OrderCancelForm`**：`Long orderId; Integer version; String reason;`

**`OrderReturnAddForm`**：`Long orderId; String reason; List<OrderReturnItemForm> items;`
**`OrderReturnApproveForm`**：`Long returnId; Integer version; List<OrderReturnApproveItemForm> items;`（`orderItemId` + `approvedQuantity`）
**`OrderReturnDecisionForm`**：`Long returnId; Integer version; String decisionReason;`
**`OrderRefundCompleteForm`**：`Long refundId; Integer version; String externalReference;`

**金额与数量的序列化（强制）**：

```java
@JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
private BigDecimal orderedQuantity;
```

**所有** BigDecimal 字段必须同时声明 `using` + `nullsUsing`（`ScmFixedScale4Serializer` 的 javadoc 明确要求，否则 null 语义失守）。

**请求侧**：数量/单价只收 **JSON 字符串**（`ScmStrictDecimalStringDeserializer`），不收 JSON 数字。

**`SalesOrderDetailVO`**（详情）：

```java
Long orderId; String orderNo;
Long customerId; String customerCode; String customerName;
String orderSource; String status;
String settleModeSnapshot; Long sellerId; OffsetDateTime expectDeliveryTime;
BigDecimal orderedTotalAmount; BigDecimal settlementTotalAmount;
String remark; String cancelReason;
OffsetDateTime submittedAt; OffsetDateTime confirmedAt; OffsetDateTime cancelledAt;
Long originalOrderId; String originalOrderNo; String supplementReason;
Integer version;
List<SalesOrderItemVO> items;
OrderAddressSnapshotVO address;
```

**`SalesOrderItemVO`**：全部快照字段 + `draftUnitPrice` / `draftPriceSource` / `draftPriceSourceId` / `manualPriceOverride` / `manualPriceReason` / `lockedUnitPrice` / `lockedPriceSource` / `orderedLineAmount` / `settlementLineAmount` / `actualQuantity` / `actualQuantitySource` / `actualQuantityReason` / `version` / `sortOrder`。

**`OrderOperationLogVO`**：`logId` / `operationType` / `operator` / `operatorName` / `reason` / `beforeData` / `afterData` / `createdAt`。

> **`operatorName`**：`operator` 存的是 `{userType}:{userId}`（`ScmOperator.current()`）。VO 需**额外解析出显示名**（通过 SmartAdmin 的 `EmployeeService` / `UserService`）。这是 A 源没有的能力（A 源写死 `'SYSTEM'`）—— **修 A-D7 的必要配套**。

### 6.3 Pricing 集成（**核心，用户明确要求**）

**注入方式**：

```java
@Service @RequiredArgsConstructor
public class SalesOrderService {
    private final PriceResolver priceResolver;      // 直接注入 W3 的 @Service，不走 HTTP
    private final CustomerService customerService;  // 直接注入 W2 的 @Service
    private final ProductSkuOptionDao skuOptionDao; // 直接注入 W1 的 DAO（只读）
}
```

**禁止**：`RestTemplate` / `WebClient` 调自己的 HTTP 端点；新建 `OrderPriceService` 重新实现优先级；在 Order 里再写一份 `AGREEMENT → CUSTOMER_TYPE → MARKET` 逻辑。

**调用矩阵（双模式，Q4）**：

| 场景 | 方法 | 模式 | 有 UNPRICED 时 |
| --- | --- | --- | --- |
| 录单页实时试算 | `priceResolver.resolve()` | 软 | 返回 `priceStatus=UNPRICED`，前端显示「未定价」 |
| 创建草稿 | `priceResolver.resolve()` | 软 | **允许**（草稿可以有未定价行） |
| 修改草稿 | `priceResolver.resolve()` | 软 | **允许** |
| **提交（锁价）** | `priceResolver.requireResolvable()` | **严** | **拒绝提交** → `SKU_NOT_SELLABLE(40949)` |
| 人工改价行 | `resolve()` + 订单 OVERRIDE 校验 | 严 | 校验可售性与改价参数，锁定人工价，source=OVERRIDE |

**理由**：与 W3 的 `resolve`（软）/ `requireResolvable`（严）双模式设计**完全对齐**。W3 文档已明确「Q2 人工修订：eligibility 不短路取价、不擦除合法 0 元」——Order 必须尊重这个语义：**0 元是合法价格，`PRICED`；`UNPRICED` 才是缺价**。

**U6 最终裁决：严格复用 W3 的 NULL 语义。**

```text
PRICED + 0.0000 → 合法零价，draft_unit_price=0.0000，source 为真实来源。
UNPRICED → draft_unit_price=NULL，draft_price_source=NULL，ordered_line_amount=NULL。
任意行未定价 → ordered_total_amount=NULL；全部已定价 → 按四位 HALF_UP 计算合计。
PriceSource 只允许 AGREEMENT/CUSTOMER_TYPE/MARKET/OVERRIDE；禁止加入 UNPRICED。
manual_price_override=true → 非空价格、OVERRIDE 来源、非空改价原因。
草稿允许未定价；submit 必须 requireResolvable() 全部通过后才计算金额、写 locked_*、进入 PENDING。
PENDING 以后（包括取消后）locked_* 永远不重新计算。
```

W3 Resolver 无 override 参数。普通行调用 requireResolvable；人工价行调用 resolve 并校验 sellable，叠加订单域 OVERRIDE 的非空价格与原因校验。所有有效订单价格均非空后才允许锁价，不修改 W3。

**可见性**：`PriceResolver.resolve()` **内部已经**调 `CustomerService.requireTradable()` + 客户类型状态校验 + SKU 可售性（`PriceValidation.unavailable`）+ 客户可见性（`ALLOWLIST`）。**Order 不重复实现可见性判断**（W3 的 `visibleSkuIds` / `requireVisible` 是 W3 的能力，消费点就是 W4 —— W3 文档 P18 明确）。

**录单页 SKU 候选来源**：复用 W3 的 `POST /scm/product/sku/option-list`（W1 提供）+ `POST /scm/pricing/resolve`（W3 提供，权限 `scm:pricing:resolve:query`）。**Order 不新增 SKU 候选端点**。

### 6.4 差量同步（O2，`SalesOrderItemChangeSet`）

**算法（移植 A 源，加 V2 错误码）**：

```text
输入：existing（当前 DB 中该订单的未删除行，已 FOR UPDATE）、requested（表单行）
1. unmatched := {existing.id → existing}
2. 遍历 requested：
   - itemId == null            → inserted
   - itemId != null：
       persisted := unmatched.remove(itemId)
       persisted == null       → 40964 ORDER_ITEM_NOT_OWNED   （跨订单行 ID）
       version == null         → 40071 ORDER_ITEM_VERSION_REQUIRED
       version != persisted.version → 40965 ORDER_ITEM_VERSION_CONFLICT
       → updated
3. 剩余 unmatched.keySet()      → removedIds（软删）
```

**对 A 源的修正**：A 源把 `NOT_OWNED` / `VERSION_REQUIRED` / `VERSION_CONFLICT` 分别映射到 `40921` / `40021` / `40922`（**与 V2 已占用码撞码**，A-D9）。V2 用 `40964` / `40071` / `40965`。

**为什么不做「全删再插」**：会改变行的身份（`id` 变），导致 `order_return_item.order_item_id` 指向失效行（`uk_order_return_item_active` 是 `(return_id, order_item_id)`，删了再插会产生新 `order_item_id`）。**保留 ID/version 是硬要求**。

### 6.5 数量与金额

**精度契约（Q3）**：

```text
DB      NUMERIC(18,4)
Java    BigDecimal
API     十进制字符串（4 位定点）
计算    HALF_UP, SCALE=4
前端    接收 4 位字符串；可选展示 2 位（仅格式化，不改存储）
```

**计算规则（`OrderAmountCalculator`，移植 A 源）**：

```text
lineAmount  = unitPrice == NULL ? NULL : round(quantity × unitPrice, 4, HALF_UP)
orderAmount = 任意 lineAmount == NULL ? NULL : round(Σ lineAmount, 4, HALF_UP)
```

**两条金额轨**：

| 金额 | 计算 | 写入时机 |
| --- | --- | --- |
| `ordered_line_amount` | `round(ordered_quantity × draft_unit_price, 4)` | 创建 / 修改 |
| `ordered_total_amount` | `Σ ordered_line_amount` | 创建 / 修改 / 提交（重算） |
| `settlement_line_amount` | `round(actual_quantity × locked_unit_price, 4)` | **仅确认** |
| `settlement_total_amount` | `Σ settlement_line_amount` | **仅确认** |

**标品 / 非标品的实重规则（O13）**：

```text
STANDARD（标品）：提交时 actual_quantity := ordered_quantity，actual_quantity_source := 'SYSTEM'
NON_STANDARD（非标品）：提交时 actual_quantity := NULL，actual_quantity_source := NULL
                        PENDING 期间由人工录入（actual_quantity_source := 'MANUAL' + reason）
```

**关于「重量」的裁决（§14 U2）**：

- A 源**只有** `actual_quantity`（实数量），**没有** `actual_weight`。
- C 源有 `actual_weight`（`numeric(18,3)`）+ `actual_quantity` 两个字段。
- 用户明确要求调查「**数量 / 重量**」。
- **本设计取值**：**只保留 `actual_quantity`**（A 源口径）。理由：非标品（如散装蔬菜）的「实际重量」在 legacy 就是通过 `actual_quantity` 承载的（销售单位可以是 kg）；引入第二个字段需要明确的业务规则（什么时候用数量、什么时候用重量），而**审计未找到任何事实源**。
- **最终批准**：不增加 actual_weight，实际重量由 actual_quantity + sale_unit_snapshot=kg 等表达。

### 6.6 错误码

**新增 `OrderErrorCode implements ScmErrorCode`**（分配在未被占用的 **40060–40079 / 40460–40479 / 40960–40979** 段）：

| 码 | 常量 | 消息 |
| --- | --- | --- |
| 40060 | `ORDER_SUPPLEMENT_REASON_REQUIRED` | 补单原因不能为空 |
| 40061 | `ORDER_SUPPLEMENT_INVALID` | 非补单订单不能关联原订单或填写补单原因 |
| 40062 | `ORDER_SKU_DUPLICATE` | 订单明细中 SKU 不能重复 |
| 40063 | `ORDER_QUANTITY_INVALID` | 数量必须大于零 |
| 40064 | `ORDER_PRICE_OVERRIDE_REASON_REQUIRED` | 人工改价必须同时填写价格与原因 |
| 40065 | `ORDER_PRICE_OVERRIDE_INVALID` | 非人工改价行不能指定价格 |
| 40066 | `ORDER_PRICE_INVALID` | 价格必须为非负四位定点数 |
| 40067 | `ORDER_ACTUAL_REASON_REQUIRED` | 实重修改原因不能为空 |
| 40068 | `ORDER_CANCEL_REASON_REQUIRED` | 取消原因不能为空 |
| 40069 | `ORDER_IDEMPOTENCY_KEY_REQUIRED` | Idempotency-Key 不能为空 |
| 40070 | `ORDER_IDEMPOTENCY_KEY_INVALID` | Idempotency-Key 长度不能超过 200 个字符 |
| 40071 | `ORDER_ITEM_VERSION_REQUIRED` | 保留订单明细必须携带版本 |
| 40072 | `ORDER_RETURN_APPROVAL_INVALID` | 批准数量无效或未批准任何商品 |
| 40073 | `ORDER_RETURN_ITEM_INVALID` | 退货明细不属于原订单或订单行不可退 |
| 40074 | `ORDER_DELETE_STATE_INVALID` | 仅草稿订单可以删除 |
| 40460 | `ORDER_NOT_FOUND` | 销售订单不存在 |
| 40461 | `ORDER_ITEM_NOT_FOUND` | 订单明细不存在 |
| 40462 | `ORDER_RETURN_NOT_FOUND` | 退货单不存在 |
| 40463 | `ORDER_REFUND_NOT_FOUND` | 退款单不存在 |
| 40960 | `ORDER_STATE_INVALID` | 当前订单状态不允许此操作 |
| 40961 | `ORDER_ORIGINAL_INVALID` | 关联原订单必须已确认且客户一致 |
| 40962 | `ORDER_ACTUAL_NOT_ALLOWED` | 仅待确认订单的非标品明细可以录入实数量 |
| 40963 | `ORDER_ACTUAL_QUANTITY_REQUIRED` | 确认前所有订单明细必须具有有效实数量 |
| 40964 | `ORDER_ITEM_NOT_OWNED` | 订单明细不属于当前订单 |
| 40965 | `ORDER_ITEM_VERSION_CONFLICT` | 订单明细版本冲突 |
| 40966 | `ORDER_IDEMPOTENCY_CONFLICT` | 相同幂等键的请求内容不一致 |
| 40967 | `ORDER_RETURN_STATUS_INVALID` | 当前退货状态不允许此操作 |
| 40968 | `ORDER_REFUND_STATUS_INVALID` | 当前退款状态不允许此操作 |
| 40969 | `ORDER_RETURN_QUANTITY_EXCEEDED` | 退货数量超过可退数量 |
| 40970 | `ORDER_RETURN_ORDER_NOT_CONFIRMED` | 仅已确认订单可以申请退货 |

**复用（不新增）**：

```text
40921  ScmCommonErrorCode.VERSION_CONFLICT        订单头 / 退货单 / 退款单乐观锁
40430  CustomerErrorCode.CUSTOMER_NOT_FOUND       客户不存在（由 requireTradable 抛）
40930  CustomerErrorCode.CUSTOMER_NOT_TRADABLE    客户不可交易（由 requireTradable 抛）
40949  PricingErrorCode.SKU_NOT_SELLABLE          提交时取价失败（由 requireResolvable 抛）
40030  PricingErrorCode.PRICE_INVALID             价格格式（由 PriceResolver 抛）
```

**与 A 源的错误码差异总表**：

| A 源码 | A 源语义 | V2 码 | 变化原因 |
| --- | --- | --- | --- |
| 40920 | 订单版本冲突 | **40921** | 统一到 `ScmCommonErrorCode` |
| 40922 | 明细版本冲突 | **40965** | 避免与 40921 语义混淆 |
| 40921 | 明细不属于本订单 | **40964** | A 源 40921 与 V2 `VERSION_CONFLICT` 撞码（A-D9） |
| 40923 | 状态不允许 | **40960** | 码段迁移 |
| 40020–40030 | 各类校验 | **40060–40074** | A 源 40020–40030 与 V2 已占用码冲突（40030 撞 `PRICE_INVALID`） |
| 40420 / 40421 | 订单/明细不存在 | **40460 / 40461** | 码段迁移 |
| 40450 / 40451 | 退货/退款不存在 | **40462 / 40463** | 码段迁移 |
| 40950–40954 | 售后 | **40967–40970** + 40921 | A 源 40954 与 V2 语义重复 |
| 40050–40052 | 售后校验 | **40072 / 40073 / 40069** | 码段迁移 |

### 6.7 权限

**权限码命名**：`scm:order:<action>` / `scm:order:return:<action>` / `scm:order:refund:<action>` / `scm:order:log:<action>`（沿用 W3 的 `scm:pricing:agreement:query` 三段式）。

**端点 → 权限映射**：见 §6.1 的表格。每个 Controller 方法**必须**有 `@SaCheckPermission`。

**数据权限（用户指令未要求，但需求文档 §6 提到）**：

> C 需求文档 §6：「数据范围：业务员只看自己客户的订单。」

**本设计取值**：**W4 不实现数据权限过滤**。理由：

1. W2 的客户有 `sellerId` 字段，但 **W2 未实现「业务员只看自己客户」** 的数据权限（审计未发现）。
2. SmartAdmin 的数据权限机制（`@DataScope`）在 W2/W3 **未被使用**。
3. 引入数据权限会改变 `POST /scm/order/query` 的语义，且需要 `customer.seller_id` 与当前登录员工的关联规则（**无事实源**）。
4. 用户指令**未列出数据权限**为 W4 必做项。

→ **U7 已批准**：W4 不做业务员数据权限；登记为后续独立能力，不自造 SCM DataScope。

### 6.8 事务边界

**每个命令 = 一个事务**（`@Transactional(rollbackFor = Exception.class)`）。

**锁序（全局固定，防止死锁）**：

```text
1. sales_order 行锁           SELECT ... FOR UPDATE（order_id）
2. sales_order_item 行锁       SELECT ... FOR UPDATE（WHERE order_id = ? AND deleted = FALSE）
3. 售后：order_return 行锁     SELECT ... FOR UPDATE
4. 售后：order_return_item 行锁 SELECT ... FOR UPDATE（WHERE return_id = ?）
5. 售后：order_refund 行锁     SELECT ... FOR UPDATE
6. 上游只读（Customer / Pricing / Product）—— 不加锁
```

**说明**：

- **不锁上游**：Order 只读 Customer/Pricing/Product，不写。因此不存在「Order ↔ Pricing」的交叉锁。价格在提交瞬间被读到即视为有效（W3 的 Resolver 用 `WHERE deleted=FALSE AND 区间有效` 查询，读到就是有效的）。
- **额度并发安全**：`OrderReturnService.create/approve` 必须先锁 `sales_order_item` 行（第 2 步），再汇总该行的 `order_return_item`。**同一个订单行的退货请求会串行化**，因此 `remaining = actual - pendingReserved - approvedQuantity` 的计算是安全的。
  - **前提**：`idx_order_return_item_order_item_id` 索引存在（§5.4），保证汇总高效。
  - **禁止**：只锁 `order_return` 不锁 `sales_order_item`（两个退货单可以并发针对同一订单行）。
- **幂等 claim 的位置**：**在事务内、业务写入之前**（§6.9）。

**各命令的事务内容**：

| 命令 | 事务内容 |
| --- | --- |
| `create` | claim → 校验 → 读客户（不加锁）→ 取价 → INSERT order → INSERT items → INSERT address → 算总额 → UPDATE order → INSERT log → complete claim |
| `update` | 校验 → 锁 order → 状态检查 → 锁 items → 差量同步 → 重取价 → 重算总额 → UPDATE order → INSERT log |
| `submit` | claim → 锁 order → 状态检查 → 锁 items → **requireResolvable** → 写 `locked_*` → 标品/非标品实重分支 → 重算 `ordered_*` → UPDATE order（status=PENDING）→ INSERT log → complete claim |
| `actualQuantity` | claim → 锁 order → 状态检查 → 锁 items → 找行 → 非标品检查 → version 检查 → reason 检查 → UPDATE item → INSERT log → complete claim |
| `confirm` | claim → 锁 order → 状态检查 → 锁 items → **逐行校验实数量 > 0** → 算 `settlement_*` → UPDATE order（status=CONFIRMED）→ INSERT log → complete claim |
| `cancel` | claim → 锁 order → **策略表检查** → version 检查 → reason 检查 → UPDATE order（status=CANCELLED）→ INSERT log → complete claim |
| `returnCreate` | claim → 锁 order（状态=CONFIRMED）→ 锁 order_items → 校验额度 → INSERT return → INSERT return_items → complete claim |
| `returnApprove` | claim → 只读定位 orderId → 锁 order → 锁 order_items → 锁 return → 状态检查 → 锁 return_items → 校验额度 → UPDATE return（APPROVED）→ UPDATE return_items → **INSERT refund（PENDING，1 个）** → complete claim |
| `refundComplete` | claim → 锁 refund → 状态检查 → version 检查 → UPDATE refund（COMPLETED）→ complete claim |

### 6.9 幂等

**组件（移植 A 源，落在 `module/scm/order/support/` + `service/`）**：

```text
OrderIdempotencyService.claim(scope, key, request) → Claim(record, replay)
OrderIdempotencyService.complete(claim, resultType, resultId, result)
OrderIdempotencyService.replay(claim, Class<T>)
OrderIdempotencyRequestHasher.hash(request) → SHA-256 十六进制
OrderIdempotencyConflictException
```

**`claim` 的精确流程（与 A 源一致）**：

```text
1. key == null/blank                    → 40069 ORDER_IDEMPOTENCY_KEY_REQUIRED
2. key.trim().length() > 200            → 40070 ORDER_IDEMPOTENCY_KEY_INVALID
3. hash := hash(request)
4. INSERT INTO idempotency_record(scope, key, hash, …) VALUES (…)
     INSERT 影响行数 == 1  → 首次请求 → Claim(新记录, replay=false)
     INSERT 影响行数 == 0  → 已存在：
         SELECT ... FOR UPDATE WHERE scope=? AND key=? AND deleted=FALSE
         stored.request_hash != hash → 40966 ORDER_IDEMPOTENCY_CONFLICT
         stored.request_hash == hash → Claim(记录, replay = (result_data != null))
```

**为什么用 INSERT 竞争而不是「先查后插」**：`uk_idempotency_record_scope_key_active` 唯一索引让 PG 在**行级**串行化，避免 TOCTOU 竞态。这是 A 源设计的核心优点，必须保留。

**请求哈希规范化（移植 A 源，必须逐条保留）**：

```text
1. 对象键递归按字典序排序
2. JSON 数字 → stripTrailingZeros
3. 形如数字的字符串 → BigDecimal.stripTrailingZeros().toPlainString()
   （使 "1.50" 与 "1.5" 哈希相同 —— 对 4 位定点字符串是关键）
4. SHA-256 → 十六进制小写
```

**作用域清单**：

```text
ORDER_CREATE
ORDER_SUBMIT:{orderId}
ORDER_CONFIRM:{orderId}
ORDER_CANCEL:{orderId}
ORDER_ACTUAL:{orderId}:{itemId}
ORDER_RETURN_CREATE
ORDER_RETURN_APPROVE:{returnId}
ORDER_RETURN_REJECT:{returnId}
ORDER_RETURN_CANCEL:{returnId}
ORDER_REFUND_COMPLETE:{refundId}
```

**不需要幂等的命令（显式声明，修 A-D6）**：

```text
POST /scm/order/update            靠 version 乐观锁保证「最多成功一次」；语义上不是幂等（重试会 40921）
POST /scm/order/delete            逻辑删除天然幂等（删两次结果相同）
POST /scm/order/batch-delete      同上
```

**幂等记录的生命周期**：**W4 不做过期清理**。理由：无 TTL 规则的事实源；`idx_idempotency_record_created` 已为后续清理任务预留。列入已知限制。

### 6.10 乐观锁

| 实体 | 锁列 | 冲突码 |
| --- | --- | --- |
| `sales_order` | `version` | `40921`（`ScmCommonErrorCode.VERSION_CONFLICT`） |
| `sales_order_item` | `version` | `40965`（`ORDER_ITEM_VERSION_CONFLICT`） |
| `order_return` | `version` | `40921` |
| `order_return_item` | `version` | `40965`（复用） |
| `order_refund` | `version` | `40921` |
| `order_operation_log` | **无** | 只追加 |
| `idempotency_record` | `version` | 内部使用 |
| `order_address_snapshot` | **无** | 不可变追加 |

**实现方式**：

- 实体用 MyBatis-Plus `@Version private Integer version;`
- **应用层显式比对**（A 源做法）：`requireVersion(o, form.version())` → 不一致抛 `40921`
- **DB 层兜底**：`UPDATE ... WHERE id=? AND version=?`，影响行数 0 → 抛 `40921`

**双保险的理由**：A 源只做应用层比对（`requireVersion`），在「读-改-写」之间若被并发插入则失守。V2 同时用 MP 的 `@Version` 让 SQL 自带 `AND version=?`，**影响行数为 0 即冲突**。

**统一**：`AfterSalesErrorCodes.VERSION_CONFLICT(40954)` **不沿用**（A-D5），售后统一用 `40921`。

### 6.11 操作日志

**写入点（6 种，与 A 源一致）**：

| operation_type | 触发 | reason | before / after |
| --- | --- | --- | --- |
| `CREATE` | 创建 | null | before=null；after=快照 |
| `UPDATE` | 修改 | null | before=旧快照；after=新快照 |
| `SUBMIT` | 提交 | null | before=旧；after=新（含 `locked_*`） |
| `ACTUAL_QUANTITY` | 实重录入 | 表单 reason | before/after 只含 `{itemId, actualQuantity}` |
| `CONFIRM` | 确认 | null | before=旧；after=新（含 `settlement_*`） |
| `CANCEL` | 取消 | 表单 reason | before=旧；after=新（含 `cancel_reason`） |

**`operator` 写入**：

```java
log.setOperator(ScmOperator.current());   // "{userType}:{userId}"
log.setCreatedBy(ScmOperator.current());
```

**与 SmartAdmin `@OperateLog` 的关系**：

```text
@OperateLog      →  SmartAdmin 的系统级操作日志（t_operate_log），记录「谁调了哪个接口」
order_operation_log  →  SCM 的业务级操作日志，记录「订单的业务状态怎么变的」
```

**两套并存，不冲突**。Controller 方法上**同时**加 `@OperateLog`（系统审计）与业务日志（在 Service 内写）。

---

## 7. Inventory 边界（用户明确要求）

### 7.1 原则

```text
1. W4 不实现任何库存余额表、库存流水表、库存占用表
2. W4 不修改任何库存相关代码（V2 当前无库存域）
3. W4 只在 Order 侧定义「如果订单需要库存，契约长什么样」
4. 禁止在 Order 内造第二套库存余额表（用户明确禁止）
5. 禁止 Order 直接扣减库存（C 的 H4 缺陷）
```

### 7.2 契约定义（只定义，不实现）

```java
package net.lab1024.sa.admin.module.scm.order.support;

/**
 * W4 定义、W4 不实现的库存契约。
 *
 * <p>W4 的订单域不做任何库存动作（不下预占、不扣减、不回滚）。
 * 该接口存在的唯一目的：把「订单对库存的需求」固化成一份可评审的契约，
 * 让后续 Inventory 波次按此实现，而不是让 Order 自己去写库存。
 *
 * <p><b>W4 的唯一实现是 {@link NoOpOrderInventoryContract}</b>。
 */
public interface OrderInventoryContract {

    /**
     * 订单进入「需要占用库存」的状态时调用（后续波次：CONFIRMED 之后）。
     * 要求实现方保证：同一 (skuId, warehouseId) 的可用量足够，否则抛业务异常。
     */
    void reserve(OrderInventoryReserveCommand command);

    /**
     * 订单取消 / 退货时释放占用。
     */
    void release(OrderInventoryReleaseCommand command);

    /**
     * 查询某 SKU 的可用量（仅查询，不改状态）。
     * 返回 null 表示「库存域未启用」——前端应隐藏库存相关展示。
     */
    OrderInventoryAvailability queryAvailability(Long skuId, Long warehouseId);
}
```

**W4 的契约占位实现（普通类，无 bean 注册、无业务调用）**：

```java
public class NoOpOrderInventoryContract implements OrderInventoryContract {
    @Override public void reserve(OrderInventoryReserveCommand c) { /* no-op */ }
    @Override public void release(OrderInventoryReleaseCommand c) { /* no-op */ }
    @Override public OrderInventoryAvailability queryAvailability(Long skuId, Long warehouseId) { return null; }
}
```

**关键设计点**：

1. **W4 不调用 `reserve` / `release`**。接口存在，但**没有任何调用点**。调用点在后续波次加入。
2. **`queryAvailability` 返回 `null`** → 前端订单详情**不显示**库存字段（不是显示 0）。这是「未启用」与「可用量为 0」的区分，与 W3 的「UNPRICED ≠ 0 元」是同一类语义纪律。
3. **`warehouseId` 参数保留**：C 的 `deliver` 硬编码 `warehouseId = 1L`（注释：「订单未单独记录仓库时沿用默认仓」）。W4 **不在 `sales_order` 里加 `warehouse_id`**（无事实源：A 源无仓库概念）。后续波次若需要，由 Inventory 域决定是「订单级」还是「明细级」仓库。

### 7.3 与 C 源的对立（必须记录）

| 维度 | C 源 | W4 |
| --- | --- | --- |
| 订单状态是否含履约 | ✅ 12 状态含采购/分拣/配送 | ✖ 4 状态 |
| 发货是否扣库存 | ✅ `deliver()` 逐行调 `stockOperateService.saleOutbound()` | ✖ 无发货 |
| 库存不足行为 | 抛异常回滚整个订单事务 | 无库存动作 |
| 仓库来源 | 硬编码 `warehouseId = 1L` | 无仓库概念 |
| 订单表是否有仓库列 | ✖（硬编码） | ✖（不加） |

→ **C 的 `deliver` 是 W4 明确不复制的最重要一段代码**（审计 H4）。

### 7.4 「可用量 / 预占」的后续波次入口

```text
Inventory 波次需交付：
  1. 库存余额表（sku × warehouse → available / reserved）
  2. 库存流水表（append-only，含 bizType / bizId）
  3. OrderInventoryContract 的实现类
  4. 在 Order 的哪个状态点调用 reserve（建议：CONFIRMED 之后新增一个「已预占」子状态，或独立 fulfillment_status 值）
  5. 并发安全：reserve 必须在 Inventory 侧用行锁 + 唯一流水号保证

W4 交付的接口已把这些决定留给 Inventory 波次，Order 不做假设。
```

---

## 8. Refund 边界（用户明确要求）

### 8.1 原则

```text
1. W4 实现退款「单据」与「状态」，不实现「资金」
2. 不调用支付渠道
3. 不生成会计凭证 / 不冲销应收（无应收域）
4. 不产生库存动作（退货入库属 Inventory 波次）
5. 一个退货单最多生成一个退款单（DB 唯一索引保证）
```

### 8.2 退款单的生成语义（A 源核心设计）

```text
退货批准（R2）时，在【同一事务】内：
    1. 写 order_return.status = 'APPROVED'，approved_amount，approved_at
    2. 写每行 order_return_item.approved_quantity / approved_amount
    3. INSERT order_refund(status='PENDING', refund_amount = Σ approved_amount)
    → 由 uk_order_refund_return_active 保证「一个退货单只有一个退款单」
    → 由 ck_order_refund_amount CHECK (refund_amount > 0) 保证「零金额退货不能生成退款单」
```

**为什么「批准」与「生成退款单」必须在同一事务**：否则会出现「已批准但无退款单」的中间态，需要人工补偿。A 源设计规格 §7 原文：「**并在同一事务中创建且仅创建一个 `PENDING` 退款记录**」。

**若批准金额为 0**（用户把所有行的批准数量都填 0）：`AfterSalesRules.requirePositiveApproval` 拒绝（`40072`）。

### 8.3 退款完成（F2）的边界

```text
允许：写 completed_at、写 external_reference（可选、唯一）
禁止：调支付渠道、改订单金额、改库存、生成凭证
```

**`external_reference` 的用途**：登记「支付渠道那笔退款的外部单号」。它是**可选**的（线下退款没有外部单号），**唯一**（同一个外部单号不能登记两次）。部分唯一索引 `WHERE deleted = FALSE AND external_reference IS NOT NULL` 精确表达这个语义。

### 8.4 与订单状态的关系（**W4 的关键边界决策**）

A 源的订单状态机**没有** `REFUNDING` 状态——`CONFIRMED` 是业务终态，退货/退款**不改变订单状态**。

C 源有 `REFUNDING(10)` 状态。

**W4 取值**：**采 A 源**。订单状态**不因退货/退款而改变**。

**理由**：

1. A 源是业务规则唯一事实源（Q2）。
2. `order_return` 自己有完整的 4 状态机，`order_refund` 有 2 状态机。订单再叠加一个 `REFUNDING` 会造成**三套状态互相耦合**（C 的 H7 就是这种耦合的产物：`REFUNDING` 永远进不去）。
3. 「订单有正在进行的退货」是**可查询的事实**（`EXISTS(SELECT 1 FROM order_return WHERE order_id=? AND status='PENDING')`），不需要冗余到 `status`。

**前端呈现**：订单列表**可选**显示「退款中」标记（通过 JOIN 查询得出），但**不改 `status`**。若用户要求，可在 `SalesOrderVO` 加 `hasPendingReturn BOOLEAN`（只读派生字段，不入库）。

### 8.5 退款边界总表

| 能力 | W4 | 依据 |
| --- | --- | --- |
| 退货单创建 / 批准 / 驳回 / 取消 | ✅ | A 源 `V5` + `AfterSalesApplicationService` |
| 退款单生成（批准时原子） | ✅ | A 源设计规格 §7 |
| 退款单完成（状态 + 外部凭证） | ✅ | A 源 `order_refund` |
| 可退额度校验 | ✅ | A 源 `AfterSalesRules` |
| 支付渠道调用 | ✖ | 用户「Refund boundary」；规格 §2.2 |
| 会计凭证 / 应收冲销 | ✖ | 无应收域（W4 无签收） |
| 退货入库 / 库存回滚 | ✖ | 用户「不要偷偷实现完整库存」 |
| 订单状态置 `REFUNDING` | ✖ | A 源无此状态（§8.4） |
| 退款单独立创建端点 | ✖ | 只能由退货批准生成（A 源语义） |

---

## 9. 前端设计（Copy First + Adapt）

### 9.0 迁移原则（W2 起生效，W3/W4 沿用）

```text
复制 → 剪枝 → 适配 → 补测试
1. 先复制 C 的同功能 Vue 页面到 xsy-scm-web
2. 剪掉 V2 不存在的能力（12 状态、发货、独立明细页、resizable）
3. 适配 API / DTO / 权限 / 枚举 / version / 字段
4. 补类型与判空（C 未跑 vue-tsc → 必有 TS7006/2339/18047/2322）
5. 禁重写 C 已有页面
6. 删 resizable / @resizeColumn / handleResizeColumn（V2 无 TableHeaderCell 使用）
7. 每个复制文件必须有头部 provenance 注释块（§10.1）
```

### 9.1 文件清单（含来源标记）

| V2 文件 | 类型 | C 来源 | 复制/新增 |
| --- | --- | --- | --- |
| `api/business/scm/order-api.ts` | API | `api/business/order/order-api.ts` | **复制→重写** |
| `api/business/scm/order-return-api.ts` | API | （无 C 对应） | **新增** |
| `api/business/scm/order-refund-api.ts` | API | `api/business/order/order-refund-api.ts` | **复制→重写** |
| `api/business/scm/order-log-api.ts` | API | `api/business/order/order-log-api.ts` | **复制→重写** |
| `constants/business/scm/order-const.ts` | 枚举 | `constants/business/order/order-const.ts` | **复制→剪枝→适配** |
| `views/business/scm/order/order-list.vue` | 页面 | `views/business/order/order-list.vue`（445 行） | **复制→剪枝→适配** |
| `views/business/scm/order/order-detail.vue` | 页面 | （C 无详情页；`xsy-app` 有移动端详情） | **新增** |
| `views/business/scm/order/order-return-list.vue` | 页面 | `views/business/order/order-refund-list.vue`（344 行，骨架） | **复制→剪枝→适配** |
| `views/business/scm/order/order-refund-list.vue` | 页面 | 同上 | **复制→剪枝→适配** |
| `views/business/scm/order/order-log-list.vue` | 页面 | `views/business/order/order-log-list.vue`（250 行） | **复制→剪枝→适配** |
| `views/business/scm/order/order-form-model.ts` | 纯函数 | （无 C 对应；参照 `pricing-form-model.ts`） | **新增** |
| `views/business/scm/order/order-errors.ts` | 错误码映射 | （无 C 对应；参照 `pricing-errors.ts`） | **新增** |
| `views/business/scm/order/components/order-form-drawer.vue` | 组件 | （C 用整页表单；W4 用 Drawer） | **新增** |
| `views/business/scm/order/components/order-item-editable-table.vue` | 组件 | `order-item-list.vue`（359 行，骨架） | **复制→剪枝→适配** |
| `views/business/scm/order/components/order-return-form-modal.vue` | 组件 | （无 C 对应） | **新增** |
| `e2e/scm-order.spec.ts` | E2E | （无 C 对应） | **新增** |

**C 的 `order-item-list.vue`（359 行）处置**：C 把它做成**独立列表页**（`SaleOrderItemController` 独立端点，审计 H20，破坏 O1）。V2 **不提供**独立明细写入口 → 把它**降级为订单表单内的可编辑表格组件**（`order-item-editable-table.vue`）。

### 9.2 复制后强制适配清单（逐项门禁）

| # | 适配项 | C 原值 | V2 值 |
| --- | --- | --- | --- |
| A1 | **API 前缀** | `/order/*`、`/order-item/*`、`/order-log/*`、`/order-refund/*` | `/scm/order/*`、`/scm/order/return/*`、`/scm/order/refund/*`、`/scm/order/log/*` |
| A2 | **API 参数位置** | path 参数（`/order/delete/${orderId}`） | body 参数（`postRequest('/scm/order/delete', { orderId })`） |
| A3 | **查询方法** | `postRequest('/order/query', param)` | `postRequest('/scm/order/query', param)`（保持 POST，符合 V2 惯例） |
| A4 | **权限码** | `order:*`（且 C 未授予任何角色） | `scm:order:*` / `scm:order:return:*` / `scm:order:refund:*` / `scm:order:log:*` |
| A5 | **订单状态枚举** | `SmartEnum<number>` **12 值** | `SmartEnum<string>` **4 值**：`DRAFT/PENDING/CONFIRMED/CANCELLED` |
| A6 | **订单来源枚举** | `MALL=1 / ADMIN=2 / SUPPLEMENT=3` | `ADMIN / MALL / SUPPLEMENT`（字符串，且 `ADMIN` 对应 C 的「后台录单」） |
| A7 | **价格来源枚举** | `PRICE_TYPE_ENUM`：`BASE/CUSTOMER_LEVEL/CURRENT/AGREEMENT`（4 值，**语义完全不同**） | `SCM_ORDER_PRICE_SOURCE_ENUM`：`AGREEMENT/CUSTOMER_TYPE/MARKET/OVERRIDE` |
| A8 | **明细状态枚举** | `ORDER_ITEM_STATUS_ENUM`：`NORMAL/REFUNDED/RETURNED` | **删除**（V2 的明细无独立状态；退货状态在 `order_return`） |
| A9 | **操作类型枚举** | 10 值（含 `DELIVER/SIGN/SETTLE/INVALID`） | 6 值：`CREATE/UPDATE/SUBMIT/ACTUAL_QUANTITY/CONFIRM/CANCEL` |
| A10 | **退款枚举** | `REFUND_TYPE_ENUM`（仅退款/退货退款）+ `REFUND_STATUS_ENUM`（4 值单表） | `order_return` 4 状态 + `order_refund` 2 状态（**两套**） |
| A11 | **删除** | `deliver` / `sign` / 独立明细页 / `batchDelete` 的裸调用 | `deliver`/`sign` **删除**；明细页降级为组件；`batchDelete` 加 `DRAFT` 前置 |
| A12 | **金额格式化** | `DECIMAL(18,2)` 到分 | 4 位定点字符串；用 `src/utils/scm-amount.ts`（W3 已建立） |
| A13 | **字段名** | `orderId`/`totalAmount`/`actualAmount`/`payStatus`/`settleType` | `orderId`/`orderedTotalAmount`/`settlementTotalAmount`（**无 `payStatus`/`settleType`**） |
| A14 | **裸 ID 列** | 显示 `customerId`/`productId`/`skuId` | 显示 `customerCode`+`customerName`/`productName`+`specName`/`skuCode` |
| A15 | **`resizable`** | 有 | **删除** `resizable` / `@resizeColumn` / `handleResizeColumn` |
| A16 | **`version`** | C 无乐观锁 → 无 version 传参 | 编辑/提交/确认/取消/实重/删除**必须**携带 `version`；40921/40965 → 提示刷新 |
| A17 | **幂等 header** | C 无 | 创建/提交/确认/取消/实重/退货/退款完成**必须**带 `Idempotency-Key`（前端生成 UUID，**同一操作重试复用同一个 key**） |
| A18 | **错误处理** | C 无统一错误映射 | 新增 `order-errors.ts` 映射 40060–40074 / 40460–40463 / 40960–40970 / 40921 / 40930 / 40949 |
| A19 | **选择器** | C 用裸 `a-input-number` 填 ID | `CustomerSelect`（W2）+ `SkuSelect`（W1/W3） |
| A20 | **三态金额呈现** | C 无 | 复用 W3 的 `未定价` / `¥ 0.0000` / `—` 三态 |
| A21 | **枚举注册** | C 直接 import | **必须**追加注册 `src/constants/index.ts`（否则 `SmartEnumSelect` 取不到） |
| A22 | **类型补齐** | C 未跑 `vue-tsc` | 补齐 TS7006 / 2339 / 18047 / 2322；`scm/order/**` 必须 **0 错误** |

### 9.3 路由与菜单

| path | name | component | 菜单 |
| --- | --- | --- | --- |
| `/order/order-list` | `OrderList` | `views/business/scm/order/order-list.vue` | 602 |
| 订单列表内详情 Drawer | — | `views/business/scm/order/order-detail.vue` | **无**（从列表打开） |
| `/order/order-return-list` | `OrderReturnList` | `views/business/scm/order/order-return-list.vue` | 603 |
| `/order/order-refund-list` | `OrderRefundList` | `views/business/scm/order/order-refund-list.vue` | 604 |
| `/order/order-log-list` | `OrderLogList` | `views/business/scm/order/order-log-list.vue` | 605 |

**实际路由落点**：SmartAdmin `src/router/` 由 V14 菜单自动生成四个列表路由；详情是列表内 Drawer，保留 `order-detail.vue` 文件，不新增隐藏路由，不修改 router core。

### 9.4 前端表格 ID（Playwright 定位用）

**必须为以下元素提供稳定 ID**（W1/W2/W3 惯例，`table-id` / `form_item_<name>`）：

```text
订单列表      order-table
订单明细表    order-item-table
退货列表      order-return-table
退款列表      order-refund-table
日志列表      order-log-table
表单字段      form_item_customerId / form_item_orderSource / form_item_originalOrderId /
              form_item_supplementReason / form_item_remark /
              form_item_receiverName / form_item_receiverPhone / form_item_address
明细行字段    form_item_skuId / form_item_orderedQuantity / form_item_manualPriceOverride /
              form_item_unitPrice / form_item_overrideReason
实重录入      form_item_actualQuantity / form_item_actualQuantityReason
取消          form_item_cancelReason
```

> **踩坑记录（W1/W2/W3 已证实）**：antd 的 `a-form-item` 若无 `name` 属性则**不渲染** `<label for>`；`a-select` 的 `getByLabel` 会命中被 `.ant-select-selection-item` 拦截的 input → Playwright 必须用
> `.ant-form-item:has(#form_item_<name>) .ant-select-selector` 定位。

---

## 10. C Frontend Migration Provenance

### 10.1 第 1 层：文件头注释块（强制）

每个从 C 复制的 Vue 文件**必须**有：

```vue
<!--
  * 销售订单列表
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-list.vue
  *      （Copy First + Adapt）
  * 复制日期：2026-09-16
  *
  * 剪枝：
  * - 删除 12 值 ORDER_STATUS_ENUM（V2 为 4 值字符串枚举）
  * - 删除「发货」按钮及 deliver 调用（V2 无发货，且 C 的 deliver 直接扣库存）
  * - 删除 PRICE_TYPE_ENUM（C 的 BASE/CUSTOMER_LEVEL/CURRENT/AGREEMENT 与 V2 语义不同）
  * - 删除 ORDER_ITEM_STATUS_ENUM（V2 明细无独立状态）
  * - 删除裸 ID 列（customerId / productId / skuId）
  * - 删除 resizable / @resizeColumn / handleResizeColumn（V2 无 TableHeaderCell 使用）
  * - 删除 payStatus / settleType 列与筛选（V2 无此二字段）
  *
  * 适配：
  * - API：/order/* → /scm/order/*
  * - 权限：order:* → scm:order:*
  * - 枚举：SmartEnum<number> 12 值 → SmartEnum<string> 4 值；注册进 src/constants/index.ts
  * - 字段：totalAmount → orderedTotalAmount；actualAmount → settlementTotalAmount
  * - 金额：4 位定点字符串；用 src/utils/scm-amount.ts 展示
  * - 选择器：裸 ID 输入 → CustomerSelect / SkuSelect
  * - 乐观锁：所有命令携带 version；40921/40965 → 提示刷新
  * - 幂等：创建/提交/确认/取消 携带 Idempotency-Key
  * - 错误：新增 order-errors.ts（40060–40074 / 40460–40463 / 40960–40970）
  *
  * 验收：
  * - e2e/scm-order.spec.ts
  * - TS 棘轮：src/views/business/scm/order/ 0 错误
  * - 截图：.runtime/w4-order-list.png
-->
```

### 10.2 第 2 层：验收报告的 Provenance 表（强制）

| V2 文件 | C 来源文件 | 复制日期 | 剪枝项 | 适配项 | 验收证据 |
| --- | --- | --- | --- | --- | --- |
| `order-list.vue` | `views/business/order/order-list.vue` | 2026-09-16 | 12 状态枚举 / 发货按钮 / `PRICE_TYPE_ENUM` / `ORDER_ITEM_STATUS_ENUM` / 裸 ID 列 / `resizable` / `payStatus`+`settleType` | API / 权限 / 枚举 / 字段 / 金额 / 选择器 / version / 幂等 / 错误 | e2e 用例 1；TS 0 错误；截图 |
| `order-log-list.vue` | `views/business/order/order-log-list.vue` | 2026-09-16 | `resizable` / `operate_by` 裸 ID 列 / 10 值操作类型 | API / 权限 / 枚举 6 值 / 补 operator 显示名 / 补 before-after 展示 | e2e 用例 5；TS 0 错误 |
| `order-return-list.vue` | `views/business/order/order-refund-list.vue` | 2026-09-16 | 4 值单表退款语义 / `REFUND_TYPE_ENUM` / `resizable` | 拆为退货（4 状态）+ 退款（2 状态）两页 / API / 权限 | e2e 用例 6；TS 0 错误 |
| `order-refund-list.vue` | 同上 | 2026-09-16 | 同上 | 同上 | e2e 用例 6；TS 0 错误 |
| `order-item-editable-table.vue` | `views/business/order/order-item-list.vue` | 2026-09-16 | 独立列表页语义（H20）/ 独立写入口 / `resizable` | 降级为表单内可编辑表格 / 差量同步 / version 传递 | e2e 用例 2；TS 0 错误 |
| `order-const.ts` | `constants/business/order/order-const.ts` | 2026-09-16 | 9 枚举全部重定义 | 字符串枚举 / 4+3+4+2+6+4+2 值 / 注册 `src/constants/index.ts` | TS 0 错误 |

### 10.3 第 3 层：机器校验（`tools/verify_w4_legacy.py`）

```text
断言 A：project-reference-examples/xsy-scm/** 全量文件 SHA-256 与 W3 基线一致（零修改）
断言 B：V1–V12 migration SHA-256 与 pg-closure-applied-migrations.sha256 一致（12/12）
断言 C：V13/V14 首次应用后 SHA-256 冻结，写入 w4-applied-migrations.sha256
断言 D：module/scm/product/** 的 W1 既有文件零 diff（仅允许新增只读文件）
断言 E：所有标记为「复制」的前端文件，头部注释块含
        `来源：project-reference-examples/xsy-scm/` 与 `复制日期：`
断言 F：V14 中每个 menu_type=2 的 component 路径，在 src/views/** 下存在同名文件（修 H15）
断言 G：V14 中每个 menu_type=3 的权限码，在 Java 源码的 @SaCheckPermission 中出现
断言 H：V14 中每个 menu_id 都有对应的 t_role_menu 授权（修 H14）
断言 I：C 的源文件 SHA-256 与本次记录一致（防止 C 被静默改动）
输出：.runtime/w4-legacy-recheck.manifest + .json
```

---

## 11. 测试矩阵

### 11.1 后端单测（无 DB，Mockito / 纯函数）

**可直接移植 A 源的 5 个纯类测试**（A 源已有，质量高）：

| 测试类 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `OrderStateMachineTest` | 4×4 = 16 组合，4 条合法边 | 16 |
| `OrderAmountCalculatorTest` | 精度、HALF_UP、0 元、边界 | 8 |
| `SalesOrderItemChangeSetTest` | inserted / updated / removed / 跨订单 ID / 缺 version / 版本冲突 | 10 |
| `OrderIdempotencyRequestHasherTest` | 键排序 / 数字规范化 / 数字字符串规范化 / 数组 / 嵌套 | 8 |
| `OrderValidatorTest` | 补单 / 重复 SKU / 数量 / 改价 / 非改价带价格 | 12 |

**W4 新增**：

| 测试类 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `SalesOrderServiceTest` | 6 个命令的编排（Mock 上游） | 14 |
| `OrderReturnServiceTest` | 额度计算 / 批准原子生成退款 | 10 |
| `OrderSnapshotFactoryTest` | 快照装配（含 UNPRICED NULL 传播） | 6 |
| `OrderErrorCodeTest` | 码值不与 V2 已占用码冲突（**门禁用**） | 3 |

**单测总数估计**：≈ 87。

### 11.2 后端 PG 集成测试（`@SpringBootTest` + test profile + Flyway，`*IT`）

**基类**：`ScmW4PgITBase`（参照 W3 的 `ScmW3PgITBase`）。

| 测试类 | 覆盖 |
| --- | --- |
| `ScmOrderMigrationIT` | V13 表/列/约束/索引/序列存在；`data_type` 断言（`NUMERIC(18,4)` / `BOOLEAN` / `TIMESTAMPTZ`）；8 张表；25 个索引；`uk_sales_order_item_order_sku_active` 与 `uk_order_refund_return_active` 存在 |
| `ScmOrderPermissionMigrationIT` | V14 的 22 条 `t_menu` 存在；`component` 路径在 `src/views/**` 存在；权限码在 `@SaCheckPermission` 出现；`t_role_menu` 授权齐全；`setval` 正确 |
| `SalesOrderServiceIT` | 创建 → 修改 → 提交 → 实重 → 确认 全链路；快照正确性；金额 4 位；`locked_*` 冻结；`draft_*` 可随价格变动 |
| `OrderPriceSnapshotIT` | **「历史成交价不重算」**：提交后修改协议价 → 订单 `locked_unit_price` 不变 |
| `OrderOptimisticLockIT` | 订单头 / 明细 / 退货 / 退款四类 version 冲突 → 40921 / 40965 |
| `OrderIdempotencyIT` | 同 key 同请求重放；同 key 不同请求 → 40966；并发同 key → 只成功一次 |
| `OrderReturnQuotaConcurrencyIT` | **并发两个退货请求针对同一订单行 → 总额度不超**（锁序验证） |
| `OrderStateMachineIT` | 7 条转换全部走 DB；4 条非法转换被拒（40960） |
| `OrderOperationLogIT` | 6 种 operation_type 均落库；`operator` 非 `'SYSTEM'`；`before_data`/`after_data` 是 JSONB object |
| `OrderAmountPrecisionIT` | `NUMERIC(18,4)` 往返不失真；4 位定点字符串序列化；null → JSON null |

**PG IT 总数估计**：≈ 60。

**运行命令**：

```bash
mvn -Ptest test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
```

> **踩坑**：`*IT` **不在** Surefire 默认 includes → 必须显式 `-Dtest='*IT'`。
> **踩坑**：`XSY_V2_DB_PASSWORD`（Spring 配置 / 乐观锁 IT）与 `XSY_DB_PASSWORD`（Playwright 脚本）**两个都要导出**。

### 11.3 后端 Web 层测试（`@WebMvcTest` + `addFilters=false`）

| 测试类 | 覆盖 |
| --- | --- |
| `SalesOrderControllerTest` | 12 个端点：权限注解、参数校验、幂等 header 缺失 → 40069、`ResponseDTO` 形状 |
| `OrderReturnControllerTest` | 6 个端点 |
| `OrderRefundControllerTest` | 3 个端点 |

**Web Test 总数估计**：≈ 30。

### 11.4 前端单测

| 测试文件 | 覆盖 |
| --- | --- |
| `order-form-model.spec.ts` | 表单纯函数：客户必选、SKU 不重复、数量 4 位正数、改价必须带原因、非改价不得带价格 |
| `order-errors.spec.ts` | 错误码 → 提示文案映射 |
| `order-const.spec.ts` | 4 个枚举值域与后端一致 |

**前端单测总数估计**：≈ 18。

### 11.5 E2E（Playwright，`e2e/scm-order.spec.ts`，**6 用例**）

| # | 用例 | 覆盖不变量 |
| --- | --- | --- |
| 1 | 后台录单 → 草稿（价格快照可见）→ 修改（差量同步保留 ID/version）→ 提交（锁价）→ 金额与日志正确 | O1/O2/O5–O9/O14/O15/O24 |
| 2 | 非标品订单：提交后 `actual_quantity` 为空 → 录入实重（必须填原因）→ 确认 → `settlement_*` 重算 | O12/O13/O17/O18/O19 |
| 3 | 取消：DRAFT → CANCELLED（必须填原因）；CONFIRMED 取消被拒 | O15/O16 |
| 4 | 补单：SUPPLEMENT 必须填原因；关联已确认原订单；客户不一致被拒 | O20/O21 |
| 5 | 操作日志：6 种操作类型可见，含操作者与前后值 | O24 |
| 6 | 退货 → 批准（原子生成退款）→ 完成退款；超额退货被拒 | §3.4 R1–R4 / F2 |

**环境要求**（W1/W2/W3 已建立）：

```bash
PLAYWRIGHT_BROWSERS_PATH=D:/DevCaches/Playwright
XSY_DB_PASSWORD=<...>          # Playwright 脚本用（psql PGPASSWORD）
XSY_V2_DB_PASSWORD=<...>       # Spring 配置用
# 后端 18080 + 前端 dev 18081 必须同时在线
```

**目标**：`6/6 PASS`。

### 11.6 质量门禁（Step 0 交付物）

```bash
# 后端
mvn -Ptest test                                                   # 目标：全部 PASS
mvn -Ptest test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false  # 目标：全部 PASS
# 前端
npm test                                                          # 目标：全部 PASS
npm run lint                                                      # 目标：0 error（用项目脚本 eslint src，不用 eslint .）
npm run build                                                     # 目标：✓（--emptyOutDir 会被沙箱拦 → 用 dist-verify 或重命名目录）
python tools/ts_baseline_ratchet.py check                         # 目标：PASS（new 0 / SCM 0 / total ≤ 1974）
# E2E
npx playwright test e2e/scm-order.spec.ts                         # 目标：6/6
# 冻结校验
python tools/verify_w4_legacy.py                                  # 目标：断言 A–I 全 PASS
python tools/verify_mapper_param_types.py                         # 目标：无新增失败
node tools/smartadmin_feature_probe.mjs                           # 目标：total=53 ok=53 failed=0 unmappedPath=0
```

> **踩坑**：SCM 交付的新文件若落在 `src/` 非白名单路径，**必须同步加入 `ts_baseline_ratchet.py` 的 `SCM_PREFIXES`**，否则「SCM 0 错误」门禁漏放。
> **踩坑**：SCM 测试全在 `sa-admin`，`-Dtest=` 必须配 `-Dsurefire.failIfNoSpecifiedTests=false`。
> **踩坑**：jar 被运行进程锁住致 repackage 失败 → 先停进程。

---

## 12. 风险

| # | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| R1 | **W4 范围与仓库文档冲突**（Q1） | 后续波次文档自相矛盾 | §14 U1 拍板后同步更新 `AGENTS.md` + 路线图 |
| R2 | **状态机选择错误**（Q2） | 若选 12 状态 → 被迫实现库存，违反用户约束 | §3.3 不建履约字段的边界；§14 U1 |
| R3 | **`draft_unit_price` NOT NULL vs W3 的 UNPRICED（null）** | 语义冲突 | §6.3 已批准 NULL 语义；DB 同空约束 + 金额空值传播；§14 U6 |
| R4 | **A 源 DDL 缺陷被照抄**（A-D1/A-D2/A-D11） | 写入时违反 CHECK | §4.2 / §4.3 已逐条修正；`ScmOrderMigrationIT` 断言白名单 |
| R5 | **A 源错误码撞码被照抄**（A-D5/A-D9） | 与 V2 已占用码冲突，前端无法区分 | §6.6 全部重新分配；`OrderErrorCodeTest` 门禁 |
| R6 | **退货额度并发超额** | 数据不一致 | §6.8 锁序（先锁 `sales_order_item`）；`OrderReturnQuotaConcurrencyIT` |
| R7 | **幂等表无限增长** | 长期磁盘 | `idx_idempotency_record_created` 预留；记录为已知限制 |
| R8 | **`order_operation_log` 无限增长** | 长期磁盘 | 记录为已知限制（A-D12）；后续波次加归档 |
| R9 | **一单一地址限制**（A-D10/G15） | 无法支持多收货地址 | 记录为已知限制；`order_address_snapshot` 的唯一索引可随时放宽为部分唯一 |
| R10 | **C 的 `order-list.vue` 复制不彻底**（漏删 12 状态） | 前端显示不存在的状态 | §9.2 A5 强制；`order-const.spec.ts` 断言值域 |
| R11 | **PG sequence vs SmartAdmin SerialNumberService**（U4） | 若选后者，编号在多实例部署下可能重复（`SerialNumberInternService` 是内存锁） | §14 U4；本设计推荐 PG sequence |
| R12 | **数据权限未实现**（U7） | 业务员能看到全部订单 | §14 U7 明确；若需要则单独立项 |
| R13 | **TS 棘轮基线** | 新文件落在非白名单路径导致漏放 | §11.6 门禁提示；`SCM_PREFIXES` 同步 |
| R14 | **git ref 静默失败**（本仓库已发生两次） | 提交丢失 | 每次提交后 `git rev-parse HEAD` 复核；必要时同时写 `.git/packed-refs` 与 loose ref |

---

## 13. 实施顺序（已授权直接执行）

```text
T0   门禁基线      记录当前 TS 棘轮基线、测试基线、Playwright 基线（不改代码）
T1   V13 migration 编写 8 张表 + 25 索引 + 3 序列 + COMMENT
T2   Migration IT   ScmOrderMigrationIT（表/列/约束/索引/序列断言）
T3   枚举与错误码  ScmOrder*Enum × 8 + OrderErrorCode + OrderErrorCodeTest（撞码门禁）
T4   纯 Manager     OrderStateMachine / OrderAmountCalculator / SalesOrderItemChangeSet /
                   OrderSnapshotFactory / OrderValidator + 对应单测（可移植 A 源）
T5   实体与 DAO     8 个 Entity + 8 个 Dao + 7 个 Mapper XML（PG 方言，无 INSTR）
T6   幂等组件       OrderIdempotencyService / Hasher / ConflictException + 单测
T7   SalesOrderService（写路径）create / update / submit / actualQuantity / confirm / cancel / delete
T8   SalesOrderQueryService（读路径）query / detail / log
T9   订单 Controller + Web Test
T10  OrderReturnService / OrderRefundService + 单测
T11  售后 Controller + Web Test
T12  PG IT          SalesOrderServiceIT / OrderPriceSnapshotIT / OrderOptimisticLockIT /
                   OrderIdempotencyIT / OrderReturnQuotaConcurrencyIT / OrderStateMachineIT /
                   OrderOperationLogIT / OrderAmountPrecisionIT
T13  V14 migration  22 条 t_menu + t_role_menu + setval
T14  Permission IT  ScmOrderPermissionMigrationIT
T15  前端 API 层    order-api.ts / order-return-api.ts / order-refund-api.ts / order-log-api.ts
T16  前端枚举       order-const.ts + 注册 src/constants/index.ts
T17  前端页面       order-list.vue / order-detail.vue / order-return-list.vue /
                   order-refund-list.vue / order-log-list.vue（Copy First + Adapt）
T18  前端组件       order-form-drawer.vue / order-item-editable-table.vue / order-return-form-modal.vue
T19  前端纯函数     order-form-model.ts / order-errors.ts + 前端单测
T20  E2E            e2e/scm-order.spec.ts（6 用例）
T21  全量回归       §11.6 全部门禁
T22  冻结与报告     V13/V14 hash 冻结；w4-applied-migrations.sha256；验收报告 + approval
T23  停止           不进入 W5
```

**关键路径**：T1 → T2 → T5 → T7 → T12 → T17 → T20 → T21。

**阶段门禁**：按依赖完成对应测试，纯规则/服务/Web 测试按行为集中运行；不为满足预计类名重复同一断言。`git rev-parse HEAD` 复核确认未切换分支或提交。

---

## 14. 最终审批（2026-09-16，全部已批准）

用户明确授权：先修订 Target Design，随后直接执行 T0 → T23，无需再次确认。以下裁决覆盖原推荐值。

| 编号 | 最终裁决 | 状态 |
| --- | --- | --- |
| Q1/Q2/U1 | W4=Sales Order；W5=Purchase；DRAFT/PENDING/CONFIRMED/CANCELLED 四状态 | APPROVED |
| Q3 | NUMERIC(18,4)，HALF_UP scale 4，API 四位定点字符串 | APPROVED |
| Q4 | submit 调 requireResolvable() 全部通过后写 locked_*；PENDING 后永不重算；草稿可重解析 | APPROVED |
| U2/U3 | 创建时快照 customer.settleMode → settle_mode_snapshot、customer.sellerId → seller_id（可空）；expect_delivery_time / remark 可空；历史永不实时回读 | APPROVED |
| U2 | 不增 pay_status / actual_weight；实重由 actual_quantity + sale_unit_snapshot 表达 | APPROVED |
| U4/U8 | PostgreSQL sequence 全局单调递增、不每日 reset；SO + yyyyMMdd + 至少补齐6位，超过999999自然扩位；Return/Refund 同理 | APPROVED |
| U5 | ADMIN / MALL / SUPPLEMENT | APPROVED |
| U6 | NULL=UNPRICED，0.0000=合法PRICED；draft price/source 同空；未定价行和含未定价行的订单金额为空；PriceSource 禁止 UNPRICED；人工价非空且 OVERRIDE + 非空原因 | APPROVED |
| U7 | W4 不实现业务员数据权限，后续独立能力；不自造 SCM DataScope | APPROVED |
| U9 | 同步 AGENTS.md 与相关 Roadmap：W4 Sales Order / W5 Purchase | APPROVED |
| U10 | 不新增 V13+ 仅约束 PostgreSQL Closure；W4 起正常追加迁移；V1–V12 零修改 | APPROVED |
| 履约 | V13 不建 fulfillment_status；后续 Fulfillment/Inventory 波次以新迁移新增；W4 无库存余额/占用/流水/出库 | APPROVED |

订单号序列允许事务回滚造成空号，不承诺提交顺序连续；sequence 的数值全局递增，与日期无关。

---

## 15. 完成定义（DoD）

```text
[x] §14 的 Q1–Q4 / U1–U10 与履约裁决全部批准
[x] V13/V14 已应用；V1–V12 零修改（hash 12/12 一致）
[x] 8 张表 + 25 索引 + 3 序列全部落地并断言
[x] 22 条 t_menu + t_role_menu 授权齐全
[x] 24 条不变量（O1–O24）全部有测试覆盖（附录 B 对账）
[x] 后端单测 / PG IT / Web Test / 前端单测 / Playwright 6/6 全 PASS
[x] TS 棘轮 PASS（SCM 0 错误；新路径已加入 SCM_PREFIXES）
[x] ESLint 0 error（用 eslint src）
[x] npm run build ✓
[x] 功能探针 53/53（无回归）
[x] project-reference-examples/xsy-scm/** 零修改（断言 A）
[x] module/scm/product/** 零修改（断言 D）
[x] verify_w4_legacy.py 断言 A–I 全 PASS
[x] Frontend Migration Provenance 三层齐全（文件头 + 报告表 + 机器校验）
[x] 验收报告 + approval 文件产出
[x] 停止，不进入 W5
```

---

## 附录 A：本设计与 legacy 的差异总表（审查用）

| # | 维度 | legacy（A 源） | 本设计 | 类型 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | 包结构 | `com.xianshuyuan.scm.order.**`（`service/` 平铺） | `net.lab1024.sa.admin.module.scm.order.**`（`constant/controller/dao/domain/manager/service/support`） | 适配 | V2 分层约定 |
| 2 | 响应 | `ApiResponse` / `PageData` | `ResponseDTO` / `PageResult` | 适配 | SmartAdmin Native First |
| 3 | 异常 | `BusinessException(ErrorCode)` | `ScmBusinessException(ScmErrorCode)` | 适配 | W1 起约定 |
| 4 | 状态机 | 策略表存在但只用 2 条边 | 7 条转换**全部**走策略表 | **修正** | 修 A-D4 |
| 5 | `draft_price_source` 白名单 | `MARKET/AGREEMENT/OVERRIDE` | `AGREEMENT/CUSTOMER_TYPE/MARKET/OVERRIDE`（可空） | **修正** | 修 A-D1 + U6 |
| 6 | `ck_sales_order_supplement` | `= 'NORMAL'` 分支（MALL 违反） | `<> 'SUPPLEMENT'` 分支 | **修正** | 修 A-D2 |
| 7 | `actual_quantity_reason` | DDL 可空，应用层强制 | DDL CHECK 强制 | **修正** | 修 A-D11 |
| 8 | 错误码 | 40920/40922/40923/40020–40030… | 40921/40960–40970/40060–40074/40460–40463 | **修正** | 修 A-D5/A-D9 |
| 9 | 操作日志 `operator` | `DEFAULT 'SYSTEM'` + 写死 | `NOT NULL` + `ScmOperator.current()` | **修正** | 修 A-D7/A-D8 |
| 10 | 地址表名 | `mall_order_address` | `order_address_snapshot` | **修正** | 修 A-D3 |
| 11 | 明细 SKU 唯一 | 仅应用层 | 部分唯一索引 | **增强** | 修 O3 |
| 12 | `update` 幂等 | 无 key，无声明 | 显式声明「非幂等，靠 version」 | **修正** | 修 A-D6 |
| 13 | `order_source` 值 | `NORMAL/SUPPLEMENT/MALL` | `ADMIN/MALL/SUPPLEMENT` | 适配 | U5 |
| 14 | 单号 | `SO`+日期+至少6位（全局 PG sequence，不每日重置） | 同 | 保留 | U4/U8 |
| 15 | 金额精度 | `NUMERIC(18,4)` / HALF_UP 4 | 同 | 保留 | Q3 |
| 16 | 双轨价格 | `draft_*` + `locked_*` | 同 | 保留 | O8 |
| 17 | 幂等 | `idempotency_record` + INSERT 竞争 + 规范化哈希 | 同 | 保留 | O22 |
| 18 | 售后 | `order_return`(4) + `order_refund`(2) 两表 | 同 | 保留 | K13/K14 |
| 19 | 库存 | 零引用 | 零引用 + `OrderInventoryContract` 契约 | 保留+增强 | 用户要求 |
| 20 | 履约状态 | 无 | 不建履约字段；后续新迁移追加 | **新增** | §3.3（防 C 的 H4 重现） |
| 21 | `remark` | 无 | 有 | **新增** | 用户要求调查（U2） |
| 22 | 试算端点 | 无 | `POST /scm/order/price/preview` | **新增** | 录单页需要 |
| 23 | 客户引用检查 | 无 | 启用 W2 预留的 `CUSTOMER_REFERENCED(40939)` | **新增** | W2 注释明确「W3 接入定价/订单后启用」 |
| 24 | 数据权限 | 无 | 无（U7） | 保留 | 无事实源 |

**「保留」21 项 / 「修正」10 项 / 「增强」2 项 / 「新增」4 项 / 「适配」4 项** —— 全部可追溯到 §4 的不变量或 §7.2 的缺陷编号。

---

## 附录 B：文件变更总清单

### B.1 后端新增（`xsy-scm-server/sa-admin/`）

```text
src/main/java/net/lab1024/sa/admin/module/scm/order/
  constant/   ScmOrderStatusEnum, ScmOrderSourceEnum, ScmOrderPriceSourceEnum,
              ScmOrderQuantitySourceEnum, ScmOrderProductTypeEnum, ScmOrderOperationTypeEnum,
              ScmOrderReturnStatusEnum, ScmOrderRefundStatusEnum, OrderErrorCode        (9)
  controller/ SalesOrderController, OrderReturnController, OrderRefundController        (3)
  dao/        SalesOrderDao, SalesOrderItemDao, OrderAddressSnapshotDao, OrderOperationLogDao,
              OrderReturnDao, OrderReturnItemDao, OrderRefundDao, IdempotencyRecordDao    (8)
  domain/entity/  SalesOrderEntity, SalesOrderItemEntity, OrderAddressSnapshotEntity,
                  OrderOperationLogEntity, OrderReturnEntity, OrderReturnItemEntity,
                  OrderRefundEntity, IdempotencyRecordEntity                             (8)
  domain/form/    SalesOrderAddForm, SalesOrderUpdateForm, SalesOrderQueryForm,
                  SalesOrderItemForm, OrderAddressForm, OrderActualQuantityForm, OrderCancelForm,
                  OrderReturnAddForm, OrderReturnItemForm, OrderReturnApproveForm,
                  OrderReturnApproveItemForm, OrderReturnDecisionForm, OrderReturnQueryForm,
                  OrderRefundCompleteForm, OrderRefundQueryForm                         (15)
  domain/vo/      SalesOrderVO, SalesOrderDetailVO, SalesOrderItemVO, OrderAddressSnapshotVO,
                  OrderOperationLogVO, OrderReturnVO, OrderReturnDetailVO, OrderReturnItemVO,
                  OrderRefundVO                                                          (9)
  manager/    OrderStateMachine, OrderAmountCalculator, OrderSnapshotFactory, OrderValidator,
              SalesOrderItemChangeSet                                                    (5)
  service/    SalesOrderService, SalesOrderQueryService, OrderReturnService, OrderRefundService,
              OrderIdempotencyService, OrderNumberGenerator                             (6)
  support/    OrderInventoryContract, NoOpOrderInventoryContract,
              OrderInventoryReserveCommand, OrderInventoryReleaseCommand,
              OrderInventoryAvailability, OrderIdempotencyRequestHasher,
              OrderIdempotencyConflictException                                          (7)
src/main/resources/mapper/scm/order/
              SalesOrderMapper.xml, SalesOrderItemMapper.xml, OrderOperationLogMapper.xml,
              OrderReturnMapper.xml, OrderReturnItemMapper.xml, OrderRefundMapper.xml,
              IdempotencyRecordMapper.xml                                                (7)
src/main/resources/db/migration/
              V13__scm_sales_order.sql, V14__scm_sales_order_permissions.sql             (2)
```

**后端新增总数**：`9+3+8+8+15+9+5+6+7 = 70` Java 文件 + `7` XML + `2` SQL = **79**。

**后端测试新增**（`src/test/java/.../module/scm/order/`）：

```text
manager/    OrderStateMachineTest, OrderAmountCalculatorTest, SalesOrderItemChangeSetTest,
            OrderSnapshotFactoryTest, OrderValidatorTest                              (5)
service/    SalesOrderServiceTest, OrderReturnServiceTest, OrderIdempotencyRequestHasherTest (3)
constant/   OrderErrorCodeTest                                                        (1)
controller/ SalesOrderControllerTest, OrderReturnControllerTest, OrderRefundControllerTest (3)
common/     ScmW4PgITBase                                                             (1)
IT/         ScmOrderMigrationIT, ScmOrderPermissionMigrationIT, SalesOrderServiceIT,
            OrderPriceSnapshotIT, OrderOptimisticLockIT, OrderIdempotencyIT,
            OrderReturnQuotaConcurrencyIT, OrderStateMachineIT, OrderOperationLogIT,
            OrderAmountPrecisionIT                                                    (10)
```

**后端测试总数**：**23**。

### B.2 前端新增（`xsy-scm-web/src/`）

```text
api/business/scm/     order-api.ts, order-return-api.ts, order-refund-api.ts, order-log-api.ts  (4)
constants/business/scm/  order-const.ts                                                          (1)
views/business/scm/order/
                      order-list.vue, order-detail.vue, order-return-list.vue,
                      order-refund-list.vue, order-log-list.vue                                  (5)
views/business/scm/order/components/
                      order-form-drawer.vue, order-item-editable-table.vue,
                      order-return-form-modal.vue                                                (3)
views/business/scm/order/
                      order-form-model.ts, order-errors.ts                                       (2)
```

**前端新增总数**：**15**。

### B.3 前端修改（**最小化**）

```text
src/constants/index.ts          追加 order 枚举注册（+3 行）
路由：V14 菜单生成列表路由；详情 Drawer，不改 router core
tools/ts_baseline_ratchet.py    SCM_PREFIXES 追加 views/business/scm/order（若新路径非白名单）
```

### B.4 E2E 与工具

```text
xsy-scm-web/e2e/scm-order.spec.ts          新增（6 用例）
tools/verify_w4_legacy.py                  新增（断言 A–I）
docs/architecture/2026-09-16-w4-order-audit.md            本波次（已交付）
docs/architecture/2026-09-16-w4-order-target-design.md    本文件
docs/architecture/w4-applied-migrations.sha256            编码后冻结
docs/architecture/2026-09-16-w4-order-验收报告.md          编码后
docs/architecture/2026-09-16-w4-order-approval.md          编码后
```

### B.5 **零修改清单（冻结，必须验证）**

```text
xsy-scm-server/sa-admin/src/main/resources/db/migration/V1..V12.sql      （12 个文件）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/**  （W1）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/** （W2）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/pricing/**  （W3）
xsy-scm-server/sa-base/**                                                  （SmartAdmin 底座）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/system/**  （SmartAdmin 系统）
xsy-scm-web/src/**（除 B.3 的三处最小修改）
project-reference-examples/xsy-scm/**                                      （C 源，零修改）
xsy-scm-miniapp/**                                                          （冻结只读）
```

---

**最终裁决已整合。直接执行 T0 → T23，完成 W4 验收报告后停止，不进入 W5。**


## 16. 实施映射与证据说明（2026-09-16）

- §13 的 T0–T23 为交付阶段。测试类数与单测数量是估算；最终将相关行为集中到 `OrderRulesTest`、`OrderWebTest`、`OrderUnpricedIT`、`SalesOrderServiceIT`、`ScmOrderMigrationIT`，完整回归结果与不变量对账见验收报告。
- 地址 DAO 有独立 XML，故最终为 8 份 Mapper XML；操作日志 DAO 只暴露 insert/query，没有 BaseMapper 更新/删除入口。append-only 在应用写入口保证，不新增数据库触发器；数据库管理员直连 SQL 不在该防线内。
- W3 Resolver 没有人工 override 重载，普通行严格调用 requireResolvable；人工价复用 resolve 的可售/可见性检查，加订单域 OVERRIDE 校验，全部有效后才锁价。未修改 W3。
- W2 客户删除引用检查通过订单域 `OrderCustomerReferenceGuard` 接在 W2 已持锁的 softDelete DAO 入口，复用 40939；W2 文件零修改。此为引用保护，不是 DataScope。
- 客户身份、结算方式、业务员及地址在创建时固定；编辑订单仅允许修改草稿输入与商品行。实际数量变更同时推进订单聚合 version，过期确认表单必须刷新。
- 幂等作用域附带当前操作者，避免不同用户复用 key 时读到对方重放数据；数据库 INSERT 竞争与请求规范化保持不变。结果用 JSON object 包装，符合 result_data CHECK。
- `ScmOrder*Enum` 与表单、DAO、VO 按最终 DDL 生成并逐项实测；实体和公开 VO 独立，API 四位字符串/null 均有 Web 与 PG 测试。
- 保留 SmartAdmin Layout、表格工具、权限和枚举选择器。C 页面骨架复制后适配，详情使用 Drawer；没有引入新前端框架。
- T0 的首次后端基线因缺少 XSY_V2_DB_PASSWORD 失败；沿用本机已有凭据后 120/120 通过。T0 时未启动浏览器服务，浏览器基线由最终 W1–W4 全套回归补验；不将未执行的初始浏览器检查记为通过。
