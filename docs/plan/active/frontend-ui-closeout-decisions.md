# 前端 UI 收尾口径决策（Sprint F-A）

决策日期：2026-10-07。
基线 HEAD：`422cc988`（审计文档 `be823423` 之后）。
状态：**本轮只产出决策与映射，未修改任何 Vue / LESS / TS 业务代码**。
依据：[前端 UI 收尾审计](frontend-ui-closeout-audit.md) §5.A3 / §5.A4 / §6、[前端 UI 优化规划](frontend-ui-optimization-plan.md) §6.2 / §7 / §8、[后端字段缺口盘点](frontend-ui-backend-gap-inventory.md) §4 F1。

本文件定稿两件事：

- **C1**：数字与编号的视觉口径（`.scm-money` / `.scm-quantity` / `.scm-mono`）与 `.num` 的逐项迁移映射。
- **C2**：Drawer 宽度分级由 S/M/L 扩展为 S/M/L/XL，并给出 34 个 Drawer 实例的完整映射。

两者都**只定义，不执行**；执行分别属于后续 Sprint F-B（数值口径）与 F-D（Drawer 铺开）。

---

# C1 数字与编号视觉口径

## C1.1 决策

正式采用**三类公共样式**，职责互斥、不得混用：

| class | 字体 | 数字排版 | 用于 |
| --- | --- | --- | --- |
| `.scm-money` | 继承全局字体（比例字体） | `tabular-nums` | 金额、单价、成本、应收/应付、收款/付款、库存金额、财务类金额 |
| `.scm-quantity` | 继承全局字体（比例字体） | `tabular-nums` | 数量、件数、库存数、预留量、可用量、百分比、普通统计数字 |
| `.scm-mono` | **等宽字体** | `tabular-nums` | 订单号、采购单号、收货单号、库存单据号、财务单号、商品编码、商品规格编码、客户/供应商/仓库编码、外部流水号，及其他需要字符等宽的技术/业务标识 |

`.scm-money` / `.scm-quantity` 已在 `theme/scm/table.less` 中落地，本轮**不修改**。

`.scm-mono` 为**新增**，定义（Sprint F-B 落到 `theme/scm/table.less`）：

```css
.scm-mono {
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    "Liberation Mono",
    monospace;
  font-variant-numeric: tabular-nums;
}
```

## C1.2 判定规则

给后续迁移一个可复用的判定顺序，避免逐页凭感觉：

```text
1. 这个字段是「钱」吗？（金额 / 单价 / 成本 / 应收应付 / 收付款）
   → 是：.scm-money
2. 这个字段是「量或比例」吗？（数量 / 件数 / 库存数 / 预留 / 可用 / 百分比 / 统计计数）
   → 是：.scm-quantity
3. 这个字段是「标识」吗？（单号 / 编码 / 外部流水号，即人要去别处比对或抄录的字符串）
   → 是：.scm-mono
4. 都不是（普通文本 / 时间 / 状态）
   → 不加任何数字类
```

**关键边界**：`.scm-mono` 与 `.scm-quantity` 的区别不是"看起来像数字"，而是**语义是不是标识**。同一个 `20261007` 出现在"日期列"里属第 4 类，出现在"单据号"里属第 3 类。

## C1.3 禁止事项

- **禁止**把现有 `class="num"` 机械替换为 `scm-quantity`。二者视觉不同（`.num` 是等宽字体，`.scm-quantity` 是比例字体仅数字等宽），机械替换会静默改变字体，属设计变更而非清理。
- **禁止**在业务页面继续新增自定义 `.num`。
- **禁止**用 `.scm-money` 承载非金额（例如把"收货进度 60%"写成 money）。

## C1.4 `.num` 全量使用点与迁移映射

扫描范围：`xsy-scm-web/src/views/business/scm`（已确认该目录外无 `.num` 使用）。

**统计口径**：

```text
定义 .num 的文件：19 个（16 个 ui-monospace 等宽变体 + 3 个 tabular-nums 变体）
使用 .num 的文件：17 个
使用点总数：55 处
定义但从未使用：2 个文件（见 C1.5）
使用但未定义：0 个文件
```

路径均相对 `xsy-scm-web/src/views/business/scm/`。

