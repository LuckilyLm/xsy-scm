# 财务与报表（ADM-01 / Finance R2）缺口盘点

盘点日期：2026-10-03。
状态：盘点完成，实施按切片推进；本文只记录**代码可证的现状与缺口**，不构成排期承诺。
依据：[产品功能需求基线](../../requirements/产品功能需求基线.md)、[有效决策](../../decisions.md)、[开发规划](admin-development-roadmap.md)、[Finance R1 正式设计](finance-r1-design.md)及当前主线代码。

## 1. 盘点口径

- 只做**只读静态盘点**（读代码与 migration），未运行构建、测试或迁移，未访问数据库。
- 需求项来自基线「财务与报表」一节，逐项给出**现有交付证据**（端点 / 页面 / 权限码）与结论。
- 「已交付」指当前主线存在可闭环的页面 + 接口 + 权限；不代表已通过运行验收。
- 报表**不新建第二套账**：金额只取已成立事实。收入类只用确认订单的 `settlement_*`，不把订单金额当实收；应付只用应付事实，不用采购单金额替代。
- 权限沿用报表中心三层结构：`scm:report:<page>:query` 查询、`scm:report:cost:query` 成本字段、`scm:report:export` 导出（导出必须 AND 上对应查询权限）。成本类整页视图（库存价值、利润）在接口层就要成本权限。

## 2. 逐项对照

### 2.1 报表中心（`/scm/report`，`ScmReportController`）

| 需求项 | 现有交付证据 | 结论 |
| --- | --- | --- |
| 经营数据报表 | `POST /scm/report/overview`、`/overview/trend`、`/overview/daily`；页面 `/report/report-overview-list`；权限 `scm:report:overview:query` | **已交付**；缺口见 G3 |
| 销售明细报表 | `POST /sales/product`、`/sales/category`、`/sales/customer`、`/sales/seller`、`/sales/item/query` 与三个 TOP 端点；页面 `/report/report-sales-list`（五个 Tab）；导出 `/sales/product/export`、`/sales/customer/export`、`/sales/item/export`；权限 `scm:report:sales:query` | **已交付**；缺口见 G1、G2 |
| 客户订单明细报表 | `/sales/customer`（粒度=客户：订单笔数、SKU 种类数、确认金额、已完成退款、最近确认时间）+ `/sales/item/query`（粒度=`sales_order_item`，含客户/销售员列与 `customerId` 筛选） | **行级已交付**；无订单表头级明细，见 G5 |
| 销售员业绩报表 | `/sales/seller`（粒度=销售员；`seller_id` 空归「未分配销售员」）；页面销售分析「按销售员」Tab | **已交付**；缺口见 G2 |
| 采购明细报表 | `/purchase/item/query` + `/purchase/item/export`（粒度=`purchase_order_item`）；另有 `/purchase/overview`、`/purchase/product`、`/purchase/supplier`、`/purchase/purchaser`、`/purchase/price-trend`、`/purchase/supplier/top`；权限 `scm:report:purchase:query` | **已交付**；导出齐全 |
| 单品利润 | `/scm/report/finance/profit/{query,summary,export}`，`ScmFinanceProfitDimensionEnum` 含 `PRODUCT`；权限 `FINANCE_PROFIT_QUERY` **AND** `COST_QUERY` | **已交付**（代码） |
| 客户利润 | 同上，`CUSTOMER` 维度 | **已交付**（代码） |

### 2.2 财务域读路径（`/scm/finance`，`FinanceReadController`）

| 需求项 | 现有交付证据 | 结论 |
| --- | --- | --- |
| 已收款报表及导出 | `POST /scm/finance/receipt/query`、`GET /receipt/{id}`、`POST /receipt/export`；页面 `finance-receipt-list`；权限 `FinancePermission.RECEIPT_QUERY`（导出再 AND `EXPORT`） | **已交付** |
| 待收款报表及导出 | `/scm/finance/receivable/query` + `/receivable/export`；报表中心 `/scm/report/finance/receivable/aging-free-detail` + 导出；账龄 `/scm/report/finance/aging/{query,summary,export}`（页面 `/report/report-finance-aging`） | **已交付** |
| 应付款报表及导出 | `/scm/finance/payable/query` + `/payable/export`；`/scm/report/finance/payable/aging-free-detail` + 导出；付款 `/scm/finance/payment/query` + `/payment/export` | **已交付** |

