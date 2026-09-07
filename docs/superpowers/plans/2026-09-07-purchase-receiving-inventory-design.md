```
# 采购需求、采购订单、收货与库存设计

- 日期：2026-09-07
- 范围：采购需求、采购订单、收货、库存、前端页面与验证
- 状态：已确认，待实施

## 1. 背景与目标

本次开发完善销售订单之后的采购执行链路：

```text
已确认销售订单
  → 采购需求
  → 采购需求汇总与分配
  → 采购订单
  → 收货草稿
  → 分次收货确认
  → 库存余额
  → PURCHASE_IN 库存流水

本次目标：

1. 修复采购需求生成订单时的锁定、唯一键并发竞态和分配插入冲突。
2. 修复采购订单需求分配的聚合增量校验。
3. 编辑采购订单时保留采购行身份，不允许通过保留行替换 demandId。
4. 实现一个采购订单对应一个收货单。
5. 支持同一收货单多次部分确认。
6. 实现严格超收校验。
7. 实现收货确认幂等。
8. 在同一事务内更新采购进度、库存余额和库存流水。
9. 实现追加式 PURCHASE_IN 库存流水。
10. 补齐采购、收货、库存相关前端页面、API、路由和导航。
11. 完成后端聚焦测试、完整测试，以及前端 lint、类型检查、单元测试、构建和 E2E 验证。

2. 已确认的业务决策

2.1 采购订单与收货单关系

一个采购订单只能对应一个收货单。

PurchaseOrder 1 ── 1 PurchaseReceipt

收货单创建后可以保存为草稿，并支持多次确认。

2.2 分次收货

同一收货单支持多次确认。

每次确认：

- 只处理本次填写的数量或重量；
- 已确认数量不可修改；
- 未收数量可以继续填写；
- 本次确认成功后产生一次库存入库；
- 本次确认失败时，所有相关变更回滚；
- 全部采购数量收齐后，收货单变为 CONFIRMED。

收货单状态：

DRAFT
  ↓ 首次部分确认
PARTIALLY_CONFIRMED
  ↓ 全部收齐
CONFIRMED

2.3 采购订单状态

采购订单状态：

DRAFT → SUBMITTED → PARTIALLY_RECEIVED → RECEIVED
   └──────────────→ CANCELLED

规则：

- DRAFT：采购订单可编辑。
- SUBMITTED：已提交执行，可以收货或取消。
- PARTIALLY_RECEIVED：至少确认过一次收货，但仍有未收数量。
- RECEIVED：所有采购行均已收齐，只读。
- CANCELLED：采购订单已取消，只读。
- PARTIALLY_RECEIVED 不允许取消。
- RECEIVED 不允许取消。
- RECEIVED 不允许编辑。
- CANCELLED 不允许编辑或收货。

2.4 收货数量与库存数量

每次收货确认均按增量处理：

本次确认数量
  → 采购订单行累计已收数量增加本次数量
  → 库存余额增加本次数量
  → 追加一条 PURCHASE_IN 流水

禁止根据累计总量重复重算库存。

2.5 幂等规则

收货确认必须携带 Idempotency-Key。

相同作用域、相同幂等键和相同规范化请求哈希：

- 只执行一次；
- 重复请求返回首次成功结果；
- 不重复增加采购订单已收数量；
- 不重复增加库存余额；
- 不重复写入 PURCHASE_IN 流水。

相同作用域和相同幂等键，但请求内容不同：

- 返回 HTTP 409；
- 返回稳定的幂等冲突错误码；
- 不产生任何业务副作用。

收货确认的幂等记录必须与收货、采购、库存变更在同一个数据库事务中提交。

3. 非目标范围

本次不实现：

- 采购退货；
- 销售出库；
- 库存占用与释放；
- 销售退货入库；
- 盘点、报损、报溢、调拨和库存调整；
- 分拣、发货和配送；
- 供应商在线确认；
- 应付账款和支付；
- 批次、保质期和先进先出；
- 真实电子秤接入；
- 自动供应商选择；
- 自动补货预测；
- 正式库存成本会计；
- 登录、RBAC 和数据权限。

本次非标品收货允许人工录入实际重量，称重来源固定为 MANUAL。DEVICE 作为未来扩展值保留。

4. 架构方案

继续使用现有模块化单体：