### 迁移到 `.scm-money`（12 处）

| 文件 | 位置/字段 | 当前 class | 字段语义 | 目标 class | 理由 |
| --- | --- | --- | --- | --- | --- |
| `purchase/components/purchase-order-detail-drawer.vue` | :67 `order.totalAmount` | `num` | 采购金额 | `scm-money` | 金额，列内需纵向比大小 |
| `purchase/components/purchase-order-detail-drawer.vue` | :106 `record.purchasePrice` | `num` | 采购单价 | `scm-money` | 单价属金额 |
| `purchase/components/purchase-order-detail-drawer.vue` | :109 `record.lineAmount` | `num` | 行金额 | `scm-money` | 金额 |
| `report/report-finance-overview.vue` | :66 `moneyText(text)` | `num` | 财务金额 | `scm-money` | 金额；该文件本就只用 tabular-nums，迁移后视觉几乎不变 |
| `report/report-finance-overview.vue` | :133 `moneyText(text)` | `num` | 财务金额 | `scm-money` | 同上 |
| `report/report-inventory-list.vue` | :122 `record.unitCost` | `num` | 单位成本 | `scm-money` | 成本属金额 |
| `report/report-inventory-list.vue` | :125 `record.costAmount` | `num` | 成本金额 | `scm-money` | 金额 |
| `report/report-inventory-list.vue` | :224 `record.avgCost` | `num` | 平均成本 | `scm-money` | 成本属金额 |
| `report/report-inventory-list.vue` | :227 `record.amount` | `num` | 库存金额 | `scm-money` | 金额 |
| `report/report-purchase-list.vue` | :191 `record.orderAmount` | `num` | 采购金额 | `scm-money` | 金额 |
| `report/report-purchase-list.vue` | :194 `record.avgPurchasePrice` | `num` | 采购均价 | `scm-money` | 单价属金额 |
| `report/report-purchase-list.vue` | :200 `record.inboundCostAmount` | `num` | 入库成本金额 | `scm-money` | 金额 |

### 迁移到 `.scm-quantity`（43 处）

