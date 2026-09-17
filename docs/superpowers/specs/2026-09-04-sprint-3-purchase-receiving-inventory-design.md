# Sprint 3 采购、收货与库存详细设计

> 历史参考：本文保留原始业务/设计语义。技术栈、实施状态和进度均为 legacy 快照；当前实现以 V2 对应波次的 target-design、approval 与验收报告为准。已删除的旧计划及验证记录可从 Git 历史恢复。

> 负责人确认口径（2026-09-09）覆盖本文件旧的待确认项：采购单由人工按固定时间段统计订单生成；多次收货采用一单多收货记录并保留少收/多收差异；收货入库支持直接入库和二次入库确认两种明确模式。超收上限、“计算库存”扣减时点和入库状态机仍待专项确认，详见 [负责人确认口径](../../requirements/2026-09-09-负责人确认口径.md)。

## 1. 背景与目标

Sprint 1 已完成商品分类、SPU/SKU 档案，Sprint 2 已完成客户可见性、协议价、销售订单、实重结算、退货和退款。采购、供应商、收货、仓库及库存流水仍未实现。本 Sprint 将销售订单闭环向供应链执行侧延伸，形成第一条可审计的采购入库链路：

```text
已确认销售订单
  → 采购需求
  → 需求汇总与人工分配
  → 采购单
  → 一次或多次收货
  → 实收/实重确认
  → 库存入库
  → 库存余额与流水
```

本设计基于 `AGENTS.md` 的采购与库存不变量以及项目设计草案 Phase 3。项目草案仍是路线图而不是冻结合同；本文第 12 节记录本期已经冻结或明确延期的业务决策。

本 Sprint 的验收目标是：

```text
销售订单 → 采购单 → 收货 → 库存
```

在 PostgreSQL、Spring Boot REST API 和 React Admin 中端到端跑通，并确保收货与库存变更可追溯、可重放而不重复入库。

## 2. 范围

### 2.1 本期包含

- 供应商基础资料及启停。
- SKU-供应商采购配置，至少支持有效供应商和人工指定供应商。
- 仓库基础资料及启停。
- 从已确认销售订单行生成采购需求，保留来源关系。
- 采购需求按已确认分组维度汇总并生成采购单。
- 采购单草稿、提交、取消和查询。
- 一张采购单对应一张或多张收货单。
- 标品按数量收货，非标品支持人工录入实际重量。
- 收货确认后在同一事务内更新采购进度、库存余额和库存流水。
- 库存余额、采购入库流水和必要的采购/收货操作日志查询。
- 关键创建、提交、取消、确认命令的幂等与乐观锁。
- 必要的 Admin 列表、详情、收货和库存查询页面。

### 2.2 明确不包含

本期不实现以下能力，除非单独补充并批准变更：

- 销售出库、库存占用与释放。
- 采购退货、销售退货入库。
- 盘点、报损、报溢、调拨和库存调整页面。
- 分拣、发货、配送及销售订单实重回写。
- 供应商门户、在线确认、应付账款、付款和对账。
- 完整批次追溯、保质期、先进先出和一物一码。
- 真实电子秤或其他硬件协议接入。
- 自动补货预测和智能采购建议。
- 未经财务确认的完整库存成本会计。
- 登录、RBAC、数据权限和动态菜单；操作者沿用 `SYSTEM`。

## 3. 设计原则与已冻结不变量

### 3.1 架构

继续采用现有模块化单体：

```text
React/Vite Admin
  → Axios /api
  → Spring MVC Controller
  → Application/Query Service
  → MyBatis-Plus Mapper + XML
  → PostgreSQL
```

后端领域包使用 `supplier`、`purchase`、`inventory`，不建立全局 controller/service/entity 包树。写操作由应用服务负责，查询由查询服务负责；库存变更只能经库存应用服务执行。

### 3.2 SKU 是交易单位