React/Vite Admin
  → Axios /api
  → Spring MVC Controller
  → Application Service / Query Service
  → MyBatis-Plus Mapper + XML
  → PostgreSQL

领域模块：

supplier
purchase
inventory

采购模块职责：

- 采购需求生成；
- 需求查询；
- 需求分配；
- 采购订单创建、编辑、提交和取消；
- 收货草稿；
- 收货确认；
- 采购操作日志。

库存模块职责：

- 库存余额查询；
- 库存余额事务更新；
- 库存流水追加；
- 库存流水查询。

库存变更只能通过库存应用服务执行，不允许页面或采购服务直接对库存表执行普通 CRUD 更新。

5. 采购需求生成

5.1 来源

采购需求只能由已确认销售订单的活动订单行生成。

不允许以下订单生成采购需求：

- 草稿订单；
- 待确认订单；
- 已取消订单；
- 已删除订单；
- 不存在的订单。

每个活动销售订单行最多对应一条活动采购需求。

5.2 生成幂等

采购需求生成需要防止以下并发情况：

请求 A：从销售订单行生成采购需求
请求 B：同时从相同销售订单行生成采购需求

服务端必须：

1. 锁定来源销售订单行，或使用等价的数据库行锁；
2. 查询活动采购需求；
3. 检查活动唯一索引；
4. 创建采购需求；
5. 遇到唯一键冲突时重新读取并返回已存在记录；
6. 不允许重复创建采购需求；
7. 不允许重复增加需求数量。

业务唯一性：

active sales_order_item_id → one purchase demand

5.3 需求数量

采购需求至少保存：

- 来源销售订单 ID；
- 来源销售订单行 ID；
- SKU ID；
- 商品快照；
- 原始需求数量；
- 已分配数量；
- 已完成收货数量；
- 待分配数量；
- 状态；
- 版本号。

数量关系：

requiredQuantity
  = allocatedQuantity + unallocatedQuantity

采购需求的原始数量不得被采购订单编辑直接覆盖。

6. 采购需求分配

6.1 分配模型

采购订单行与采购需求之间通过 allocation 关联：

PurchaseOrderItem
  ↔ PurchaseDemandAllocation
  ↔ PurchaseDemand
  ↔ SalesOrderItem

一条采购订单行可以分配多条采购需求。

同一采购需求可以拆分到多条采购订单行，但所有分配数量之和不得超过采购需求的可分配数量。

6.2 聚合增量校验

创建或更新分配时，服务端不能只校验单条请求数量，必须聚合计算：

已有活动 allocation 总量
+ 本次请求中同一 demandId 的增量总量
≤ requiredQuantity

更新采购订单草稿时，应排除当前订单原有 allocation，再将新提交的保留 allocation 作为整体重新计算：

其他采购订单活动 allocation 总量
+ 当前采购订单本次 allocation 总量
≤ requiredQuantity

必须防止：

- 同一请求中重复提交相同 demandId 导致绕过校验；
- 两条采购订单行分别分配同一需求后总量超额；
- 并发分配导致总分配量超过需求量；
- 删除旧分配后插入新分配时短暂违反唯一约束；
- 相同 allocation 重复插入。

6.3 分配并发

对于一次涉及多个采购需求的分配请求：

1. 按 demandId 升序锁定所有相关采购需求；
2. 读取所有活动 allocation；
3. 计算本次聚合增量；
4. 校验剩余可分配量；
5. 插入或更新 allocation；
6. 更新采购需求累计已分配量；
7. 在同一个事务中提交。

固定锁定顺序：

purchase_demand 按 ID 升序
  → purchase_order
  → purchase_order_item
  → allocation

如果数据库唯一约束发生冲突，服务端不能吞掉异常继续写入，必须回滚并返回明确的冲突错误。

6.4 重复分配规则

活动 allocation 唯一键：

(purchase_order_item_id, purchase_demand_id)

完全相同的重复分配：

- 返回已有 allocation；
- 不重复增加分配数量；
- 不重复更新采购需求已分配数量。

相同采购订单行和相同 demandId，但数量不同：

- 返回 HTTP 409；
- 返回 allocation 冲突错误；
- 不自动覆盖原分配数量。

7. 采购订单编辑与 retained reconciliation

7.1 允许编辑的状态

只有 DRAFT 采购订单允许编辑。

以下状态不可编辑：