| 文件 | 位置/字段 | 当前 class | 字段语义 | 目标 class | 理由 |
| --- | --- | --- | --- | --- | --- |
| `inventory/components/inventory-conversion-detail-drawer.vue` | :31 `sourceQuantity` | `num` | 源数量 | `scm-quantity` | 数量 |
| `inventory/components/inventory-conversion-detail-drawer.vue` | :34 `targetQuantity` | `num` | 目标数量 | `scm-quantity` | 数量 |
| `inventory/components/inventory-loss-gain-detail-drawer.vue` | :30 `record.quantity` | `num` | 报损报溢数量 | `scm-quantity` | 数量 |
| `inventory/components/inventory-stocktake-detail-drawer.vue` | :26 `record.bookQuantity` | `num` | 账面量 | `scm-quantity` | 数量 |
| `inventory/components/inventory-stocktake-detail-drawer.vue` | :29 `record.actualQuantity` | `num` | 实盘量 | `scm-quantity` | 数量 |
| `inventory/components/inventory-stocktake-detail-drawer.vue` | :32 `record.deltaQuantity` | `num` | 差异量 | `scm-quantity` | 数量（正负由 `deltaClass` 表达，与数字类无关） |
| `inventory/components/inventory-transfer-detail-drawer.vue` | :27 `record.quantity` | `num` | 调拨数量 | `scm-quantity` | 数量 |
| `purchase/purchase-demand-list.vue` | :94 `record.requiredQuantity` | `num` | 需求数量 | `scm-quantity` | 数量 |
| `purchase/purchase-demand-list.vue` | :98 `record.allocatedQuantity` | `num` | 已分配数量 | `scm-quantity` | 数量 |
| `purchase/purchase-demand-list.vue` | :102 `record.unallocatedQuantity` | `num` | 未分配数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-demand-batch-detail-drawer.vue` | :64 `quantity(record[col])` | `num` | 快照数值列（`numericColumns`） | `scm-quantity` | 由 `numericColumns` 白名单驱动，全为数量口径 |
| `purchase/components/purchase-demand-batch-detail-drawer.vue` | :82 `record.sourceQuantity` | `num` | 来源数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-demand-batch-detail-drawer.vue` | :85 `record.requiredQuantity` | `num` | 需求数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-demand-summary-preview.vue` | :68 `quantity(record[col])` | `num` | 需求汇总数值列 | `scm-quantity` | 由 `quantity()` 包装，数量口径 |
| `purchase/components/purchase-order-detail-drawer.vue` | :70 `order.receivedProgress` | `num` | 收货进度（百分比） | `scm-quantity` | 百分比，按 C1.1 归数量档 |
| `purchase/components/purchase-order-detail-drawer.vue` | :94 `record.plannedQuantity` | `num` | 计划数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-detail-drawer.vue` | :97 `record.receivedQuantity` | `num` | 已收数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-detail-drawer.vue` | :100 `record.remainingQuantity` | `num` | 剩余数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-detail-drawer.vue` | :103 `record.overReceiptQuantity` | `num` | 超收数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-detail-drawer.vue` | :129 `record.quantity` | `num` | 数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-item-editable-table.vue` | :53 `allocatedOnItem(record)` | `num` | 本行已分配量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-order-item-editable-table.vue` | :118 `demand.unallocatedQuantity` | `num` | 需求未分配量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-confirm-modal.vue` | :45 `record.plannedQuantity` | `num` | 计划数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-confirm-modal.vue` | :48 `record.cumulativeReceivedQuantity` | `num` | 累计已收 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-confirm-modal.vue` | :51 `record.remainingQuantity` | `num` | 剩余数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-confirm-modal.vue` | :54 `record.overReceiptQuantity` | `num` | 超收数量 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-confirm-modal.vue` | :57 `record.receiptDifference` | `num` | 收货差异 | `scm-quantity` | 数量 |
| `purchase/components/purchase-receipt-item-workbench.vue` | :60 `quantity(record[col])` | `num` | 收货明细数值列 | `scm-quantity` | 由 `quantity()` 包装，数量口径 |
| `report/report-inventory-list.vue` | :119 `record.quantity` | `num` | 数量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :128 `record.beforeQuantity` | `num` | 变动前数量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :131 `record.afterQuantity` | `num` | 变动后数量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :215 `record.quantity` | `num` | 现有量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :218 `record.reservedQuantity` | `num` | 预留量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :221 `record.availableQuantity` | `num` | 可用量 | `scm-quantity` | 数量 |
| `report/report-inventory-list.vue` | :282 `quantityText(record[col])` | `num` | 汇总数值列 | `scm-quantity` | 由 `quantityText()` 包装，数量口径 |
| `report/report-purchase-list.vue` | :182 `record.orderCount` | `num` | 采购单数 | `scm-quantity` | 统计计数，属普通统计数字 |
| `report/report-purchase-list.vue` | :185 `record.plannedQuantity` | `num` | 计划数量 | `scm-quantity` | 数量 |
| `report/report-purchase-list.vue` | :188 `record.receivedQuantity` | `num` | 已收数量 | `scm-quantity` | 数量 |
| `sorting/components/sorting-task-create-modal.vue` | :74 `record.actualQuantity` | `num` | 实分数量 | `scm-quantity` | 数量 |
| `sorting/components/sorting-task-create-modal.vue` | :77 `record.orderedQuantity` | `num` | 订单数量 | `scm-quantity` | 数量 |
| `sorting/components/sorting-task-detail-drawer.vue` | :102 `record.plannedQuantitySnapshot` | `num` | 计划量快照 | `scm-quantity` | 数量 |
| `sorting/components/sorting-task-detail-drawer.vue` | :116 `record.sortedQuantity` | `num` | 已分拣量 | `scm-quantity` | 数量 |
| `delivery/components/route-planning-suggestion-panel.vue` | :51 `record[col]`（`legDistance` / `cumulativeDistance`） | `num` | 分段距离 / 累计距离（米） | `scm-quantity` | 距离属数量档；`:50` 已用 `v-if` 限定这两个 dataIndex |

### 迁移到 `.scm-mono`（0 处）

**本项为 0，是本次扫描最重要的结论**：