采购、收货和库存均引用 `sku_id`，不以 `spu_id` 作为库存或采购交易单位。采购单、收货单和库存流水必须保存以下历史快照，以避免商品主数据修改影响历史单据读取：

- SKU 编码和名称；
- SPU 编码和名称；
- 规格 JSON；
- 销售/采购单位；
- 商品类型（标品或非标品）；
- 必要时的分类快照。

### 3.3 数量、重量和金额

- API 中的数量、重量、价格和金额均使用十进制字符串。
- Java 使用 `BigDecimal`，统一按领域定义的精度和 `RoundingMode.HALF_UP` 计算。
- PostgreSQL 使用 `NUMERIC`，金额沿用 `NUMERIC(18,4)`；重量精度必须足以表达现场称重分度，最终 scale 在设备/业务确认后冻结。
- 客户端不得提交库存余额、累计收货量、采购单累计金额作为权威数据；服务端重新计算。
- 采购数量、已收数量和实际重量分开保存，实际重量永远不覆盖计划采购数量。

### 3.4 软删除、版本和关系

业务表沿用现有约定：`BIGINT identity`、`version`、`deleted`、创建/更新审计字段。数据库不建立外键；服务事务负责验证供应商、SKU、订单、仓库和单据行关系，并通过索引保证查询性能。活动数据的唯一性使用部分唯一索引，已删除记录不阻塞编码复用。

### 3.5 库存不可绕过流水

每一次库存变化必须在同一事务中产生不可变或只追加的 `InventoryMovement`。本期采购收货只产生 `PURCHASE_IN`，不允许通过普通 CRUD 直接更新库存数量。库存余额与流水写入必须原子完成：任何一步失败，收货状态、采购进度、库存余额、库存流水和日志全部回滚。

## 4. 业务流程

### 4.1 采购需求来源

采购需求由已确认销售订单的活动订单行生成。每条需求保留 `sales_order_id` 和 `sales_order_item_id`，并记录原订单数量、已生成采购数量和待采购数量。生成操作必须幂等：同一订单行的同一数量不能被重复计入；发生并发生成时，服务端锁定来源行或使用唯一业务键重新校验。

本期由用户选择固定时间段并通过显式命令从已确认销售订单人工生成需求，不从草稿或待审核订单自动生成。生成前可选择是否计算实时库存；该选项的扣减时点和并发锁定口径由专项设计确认，服务端不得静默扣减。每个活动销售订单行只允许存在一条采购需求；完全相同的重复生成返回已有结果，不同幂等键并发生成时也必须依靠来源行锁和活动唯一索引收敛为同一结果。订单确认后发生退货或其他变化时，本期不自动冲销采购需求，留待后续受控命令处理。

### 4.2 汇总与分配

采购需求首先形成可审阅的需求集合，再按确认的分组维度汇总为采购单行。汇总必须保留需求分配明细：

```text
PurchaseOrderItem
  ↔ PurchaseDemandAllocation
  ↔ PurchaseDemand
  ↔ SalesOrderItem
```

采购单行的 SKU、数量、单位和商品快照由服务端生成。前端可以提出供应商、采购员和仓库分配，但最终关联和数量均由服务端校验。

本期不实现自动供应商选择或复杂拆单。创建采购单时必须人工明确供应商和仓库，采购员为可选负责人字段；同一采购单的所有行必须匹配采购单头的供应商和仓库。系统允许一条采购单行分配多条需求，也允许同一需求拆分到多条采购单行，但不得超过剩余可分配量。

活动 allocation 以 `(purchase_order_item_id, purchase_demand_id)` 唯一。完全相同的重复分配返回已有 allocation 且不得再次增加 `allocatedQuantity`；相同组合但数量不同视为冲突并返回 409。多个采购单行引用同一需求时，服务端先按固定顺序锁定涉及的需求，再统一计算本次增量和剩余量。

### 4.3 采购单执行

本期冻结的 MVP 采购单流程为：

