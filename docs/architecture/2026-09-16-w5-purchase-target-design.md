# W5 Purchase 采购域 · Target Design（目标设计）

> 阶段：W5 = Purchase（**设计已批准（2026-09-16），进入实施 T0 → T23**）
> 基线 HEAD：`cd9f2b88c558d601e57f8ad48241158afe78c780`（`feature/sprint5`）
> 冻结：`V1–V14` 不可修改 · `SmartAdmin v3.31 + PostgreSQL` · `Java 21` · `Flyway` · `Sa-Token` · `Vue3 + TS`
> 事实源：`docs/architecture/2026-09-16-w5-purchase-audit.md`（本设计的全部事实依据）
> 裁决记录：`docs/architecture/2026-09-16-w5-purchase-approval.md`
> 修订历史：2026-09-16 用户批准 22 项裁决，其中 **Q3a / Q13 / Q14 / Q17 / Q5 / Q6a 六项按用户指令改写**；
> 本文的全部派生数字（表 / 索引 / 错误码 / API 与 Form 字段 / 测试数 / DoD / §0.2 / §14 / 附录 A）已按修订后重算。

---

## 0. 本文件的状态与前置裁决

### 0.1 本文件的定位

这是 W5 采购域的**目标设计**。它把审计（audit）中提取的事实与不变量，转成可直接执行的规格：
表、字段、索引、状态机、API、权限、错误码、事务边界、锁序、幂等、测试矩阵、实施顺序、DoD。

**当前状态：已获批准（2026-09-16）。** 六项修订（Q3a / Q13 / Q14 / Q17 / Q5 / Q6a）已并入本文件，
本文即 W5 编码的唯一规格依据。

### 0.2 前置裁决项（**共 22 项**：Q1–Q17 + 子项 Q2a/Q3a/Q6a/Q7a/Q8a）

> 审计（`…-w5-purchase-audit.md` §0.2）只列了其中的 15 项（Q1–Q13 + Q3a/Q4a）。
> 本设计在细化过程中**新增 8 项**（Q2a、Q6a、Q7a、Q8a、Q14、Q15、Q16、Q17），
> 并把审计的 Q4a 并入 Q4。**本表与 §14 是唯一权威清单。**
>
> **2026-09-16 批准与修订**：Q1、Q2、Q2a、Q3、Q4、Q6、Q7、Q7a、Q8、Q8a、Q9、Q10、Q11、Q12、Q15、Q16
> 按本设计推荐值**批准**；**Q3a、Q13、Q14、Q17、Q5、Q6a 六项按用户指令修订后批准**（下表对应行已改写）。

| 编号 | 问题 | 本设计推荐 | 章节 |
| --- | --- | --- | --- |
| Q1 | 是否建仓库主数据 | **建**（最小化 `warehouse`） | §2.1 · §5.2 |
| Q2 | 采购单状态机 | **A 源 6 状态** | §4.1 |
| Q2a | 是否实现少收关单 | **实现**（`shortClose` + `SHORT_CLOSED` 终态） | §4.1 |
| Q3 | 超收规则 | **可配置容差 + 超出整笔回滚** | §7.5 |
| Q3a | 容差配置载体 | **SmartAdmin Config（`t_config` + `ConfigService`）**；key `scm.purchase.over_receipt_tolerance_percent`，默认 10、范围 0–100；缺失回退 10、非法 40999；V15 播种（**非字典、非自建 `sys_config`**） | §7.5 |
| Q4 | 收货模式 | **W5 只实现 `DIRECT`**；4 个模式/投递列**不建**（W6 `ALTER` 追加） | §3.4 · §5.7 |
| Q5 | Inventory 边界 | **只定义契约，零调用点，零库存表** + 明确 W6 首次启用前的 backfill 口径与稳定唯一源键（`PURCHASE_RECEIPT_ITEM` / `receipt_item_id`） | §3.5 · §8 · §8.5 |
| Q6 | 采购需求是否属于 W5 | **属于**，三处剪枝（去 batch/fulfilled/库存抵扣） | §1.1 · §5.3 |
| Q6a | 生成入参 | **半开时间段 `[startAt, endAt)`** + warehouseId + 可选 supplierId/purchaserId；`demand_date = source_confirmed_at` 的 `Asia/Shanghai` 日期（**不是 `date(startAt)`**） | §7.4 |
| Q7 | 收货单确认口径 | **一单一次确认**（不建 confirmation 两表） | §4.3 · §5.7 |
| Q7a | 是否保留 `PARTIALLY_CONFIRMED` | **不保留**；`confirm` 必须提交本单全部行 | §4.3 |
| Q8 | 单号机制 | **PG sequence**（偏离 Native First，理由见 §7.6） | §7.6 |
| Q8a | 单号前缀 | **`PO`（采购单）/ `PR`（收货单）** | §7.6 |
| Q9 | 退货/无单收货/询价/二维码 | **全部不做** | §1.2 |
| Q10 | 数据权限 | **不做** | §1.2 |
| Q11 | 错误码段 | **合计 40 个码**：`PurchaseErrorCode` 38 + `WarehouseErrorCode` 2（见 §7.7） | §7.7 |
| Q12 | 文档同步 | **同步** `AGENTS.md` + 迁移审计报告 + `MEMORY.md` + `AGENTS.md §6` 补 `warehouse` | §13 |
| Q13 | 采购行 ↔ 需求基数 | **M1：一行一 SKU，一行多需求 = N allocations**；allocation 身份 `(purchase_order_item_id, purchase_demand_id)`；差量同步按 **allocation 集合**处理（禁「一 item 一 allocation」算法） | §5.4 · §5.6 · §7.4 · §7.8 |
| Q14 | `purchase_operation_log.purchase_order_id` 可空 | **可空** + **operation-type-aware CHECK**（`DEMAND_GENERATE` 双 id 空 / `DEMAND_ALLOCATE` 只有采购单 id / `RECEIPT_*` 双 id 非空） | §5.10 · §7.12 |
| Q15 | `receipt_weighing_record` 无 version/deleted | **接受**（只追加审计事实） | §5.9 |
| Q16 | `receipt_weighing_record.scale_precision` | **删除**（A-D4：A 源从不赋值） | §5.9 |
| Q17 | 需求单位 vs 采购单位 | **两者分离**：`purchase_demand.demand_unit_snapshot` ← `sales_order_item.sale_unit_snapshot`；`purchase_order_item.purchase_unit_snapshot` ← `supplier_sku.purchase_unit`；**不等则拒绝自动分配**（`PURCHASE_UNIT_CONVERSION_REQUIRED`） | §5.3 · §5.11 · §7.4 |

### 0.3 本设计的三个硬约束（来自用户指令）

```text
H1  W5 只做 Purchase：不实现库存余额 / 占用 / 流水 / 出库 / 分拣 / 配送
H2  W5 只定义 Inventory integration contract；禁止创建任何临时库存表
H3  Frontend = Copy First + Adapt：C 已有采购页面优先复制 → 剪枝 → 适配；不重新生成
```

### 0.4 与 legacy 的一句话对应关系

> W5 把 A 源（`95a5423`）的**采购需求 → 采购单 → 多次收货 → 累计差异**四条业务规则完整保留，
> 把 A 源的**库存写入（`InventoryApplicationService.postPurchaseIn`）从收货事务中摘除**，
> 只留下一个零调用点的 `PurchaseInventoryContract`；表结构由 15 张收敛为 9 张
> （去掉 A 源 2 张死表/死字段 + 2 张多次确认表 + 2 张库存表）。

---

## 1. 范围（Scope）

### 1.1 做

```text
S1  仓库主数据（最小）：warehouse 表 + 只读列表 + 最小 CRUD + 1 条默认仓库种子
    —— 回答 Q1：采购单/收货单必须绑定仓库（A 源 warehouse_id NOT NULL），
       单仓库约束由「种子数据 + 前端不提供新建入口」表达，而不是硬编码 1L

S2  采购需求：从已确认销售订单按半开时间段 [startAt, endAt) 汇总生成
    —— purchase_demand（每活动订单行至多一条，demand_unit_snapshot ← 销售单位）
    —— demand_date = source_confirmed_at 在 Asia/Shanghai 下的日期（Q6a，禁止 date(startAt)）
    —— 幂等：Idempotency-Key + idempotency_record（复用 W4）

S3  采购单：create / update(retained reconciliation) / submit / cancel / short-close
    —— 供应商 + 仓库必须人工明确；单头与全行一致（P5）
    —— 6 状态机（Q2）；金额服务端重算（P6）
    —— 采购单价来自 supplier_sku.reference_price 的建议值，但必须人工确认后写入

S4  采购收货：一采购单多张独立收货单；DRAFT → CONFIRMED 单次确认
    —— 多次收货累计；部分收货；容差内超收；超出整笔回滚（Q3）
    —— 标品按数量、非标品按人工实重（P21）
    —— 幂等：Idempotency-Key + idempotency_record

S5  收货差异与对账字段：planned / cumulative / remaining / over_receipt / difference
    —— 恒等式由 DB CHECK 强制（P24）

S6  称重记录：receipt_weighing_record（MANUAL，非标品实重审计；G-05 预留 DEVICE 扩展点）

S7  采购操作日志：purchase_operation_log（全量 before/after JSONB + type 白名单）

S8  前端：5 个页面 + 6 个组件 + 4 个 API + 常量/类型/错误/表单模型
    —— C 的 4 个采购页面 Copy First + Adapt

S9  Inventory 契约：PurchaseInventoryContract + NoOpPurchaseInventoryContract（**零调用点**）
```

### 1.2 不做（用户锁定 + legacy 事实源）

| 项 | 理由 |
| --- | --- |
| **库存余额 / 库存流水 / 库存占用 / 库存出库** | 用户指令 H1/H2；`OrderInventoryContract` 先例 |
| 采购退货（退供应商） | 需求 05-06 未定；A 源 spec §2.2 排除；C 源零实现 |
| 无单收货（`NO_ORDER` + 补关联） | **A 源无事实源**；C 源独有；会产生无来源库存事实 |
| 询价比价（`t_inquiry*`） | **A 源无**；C 源独有；需求 05-04 未定 |
| 二维码 / 供应商协同 / 供应商门户 | 需求 05-03/05-02 未定；A 源无实现 |
| 供应商「接单」态（C 的 `PENDING(1)`） | 供应商不是系统用户（需求 05-03 未定） |
| 批次 / 保质期 / 一物一码 | G-03 已定不启用 |
| `DEFERRED` 收货模式 + 二次入库（putaway） | 必须写库存才能收尾 → 属 W6（Q4） |
| 应付账款 / 付款 / 对账 | 财务域（W7+）；需求 G-02/09-02 |
| 按采购员/供应商的数据权限 | Q10 |
| 采购成本会计口径（移动加权平均等） | 财务未确认（A 源 spec §5.5） |
| **单位换算模型**（kg↔箱、件↔瓶…） | **Q17**：W5 无换算系数来源，**禁止猜系数**；需求单位 ≠ 采购单位时拒绝自动分配（`PURCHASE_UNIT_CONVERSION_REQUIRED`）；将来作为独立 Unit Conversion 能力另开波次 |
| 电子秤 / 设备协议接入 | G-05 一期手工录入 |

### 1.3 交付物

```text
docs/architecture/2026-09-16-w5-purchase-audit.md            （审计，已完成）
docs/architecture/2026-09-16-w5-purchase-target-design.md    （本文件）
docs/architecture/2026-09-16-w5-purchase-approval.md         （批准记录，编码前生成）
docs/architecture/w5-applied-migrations.sha256               （V1–V16 冻结清单）
```

---

## 2. 目标架构

### 2.1 模块落点

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/
├─ common/            （W1–W4，复用，**零修改**）
├─ product/           （W1，只读引用）
├─ customer/          （W2，只读引用）
├─ supplier/          （W2，复用 SupplierService / SupplierSkuService / SupplierValidator）
├─ pricing/           （W3，W5 **不调用**）
├─ order/             （W4，只读引用 sales_order / sales_order_item）
├─ warehouse/         ← **W5 新增**（最小主数据域）
│   ├─ constant/      ScmWarehouseStatusEnum · WarehouseErrorCode
│   ├─ controller/    WarehouseController
│   ├─ dao/           WarehouseDao
│   ├─ domain/{entity,form,vo}/
│   ├─ manager/       WarehouseValidator
│   └─ service/       WarehouseService · WarehouseQueryService
└─ purchase/          ← **W5 新增**
    ├─ constant/      ScmPurchaseStatusEnum · ScmPurchaseDemandStatusEnum
    │                 ScmReceiptStatusEnum · ScmPurchaseOperationTypeEnum
    │                 ScmPurchaseQuantitySourceEnum · ScmWeighingSourceEnum
    │                 PurchaseErrorCode · PurchaseConfigKey（Q3a：t_config key 常量）
    ├─ controller/    PurchaseDemandController · PurchaseOrderController · PurchaseReceiptController
    ├─ dao/           PurchaseDemandDao · PurchaseDemandAllocationDao · PurchaseOrderDao
    │                 PurchaseOrderItemDao · PurchaseReceiptDao · PurchaseReceiptItemDao
    │                 ReceiptWeighingRecordDao · PurchaseOperationLogDao
    ├─ domain/
    │   ├─ entity/    PurchaseDemandEntity · PurchaseDemandAllocationEntity
    │   │             PurchaseOrderEntity · PurchaseOrderItemEntity
    │   │             PurchaseReceiptEntity · PurchaseReceiptItemEntity
    │   │             ReceiptWeighingRecordEntity · PurchaseOperationLogEntity
    │   ├─ form/      （见 §7.2）
    │   └─ vo/        （见 §7.2）
    ├─ manager/       PurchaseOrderValidator · PurchaseOrderItemChangeSet
    │                 PurchaseOrderStateMachine · PurchaseDemandAllocator
    │                 PurchaseReceiptQuantityCalculator · PurchaseAmountCalculator
    │                 PurchaseSnapshotFactory
    ├─ service/       PurchaseDemandService · PurchaseOrderService · PurchaseReceiptService
    │                 PurchaseQueryService · PurchaseNumberGenerator
    │                 PurchaseIdempotencyService
    └─ support/       PurchaseInventoryContract · NoOpPurchaseInventoryContract
                      PurchaseJsonbTypeHandler · PurchaseIdempotencyRequestHasher
                      PurchaseDemandSourceGuard · PurchaseWarehouseReferenceGuard
```

**为什么 `warehouse` 独立成域（偏离 `AGENTS.md` §6 的包清单）**

`AGENTS.md` §6 的领域清单（`product customer supplier order purchase inventory sorting delivery …`）
没有 `warehouse`。三个候选落点：

| 候选 | 问题 |
| --- | --- |
| `supplier/`（A 源做法） | 让 W2 已验收的包承担 W5 职责；W6 库存域会反向依赖 W2 包 |
| `purchase/` | 仓库是跨域共享主数据，放进采购会让 W6 依赖 `purchase` 包 |
| **`warehouse/`（本设计）** | 唯一没有反向依赖的方案；W6 库存域可直接依赖它 |

→ **采 `warehouse/`**，并在实施时于 `AGENTS.md` §6 的领域清单中补一行（Q12 的一部分）。

### 2.2 分层职责（对齐 SmartAdmin ERP 范式与 W1–W4）

| 层 | 职责 | 硬约束 |
| --- | --- | --- |
| `controller` | 只做参数校验 + 权限注解 + 调用 service | 不写业务逻辑；`@SaCheckPermission` 必须存在 |
| `service`（写） | 事务边界 + 幂等 + 操作日志 | `@Transactional(rollbackFor = Exception.class)` |
| `service`（读） | 查询与装配 | 只读，不开启事务 |
| `manager` | 纯领域逻辑（校验/状态机/差量/数量计算） | 无 Spring 事务；可被单元测试直接调用 |
| `dao` | MyBatis-Plus + XML | 不写业务规则 |
| `support` | 契约 / TypeHandler / 哈希 | 无状态 |

### 2.3 与上游域的关系

```text
W1 Product ──（只读）──┐
                       ├──→ W5 Purchase ──（只读）──→ sales_order / sales_order_item（W4）
W2 Supplier ─（复用）──┤         │
   supplier_sku        │         └──（契约，零调用）──→ W6 Inventory（未实现）