- SUBMITTED；
- PARTIALLY_RECEIVED；
- RECEIVED；
- CANCELLED。

7.2 行身份保留

前端编辑采购订单时，保留的采购订单行必须提交原始行 ID。

服务端必须采用 retained reconciliation：

- 提交且保留的采购订单行保留原 ID；
- 新增采购订单行生成新 ID；
- 被删除且尚未收货的采购订单行执行软删除；
- 已经收货的采购订单行不允许被删除；
- 不允许删除后再使用新行伪装替换原行。

7.3 禁止替换 demandId

对于保留采购订单行：

- 原有 allocation 的 demandId 必须保持不变；
- 不允许把原 allocation 改绑到另一个采购需求；
- 不允许通过修改采购订单行的 demandId 绕过需求来源审计；
- 不允许通过删除并重建行伪造来源关系。

如果需要更换采购需求，应：

1. 删除原 allocation；
2. 释放原采购需求的已分配数量；
3. 在业务允许的情况下新增独立采购订单行；
4. 建立新的 allocation；
5. 保留完整操作日志。

对已提交或已收货采购订单不允许执行上述替换。

8. 收货模型

8.1 一个采购订单一个收货单

每个采购订单最多创建一个活动收货单：

purchase_order_id → one active purchase_receipt

创建收货单不会改变：

- 采购订单状态；
- 采购订单已收数量；
- 库存余额；
- 库存流水。

创建收货单只保存草稿。

8.2 收货单状态

DRAFT
PARTIALLY_CONFIRMED
CONFIRMED

状态规则：

- DRAFT：尚未确认过任何收货。
- PARTIALLY_CONFIRMED：已确认至少一批，但仍存在未收数量。
- CONFIRMED：采购订单所有行均已收齐。
- PARTIALLY_CONFIRMED 状态仍允许继续提交未收数量。
- CONFIRMED 后收货单和收货行全部只读。

8.3 收货确认批次

为了支持同一收货单多次确认，需要保存每次确认的不可变批次记录。

建议增加：

PurchaseReceiptConfirmation

核心字段：

- ID；
- 收货单 ID；
- 幂等作用域；
- 幂等键；
- 请求哈希；
- 确认批次号；
- 确认时间；
- 操作者；
- 本次确认总数量；
- 状态；
- 返回结果快照或关联结果信息；
- 创建时间。

每个收货行的确认批次数量也必须保存，不能只覆盖收货行当前值。

可采用：

PurchaseReceiptConfirmationItem

保存：

- 确认批次 ID；
- 收货行 ID；
- 本次计划数量；
- 本次实际数量或重量；
- 单位；
- 称重来源；
- 称重记录 ID。

8.4 标品收货

标品以 receivedQuantity 作为本次入库数量：

inventoryIncrease = receivedQuantity

原计划采购数量和累计已收数量分开保存。

8.5 非标品收货

非标品保存：

- 计划或申报数量；
- 本次实际重量；
- 称重来源；
- 人工修正原因；
- 称重记录。

本期称重来源仅允许：

MANUAL

库存入库数量使用确认后的实际重量：

inventoryIncrease = actualWeight

实际重量不得覆盖原采购计划数量。

8.6 超收校验

服务端重新计算每个采购订单行的剩余可收数量：

remainingQuantity
  = plannedQuantity - confirmedReceivedQuantity

本次确认必须满足：

本次确认数量 > 0
本次确认数量 ≤ remainingQuantity

非标品使用实际重量进行相同校验。

严格禁止：

- 数量为零；
- 负数；
- 超过剩余数量；
- 收货行不属于当前采购订单；
- 同一请求中重复提交同一收货行；
- 前端提交累计数量绕过服务端计算。

9. 收货确认事务

收货确认必须使用 @Transactional。

事务步骤：

1. 校验 Idempotency-Key；
2. 计算规范化请求哈希；
3. 查询幂等记录；
4. 如果已有成功记录：
  - 哈希相同，返回历史结果；
  - 哈希不同，返回 409；
5. 锁定采购订单；
6. 校验采购订单状态；
7. 锁定采购订单行；
8. 校验收货单归属；
9. 校验收货单版本；
10. 校验收货行版本；
11. 聚合历史确认数量；
12. 校验本次确认数量不超收；
13. 写入确认批次和确认明细；
14. 更新收货单状态；
15. 更新采购订单行累计已收数量；
16. 更新采购订单状态；
17. 锁定或创建库存余额；
18. 记录库存变动前数量；
19. 增加库存余额；
20. 追加 PURCHASE_IN 流水；
21. 写入采购和收货操作日志；
22. 写入幂等完成记录；
23. 提交事务并返回确认后的详情。

