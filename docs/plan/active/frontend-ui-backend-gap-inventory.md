# 前端 UI 优化的后端字段缺口盘点

盘点日期：2026-10-06。
状态：只读静态盘点完成（读代码与 VO 定义），未运行构建、测试或迁移，未访问数据库。
依据：[前端 UI 优化规划](frontend-ui-optimization-plan.md)、[财务与报表缺口盘点](finance-r2-report-gap-inventory.md)及当前主线代码。

## 1. 为什么单独开这张单

`frontend-ui-optimization-plan.md` §3「非目标」明确本轮 UI 优化 **不改后端接口、不改 DTO / VO / Entity 字段结构、不改数据库**；
§33「建议提交拆分」进一步禁止一个提交同时包含「UI foundation + 多业务模块 + **后端字段重构** + 数据库迁移」。

因此在执行 UI 优化时遇到的后端字段不足，一律**不在 UI 提交里补齐**，也不在前端用近似字段冒充。
本文件集中登记这些缺口，作为后续独立后端工单的输入；每条都给出**当前代码证据**，便于独立复核。

**前端既有纪律**：遇到缺口时前端只做「不显示」，绝不用 `orderId` 冒充订单号、用 0 冒充未知成本之类的降级方案。
本盘点同时列出前置条件与建议补齐方式，但**不代表已排期**。

## 2. 需要在 UI 上落位、但当前后端未提供的字段

### 2.1 报表 - 收货 / 入库（§21）

| 编号 | 缺口 | 现有代码证据 | 建议补齐方式 |
| --- | --- | --- | --- |
| B1 | 收货明细 / 入库明细 / 待入库三张表**无页面级汇总**（无合计金额、合计数量、单据数、SKU 种类数） | `ReceiptReportVO` 只有 `ReceiptRow` / `InboundRow` / `PendingPutawayRow` 三个**行** VO，**无 `Summary` 内部类**（`report/domain/vo/ReceiptReportVO.java`）；`ReceiptReportService` 三个方法一律 `return PageResult<...>`，无 summary 端点（`report/service/ReceiptReportService.java:37,49,70`）。对比：库存域已有 `/inventory/loss/summary`（返回 `InventoryReportVO.LossSummary`）与 `/inventory/flow-summary/query`，说明本报表缺的是同一模式 | 仿 `InventoryReportVO.LossSummary`：新增 `ReceiptReportVO.ReceiptSummary` / `InboundSummary` / `PendingPutawaySummary` 与对应 `/summary` 端点，复用三条查询的**同一时间范围与仓库范围**；汇总口径必须与列表同方法派生，避免合计与明细对不上 |
| B2 | 收货明细行**无「收货人 / 经办人」** | `ReceiptRow` 无 operator 字段（`ReceiptReportVO.java:23-57`）；对比 `InboundRow` 有 `operator`（`:79`） | 若确认业务需要，从 `purchase_receipt` 补 `operator`（需确认码表语义与 `InboundRow.operator` 一致） |

**说明**：B1 属「后端无聚合」，不是「前端不做」。前端在 B1 落地前**不得**用当前页数据在前端求和冒充全量汇总（分页会算错）。

### 2.2 财务详情抽屉（§20.6）

| 编号 | 缺口 | 现有代码证据 | 建议补齐方式 |
| --- | --- | --- | --- |
| B3 | 财务详情**表头 VO 无审计字段**（createTime / updateTime / creator），§20.6 计划第 7 段「系统信息」无法渲染 | `FinanceReceivableVO` 字段止于 `dueDate` / `reason`（`finance/domain/vo/FinanceReceivableVO.java:57-58`），`FinancePayableVO` / `FinanceReceiptVO` 同样无审计字段；`FinanceReceivableDetailVO` 只组合 `receivable` / `items` / `redEntries` / `originalReceivable` / `writeOffs` / `operationLogs`（`finance/domain/vo/FinanceReceivableDetailVO.java:11-21`） | 在 `FinanceReceivableVO` / `FinancePayableVO` / `FinanceReceiptVO` 增加 `createdAt` / `updatedAt` / `createdBy`（若表上确有 `create_time` / `update_time` / `creator` 列；需先核对 migration） |