> 现有 55 处 `.num` 使用点**全部是金额或数量，没有任何一处是单号 / 编码 / 流水号**。

也就是说，项目里过去的 `.num` 从来不是"给标识用的等宽类"，而是"给数字用的等宽类"。因此：

- 本轮 **不存在** `.num → .scm-mono` 的迁移；
- `.scm-mono` 是**为当前用普通文本渲染的标识类字段**准备的（单据号列、编码列、外部流水号），这些位置目前没有 class，需要在 Sprint F-B 单独识别并接入，**不在本轮 `.num` 迁移范围内**。

## C1.5 定义但从未使用的 `.num`（2 个文件）

以下文件定义了 `.num { … }` 但模板中没有任何 `class="num"`，属纯残留：

| 文件 | 定义位置 | 处理 |
| --- | --- | --- |
| `delivery/route-detail.vue` | :843 | Sprint F-B 删除该样式块（该文件无 `.num` 使用） |
| `purchase/purchase-order-list.vue` | :583 | Sprint F-B 删除该样式块（该文件无 `.num` 使用） |

## C1.6 Sprint F-B 完成定义

```text
views/business/scm 下不再存在任何页面自定义的 .num：
- 19 个定义全部移除（17 个随迁移一并删除 + 2 个纯残留直接删除）
- 55 处使用点全部改为 scm-money / scm-quantity
- 新增 .scm-mono 到 theme/scm/table.less
```

**视觉影响提示**：55 处中有 43 处会从**等宽字体**变为**比例字体 + tabular-nums**。数字纵向对齐不变（`tabular-nums` 保证等宽数字），但字形会变。这属于设计变更，Sprint F-B 的提交信息必须显式说明，不得以"CSS 清理"名义提交。

---

# C2 Drawer 宽度分级

## C2.1 决策

`theme/scm/scm-drawer.ts` 由三级扩展为四级（Sprint F-D 落地）：

| 等级 | 宽度 | 语义 |
| --- | --- | --- |
| S | 600 | 简单配置 / 简单维护 |
| M | 780 | 中型主数据 / 常规编辑表单 |
| L | 940 | 复杂业务编辑表单 |
| XL | 1120 | 复杂详情 / 工作台 / 内嵌宽表 / 多明细业务 |

**不再允许业务页面硬编码** `620 / 760 / 860 / 920 / 1000 / 1120` 等宽度。所有 Drawer 一律经 `scmDrawerWidth(level)` 取值，视口兜底继续由 `theme/scm/responsive.less` 的 `max-width: 96vw` 负责。

## C2.2 映射原则

不是按最接近的数字机械映射，而是**读 Drawer 内容**后判断：字段数量、是否双列、是否有内嵌表格、是否有商品明细、是否有地图、是否有复杂详情、是否是操作工作台。

数字仅供参考的起点：`620 → 通常 S`、`760 → 通常 M`、`860 → 通常 L`、`920 → L`、`1000 → L 或 XL`、`1120 → XL`。

## C2.3 全量映射表

范围：`views/business/scm` 下 **33 个文件 / 34 个 `<a-drawer>` 实例**（`inventory-outbound-list.vue` 内含新建与详情两个）。

路径均相对 `xsy-scm-web/src/views/business/scm/`。

### S = 600（5 个）

| 文件 | 当前宽度 | 建议等级 | 目标宽度 | 业务类型 | 调整理由 |
| --- | --- | --- | --- | --- | --- |
| `delivery/components/route-form-drawer.vue` | `scmDrawerWidth('s')` | S | 600 | 线路新建/编辑 | 已合规；3 个 section、2 列短表单、无明细表 |
| `finance/balance-movement-detail.vue` | `min(600px, 96vw)` | S | 600 | 余额来源流水详情 | 已合规；单列描述 6 项，纯只读 |
| `pricing/components/agreement-price-form-drawer.vue` | `scmDrawerWidth('s')` | S | 600 | 客户协议价编辑 | 已合规；8 项单列表单 |
| `pricing/components/customer-type-price-form-drawer.vue` | `620` | S | 600 | 客户类型价编辑 | 4 项单列表单，620 属规范外取值，收敛到 S |
| `inventory/inventory-warning-threshold-list.vue` | `620` | S | 600 | 预警阈值配置 | 4 项单列 + 备注，典型简单配置；620 → 600 |