### 2.3 其他已交付的报表切片（需求基线未单列，避免重复建设）

- 往来概览：`/scm/report/finance/overview` + 导出；页面 `/report/report-finance-overview`。
- 客户对账：`/scm/report/customer/statement/{freeze,history,{id},{id}/export}`；页面 `/report/report-customer-statement`。
- 供应商对账：`/scm/report/supplier/statement/{freeze,history,{id},{id}/export}`；页面 `/report/report-supplier-statement`。
- 异常订单分析：`/scm/report/order-exceptions/*`（V87）；页面 `/report/report-order-exceptions`。
- 采购商品每日清单：`PurchaseDailyReportController` + `PurchaseDailyReportJob`（V74）；页面 `purchase-daily-report.vue`。
- 收货 / 入库 / 待入库、库存流水 / 损耗 / 库存价值 / 收发存：`ScmReportController` 对应端点，多数带导出。

## 3. 真实缺口

### 3.1 建议本轮实施（口径清晰、与同类维度同构）

| 编号 | 缺口 | 现状证据 | 补齐方式 |
| --- | --- | --- | --- |
| G1 | 销售分析「按分类」无导出 | 前端 `reportSalesApi` 只有 `productExport` / `customerExport` / `itemExport`；后端无 `/sales/category/export` | 新增 `/sales/category/export`，复用 `salesReportService.byCategory`，权限 `SALES_QUERY` AND `EXPORT`；前端按分类 Tab 补导出按钮 |
| G2 | 销售分析「按销售员」无导出 | 同上，后端无 `/sales/seller/export` | 新增 `/sales/seller/export`，复用 `bySeller`；前端按销售员 Tab 补导出按钮。**不含成本字段**，故只 AND `EXPORT` |
| G3 | 经营概览无导出 | 经营概览页无导出按钮；后端无 `/overview/daily/export` | 新增 `/overview/daily/export`，复用 `overviewReportService.dailyStat`（日期轴已补齐，导出不会跳天）；权限 `OVERVIEW_QUERY` AND `EXPORT` |

三个缺口共用报表中心既有的 `exportRows`（第 1 页 + 上限探测行，超限抛 41112）与 `ScmReportExcel`，导出与列表**同一查询方法**，口径不可能分叉。

### 3.2 待口径确认后再决定（本轮不实施）

| 编号 | 事项 | 说明 |
| --- | --- | --- |
| G4 | 采购概览 / 按采购员 / 价格波动无导出 | 需求基线只要求「采购明细报表」，这三项是超出需求的增量视图；是否补导出待确认，避免无需求驱动的建设 |
| G5 | 无订单表头级「客户订单明细」 | 现有行级明细能回答「某客户买了什么」，客户聚合能回答「某客户合计多少」；表头级（某客户有几单、每单多少、状态如何）目前只能去订单运营列表看。是否要按报表口径再出一张，需负责人确认 |

### 3.3 已知阻塞与依赖（非缺口）

- **优惠净收入与退款反向分摊**：`settlement_*` 目前不含营销优惠。营销接入正式订单（开发规划第 3 阶段）完成后，本域需同步净收入口径与退款反向分摊，并做财务勾稽。现在不预先改口径。
- **成本完整性**：`ReportDailyStatVO.purchaseInCostMissingCount` 已在每日统计中显式暴露「当日无 `unit_cost` 的采购入库行数」，成本不完整时不被静默当成零成本。
- **利润跨期与历史成本**：`/finance/profit` 依赖成本权限，跨期与历史成本场景仍待用户验收（见开发规划第 7 节 ADM-01 行）。

## 4. 实施切片

1. **切片 1（本轮）**：G1 + G2 + G3 三个导出端点与对应前端按钮。三者同构，一次提交。
2. 后续切片按用户确认的 G4 / G5 结论推进；每片单独提交并更新[开发规划](admin-development-roadmap.md)与[项目状态](../../status.md)。

## 5. 不重复建设

- 不重建已交付的经营概览、销售分析五维度、采购分析、收货入库、库存分析、往来概览、账龄、利润、客户/供应商对账、异常订单与采购每日清单。
- 不把订单金额当实收、不把采购单金额当应付、不用收货参考金额代替入库成本。
- 报表导出与列表同方法、同权限、同数据范围；`scm:report:export` 只代表允许导出，不扩大可见范围。