```text
DRAFT → SUBMITTED → PARTIALLY_RECEIVED → RECEIVED
   └──────────────→ CANCELLED
```

状态含义：

- `DRAFT`：可编辑采购单头和采购单行，可删除或重新分配未提交需求。
- `SUBMITTED`：已提交执行，只允许收货或取消。
- `PARTIALLY_RECEIVED`：至少有一次确认收货，但仍有可收数量。
- `RECEIVED`：按确认的完成规则达到完成条件，只读。
- `CANCELLED`：终止状态，只读并保留取消原因。

状态只能通过显式命令端点转换，不提供通用状态更新接口。仅 `DRAFT`、`SUBMITTED` 可以取消；`PARTIALLY_RECEIVED` 不允许取消。本期不增加供应商确认、采购审核、少收关单或反收货状态。

草稿编辑采用 retained reconciliation：保留行必须保留原行 ID、SKU 和需求来源；不得把已有 allocation 改绑到其他 SKU 或需求。省略且尚未收货的草稿行会软删除，其 allocation 同事务软删除并释放需求已分配量；已有收货、已提交或终态采购单不得编辑。

### 4.4 多次收货

一张采购单允许多张收货单；每次到货新增一条收货记录，不修改并覆盖历史收货记录。收货单创建时只保存草稿，不改变库存；收货确认或后续入库确认才产生库存副作用，具体由入库模式决定。

每个收货行必须属于当前采购单的采购单行，且本次收货数量大于零。服务端根据采购单行计划数量、历史确认收货数量和本次数量记录累计差异；少收允许继续收货，多收必须记录异常并按后续确认的容差/审批规则处理，不能静默覆盖历史数量。

标品以确认的 `receivedQuantity` 作为采购累计实收量和库存入库量。非标品同时保存计划/申报数量与 `actualWeight`，并以确认后的 `actualWeight` 作为采购累计实收量和库存入库量；原计划数量不得被覆盖。非标品本期只允许 `MANUAL` 称重来源，并追加独立称重记录。

推荐的收货单流程为：

```text
DRAFT → CONFIRMED
```

确认后单条收货记录、收货行和称重记录只读；本期不实现反收货。少收允许后续继续收货，多收保留异常记录并等待容差/审批规则，不得静默抹平差异。累计实收量达到采购计划量时采购单可转为 `RECEIVED`，大于零但尚未达到计划量时转为 `PARTIALLY_RECEIVED`。

### 4.5 入库

确认收货事务按以下逻辑执行：

1. 校验幂等键、收货单版本和采购单状态；
2. 锁定采购单、相关采购单行和库存余额记录；
3. 校验 SKU、仓库、收货数量、重复行和剩余量；
4. 将收货单置为 `CONFIRMED`，写入确认时间和操作者；
5. 更新采购单行累计已收数量及采购单状态；
6. 更新或创建 `Inventory` 余额；
7. 追加 `InventoryMovement`，类型为 `PURCHASE_IN`；
8. 写入采购操作日志和收货/库存关联信息；
9. 完成幂等记录并返回确认后的详情。

锁定顺序固定为采购单 → 采购单行 → 库存余额，所有收货事务保持一致顺序，以减少死锁。任何失败必须回滚全部步骤。

## 5. 领域模型

### 5.1 Supplier

核心字段：