固定锁定顺序：

采购订单
  → 采购订单行
  → 库存余额

所有收货事务必须遵守相同顺序，降低死锁概率。

任一步骤失败，都必须回滚：

- 收货单状态；
- 收货确认批次；
- 采购订单行累计已收数量；
- 采购订单状态；
- 库存余额；
- 库存流水；
- 幂等记录；
- 操作日志。

10. 库存模型

10.1 库存余额

本期库存粒度为：

warehouseId + skuId

库存余额至少保存：

- ID；
- 仓库 ID；
- SKU ID；
- 商品快照；
- 当前数量；
- 单位；
- 可选平均成本；
- 版本；
- 创建时间；
- 更新时间；
- 软删除字段。

活动库存余额唯一：

(warehouse_id, sku_id)

10.2 首次创建库存余额

两个并发收货事务可能同时创建相同仓库和 SKU 的库存余额。

必须使用：

- 活动唯一索引；
- 行锁；
- INSERT ... ON CONFLICT 或等价策略；
- 明确的冲突重试或冲突返回。

不能通过捕获异常后继续执行来掩盖库存不一致。

10.3 库存流水

库存流水只追加，不允许修改历史流水。

本次只使用：

PURCHASE_IN

核心字段：

- 流水 ID；
- 流水号；
- 仓库 ID；
- SKU ID；
- 变动类型；
- 来源单据类型；
- 来源单据 ID；
- 来源单据行 ID；
- 变动前数量；
- 变动数量；
- 变动后数量；
- 单位；
- 采购价或成本字段；
- 操作者；
- 发生时间；
- 备注。

每条 PURCHASE_IN 必须满足：

afterQuantity
  = beforeQuantity + movementQuantity

同一确认批次不得产生两条相同来源的入库流水。

建议来源唯一性：

(source_type, source_id, source_item_id, confirmation_id)

11. 数据库迁移

从当前 HEAD 的最新迁移之后新增 Flyway migration，不修改已经应用的 V8、V9 或其他历史迁移。

迁移内容包括：

1. 采购需求活动唯一索引；
2. 需求分配活动唯一索引；
3. 采购订单与收货单活动唯一索引；
4. 收货状态扩展；
5. 收货确认批次表；
6. 收货确认明细表；
7. 库存余额表或现有库存表调整；
8. 库存余额 (warehouse_id, sku_id) 活动唯一索引；
9. 库存流水表或现有流水表调整；
10. 幂等记录唯一索引；
11. 采购订单、采购行、收货和库存查询索引；
12. 必要的数量、重量和状态 CHECK 约束；
13. 单据号和流水号 PostgreSQL sequence。

数据库不创建外键。

关系完整性通过：

- 服务层校验；
- 事务；
- 唯一索引；
- CHECK 约束；
- 查询索引；
- 操作日志；
- 集成测试。

11.1 重要索引

至少包括：

purchase_demand(sales_order_item_id)
purchase_demand(status, sku_id)
purchase_demand_allocation(purchase_order_item_id, purchase_demand_id)
purchase_order(status, supplier_id, warehouse_id)
purchase_order_item(purchase_order_id)
purchase_receipt(purchase_order_id)
purchase_receipt_confirmation(receipt_id, idempotency_key)
inventory(warehouse_id, sku_id)
inventory_movement(warehouse_id, sku_id, occurred_at)
inventory_movement(source_type, source_id, source_item_id)
idempotency_record(scope, idempotency_key)

活动唯一索引必须排除软删除记录。

12. API 设计

所有 API 使用 /api 前缀，响应格式：

{
  "code": 0,
  "message": "success",
  "data": {}
}

错误状态：

- 参数错误：400
- 资源不存在：404
- 版本、唯一性或幂等冲突：409
- 未处理服务器错误：500

12.1 采购需求

GET  /api/purchase-demands
POST /api/purchase-demands/generate
POST /api/purchase-demands/allocate

生成请求：

{
  "salesOrderId": 1001
}

分配请求需要包含：