W5 Warehouse ─（主数据）┘
W3 Pricing ─（不调用）
```

| 依赖方向 | 形态 | 说明 |
| --- | --- | --- |
| W5 → W1 Product | **只读** | 读 `spu_code` / `product_name` / `sku_code` / `spec_values` / `product_type`，写入快照 |
| W5 → W2 Supplier | **复用服务** | `SupplierService.requireEnabledSupplier`（W2 已有等价能力则直接注入） |
| W5 → W2 `supplier_sku` | **复用表** | `purchase_unit` / `reference_price` / `purchaser_id` / `is_default`（**禁新建第二套**） |
| W5 → W4 Order | **只读** | 汇总来源：`sales_order.status = 'CONFIRMED'` + `sales_order_item.actual_quantity > 0` |
| W5 → W3 Pricing | **无** | 采购价与销售价独立 |
| W5 → W6 Inventory | **仅契约** | `PurchaseInventoryContract`，**零调用点** |

---

## 3. Purchase vs Receiving vs Inventory 边界（**用户明确要求**）

### 3.1 三者的职责划分（一句话版）

```text
Purchase  负责：采购需求 → 采购单 → 采购行的「计划量 / 累计已收量 / 差异」事实
Receiving 负责：每一次到货的「本次数量 / 本次实重 / 累计快照」，是 Purchase 聚合内部命令
Inventory 负责：从「收货确认」开始，把收货事实转成库存余额 + 只追加流水（W6，W5 不实现）
```

### 3.2 Receiving 是 Purchase 聚合内部命令，还是独立聚合？

**结论：业务上是 Purchase 聚合内部命令，物理上是独立表（但同模块、同聚合边界）。**

| 维度 | 判定 | 依据 |
| --- | --- | --- |
| **模块落点** | `module/scm/purchase/**`（**不建独立 `receiving` 包**） | 收货的每一次确认都直接改 `purchase_order_item.received_quantity` 与 `purchase_order.status`；跨包会形成循环依赖 |
| **聚合根** | `PurchaseOrder` 是聚合根；`PurchaseReceipt` 是**受聚合根管辖的实体** | `create` 要求采购单 ∈ {`SUBMITTED`, `PARTIALLY_RECEIVED`}；仓库强制继承采购单；`confirm` 在采购单行上累计 |
| **独立表** | 是（有自己的单号序列、自己的版本字段） | 一张采购单多张收货单，每次到货不可覆盖历史（P16） |
| **独立服务** | 是（`PurchaseReceiptService`），但**不得独立于采购单存在** | 便于测试与职责分离；但所有写路径都必须先锁采购单 |
| **独立状态机** | 是（`DRAFT → CONFIRMED`），但**不引入独立聚合根的语义** | A 源 V10 的 `PARTIALLY_CONFIRMED` 被 Q7 删除 |

**为什么不做成独立聚合根**：独立聚合根意味着「收货单可以脱离采购单独立创建/演进」，
但 A 源和 C 源都要求「收货必须关联采购单与采购行」；且库存事实需要采购行的计划量作为基数。
做成独立聚合根会引入「跨聚合最终一致」问题，而 `AGENTS.md` §5 明确「禁止分布式事务、复杂事件基础设施」。

### 3.3 Inventory 从什么时候开始拥有库存事实？

```text
时间轴
─────────────────────────────────────────────────────────────────────────────
创建采购单        提交采购单        创建收货单        收货确认(CONFIRMED)       W6
   │                 │                │                    │                  │
   ▼                 ▼                ▼                    ▼                  ▼
写 purchase_order  改 status      写 purchase_receipt   累计 received_     写 inventory
+ _item             + submitted_at + _item              quantity           + inventory_movement
+ allocation                                             + 差异字段          （PURCHASE_IN）
   │                 │                │                    │                  │
   └─────────────────┴────────────────┴────────────────────┘                  │
              库存事实 = 无（W5 全程不产生）                                    │
                                                          └── W5 结束，库存事实尚未创建 ──┘
```

**W5 的边界声明**

```text
W5 在「收货确认」处停止：产生「已收货事实」（purchase_receipt.status = CONFIRMED
+ purchase_order_item.received_quantity 累计 + 差异字段），
但不产生「库存事实」（不写任何余额、不写任何流水）。
```

**为什么这是正确的**：(1) 用户指令 H1/H2 明文要求；
(2) 库存事实需要余额维度（`warehouse + sku`）、成本口径（加权平均）、
占用/释放、盘点/报损等一整套语义，任何「临时余额表」都会成为 W6 的迁移债；
(3) 采购侧真正需要的库存信息只有「可用量」（用于汇总时抵扣），而 A 源该能力本身未落地（A-D3/G7）。

### 3.4 逐问回答（用户指令 §3 的 7 个问题）

| 问题 | 答案 | 依据 |
| --- | --- | --- |
| **创建采购单 → 是否影响库存？** | **否**。只写 `purchase_order` / `purchase_order_item` / `purchase_demand_allocation` | A 源 `create` + C 源 `add` 一致 |
| **提交采购单 → 是否影响库存？** | **否**。只改 `status = SUBMITTED` + `submitted_at` | A 源 `submit` |
| **收货 → 谁产生库存？** | **W6 的库存域**。W5 的 `confirm` 只产生「已收货事实」；库存事实由 W6 消费该事实后创建 | 用户 H1/H2；A 源的库存写入被摘除 |
| **部分收货 → 如何累计？** | 同事务在 `purchase_order_item.received_quantity` 上累加（乐观锁）；同时把累计快照写入 `purchase_receipt_item.cumulative_received_quantity` / `remaining_quantity` / `over_receipt_quantity` / `receipt_difference` | A 源 `confirmLine` |
| **重复收货 → 如何幂等？** | `Idempotency-Key` + `idempotency_record`（`scope = PURCHASE_RECEIPT_CONFIRM:{receiptId}`）；同 key + 同规范化请求哈希 → 返回原结果；同 key + 异哈希 → 409 | A 源 spec §6.6 + V2 W4 `OrderIdempotencyService` |
| **超收 → 允许还是拒绝？** | **容差内允许，超出拒绝并整笔回滚**。`累计有效收货 ≤ planned × (1 + tolerance/100)`，`tolerance` 来自 SmartAdmin `t_config` 的 `scm.purchase.over_receipt_tolerance_percent`，默认 10、范围 0–100 | A 源 `validateLines` + V33 · Q3a |
| **退货 → 谁负责扣回库存？** | **W5 不做**。采购退货属后续波次（G3）；若将来实现，扣回库存由 W6 的 `PURCHASE_RETURN_OUT` 流水负责 | 需求 05-06 未定 |
| **`warehouse_id` → 属于采购单、收货单还是库存域？** | **属于采购单**（`NOT NULL`），收货单**继承并快照**；库存域只做引用。仓库**主数据**独立成 `module/scm/warehouse/` | A 源 `purchase_order.warehouse_id NOT NULL` |
| **batch / lot → W5 做还是后续？** | **不做，且 G-03 已定永久不启用批次与保质期** | 需求 G-03 |

### 3.5 Inventory 集成契约（只定义，不实现）

见 §8。核心纪律：**W5 定义 `PurchaseInventoryContract`，提供 `NoOpPurchaseInventoryContract` 默认实现，
W5 代码中零调用点。** W6 实现时在 `PurchaseReceiptService.confirm` 的事务内加**一行调用**（无迁移）。

**Q5 补充（W6 Inventory bootstrap contract）**：W5 边界不变（零库存表 / 零库存写入 / 契约零调用点），
但设计必须明确 **W6 Inventory 首次启用前的 backfill 口径**：W6 必须在库存能力上线前，
**回放全部历史 `purchase_receipt.status = 'CONFIRMED'` 的 `purchase_receipt_item`**，
且每条库存流水必须带**稳定唯一源键**（`source_document_type = 'PURCHASE_RECEIPT_ITEM'` +
`source_document_item_id = receipt_item_id`），使「历史 backfill / 未来实时 confirm / 重试」
三者互相**不可重复入库**。W5 **不新增** `inventory_posted` 之类的死字段。详见 §8.5。

---

## 4. 状态机

### 4.1 采购单状态（6 值，Q2）

```text
DRAFT ──submit──→ SUBMITTED ──收货确认──→ PARTIALLY_RECEIVED ──收货确认──→ RECEIVED
  │                    │                          │
  │cancel              │cancel                    │short-close
  ▼                    ▼                          ▼
CANCELLED ←────────────┘                     SHORT_CLOSED
（终态）                                      （终态）
```

| 状态 | 含义 | 可执行命令 |
| --- | --- | --- |
| `DRAFT` | 草稿，可编辑行与需求分配 | `update` · `submit` · `cancel` · `delete` |
| `SUBMITTED` | 已提交，只允许收货或取消 | `cancel` · `receipt:create` |
| `PARTIALLY_RECEIVED` | 至少一次确认收货，仍有可收量 | `receipt:create` · `short-close` |
| `RECEIVED` | 达完成条件（含容差内超收），只读 | — |
| `SHORT_CLOSED` | 少收关单，终态，只读 | — |
| `CANCELLED` | 取消，终态，只读 | — |

### 4.2 状态转换表（完整规格）

| # | 命令 | from | to | 前置校验 | 权限 | 幂等 scope |
| --- | --- | --- | --- | --- | --- | --- |
| T1 | `create` | — | `DRAFT` | 供应商启用 + 仓库启用 + 至少 1 行 + 每行 SKU ∈ `supplier_sku`(启用) + 需求分配校验 | `scm:purchase:add` | `PURCHASE_ORDER_CREATE` |
| T2 | `update` | `DRAFT` | `DRAFT` | `version` 必须相等（缺失 → `PURCHASE_ITEM_VERSION_REQUIRED`）；retained reconciliation（P2/P3） | `scm:purchase:update` | —（靠 `@Version`） |
| T3 | `submit` | `DRAFT` | `SUBMITTED` | `version` 相等；至少 1 行 | `scm:purchase:submit` | `PURCHASE_ORDER_SUBMIT:{id}` |
| T4 | `cancel` | `DRAFT` · `SUBMITTED` | `CANCELLED` | `version` 相等；`cancelReason` 非空 | `scm:purchase:cancel` | `PURCHASE_ORDER_CANCEL:{id}` |
| T5 | `shortClose` | `PARTIALLY_RECEIVED` | `SHORT_CLOSED` | `version` 相等；**至少一行已收 且 至少一行未收齐**；`reason` 非空 | `scm:purchase:short-close` | `PURCHASE_ORDER_SHORT_CLOSE:{id}` |
| T6 | `delete` | `DRAFT` | 逻辑删除 | 无收货记录（`DRAFT` 隐含） | `scm:purchase:delete` | — |
| T7 | 收货确认驱动 | `SUBMITTED` · `PARTIALLY_RECEIVED` | `RECEIVED` / `PARTIALLY_RECEIVED` | 全部行 `received >= planned` → `RECEIVED`，否则 `PARTIALLY_RECEIVED` | —（随收货确认） | — |
| T8 | 收货确认驱动 | `PARTIALLY_RECEIVED` · `RECEIVED` | **不变** | `SHORT_CLOSED` / `CANCELLED` / `RECEIVED` 时**禁止新收货** | — | — |

**关键约束（P14）**：`PARTIALLY_RECEIVED` **不允许 `cancel`**（A 源 spec §4.3）。
需要终止时使用 `shortClose`（Q2a）。

**`PurchaseOrderStateMachine` 规格（实现为声明式策略，不是散落的 if）**

```java
public static boolean canTransition(String from, String to) {
    return switch (from) {
        case "DRAFT"              -> Set.of("SUBMITTED", "CANCELLED").contains(to);
        case "SUBMITTED"          -> Set.of("PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED").contains(to);
        case "PARTIALLY_RECEIVED" -> Set.of("PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED").contains(to);
        default                   -> false;   // RECEIVED / SHORT_CLOSED / CANCELLED 为终态
    };
}
public static boolean editable(String s)      { return "DRAFT".equals(s); }
public static boolean receivable(String s)    { return "SUBMITTED".equals(s) || "PARTIALLY_RECEIVED".equals(s); }
public static boolean cancellable(String s)   { return "DRAFT".equals(s) || "SUBMITTED".equals(s); }
public static boolean shortClosable(String s) { return "PARTIALLY_RECEIVED".equals(s); }
```

### 4.3 收货单状态（2 值，Q7/Q7a）

```text
DRAFT ──confirm──→ CONFIRMED（只读，不可回退）
  │
  └─delete──→ 逻辑删除
```

| 状态 | 含义 | 可执行命令 |
| --- | --- | --- |
| `DRAFT` | 已建单，**不产生任何副作用** | `update` · `confirm` · `delete` |
| `CONFIRMED` | 已确认，只读 | — |

**与 A 源的差异（必须记录）**

| 项 | A 源 | W5 | 理由 |
| --- | --- | --- | --- |
| 状态值 | `DRAFT` / `PARTIALLY_CONFIRMED` / `CONFIRMED` | `DRAFT` / `CONFIRMED` | Q7：多次确认被「一采购单多收货单」取代 |
| `CONFIRMED` 判据 | **整张采购单是否收齐** | **本收货单是否已提交** | 修 A-D5：收货单状态不应由别的单据进度决定 |
| 允许部分行提交 | 允许（`validateLines` 只校验请求中的行） | **要求本次提交覆盖本收货单全部行** | 修 A-D5/G11：避免「确认了但仍有 0 数量行」的歧义 |

**`confirm` 的完整语义（W5）**

```text
前置：
  1. 收货单存在且 status = DRAFT
  2. 收货单 version == 请求 version
  3. 采购单存在且 status ∈ {SUBMITTED, PARTIALLY_RECEIVED}
  4. 请求行集合 == 本收货单全部活动行的集合（不允许只提交子集）—— 修 A-D5/G11

逐行：
  5. declaredQuantity > 0，scale ≤ 4，整数位 ≤ 14
  6. 标品：actualWeight / weightSource / correctionReason 必须全空
     非标品：actualWeight 必填且 > 0；weightSource 必须 == MANUAL；effectiveQuantity = actualWeight
  7. ceiling   = plannedQuantity × (1 + tolerance/100)
     remaining = ceiling − purchaseItem.receivedQuantity
     effectiveQuantity > remaining → PURCHASE_RECEIPT_OVER_RECEIVED（整笔回滚）
  8. 非标品追加 receipt_weighing_record（MANUAL）

落库（同事务）：
  9.  purchase_order_item.received_quantity += effectiveQuantity（乐观锁）
  10. purchase_receipt_item 写 received_quantity / cumulative / remaining / over / difference
      + actual_weight / weight_unit / weighing_source / correction_reason
  11. purchase_receipt → CONFIRMED + confirmed_at + received_at + operator
  12. purchase_order.status：全行 received >= planned → RECEIVED，否则 PARTIALLY_RECEIVED
  13. purchase_operation_log：RECEIPT_CONFIRM + 全量 before/after
  14. idempotency_record：complete
  15. （W6）PurchaseInventoryContract.postInbound(...) —— W5 零调用
```

### 4.4 采购需求状态（3 值，剪枝 A-D2）

```text
PENDING ──分配(部分)──→ PARTIALLY_ALLOCATED ──分配(补齐)──→ ALLOCATED
   │                          │                              │
   └──────分配(全部)───────────┘                              │
        allocated = required                                  │
```

| 状态 | 含义 |
| --- | --- |
| `PENDING` | `allocated_quantity = 0` |
| `PARTIALLY_ALLOCATED` | `0 < allocated_quantity < required_quantity` |
| `ALLOCATED` | `allocated_quantity = required_quantity` |

**与 A 源的差异**：删除 `FULFILLED` / `CANCELLED`（A 源无写入路径，A-D2）。

---

## 5. 数据表与字段快照

### 5.1 表清单（9 张，全部新增）

| # | 表 | 归属 | 说明 |
| --- | --- | --- | --- |
| 1 | `warehouse` | 仓库域 | 最小主数据（Q1） |
| 2 | `purchase_demand` | 采购域 | 采购需求（来源销售订单行） |
| 3 | `purchase_demand_allocation` | 采购域 | 需求 ↔ 采购单行 的数量分配 |
| 4 | `purchase_order` | 采购域 | 采购单头 |
| 5 | `purchase_order_item` | 采购域 | 采购单行（含快照 + 累计已收） |
| 6 | `purchase_receipt` | 采购域 | 收货单头 |
| 7 | `purchase_receipt_item` | 采购域 | 收货单行（含 5 个对账数量） |
| 8 | `receipt_weighing_record` | 采购域 | 称重记录（MANUAL） |
| 9 | `purchase_operation_log` | 采购域 | 操作日志（全量前后快照） |

**序列（2 个）**：`purchase_order_no_seq` · `purchase_receipt_no_seq`

**被复用的既有表（不新建）**

```text
supplier              （W2，V8）
supplier_sku          （W2，V8）—— W5 只读 + 复用，**禁止新建第二套**
product_spu / product_sku（W1，V6）—— 只读
sales_order / sales_order_item（W4，V13）—— 只读
idempotency_record    （W4，V13）—— 复用
t_menu / t_role_menu  （SmartAdmin）—— V16 追加
t_config              （SmartAdmin，V3 建）—— V15 播种 1 条采购容差参数（Q3a）
```

**W5 明确不建的表（A 源有、W5 不迁）**

| A 源表 | 不迁理由 |
| --- | --- |
| `inventory` · `inventory_movement` | W5 不实现库存（H1/H2） |
| `purchase_receipt_confirmation` · `purchase_receipt_confirmation_item` | Q7：一单一次确认，幂等走 `idempotency_record` |
| `purchase_demand_generation_batch` | A-D1：A 源零调用点的死表 |
| `sys_config` | A-D18：与 SmartAdmin 原生配置能力重复（Q3a：改用 `t_config` + `ConfigService`） |

### 5.2 `warehouse`

```sql
CREATE TABLE warehouse (
    id             BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    warehouse_code VARCHAR(64)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    address        VARCHAR(255),
    remark         VARCHAR(500),
    version        INTEGER      NOT NULL DEFAULT 0,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64),
    updated_by     VARCHAR(64),
    CONSTRAINT ck_warehouse_status  CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_warehouse_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_warehouse_code_active ON warehouse (warehouse_code) WHERE deleted = FALSE;
CREATE INDEX idx_warehouse_name   ON warehouse (name);
CREATE INDEX idx_warehouse_status ON warehouse (status) WHERE deleted = FALSE;
```

**种子（1 条，表达 G-03 单仓库）**

```sql
INSERT INTO warehouse(warehouse_code, name, status, remark)
VALUES ('WH001','默认仓库','ENABLED','G-03 单仓库口径：系统仅维护一个启用仓库')
ON CONFLICT DO NOTHING;
```

**单仓库约束的表达方式**：**种子数据 + 前端不提供新建入口**（后端 `create` 端点保留但需
`scm:warehouse:add` 权限，默认不授予业务角色）。**不**在 DB 层加「最多一行」约束
（那会让 W6/未来多仓扩展必须改约束）。

### 5.3 `purchase_demand`

```sql
CREATE TABLE purchase_demand (
    id                       BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    sales_order_id           BIGINT       NOT NULL,
    sales_order_item_id      BIGINT       NOT NULL,
    spu_id                   BIGINT       NOT NULL,
    sku_id                   BIGINT       NOT NULL,
    sales_order_no_snapshot  VARCHAR(64)  NOT NULL,
    spu_code_snapshot        VARCHAR(64)  NOT NULL,
    product_name_snapshot    VARCHAR(150) NOT NULL,
    sku_code_snapshot        VARCHAR(64)  NOT NULL,
    sku_name_snapshot        VARCHAR(150) NOT NULL,
    spec_values_snapshot     JSONB        NOT NULL DEFAULT '{}'::JSONB,
    demand_unit_snapshot     VARCHAR(32)  NOT NULL,
    product_type_snapshot    VARCHAR(20)  NOT NULL,
    required_quantity        NUMERIC(18,4) NOT NULL,
    allocated_quantity       NUMERIC(18,4) NOT NULL DEFAULT 0,
    supplier_id              BIGINT,
    warehouse_id             BIGINT,
    purchaser_id             BIGINT,
    status                   VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    demand_date              DATE         NOT NULL,
    source_confirmed_at      TIMESTAMPTZ  NOT NULL,
    version                  INTEGER      NOT NULL DEFAULT 0,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64),
    updated_by               VARCHAR(64),
    CONSTRAINT ck_purchase_demand_spec_values CHECK (jsonb_typeof(spec_values_snapshot) = 'object'),
    CONSTRAINT ck_purchase_demand_product_type CHECK (product_type_snapshot IN ('STANDARD','NON_STANDARD')),
    CONSTRAINT ck_purchase_demand_required    CHECK (required_quantity > 0),
    CONSTRAINT ck_purchase_demand_allocated   CHECK (allocated_quantity >= 0 AND allocated_quantity <= required_quantity),
    CONSTRAINT ck_purchase_demand_status      CHECK (status IN ('PENDING','PARTIALLY_ALLOCATED','ALLOCATED')),
    -- 一旦发生分配，必须同时具备 supplier 与 warehouse。
    -- 注意：未分配（allocated_quantity = 0）时**不要求**两者为空 —— generate 必填 warehouseId，
    --       新生成的需求在第一次分配前就已经带着 warehouse_id。因此这里只约束「已分配」一侧。
    CONSTRAINT ck_purchase_demand_assignment  CHECK (
        allocated_quantity = 0
        OR (supplier_id IS NOT NULL AND warehouse_id IS NOT NULL)
    ),
    CONSTRAINT ck_purchase_demand_version     CHECK (version >= 0),
    -- Q6a：demand_date 必须是 source_confirmed_at 在 Asia/Shanghai 下的日期（禁止 date(startAt)）
    CONSTRAINT ck_purchase_demand_date        CHECK (
        demand_date = (source_confirmed_at AT TIME ZONE 'Asia/Shanghai')::date)
);
CREATE UNIQUE INDEX uk_purchase_demand_source_active
    ON purchase_demand (sales_order_item_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_sales_order_id
    ON purchase_demand (sales_order_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_sku_status
    ON purchase_demand (sku_id, status) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_assignment
    ON purchase_demand (supplier_id, warehouse_id) WHERE deleted = FALSE AND supplier_id IS NOT NULL;
CREATE INDEX idx_purchase_demand_created
    ON purchase_demand (created_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_source_confirmed_at
    ON purchase_demand (source_confirmed_at) WHERE deleted = FALSE;
```

**与 A 源的差异**

| 项 | A 源 | W5 | 理由 |
| --- | --- | --- | --- |
| `fulfilled_quantity` | 有（永不写入） | **删除** | A-D2 |
| `generation_batch_id` | 有 | **删除** | A-D1 |
| `calculate_inventory` / `original_required_quantity` / `inventory_deducted_quantity` | 有（未实现） | **删除** | A-D3 · G7 |
| `target_warehouse_id` | 有 | 改名为 `warehouse_id`（与 `purchase_order.warehouse_id` 一致） | 命名一致性 |
| 状态值 | 5 值 | **3 值** | 剪枝不可达状态 |
| `source_confirmed_at` | V27 新增、**可空** | **保留 + `NOT NULL`** | 汇总区间按订单**确认时间**过滤，且 `demand_date` 由它派生（Q6a） |
| `demand_date` | `date(startAt)`（汇总窗口第一天） | **`source_confirmed_at` 在 `Asia/Shanghai` 下的 `LocalDate`** | **Q6a**：跨多日窗口不得把全部需求写成同一天；DB CHECK `ck_purchase_demand_date` 强制 |
| `purchase_unit_snapshot` | 有（**错取 `sale_unit_snapshot`**，A-D24） | 改名 **`demand_unit_snapshot`**，语义明确为**销售/需求单位**，来源 `sales_order_item.sale_unit_snapshot` | **Q17**：需求单位 ≠ 采购单位，禁止在 `allocate` 时用采购单位覆盖需求单位 |

### 5.4 `purchase_demand_allocation`

```sql
CREATE TABLE purchase_demand_allocation (
    id                     BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    purchase_demand_id     BIGINT       NOT NULL,
    purchase_order_item_id BIGINT       NOT NULL,
    sales_order_id         BIGINT       NOT NULL,
    sales_order_item_id    BIGINT       NOT NULL,
    sku_id                 BIGINT       NOT NULL,
    allocated_quantity     NUMERIC(18,4) NOT NULL,
    demand_snapshot        JSONB        NOT NULL DEFAULT '{}'::JSONB,
    version                INTEGER      NOT NULL DEFAULT 0,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(64),
    updated_by             VARCHAR(64),
    CONSTRAINT ck_purchase_demand_allocation_quantity CHECK (allocated_quantity > 0),
    CONSTRAINT ck_purchase_demand_allocation_snapshot CHECK (jsonb_typeof(demand_snapshot) = 'object'),
    CONSTRAINT ck_purchase_demand_allocation_version  CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_purchase_demand_allocation_source_active
    ON purchase_demand_allocation (purchase_order_item_id, purchase_demand_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_allocation_demand_id
    ON purchase_demand_allocation (purchase_demand_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_demand_allocation_sales_order_item_id
    ON purchase_demand_allocation (sales_order_item_id) WHERE deleted = FALSE;
```

**基数（Q13 修订后）**：`purchase_order_item` 与 `purchase_demand_allocation` 是 **1:N** ——
一行采购行（一个 SKU）可以承接**多个**需求；每个 `(purchase_order_item_id, purchase_demand_id)`
组合至多一条活动 allocation（由 `uk_purchase_demand_allocation_source_active` 强制）。
「一行多需求」由**多行 allocation** 表达，**不是**把多个需求塞进行上的一个 `demandId` 字段。
A 源的 `allocationByItemId`（`Map<itemId, allocation>` 覆盖写，A-D23）在 W5 被替换为
`Map<(itemId, demandId), allocation>`（见 §7.8）。

**`demand_snapshot` 内容（W5 规格，A 源只有 `{demandId}`）**

```json
{
  "demandId": 123,
  "salesOrderId": 9001,
  "salesOrderItemId": 9101,
  "skuId": 3001,
  "requiredQuantity": "1000.0000",
  "demandUnit": "kg",
  "skuCode": "SKU-3001",
  "skuName": "有机菠菜-500g"
}
```

### 5.5 `purchase_order`

```sql
CREATE TABLE purchase_order (
    id                       BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_no                 VARCHAR(64)  NOT NULL,
    supplier_id              BIGINT       NOT NULL,
    supplier_code_snapshot   VARCHAR(64)  NOT NULL,
    supplier_name_snapshot   VARCHAR(150) NOT NULL,
    purchaser_id             BIGINT,
    warehouse_id             BIGINT       NOT NULL,
    warehouse_code_snapshot  VARCHAR(64)  NOT NULL,
    warehouse_name_snapshot  VARCHAR(150) NOT NULL,
    planned_arrival_date     DATE,
    status                   VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    total_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    remark                   VARCHAR(500),
    cancel_reason            VARCHAR(500),
    short_close_reason       VARCHAR(500),
    submitted_at             TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    short_closed_at          TIMESTAMPTZ,
    version                  INTEGER      NOT NULL DEFAULT 0,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64),
    updated_by               VARCHAR(64),
    CONSTRAINT ck_purchase_order_status CHECK (status IN (
        'DRAFT','SUBMITTED','PARTIALLY_RECEIVED','RECEIVED','SHORT_CLOSED','CANCELLED')),
    CONSTRAINT ck_purchase_order_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_purchase_order_cancel_reason CHECK (
        status <> 'CANCELLED' OR (cancel_reason IS NOT NULL AND btrim(cancel_reason) <> '')),
    CONSTRAINT ck_purchase_order_short_close_reason CHECK (
        status <> 'SHORT_CLOSED' OR (short_close_reason IS NOT NULL AND btrim(short_close_reason) <> '')),
    -- 状态时间戳一致性（A 源没有这组约束）
    -- 状态时间戳一致性：三条约束一一对应「已提交」「已取消」「已少收关单」
    -- 注意：submitted_at 不能用 status='DRAFT' 兜底 —— DRAFT 可被直接取消，
    --       此时 submitted_at 仍为 NULL 而 status='CANCELLED'，会被该写法误拒。
    CONSTRAINT ck_purchase_order_submitted_at CHECK (
        status NOT IN ('SUBMITTED','PARTIALLY_RECEIVED','RECEIVED','SHORT_CLOSED')
        OR submitted_at IS NOT NULL),
    CONSTRAINT ck_purchase_order_cancelled_at CHECK (
        status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
    CONSTRAINT ck_purchase_order_short_closed_at CHECK (
        status <> 'SHORT_CLOSED' OR short_closed_at IS NOT NULL),
    CONSTRAINT ck_purchase_order_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_purchase_order_no_active ON purchase_order (order_no) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_order_status_supplier_warehouse
    ON purchase_order (status, supplier_id, warehouse_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_order_purchaser_created
    ON purchase_order (purchaser_id, created_at DESC) WHERE deleted = FALSE AND purchaser_id IS NOT NULL;
CREATE INDEX idx_purchase_order_created
    ON purchase_order (created_at DESC) WHERE deleted = FALSE;
```

**与 A 源的差异**：新增 3 条状态时间戳约束 —— `ck_purchase_order_submitted_at`
（`SUBMITTED` / `PARTIALLY_RECEIVED` / `RECEIVED` / `SHORT_CLOSED` 必须有 `submitted_at`；
`DRAFT` / `CANCELLED` 不要求，因为 `DRAFT` 可被**直接取消**）、`ck_purchase_order_cancelled_at`、
`ck_purchase_order_short_closed_at`，修 A 源「状态与时间戳可以不一致」的隐患。

### 5.6 `purchase_order_item`

```sql
CREATE TABLE purchase_order_item (
    id                     BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    purchase_order_id      BIGINT       NOT NULL,
    spu_id                 BIGINT       NOT NULL,
    sku_id                 BIGINT       NOT NULL,
    spu_code_snapshot      VARCHAR(64)  NOT NULL,
    product_name_snapshot  VARCHAR(150) NOT NULL,
    sku_code_snapshot      VARCHAR(64)  NOT NULL,
    sku_name_snapshot      VARCHAR(150) NOT NULL,
    spec_values_snapshot   JSONB        NOT NULL DEFAULT '{}'::JSONB,
    purchase_unit_snapshot VARCHAR(32)  NOT NULL,
    product_type_snapshot  VARCHAR(20)  NOT NULL,
    planned_quantity       NUMERIC(18,4) NOT NULL,
    received_quantity      NUMERIC(18,4) NOT NULL DEFAULT 0,
    purchase_price         NUMERIC(18,4) NOT NULL,
    line_amount            NUMERIC(18,4) NOT NULL,
    sort_order             INTEGER      NOT NULL DEFAULT 0,
    version                INTEGER      NOT NULL DEFAULT 0,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(64),
    updated_by             VARCHAR(64),
    CONSTRAINT ck_purchase_order_item_spec_values CHECK (jsonb_typeof(spec_values_snapshot) = 'object'),
    CONSTRAINT ck_purchase_order_item_product_type CHECK (product_type_snapshot IN ('STANDARD','NON_STANDARD')),
    CONSTRAINT ck_purchase_order_item_planned_quantity  CHECK (planned_quantity > 0),
    CONSTRAINT ck_purchase_order_item_received_quantity CHECK (received_quantity >= 0),
    CONSTRAINT ck_purchase_order_item_purchase_price    CHECK (purchase_price >= 0),
    CONSTRAINT ck_purchase_order_item_line_amount       CHECK (line_amount >= 0),
    CONSTRAINT ck_purchase_order_item_version           CHECK (version >= 0)
);
CREATE INDEX idx_purchase_order_item_order_id
    ON purchase_order_item (purchase_order_id, sort_order) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_order_item_sku_id
    ON purchase_order_item (sku_id) WHERE deleted = FALSE;
-- Q13 / M1：一行一 SKU（与 W4 的 uk_sales_order_item_order_sku_active 同构）—— 修订后保留
CREATE UNIQUE INDEX uk_purchase_order_item_order_sku_active
    ON purchase_order_item (purchase_order_id, sku_id) WHERE deleted = FALSE;
```

**Q13 修订后的行身份与 allocation 身份（必须区分）**

```text
行身份        = (purchase_order_id, sku_id)                       uk_purchase_order_item_order_sku_active（保留）
allocation 身份 = (purchase_order_item_id, purchase_demand_id)      uk_purchase_demand_allocation_source_active
一行多需求     = 该行下挂 N 条 purchase_demand_allocation（每行一个 demandId）
```

**关键裁决记录**：**不引入 `received_quantity <= planned_quantity` 约束**。
A 源 V11 加过它，V33 又删掉了（容差内超收合法）。W5 的超收上限是**运行时**计算
（`planned × (1 + tolerance/100)`），不能表达为静态 CHECK。这是**已知的 DB 级缺口**，
由 `PurchaseReceiptQuantityCalculator` + `PURCHASE_RECEIPT_OVER_RECEIVED` 在服务层强制。

### 5.7 `purchase_receipt`

```sql
CREATE TABLE purchase_receipt (
    id                        BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_no                VARCHAR(64)  NOT NULL,
    purchase_order_id         BIGINT       NOT NULL,
    purchase_order_no_snapshot VARCHAR(64) NOT NULL,
    supplier_id               BIGINT       NOT NULL,
    supplier_code_snapshot    VARCHAR(64)  NOT NULL,
    supplier_name_snapshot    VARCHAR(150) NOT NULL,
    warehouse_id              BIGINT       NOT NULL,
    warehouse_code_snapshot   VARCHAR(64)  NOT NULL,
    warehouse_name_snapshot   VARCHAR(150) NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    received_at               TIMESTAMPTZ,
    confirmed_at              TIMESTAMPTZ,
    operator                  VARCHAR(64),
    remark                    VARCHAR(500),
    version                   INTEGER      NOT NULL DEFAULT 0,
    deleted                   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64),
    updated_by                VARCHAR(64),
    CONSTRAINT ck_purchase_receipt_status CHECK (status IN ('DRAFT','CONFIRMED')),
    CONSTRAINT ck_purchase_receipt_confirmation CHECK (
        (status = 'DRAFT'     AND confirmed_at IS NULL     AND operator IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND operator IS NOT NULL AND btrim(operator) <> '')),
    CONSTRAINT ck_purchase_receipt_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_purchase_receipt_no_active ON purchase_receipt (receipt_no) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_order_created
    ON purchase_receipt (purchase_order_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_warehouse_received
    ON purchase_receipt (warehouse_id, received_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_status_created
    ON purchase_receipt (status, created_at DESC) WHERE deleted = FALSE;
```

**与 A 源的差异**

| 项 | A 源 | W5 | 理由 |
| --- | --- | --- | --- |
| `receipt_mode` | V27 有（`DIRECT`/`DEFERRED`） | **不建** | Q4：W5 只做 `DIRECT`；建一个恒为 `DIRECT` 的字段等于死字段（同 W4 拒绝 `fulfillment_status` 的纪律） |
| `putaway_status` / `putaway_at` / `putaway_operator` | V27 有 | **不建** | 这三列描述「库存投递状态」，属库存域语义；W5 不实现库存 → 建了必然是假值。W6 用 `ALTER TABLE` 追加 |
| `reason` | V27 有 | 用 `remark` 代替 | 与 V2 其它表命名一致 |
| `supplier_id` + 供应商快照 | **无** | **新增** | A 源只存采购单号快照；收货单直接挂供应商快照更利于查询与审计 |
| `operator` | A 源有 | 保留，但由 `ScmOperator.current()` 写入 | A-D12 |

### 5.8 `purchase_receipt_item`

```sql
CREATE TABLE purchase_receipt_item (
    id                             BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    purchase_receipt_id            BIGINT       NOT NULL,
    purchase_order_item_id         BIGINT       NOT NULL,
    sku_id                         BIGINT       NOT NULL,
    spu_code_snapshot              VARCHAR(64)  NOT NULL,
    product_name_snapshot          VARCHAR(150) NOT NULL,
    sku_code_snapshot              VARCHAR(64)  NOT NULL,
    sku_name_snapshot              VARCHAR(150) NOT NULL,
    spec_values_snapshot           JSONB        NOT NULL DEFAULT '{}'::JSONB,
    purchase_unit_snapshot         VARCHAR(32)  NOT NULL,
    product_type_snapshot          VARCHAR(20)  NOT NULL,
    planned_quantity               NUMERIC(18,4) NOT NULL,
    received_quantity              NUMERIC(18,4) NOT NULL DEFAULT 0,
    cumulative_received_quantity   NUMERIC(18,4) NOT NULL DEFAULT 0,
    remaining_quantity             NUMERIC(18,4) NOT NULL DEFAULT 0,
    over_receipt_quantity          NUMERIC(18,4) NOT NULL DEFAULT 0,
    receipt_difference             NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_weight                  NUMERIC(18,4),
    weight_unit                    VARCHAR(32),
    weighing_source                VARCHAR(20),
    correction_reason              VARCHAR(500),
    sort_order                     INTEGER      NOT NULL DEFAULT 0,
    version                        INTEGER      NOT NULL DEFAULT 0,
    deleted                        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                     VARCHAR(64),
    updated_by                     VARCHAR(64),
    CONSTRAINT ck_purchase_receipt_item_spec_values  CHECK (jsonb_typeof(spec_values_snapshot) = 'object'),
    CONSTRAINT ck_purchase_receipt_item_product_type CHECK (product_type_snapshot IN ('STANDARD','NON_STANDARD')),
    CONSTRAINT ck_purchase_receipt_item_planned      CHECK (planned_quantity > 0),
    CONSTRAINT ck_purchase_receipt_item_received     CHECK (received_quantity >= 0),
    CONSTRAINT ck_purchase_receipt_item_actual_weight CHECK (actual_weight IS NULL OR actual_weight > 0),
    CONSTRAINT ck_purchase_receipt_item_weight_source CHECK (weighing_source IS NULL OR weighing_source = 'MANUAL'),
    -- 三字段同生同灭（与 V2 的 draft_* / locked_* 对偶纪律一致）
    CONSTRAINT ck_purchase_receipt_item_weight_fields CHECK (
        (actual_weight IS NULL AND weight_unit IS NULL AND weighing_source IS NULL)
        OR (actual_weight IS NOT NULL AND weight_unit IS NOT NULL AND btrim(weight_unit) <> ''
            AND weighing_source IS NOT NULL)),
    -- P24：5 个对账数量的恒等式（A 源 V28 的约束，逐字保留语义）
    CONSTRAINT ck_purchase_receipt_item_reconciliation CHECK (
        cumulative_received_quantity >= 0
        AND remaining_quantity = GREATEST(planned_quantity - cumulative_received_quantity, 0)
        AND over_receipt_quantity = GREATEST(cumulative_received_quantity - planned_quantity, 0)
        AND receipt_difference = cumulative_received_quantity - planned_quantity),
    CONSTRAINT ck_purchase_receipt_item_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_purchase_receipt_item_receipt_order_item_active
    ON purchase_receipt_item (purchase_receipt_id, purchase_order_item_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_item_receipt_id
    ON purchase_receipt_item (purchase_receipt_id, sort_order) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_item_order_item_id
    ON purchase_receipt_item (purchase_order_item_id) WHERE deleted = FALSE;
CREATE INDEX idx_purchase_receipt_item_sku_id
    ON purchase_receipt_item (sku_id) WHERE deleted = FALSE;
```

**与 A 源的差异**：`actual_weight` 由 A 源的 `NUMERIC`（无 scale，V9 去掉了 scale）改为
**`NUMERIC(18,4)`**，与 V2 冻结的 4 位定点纪律一致（P20）。

### 5.9 `receipt_weighing_record`

```sql
CREATE TABLE receipt_weighing_record (
    id                        BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    purchase_receipt_item_id  BIGINT       NOT NULL,
    raw_reading               NUMERIC(18,4) NOT NULL,
    confirmed_reading         NUMERIC(18,4) NOT NULL,
    unit                      VARCHAR(32)  NOT NULL,
    source                    VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    device_session_id         VARCHAR(128),
    modification_reason       VARCHAR(500),
    recorded_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator                  VARCHAR(64)  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64),
    CONSTRAINT ck_receipt_weighing_record_raw       CHECK (raw_reading > 0),
    CONSTRAINT ck_receipt_weighing_record_confirmed CHECK (confirmed_reading > 0),
    CONSTRAINT ck_receipt_weighing_record_source    CHECK (source = 'MANUAL')
);
CREATE INDEX idx_receipt_weighing_record_item_recorded
    ON receipt_weighing_record (purchase_receipt_item_id, recorded_at DESC);
```

**与 A 源的差异**

| 项 | A 源 | W5 | 理由 |
| --- | --- | --- | --- |
| `scale_precision` | 有（`confirmLine` 从不赋值） | **删除** | A-D4：W5 无设备精度来源（G-05 手工录入） |
| `version` / `deleted` / `updated_*` | 有 | **删除** | 称重记录是**只追加审计事实**，与 A 源自己的 `inventory_movement` 只追加纪律一致（V11 的触发器） |
| `source` | `MANUAL` | `MANUAL`（CHECK 收紧） | 保留 `DEVICE` 作为 **G-05 的扩展点**，W6+ 加 CHECK 时再放宽 |

### 5.10 `purchase_operation_log`

```sql
CREATE TABLE purchase_operation_log (
    id                  BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    purchase_order_id   BIGINT,                      -- Q14：可空（仅 DEMAND_GENERATE 允许为空）
    purchase_receipt_id BIGINT,
    operation_type      VARCHAR(40)  NOT NULL,
    operator            VARCHAR(64)  NOT NULL,
    reason              VARCHAR(500),
    before_data         JSONB,
    after_data          JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    CONSTRAINT ck_purchase_operation_log_type CHECK (operation_type IN (
        'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE',
        'DEMAND_GENERATE','DEMAND_ALLOCATE',
        'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE')),
    -- Q14：日志归属由 operation_type 决定，不是简单的「至少一个 id 非空」——
    --      DEMAND_GENERATE 时采购单与收货单都还不存在，两个 id 必须同时为空。
    CONSTRAINT ck_purchase_operation_log_owner CHECK (
        (operation_type = 'DEMAND_GENERATE'
             AND purchase_order_id IS NULL     AND purchase_receipt_id IS NULL)
     OR (operation_type = 'DEMAND_ALLOCATE'
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
     OR (operation_type IN ('CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE')
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
     OR (operation_type IN ('RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE')
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NOT NULL)),
    CONSTRAINT ck_purchase_operation_log_before CHECK (before_data IS NULL OR jsonb_typeof(before_data) = 'object'),
    CONSTRAINT ck_purchase_operation_log_after  CHECK (after_data  IS NULL OR jsonb_typeof(after_data)  = 'object')
);
CREATE INDEX idx_purchase_operation_log_order_created
    ON purchase_operation_log (purchase_order_id, created_at DESC);
CREATE INDEX idx_purchase_operation_log_receipt_created
    ON purchase_operation_log (purchase_receipt_id, created_at DESC) WHERE purchase_receipt_id IS NOT NULL;
CREATE INDEX idx_purchase_operation_log_type_created
    ON purchase_operation_log (operation_type, created_at DESC);
```

**与 A 源的差异**：`purchase_order_id` 由 `NOT NULL` 改为**可空**（Q14）；新增 `operation_type`
白名单 CHECK（修 A-D16）与 **operation-type-aware 归属 CHECK** `ck_purchase_operation_log_owner`
（Q14：`DEMAND_GENERATE` 双 id 为空 · `DEMAND_ALLOCATE` 只有采购单 id · `RECEIPT_*` 双 id 非空）；
`before_data` / `after_data` 写**全量快照**（修 A-D15），格式与 W4 的 `order_operation_log` 一致：

```json
{
  "header": { "status": "SUBMITTED", "version": 1, "totalAmount": "6200.0000" },
  "items":  [ { "id": 11, "skuId": 3001, "plannedQuantity": "1000.0000",
                "receivedQuantity": "0.0000", "purchasePrice": "6.2000" } ]
}
```

### 5.11 快照矩阵（用户明确要求调查的四类快照）

| 快照类别 | 落点 | 冻结时点 | 说明 |
| --- | --- | --- | --- |
| **Supplier snapshot** | `purchase_order.supplier_code_snapshot` / `supplier_name_snapshot` | **创建/编辑时**（DRAFT 可刷新） | `submit` 后永不回读主数据（P4） |
| | `purchase_receipt.supplier_code_snapshot` / `supplier_name_snapshot` | **收货单创建时** | 从采购单继承 |
| **SKU snapshot** | `purchase_order_item` 的 `spu_code` / `product_name` / `sku_code` / `sku_name` / `spec_values` / `product_type` | **创建/编辑时** | 采购单行是交易单位（`sku_id`） |
| | `purchase_receipt_item` 同上 + `purchase_unit` | **收货单创建时** | 从采购单行继承 |
| | `purchase_demand` 同上 + `sales_order_no` | **需求生成时** | 从订单行继承 |
| **Demand unit snapshot** | `purchase_demand.demand_unit_snapshot`（来源 `sales_order_item.sale_unit_snapshot`） | **需求生成时** | **Q17**：需求单位是**销售单位**，永不被采购单位覆盖 |
| **Purchase unit snapshot** | `purchase_order_item.purchase_unit_snapshot`（来源 `supplier_sku.purchase_unit`） | **创建/编辑时** | 供应商-商品关系可改，历史单据不受影响 |
| | `purchase_receipt_item.purchase_unit_snapshot` | 收货单创建时 | 继承 |
| **Purchase price snapshot** | `purchase_order_item.purchase_price` + `line_amount` | **创建/编辑时**（人工确认值） | `supplier_sku.reference_price` 只作**建议值**，不自动写入（K7） |
| | `purchase_receipt_item` **不存价格** | — | 收货只记数量/重量；金额由采购行派生（A-D8） |
| **Warehouse snapshot** | `purchase_order` + `purchase_receipt` 的 `warehouse_code` / `warehouse_name` | 创建时 / 继承 | W5 新增（A 源已有） |
| **Sales order snapshot** | `purchase_demand.sales_order_no_snapshot` | 需求生成时 | 来源追溯 |

**明确不做的快照**：`customer`（采购不涉及客户）、`category`（C 的 `category_id` 不迁，K8）。

---

## 6. Migration 设计

### 6.1 命名与版本

```text
V15__scm_purchase.sql              采购域 9 表 + 2 序列 + 2 条种子（warehouse 1 条 + t_config 1 条）
V16__scm_purchase_permissions.sql  25 条 t_menu + t_role_menu + setval
```

**V1–V14 禁止修改**（已冻结，`docs/architecture/pg-closure-applied-migrations.sha256` 覆盖 V1–V12，
W4 的 `w4-applied-migrations.sha256` 覆盖 V13–V14；W5 新增 `w5-applied-migrations.sha256` 覆盖 V1–V16）。

### 6.2 `V15__scm_purchase.sql` 结构

```text
1.  文件头注释（scope / 无外键 / G-03 单仓库 / Q 裁决编号）
2.  CREATE SEQUENCE purchase_order_no_seq
3.  CREATE SEQUENCE purchase_receipt_no_seq
4.  CREATE TABLE warehouse                    + 3 索引 + 1 条种子
4b. t_config 种子 1 条（Q3a：scm.purchase.over_receipt_tolerance_percent = 10，ON CONFLICT DO NOTHING）
5.  CREATE TABLE purchase_demand              + 6 索引
6.  CREATE TABLE purchase_demand_allocation   + 3 索引
7.  CREATE TABLE purchase_order               + 4 索引
8.  CREATE TABLE purchase_order_item          + 3 索引
9.  CREATE TABLE purchase_receipt             + 4 索引
10. CREATE TABLE purchase_receipt_item        + 4 索引
11. CREATE TABLE receipt_weighing_record      + 1 索引
12. CREATE TABLE purchase_operation_log       + 3 索引
13. COMMENT ON TABLE/COLUMN（每张表每个字段，与 V13 同风格）
```

**合计**：9 表 · 31 索引（其中 **27 个部分索引**（含 `deleted = FALSE` 或语义等价谓词）、**4 个普通索引**、
**7 个唯一索引**，7 个唯一索引全部是部分唯一）· 2 序列 · **2 条种子**（`warehouse` 1 条 + `t_config` 1 条）· 无外键。

### 6.3 `V16__scm_purchase_permissions.sql` 结构

**菜单 id 分配（W5 = `701+`）**

```text
W1 = 401–424 · W2 = 431–487 · W3 = 425 / 435 / 486–487 / 501–541 · W4 = 601–641 · W5 = 701–753
```

| menu_id | menu_name | type | parent | path | component / perms |
| --- | --- | --- | --- | --- | --- |
| 701 | 采购管理 | 1 | 0 | `/purchase` | — |
| 702 | 采购需求 | 2 | 701 | `/purchase/purchase-demand-list` | `/business/scm/purchase/purchase-demand-list.vue` |
| 703 | 采购订单 | 2 | 701 | `/purchase/purchase-order-list` | `/business/scm/purchase/purchase-order-list.vue` |
| 704 | 采购收货 | 2 | 701 | `/purchase/purchase-receipt-list` | `/business/scm/purchase/purchase-receipt-list.vue` |
| 705 | 采购日志 | 2 | 701 | `/purchase/purchase-log-list` | `/business/scm/purchase/purchase-log-list.vue` |
| 706 | 仓库管理 | 2 | 701 | `/purchase/warehouse-list` | `/business/scm/purchase/warehouse-list.vue` |
| 711 | 查询 | 3 | 702 | — | `scm:purchase:demand:query` |
| 712 | 生成 | 3 | 702 | — | `scm:purchase:demand:generate` |
| 713 | 分配 | 3 | 702 | — | `scm:purchase:demand:allocate` |
| 721 | 查询 | 3 | 703 | — | `scm:purchase:query` |
| 722 | 新建 | 3 | 703 | — | `scm:purchase:add` |
| 723 | 编辑 | 3 | 703 | — | `scm:purchase:update` |
| 724 | 提交 | 3 | 703 | — | `scm:purchase:submit` |
| 725 | 取消 | 3 | 703 | — | `scm:purchase:cancel` |
| 726 | 少收关单 | 3 | 703 | — | `scm:purchase:short-close` |
| 727 | 删除 | 3 | 703 | — | `scm:purchase:delete` |
| 731 | 查询 | 3 | 704 | — | `scm:purchase:receipt:query` |
| 732 | 新建 | 3 | 704 | — | `scm:purchase:receipt:add` |
| 733 | 编辑 | 3 | 704 | — | `scm:purchase:receipt:update` |
| 734 | 确认 | 3 | 704 | — | `scm:purchase:receipt:confirm` |
| 735 | 删除 | 3 | 704 | — | `scm:purchase:receipt:delete` |
| 741 | 查询 | 3 | 705 | — | `scm:purchase:log:query` |
| 751 | 查询 | 3 | 706 | — | `scm:warehouse:query` |
| 752 | 新建 | 3 | 706 | — | `scm:warehouse:add` |
| 753 | 编辑 | 3 | 706 | — | `scm:warehouse:update` |

**共 25 条菜单**（6 个页面/分组 + 19 个权限点）。

```sql
-- 授权给 role_id = 1（与 W1–W4 一致）
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (701,702,703,704,705,706,
                    711,712,713,
                    721,722,723,724,725,726,727,
                    731,732,733,734,735,
                    741,
                    751,752,753)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
```

**权限码与端点对照（19 个权限码必须 19/19 命中 `@SaCheckPermission`）**

| 权限码 | 端点 |
| --- | --- |
| `scm:purchase:demand:query` | `POST /scm/purchase/demand/query` |
| `scm:purchase:demand:generate` | `POST /scm/purchase/demand/generate` |
| `scm:purchase:demand:allocate` | `POST /scm/purchase/demand/allocate` |
| `scm:purchase:query` | `POST /scm/purchase/query` · `GET /scm/purchase/detail/{id}` · `GET /scm/purchase/item/{orderId}` |
| `scm:purchase:add` | `POST /scm/purchase/create` |
| `scm:purchase:update` | `POST /scm/purchase/update` |
| `scm:purchase:submit` | `POST /scm/purchase/submit` |
| `scm:purchase:cancel` | `POST /scm/purchase/cancel` |
| `scm:purchase:short-close` | `POST /scm/purchase/short-close` |
| `scm:purchase:delete` | `POST /scm/purchase/delete` · `POST /scm/purchase/batch-delete` |
| `scm:purchase:receipt:query` | `POST /scm/purchase/receipt/query` · `GET /scm/purchase/receipt/detail/{id}` · `GET /scm/purchase/receipt/item/{receiptId}` |
| `scm:purchase:receipt:add` | `POST /scm/purchase/receipt/create` |
| `scm:purchase:receipt:update` | `POST /scm/purchase/receipt/update` |
| `scm:purchase:receipt:confirm` | `POST /scm/purchase/receipt/confirm` |
| `scm:purchase:receipt:delete` | `POST /scm/purchase/receipt/delete` · `POST /scm/purchase/receipt/batch-delete` |
| `scm:purchase:log:query` | `GET /scm/purchase/log/{orderId}` |
| `scm:warehouse:query` | `GET /scm/warehouse/list` · `POST /scm/warehouse/query` · `GET /scm/warehouse/detail/{id}` |
| `scm:warehouse:add` | `POST /scm/warehouse/create` |
| `scm:warehouse:update` | `POST /scm/warehouse/update` |

### 6.4 PostgreSQL 索引设计（逐条给理由）

| 索引 | 类型 | 理由 |
| --- | --- | --- |
| `uk_warehouse_code_active` | 部分唯一 | 活动编码唯一；已删记录不阻塞复用（V2 全库纪律） |
| `idx_warehouse_status` | 部分 | 只查启用仓库（W5 唯一查询形态） |
| `uk_purchase_demand_source_active` | **部分唯一** | **P8 的唯一强制手段**：每个活动订单行至多一条活动需求（并发靠 INSERT 竞争收敛） |
| `idx_purchase_demand_sales_order_id` | 部分 | 「按订单查需求」是详情页主查询 |
| `idx_purchase_demand_sku_status` | 部分 | 「按 SKU 查未分配需求」是分配页主查询 |
| `idx_purchase_demand_assignment` | 部分 | 按 (supplier, warehouse) 找可汇总需求；`supplier_id IS NOT NULL` 排除未分配 |
| `idx_purchase_demand_source_confirmed_at` | 部分 | 汇总区间按**订单确认时间**过滤（P1 口径） |
| `uk_purchase_demand_allocation_source_active` | **部分唯一** | **P11**：`(item, demand)` 唯一；配合服务端数量校验实现「同组合不同数量 → 冲突」 |
| `idx_purchase_demand_allocation_sales_order_item_id` | 部分 | 来源追溯：从订单行反查采购来源 |
| `uk_purchase_order_no_active` | 部分唯一 | 单号唯一 |
| `idx_purchase_order_status_supplier_warehouse` | 部分 | 列表页按状态/供应商/仓库筛选（最常用组合） |
| `idx_purchase_order_purchaser_created` | 部分 | 「我的采购单」按创建倒序；`purchaser_id IS NOT NULL` 排除未指派 |
| `idx_purchase_order_created` | 部分 | 无筛选时的默认排序 |
| `idx_purchase_order_item_order_id` | 部分 | 详情页按 `sort_order` 稳定排序 |
| `uk_purchase_order_item_order_sku_active` | **部分唯一** | **Q13/M1 的核心**：一行一 SKU，让差量同步的行身份稳定 |
| `uk_purchase_receipt_no_active` | 部分唯一 | 单号唯一 |
| `idx_purchase_receipt_order_created` | 部分 | 「某采购单的所有收货单」是详情页主查询（**注意：不能唯一**，P16 要求一单多收货单） |
| `idx_purchase_receipt_warehouse_received` | 部分 | 按仓库查收货历史 |
| `idx_purchase_receipt_status_created` | 部分 | 「待确认收货单」列表 |
| `uk_purchase_receipt_item_receipt_order_item_active` | **部分唯一** | 一张收货单对同一采购行只有一行（防止重复行导致重复累计） |
| `idx_purchase_receipt_item_order_item_id` | 部分 | 从采购行反查收货历史 |
| `idx_receipt_weighing_record_item_recorded` | 普通（表本身只追加） | 称重审计按时间倒序 |
| `idx_purchase_operation_log_order_created` | 普通 | 日志页主查询 |
| `idx_purchase_operation_log_receipt_created` | 部分 | 按收货单查日志 |
| `idx_purchase_operation_log_type_created` | 普通 | 按操作类型审计 |

> **与 §5 的一致性**：§5.2–§5.10 的 DDL 中共 **31** 条 `CREATE INDEX`（实测计数），
> 上表列出其中 **25** 条**需要给出理由**的索引；另 6 条为无争议的常规外键列索引，
> 不重复论证：`idx_warehouse_name` · `idx_purchase_demand_created` ·
> `idx_purchase_demand_allocation_demand_id` · `idx_purchase_order_item_sku_id` ·
> `idx_purchase_receipt_item_receipt_id` · `idx_purchase_receipt_item_sku_id`。
> 索引总账以 §5 的 DDL 为**唯一事实源**（T2 的 `ScmPurchaseMigrationIT` 会逐条断言 31 条存在）。

### 6.5 不引入的 DB 级约束（**明确记录为已知风险**）

| 缺口 | 为什么不加 | 服务层补偿 |
| --- | --- | --- |
| `purchase_order_item.received_quantity <= planned × (1+tolerance)` | 容差是**运行时配置**（`t_config` 可改），静态 CHECK 无法表达 | `PurchaseReceiptQuantityCalculator` + `PURCHASE_RECEIPT_OVER_RECEIVED` |
| `purchase_order.total_amount = Σ line_amount` | 跨行聚合约束，PG 无法用 CHECK 表达（需触发器） | `PurchaseAmountCalculator`（唯一写入口）+ 单测 |
| `purchase_receipt.warehouse_id = purchase_order.warehouse_id` | 跨表约束 | `PurchaseReceiptService.create` 强制继承 + IT 断言 |
| 外键（`supplier_id` / `sku_id` / `warehouse_id` / `purchase_order_id` …） | **`AGENTS.md` 明文禁止外键** | 服务层 `PurchaseWarehouseReferenceGuard` / `PurchaseOrderValidator` |
| 「同一供应商同一 SKU 至多一条 `is_default`」 | **W2 R12 明文禁止**（legacy 允许重复） | 不实现 |

---

## 7. 后端设计

### 7.1 API 契约（统一 `/scm/purchase/**` + `/scm/warehouse/**`）

**采购需求**

```http
POST /scm/purchase/demand/query        scm:purchase:demand:query
POST /scm/purchase/demand/generate     scm:purchase:demand:generate    Idempotency-Key
POST /scm/purchase/demand/allocate     scm:purchase:demand:allocate    Idempotency-Key
```

**采购单**

```http
POST /scm/purchase/query               scm:purchase:query
GET  /scm/purchase/detail/{id}         scm:purchase:query
GET  /scm/purchase/item/{orderId}      scm:purchase:query
GET  /scm/purchase/log/{orderId}       scm:purchase:log:query
POST /scm/purchase/create              scm:purchase:add        Idempotency-Key
POST /scm/purchase/update              scm:purchase:update
POST /scm/purchase/submit              scm:purchase:submit     Idempotency-Key
POST /scm/purchase/cancel              scm:purchase:cancel     Idempotency-Key
POST /scm/purchase/short-close         scm:purchase:short-close Idempotency-Key
POST /scm/purchase/delete              scm:purchase:delete
POST /scm/purchase/batch-delete        scm:purchase:delete
```

**采购收货**

```http
POST /scm/purchase/receipt/query       scm:purchase:receipt:query
GET  /scm/purchase/receipt/detail/{id} scm:purchase:receipt:query
GET  /scm/purchase/receipt/item/{receiptId}  scm:purchase:receipt:query
POST /scm/purchase/receipt/create      scm:purchase:receipt:add      Idempotency-Key
POST /scm/purchase/receipt/update      scm:purchase:receipt:update
POST /scm/purchase/receipt/confirm     scm:purchase:receipt:confirm  Idempotency-Key
POST /scm/purchase/receipt/delete      scm:purchase:receipt:delete
POST /scm/purchase/receipt/batch-delete scm:purchase:receipt:delete
```

**仓库**

```http
GET  /scm/warehouse/list               scm:warehouse:query
POST /scm/warehouse/query              scm:warehouse:query
GET  /scm/warehouse/detail/{id}        scm:warehouse:query
POST /scm/warehouse/create             scm:warehouse:add
POST /scm/warehouse/update             scm:warehouse:update
```

**共 27 个端点**（需求 3 · 采购单 11 · 收货 8 · 仓库 5）。

**统一约定**

| 项 | 约定 |
| --- | --- |
| 响应 | `ResponseDTO<T>` / `ResponseDTO<PageResult<T>>`（SmartAdmin 原生） |
| 业务冲突 | HTTP 200 + `ResponseDTO.code = 409xx`（与 W4 一致） |
| 幂等头 | `Idempotency-Key`，最大 200 字符 |
| 乐观锁 | 请求体携带 `version` |
| 定点数 | 请求/响应均为 **4 位小数字符串**；`null` 保留语义（不写 0） |
| 方法 | 查询 `POST`（表单体）+ 详情 `GET`；写操作一律 `POST` |

### 7.2 Form / VO 字段

**Form**

```text
WarehouseQueryForm     warehouseCode · name · status · pageNum · pageSize
WarehouseAddForm       warehouseCode · name · address · remark
WarehouseUpdateForm    id · warehouseCode · name · address · remark · version

PurchaseDemandQueryForm     salesOrderNo · skuId · supplierId · warehouseId · status · demandDateFrom/To · page*
PurchaseDemandGenerateForm  startAt · endAt · warehouseId · supplierId(可选) · purchaserId(可选)
PurchaseDemandAllocateForm  demandId · purchaseOrderItemId · quantity · supplierId · warehouseId · version(=需求版本)

PurchaseOrderQueryForm   orderNo · supplierId · purchaserId · warehouseId · status · createdFrom/To · page*
PurchaseOrderAddForm     supplierId · purchaserId · warehouseId · plannedArrivalDate · remark
                         · items[] { skuId · quantity · price
                                     · allocations[] { demandId · quantity · demandVersion } }
PurchaseOrderUpdateForm  id · version · supplierId · purchaserId · warehouseId · plannedArrivalDate · remark
                         · items[] { id(可空) · version(保留时必填) · skuId · quantity · price
                                     · allocations[] { demandId · quantity · demandVersion } }
PurchaseOrderVersionForm id · version
PurchaseOrderCancelForm  id · version · cancelReason
PurchaseOrderShortCloseForm id · version · shortCloseReason
PurchaseOrderDeleteForm  id
PurchaseOrderBatchDeleteForm orders[]

PurchaseReceiptQueryForm   receiptNo · purchaseOrderId · supplierId · warehouseId · status · receivedFrom/To · page*
PurchaseReceiptCreateForm  purchaseOrderId · remark
PurchaseReceiptUpdateForm  id · version · remark
PurchaseReceiptConfirmForm id · version · items[] {
                               receiptItemId · version · receivedQuantity
                               · actualWeight(非标品必填) · weightSource(非标品必填=MANUAL)
                               · correctionReason(可空) }
PurchaseReceiptDeleteForm  id
PurchaseReceiptBatchDeleteForm receipts[]
```

**VO**

```text
WarehouseVO   id · warehouseCode · name · status · address · remark · version · createdAt · updatedAt

PurchaseDemandVO   id · salesOrderId · salesOrderNoSnapshot · salesOrderItemId · skuId · skuCode · skuName
                   · productName · specValues · demandUnit · productType · requiredQuantity · allocatedQuantity
                   · unallocatedQuantity(= required − allocated) · supplierId · supplierName
                   · warehouseId · warehouseName · status · demandDate · sourceConfirmedAt · version · createdAt

PurchaseOrderVO    id · orderNo · supplierId · supplierCode · supplierName · purchaserId · purchaserName
                   · warehouseId · warehouseCode · warehouseName · plannedArrivalDate · status
                   · totalAmount · receivedProgress(汇总进度) · remark · cancelReason
                   · shortCloseReason · submittedAt · cancelledAt · shortClosedAt
                   （`purchase_order` **没有** `confirmed_at` 列；收货完成时间见各收货单的 `confirmed_at`）
                   · version · createdAt · updatedAt
                   · items[]  · allocations[]  · logs[]（仅 detail 返回）

PurchaseOrderItemVO  id · skuId · spuCode · productName · skuCode · skuName · specValues
                     · purchaseUnit · productType · plannedQuantity · receivedQuantity
                     · remainingQuantity(= max(planned − received, 0))
                     · overReceiptQuantity(= max(received − planned, 0))
                     · purchasePrice · lineAmount · sortOrder · version
                     · allocations[]  ← PurchaseOrderAllocationVO

PurchaseOrderAllocationVO  allocationId · demandId · salesOrderId · salesOrderNo · salesOrderItemId
                           · skuId · quantity · demandUnit · demandVersion · demandStatus

PurchaseReceiptVO    id · receiptNo · purchaseOrderId · purchaseOrderNo · supplierId · supplierName
                     · warehouseId · warehouseName · status · receivedAt · confirmedAt · operator
                     · remark · version · createdAt · updatedAt
                     · items[]

PurchaseReceiptItemVO  id · purchaseOrderItemId · skuId · skuCode · skuName · specValues
                       · purchaseUnit · productType · plannedQuantity · receivedQuantity
                       · cumulativeReceivedQuantity · remainingQuantity · overReceiptQuantity
                       · receiptDifference · actualWeight · weightUnit · weighingSource
                       · correctionReason · version

PurchaseOperationLogVO  id · purchaseOrderId · purchaseReceiptId · operationType · operator
                        · reason · beforeData · afterData · createdAt
```

**定点数字段（必须同时声明 `using` + `nullsUsing`）**

```java
@JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
private BigDecimal plannedQuantity;
```

适用字段：所有数量（`required` / `allocated` / `allocationQuantity` / `unallocated` / `planned` / `received` /
`cumulative` / `remaining` / `overReceipt` / `receiptDifference` / `actualWeight`）、
所有金额（`purchasePrice` / `lineAmount` / `totalAmount`）。
**可空且语义为「无值」**：`actualWeight`（标品为 `null`，不是 `0.0000`）。

### 7.3 权限（**19 个权限码**，见 §6.3；另有 6 个页面/分组菜单不带 perms，合计 25 条菜单）

**授权策略**：全部授予 `role_id = 1`（与 W1–W4 一致）。
`scm:warehouse:add` / `scm:warehouse:update` **保留但建议不授予业务角色**（G-03 单仓库）。

### 7.4 采购需求集成（**核心**）

**生成（`PurchaseDemandService.generate`）**

```text
入参：startAt · endAt（半开区间，startAt < endAt）· warehouseId · supplierId(可选) · purchaserId(可选)

事务：
1. 幂等 claim(scope = "PURCHASE_DEMAND_GENERATE", key, request)
2. 校验：startAt < endAt（否则 PURCHASE_QUANTITY_INVALID）
        warehouseId 启用（否则 PURCHASE_WAREHOUSE_DISABLED）
3. 查询来源行（稳定排序）：
   SELECT oi.*, o.* FROM sales_order_item oi JOIN sales_order o ON o.id = oi.order_id
   WHERE o.status = 'CONFIRMED' AND o.deleted = FALSE AND oi.deleted = FALSE
     AND o.confirmed_at >= :startAt AND o.confirmed_at < :endAt
     AND oi.actual_quantity IS NOT NULL AND oi.actual_quantity > 0
   ORDER BY oi.sku_id ASC, o.confirmed_at ASC, oi.id ASC        ← 确定性排序（P12 同源纪律）
4. 逐行：若 uk_purchase_demand_source_active 已有活动需求 → 跳过（返回已有 id）
        否则 INSERT ... ON CONFLICT DO NOTHING，冲突则重读（INSERT 竞争，收敛为同一行）
        required_quantity    = oi.actual_quantity        ← 取实数量（A 源口径）
        demand_unit_snapshot = oi.sale_unit_snapshot     ← Q17：需求单位 = 销售单位（永不改写）
        source_confirmed_at  = o.confirmed_at            ← 原订单确认时间，原样保存
        demand_date          = o.confirmed_at 在 Asia/Shanghai 下的 LocalDate
                               （Q6a：**禁止** date(startAt)；由 ck_purchase_demand_date 强制）
        supplier_id / warehouse_id = 入参（可空 → 待分配）
5. 写 purchase_operation_log（DEMAND_GENERATE，purchase_order_id = NULL，purchase_receipt_id = NULL）
   —— 见下方「日志归属」说明
6. 幂等 complete(返回 createdIds)
```

**日志归属（Q14 修订后：由 `operation_type` 决定，不是「至少一个 id 非空」）**

| operation_type | `purchase_order_id` | `purchase_receipt_id` | 依据 |
| --- | --- | --- | --- |
| `DEMAND_GENERATE` | **NULL** | **NULL** | 此时采购单与收货单都还不存在 → 两个 id 必须同时为空 |
| `DEMAND_ALLOCATE` | **NOT NULL** | NULL | 分配必挂在某个采购行上；由 `purchaseOrderItemId` **反查** `purchase_order_id` 后写入 |
| `CREATE` / `UPDATE` / `SUBMIT` / `CANCEL` / `SHORT_CLOSE` / `DELETE` | **NOT NULL** | NULL | 采购单自身命令 |
| `RECEIPT_CREATE` / `RECEIPT_UPDATE` / `RECEIPT_CONFIRM` / `RECEIPT_DELETE` | **NOT NULL** | **NOT NULL** | 收货单必属采购单（`purchase_receipt.purchase_order_id NOT NULL`） |

**A 源的问题**：`purchase_order_id` 是 `NOT NULL`，而 `DEMAND_GENERATE` 时还没有采购单。
把约束简单放宽成 `purchase_order_id IS NOT NULL OR purchase_receipt_id IS NOT NULL` **也不成立**——
`DEMAND_GENERATE` 时**两个 id 都不存在**，会被这条 CHECK 拒绝。
**W5 处置**：`purchase_order_id` 改**可空** + `ck_purchase_operation_log_owner` 按 type 分支强制（§5.10）。
需求生成日志用 `purchase_order_id = NULL` + `after_data.demandIds = [...]`；
`DEMAND_ALLOCATE` 通过 `purchaseOrderItemId` 反查并写入真实 `purchase_order_id`。

**分配（`PurchaseDemandService.allocate` 与 `PurchaseOrderService` 的分配校验）**

分配是**以 allocation 为单位的集合操作**（Q13 修订后）：一次请求携带一组
`(demandId, quantity, demandVersion)`，全部落在**同一采购行**上。

```text
锁定顺序（P12）：按 demandId 升序，逐个 SELECT ... FOR UPDATE

逐条 allocation 校验（P9/P10）：
  demand 存在且 status ∈ {PENDING, PARTIALLY_ALLOCATED, ALLOCATED}
  demandVersion 必填且 == demand.version        （缺失 → PURCHASE_DEMAND_VERSION_REQUIRED
                                                  不等 → PURCHASE_DEMAND_VERSION_CONFLICT）
  quantity > 0
  demand.allocated + quantity <= demand.required （否则 PURCHASE_DEMAND_ALLOCATION_EXCEEDED）
  demand.warehouse_id == 采购单.warehouse_id                （否则 PURCHASE_DEMAND_ALLOCATION_CONFLICT；
                                                             warehouse 在 generate 时已固定）
  若 demand.allocated_quantity > 0：demand.supplier_id == 采购单.supplier_id
                          （否则 PURCHASE_DEMAND_ALLOCATION_CONFLICT）
  若 demand.allocated_quantity == 0（首次分配）：把 demand.supplier_id 落为采购单.supplier_id
                          （supplier 由「第一次分配」固定，之后不得改变）
  purchaseOrderItem 存在、属于该采购单、skuId == demand.skuId（否则 PURCHASE_DEMAND_ITEM_NOT_OWNED）
  demand.demand_unit_snapshot == purchaseOrderItem.purchase_unit_snapshot
                          （不等 → PURCHASE_UNIT_CONVERSION_REQUIRED，Q17）
  采购单 status == SUBMITTED，且 (supplier, warehouse) 一致（否则 PURCHASE_DEMAND_SOURCE_INVALID）

同一请求内 (demandId) 重复出现 → PURCHASE_DEMAND_ALLOCATION_DUPLICATE（40090）
同一请求内 Σ quantity 超过 demand.required → PURCHASE_DEMAND_ALLOCATION_EXCEEDED

写入：
  同一事务内按 demandId 升序逐个 UPDATE purchase_demand：
    allocated += 本次该 demand 的合计；status 按 §4.4；version = demandVersion + 1
```

**订单集成（只读，用户指令 §1.B 要求复用）**

| 读取项 | 来源 | 说明 |
| --- | --- | --- |
| 来源订单状态 | `sales_order.status = 'CONFIRMED'` | 只接受已确认订单（P7） |
| 来源区间 | `sales_order.confirmed_at ∈ [startAt, endAt)` | 半开区间（Q6a） |
| 需求量 | `sales_order_item.actual_quantity` | **取实数量**（A 源口径），不是 `ordered_quantity` |
| 快照来源 | `sales_order_item` 的 `spu_code` / `product_name` / `sku_code` / `spec_name` / `spec_values` / `sale_unit` / `product_type` | 逐字段复制 |
| 需求单位 | `sales_order_item.sale_unit_snapshot` | **Q17**：需求单位 = 销售单位，生成时冻结 |
| 采购单位 | `supplier_sku.purchase_unit`（**不是** `sale_unit`） | 采购单位来自供应商-商品关系，写入 `purchase_order_item.purchase_unit_snapshot` |

**Q17 修订：需求单位与采购单位分离，禁止「只换单位字符串」**

A 源把需求行的 `purchaseUnitSnapshot` 直接设为 `row.getSaleUnitSnapshot()`（登记为 A-D24）。
原设计打算「在 `allocate` 时用 `supplier_sku.purchase_unit` 覆盖」——**这两者都不可接受**：
后者会把「100 kg」仅替换单位字符串就变成「100 箱」。

```text
purchase_demand.demand_unit_snapshot        ← sales_order_item.sale_unit_snapshot   （生成时冻结，永不改写）
purchase_order_item.purchase_unit_snapshot  ← supplier_sku.purchase_unit            （创建/编辑时冻结）

分配时：
  demand.demand_unit_snapshot == purchaseOrderItem.purchase_unit_snapshot  → 允许自动分配
  demand.demand_unit_snapshot != purchaseOrderItem.purchase_unit_snapshot  → 拒绝自动分配
                                                                             PURCHASE_UNIT_CONVERSION_REQUIRED
```

**W5 没有单位换算模型**，因此**不允许**任何形式的单位替换或换算系数猜测。
若将来需要 kg/箱、件/瓶 等换算，**单独新增 Unit Conversion 能力**（独立波次），不在 W5 猜系数。

### 7.5 数量与金额语义（用户明确要求）

**数量的四个派生量（P24 恒等式）**

```text
cumulative_received_quantity = 采购行 received_quantity（确认后的累计）
remaining_quantity           = GREATEST(planned_quantity − cumulative_received_quantity, 0)
over_receipt_quantity        = GREATEST(cumulative_received_quantity − planned_quantity, 0)
receipt_difference           = cumulative_received_quantity − planned_quantity      （可为负）
```

**校验用的「可收上限」（运行时计算，不落库）**

```text
tolerance  = config("scm.purchase.over_receipt_tolerance_percent")  默认 "10"，范围 0–100
ceiling    = planned_quantity × (1 + tolerance / 100)
available  = ceiling − purchase_order_item.received_quantity
本次 effectiveQuantity > available → PURCHASE_RECEIPT_OVER_RECEIVED（整笔回滚）
```

**标品 vs 非标品（P21/P22）**

```text
STANDARD      effectiveQuantity = declaredQuantity（本次数量）
              请求不得携带 actualWeight / weightSource / correctionReason
NON_STANDARD  effectiveQuantity = actualWeight（本次实重，必填且 > 0）
              weightSource 必须 == MANUAL
              planned_quantity 永不被覆盖（P22）
```

**金额（P6）**

```text
line_amount  = round(planned_quantity × purchase_price, 4, HALF_UP)
total_amount = Σ line_amount（create / update 时重算）
收货不改变 total_amount（A-D8：W5 无「实际金额」字段）
```

**容差配置载体（Q3a 修订后：SmartAdmin Config，不是 Dict）**

| 项 | 规格 |
| --- | --- |
| 载体 | **SmartAdmin 原生 Config**：`t_config` 表（V3 已建，**不新建 `sys_config`**）+ `sa-base` 的 `ConfigService` |
| key | `scm.purchase.over_receipt_tolerance_percent` |
| 默认值 | `10`（**缺失时回退**） |
| 合法范围 | **0–100**（含端点） |
| 读取 | `ConfigService.getConfig(String)` → `ConfigVO.getConfigValue()`（**只读，不写**） |
| 非法值 | 解析失败 / 负数 / > 100 → `PURCHASE_TOLERANCE_CONFIG_INVALID(40999)` |
| 缓存 | 复用 `ConfigService` 自带的 `ConcurrentHashMap` 缓存 + `@SmartReload(CONFIG_RELOAD)`；**W5 不自建缓存** |
| 种子 | **V15 播种 1 条**（见下） |

**为什么选 Config 而不是 Dict**：这是**运行参数**（数值 + 范围 + 默认值），不是枚举字典；
SmartAdmin 已有 `t_config` 管理能力与按 key 查询能力；新建 `sys_config` 或 SCM 自定义配置基础设施被禁止。

**V15 播种可行性核验（实测 schema，不猜字段）**

`t_config` 由 `V3__sa_system_login_support_and_seed.sql` 建立，实测 schema：

```sql
CREATE TABLE t_config (
    config_id    BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    config_name  VARCHAR(255) NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT         NOT NULL,
    remark       VARCHAR(255),
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_t_config_key UNIQUE (config_key)
);
```

核验结论 —— **播种安全**：

1. `create_time` / `update_time` 都有 `DEFAULT CURRENT_TIMESTAMP`，插入无需显式赋值；
2. `uk_t_config_key` 唯一 → `ON CONFLICT (config_key) DO NOTHING` 幂等播种；
3. `ConfigService` 在 `@PostConstruct` 装载缓存，而 Flyway 迁移在 Spring 上下文启动期、
   `ConfigDao`（依赖 `DataSource`）创建**之前**完成 → **首次启动即可见**，不需要人工补配；
4. 只读路径 `ConfigService.getConfig(String)` 已存在，**不需要给 `sa-base` 加重载**
   （`ConfigKeyEnum` 属 SmartAdmin 底座、在零修改清单内 → **不修改**）。

V15 播种语句（四个业务字段全部显式，不使用 `SELECT *` 或位置插入）：

```sql
INSERT INTO t_config (config_name, config_key, config_value, remark)
VALUES ('采购超收容差百分比', 'scm.purchase.over_receipt_tolerance_percent', '10',
        'W5 采购收货超收容差，合法范围 0-100，缺失时回退 10')
ON CONFLICT (config_key) DO NOTHING;
```

**SCM 侧只读封装**：`purchase/constant/PurchaseConfigKey` 持有 key 字面量与默认值；
`PurchaseReceiptService` 注入 SmartAdmin 的 `ConfigService` 读取；
解析与范围校验放在 `PurchaseReceiptQuantityCalculator.tolerance(String raw)` 这个**纯函数**里
（可被单测直接覆盖）。**不新增任何 SCM 配置基础设施**。

**为什么默认值必须可回退**：配置缺失时若直接报错，会让「未配置环境」完全无法收货；
A 源的 `tolerancePercent()` 就是 `raw == null ? "10" : raw`（缺失回退默认）。

### 7.6 单号（Q8）

```java
// PurchaseNumberGenerator —— 与 W4 的 OrderNumberGenerator.format 完全同构
public String order()   { return format("PO", dao.nextOrderNo());   }
public String receipt() { return format("PR", dao.nextReceiptNo()); }
public static String format(String prefix, long n) {
    return prefix
        + LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE)
        + String.format(Locale.ROOT, "%06d", n);
}
```

| 项 | 值 |
| --- | --- |
| 序列 | `purchase_order_no_seq` / `purchase_receipt_no_seq`（`START WITH 1 INCREMENT BY 1`） |
| 前缀 | `PO`（采购单）/ `PR`（收货单） |
| 格式 | `PO` + `yyyyMMdd` + 至少 6 位（超 999999 自然扩位） |
| 重置 | **不按日重置**，全局单调递增 |
| 时区 | `Asia/Shanghai` |

**为什么偏离 Native First（必须记录）**：V2 的 `SerialNumberService` 唯一 `@Service` 实现是
`SerialNumberInternService`（**内存锁 + `ConcurrentHashMap`**）→ **多实例部署会生成重复单号**；
另有 `SerialNumberMysqlService`（名字即 MySQL 专用）；且 PG 收口已发现
`SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` 是 dead code。
→ 采 PG sequence 是**集群安全**的选择，且与 W4 的 `sales_order_no_seq` 一致。

### 7.7 错误码（Q11）

```java
public enum PurchaseErrorCode implements ScmErrorCode {
    // ---- 40080–40091 BAD_REQUEST ----
    PURCHASE_QUANTITY_INVALID            (40080, "采购数量必须为大于零的四位定点数"),
    PURCHASE_PRICE_INVALID               (40081, "采购单价必须为非负四位定点数"),
    PURCHASE_DEMAND_ALLOCATION_EXCEEDED  (40082, "分配数量超过需求量"),
    PURCHASE_RECEIPT_QUANTITY_INVALID    (40083, "收货数量或实重不正确"),
    PURCHASE_IDEMPOTENCY_KEY_REQUIRED    (40084, "Idempotency-Key 不能为空"),
    PURCHASE_IDEMPOTENCY_KEY_INVALID     (40085, "Idempotency-Key 长度不能超过 200 个字符"),
    PURCHASE_CANCEL_REASON_REQUIRED      (40086, "取消原因不能为空"),
    PURCHASE_SHORT_CLOSE_REASON_REQUIRED (40087, "少收关单原因不能为空"),
    PURCHASE_ITEM_VERSION_REQUIRED       (40088, "保留采购明细必须携带版本"),
    PURCHASE_ORDER_ITEM_EMPTY            (40089, "采购单至少需要一行有效明细"),
    PURCHASE_DEMAND_ALLOCATION_DUPLICATE (40090, "同一采购行重复关联同一采购需求"),
    PURCHASE_DEMAND_VERSION_REQUIRED     (40091, "采购需求分配必须携带需求版本"),

    // ---- 40480–40489 NOT_FOUND ----
    PURCHASE_DEMAND_NOT_FOUND            (40480, "采购需求不存在"),
    PURCHASE_ORDER_NOT_FOUND             (40481, "采购单不存在"),
    PURCHASE_ORDER_ITEM_NOT_FOUND        (40482, "采购明细不存在"),
    PURCHASE_RECEIPT_NOT_FOUND           (40483, "收货单不存在"),
    PURCHASE_RECEIPT_ITEM_NOT_FOUND      (40484, "收货明细不存在"),

    // ---- 40971–40999 CONFLICT ----
    PURCHASE_UNIT_CONVERSION_REQUIRED    (40971, "需求单位与采购单位不一致，W5 不支持自动换算"),
    PURCHASE_DEMAND_VERSION_CONFLICT     (40972, "采购需求版本冲突，请刷新后重试"),
    PURCHASE_DEMAND_SOURCE_INVALID       (40980, "销售订单状态不允许生成采购需求"),
    PURCHASE_DEMAND_ALLOCATION_CONFLICT  (40981, "采购需求分配冲突"),
    PURCHASE_ORDER_STATE_INVALID         (40982, "当前采购单状态不允许此操作"),
    PURCHASE_ORDER_ITEM_NOT_OWNED        (40983, "采购明细不属于当前采购单"),
    PURCHASE_ORDER_ITEM_VERSION_CONFLICT (40984, "采购明细版本冲突"),
    PURCHASE_DEMAND_REPLACEMENT_NOT_ALLOWED (40985, "保留采购明细不允许替换采购需求来源"),
    PURCHASE_SUPPLIER_DISABLED           (40986, "供应商已停用，不能用于新采购单"),
    PURCHASE_WAREHOUSE_DISABLED          (40987, "仓库已停用，不能用于新采购单"),
    PURCHASE_RECEIPT_STATE_INVALID       (40988, "当前收货单状态不允许此操作"),
    PURCHASE_RECEIPT_OVER_RECEIVED       (40989, "本次收货数量超过剩余可收数量"),
    PURCHASE_IDEMPOTENCY_CONFLICT        (40990, "相同幂等键的请求内容不一致"),
    PURCHASE_RECEIPT_ORDER_STATE_INVALID (40991, "当前采购单状态不允许创建或确认收货"),
    PURCHASE_SUPPLIER_SKU_DISABLED       (40992, "该供应商未启用此 SKU 的采购配置"),
    PURCHASE_ORDER_DELETE_STATE_INVALID  (40993, "仅草稿采购单可以删除"),
    PURCHASE_RECEIPT_DELETE_STATE_INVALID(40994, "仅草稿收货单可以删除"),
    PURCHASE_DEMAND_ITEM_NOT_OWNED       (40995, "采购明细与采购需求不匹配"),
    PURCHASE_ORDER_ITEM_DUPLICATE_SKU    (40997, "采购单内 SKU 不能重复"),
    PURCHASE_RECEIPT_ITEM_INCOMPLETE     (40998, "确认收货必须提交本收货单的全部明细"),
    PURCHASE_TOLERANCE_CONFIG_INVALID    (40999, "采购超收容差配置无效");

    private final int code;
    private final String msg;
}
```

**仓库域错误码（`warehouse/constant/WarehouseErrorCode`，**2** 个）**

```java
public enum WarehouseErrorCode implements ScmErrorCode {
    WAREHOUSE_NOT_FOUND      (40485, "仓库不存在"),
    WAREHOUSE_CODE_DUPLICATE (40996, "仓库编码已存在");

    private final int code;
    private final String msg;
}
```

**为什么拆成两个枚举**：`module/scm/warehouse/**` 必须能**独立于** `module/scm/purchase/**` 抛错，
否则会形成 `warehouse → purchase` 的反向依赖 —— 而 §2.1 把 `warehouse` 独立成域的**唯一理由**
就是避免这种反向依赖（W6 库存域要直接依赖它）。
`PURCHASE_WAREHOUSE_DISABLED(40987)` **留在** `PurchaseErrorCode`：它是**采购侧规则**
（不允许用停用仓库建单），不是仓库域自身的不变量。

**撞码校验（实测 V2 已占用码）**

```text
已占用 40000 40010-40011 40020-40026 40030-40037 40040 40060-40074
       40410 40420 40430-40433 40440 40442 40460-40463
       40910-40911 40920-40923 40930 40932-40933 40935-40949 40960-40970
W5 使用 40080-40091 · 40480-40485 · 40971-40999
→ 交集为空 ✓（40080-40091 落在 40075-40099 空档；40480-40485 落在 40464-40499 空档；
              40971-40999 与已占用段（最大 40970）无交集）

码量勾稽（W5 合计 **40** 个）：
  PurchaseErrorCode   38 = 400xx 12（40080–40091）· 404xx 5（40480–40484）
                          · 409xx 21（40971–40972 + 40980–40995 + 40997–40999）
  WarehouseErrorCode   2 = 40485（NOT_FOUND）· 40996（CODE_DUPLICATE）
  → 合计 40（原设计 36 个；Q13 新增 2 个、Q17 新增 1 个、Q14 不新增）
  → 两个枚举**合起来**的码值集合与 W1–W4 全部错误码零交集，且两枚举之间零重复
```

**复用（不重复定义）**：`ScmCommonErrorCode.VERSION_CONFLICT(40921)` · `ScmCommonErrorCode.VALIDATION_ERROR(40000)`。

**撞码门禁**：新增 `PurchaseErrorCodeTest`（断言 `PurchaseErrorCode` 38 个 + `WarehouseErrorCode` 2 个，
**合计 40** 个码与 W1–W4 全部错误码枚举无交集，且两个枚举之间码值不重复）。

### 7.8 差量同步（P1/P2/P3）与 allocation 集合对账（**Q13 修订的核心**）

`PurchaseOrderItemChangeSet` + `PurchaseOrderAllocationChangeSet` + `PurchaseOrderService.update`：

```text
输入：items[]（保留行带 id + version，新增行 id = null）
      每行携带 allocations[]（demandId + quantity + demandVersion）

A. 行级对账（行身份 = (purchaseOrderId, skuId)）
1. 收集请求行 → requestedBySku
   同一 skuId 出现两次 → PURCHASE_ORDER_ITEM_DUPLICATE_SKU
2. 加载现有行（FOR UPDATE）→ existingBySku
3. 保留行校验：
   id 存在 且 existingBySku.get(skuId).id == id      （否则 PURCHASE_ORDER_ITEM_NOT_OWNED）
   version 必须相等                                   （否则 PURCHASE_ORDER_ITEM_VERSION_CONFLICT）
4. 删除行校验：不在保留集内 且 received_quantity > 0 → PURCHASE_ORDER_STATE_INVALID

B. allocation 级对账（allocation 身份 = (purchaseOrderItemId, purchaseDemandId)）
5. 对每个保留/新增行加载其现有活动 allocation 集合 → existingAllocByDemand
6. 同一行内重复 demandId → PURCHASE_DEMAND_ALLOCATION_DUPLICATE
7. 逐条校验：
   demandVersion 必填                                  （否则 PURCHASE_DEMAND_VERSION_REQUIRED）
   demandVersion == 该 demand 当前 version             （否则 PURCHASE_DEMAND_VERSION_CONFLICT）
   demand.demand_unit_snapshot == 行.purchase_unit_snapshot
                                                       （否则 PURCHASE_UNIT_CONVERSION_REQUIRED）
   新增 allocation 的 demandId 已在 existingAllocByDemand 中 → PURCHASE_DEMAND_ALLOCATION_DUPLICATE
8. **删除的 allocation**（在 existing 中、不在请求中）→ 软删该 allocation 行（**只删这一条**）
9. **保留的 allocation**（两侧都有）→ 只更新 allocated_quantity（**不重建行**，同 demandId 的其它分配不受影响）
10. **新增的 allocation** → INSERT 新 allocation 行
11. 校验 demand.allocated + 本次该 demand 的 Σquantity <= demand.required
                                                       （否则 PURCHASE_DEMAND_ALLOCATION_EXCEEDED）
    校验采购单 (supplier, warehouse) 与该 demand 一致   （否则 PURCHASE_DEMAND_ALLOCATION_CONFLICT）

C. 需求侧重算（按 demandId 升序锁定，逐个 SELECT ... FOR UPDATE）
12. 对**本次涉及 demandId 的并集**（旧集合 ∪ 新集合）逐个重算：
      oldTotals[d] = 本单对该 demand 的旧分配合计（删除前）
      newTotals[d] = 本次请求对该 demand 的分配合计
      otherAllocated = demand.allocated − oldTotals[d]
      finalAllocated = otherAllocated + newTotals[d]
      校验 0 <= finalAllocated <= demand.required        （否则 PURCHASE_DEMAND_ALLOCATION_EXCEEDED）
      UPDATE purchase_demand：allocated = finalAllocated；status 按 §4.4；version = version + 1
    —— **必须遍历并集**：只在旧集合出现的 demand（被删空）也要重算，否则 allocated 不会回落、
       status 也不会从 ALLOCATED 退回 PENDING。

D. 写入与收尾
13. 新增行 → createItem；保留行 → 更新 planned / purchase_price / line_amount / sort_order
14. 删除行 → 先软删其全部 allocation，再软删 orderItem
15. 重算 total_amount（§7.5）
16. 写 purchase_operation_log（UPDATE + 全量 before/after，**含 allocations 全量**）
```

**A-D23 的修复点**：A 源用 `Map<itemId, allocation>`（覆盖写）表达分配，
导致「一行多需求」在编辑时只保留最后一条。W5 的算法**以 allocation 为主键集合**对账
（`Map<(itemId, demandId), allocation>`），与 `uk_purchase_demand_allocation_source_active`
的 `(purchase_order_item_id, purchase_demand_id)` 完全同构 ——
「只改一个 allocation / 删一个 allocation / 保留其它 allocation」都是**独立的行级操作**，
禁止任何「一个 item 对一个 allocation」的算法。

**两层身份必须区分**：

```text
行身份         = (purchase_order_id, sku_id)                     uk_purchase_order_item_order_sku_active（保留）
allocation 身份 = (purchase_order_item_id, purchase_demand_id)    uk_purchase_demand_allocation_source_active
```

**Q13 修订要求的 6 个用例**（§11 已登记）：同一 SKU 一行关联两个 demand · 只改其中一个 allocation ·
删除其中一个 allocation · 相同 `(item,demand)` 重复拒绝/幂等 · 多 demand 总分配不得超各自 required ·
编辑后所有 demand 的 `allocated`/`status` 重算正确。

### 7.9 事务边界与锁序

**事务边界**

| 命令 | 事务范围 |
| --- | --- |
| `demand.generate` | 幂等 claim → 来源读取 → 需求 INSERT → 日志 → 幂等 complete（**同一事务**） |
| `demand.allocate` | 幂等 claim → 需求锁（按 demandId 升序）→ 采购行校验（含**单位一致**）→ allocation INSERT → 需求 UPDATE（逐个重算）→ 日志 → complete |
| `order.create` | 幂等 claim → 供应商/仓库校验 → 需求锁（按 demandId 升序）→ 单头 INSERT → 明细 INSERT → allocation 集合 INSERT → 需求重算 → 日志 → complete |
| `order.update` | 单头锁 → 明细锁 → allocation 锁 → 需求锁（旧∪新 demandId，升序）→ 行/分配对账写入 → 需求重算 → 日志 |
| `order.submit` / `cancel` / `short-close` | 幂等 claim → 单头锁 → version 校验 → 状态 UPDATE → 日志 → complete |
| `receipt.create` | 幂等 claim → 采购单锁 → 采购行锁 → 收货单 INSERT → 收货行 INSERT（快照）→ 日志 → complete |
| `receipt.confirm` | 幂等 claim → 采购单锁 → 收货单锁 → 采购行锁 → 收货行锁 → 逐行校验 → 全部写入 → 日志 → complete → **（W6）InventoryContract** |
| `warehouse.*` | 单表单行事务 |

**锁序（全局固定，P12）**

```text
1. purchase_demand        （按 demandId 升序）
2. purchase_order         （单行）
3. purchase_order_item    （按 id 升序）
4. purchase_receipt       （单行）
5. purchase_receipt_item  （按 id 升序）
```

**采购需求 → 采购单** 的锁序与 **采购单 → 收货单** 的锁序**不得交叉**：
`order.create` 会同时锁需求与采购单（1→2→3），`receipt.confirm` 只锁 2→3→4→5，
两条路径的公共前缀顺序一致，不会形成环。**W5 不涉及库存余额锁**（W6 追加时需把
「库存余额」排在 5 之后，即 `… → 5. receipt_item → 6. inventory`）。

### 7.10 乐观锁

| 实体 | 机制 |
| --- | --- |
| `PurchaseOrderEntity` | MP `@Version` + `OptimisticLockerInnerInterceptor`；`updateById` 影响行数 = 0 → `VERSION_CONFLICT(40921)` |
| `PurchaseOrderItemEntity` | 同上 |
| `PurchaseReceiptEntity` / `PurchaseReceiptItemEntity` | 同上 |
| `PurchaseDemandEntity` | 同上 |
| `PurchaseDemandAllocationEntity` | 同上（编辑路径） |
| `WarehouseEntity` | 同上 |
| `PurchaseOperationLogEntity` | **无 version**（只追加） |
| `ReceiptWeighingRecordEntity` | **无 version**（只追加） |

**并发场景（必须覆盖，P25 的对偶）**

```text
C1  两次同时 generate 同一销售订单行 → uk_purchase_demand_source_active + INSERT 竞争
C2  两个操作者同时 submit 同一采购单 → version 冲突
C3  两次同时 confirm 同一收货单 → 幂等键 + version
C4  两张收货单同时收取同一采购单的剩余量 → 采购行 FOR UPDATE + version
C5  同一需求被两张采购单同时分配 → demand FOR UPDATE（按 demandId 升序）+ 数量校验
C6  两个操作者同时 update 同一草稿采购单 → version 冲突
```

### 7.11 幂等（8 个作用域）

| 命令 | scope | 备注 |
| --- | --- | --- |
| `demand.generate` | `PURCHASE_DEMAND_GENERATE` | 全局 |
| `demand.allocate` | `PURCHASE_DEMAND_ALLOCATE:{demandId}` | 按需求隔离 |
| `order.create` | `PURCHASE_ORDER_CREATE` | 全局 |
| `order.submit` | `PURCHASE_ORDER_SUBMIT:{id}` | 按单隔离 |
| `order.cancel` | `PURCHASE_ORDER_CANCEL:{id}` | 按单隔离 |
| `order.shortClose` | `PURCHASE_ORDER_SHORT_CLOSE:{id}` | 按单隔离 |
| `receipt.create` | `PURCHASE_RECEIPT_CREATE:{purchaseOrderId}` | 按采购单隔离 |
| `receipt.confirm` | `PURCHASE_RECEIPT_CONFIRM:{receiptId}` | 按收货单隔离 |

**实现**：直接复用 W4 的 `OrderIdempotencyService` 模式（**复制为 `PurchaseIdempotencyService`，
不修改 W4 代码**），关键三点：

```text
1. scope 前缀拼 ScmOperator.current()  → 修 A-D14（不同操作者不可互相重放）
2. claim 用 INSERT 竞争（uk_idempotency_record_scope_key_active），不先查后插  → 修 A-D17
3. complete 与业务写入同一事务；result_data 存 {"value": ...}
```

**规范化请求哈希**：复用 `OrderIdempotencyRequestHasher` 的纪律
（键排序 / 数字 `stripTrailingZeros` / 数字字符串归一），复制为 `PurchaseIdempotencyRequestHasher`。

### 7.12 操作日志（P 系列审计）

**写入点（12 种 operation_type）**

| operation_type | 触发命令 | before_data | after_data |
| --- | --- | --- | --- |
| `DEMAND_GENERATE` | `demand.generate` | `null` | `{demandIds:[...], sourceLineCount, createdCount, skippedCount}`（`purchase_order_id = NULL`、`purchase_receipt_id = NULL`） |
| `DEMAND_ALLOCATE` | `demand.allocate` | `{allocatedQuantity}` | `{purchaseOrderItemId, purchaseOrderId, allocations:[{demandId,quantity}], allocatedQuantity, supplierId, warehouseId}`（`purchase_order_id` 由 `purchaseOrderItemId` 反查后写入，**非空**） |
| `CREATE` | `order.create` | `null` | 全量 header + items |
| `UPDATE` | `order.update` | 全量 header + items | 全量 header + items |
| `SUBMIT` | `order.submit` | `{status:'DRAFT',version:n}` | `{status:'SUBMITTED',version:n+1}` |
| `CANCEL` | `order.cancel` | `{status,version}` | `{status:'CANCELLED',cancelReason,version}` |
| `SHORT_CLOSE` | `order.shortClose` | `{status:'PARTIALLY_RECEIVED',version}` | `{status:'SHORT_CLOSED',shortCloseReason,version}` |
| `DELETE` | `order.delete` | 全量 | `{deleted:true}` |
| `RECEIPT_CREATE` | `receipt.create` | `null` | `{receiptNo, items:[...]}` |
| `RECEIPT_UPDATE` | `receipt.update` | `{remark,version}` | `{remark,version}` |
| `RECEIPT_CONFIRM` | `receipt.confirm` | `{items:[{id,receivedQuantity}]}` | `{items:[{id,receivedQuantity,cumulative,over,difference}], orderStatus}` |
| `RECEIPT_DELETE` | `receipt.delete` | 全量 | `{deleted:true}` |

**`operator`**：`ScmOperator.current()`（修 A-D12 的 `SYSTEM` 硬编码）。
**日志只追加**：无 update/delete 端点。
**归属（Q14）**：`purchase_order_id` 的取值由 `operation_type` 决定，并由
`ck_purchase_operation_log_owner`（§5.10）在 DB 层强制 —— `DEMAND_GENERATE` **双 id 为空**、
`DEMAND_ALLOCATE` **只有采购单 id**（由 `purchaseOrderItemId` 反查）、`RECEIPT_*` **双 id 非空**。
`RECEIPT_*` 的 `purchase_receipt_id` 取本收货单 id；`purchase_order_id` 取收货单继承的采购单 id。

---

## 8. Inventory 边界（用户明确要求）

### 8.1 原则

```text
1. W5 不创建任何库存表（无 inventory / inventory_movement / 临时余额表）
2. W5 不写任何库存余额、不产生任何库存流水
3. W5 只定义契约 + 提供 NoOp 默认实现，**零调用点**
4. W6 实现契约时，在 PurchaseReceiptService.confirm 的事务内加「一行调用」（无迁移）
5. 契约必须能表达「未启用」与「可用量为 0」的区别（同 W3 的 UNPRICED ≠ 0 元纪律）
6. **Q5**：W6 首次启用库存前必须完成历史 CONFIRMED 收货行的 backfill，且以稳定唯一源键防重（§8.5）
7. **Q5**：W5 **不新增** `inventory_posted` 之类的死字段（与 W4 拒绝 `fulfillment_status` 同一纪律）
```

### 8.2 契约定义（只定义，不实现）

```java
package net.lab1024.sa.admin.module.scm.purchase.support;

/**
 * Future integration only: W5 neither implements nor calls inventory mutations.
 *
 * <p>W6 (Inventory) is expected to implement this contract. The single call site will be
 * {@code PurchaseReceiptService.confirm(...)}, inside the same transaction, immediately after
 * the purchase-side writes. W5 ships {@link NoOpPurchaseInventoryContract} and never invokes
 * any method, so W5 收货确认 does not create inventory facts.</p>
 */