- `id`, `supplierCode`, `name`;
- `enabled`, `deleted`, `version`;
- `remark`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`。

活动供应商编码唯一。停用供应商不能被新采购单使用，但历史单据仍可读取。

### 5.2 SupplierSku

表示 SKU 可由某供应商采购的配置关系。核心字段：

- `supplierId`, `skuId`;
- `isDefault`, `enabled`;
- 可选参考采购价和负责人字段；
- SKU/供应商快照、版本和审计字段。

本期不自动选择默认供应商；默认标记仅作为配置数据保留，采购单仍要求人工明确供应商。历史采购单不依赖该关系实时读取。

### 5.3 Warehouse

核心字段：

- `warehouseCode`, `name`, `enabled`;
- 地址/备注；
- `deleted`, `version` 和审计字段。

停用仓库不能用于新采购单或新收货，但不影响历史库存和流水查询。

### 5.4 PurchaseDemand

核心字段：

- 来源 `salesOrderId`, `salesOrderItemId`；
- `skuId` 及商品快照；
- `requiredQuantity`, `allocatedQuantity`, `fulfilledQuantity`；
- `supplierId`, `purchaserId`, `warehouseId`（分配后）；
- 状态、需求日期、版本和审计字段。

数量关系：

```text
requiredQuantity = allocatedQuantity + unallocatedQuantity
allocatedQuantity = purchase order allocations total
```

不得因采购单重建而丢失来源行身份。

### 5.5 PurchaseOrder / PurchaseOrderItem

采购单头保存单号、供应商、采购员、仓库、计划日期、状态、备注、版本和审计字段。采购单行保存 SKU/商品快照、计划采购数量、累计已收数量、采购单位、采购单价、金额和版本。

服务端按：

```text
lineAmount = round(plannedQuantity × purchasePrice, 4, HALF_UP)
totalAmount = sum(lineAmount)
```

重新计算金额。若本期采购价口径尚未由财务确认，则只把单价和金额作为业务记录，不宣称已完成正式成本核算。

### 5.6 PurchaseDemandAllocation

保存采购单行与采购需求之间的数量分配，是采购来源追溯的必要实体。建议保存分配时的销售订单、销售订单行、SKU 和需求快照，支持一条采购单行对应多条需求。

### 5.7 PurchaseReceipt / PurchaseReceiptItem

收货单头保存收货单号、采购单、仓库、状态、收货时间、确认时间、操作者、备注和版本。收货行保存采购单行、SKU/商品快照、本次计划单位数量、本次实际数量/重量、单位、称重来源和版本。

称重记录至少保存：

- 记录 ID、收货行 ID；
- 原始读数、最终确认读数、单位和精度；
- 来源 `MANUAL`（本期）或未来 `DEVICE`；
- 记录时间、操作者、修改原因；
- 可选设备会话标识。

### 5.8 Inventory / InventoryMovement

MVP 库存余额固定按 `warehouseId + skuId` 维护，本期不启用批次维度。库存余额保存数量、版本、可选平均成本和审计字段；本期不实现负库存写入或正式库存成本核算。

库存流水为只追加实体，至少保存：

- 流水号和发生时间；
- 仓库、SKU、批次（如启用）；
- `movementType = PURCHASE_IN`；
- 来源单据类型、来源单据 ID 和来源行 ID；
- 变动前数量、变动数量、变动后数量；
- 单位、采购价/成本字段；
- 操作者和备注。

预留未来类型：`SALES_OUT`、`RETURN_IN`、`PURCHASE_RETURN_OUT`、`STOCKTAKE_IN`、`STOCKTAKE_OUT`、`LOSS`、`GAIN`、`TRANSFER_IN`、`TRANSFER_OUT`、`ADJUSTMENT`。本期不实现这些类型的命令。

## 6. API 合同

所有接口使用 `/api` 前缀和 `{code,message,data}` 响应，分页沿用 `{records,page,pageSize,total}`。请求中的 `version` 用于乐观锁；不存在返回 404，参数/校验错误返回 400，唯一性、版本和幂等冲突返回 409。

### 6.1 基础资料

```http
GET  /api/suppliers
POST /api/suppliers
PUT  /api/suppliers/{id}
POST /api/suppliers/{id}/status

GET  /api/warehouses
POST /api/warehouses
PUT  /api/warehouses/{id}
POST /api/warehouses/{id}/status