{
  "demandId": 2001,
  "quantity": "10.0000",
  "supplierId": 3001,
  "purchaserId": 4001,
  "warehouseId": 5001,
  "version": 1
}

12.2 采购订单

GET  /api/purchase-orders
POST /api/purchase-orders
GET  /api/purchase-orders/{id}
PUT  /api/purchase-orders/{id}
POST /api/purchase-orders/{id}/submit
POST /api/purchase-orders/{id}/cancel

创建和提交采购订单必须携带：

Idempotency-Key: <key>

采购订单更新只允许 DRAFT 状态。

客户端不得提交以下权威字段作为写入值：

- 累计已收数量；
- 库存余额；
- 采购订单状态；
- 采购订单总金额。

这些字段由服务端计算。

12.3 收货

GET  /api/purchase-receipts
POST /api/purchase-receipts
GET  /api/purchase-receipts/{id}
PUT  /api/purchase-receipts/{id}
POST /api/purchase-receipts/{id}/confirm

创建收货单时：

- 同一采购订单已有活动收货单时返回已有收货单或明确冲突；
- 不产生库存变更；
- 收货单初始状态为 DRAFT。

确认请求必须携带：

Idempotency-Key: <key>

请求示例：

{
  "version": 2,
  "items": [
    {
      "receiptItemId": 7001,
      "version": 1,
      "receivedQuantity": "5.0000",
      "actualWeight": null,
      "weightSource": null,
      "correctionReason": null
    }
  ]
}

非标品示例：

{
  "version": 2,
  "items": [
    {
      "receiptItemId": 7002,
      "version": 1,
      "receivedQuantity": "5.0000",
      "actualWeight": "4.8600",
      "weightSource": "MANUAL",
      "correctionReason": "去除包装后人工确认"
    }
  ]
}

服务端不信任客户端提交的累计值，重新计算：

- 历史确认数量；
- 本次确认数量；
- 剩余数量；
- 采购订单状态；
- 库存余额；
- 库存流水。

12.4 库存

GET /api/inventories
GET /api/inventory-movements

库存查询支持：

- 仓库；
- SKU；
- 分类；
- 数量范围；
- 更新时间；
- 分页。

库存流水查询支持：

- 仓库；
- SKU；
- 业务类型；
- 来源单据；
- 收货单；
- 日期范围；
- 分页。

本期不提供通用库存调整写接口。

13. 错误码

错误码沿用项目现有 ErrorCode、BusinessException 和统一异常处理机制。

至少需要覆盖：

PURCHASE_SOURCE_ORDER_NOT_CONFIRMED
PURCHASE_DEMAND_ALREADY_EXISTS
PURCHASE_DEMAND_ALLOCATION_EXCEEDED
PURCHASE_DEMAND_ALLOCATION_CONFLICT
PURCHASE_DEMAND_VERSION_CONFLICT
PURCHASE_ORDER_STATUS_NOT_ALLOWED
PURCHASE_ORDER_VERSION_CONFLICT
PURCHASE_ORDER_ITEM_NOT_FOUND
PURCHASE_ORDER_ITEM_DEMAND_REPLACEMENT_NOT_ALLOWED
PURCHASE_RECEIPT_ALREADY_EXISTS
PURCHASE_RECEIPT_STATUS_NOT_ALLOWED
PURCHASE_RECEIPT_ITEM_NOT_FOUND
PURCHASE_RECEIPT_OVER_RECEIVED
PURCHASE_RECEIPT_VERSION_CONFLICT
IDEMPOTENCY_CONFLICT
INVENTORY_CONFLICT
INVENTORY_BALANCE_NOT_FOUND
SUPPLIER_NOT_ENABLED
WAREHOUSE_NOT_ENABLED
SKU_NOT_FOUND

14. 前端页面、路由与导航

继续沿用现有 Admin 主题、顶部 Header、一级侧栏和二级侧栏。

建议路由：

/purchases/demands
/purchases/orders
/purchases/orders/new
/purchases/orders/:id
/purchases/orders/:id/receipts
/purchases/receipts
/warehouses/inventories
/warehouses/inventory-movements

14.1 采购需求页面

展示：

- 来源销售订单；
- 客户；
- SKU；
- 商品名称；
- 需求数量；
- 已分配数量；
- 待分配数量；
- 供应商；
- 仓库；
- 状态；
- 创建时间。

