# Sprint 2 客户定价与销售订单纵向链路设计

## 1. 背景与目标

Sprint 2 在 Sprint 1 商品 SPU/SKU 档案之上增加最小客户定价基础和可审计的后台销售订单闭环。完成后，启用客户可按可见性策略选择在售 SKU，系统按协议价优先、市场价回退的规则生成草稿，在提交时重新校验并锁价，非标品可在审核阶段录入实重，订单确认后可发起退货并生成待处理退款记录。

本 Sprint 仍采用 PostgreSQL → Spring Boot REST API → React Admin UI 的模块化单体纵向链路。

## 2. 范围

### 2.1 包含

- 客户类型、客户启停、SKU 可见性策略和客户-SKU 协议价。
- 销售订单草稿、提交审核、实重录入、确认和取消。
- 普通订单与补单，补单可关联同一客户的已确认原订单。
- 协议价/市场价解析、草稿人工改价、提交锁价和金额计算。
- 订单持久化操作日志和关键命令幂等。
- 已确认订单的退货申请、审核、驳回、取消及退款记录完成。
- 客户、协议价、订单、退货和退款的 Admin 页面及自动化测试。

### 2.2 不包含

- 登录、RBAC、数据权限和动态菜单；操作者暂记为 `SYSTEM`。
- 客户联系人、地址、信用额度、客户组和组级结算。
- 支付渠道调用、真实付款、退款会计和对账。
- 采购、库存占用、退货入库和库存流水。
- 电子秤或其他设备接入。
- 微服务、消息队列、分布式事务和复杂事件基础设施。

## 3. 客户与定价

客户仅包含编码、名称、类型、启用状态、可见性策略、版本和审计字段。可见性策略为：

- `ALL_ENABLED`：可下单所有未删除、已上架且所属 SPU 已上架的 SKU。
- `ALLOWLIST`：仅可下单有效客户-SKU 分配中同时满足上架条件的 SKU。

可见性集合更新采用差量同步：保留项携带 ID/version，新增项插入，移除项软删除；禁止通过全删再插改变保留项身份。

客户-SKU 协议价使用半开有效期 `[effectiveFrom, effectiveTo)`，`effectiveTo` 可空。同一客户/SKU 的未删除有效期不得重叠，校验和写入在同一事务完成。

价格解析必须由服务端批量执行，并依次验证：客户启用、客户可见性、SPU/SKU 未删除且上架。当前时刻存在协议价时返回协议价和来源记录 ID，否则回退 SKU 市场价。前端金额和价格来源不具有权威性。

## 4. 销售订单聚合

`SalesOrder` 是聚合根，`SalesOrderItem` 只能通过订单应用服务写入。订单状态为：

```text
DRAFT -> PENDING -> CONFIRMED
   \          \
    ----------> CANCELLED
```

- `DRAFT`：可编辑头和订单行，可保存人工改价。
- `PENDING`：已重新校验并锁价，仅允许非标品实重录入、确认或取消。
- `CONFIRMED`：结算完成，订单编辑终止，可创建退货。
- `CANCELLED`：终止状态，保留取消原因和历史快照。

取消仅允许 `DRAFT`、`PENDING`，且必须填写原因。状态只能通过显式命令端点转换，不提供通用状态更新接口。

订单来源为 `NORMAL` 或 `SUPPLEMENT`。补单必须填写原因；可选关联一个已确认原订单，若关联则客户必须一致。补单沿用普通订单的定价、提交、称重、确认和售后规则。

订单行更新采用差量同步并保留已有 ID/version；跨订单行 ID、缺少版本或过期版本均拒绝。订单持久化客户、商品、SKU、规格、销售单位、商品类型、价格及来源快照，使主数据变更后历史仍可读取。

## 5. 价格、数量与结算

数量、单价和金额均使用四位小数。Java 使用 `BigDecimal`，PostgreSQL 使用 `NUMERIC(18,4)`，API 使用十进制字符串。服务端计算统一使用 `HALF_UP`：

```text
lineAmount = round(quantity * unitPrice, 4, HALF_UP)
```

草稿非人工改价行按协议价优先、市场价回退解析价格。草稿人工改价必须填写原因，来源记为 `OVERRIDE` 并写入持久化审计。

提交时服务端再次验证客户、可见性和 SKU，并重新解析所有非人工改价行，将结果复制为锁定单价和锁定来源。提交后价格不再随主数据改变。

