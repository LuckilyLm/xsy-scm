# 财务与报表（ADM-01 / Finance R2）缺口盘点

盘点日期：2026-10-03。
状态：盘点完成，缺口已全部实施（切片 1 + 切片 2）；本文只记录**代码可证的现状与缺口**，不代表已通过运行验收。
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
| 经营数据报表 | `POST /scm/report/overview`、`/overview/trend`、`/overview/daily`（切片 1 补 `/overview/daily/export`）；页面 `/report/report-overview-list`；权限 `scm:report:overview:query` | **已交付**（含导出） |
| 销售明细报表 | `POST /sales/product`、`/sales/category`、`/sales/customer`、`/sales/seller`、`/sales/item/query`、`/sales/order/query` 与三个 TOP 端点；页面 `/report/report-sales-list`（六个 Tab）；导出 product / category / customer / seller / item / order；权限 `scm:report:sales:query` | **已交付**（含导出） |
| 客户订单明细报表 | `/sales/customer`（粒度=客户：订单笔数、SKU 种类数、确认金额、已完成退款、最近确认时间）+ `/sales/item/query`（粒度=`sales_order_item`，含客户/销售员列与 `customerId` 筛选）；切片 2 新增 `/sales/order/query`（粒度=`sales_order`，订单表头级，含订单行数与 SKU 种类数） | **已交付**（行级 + 订单表头级） |
| 销售员业绩报表 | `/sales/seller`（粒度=销售员；`seller_id` 空归「未分配销售员」）+ 切片 1 的 `/sales/seller/export`；页面销售分析「按销售员」Tab | **已交付**（含导出） |
| 采购明细报表 | `/purchase/item/query` + `/purchase/item/export`（粒度=`purchase_order_item`）；另有 `/purchase/overview`、`/purchase/product`、`/purchase/supplier`、`/purchase/purchaser`、`/purchase/price-trend`、`/purchase/supplier/top`，切片 2 补齐 overview / purchaser / price-trend 三个导出；权限 `scm:report:purchase:query` | **已交付**（导出齐全） |
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

### 3.1 已实施（切片 1：三个导出）

| 编号 | 缺口 | 现状证据 | 补齐方式 |
| --- | --- | --- | --- |
| G1 | 销售分析「按分类」无导出 | 前端 `reportSalesApi` 只有 `productExport` / `customerExport` / `itemExport`；后端无 `/sales/category/export` | 新增 `/sales/category/export`，复用 `salesReportService.byCategory`，权限 `SALES_QUERY` AND `EXPORT`；前端按分类 Tab 补导出按钮 |
| G2 | 销售分析「按销售员」无导出 | 同上，后端无 `/sales/seller/export` | 新增 `/sales/seller/export`，复用 `bySeller`；前端按销售员 Tab 补导出按钮。**不含成本字段**，故只 AND `EXPORT` |
| G3 | 经营概览无导出 | 经营概览页无导出按钮；后端无 `/overview/daily/export` | 新增 `/overview/daily/export`，复用 `overviewReportService.dailyStat`（日期轴已补齐，导出不会跳天）；权限 `OVERVIEW_QUERY` AND `EXPORT` |

三个缺口共用报表中心既有的 `exportRows`（第 1 页 + 上限探测行，超限抛 41112）与 `ScmReportExcel`，导出与列表**同一查询方法**，口径不可能分叉。

### 3.2 已实施（切片 2：采购导出与订单表头级明细，2026-10-03 负责人确认）

| 编号 | 事项 | 处理 |
| --- | --- | --- |
| G4 | 采购概览 / 按采购员 / 价格波动无导出 | 已补 `/purchase/overview/export`（单行指标卡）、`/purchase/purchaser/export`、`/purchase/price-trend/export`，分别复用 `overview` / `byPurchaser` / `priceTrend`；权限 `PURCHASE_QUERY` AND `EXPORT`，成本字段沿用 Service 的字段级抹除 |
| G5 | 无订单表头级「客户订单明细」 | 已补 `/sales/order/query` + `/sales/order/export`（`SalesReportVO.OrderRow`），页面销售分析新增「客户订单明细」Tab。一行 = 一个订单，含订单行数、SKU 种类数、结算金额与已完成退款金额；退款按 `order_id` 独立聚合，不经订单行 JOIN |

**G5 的筛选语义（刻意与行级不同）**：`categoryId` / `keyword` 是**行级**条件，在订单维度下用 `EXISTS` 只判定「该订单是否命中」，命中后金额仍按**整单**汇总。若直接套用行级 `salesFilters`，订单金额会变成「只匹配那几行」的合计——一单买 10 个菜、只筛蔬菜时金额凭空变小，而页面看不出来。

### 3.3 已知阻塞与依赖（非缺口）

- **优惠净收入与退款反向分摊（已完成）**：应收改为**净额**（行毛额 − 该行优惠分摊），红字按同一份冻结分摊反向（V93）。销售毛利报表直接取 `finance_receivable_item.amount` 作为收入，因此净收入与退款反向自动同步，报表 SQL 不需要改。见[营销接入正式订单计划](promotion-order-integration-plan.md) 3-4。
- **成本完整性**：`ReportDailyStatVO.purchaseInCostMissingCount` 已在每日统计中显式暴露「当日无 `unit_cost` 的采购入库行数」，成本不完整时不被静默当成零成本。
- **利润跨期与历史成本**：`/finance/profit` 依赖成本权限，跨期与历史成本场景仍待用户验收（见开发规划第 7 节 ADM-01 行）。

## 4. 实施切片

1. **切片 1（已完成）**：G1 + G2 + G3 三个导出端点与对应前端按钮。
2. **切片 2（已完成）**：G4 采购三个导出 + G5 订单表头级客户订单明细（后端查询 / 导出、前端 Tab 与按钮、表格 id 与列配置注册）。
3. 后续按营销接入订单的进度同步净收入与退款反向分摊；每片单独提交并更新[开发规划](admin-development-roadmap.md)与[项目状态](../../status.md)。

## 5. 不重复建设

- 不重建已交付的经营概览、销售分析五维度、采购分析、收货入库、库存分析、往来概览、账龄、利润、客户/供应商对账、异常订单与采购每日清单。
- 不把订单金额当实收、不把采购单金额当应付、不用收货参考金额代替入库成本。
- 报表导出与列表同方法、同权限、同数据范围；`scm:report:export` 只代表允许导出，不扩大可见范围。