支持：

- 筛选；
- 分页；
- 选择需求；
- 分组预览；
- 分配供应商和仓库；
- 创建采购订单；
- 409 冲突后刷新数据。

14.2 采购订单列表

展示：

- 采购订单号；
- 供应商；
- 仓库；
- 采购员；
- 计划日期；
- 计划数量；
- 已收数量；
- 待收数量；
- 金额；
- 状态；
- 创建时间；
- 操作。

操作：

- 查看详情；
- 编辑草稿；
- 提交；
- 取消；
- 创建或打开收货单。

提交和取消需要二次确认，并防止重复点击。

14.3 采购订单详情页

展示：

- 采购订单头；
- 采购订单状态；
- 供应商；
- 仓库；
- 采购员；
- 计划日期；
- 采购订单行；
- 商品快照；
- 采购数量；
- 已收数量；
- 待收数量；
- 来源采购需求；
- 收货进度；
- 操作日志；
- 库存流水入口。

14.4 收货页面

收货页面使用全页工作流，不使用大型 Drawer。

每行展示：

- SKU；
- 规格；
- 采购数量；
- 历史已收数量；
- 本次可收数量；
- 本次数量；
- 本次实际重量；
- 单位；
- 称重来源；
- 最近称重记录；
- 异常或人工修正原因。

状态行为：

- DRAFT：允许填写并确认；
- PARTIALLY_CONFIRMED：已确认批次只读，剩余数量可继续填写；
- CONFIRMED：全部只读；
- 确认按钮在请求期间禁用；
- 前端重复禁用不替代后端幂等保护。

成功后展示：

- 本次确认数量；
- 累计已收数量；
- 采购订单最新状态；
- 库存余额变化；
- PURCHASE_IN 流水入口。

14.5 库存余额页面

库存余额只读展示：

- 仓库；
- SKU；
- 商品名称；
- 当前数量；
- 单位；
- 更新时间；
- 可选成本信息。

禁止提供直接编辑库存数量按钮。

14.6 库存流水页面

只读展示：

- 流水号；
- 发生时间；
- 仓库；
- SKU；
- 类型；
- 来源收货单；
- 变动前数量；
- 变动数量；
- 变动后数量；
- 单位；
- 操作者。

本期只显示和筛选 PURCHASE_IN。

15. 前端状态与 API 约定

使用现有 Axios API 边界，不在页面直接调用 fetch 或创建新的 HTTP 客户端。

TanStack Query 管理：

- 采购需求列表；
- 采购订单列表；
- 采购订单详情；
- 收货单详情；
- 库存余额；
- 库存流水。

Mutation 成功后精确刷新受影响的查询。

409 错误处理：

1. 显示明确的冲突提示；
2. 不覆盖用户当前编辑内容；
3. 重新获取最新详情；
4. 提示用户比较并重试。

所有网络页面必须处理：

- loading；
- empty；
- error；
- retry；
- success。

金额、数量和重量在前端使用字符串传输，不能使用浮点数作为业务权威值。

16. 测试设计

16.1 后端单元测试

覆盖：

- 已确认销售订单行才能生成采购需求；
- 同一销售订单行重复生成返回同一需求；
- 采购需求聚合增量校验；
- 同一请求重复 demandId 不得绕过数量校验；
- allocation 重复插入；
- allocation 数量冲突；
- retained reconciliation 保留原采购行 ID；
- 保留采购行禁止替换 demandId；
- 采购订单状态矩阵；
- 收货单状态矩阵；
- 分次收货累计数量；
- 非标品计划数量与实际重量分离；
- 严格超收；
- 数量和重量精度；
- 幂等键相同且请求相同；
- 幂等键相同但请求不同；
- 采购订单部分收货和全部收货状态；
- PURCHASE_IN 数量计算；
- 库存流水前后数量计算。

16.2 PostgreSQL 集成测试

覆盖：

- Flyway 迁移成功；
- 活动采购需求来源唯一性；
- allocation 活动唯一性；
- 采购订单一个活动收货单；
- 收货单多次确认；
- 同一收货重复确认不重复入库；
- 收货确认事务回滚；
- 采购订单累计已收数量与收货批次一致；
- 库存余额与流水累计一致；
- 首次并发创建库存余额；
- 并发生成同一采购需求；
- 并发分配同一需求；
- 并发确认同一收货单；
- 并发收取同一采购订单剩余数量；
- 单据号和流水号不重复；
- 幂等记录和库存事务原子提交。

