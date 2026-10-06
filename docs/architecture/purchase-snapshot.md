# 采购域快照矩阵

状态：当前长期规则。源码侧的对应实现是 `com.xsy.scm.purchase.manager.PurchaseSnapshotFactory`。

本文件承载「哪个快照在哪个时点冻结」的完整矩阵。**放在这里而不是类头 Javadoc 里**，是因为它是一张会随业务调整的表：任何一列的冻结时点一变，实现与这张表都要改。写在源码里会让几十行 Javadoc 与实现长期漂移，而读者也无法判断哪一份是权威。

## 1. 四个冻结时点

| 快照 | 落点 | 冻结时点 |
| --- | --- | --- |
| Supplier | `purchase_order` / `purchase_receipt` | 创建 / 编辑时（DRAFT 可刷新）；收货单创建时继承 |
| SKU | `purchase_order_item` / `purchase_receipt_item` / `purchase_demand` | 创建 / 编辑时；收货单创建时继承；需求生成时 |
| Purchase unit | `purchase_order_item.purchase_unit_snapshot` | 创建 / 编辑时，来源 `supplier_sku.purchase_unit` |
| Purchase price | `purchase_order_item.purchase_price` + `line_amount` | 创建 / 编辑时（人工确认值） |

## 2. 两条不变量

- **`submit` 后永不回读主数据**：采购单一旦提交，供应商 / 商品 / 采购单位 / 价格全部以快照为准；主数据后续改名、改价、停用都不影响已提交的单据。
- **收货单行不存价格**：收货只记数量与重量，金额由采购行派生（见 `PurchaseReceiptItemEntity`）。

## 3. 为什么快照要分「头级」与「行级」

- 头级快照（供应商）落在一张单据上，全单共享一个值；
- 行级快照（商品规格、采购单位、采购价）落在明细行上，同一张单的不同行可以取不同的值 —— 这正是「一张采购单可以买多个供应商的商品规格」的表达方式。

采购单位快照取的是 `supplier_sku.purchase_unit`（该供应商对该商品规格的供货单位），**不是** `product_sku.sale_unit`。需求侧的单位快照则取 `sales_order_item.sale_unit_snapshot`（销售单位），两者不是同一个概念，不要互相替换。

## 4. 时区口径

快照里的日期字段统一用 `AT TIME ZONE 'Asia/Shanghai'` 取业务日，与 `ScmDocumentNumbers` 的单号日期段、需求生成的 `demand_date` 共用同一个业务时区，避免跨零点错位。