- 标品：提交时将 `actualQuantity` 复制为 `orderedQuantity`，来源记为系统。
- 非标品：提交时 `actualQuantity` 为空；`PENDING` 期间由 `SYSTEM` 人工录入或修正，必须填写原因并记录前后值。
- `orderedQuantity` 永不被实重覆盖。
- 确认要求每行均有大于零的实数量，并按 `actualQuantity * lockedUnitPrice` 重新计算结算金额及总额。

## 6. 操作日志与幂等

订单操作日志只追加不修改，记录操作类型、`SYSTEM` 操作者、时间和 JSONB before/after 数据。创建文档和造成状态转换或副作用的命令必须携带 `Idempotency-Key`。

幂等记录以操作作用域和 key 唯一，保存规范化请求哈希和结果：

- 相同 key、相同请求返回之前的结果。
- 相同 key、不同请求返回 HTTP 409。

订单号、退货号和退款号由 PostgreSQL 序列生成，并组合日期前缀形成展示编号，禁止使用 `MAX + 1`。

## 7. 退货与退款

仅 `CONFIRMED` 订单可以创建退货。退货状态为 `PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`。

创建和批准时锁定相关记录并校验每个退货行属于同一原订单。待审核和已批准退货数量都占用可退额度：

```text
remaining = originalActualQuantity - pendingReserved - approvedQuantity
```

数量必须大于零，累计不得超过原订单行实数量。驳回和取消必须填写原因。

批准时可逐行接受数量，按原订单锁定单价计算批准金额，并在同一事务中创建且仅创建一个 `PENDING` 退款记录。退款完成可记录可选且唯一的外部凭证，将状态改为 `COMPLETED`；不调用支付渠道、不产生库存动作。所有售后状态变化追加到原订单操作历史。

## 8. 数据库与 API 约束

Flyway 只新增 V3 及后续迁移，不修改已经应用的 V1/V2。所有关系使用服务事务校验和查询索引，不建立数据库外键。业务表使用 BIGINT identity、乐观锁 `version`、软删除 `deleted` 和审计字段；活动记录唯一性使用 PostgreSQL 部分唯一索引。

API 继续使用 `/api` 前缀和 `{code,message,data}` 响应；分页使用 `{records,page,pageSize,total}`。Bean Validation 错误返回 400，不存在返回 404，版本/唯一性/幂等冲突返回 409。

主要显式命令：

```http
POST /api/orders
PUT  /api/orders/{id}
POST /api/orders/{id}/submit
POST /api/orders/{id}/items/{itemId}/actual-quantity
POST /api/orders/{id}/confirm
POST /api/orders/{id}/cancel

POST /api/order-returns
POST /api/order-returns/{id}/approve
POST /api/order-returns/{id}/reject
POST /api/order-returns/{id}/cancel
POST /api/order-refunds/{id}/complete
```

## 9. 前端交互

保持现有 Admin 顶栏和双侧栏，一级/二级菜单根据路由激活。客户、协议价和订单列表使用高密度 ProTable；订单编辑采用独立全页表单而非大型 Drawer。

订单表单使用纯不可变 form model，保留订单头/行 ID 与 version，发送十进制字符串，不发送客户端权威总额。选择客户后仅加载可下单 SKU；保存草稿与提交分离，提交前提示将刷新并锁定价格。人工改价和实重修正必须填写原因，并禁用重复提交。

页面显式处理 loading、empty、error、retry、success 和 409 conflict。金额右对齐并使用等宽数字；已确认订单只显示读取、退货等允许操作。

## 10. 测试与验收

后端测试覆盖：可见性差量同步、协议价区间边界和重叠、价格解析、订单行身份保留、状态矩阵、补单校验、四位精度、改价与锁价、实重审计、退货额度、原子退款和幂等冲突。PostgreSQL 集成测试验证事务回滚、乐观锁、锁价稳定、并发安全额度，以及迁移中的表、约束、索引、JSONB、序列和零外键。

前端测试覆盖路由导航、筛选和表单、不可变订单行、ID/version 保留、改价原因、锁价展示、实重就绪、可退数量、冲突重载和重复提交防护。

浏览器验收至少覆盖：

1. 客户可见性和协议价 → 草稿 → 提交锁价 → 非标品实重 → 确认 → 金额和日志。
2. 补单 → 部分退货 → 批准生成退款 → 完成退款 → 超额退货被拒绝。