public interface PurchaseInventoryContract {

    /**
     * 稳定唯一源键的文档类型常量（Q5）。
     *
     * <p>W6 的库存流水必须持久化 {@code source_document_type = SOURCE_DOCUMENT_TYPE} 与
     * {@code source_document_item_id = InboundFact.receiptItemId}，并据此建立部分唯一索引
     * {@code uk (source_document_type, source_document_item_id) WHERE deleted = FALSE}，
     * 使「历史 backfill / 未来实时 confirm / 重试」三者不可重复入库。</p>
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * 一次「已确认的收货行」所代表的入库事实。
     *
     * <p>W5 只负责产生这个事实；W6 决定它是被 push（本方法）还是 pull（W6 读表）。
     * {@code idempotencyKey} 由 W6 用于防重（W6 侧应有
     * {@code uk (source_document_type, source_document_item_id)} 的部分唯一索引）。</p>
     */
    record InboundFact(
            Long purchaseOrderId,
            Long receiptId,
            /** 稳定唯一源键的 item 维度：{@code source_document_item_id}（Q5）。 */
            Long receiptItemId,
            Long warehouseId,
            Long skuId,
            String warehouseCode,
            String warehouseName,
            String skuCode,
            String skuName,
            String unit,
            java.math.BigDecimal quantity,
            java.math.BigDecimal unitCost,
            String idempotencyKey) {
    }