### M = 780（4 个）

| 文件 | 当前宽度 | 建议等级 | 目标宽度 | 业务类型 | 调整理由 |
| --- | --- | --- | --- | --- | --- |
| `supplier/components/supplier-form-drawer.vue` | `scmDrawerWidth('m')` | M | 780 | 供应商主数据编辑 | 已合规；4 个 section、2 列、含地图选点但无宽表 |
| `order/components/order-payment-drawer.vue` | `min(780px, 96vw)` | M | 780 | 订单支付 | 已合规；2 项表单 + 支付记录表（`scroll.x=680`，780 内可容纳）+ 嵌套流水抽屉 |
| `purchase/components/purchase-receipt-form-drawer.vue` | `min(720px, 96vw)` | M | 780 | 收货单新建/编辑备注 | 仅 4~5 项表单 + 只读描述，无明细表；720 偏窄，收敛到 M |
| `order/order-return-list.vue` | `min(850px,96vw)` | M | 780 | 退货单详情 | 单列描述 4 项 + 一张明细表（`scroll.x=600`），780 足够；850 → 780 |

### L = 940（12 个）

| 文件 | 当前宽度 | 建议等级 | 目标宽度 | 业务类型 | 调整理由 |
| --- | --- | --- | --- | --- | --- |
| `customer/components/customer-form-drawer.vue` | `scmDrawerWidth('l')` | L | 940 | 客户主数据编辑 | 已合规；4 个 section、21 个双列字段，复杂编辑表单 |
| `product/components/product-form-drawer.vue` | `min(920px, 96vw)` | L | 940 | 商品编辑 | 多 section、38 项表单 + 商品规格可编辑表；920 → 940 |
| `finance/finance-payable-list.vue`（红字应付） | `min(920px, 96vw)` | L | 940 | 登记红字应付 | 只读描述 + 原因 + 可编辑明细表（`scroll.x=850`）；920 → 940 |
| `finance/finance-write-off-list.vue`（多目标核销） | `min(860px, 96vw)` | L | 940 | 登记多目标核销 | 资金单选型 + 可编辑分配表（`scroll.x=690`）+ 目标单增删，属操作型表单；860 → 940 |
| `inventory/inventory-outbound-list.vue`（新建/编辑草稿） | `900` | L | 940 | 出库单编辑 | 2 项表单 + 商品规格可编辑明细表（含 `SkuSelect` 260px）；900 → 940 |
| `inventory/inventory-loss-gain-list.vue`（新建/编辑） | `900` | L | 940 | 报损报溢单编辑 | 4 项表单 + 可编辑明细表；900 → 940 |
| `inventory/inventory-stocktake-list.vue`（新建/编辑草稿） | `900` | L | 940 | 盘点单编辑 | 2 项表单 + 可编辑实盘明细表；900 → 940 |
| `inventory/inventory-transfer-list.vue`（新建/编辑草稿） | `900` | L | 940 | 调拨单编辑 | 3 项表单 + 可编辑明细表；900 → 940 |
| `inventory/components/inventory-loss-gain-detail-drawer.vue` | `860` | L | 940 | 报损报溢单详情 | 双列描述 11 项 + 明细表；860 → 940 |
| `inventory/components/inventory-stocktake-detail-drawer.vue` | `860` | L | 940 | 盘点单详情 | 双列描述 7 项 + 明细表（`scroll.x=900`）；860 → 940 |
| `inventory/components/inventory-transfer-detail-drawer.vue` | `860` | L | 940 | 调拨单详情 | 双列描述 9 项 + 明细表；860 → 940 |
| `inventory/inventory-outbound-list.vue`（出库单详情） | `760` | L | 940 | 出库单详情 | 双列描述 7 项 + 明细表；与同族四个库存单据详情统一到 L（原 760 偏窄） |

### XL = 1120（13 个）