**重要澄清（避免误修）**：**流水行本身是有审计信息的**——`FinanceOperationLogVO` 已含 `operator` 与 `createdAt`（`finance/domain/vo/FinanceOperationLogVO.java:20,28`），§20.6 第 6 段「流水记录」已完整可用。
缺的只是**表头单据级**的创建/更新信息（第 7 段）。前端已在 `finance-detail-drawer.vue:97` 用注释记录该段不渲染，**不得**用 `operationLogs` 首条冒充单据创建时间。

### 2.3 销售订单详情（§14.3）

| 编号 | 缺口 | 现有代码证据 | 建议补齐方式 |
| --- | --- | --- | --- |
| B4 | 订单详情**无库存 / 配送状态**，§14.3 第 5 段无法渲染 | `SalesOrderDetailVO extends SalesOrderVO`，新增字段仅 `items` / `address` / `discount` / `gifts`（`order/domain/vo/SalesOrderDetailVO.java:11-31`）；`SalesOrderVO` 状态止于 `status` / `cancelReason` / `submittedAt` / `confirmedAt` / `cancelledAt`，**无库存占用状态、无配送/发运状态**（`order/domain/vo/SalesOrderVO.java:21,30-33`） | 若需求确认，由订单域组合库存预留状态与配送单状态为**只读快照**返回（订单本身不写库存状态，避免双写） |
| B5 | 订单详情**无退货 / 退款数据**，§14.3 第 6 段无法渲染 | 同上，`SalesOrderDetailVO` 无退货或退款集合；退货走独立列表 `order-return-list.vue`，其 `ReturnRow` 亦只有 `orderId`（见 B6） | 由详情接口组合该订单的退货单与已完成退款金额（只读聚合），不做前端二次请求拼装 |

### 2.4 其他已在前端注释中登记的缺口

以下缺口此前已在前端代码注释中显式登记，此处统一收编，避免散落。

| 编号 | 缺口 | 现有代码证据 | 建议补齐方式 |
| --- | --- | --- | --- |
| B6 | 退货列表**无原订单号 / 客户名** | `OrderReturnVO` 只带 `orderId`，`ReturnRow` 无订单号与客户名 → `order-return-list.vue:139-141` 注释说明 §14.5「原订单/客户（若 VO 已有）」当前无法满足 | `OrderReturnVO` 补 `orderNo` / `customerName`（订单已有关联，属读路径补齐，无迁移） |
| B7 | 优惠券列表**无发放量 / 已领取 / 已使用** | `PromotionCoupon` 无这三个统计字段 → `promotion-coupon-list.vue:323` 注释说明「VO 不返回，前端不臆造」 | 由券模板聚合领取/使用计数（只读聚合）；需确认是否需要按时间窗口径 |
| B8 | 库存预留**无规格值（specValues）** | 预留 VO 不返回 `specValues` → `inventory-reservation-list.vue:81` 注释说明「主行只能是规格名称，编码作次要行」 | 预留 VO 补 `specValues`（与商品/库存域口径一致） |

## 3. 明确**不**属于缺口的情形

避免被误当缺口重复开单：

- **财务「流水记录」段**：`FinanceOperationLogVO` 已有 `operator` / `createdAt`，功能完整（见 2.2 澄清）。
- **收货与入库的生命周期区分**：`ReceiptRow` 已同时暴露 `receiptMode` 与 `putawayStatus`，页面能正确区分「已确认收货」与「已入库」，**不是缺口**。
- **订单优惠与赠品**：`SalesOrderDetailVO.discount`（`OrderDiscountVO`）与 `gifts`（`PromotionDiscountVO.GiftEntitlementVO`）已提供，§14.3 金额结算段可完整渲染。
- **入库成本缺失的标志位**：`InboundRow.costMissing` 已用于区分「无成本权限」与「成本确实缺失」，属已解决项。

## 4. 处理纪律

1. 本清单条目**不与 UI 提交混做**；每条独立后端工单、独立提交。
2. 后端补齐后，前端再单独提交「段落开启 + 契约测试」，两者不合并。
3. 补齐前，前端保持现状：**不渲染该段 / 不臆造数值**，并在代码注释中指向本文件对应编号。
4. 任何涉及 VO 字段新增的改动，须同步核对该字段在表结构中是否真实存在（避免为「看起来完整」而造字段）。