    /**
     * 可用量探测。
     *
     * <p><b>返回 {@code null} 表示「库存能力未启用」</b>，必须与「可用量为 0」严格区分——
     * 与 W3 的 {@code UNPRICED ≠ 0 元}、W4 的 {@code ordered_total_amount 可空} 同一语义纪律。
     * 调用方在 {@code null} 时不得把可用量当作 0。</p>
     */
    record Availability(java.math.BigDecimal available, java.math.BigDecimal reserved) {
    }

    void postInbound(InboundFact fact);

    Availability queryAvailability(Long skuId, Long warehouseId);
}
```

```java
package net.lab1024.sa.admin.module.scm.purchase.support;

/** Contract placeholder only. No W5 purchase command invokes these methods. */
public final class NoOpPurchaseInventoryContract implements PurchaseInventoryContract {

    @Override
    public void postInbound(InboundFact fact) {
        // intentionally empty
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        // null == "inventory not enabled", NOT "available quantity is zero"
        return null;
    }
}
```

### 8.3 与 C 源的对立（必须记录）

| 维度 | C 源 | W5 |
| --- | --- | --- |
| 库存写入位置 | `ReceiveService.add` / `confirmInbound` **直接调用** `StockOperateService.purchaseInbound` | **零调用点** |
| 仓库 | 硬编码 `warehouseId = 1L` | 采购单绑定真实仓库 + 快照 |
| 收货单创建是否影响库存 | `directStock=true` 时**立即**入库 | **不影响**（P17） |
| 库存与采购的事务关系 | 同一事务（隐式） | W5 无事务关系；W6 决定（契约预留 `idempotencyKey`） |

### 8.4 「可用量 / 预占」的后续波次入口

| 能力 | 波次 | 说明 |
| --- | --- | --- |
| 收货 → 库存余额 + `PURCHASE_IN` 流水 | **W6** | 实现 `PurchaseInventoryContract` |
| 采购汇总时的「实时库存抵扣」 | **W6+** | A 源未落地（A-D3/G7），需专项设计并发口径 |
| 销售出库 / 库存占用 | **W6+** | W4 的 `OrderInventoryContract` 已预留 |
| 采购退货扣回（`PURCHASE_RETURN_OUT`） | **W6+/W7** | 需求 05-06 未定 |

### 8.5 W6 Inventory bootstrap contract（**Q5**）

W5 的边界**不变**：零库存表 · 零库存写入 · `PurchaseInventoryContract` 零调用点 ·
**不新增** `inventory_posted` 等死字段。但 W6 的**启动方式**必须在 W5 阶段约定清楚，
否则「历史已确认收货」与「未来实时收货」会打架。

**稳定唯一源键（W6 必须实现）**

```text
source_document_type    = 'PURCHASE_RECEIPT_ITEM'   （PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE）
source_document_item_id = purchase_receipt_item.id
```

W6 的库存流水表必须带这两列，并建立部分唯一索引（**W5 不建此表/索引**）：

```sql
-- W6 迁移（W5 不建）
CREATE UNIQUE INDEX uk_inventory_movement_source_active
    ON inventory_movement (source_document_type, source_document_item_id)
    WHERE deleted = FALSE AND source_document_item_id IS NOT NULL;