| 文件 | 当前宽度 | 建议等级 | 目标宽度 | 业务类型 | 调整理由 |
| --- | --- | --- | --- | --- | --- |
| `finance/finance-detail-drawer.vue` | `min(1120px, 96vw)` | XL | 1120 | 财务单据详情 | 已合规；7 个 section、5 张内嵌表（`scroll.x` 760/900/960）+ 金额组成网格 |
| `supplier/components/supplier-sku-drawer.vue` | `1120` | XL | 1120 | 供应商关联商品维护 | 已合规；整表替换式宽表编辑 |
| `purchase/components/purchase-demand-batch-detail-drawer.vue` | `1000px` | XL | 1120 | 冻结批次明细 | 三列描述 12 项 + 2 张宽表（`scroll.x` 1800 / 1400），宽度需求远超 L |
| `inventory/components/inventory-conversion-detail-drawer.vue` | `1000` | XL | 1120 | 转换单详情 | 双列描述 11 项 + 明细表 `scroll.x=1100`，L(940) 不足以容纳 |
| `inventory/inventory-conversion-list.vue`（新建/编辑） | `1080` | XL | 1120 | 转换单编辑 | 4 项表单 + 双 `SkuSelect` 可编辑表（`scroll.x=900`），多明细业务 |
| `report/report-components/report-drilldown-drawer.vue` | `min(1180px, 94vw)` | XL | 1120 | 报表下钻 | 单张宽表 `scroll.x=1500`；1180 → 1120（仍大于 XL，见 C2.4） |
| `order/components/order-form-drawer.vue` | `min(1200px, 96vw)` | XL | 1120 | 销售订单编辑 | 10 项双列表单 + 商品明细可编辑表 + 价格解析，多明细业务 |
| `order/order-detail.vue` | `min(1180px,96vw)` | XL | 1120 | 销售订单详情 | 6 个 section + 明细表 `scroll.x=1100` + 时间线 + 变更对比 |
| `purchase/components/purchase-order-form-drawer.vue` | `min(1280px, 96vw)` | XL | 1120 | 采购单编辑 | 双列表单 + 需求分配可编辑表，多明细业务 |
| `purchase/components/purchase-order-detail-drawer.vue` | `min(1240px, 96vw)` | XL | 1120 | 采购单详情 | 13 项描述 + 明细表 `scroll.x=1300` |
| `sorting/components/sorting-scale-drawer.vue` | `960px` | XL | 1120 | 秤读数工作台 | 宽表 `scroll.x=1410`，属工作台；960 → 1120 |
| `sorting/components/sorting-task-detail-drawer.vue` | `min(1500px, 96vw)` | XL | 1120 | 分拣任务详情/工作台 | 任务头 + 三列描述 + 明细表 `scroll.x=1420`，属工作台 |
| `delivery/route-detail.vue` | `min(1500px, 96vw)` | XL | 1120 | 配送线路详情/工作台 | 线路头 + 汇总 + 地图面板 + 订单/履约/打印多面板 |

### 统计

```text
S  = 5
M  = 4
L  = 12
XL = 13
合计 = 34（对应 33 个文件）
```

## C2.4 需要人工决策的 Drawer

以下 9 项无法仅凭分级规则定论，**不建议在 Sprint F-D 机械执行**：

| # | Drawer | 问题 | 建议决策项 |
| --- | --- | --- | --- |
| 1 | `delivery/route-detail.vue` | 含地图面板 + 多面板工作台，1500 → 1120 会明显压缩地图与面板并排布局 | 是否允许工作台类单独放宽，或改为地图全屏入口 |
| 2 | `sorting/components/sorting-task-detail-drawer.vue` | 明细表 `scroll.x=1420` > 1120，收敛后抽屉内必出横向滚动 | 接受内部横向滚动，还是收窄该表列宽 |
| 3 | `purchase/components/purchase-demand-batch-detail-drawer.vue` | 表 `scroll.x=1800`，是全部 Drawer 中宽度需求最大者 | 同上；1800 已超出任何合理抽屉宽度，建议改为独立页面或列折叠 |
| 4 | `report/report-components/report-drilldown-drawer.vue` | 表 `scroll.x=1500` > 1120 | 接受内部滚动，或下钻改为独立页面 |
| 5 | `purchase/components/purchase-order-detail-drawer.vue` | 表 `scroll.x=1300` > 1120 | 同上 |
| 6 | `sorting/components/sorting-scale-drawer.vue` | 表 `scroll.x=1410` > 1120 | 同上 |
| 7 | `order/order-detail.vue` | 表 `scroll.x=1100` 略大于 XL 可用内宽（约 1072） | 接受 1% 级内部滚动，或把明细表 `scroll.x` 调到 1060 |
| 8 | 库存 5 个单据详情抽屉 | 现状宽度不一致：760 / 860 / 860 / 860 / 1000；本方案统一为 L(940) + 转换单 XL(1120) | 确认是否接受"转换单与其他四张不同级"，或把转换单明细表列宽压到 940 内 |
| 9 | `inventory/inventory-conversion-detail-drawer.vue` | 明细表 `scroll.x=1100` 略大于 XL 内宽 | 同 #7 |