16.3 前端测试

覆盖：

- 采购需求筛选；
- 采购需求分组；
- 分配请求聚合；
- 采购订单表单；
- 保留采购行 ID；
- 禁止替换 demandId；
- 收货剩余量计算；
- 分次收货；
- 部分确认后继续编辑；
- 全部确认后只读；
- 十进制字符串传输；
- 非标品人工实际重量；
- 人工修正原因；
- 确认按钮重复提交保护；
- loading、empty、error、retry、success；
- 409 冲突刷新。

16.4 E2E 流程

至少验证：

1. 使用已确认销售订单生成采购需求；
2. 验证采购需求来源和待分配数量；
3. 将需求分配到供应商、仓库并创建采购订单；
4. 提交采购订单；
5. 创建收货单；
6. 第一次部分确认收货；
7. 验证收货单变为 PARTIALLY_CONFIRMED；
8. 验证采购订单变为 PARTIALLY_RECEIVED；
9. 验证库存余额增加；
10. 验证产生一条 PURCHASE_IN 流水；
11. 第二次确认剩余数量；
12. 验证收货单变为 CONFIRMED；
13. 验证采购订单变为 RECEIVED；
14. 验证库存余额累计正确；
15. 验证产生第二条 PURCHASE_IN 流水；
16. 重复提交第一次确认请求；
17. 验证库存、采购数量和流水数量不重复；
18. 提交超过剩余数量的收货；
19. 验证超收被拒绝且数据不变；
20. 模拟版本冲突；
21. 验证页面提示并刷新最新数据。

17. 验证命令

后端：

cd xsy-scm-server

mvn.cmd -Dtest=PurchaseDemandServiceTest,PurchaseOrderServiceTest test
mvn.cmd -Dtest=PurchasePersistenceIT test
mvn.cmd test
mvn.cmd clean verify

前端：

cd xsy-scm-web

npm run lint
npm run typecheck
npm test
npm run build

E2E：

npm run e2e -- purchase-flow.spec.ts
npm run e2e -- receiving-inventory.spec.ts

如果仓库中已有不同的测试文件名，应以实际测试脚本为准，不应为了匹配本文档而重命名无关测试。

18. 风险与兼容策略

18.1 现有设计与本次决策差异

如果现有代码或历史设计支持“一张采购订单对应多张收货单”，本次实现必须以当前已确认规则为准：

一个采购订单只能有一个收货单
一个收货单支持多次确认

迁移应通过活动唯一索引或服务层校验阻止新增第二张活动收货单。

已有历史数据不得被静默删除或重建。若历史数据不符合新约束，应在迁移前通过测试数据检查并制定兼容处理。

18.2 并发冲突

不能仅依赖前端禁用按钮。

必须同时使用：

- 数据库唯一索引；
- 乐观锁；
- 行锁；
- 幂等记录；
- 事务；
- 服务端重新聚合校验。

18.3 库存一致性

不得先提交收货，再异步写库存。

收货确认、采购进度、库存余额、库存流水和幂等结果必须在同一事务内提交。

18.4 数据精度

Java 使用 BigDecimal。

前端使用十进制字符串。

数据库使用适当的 NUMERIC 类型。

禁止使用浮点数表示数量、重量、价格或金额。

19. 完成标准

本设计对应的实现只有在以下条件均满足后才算完成：

- 采购需求生成并发安全；
- 采购需求分配使用聚合增量校验；
- 保留采购行不允许替换 demandId；
- 一个采购订单只能创建一个收货单；
- 收货单支持多次确认；
- 收货单状态正确流转；
- 采购订单状态正确流转；
- 严格阻止超收；
- 收货确认具备幂等性；
- 收货与库存事务原子；
- 库存余额与流水一致；
- PURCHASE_IN 流水追加且不可变；
- 前端页面、API、路由和导航完整；
- 后端聚焦测试通过；
- 后端完整测试通过；
- 前端 lint 通过；
- 前端 typecheck 通过；
- 前端 test 通过；
- 前端 build 通过；
- 关键 E2E 通过；
- 代码未覆盖或破坏用户已有未提交修改；
- 变更后的 Git diff 已检查；
- 未验证的项目已明确记录。
```