```

**三条路径必须互不重复入库**

| 路径 | 触发时机 | 防重机制 |
| --- | --- | --- |
| **历史 backfill** | W6 首次启用库存前，一次性回放 | `INSERT ... ON CONFLICT (source_document_type, source_document_item_id) DO NOTHING`（唯一索引兜底） |
| **未来实时 confirm** | W6 上线后，`PurchaseReceiptService.confirm` 事务内调用 `postInbound` | 同一唯一索引 + `InboundFact.idempotencyKey` |
| **重试 / 重复消费** | 手工重跑 backfill、消息重投、接口重放 | 同一唯一索引（**DB 层兜底，不依赖应用层判重**） |

**backfill 的取数口径（W5 已冻结，W6 可直接用）**

```sql
SELECT ri.id, ro.id AS purchase_order_id, ri.purchase_receipt_id, ro.warehouse_id, ri.sku_id,
       ri.purchase_unit_snapshot, ri.received_quantity, oi.purchase_price
FROM purchase_receipt_item ri
JOIN purchase_receipt     r  ON r.id  = ri.purchase_receipt_id AND r.deleted  = FALSE
JOIN purchase_order       ro ON ro.id = r.purchase_order_id   AND ro.deleted = FALSE
JOIN purchase_order_item  oi ON oi.id = ri.purchase_order_item_id AND oi.deleted = FALSE
WHERE r.status = 'CONFIRMED' AND ri.deleted = FALSE
ORDER BY ri.id ASC;                     -- 确定性顺序，便于对账
```

**为什么 W5 不落 `inventory_posted`**：该列在 W5 恒为 `false`（无人写），属**死字段**——
与 W4 拒绝 `fulfillment_status`、本设计拒绝 `receipt_mode` 是同一纪律。
「是否已入库」的事实由 **W6 的流水行是否存在**表达（源键唯一），不需要在采购侧留影子列。

**W5 的责任边界**：W5 只负责让上述源键**天然存在且稳定**
（`purchase_receipt_item.id` 一经确认即不可变、不可复用、软删不影响 id），
并在 §11 的 `PurchaseInventoryContractAbsenceIT` 中证明 W5 确实没有写任何库存。

---

## 9. 前端设计（Copy First + Adapt）

### 9.0 迁移原则（W2 起生效，W3/W4/W5 沿用）

```text
C 属范围 SCM Vue 页面 → 复制 → 剪枝 → 适配 → 补测试；禁重写 C 已有页面。
绝不复制 layout / login / system / router core / permission framework / request framework / system menu seed。
复制文件必须在文件头写 Provenance 注释块；新增文件不要求。
```

### 9.1 文件清单（含来源标记）

| # | 文件 | 类型 | 来源 |
| --- | --- | --- | --- |
| 1 | `src/api/business/scm/purchase-demand-api.ts` | 复制 | C `api/business/purchase/purchase-generate-api.ts` |
| 2 | `src/api/business/scm/purchase-order-api.ts` | 复制 | C `api/business/purchase/purchase-api.ts` + `purchase-item-api.ts` |
| 3 | `src/api/business/scm/purchase-receipt-api.ts` | 复制 | C `api/business/purchase/purchase-receive-api.ts` |
| 4 | `src/api/business/scm/warehouse-api.ts` | 新增 | — |
| 5 | `src/constants/business/scm/purchase-const.ts` | 复制 | C `constants/business/purchase/purchase-const.ts` |
| 6 | `src/views/business/scm/purchase/purchase-types.ts` | 新增 | — |
| 7 | `src/views/business/scm/purchase/purchase-errors.ts` | 新增 | 仿 W4 `order-errors.ts` |
| 8 | `src/views/business/scm/purchase/purchase-form-model.ts` | 新增 | — |
| 9 | `src/views/business/scm/purchase/purchase-demand-list.vue` | 新增 | —（C 无需求页） |
| 10 | `src/views/business/scm/purchase/purchase-order-list.vue` | 复制 | C `views/business/purchase/purchase-list.vue` |
| 11 | `src/views/business/scm/purchase/purchase-receipt-list.vue` | 复制 | C `views/business/purchase/purchase-receive-list.vue` |
| 12 | `src/views/business/scm/purchase/purchase-log-list.vue` | 新增 | 仿 W4 `order-log-list.vue` |
| 13 | `src/views/business/scm/purchase/warehouse-list.vue` | 新增 | — |
| 14 | `.../components/purchase-demand-generate-modal.vue` | 复制 | C `views/business/purchase/purchase-generate.vue` |
| 15 | `.../components/purchase-order-form-drawer.vue` | 新增 | — |
| 16 | `.../components/purchase-order-item-editable-table.vue` | 复制 | C `views/business/purchase/purchase-item-list.vue`（**降级**） |
| 17 | `.../components/purchase-order-detail-drawer.vue` | 新增 | — |
| 18 | `.../components/purchase-receipt-form-drawer.vue` | 新增 | — |
| 19 | `.../components/purchase-receipt-confirm-modal.vue` | 新增 | — |
| 20 | `src/constants/index.ts` | **修改** | 注册 `purchase-const.ts`（最小化改动） |
| 21 | `e2e/scm-purchase.spec.ts` | 新增 | — |
| 22 | `test/w5-purchase-contract.test.mjs` | 新增 | 仿 W4 `w4-order-contract.test.mjs` |

**共 22 个文件**：**复制 8** · **新增 13**（含 2 个测试文件 `e2e/scm-purchase.spec.ts`、
`test/w5-purchase-contract.test.mjs`）· **修改 1**（`src/constants/index.ts` 注册 `purchase-const.ts`）。
8 个复制文件**必须**带 Provenance 头（§10.1 第 1 层）；13 个新增文件不要求。

### 9.2 复制后强制适配清单（逐项门禁）

| # | 适配项 | 适用文件 | 说明 |
| --- | --- | --- | --- |
| A1 | **删除 `resizable` / `@resizeColumn` / `handleResizeColumn`** | 10, 11, 16 | V2 无 `TableHeaderCell` |
| A2 | 枚举 5 值数字 → **6 值字符串** | 5, 10 | `PURCHASE_STATUS_ENUM` |
| A3 | 枚举 `RECEIVE_STATUS_ENUM` 3 值 → **2 值** | 5, 11 | 去掉 `INVALID` |
| A4 | **删除 `RECEIVE_FLAG_ENUM`** | 5, 11 | A 源无此概念；改用 `overReceiptQuantity` + `receiptDifference` |
| A5 | **删除 `SUPPLIER_STATUS_ENUM` / `INQUIRY_STATUS_ENUM`** | 5 | W2 已有供应商枚举；询价不做 |
| A6 | API 前缀 `/purchase/**` → `/scm/purchase/**` | 1, 2, 3 | — |
| A7 | 写命令补 **`Idempotency-Key`** | 2, 3 | 仿 `order-api.ts` 的 `orderCommand` |
| A8 | 补 **`version`** 字段与提交 | 6, 10, 11, 15, 18 | 乐观锁 |
| A9 | `supplierId` 数字输入 → **供应商选择器** | 10, 15 | 复用 `supplier-api.ts` |
| A10 | 新增 **仓库选择器** | 10, 15 | `warehouse-api.ts` |
| A11 | 采购员输入 → **员工选择器** | 10, 15 | 复用 `components/system/employee-select/index.vue` |
| A12 | 删除 `actualAmount` 列 | 10 | W5 无该字段（A-D8） |
| A13 | 补 `submit` / `cancel` / `short-close` 操作列按钮 | 10 | 6 状态机 |
| A14 | 补 `delete`（仅 DRAFT） | 10 | — |
| A15 | 补收货 `confirm` 操作（仅 DRAFT） | 11 | — |
| A16 | 收货单新增/编辑**不允许直接填状态** | 11 | 状态由命令驱动 |
| A17 | 数量/金额列右对齐 + 等宽字体 | 10, 11, 16 | 4 位定点字符串 |
| A18 | `null` 渲染为 `—`，`"0.0000"` 渲染为 `0.0000` | 10, 11, 16 | 三态纪律（同 W4） |
| A19 | 明细页 `purchase-item-list.vue` → **表单内可编辑表格** | 16 | 修 C 的 H13/H29 |
| A20 | 汇总预览去库存抵扣列 | 14 | A-D3 |
| A21 | 汇总入参改半开时间段 | 14 | Q6a |
| A22 | 权限码 `purchase:*` → `scm:purchase:*` | 10, 11, 14 | — |
| A23 | 表格 ID 常量改 V2 命名 | 10, 11, 16 | Playwright 定位用 |
| A24 | 错误提示接 `purchase-errors.ts` | 2, 3 | 错误码 → 中文 |
| A25 | 非标品行突出实重录入 | 11, 19 | G-05 |
| A26 | 容差提示（本次可收上限） | 19 | Q3 |
| A27 | 补 loading / empty / error / retry / 409 重载 | 10, 11, 14 | 与 W4 一致 |
| A28 | 删除 C 的 `useTable` 里 `TABLE_ID_CONST.BUSINESS.PURCHASE.*` 引用 | 10, 11, 16 | 改为 V2 常量 |
| A29 | 补 `a-form-item` 的 `name`（Playwright 需要 `<label for>`） | 15, 18 | 环境坑 |
| A30 | 补 `v-privilege` 到所有操作按钮 | 10, 11, 14 | 权限前端拦截 |
| A31 | **采购行多需求分配编辑器**：`items[].allocations[]` 可增 / 删 / 改（不是单 `demandId`） | 15, 16 | **Q13**：一行多需求；只改/删单个 allocation 不得影响同行其它 allocation |
| A32 | **需求单位 ≠ 采购单位时禁止自动分配**并给出提示 | 15, 16 | **Q17**：`PURCHASE_UNIT_CONVERSION_REQUIRED` |

### 9.3 路由与菜单

菜单由 `V16` 写入 `t_menu`（`/purchase/*` + component `/business/scm/purchase/*.vue`），
前端**不新增路由文件**（复用 SmartAdmin 的动态菜单 → 路由机制，与 W1–W4 一致）。

### 9.4 前端表格 ID（Playwright 定位用）

```text
TABLE_ID_CONST.BUSINESS.SCM.PURCHASE.ORDER     = 'scm-purchase-order-table'
TABLE_ID_CONST.BUSINESS.SCM.PURCHASE.RECEIPT   = 'scm-purchase-receipt-table'
TABLE_ID_CONST.BUSINESS.SCM.PURCHASE.DEMAND    = 'scm-purchase-demand-table'
TABLE_ID_CONST.BUSINESS.SCM.PURCHASE.LOG       = 'scm-purchase-log-table'
TABLE_ID_CONST.BUSINESS.SCM.PURCHASE.WAREHOUSE = 'scm-warehouse-table'
```

---

## 10. C Frontend Migration Provenance

### 10.1 第 1 层：文件头注释块（强制）

```ts
/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-list.vue
复制日期：<YYYY-MM-DD>。Copy First + Adapt。
剪枝：明细独立列表页、resizable/列拖拽、actualAmount、RECEIVE_FLAG、询价/供应商页、裸ID输入。
适配：6 值字符串状态机、API /scm/purchase/**、scm:purchase:* 权限、4 位定点字符串、
      NULL 三态、version 乐观锁、Idempotency-Key、供应商/仓库/员工选择器、错误码映射。
验收：W5 前端单测、TS 棘轮、Playwright。 */
```

### 10.2 第 2 层：验收报告的 Provenance 表（强制）

| 目标文件 | 来源文件 | 复制日期 | 剪枝 | 适配（A# 编号） |
| --- | --- | --- | --- | --- |
| `purchase-order-list.vue` | C `purchase-list.vue` | 待填 | A12 A28 | A1 A2 A6 A8 A9 A10 A11 A13 A14 A17 A18 A22 A23 A27 A30 |
| `purchase-receipt-list.vue` | C `purchase-receive-list.vue` | 待填 | A4 | A1 A3 A6 A7 A8 A15 A16 A17 A18 A22 A23 A25 A27 A30 |
| `purchase-order-item-editable-table.vue` | C `purchase-item-list.vue` | 待填 | 独立列表页 | A1 A19 A23 |
| `purchase-demand-generate-modal.vue` | C `purchase-generate.vue` | 待填 | 库存抵扣 | A20 A21 A22 A27 |
| `purchase-const.ts` | C `purchase-const.ts` | 待填 | A5 | A2 A3 A4 |
| `purchase-order-api.ts` | C `purchase-api.ts` + `purchase-item-api.ts` | 待填 | 独立明细写端点 | A6 A7 |
| `purchase-receipt-api.ts` | C `purchase-receive-api.ts` | 待填 | `addNoOrder` / `relatePurchase` | A6 A7 |
| `purchase-demand-api.ts` | C `purchase-generate-api.ts` | 待填 | — | A6 |

### 10.3 第 3 层：机器校验（`tools/verify_w5_legacy.py`）

仿 `tools/verify_w4_legacy.py` 的断言 A–I，W5 扩展为 **A–K**（新增 J/K 两条）：

| 断言 | 内容 |
| --- | --- |
| `A_reference_unchanged` | `project-reference-examples/**` 零修改 |
| `B_frozen_migrations` | V1–V14 的 sha256 与 `w5-applied-migrations.sha256` 一致 |
| `C_frozen_domains` | `module/scm/{product,customer,supplier,pricing,order}/**` 零修改（除 `constants/index.ts` 注册） |
| `D_no_inventory_implementation` | 全仓 grep `inventory_movement` / `inventory_balance` / `stockOperateService` / `postPurchaseIn` → **0 命中**（契约名除外） |
| `E_inventory_contract_zero_callsites` | `PurchaseInventoryContract` 的方法名在 `module/scm/purchase/**` 中**只出现在 support/ 包内** |
| `F_menu_components` | V16 的 6 个 component 路径对应的 `.vue` 文件全部存在 |
| `G_provenance_headers` | 8 个复制文件头部含 `Copy First + Adapt` + 来源路径 |
| `H_role_grants` | V16 的 25 个 menu_id 与 `t_role_menu` 授权集完全一致 |
| `I_source_hashes` | C 源 6 个被复制文件的 sha256 与登记值一致 |
| `J_error_code_no_collision` | `PurchaseErrorCode`（38）+ `WarehouseErrorCode`（2）= **40** 个码与 W1–W4 全部错误码枚举无交集 |
| `K_menu_id_range` | V16 的 menu_id 全部 ∈ [701, 799]，与 W1–W4 的区间无交集 |

---

## 11. 测试矩阵

### 11.1 后端单测（无 DB，Mockito / 纯函数）

| 测试类 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `PurchaseRulesTest` | 状态机 6 状态全部转换（合法 + 非法）+ 权限判定 | 20 |
| `PurchaseAmountCalculatorTest` | `line_amount` / `total_amount` 精度（HALF_UP、scale=4、边界 14 位） | 8 |
| `PurchaseReceiptQuantityCalculatorTest` | ceiling 计算、remaining、容差 0/10/100、超收边界 + **容差配置解析（缺失回退 10 / 非法 / 越界，Q3a）** | 16 |
| `PurchaseOrderItemChangeSetTest` | 行级：保留 / 新增 / 删除行、版本缺失、SKU 重复 | 12 |
| `PurchaseOrderAllocationChangeSetTest` | **Q13**：一行两 demand、只改一条、删一条、重复 `(item,demand)`、多 demand 合计超限 | 12 |
| `PurchaseOrderValidatorTest` | 供应商/仓库启用、行非空、SKU ∈ supplier_sku | 8 |
| `PurchaseDemandAllocatorTest` | 超需求、跨 (supplier,warehouse) 冲突、按 demandId 升序、**单位不一致拒绝（Q17）** | 12 |
| `PurchaseErrorCodeTest` | **撞码门禁**：`PurchaseErrorCode` 38 + `WarehouseErrorCode` 2 = **40** 码与 W1–W4 无交集 | 1 |
| `PurchaseIdempotencyRequestHasherTest` | 键排序 / 数字归一 / 字符串归一 | 5 |
| `ScmPurchaseStatusEnumTest` | 枚举值与 DB CHECK 白名单一致 | 3 |
| `WarehouseValidatorTest` | 编码重复、停用校验 | 4 |

**小计 ≈ 101（11 个单测类）**

### 11.2 后端 PG 集成测试（`@SpringBootTest` + test profile + Flyway，`*IT`）

| 测试类 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `ScmPurchaseMigrationIT` | V15 建 9 表 / 31 索引 / 2 序列 / 2 条种子；无外键；无库存表；`ck_purchase_operation_log_owner` 生效（Q14）；`flyway_schema_history` 16 条 | 4 |
| `ScmPurchasePermissionMigrationIT` | V16 的 25 个权限码全部出现在 `@SaCheckPermission` | 2 |
| `PurchaseDemandServiceIT` | 生成去重、来源唯一索引、并发 generate 收敛、区间过滤、**`demand_date` 派生（Q6a）**、**`demand_unit_snapshot` 来源（Q17）** | 8 |
| `PurchaseDemandAllocationIT` | **Q13/Q17**：一行两 demand、只改一条、删一条、重复 `(item,demand)`、多 demand 合计超限、编辑后 status 重算、单位不一致拒绝、单位一致通过、分配唯一键 | 9 |
| `PurchaseOrderServiceIT` | create/update/submit/cancel/short-close 全链路 + **allocation 集合差量同步（Q13）** | 10 |
| `PurchaseOrderOptimisticLockIT` | 并发 submit / 并发 update | 3 |
| `PurchaseOrderIdempotencyIT` | 同 key 同 hash 重放、同 key 异 hash 409、跨操作者隔离 | 4 |
| `PurchaseReceiptServiceIT` | 多次收货累计、部分收货、跨收货单累计 | 6 |
| `PurchaseReceiptOverReceiptIT` | 容差内通过 / 超限回滚 / 容差 0 与 100 / **t_config 播种值生效（Q3a）** / 配置缺失回退 10 / 配置非法 40999 | 7 |
| `PurchaseReceiptWeightIT` | 标品拒收实重 / 非标品实重作为累计 / 称重记录只追加 | 4 |
| `PurchaseReceiptConcurrencyIT` | 两张收货单并发收同一剩余量 | 3 |
| `PurchaseReceiptReconciliationIT` | `ck_purchase_receipt_item_reconciliation` 恒等式 + `purchase_order` 状态时间戳约束 | 3 |
| `PurchaseOperationLogIT` | 12 种 operation_type 全部落库 + 全量前后快照 + 白名单 CHECK + **归属 CHECK（Q14：`DEMAND_GENERATE` 双 id 为空 / `DEMAND_ALLOCATE` 采购单 id 非空 / `RECEIPT_*` 双 id 非空）** | 5 |
| `PurchaseWarehouseIT` | 仓库种子、编码唯一、停用后不能用于新采购单 | 4 |
| `PurchaseInventoryContractAbsenceIT` | 断言 W5 无任何库存表、无任何库存写入、契约零调用点 | 2 |

**小计 ≈ 74（15 个 IT 类）**

**IT 基类**：`ScmW5PgITBase`（仿 `ScmW3PgITBase` / `ScmW4` 的基类模式）

### 11.3 后端 Web 层测试（`@WebMvcTest` + `addFilters=false`）

| 测试类 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `PurchaseOrderWebTest` | 27 端点全部可达 + 权限注解存在 + 定点数字符串序列化 | 8 |
| `WarehouseWebTest` | 5 端点 + 编码重复 | 3 |

**小计 ≈ 11**

### 11.4 前端单测（`test/*.test.mjs`，`node --experimental-strip-types --test`）

| 文件 | 覆盖 | 用例数（估） |
| --- | --- | --- |
| `w5-purchase-contract.test.mjs` | 6 值状态机转换表、`null` vs `"0.0000"` 三态、容差提示计算、`remaining`/`over`/`difference` 恒等式、幂等键保留与刷新、**allocations 集合增删改（Q13）**、**单位不一致禁用自动分配（Q17）** | 24 |

**小计 = 24**

### 11.5 E2E（Playwright，`e2e/scm-purchase.spec.ts`）

| # | 用例 | 断言 |
| --- | --- | --- |
| 1 | 从已确认销售订单生成采购需求 | 需求列表出现、来源订单号正确、需求量 = 实数量 |
| 2 | 汇总生成采购单 | 采购单列表出现、供应商/仓库正确、金额 = Σ 行金额 |
| 3 | 第一次部分收货 | 采购单状态 `PARTIALLY_RECEIVED`、行 `receivedQuantity` 累计、差异字段正确 |
| 4 | 第二次收货完成采购单 | 状态 `RECEIVED`、两条收货单、累计 = 计划量 |
| 5 | 超收超出容差被拒绝 | 提示 40989、数据回滚、行累计不变 |
| 6 | 重复提交同一收货确认 | 只累计一次（幂等） |
| 7 | 少收关单 | `PARTIALLY_RECEIVED` → `SHORT_CLOSED` + 原因必填校验 |
| 8 | 非标品实重收货 | 实重作为累计量、称重记录生成、计划量未被覆盖 |
| 9 | **一行多需求**：一个 SKU 行关联两个需求，编辑时只改其中一个 allocation | 另一个 allocation 数量不变；两个需求的 `allocated` / `status` 各自正确 |

**共 9 个用例。**

```bash
# 后端 18080 + 前端 dev 18081 必须同时在线
PLAYWRIGHT_BROWSERS_PATH=D:/DevCaches/Playwright npx playwright test e2e/scm-purchase.spec.ts
```

### 11.6 质量门禁（Step 0 交付物）

```bash
# 后端
mvn -f xsy-scm-server/pom.xml test                                   # 单测
mvn -f xsy-scm-server/pom.xml test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false   # PG IT
# 前端
cd xsy-scm-web && npm run lint && npm run build && node --experimental-strip-types --test test/*.test.mjs
python tools/ts_baseline_ratchet.py check                            # TS 棘轮（SCM 必须 0）
python tools/verify_w5_legacy.py                                     # Provenance 断言 A–K
# E2E
PLAYWRIGHT_BROWSERS_PATH=D:/DevCaches/Playwright npx playwright test
# 冻结校验
sha256sum -c docs/architecture/w5-applied-migrations.sha256
```

---

## 12. 风险

| # | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| R1 | **采购需求 + 分配使 W5 规模显著大于 W4** | 工期/回归风险 | Q6 备选：把需求延后到 W5.5；本设计按「含需求」给出，若裁决延后则删 §5.3/§5.4/§7.4 与对应表 |
| R2 | 容差配置依赖 SmartAdmin `t_config` | 收货不可用 | **V15 已播种**（`ON CONFLICT DO NOTHING`）；仍保留缺失回退默认 10（§7.5）；配置非法 → 40999 |
| R3 | 单号用 PG sequence 偏离 Native First | 架构一致性审查风险 | 已在 §7.6 记录理由（`SerialNumberInternService` 多实例不安全） |
| R4 | `warehouse` 独立成域偏离 `AGENTS.md` §6 包清单 | 文档不一致 | Q12 同步更新 `AGENTS.md` |
| R5 | 超收上限无法用静态 CHECK 表达 | DB 层缺口 | §6.5 明确登记 + 服务层强制 + IT 覆盖 |
| R6 | 一行一 SKU（M1）+ N allocations 与 spec §4.2 的「一需求拆多行」表述不同 | 与 spec 字面不符 | §5.6/§7.8 记录：**需求基数（一行多需求）已按 Q13 完整支持**，放弃的只是「同一需求跨多行」；A 源代码本身无法处理 N:N（A-D23） |
| R7 | 无库存抵扣导致「采购量 = 需求量」，可能过量采购 | 业务效率 | Q6 剪枝 A-D3；W6+ 专项设计 |
| R8 | C 前端页面基于「数字枚举 + 独立明细页 + 单 demandId」，适配面 32 项 | 回归风险 | A1–A32 逐项门禁 + `verify_w5_legacy.py` 断言 G |
| R9 | `receipt_weighing_record` 无 version/deleted，与 V2 全库纪律不同 | 审查风险 | §5.9 记录理由（只追加审计事实，同 A 源 `inventory_movement`） |
| R10 | 需求生成的日志无 `purchase_order_id` | 与 A 源 `NOT NULL` 不符 | §7.4/§5.10 改为可空 + `ck_purchase_operation_log_owner` 按 type 分支强制（Q14） |
| R11 | `purchase_order_item.received_quantity` 在无库存域时缺少「入库确认」的二次校验 | 数据一致性 | W6 实现时在 confirm 事务内调用契约，同事务原子 |
| R12 | A 源 `SHORT_CLOSED` 的「已收部分如何冲销」无口径 | 财务口径 | G1：W5 只做状态与原因，不冲销；登记后续波次 |
| R13 | **需求单位 ≠ 采购单位时无法自动分配**（W5 无换算模型） | 部分商品无法走汇总分配链路 | **Q17 明确选择拒绝而非猜系数**：返回 `PURCHASE_UNIT_CONVERSION_REQUIRED(40971)` 并在前端提示；换算能力另开波次 |
| R14 | W6 首次启用库存需回放全部历史 CONFIRMED 收货行 | 数据量大 / 重复入库 | **Q5**：稳定唯一源键（`PURCHASE_RECEIPT_ITEM` + `receipt_item_id`）+ 部分唯一索引兜底；取数 SQL 已在 §8.5 冻结 |

---

## 13. 实施顺序（已授权后按序执行）

```text
T0  质量门禁基线：跑通现有后端单测 / PG IT / 前端单测 / TS 棘轮 / W1–W4 Playwright
    —— 记录基线数字（当前：后端 120 单测 + 96 PG IT；前端 20 单测；W1–W4 Playwright 8）

T1  V15__scm_purchase.sql（9 表 + 2 序列 + 31 索引 + 2 条种子（warehouse + t_config）+ COMMENT）
T2  ScmPurchaseMigrationIT（建表/索引/序列/种子/无外键/无库存表/operation-log 归属 CHECK）

T3  purchase/constant/**（6 枚举 + PurchaseErrorCode（40 码）+ PurchaseConfigKey + PurchaseErrorCodeTest 撞码门禁）
T4  warehouse/**（entity/dao/service/query/controller/validator + WarehouseIT + WarehouseWebTest）
T5  purchase/domain/entity/**（8 Entity，含 @TableLogic 与 @Version）
T6  purchase/domain/{form,vo}/** + purchase-types 对应的 DTO
T7  purchase/manager/**（StateMachine / Validator / ItemChangeSet / AllocationChangeSet /
    AmountCalculator / QuantityCalculator（含 tolerance 纯函数）/ Allocator（含单位一致校验）/
    SnapshotFactory + 6 个单测）
T8  purchase/support/**（InventoryContract + NoOp + JsonbTypeHandler + RequestHasher
    + DemandSourceGuard + WarehouseReferenceGuard）

T9  purchase/dao/** + mapper XML（8 Dao + 8 XML；PG 方言；显式 resultMap 覆盖列名歧义）
T10 PurchaseNumberGenerator + PurchaseIdempotencyService
T11 PurchaseDemandService + PurchaseDemandController + PurchaseDemandServiceIT
    + PurchaseDemandAllocationIT（含 Q13 / Q17 用例）
T12 PurchaseOrderService（allocation 集合差量同步）+ PurchaseOrderController
    + PurchaseOrderServiceIT + OptimisticLockIT
    + IdempotencyIT
T13 PurchaseReceiptService + PurchaseReceiptController + 6 个收货 IT
T14 PurchaseQueryService + 3 个日志端点 + PurchaseOperationLogIT
T15 V16__scm_purchase_permissions.sql + ScmPurchasePermissionMigrationIT + PurchaseOrderWebTest
T16 前端：4 API + purchase-const.ts + 类型/错误/表单模型 + constants/index.ts 注册
T17 前端：5 页面 + 6 组件（Copy First + Adapt，A1–A32 逐项）
T18 前端单测 w5-purchase-contract.test.mjs + TS 棘轮 + ESLint
T19 e2e/scm-purchase.spec.ts（8 用例）+ tools/w5_e2e_accounts.py
T20 全量回归：后端单测 + PG IT + 前端单测 + build + W1–W5 Playwright + 棘轮 + ESLint
T21 tools/verify_w5_legacy.py（断言 A–K）+ docs/architecture/w5-applied-migrations.sha256
T22 验收报告 docs/architecture/2026-09-16-w5-purchase-验收报告.md（含 Provenance 表）
T23 文档同步：AGENTS.md（W5 = COMPLETE）+ 迁移审计报告 Roadmap + MEMORY.md
```

---

## 14. 最终审批（**2026-09-16 已批准**）

> 批准记录：`docs/architecture/2026-09-16-w5-purchase-approval.md`。
> Q1–Q17（含子项）**全部批准**；其中 **Q3a / Q13 / Q14 / Q17 / Q5 / Q6a 按用户指令修订后批准**。

| 编号 | 裁决项 | 最终裁决（批准形态） | 状态 |
| --- | --- | --- | --- |
| **Q1** | 是否建仓库主数据 | 建最小 `warehouse`（9 表之一），种子 1 个默认仓库 | APPROVED |
| **Q2** | 采购单状态机 | 6 状态 `DRAFT/SUBMITTED/PARTIALLY_RECEIVED/RECEIVED/SHORT_CLOSED/CANCELLED` | APPROVED |
| **Q2a** | 是否实现少收关单 | 实现（`shortClose` 命令 + `SHORT_CLOSED` 终态） | APPROVED |
| **Q3** | 超收规则 | 可配置容差（默认 10，范围 0–100）+ 超出整笔回滚 | APPROVED |
| **Q3a** | 容差配置载体 | **【修订】SmartAdmin Config**（`t_config` + `ConfigService.getConfig(String)`）；key `scm.purchase.over_receipt_tolerance_percent`；默认 10、范围 0–100；缺失回退 10；非法 → 40999；V15 播种；**不用 Dict、不建 `sys_config`、不改 `sa-base`** | APPROVED（修订） |
| **Q4** | 收货模式 | W5 只做 `DIRECT`；不建 `receipt_mode` / putaway 三列（W6 ALTER 追加） | APPROVED |
| **Q5** | Inventory 边界 | **【修订】只定义 `PurchaseInventoryContract` + NoOp，零调用点，零库存表**；并明确 W6 首次启用前的 backfill 口径与稳定唯一源键（`source_document_type='PURCHASE_RECEIPT_ITEM'` + `source_document_item_id=receipt_item_id`），使历史 backfill / 实时 confirm / 重试不可重复入库；W5 **不新增** `inventory_posted` 死字段 | APPROVED（修订） |
| **Q6** | 采购需求是否属于 W5 | 属于；剪枝 batch / `fulfilled_quantity` / 库存抵扣 | APPROVED |
| **Q6a** | 生成入参 | **【修订】半开时间段 `[startAt, endAt)`** + warehouseId + 可选 supplierId/purchaserId；**`demand_date = source_confirmed_at` 在 `Asia/Shanghai` 下的 `LocalDate`**（禁止 `date(startAt)`；跨多日窗口不得统一写成第一天）；字段名保留 `demand_date`（语义 = 来源订单确认日，**不是**生成日）；DB CHECK 强制 | APPROVED（修订） |
| **Q7** | 收货单确认口径 | 一单一次确认；不建 `purchase_receipt_confirmation(_item)` 两表 | APPROVED |
| **Q7a** | 是否保留 `PARTIALLY_CONFIRMED` | 不保留；且 `confirm` 必须提交本单全部行 | APPROVED |
| **Q8** | 单号机制 | PG sequence + `PO`/`PR` + yyyyMMdd + ≥6 位，全局递增不重置 | APPROVED |
| **Q8a** | 单号前缀 | `PO`（采购单）/ `PR`（收货单） | APPROVED |
| **Q9** | 排除项 | 采购退货 / 无单收货 / 询价比价 / 二维码协同 全部不做 | APPROVED |
| **Q10** | 数据权限 | 不做 | APPROVED |
| **Q11** | 错误码段 | **【重算】合计 40 个码**：`PurchaseErrorCode` 38（`40080–40091` 12 · `40480–40484` 5 · `40971–40972`+`40980–40995`+`40997–40999` 21）+ `WarehouseErrorCode` 2（`40485` · `40996`） | APPROVED（重算） |
| **Q12** | 文档同步 | 同步 `AGENTS.md` + 迁移审计报告 + `MEMORY.md` + `AGENTS.md §6` 补 `warehouse` | APPROVED |
| **Q13** | 采购行 ↔ 需求基数 | **【修订】保留「一行一 SKU」（`uk_purchase_order_item_order_sku_active` 保留），实现改为 N allocations**：`items[] { id · version · skuId · quantity · price · allocations[] { demandId · quantity · demandVersion } }`；allocation 身份 = `(purchase_order_item_id, purchase_demand_id)`；retained reconciliation 按 **allocation 集合**对账，禁止「一个 item 一个 allocation」算法；新增 6 类必测用例 | APPROVED（修订） |
| **Q14** | `purchase_operation_log.purchase_order_id` 可空 | **【修订】可空 + operation-type-aware CHECK** `ck_purchase_operation_log_owner`：`DEMAND_GENERATE` 双 id 为空 / `DEMAND_ALLOCATE` 仅采购单 id（由 `purchaseOrderItemId` 反查）/ `CREATE·UPDATE·SUBMIT·CANCEL·SHORT_CLOSE·DELETE` 采购单 id 非空 / `RECEIPT_*` 双 id 非空；**不用「至少一个 id 非空」** | APPROVED（修订） |
| **Q15** | `receipt_weighing_record` 无 version/deleted | 接受（只追加审计事实） | APPROVED |
| **Q16** | `receipt_weighing_record.scale_precision` | 删除（A-D4：A 源从不赋值） | APPROVED |
| **Q17** | 需求单位 vs 采购单位 | **【修订】两者分离**：`purchase_demand.demand_unit_snapshot` ← `sales_order_item.sale_unit_snapshot`；`purchase_order_item.purchase_unit_snapshot` ← `supplier_sku.purchase_unit`；相等才允许自动分配，不等 → `PURCHASE_UNIT_CONVERSION_REQUIRED(40971)`；**禁止把 100 kg 仅改单位字符串变成 100 箱**；W5 不猜换算系数，换算能力另开波次 | APPROVED（修订） |

**共 22 项**（Q1–Q17 + 子项 Q2a/Q3a/Q6a/Q7a/Q8a；与 §0.2 完全一致），**全部 APPROVED**。
批准后本文件即为 W5 编码的唯一规格依据。

---

## 15. 完成定义（DoD）

```text
[ ]  1. 22 项裁决（Q1–Q17 + Q2a/Q3a/Q6a/Q7a/Q8a）全部批准，批准记录写入 2026-09-16-w5-purchase-approval.md
[ ]  2. V15 / V16 已应用，flyway_schema_history 16 条全 success
[ ]  3. V1–V14 零修改（sha256 逐字节一致）
[ ]  4. 9 张新表 + 31 索引 + 2 序列 + 2 条种子（`warehouse` 1 条 + `t_config` 1 条）全部实测存在
[ ]  5. 全库零外键（W5 新增表）
[ ]  6. 25 条 t_menu（701–753）已建 + t_role_menu 授权一致
[ ]  7. 19 个权限码 100% 出现在 @SaCheckPermission（含 scm:warehouse:*）
[ ]  8. 27 个端点全部可达，权限注解齐全
[ ]  9. 6 状态机 + 3 需求状态 的转换表 100% 被单测覆盖（含非法转换）
[ ] 10. 8 个幂等作用域全部生效：同 key 同 hash 重放、同 key 异 hash 409、跨操作者隔离
[ ] 11. 容差规则生效：容差内通过、超出整笔回滚、`t_config` 播种值生效、配置缺失回退 10、配置无效 40999
[ ] 12. 标品/非标品分支正确：非标品实重作为累计量、计划量未被覆盖、称重记录只追加
[ ] 13. 5 个对账数量恒等式由 DB CHECK 强制（`ck_purchase_receipt_item_reconciliation`）
[ ] 14. 12 种 operation_type 全部落库且带全量前后快照；白名单 CHECK + `ck_purchase_operation_log_owner` 归属 CHECK 生效（Q14）
[ ] 15. 错误码零撞码（**40** 个码 = PurchaseErrorCode 38 + WarehouseErrorCode 2，PurchaseErrorCodeTest PASS）
[ ] 16. **零库存实现**：全仓无 inventory 表、无库存写入、契约零调用点（verify 断言 D/E PASS）
[ ] 17. 后端单测 / PG IT / Web 测试 全绿（≈101 / 74 / 11）
[ ] 18. 前端单测 24 用例全绿；TS 棘轮 PASS（SCM 0）；ESLint 0 error
[ ] 19. Playwright `scm-purchase.spec.ts` 9 用例全绿
[ ] 20. `npm run build` SUCCESS
[ ] 21. 前端 8 个复制文件 Provenance 头齐全；A1–A32 适配逐项核销
[ ] 22. `tools/verify_w5_legacy.py` 断言 A–K 全部 PASS
[ ] 23. `docs/architecture/w5-applied-migrations.sha256` 校验 PASS
[ ] 24. `project-reference-examples/xsy-scm/**` 零修改
[ ] 25. W1–W4 全量回归无退化（Playwright 8/8 + 后端既有 120 单测 + 96 IT）
[ ] 26. 验收报告含 Provenance 表 + 门禁数字 + 遗留风险
[ ] 27. AGENTS.md / 迁移审计报告 / MEMORY.md 已同步
[ ] 28. **Q13**：6 类 allocation 用例全部覆盖（一行两 demand / 只改一条 / 删一条 / 重复拒绝 / 合计超限 / 状态重算）
[ ] 29. **Q17**：需求单位与采购单位一致才允许自动分配；不一致返回 40971
[ ] 30. **Q6a**：`demand_date` 由 `source_confirmed_at` 派生（`ck_purchase_demand_date` 生效；跨多日窗口不统一为第一天）
[ ] 31. **Q5**：契约含 `SOURCE_DOCUMENT_TYPE` 常量 + §8.5 backfill 口径；W5 无 `inventory_posted` 字段
[ ] 32. **Q3a**：`t_config` 播种 1 条，`ConfigService` 读取，缺失回退 10，非法 40999
```

---

## 附录 A：本设计与 legacy 的差异总表（审查用）

| # | 项 | legacy（A 源） | W5 设计 | 理由 |
| --- | --- | --- | --- | --- |
| 1 | 库存写入 | 收货事务内 `postPurchaseIn`（`Propagation.MANDATORY`） | **零调用点契约** | H1/H2 · A-D13 |
| 2 | `inventory` / `inventory_movement` | 2 张表 | **不建** | H1/H2 |
| 3 | `purchase_receipt_confirmation(_item)` | 2 张表（多次确认） | **不建** | Q7 |
| 4 | `purchase_demand_generation_batch` | 1 张表（零调用点） | **不建** | A-D1 |
| 5 | `sys_config` | 1 张表 + 1 条种子 | **不建**，用 SmartAdmin `t_config` + `ConfigService` | A-D18 · Q3a |
| 6 | `purchase_demand.fulfilled_quantity` | 有（永不写入） | **删除** | A-D2 |
| 7 | `purchase_demand` 状态 | 5 值 | **3 值** | A-D2 |
| 8 | `purchase_receipt` 状态 | 3 值（含 `PARTIALLY_CONFIRMED`） | **2 值** | Q7a |
| 9 | 收货单 `CONFIRMED` 判据 | 整张采购单收齐 | **本收货单已提交** | A-D5 |
| 10 | `confirm` 是否允许部分行 | 允许 | **要求全行** | A-D5 · G11 |
| 11 | `receipt_mode` / putaway 三列 | 4 列 | **不建**（W6 ALTER） | Q4 |
| 12 | `receipt_weighing_record.scale_precision` | 有（从不赋值） | **删除** | A-D4 |
| 13 | `receipt_weighing_record` 的 version/deleted | 有 | **删除**（只追加） | Q15 |
| 14 | `actual_weight` 精度 | `NUMERIC`（无 scale） | **`NUMERIC(18,4)`** | P20 |
| 15 | `purchase_operation_log.purchase_order_id` | `NOT NULL` | **可空** + operation-type-aware CHECK | R10 · Q14 |
| 16 | 操作日志内容 | 只写 status/version | **全量前后快照** | A-D15 |
| 17 | 操作日志 type 白名单 | 无 CHECK | **12 值 CHECK** | A-D16 |
| 18 | 操作者 | 硬编码 `SYSTEM` | **`ScmOperator.current()`** | A-D12 · K6 |
| 19 | 幂等 scope | 不含操作者 | **拼 `ScmOperator.current()`** | A-D14 |
| 20 | 幂等实现 | 先查后插 | **INSERT 竞争** | A-D17 |
| 21 | 错误码 | 与 W4 大面积撞码 | **重分配** | A-D19 · Q11 |
| 22 | 采购行 ↔ 需求基数 | 规格 N:N，代码 1:N（`allocationByItemId` 覆盖写） | **M1（一行一 SKU）+ N allocations**（`Map<(itemId,demandId), allocation>` 集合对账） | A-D23 · Q13 |
| 23 | 需求单位 vs 采购单位 | 需求行 `purchaseUnitSnapshot` **错取 `sale_unit`** | **两者分离**：需求 = `sale_unit`（冻结不改），采购 = `supplier_sku.purchase_unit`；不等则拒绝自动分配 | A-D24 · Q17 |
| 24 | 采购单状态时间戳 | 无一致性约束 | **3 条 CHECK** | 修隐患 |
| 25 | 列表查询 | N+1（每单调 detail） | **分页 + 批量装配** | A-D11 |
| 26 | 供应商商品关系 | `supplier_sku`（V8 建） | **复用 V2 W2 的 `supplier_sku`** | 用户 §4 |
| 27 | 仓库主数据 | 有（`supplier` 包） | **独立 `warehouse` 域** | §2.1 |
| 28 | 单号 | PG sequence（已一致） | PG sequence | Q8 |
| 29 | 容差配置载体 | 自建 `sys_config` 表 | **SmartAdmin `t_config`**（`ConfigService.getConfig(String)`） | A-D18 · Q3a |
| 30 | 操作日志归属 | `purchase_order_id NOT NULL`（需求生成无单可挂） | **operation-type-aware CHECK** | Q14 |
| 31 | `demand_date` | `date(startAt)`（汇总窗口第一天） | **`source_confirmed_at` 的 Asia/Shanghai 日期** | Q6a |
| 32 | W6 库存启动口径 | 无（A 源收货事务内直接写库存） | **稳定唯一源键 + backfill 契约**（W5 只定义、不实现） | Q5 |

---

## 附录 B：文件变更总清单

### B.1 后端新增（`xsy-scm-server/sa-admin/`）

```text
src/main/resources/db/migration/V15__scm_purchase.sql
src/main/resources/db/migration/V16__scm_purchase_permissions.sql

src/main/java/net/lab1024/sa/admin/module/scm/warehouse/
  constant/ScmWarehouseStatusEnum.java · constant/WarehouseErrorCode.java
  controller/WarehouseController.java
  dao/WarehouseDao.java
  domain/entity/WarehouseEntity.java
  domain/form/{WarehouseQueryForm,WarehouseAddForm,WarehouseUpdateForm}.java
  domain/vo/WarehouseVO.java
  manager/WarehouseValidator.java
  service/{WarehouseService,WarehouseQueryService}.java

src/main/java/net/lab1024/sa/admin/module/scm/purchase/
  constant/{ScmPurchaseStatusEnum,ScmPurchaseDemandStatusEnum,ScmReceiptStatusEnum,
            ScmPurchaseOperationTypeEnum,ScmPurchaseQuantitySourceEnum,
            ScmWeighingSourceEnum,PurchaseErrorCode,PurchaseConfigKey}.java
  controller/{PurchaseDemandController,PurchaseOrderController,PurchaseReceiptController}.java
  dao/{PurchaseDemandDao,PurchaseDemandAllocationDao,PurchaseOrderDao,PurchaseOrderItemDao,
       PurchaseReceiptDao,PurchaseReceiptItemDao,ReceiptWeighingRecordDao,
       PurchaseOperationLogDao}.java
  domain/entity/{PurchaseDemandEntity,PurchaseDemandAllocationEntity,PurchaseOrderEntity,
                 PurchaseOrderItemEntity,PurchaseReceiptEntity,PurchaseReceiptItemEntity,
                 ReceiptWeighingRecordEntity,PurchaseOperationLogEntity}.java
  domain/form/  （17 个 Form，见 §7.2）
  domain/vo/    （6 个 VO，见 §7.2）
  manager/{PurchaseOrderValidator,PurchaseOrderItemChangeSet,PurchaseOrderAllocationChangeSet,
           PurchaseOrderStateMachine,PurchaseDemandAllocator,PurchaseReceiptQuantityCalculator,
           PurchaseAmountCalculator,PurchaseSnapshotFactory}.java
  service/{PurchaseDemandService,PurchaseOrderService,PurchaseReceiptService,
           PurchaseQueryService,PurchaseNumberGenerator,PurchaseIdempotencyService}.java
  support/{PurchaseInventoryContract,NoOpPurchaseInventoryContract,
           PurchaseJsonbTypeHandler,PurchaseIdempotencyRequestHasher,
           PurchaseDemandSourceGuard,PurchaseWarehouseReferenceGuard}.java

src/main/resources/mapper/scm/purchase/*.xml（8 个）
```

### B.2 后端测试新增

```text
src/test/java/.../scm/purchase/  （15 个 IT + 11 个单测 + 2 个 Web 测试，见 §11）
src/test/java/.../scm/warehouse/ （WarehouseIT + WarehouseWebTest）
src/test/java/.../scm/purchase/ScmW5PgITBase.java
```

### B.3 前端新增（`xsy-scm-web/src/`）

```text
src/api/business/scm/{purchase-demand-api,purchase-order-api,purchase-receipt-api,warehouse-api}.ts
src/constants/business/scm/purchase-const.ts
src/views/business/scm/purchase/
  purchase-types.ts · purchase-errors.ts · purchase-form-model.ts
  purchase-demand-list.vue · purchase-order-list.vue · purchase-receipt-list.vue
  purchase-log-list.vue · warehouse-list.vue
  components/purchase-demand-generate-modal.vue · components/purchase-order-form-drawer.vue
  components/purchase-order-item-editable-table.vue · components/purchase-order-detail-drawer.vue
  components/purchase-receipt-form-drawer.vue · components/purchase-receipt-confirm-modal.vue
```

### B.4 前端修改（**最小化**）

```text
src/constants/index.ts           注册 purchase-const（1 处 import + 1 处 export）
src/constants/table-id-const.ts  新增 5 个采购表格 ID（若该文件存在）
```

### B.5 E2E 与工具

```text
xsy-scm-web/e2e/scm-purchase.spec.ts
xsy-scm-web/test/w5-purchase-contract.test.mjs
tools/w5_e2e_accounts.py
tools/verify_w5_legacy.py
docs/architecture/w5-applied-migrations.sha256
docs/architecture/2026-09-16-w5-purchase-approval.md
docs/architecture/2026-09-16-w5-purchase-验收报告.md
```

### B.6 **零修改清单（冻结，必须验证）**

```text
xsy-scm-server/sa-admin/src/main/resources/db/migration/V1..V14.sql       （14 个）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/common/**            （全部）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/product/**           （W1）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/customer/**          （W2）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/supplier/**          （W2）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/pricing/**           （W3）
xsy-scm-server/sa-admin/src/main/java/.../module/scm/order/**             （W4）
xsy-scm-server/sa-base/**                                                 （SmartAdmin 底座）
xsy-scm-web/src/views/business/scm/{product,customer,supplier,pricing,order}/**  （W1–W4）
xsy-scm-web/src/layout/** · src/router/**（core）· src/lib/** · src/components/system/**
project-reference-examples/xsy-scm/**                                     （只读资产库）
```