**共同结论**：C2 暴露的不只是"宽度不统一"，而是**多个 Drawer 内嵌表格的 `scroll.x` 本身超过任何合理抽屉宽度**。这是 Sprint F-D 之前需要先拍板的口径，否则会变成"把硬编码换成另一种硬编码"。

## C2.5 规划与代码的冲突

| # | 冲突 | 规划/文档表述 | 当前代码实际 | 处理 |
| --- | --- | --- | --- | --- |
| 1 | Drawer 分级等级数 | 规划 §6.2 只有 S/M/L 三级 | 实际存在 1000 / 1080 / 1120 / 1180 / 1200 / 1240 / 1280 / 1500 等取值，均超出三级 | 本文件扩展为四级并回写规划 §6.2 |
| 2 | `.num` 规模 | 缺口盘点 §4 F1 表述为"16 个页面各自存在" | 实际 19 个文件定义、17 个文件使用、55 处使用点；另有 2 个文件定义后从未使用 | 以本文件为准，回写 F1 |
| 3 | Drawer 数量 | 收尾审计 §5.A3 记为"33 个 a-drawer" | 33 个**文件**、34 个**实例**（`inventory-outbound-list.vue` 含新建 + 详情两个） | 以本文件为准，回写审计 §5.A3 |
| 4 | 规划 §8 操作列上限 | "少数复杂工作台可放宽到 180px" | 与收尾审计 §5.A1 列出的 7 处超宽（200~250px）一致，无冲突 | 无需处理，F-C 执行 |
| 5 | `.scm-mono` 的迁移来源 | 缺口盘点 §4 F1 预期"若要统一需新增等宽类再迁移" | 55 处 `.num` **无一处是标识类**，`.num → .scm-mono` 迁移数为 0 | 澄清：`.scm-mono` 面向当前无 class 的标识列，不在 `.num` 迁移范围内 |

---

# 本轮汇报

| 项 | 结果 |
| --- | --- |
| 1. `.num` 总使用点 | **55 处**，分布在 **17 个文件**；另有 19 个文件定义（含 2 个定义后从未使用） |
| 2. 各目标 class 迁移数 | `.scm-money` **12 处**、`.scm-quantity` **43 处**、`.scm-mono` **0 处** |
| 3. Drawer 分级分布 | S **5**、M **4**、L **12**、XL **13**，合计 **34 个实例 / 33 个文件** |
| 4. 需人工决策 | **9 项**（见 C2.4），核心是 6 个 Drawer 的内嵌表 `scroll.x` 超过 1120 |
| 5. 规划与代码冲突 | **5 项**（见 C2.5），其中 3 项需回写规划/审计文档 |

## 本轮未做的事

- 未修改任何 Vue / LESS / TS 业务代码；
- 未修改 `theme/scm/scm-drawer.ts`；
- 未新增 `.scm-mono`；
- 未迁移任何 `.num`；
- 未修改任何 Drawer 宽度；
- 未运行构建 / TypeScript / ESLint / Playwright。

## 后续

- **Sprint F-B**：落地 `.scm-mono`，按 C1.4 迁移 55 处，删除 19 处自定义 `.num`；提交信息须说明字体变更。
- **Sprint F-D**：先拍板 C2.4 的 9 项，再落地四级宽度并迁移 34 个 Drawer。
- **文档回写**：按 C2.5 更新规划 §6.2、缺口盘点 §4 F1、收尾审计 §5.A3。