GET  /api/suppliers/{id}/skus
PUT  /api/suppliers/{id}/skus
```

供应商和仓库保存、状态变更的请求必须校验编码、启停状态和版本；状态命令是否要求幂等键按现有通用规则执行。

### 6.2 采购需求

```http
GET  /api/purchase-demands
POST /api/purchase-demands/generate
POST /api/purchase-demands/allocate
```

`generate` 请求包含已确认销售订单 ID 或订单范围，服务端只接受符合来源条件的订单。`allocate` 请求携带需求 ID、数量、供应商、采购员、仓库和 `version`，服务端拒绝跨来源、超需求和过期版本。

### 6.3 采购单

```http
GET  /api/purchase-orders
POST /api/purchase-orders
GET  /api/purchase-orders/{id}
PUT  /api/purchase-orders/{id}
POST /api/purchase-orders/{id}/submit
POST /api/purchase-orders/{id}/cancel
```

创建和提交采购单需要 `Idempotency-Key`。客户端不得提交服务端计算的累计已收数量或库存余额作为写入值。采购单更新只允许 `DRAFT`，提交后使用显式收货/取消命令。

### 6.4 收货

```http
GET  /api/purchase-receipts
POST /api/purchase-receipts
GET  /api/purchase-receipts/{id}
PUT  /api/purchase-receipts/{id}
POST /api/purchase-receipts/{id}/confirm
```

确认请求必须携带 `Idempotency-Key`，包含收货单版本、收货行版本、本次实际数量/重量和原因（如为人工修正）。服务端重新计算剩余量、采购状态、库存余额和流水，不信任前端累计值。

### 6.5 库存

```http
GET /api/inventories
GET /api/inventory-movements
```

本期不提供通用库存调整写接口。库存查询支持仓库、SKU、分类、数量范围和更新时间筛选；流水查询支持仓库、SKU、业务类型、来源单号和日期筛选。

### 6.6 幂等

幂等作用域至少区分：

- `PURCHASE_DEMAND_GENERATE`；
- `PURCHASE_ORDER_CREATE`；
- `PURCHASE_ORDER_SUBMIT`；
- `PURCHASE_ORDER_CANCEL`；
- `PURCHASE_RECEIPT_CREATE`；
- `PURCHASE_RECEIPT_CONFIRM`。

相同作用域和 key、相同规范化请求哈希返回原结果；相同 key 但请求不同返回 HTTP 409。收货确认的幂等完成记录必须与库存事务同一事务提交。

## 7. 数据库设计

从当前已存在的 V7 之后新增 Flyway 迁移，不修改已应用迁移。建议按以下逻辑拆分，实际版本号以实现时 HEAD 为准：

- 演进 V1 已存在的供应商、仓库基础表，并新增 SKU-供应商关系；
- 采购需求、分配、采购单、采购单行和采购操作日志；
- 收货单、收货行和称重记录；
- 库存余额、库存流水及必要序列；
- Sprint 3 演示数据。

所有表保留软删除、版本和审计字段；单据编号使用 PostgreSQL sequence，不使用 `MAX + 1`。建议索引包括：

- 活动供应商编码、仓库编码的部分唯一索引；
- `purchase_demand(sales_order_item_id)`；
- `purchase_order(status, supplier_id, warehouse_id)`；
- `purchase_order_item(purchase_order_id)`；
- `purchase_receipt(purchase_order_id, status)`；
- `inventory(warehouse_id, sku_id)` 活动唯一索引；
- `inventory_movement(warehouse_id, sku_id, occurred_at)`；
- 来源单据类型/ID/行 ID 索引；
- 幂等作用域与 key 唯一索引。

数据库可以使用非空、正数、状态枚举等 CHECK 约束，但不建立外键。部分唯一索引和事务校验共同保证活动记录唯一性。

## 8. 事务、并发和错误处理

### 8.1 收货确认事务

收货确认必须标注 `@Transactional`，且不得在事务外调用库存写入。事务中锁定顺序统一为采购单、采购单行、库存余额；插入库存余额时处理并发创建冲突并重试或返回明确冲突。

事务成功后必须同时满足：

```text
receipt.status = CONFIRMED
purchase.receivedQuantity += receipt.quantity
inventory.quantity += receipt.quantity
新增一条 PURCHASE_IN movement
```

其中库存流水的变动前/变动后数量必须与余额更新一致。

### 8.2 并发场景

至少处理：

- 两次同时生成同一销售订单行的采购需求；
- 两个操作者同时提交同一采购单；
- 两次同时确认同一收货单；
- 两张收货单同时收取同一采购单的剩余数量；
- 两个首次收货事务同时创建同一仓库/SKU 库存余额。

冲突使用版本校验、行锁、唯一索引和幂等记录共同防护。不能通过捕获异常后继续写入来掩盖库存不一致。

### 8.3 错误

新增错误码应沿用 `ErrorCode`/`BusinessException`/`GlobalExceptionHandler` 现有模式，至少覆盖：供应商或仓库停用、SKU 无效、来源订单状态不允许、数量超额、状态不允许、行不属于单据、版本冲突、幂等冲突和库存写入冲突。

## 9. 前端设计

### 9.1 导航和路由

保留现有 Admin 顶栏、一级/二级侧栏和密集 ERP 风格。当前 `navigation.tsx` 已有“采购”和“库房”一级入口，但需要补齐二级导航；当前 `router/index.tsx` 尚无采购/库存路由，需要增加懒加载路由。

建议路由：

```text
/purchases/demands
/purchases/orders
/purchases/orders/new
/purchases/orders/:id
/purchases/orders/:id/receipts/new
/purchases/receipts
/warehouses/inventories
/warehouses/inventory-movements
/warehouses/suppliers
/warehouses/settings
```

最终路径可在实现前按菜单信息架构统一，但页面必须保持可深链接和刷新恢复。

### 9.2 采购需求页

使用 ProTable 展示来源订单、客户、SKU、需求数量、已分配、待分配、供应商、仓库和状态。批量汇总前展示预览分组及分配关系，避免用户在生成采购单后才发现拆分结果。处理 loading、empty、error、retry 和 409 冲突。

### 9.3 采购单页

列表展示采购单号、供应商、仓库、采购员、计划日期、计划数量、已收数量、金额、状态和操作。详情页展示采购需求来源、采购单行快照、收货进度、历史收货和操作日志。草稿可编辑；提交、取消等破坏性操作需要确认并禁用重复提交。

### 9.4 收货页

采用独立全页或明确的作业页，不使用大型 Drawer。每行显示：

- 采购数量；
- 历史已收；
- 本次数量/实重；
- 剩余数量；
- 单位和 SKU 规格；
- 称重来源和最近记录；
- 异常/人工修正原因。

重量和数量使用等宽数字、右对齐；非标品突出大号实重录入。确认后转为只读，并显示成功后的采购状态、库存结果和流水入口。真实电子秤只保留 `DEVICE` 数据来源扩展点，本期使用人工录入。

### 9.5 库存页

库存查询使用密集表格，支持仓库、SKU、分类和数量条件；库存流水只读并显示来源收货单、变动前数量、变动量、变动后数量和操作时间。禁止在库存余额列表上直接提供“编辑库存”按钮。

## 10. 测试与验收

### 10.1 后端单元测试

覆盖：

- 采购需求生成去重和来源校验；
- 汇总及拆分输入/输出模型；
- 采购单状态矩阵；
- 多次收货的累计数量和剩余数量；
- 标品数量与非标品实际重量分离；
- 数量、重量、金额精度；
- 行归属、版本和状态冲突；
- 幂等相同请求重放与不同请求冲突；
- 采购状态完成条件（以确认后的规则为准）。

### 10.2 PostgreSQL 集成测试

覆盖：

- 新迁移创建表、索引、序列且无外键；
- 采购需求来源唯一性和分配数量；
- 一单多次收货；
- 收货确认事务回滚；
- 库存余额与流水累计一致；
- 同一收货重复确认只产生一条入库流水；
- 并发收货和乐观锁/行锁；
- 活动记录部分唯一索引；
- 采购单号、收货单号和流水号不重复。

### 10.3 前端测试

覆盖采购需求筛选/选择/分组模型、采购单表单、收货剩余量计算、十进制字符串传输、版本保留、确认后只读、人工修正原因、重复提交防护、loading/empty/error/retry/success 和 409 重载。

普通基础资料 CRUD 使用聚焦成功路径加关键校验，不用大量脆弱 E2E 替代领域测试。

### 10.4 浏览器验收

至少完成以下流程：

1. 使用已有已确认销售订单生成采购需求，确认来源订单行和待采购数量；
2. 汇总并分配供应商/仓库，生成采购单；
3. 对同一采购单执行第一次部分收货，验证采购进度和库存 `PURCHASE_IN`；
4. 执行第二次收货完成采购单，验证累计数量、库存余额和两条流水；
5. 重复提交同一收货确认，验证只入库一次；
6. 触发版本冲突或模拟事务失败，验证页面提示、数据回滚和可重试。

## 11. 实施分阶段计划

1. 先由业务、仓储和财务确认“决策闸门”中的规则。
2. 以迁移集成测试和领域单测先行，建立供应商、仓库和采购基础表。
3. 实现采购需求来源、去重、汇总和分配。
4. 实现采购单聚合、状态命令、编号和查询。
5. 实现多次收货、人工实重、称重审计和确认事务。
6. 实现库存余额、`PURCHASE_IN` 流水及只读查询。
7. 接入前端导航、页面、冲突处理和关键 E2E。
8. 运行后端 `mvn.cmd test`/必要 PostgreSQL 集成测试，以及前端 `npm run lint`、`npm run typecheck`、`npm test`、`npm run build` 和关键 Playwright 场景。

## 12. MVP 业务决策记录

| 决策项 | Sprint 3 结论 |
| --- | --- |
| 采购需求生成 | 已确认：用户通过显式命令从已确认销售订单生成；每个活动订单行一条需求，不自动生成或自动冲销。 |
| 采购拆分 | 已确认：不实现自动拆单；供应商和仓库必须人工明确，采购员为可选负责人。 |
| 多供应商选择 | 延期：本期不自动选择默认供应商。 |
| 采购审核/供应商确认 | 不纳入本期：状态机保持 `DRAFT → SUBMITTED → PARTIALLY_RECEIVED/RECEIVED`。 |
| 多次收货完成规则 | 已确认：严格禁止超收；累计实收等于计划量时完成，少收不能人工关单。 |
| 取消与反收货 | 已确认：部分收货后不可取消；反收货不纳入本期。 |
| 负库存 | 不纳入本期：Sprint 3 只有采购入库，不提供负库存写命令。 |
| 批次与保质期 | 不纳入本期：库存粒度为 `warehouseId + skuId`。 |
| 库存成本 | 延期：保留可选成本字段，不固化移动平均、税费或运费口径。 |
| 电子秤 | 不纳入本期：仅支持 `MANUAL` 称重，`DEVICE` 留作后续扩展。 |

这些结论是 Task #15–#18 的实现边界。任何改变上述口径的需求都需要先更新本决策记录，不得由代码静默引入永久规则。

## 13. 与后续 Sprint 的接口

- Sprint 4 分拣、发货和配送将消费 SKU、仓库库存和库存流水；本期必须保留稳定的 SKU 与单据来源 ID。
- 后续销售出库应新增 `SALES_OUT`，沿用库存应用服务和流水模型，不直接改余额。
- 后续采购退货、销售退货入库、盘点和调拨应复用 `InventoryMovement`，各自拥有显式命令和事务。
- 后续财务模块消费采购价、收货记录和库存成本字段；本期不把未确认成本算法固化成会计凭证。
- 真实设备接入应通过设备代理或后端适配层，不让浏览器直接访问串口、厂商驱动或本地设备。
