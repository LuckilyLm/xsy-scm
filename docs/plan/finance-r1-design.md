# Finance R1 规划与正式设计（P3 / F1-0.5 收口版）

> 状态：**F1-0.5 已收口；D-1…D-5 全部为 A，本文再无待裁决项，F1-1 可开工**。本文是 Finance R1 的
> **唯一规划文档**：调研结论摘要（§0.1）、裁决索引（§1.3）、正式设计（§2–§25）、
> D-1…D-5 收口落点（§26）、R0 接轨（§27）与实施计划（§24）全部在此。
> **27 条 Q 裁决、10 条全局不变量与 D-1…D-5 的权威全文在 `docs/decisions.md`
> 「P3 Finance R1 裁决（2026-09-25）」及其「第三批（2026-09-26）」**，
> 本稿只索引与落实，不重复裁决原文。
> 2026-09-25 的四份过程稿（调研稿、第一批裁决表、第二批裁决表、最终裁决表）已并入本稿后删除，
> 裁决前的候选并列过程保留在 git 历史（提交 `76904ea`）；D-1…D-5 的候选并列过程保留在提交 `caace54a`。
> 基线：`origin/main @ caace54a`（2026-09-26 重扫确认 `db/migration/` 最大 **V64**、`t_menu` 最大
> **1421**、SCM 错误码最大 **41128**）。选号与选迁移版本前必须重新 `git fetch` 并重扫。
>
> 依据（按优先级）：当前代码与数据库 → `docs/decisions.md` P3 裁决 → 本稿 →
> `docs/requirements/产品功能需求基线.md`。**不参考**蔬东坡或其他系统增加任何功能。
>
> 硬约束：27 条 Q 裁决 + D-1…D-5 全部已收口；本稿**只落实裁决，不新增裁决未要求的对象、状态、权限或列**。
> 实现期若发现必须补规则，回到 `docs/decisions.md` 追加裁决，不在本稿或代码里就地决定。

## 0. 设计总则

1. **Finance R1 是财务事实的产生地**，不是第二套业务账：它只消费已经成立的业务事实
   （签收、收货确认、退货批准、退款业务事实），产生自己的事实（应收 / 应付 / 收款 / 付款 / 核销 / 日志）。
2. **财务域对业务域只读**。`module/scm/finance` 内允许对 `sales_order(_item)`、`order_return(_item)`、
   `order_refund`、`purchase_receipt(_item)`、`purchase_order(_item)`、`inventory_outbound(_item)`、
   `delivery_route_order` 建**只读 DAO**（仅 SELECT）；**禁止**出现对这些表的任何 INSERT / UPDATE / DELETE，
   也禁止对 `inventory_balance / inventory_movement / inventory_reservation` 的任何写。
   理由：触发路径有两条（签收生成正常应收、签收补生成红字），若按「业务域装配金额」的契约形态，
   两条路径会各算一遍金额并可能不一致；把取数收进财务域、把防重交给库级唯一索引，才能保证单一口径。
   这与 `report` 域只读跨域表的既有形态同形，但比它多一条「只读」的库级纪律（见 §25 不变量 4/5）。
3. **财务单据没有状态机**（第二批 Q17 / Q20）：已核销额、未核销额、结清状态、可冲金额全部
   由有效核销关系与红字关系**读时派生**，不落状态列、不落余额列。
4. **方向编码在类型里，数量与金额恒为正**：沿用 `inventory_movement` 的纪律
   （「数量恒为正、方向编码在类型里」，`docs/decisions.md` 盘点段）。财务侧对应物是
   `entry_type IN ('NORMAL','RED')` 与 `entry_type IN ('NORMAL','REVERSE')`，金额列一律 `> 0`，
   净值在读时按方向相减。
5. **不冗余业务归属列**：`seller_id` / `purchaser_id` 是**范围归属**，必须读时 join 业务表取活值
   （P0 裁决 2「没有授权范围就查不到数据」要求范围随授权即时生效，落库即会漂移）；
   `customer_id` / `supplier_id` 是**结算对方**，属财务事实本身，落库（与 `purchase_receipt.supplier_id` 同形）；
   名称一律快照列（与 `supplier_name_snapshot` 同形），只服务展示与导出。
6. **零既有表改动**：本设计不 ALTER 任何现有表。所有关联都是新表上的新列指向既有主键。
   因此 F1-1 不触碰 `MIG/V13/V15/V25/V42/V60/V63` 的已应用字节，迁移校验和守卫不受影响。
7. **append-only 由数据库 CHECK 兜底，不只靠 Java 纪律**：八张表全部带
   `CHECK (deleted = FALSE)`（照 `V19:83` 的 `ck_inventory_movement_append_only`），
   软删历史财务事实在库层直接失败；`finance_operation_log` 不提供任何删除入口。
   `deleted` 列保留只为与部分唯一索引的既有谓词形态一致。
   实体**不用** `@TableLogic`（它表达「可被软删的实体」，与财务事实语义相反，
   照 `InventoryMovementEntity` 的既有取舍），读取一律在 SQL 里显式写 `deleted = FALSE`。

### 0.1 调研结论摘要（2026-09-25 开工前调研，过程稿已并入本稿）

**销售履约链**：`sales_order`（CONFIRMED 时冻结 `settlement_line_amount = actual_quantity × locked_unit_price`，
`SalesOrderService.java:296`）→ 分拣（`sorted_quantity`，不回写订单）→ 发车单事务产生
`inventory_outbound`（直生 CONFIRMED、一线路一单）与 `inventory_outbound_item`（带 `sales_order_item_id`、
**无价格列**）→ `inventory_movement(SALES_OUT)`（`unit_cost` = 出库时库存均价，**不是售价**）
→ 订单级 `SIGNED / EXCEPTION` 签收（只写履约四列，零金额零库存）。

**采购入库链**：`purchase_order.total_amount` 按**下单量**算且收货不改 → `purchase_receipt` CONFIRMED
（`received_at` 与 `confirmed_at` 同值）→ `purchase_receipt_item`（**无价格列**；有效量 = 标品申报量 /
非标 `actual_weight`）→ `inventory_movement(PURCHASE_IN)`（`unit_cost` = `purchase_order_item.purchase_price`）。
**采购退货全库不存在**；已确认收货单不可改不可删。

**四个承重结论**：

1. 应收与应付的金额都**不是库里现成的一列**，「量 × 价」必须新落库为财务事实（§12 公式）。
2. 订单结算量、分拣实发量、出库实发量三者天然不等；应付侧「收货确认」与「正式入库」金额相同、只差时点。
3. 全库 64 个迁移逐关键词检索：应收 / 应付 / 收款 / 付款 / 核销 / 凭证 / 资金账户 **0 命中**，R1 是纯绿地；
   SmartAdmin 原生 `t_oa_invoice` / `t_oa_bank` 属 OA 示例域，不作财务地基。
4. 数据范围现有五维全是负责人 / 仓库语义，**无结算对方维**；`purchase_receipt` 上无 `purchaser_id`
   （应付按采购员收窄须回溯父采购单）。

**R0 影子口径（不得改名、不得改读 finance 表）**：`confirmedOrderAmount`（`ReportDao.xml:139`）、
`receiptReferenceAmount`（:512/:907，与「收货确认时应付」逐字同值）、`inboundCostAmount`（:534，且是供应商 TOP10 排序键）。
报表 SQL 完全不读配送与分拣表；实测 37 个只读端点（26 查询 + 11 导出），
`AGENTS.md` / `progress.md` 所称「41 个只读端点」与实现不符，待文档收口时订正。

**可复用基建**：`idempotency_record` 三段式（`claim → 写 → complete` 同事务，重放返回首次结果）、
`@Version` + `VERSION_CONFLICT(40921)`、`SELECT … FOR UPDATE` 与「单据锁先于余额锁、余额锁按
`(warehouse_id, sku_id)` 升序」纪律、`order_operation_log` 的 JSONB before/after + 白名单 + 唯一写入口、
`ScmDataScopeService` / `ScmValueScope` / `ScmWarehouseScopeGuard`、`ScmReportTimeRangeResolver`、
FastExcel 导出层（`ScmReportExcel` 形态）、`ScmDocumentNumbers.format`（前缀 + 业务日 + `%06d`）、
菜单种子四条约定（`menu_id == sort`、`context_menu_id == parent`、`api_perms == web_perms`、`perms_type = 1`）。

## 1. 范围 / 明确不做

### 1.1 本期做（主线计划 §5 九项）

应收、应付、收款、付款、核销、财务操作日志、财务权限、财务明细查询、财务明细导出。

### 1.2 明确不做

- **Finance R2**：客户利润、商品利润、订单利润、销售毛利、应收账龄、应付账龄、客户对账、供应商对账、
  财务分析；`due_date`、账期计算、供应商结算主数据（第二批 Q15）。
- **P5**：满减、满赠、优惠券、限时特价、在线支付、余额充值、余额支付、商城账期支付、商城货到付款、
  营销活动；收款方式枚举中不得出现 `ONLINE_PAYMENT / BALANCE / COD / RECHARGE`（第二批 Q21）。
- **不扩展**：GPS、自动路线优化、司机 App、自动采购、绩效、智能推荐、参考系统独有能力。
- **不做**：会计凭证与总账、发票与税额（第二批 Q14）、多币种、坏账准备、
  财务审批状态机 / `approver` / 待办（第二批 Q20）、财务回单附件与 `scm:finance:attachment:query`
  （第二批 Q25）、字段级金额裁剪与 masking（第二批 Q24）、`t_dict` 字典（第二批 Q21）、
  幂等基建重构 / TTL / cleanup job（第二批 Q26）、退货库存处理与 `RETURN_IN`、部分签收模型（第二批 Q27）。
- **不修改**任何既有业务行为：订单状态机与 `settlement_*`、分拣、配送、库存、采购、退货退款的状态机与金额。

### 1.3 裁决索引（权威全文在 `docs/decisions.md`「P3 Finance R1 裁决（2026-09-25）」及其「第三批正式裁决（D-1 … D-5，2026-09-26）」）

| Q | 一句话结论 | 本稿落点 |
| --- | --- | --- |
| Q1 | 应收在订单 `SIGNED` 时形成；不新增财务确认环节 | §3.3 |
| Q2 | 应收数量 = `inventory_outbound_item.quantity` | §3.3、§12 |
| Q3 | 应收单价 = `sales_order_item.locked_unit_price`，无第二价格源 | §12 |
| Q4 | 应收 = 订单级单头 + 行级明细（保留出库行来源） | §3 |
| Q6 | `EXCEPTION` 不形成应收；不新增部分签收 | §3.3、§9 |
| Q9 | 应付在 `purchase_receipt CONFIRMED` 时形成 | §4.3 |
| Q10 | 应付 = 收货单级单头 + 收货行级明细 | §4 |
| Q11 | 合法容差内超收全额计入应付 | §4.2、§12 |
| Q13 | 应付减少 = 红字应付（引用原单、必填原因、留日志） | §8.3 |
| Q14 | `purchase_price` = 采购结算单价，不赋税务语义 | §12 |
| Q16 | 允许预收 / 预付；未核销部分 = 待核销款（≠ 余额 / 钱包） | §5、§6、§7 |
| Q17 | 核销 M:N，独立 `WriteOff`；已核销 / 结清读时派生；禁跨客户 / 供应商 | §7 |
| Q18 | 核销撤销 = 反向核销事实；无会计期间 / 关账 | §8.4 |
| Q19 | `order_refund.COMPLETED` ≠ 资金付出；退款付款 = 独立 `Payment`，来源库级唯一 | §6、§9 |
| Q5 | 手工出库不产生应收，不建观察列表 | §3.3、§1.2 |
| Q7 | 补单独立生成应收，不冗余 `original_order_id` | §3 |
| Q8 | 签收但无出库行：不生成单头 / 明细 / 0 元事实 | §3.3 |
| Q12 | 少收差异不形成财务事实，采购域为唯一事实源 | §4.3 |
| Q15 | 不存 `due_date`、不补供应商账期、不做账龄 | §1.2、§4 |
| Q20 | 无财务审批状态机；破坏性动作 = 独立权限 + 原因 + 人 + 时 + 日志 + 并发保护 | §8、§16 |
| Q21 | 方式枚举 `CASH / BANK_TRANSFER / OTHER`，Java enum + CHECK，不用 `t_dict` | §5、§6 |
| Q22 | 财务事实一律 `NUMERIC(18,4)`、scale 4、`HALF_UP` | §12 |
| Q23 | 不新增范围维；应收 `orderSellerScope`、应付 `purchaserScope`（回溯父单） | §15 |
| Q24 | 有单据查询权即可见金额；不新增字段级裁剪 | §16 |
| Q25 | 本期不启用财务回单附件，只存文本凭据号 | §5、§6 |
| Q26 | 复用 `idempotency_record`；请求级幂等 + 来源唯一索引双保险 | §11、§13 |
| Q27 | `order_return APPROVED` = 红字应收来源；先退后签由签收补生成；Refund 不再冲应收 | §8.2、§9 |

第三批 D-1…D-5（2026-09-26 负责人裁决，全部为 A）：

| D | 一句话结论 | 本稿落点 |
| --- | --- | --- |
| D-1 | 不回填上线前既有的 SIGNED / CONFIRMED / APPROVED 事实；本期无补生成 API 与回填权限 | §19 |
| D-2 | 自动红字的可生成额度**不扣除**既有核销额；Finance 不反向控制订单域状态机 | §7、§8.2 |
| D-3 | Receipt / Payment 纠错 = append-only 反向事实（`entry_type` + `reverse_of_id` + `reason`），反向前已用额必须为 0 | §5、§6、§6.3、§16 |
| D-4 | 少拣导致的超额合法退货**全额**生成 RED Receivable；净应收可为负，以 `openAmount` / `overAppliedAmount` 两个只读派生值表达 | §7、§8.2、§22 |
| D-5 | 收款按 `customerSellerScope`；付款供应商侧沿用采购团队共享读、客户侧同收款；核销随 target；禁止角色 bypass | §15 |

10 条全局不变量见 `docs/decisions.md` 同节，本稿 §25.1 逐条复述为可实现断言。

## 2. 财务领域对象

```text
业务事实（只读消费）                        财务事实（R1 产生）
sales_order / sales_order_item ──┐
delivery_route_order(SIGNED)  ───┼──→ finance_receivable ──→ finance_receivable_item
inventory_outbound_item       ───┘            │
order_return(APPROVED)        ────────────→ finance_receivable(entry_type='RED')
                                              │
purchase_receipt(CONFIRMED) ──→ finance_payable ──→ finance_payable_item
purchase_receipt_item       ──┘
                                              │
finance_receipt（收款，人工登记）─────────────┤
finance_payment（付款，人工登记 / 退款触发）───┤
                                              ↓
                                     finance_write_off（M:N 核销关系行）
                                              ↓
                                     finance_operation_log（财务操作日志）
```

对象清单（**8 张新表** + 5 条序列）：

| 对象 | 表 | 粒度 | 触发方 | 方向字段 |
| --- | --- | --- | --- | --- |
| 应收单 | `finance_receivable` | 一张 `sales_order` 一单（第一批 Q4） | 签收事务内生成 | `entry_type NORMAL/RED` |
| 应收明细 | `finance_receivable_item` | 一条 `inventory_outbound_item` 一行（正常）；一条 `order_return_item` 一行（红字） | 同上 | 随单头 |
| 应付单 | `finance_payable` | 一张 `purchase_receipt` 一单（第一批 Q10）；红字应付是**另一张** `entry_type='RED'` 的单 | 收货确认事务内生成 / 手工登记 | `entry_type NORMAL/RED` |
| 应付明细 | `finance_payable_item` | 一条 `purchase_receipt_item` 一行 | 同上 | 无 |
| 收款 | `finance_receipt` | 一笔钱一单，可无应收（预收，第一批 Q16） | 财务人工登记 | `entry_type NORMAL/REVERSE`（D-3） |
| 付款 | `finance_payment` | 一笔钱一单，可无应付（预付）；退款付款带来源 | 财务人工登记 | `entry_type NORMAL/REVERSE`（D-3） |
| 核销 | `finance_write_off` | 一笔收/付款对一笔应收/应付的一次分配 | 财务人工 | `entry_type NORMAL/REVERSE` |
| 财务日志 | `finance_operation_log` | 一次财务写动作一行 | 与业务写同事务 | `operation_type` 白名单 |

应付的「红字」按第一批 Q13 是**一张新的 `finance_payable`（`entry_type='RED'`）**，
与应收红字同形；因此 `finance_payable` 也带 `entry_type` 与 `original_payable_id`。

## 3. Receivable / ReceivableItem

### 3.1 `finance_receivable`

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY PK` | |
| `receivable_no` | `VARCHAR(64) NOT NULL`，唯一索引 `WHERE deleted = FALSE` | 前缀 `AR` + 业务日 + 6 位序号（§21） |
| `source_type` | `VARCHAR(32) NOT NULL CHECK IN ('SALES_ORDER','ORDER_RETURN')` | 正常应收 / 红字应收的来源 |
| `source_id` | `BIGINT NOT NULL` | 订单 id / 退货单 id |
| `order_id` | `BIGINT NOT NULL` | 红字也指向被冲订单，便于按单查询 |
| `customer_id` | `BIGINT NOT NULL` | 结算对方（财务事实，落库） |
| `customer_name_snapshot` | `VARCHAR(150) NOT NULL` | 展示用快照 |
| `entry_type` | `VARCHAR(8) NOT NULL CHECK IN ('NORMAL','RED')` | 方向编码在类型里 |
| `original_receivable_id` | `BIGINT`，CHECK：`entry_type='RED'` 时 NOT NULL，`NORMAL` 时 NULL | 红字必须引用原应收（第一批 Q13/Q27） |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | 单头金额 = 明细之和；恒为正 |
| `event_at` | `TIMESTAMPTZ NOT NULL` | 业务事件时点：正常 = `signed_at`；红字 = 退货 `approved_at` |
| `reason` | `VARCHAR(500)`，CHECK：`RED` 时必填非空 | 红字原因（第一批 Q13） |
| `version` / `deleted` / 审计四列 | 同既有范式 | |

索引：

```text
uk_finance_receivable_source_active   ON (source_type, source_id) WHERE deleted = FALSE
idx_finance_receivable_order          ON (order_id)               WHERE deleted = FALSE
idx_finance_receivable_customer_event ON (customer_id, event_at DESC) WHERE deleted = FALSE
idx_finance_receivable_original       ON (original_receivable_id) WHERE deleted = FALSE AND entry_type = 'RED'
```

**不存**：`seller_id`（范围归属，读时 join `sales_order`）、`status`（结清派生，Q17）、
`settled_amount`（第二权威源，Q17 禁止）、`due_date`（Q15）。

### 3.2 `finance_receivable_item`

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `id` | PK | |
| `receivable_id` | `BIGINT NOT NULL` | |
| `source_type` | `VARCHAR(32) NOT NULL CHECK IN ('INVENTORY_OUTBOUND_ITEM','ORDER_RETURN_ITEM')` | 正常 / 红字明细来源 |
| `source_id` | `BIGINT NOT NULL` | `inventory_outbound_item.id` / `order_return_item.id` |
| `order_item_id` | `BIGINT NOT NULL` | 行级追溯（第一批 Q4） |
| `sku_id` / `sku_name_snapshot` / `unit_snapshot` | 同既有快照范式 | 展示与对账 |
| `quantity` | `NUMERIC(18,4) NOT NULL CHECK (quantity > 0)` | 正常 = 出库量；红字 = 退货批准量 |
| `unit_price` | `NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0)` | 正常 = `locked_unit_price`；红字 = 退货行锁定单价 |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount >= 0)` | 正常 = `round(quantity × unit_price, 4)`；红字 = `order_return_item.approved_amount`（订单域已算，财务不重算） |
| `version` / `deleted` / 审计四列 | | |

索引：`uk_finance_receivable_item_source_active ON (source_type, source_id) WHERE deleted = FALSE`、
`idx_finance_receivable_item_receivable ON (receivable_id) WHERE deleted = FALSE`、
`idx_finance_receivable_item_order_item ON (order_item_id) WHERE deleted = FALSE`。

**红字明细不存 `original_receivable_item_id`（2026-09-25 评审修订）**：
P2 已明确一条 `sales_order_item` 未来可能对应多条 `inventory_outbound_item`
（`V63:29-33` 刻意不建 `(sales_order_item_id)` 唯一索引），因此**不存在唯一的原正常明细行**，
不得假设 1:1。红字明细的行级追溯链为
`RED receivable → original_receivable_id（单头级）` +
`RED receivable_item → source(order_return_item) → order_item_id`；
`order_item_id` 上的聚合只服务行级追溯与展示 —— 自动红字**不做可冲上限校验**（D-2 / D-4，见 §8.2）。
**不新增 allocation 表**（评审意见三）。

**来源锚点用 `inventory_outbound_item.id` 而不是 `sales_order_item_id`**：
`V63:29-33` 注释明确出库行**刻意不建** `(sales_order_item_id)` 唯一索引
（一条订单行将来可能被再出一行），所以行级唯一只能挂在出库行主键上。

### 3.3 生成规则（正常应收）

触发：`DeliveryRouteService.sign` 将某订单置 `SIGNED` 的**同一事务**内、且**只在
`queries.markSigned(...) == 1` 之后**调用
`FinanceReceivableService.generateOnSign(deliveryRouteOrderId)`（F1-2B 落地形态）。

> **签名与规划稿不同，且刻意不同**：原写 `(orderId, signedAt, operator)`，但 `markSigned` 的
> `signed_at` 是数据库时钟 `now()`，调用方手里没有这个值 —— 由 Java 侧传 `now()` 会让
> `receivable.event_at` 成为一个比签收时刻更晚的近似值，违反 §3.1「event_at = 签收时刻」。
> 因此入参只有 `delivery_route_order.id`，时点与签收人由财务侧只读 DAO 回读该行的
> `signed_at` / `signed_by`。`markSigned` 返回 0 时既有 `VERSION_CONFLICT` 语义不变，
> 不触发财务生成。

1. 读 `delivery_route_order`（已 `SIGNED`）→ 取 `order_id`；
2. 读该订单的 `inventory_outbound_item`（`sales_order_item_id IS NOT NULL`）；
   **若为空：直接 return，不生成单头、不生成明细、不生成 0 元事实**（第二批 Q8，跳过是成功语义）；
3. 逐行 join `sales_order_item` 取 `locked_unit_price`，`amount = round(quantity × locked_unit_price, 4, HALF_UP)`；
4. `单头 amount = Σ 明细 amount`；若合计为 0（全部单价 0 的极端情形）同样跳过，不生成 0 元事实；
5. 插入单头 + 明细，`insertOnConflictDoNothing` 语义：来源唯一索引命中即视为已生成，直接返回（幂等）。
6. 读该订单**此前已 `APPROVED` 且尚未生成红字应收**的 `order_return`，逐单执行 §8 的红字生成
   （第二批 Q27 的 ①②③）。
   **F1-2C 落地形态**：`generateOnSign` = 「确保正常应收」+「按 `order_return.id` 升序遍历该订单全部
   `APPROVED` 退货并逐张调用同一个 `generateRed` 实现」，红字算法只有一份；正常应收已存在时
   重跑 `generateOnSign` 会把漏掉的红字补回来（可重放即自然收敛，不是回填 API）。

不在 `EXCEPTION` 分支调用（第二批 Q6：`EXCEPTION` 不形成应收）。

## 4. Payable / PayableItem

### 4.1 `finance_payable`

列与 `finance_receivable` 同形，差异：

| 列 | 取值 |
| --- | --- |
| `payable_no` | 前缀 `AP` |
| `entry_type` | `VARCHAR(8) NOT NULL CHECK IN ('NORMAL','RED')` |
| `source_type` | `VARCHAR(32) NOT NULL CHECK IN ('PURCHASE_RECEIPT','MANUAL')`；与 `entry_type` 配对 CHECK：`NORMAL → 'PURCHASE_RECEIPT'`、`RED → 'MANUAL'`（2026-09-25 评审意见二） |
| `source_id` | `BIGINT`；`NORMAL` 时 NOT NULL = `purchase_receipt.id`；`RED`（手工调整）时**必须 NULL** |
| `original_payable_id` | `BIGINT`；`RED` 时 NOT NULL 指向被冲原应付，`NORMAL` 时必须 NULL |
| `purchase_order_id` | `BIGINT NOT NULL`（红字也指向原采购单） |
| `supplier_id` / `supplier_name_snapshot` | 结算对方 |
| `amount` / `event_at` | 同应收；`event_at` = `purchase_receipt.confirmed_at` |
| `reason` | `VARCHAR(500)`；`RED` 时必填非空（第一批 Q13） |

**不存** `purchaser_id`（范围归属，读时 join `purchase_receipt → purchase_order`，第二批 Q23 指定路径）、
`warehouse_id`（应付不按仓收窄）、`status`、`settled_amount`、`due_date`。

索引：

```text
uk_finance_payable_source_active    ON (source_type, source_id)
                                    WHERE deleted = FALSE AND source_id IS NOT NULL
idx_finance_payable_supplier_event  ON (supplier_id, event_at DESC) WHERE deleted = FALSE
idx_finance_payable_order           ON (purchase_order_id)          WHERE deleted = FALSE
idx_finance_payable_original        ON (original_payable_id)        WHERE deleted = FALSE AND entry_type = 'RED'
```

**来源唯一索引的谓词必须带 `AND source_id IS NOT NULL`（§3.1 修正，全文一致）**：
手工红字应付的 `source_type='MANUAL'` 且 `source_id=NULL`，若谓词只写 `deleted = FALSE`，
PostgreSQL 会因 `NULL` 不等于任何值而**放行任意多条手工红字**，索引形同不存在；
反过来说，它本来也不该约束手工来源 —— 手工红字的防重由「可冲上限 + 请求级幂等键」承担（§8.3）。
`finance_payable_item` 同形。

### 4.2 `finance_payable_item`

| 列 | 语义 |
| --- | --- |
| `source_type` | `CHECK IN ('PURCHASE_RECEIPT_ITEM','MANUAL')`；与单头 `entry_type` 配对 CHECK：`NORMAL → 'PURCHASE_RECEIPT_ITEM'`、`RED → 'MANUAL'` |
| `source_id` | `purchase_receipt_item.id`；`MANUAL` 时**必须 NULL**，来源唯一索引的 `source_id IS NOT NULL` 谓词将其排除 |
| `purchase_order_item_id` | 回溯采购行（第一批 Q10） |
| `quantity` | = `purchase_receipt_item.received_quantity`（有效量，含合法容差内超收，第一批 Q11） |
| `unit_price` | = `purchase_order_item.purchase_price`（结算单价语义，第一批 Q14，不赋税务含义） |
| `amount` | `round(quantity × unit_price, 4)` |

### 4.3 生成规则

触发：`PurchaseReceiptService.confirm` 将收货单置 `CONFIRMED` 的**同一事务**内，调用
`FinancePayableService.generateOnReceiptConfirm(receiptId, confirmedAt, operator)`。
`DIRECT` 与 `WAREHOUSE_CONFIRM` 都在 confirm 时生成（第一批 Q9：putaway 不决定应付时点）。
金额按**实际确认收货有效量**计算；少收未交部分不产生任何事实（第二批 Q12）。

## 5. Receipt（收款）

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `receipt_no` | 前缀 `RC`，唯一 | |
| `customer_id` / `customer_name_snapshot` | NOT NULL | 收款对象恒为客户（预收也来自客户，第一批 Q16） |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | 一笔钱一单；**反向行金额同样恒正**，方向由 `entry_type` 表达（D-3） |
| `method` | `VARCHAR(16) NOT NULL CHECK IN ('CASH','BANK_TRANSFER','OTHER')` | 第二批 Q21 固定三值，Java enum + CHECK |
| `received_at` | `TIMESTAMPTZ NOT NULL` | 收款时点，登记人填写、必填；服务端不做「不得晚于当前」之类的额外校验（与既有业务时点列同形） |
| `entry_type` | `VARCHAR(8) NOT NULL CHECK IN ('NORMAL','REVERSE')` | D-3：登记纠错的反向事实 |
| `reverse_of_id` | `BIGINT`；CHECK：`REVERSE` 时 NOT NULL、`NORMAL` 时**必须 NULL** | 指向被冲的原 `NORMAL` 收款 |
| `reason` | `VARCHAR(500)`；CHECK：`REVERSE` 时必填非空 | 纠错原因（D-3） |
| `external_reference` | `VARCHAR(128)`，**普通索引**（不唯一） | 资金凭据号，只是文本（第二批 Q25 只存文本不挂附件；唯一性从未被裁决，见下方说明） |
| `remark` | `VARCHAR(500)` | |
| `version` / `deleted` / 审计四列 | | `deleted` 受 `CHECK (deleted = FALSE)` 锁死（§0 第 7 条） |

**无来源列**：收款一律人工登记；与应收的关系只通过 `finance_write_off` 表达（Q16 允许无应收的预收）。
**无状态列**：待核销余额读时派生（Q17）。

索引：

```text
idx_finance_receipt_customer_received  ON (customer_id, received_at DESC)  WHERE deleted = FALSE
idx_finance_receipt_external_ref       ON (external_reference)             WHERE deleted = FALSE AND external_reference IS NOT NULL
uk_finance_receipt_single_reverse      ON (reverse_of_id)                  WHERE deleted = FALSE AND entry_type = 'REVERSE'
```

**`external_reference` 刻意不建唯一索引（§3.2 修正）**：它只是资金凭据文本，
「全系统唯一」从未被裁决，而银行流水号在不同客户、不同账户间重复是真实存在的。
把它当唯一键会让第二笔合法收款因为凭据号撞号而登不进去，且错误信息指向一个与业务无关的列。
真正的防重复由两层承担，都与它无关：人工登记命令走请求级 `Idempotency-Key`（§13），
退款付款走 `Idempotency-Key` + `uk_finance_payment_source_active` 的 `(source_type, source_id)`
库级唯一索引（§6）。**不得拿 `external_reference` 当幂等键。**

## 6. Payment（付款）

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `payment_no` | 前缀 `PM`，唯一 | |
| `counterparty_type` | `VARCHAR(16) NOT NULL CHECK IN ('SUPPLIER','CUSTOMER')` | 付款给供应商（应付/预付）或客户（退款，第一批 Q19） |
| `counterparty_id` / `counterparty_name_snapshot` | NOT NULL | |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | **反向行金额同样恒正**（D-3） |
| `method` | 同收款三值 | |
| `paid_at` | `TIMESTAMPTZ NOT NULL` | |
| `entry_type` | `VARCHAR(8) NOT NULL CHECK IN ('NORMAL','REVERSE')` | D-3 |
| `reverse_of_id` | `BIGINT`；CHECK：`REVERSE` 时 NOT NULL、`NORMAL` 时**必须 NULL** | 指向被冲的原 `NORMAL` 付款 |
| `reason` | `VARCHAR(500)`；CHECK：`REVERSE` 时必填非空 | 纠错原因（D-3） |
| `external_reference` | `VARCHAR(128)`，**普通索引**（不唯一，同收款） | 资金凭据文本 |
| `source_type` | `VARCHAR(32) CHECK IN ('ORDER_REFUND')`，可空 | 退款付款的来源（第一批 Q19）；`REVERSE` 时**必须 NULL** |
| `source_id` | `BIGINT`，与 `source_type` 成对 CHECK | `order_refund.id`；`REVERSE` 时**必须 NULL** |
| `remark` / `version` / `deleted` / 审计四列 | | `deleted` 受 `CHECK (deleted = FALSE)` 锁死（§0 第 7 条） |

索引：

```text
uk_finance_payment_source_active       ON (source_type, source_id)
                                       WHERE deleted = FALSE AND source_id IS NOT NULL
idx_finance_payment_counterparty_paid  ON (counterparty_type, counterparty_id, paid_at DESC) WHERE deleted = FALSE
idx_finance_payment_external_ref       ON (external_reference)
                                       WHERE deleted = FALSE AND external_reference IS NOT NULL
uk_finance_payment_single_reverse      ON (reverse_of_id)
                                       WHERE deleted = FALSE AND entry_type = 'REVERSE'
```

**`REVERSE` 行的 `source_type` / `source_id` 必须为 NULL（D-3 的库级 CHECK，不是服务层约定）**：
`uk_finance_payment_source_active` 的谓词是 `source_id IS NOT NULL`，
若反向行沿用原行的 `ORDER_REFUND` 来源，它会与原行**抢同一个唯一键** ——
结果是「登错一笔退款付款后再也反向不掉」（唯一索引拒绝插入反向行），
纠错路径被自己的防重索引锁死。NULL 来源让反向行落在谓词之外，原行与反向行各自成立。

`uk_finance_payment_source_active` 即第二批 Q26 要求的「退款付款库级唯一」：
同一 `order_refund` 最多一笔正式退款 `Payment`（第一批 Q19）。

**退款付款的登记流程**：财务在付款页选择来源类型 `ORDER_REFUND` 与某张 `order_refund`，
服务端校验该退款 `status = 'COMPLETED'`、金额等于 `order_refund.refund_amount`（第一批 Q19）、
`counterparty_type = 'CUSTOMER'` 且 `counterparty_id = order_refund.customer_id`；
随后插入 `finance_payment`，来源唯一索引兜底防重。
**`Payment` 不冲减应收**（第二批 Q27：Return 负责红冲，Refund Payment 只负责真实资金退付，避免双重冲减）。

### 6.3 收付款的 append-only 纠错（D-3 已裁决：采纳反向事实）

评审指出的缺口成立：「反向核销 + 重新登记」只能纠正**分配**，纠正不了**登错的 Receipt / Payment 本身**
（金额错、对象错、凭据号错）。按全局不变量 1/2，纠错只能新增反向事实。正式模型即 §5 / §6 已列的三列：

`finance_receipt` 与 `finance_payment` 各带三列：

```text
entry_type   VARCHAR(8)  NOT NULL CHECK IN ('NORMAL','REVERSE')
reverse_of_id BIGINT     CHECK：REVERSE 时 NOT NULL 且指向同表 entry_type='NORMAL' 的行；NORMAL 时 NULL
reason       VARCHAR(500) CHECK：REVERSE 时必填非空
```

金额仍恒正；反向事实不修改原记录。配套部分唯一索引：

```text
uk_finance_receipt_single_reverse ON finance_receipt (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE'
uk_finance_payment_single_reverse ON finance_payment (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE'
```

**纠错流程（四步，每步各自可追溯）**：

```text
原 NORMAL 收/付款
→ 若已存在有效 WriteOff，先逐笔反向核销（§8.4）
→ 确认该单已用额 = 0
→ 创建 REVERSE 收/付款（必填 reason，独立权限 1526 / 1527）
→ 重新登记正确的 NORMAL
→ 必要时重新核销
```

**禁止**：`UPDATE` 原收款 / 付款事实、`DELETE`、软删隐藏、直接改金额、直接改客户 / 供应商。
`CHECK (deleted = FALSE)`（§0 第 7 条）让软删在库层直接失败。

对六个面的影响（实现时逐条在 IT 里钉住）：

| 面 | 影响 |
| --- | --- |
| 核销余额 | 收付款「有效额」变为 `amount − Σ REVERSE.amount`，待核销余额 = 有效额 − 已用额；派生公式与 §7 同构，仍读时算 |
| 已用额与反向的先后 | 若一张收款已被核销后再反向，会出现「已用 > 有效额」的负待核销。正式约束：**反向前必须已用额 = 0**（即先反向其全部核销），否则 `41135` 同形错误拒绝 —— 保持派生值非负、语义不崩。这条前置与 D-4 的负净应收刻意不叠加：负值只允许出现在「应收侧忠实记录已成立退货」这一处 |
| 退款 Payment 来源唯一索引 | `uk_finance_payment_source_active` 已带 `source_id IS NOT NULL` 谓词；反向行必须 `source_type/source_id = NULL`（CHECK：`REVERSE → source_type IS NULL`），否则反向行会与原行抢同一 `ORDER_REFUND` 来源或被误认为第二笔退款付款 |
| 查询 | 列表默认含反向行并以方向列标识（与核销页同形）；「待核销余额 > 0」筛选按有效额计算；不默认隐藏反向行，否则纠错不可见 |
| 导出 | 与查询同一 Service 方法、同一口径（P0 裁决 10）；导出列增加方向与 `reverse_of_id` 指向单号 |
| operation log | 新增 `operation_type`：`RECEIPT_REVERSE` / `PAYMENT_REVERSE`；`before_data` 为原行有效额快照、`after_data` 为反向后快照 |
| 并发 | 反向需 `FOR UPDATE` 锁原行（rank 1/2）+ `uk_*_single_reverse` 兜底「一行只被反向一次」；与核销并发时两者都锁原收付款行，串行后各自校验已用额/有效额 |

**不采用的替代**：允许修改 / 软删收付款单（违反不变量 1/2 与第二批 Q20 的单步生效语义）；
把登错交给「再登记一笔相反方向的收款」表达（会污染 `method` / 凭据号语义，且预收余额口径失真）；
不纠错、只走数据修正迁移（财务纠错是日常动作，运维不可接受）。
反向收付款**不需要第二人审批**：Q20「无财务审批状态机」未被推翻，制衡由
「独立破坏性权限 + 必填原因 + 操作人 + 时点 + 日志 + 并发保护」承担。

## 7. WriteOff（核销）

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `write_off_no` | 前缀 `WO`，唯一 | |
| `source_type` | `VARCHAR(16) NOT NULL CHECK IN ('RECEIPT','PAYMENT')` | 钱从哪来 |
| `source_id` | `BIGINT NOT NULL` | `finance_receipt.id` / `finance_payment.id` |
| `target_type` | `VARCHAR(16) NOT NULL CHECK IN ('RECEIVABLE','PAYABLE')` | 抵到哪去 |
| `target_id` | `BIGINT NOT NULL` | `finance_receivable.id` / `finance_payable.id` |
| 配对 CHECK | `(source_type='RECEIPT' AND target_type='RECEIVABLE') OR (source_type='PAYMENT' AND target_type='PAYABLE')` | 收款只核应收、付款只核应付 |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | 本次核销金额 |
| `entry_type` | `VARCHAR(8) NOT NULL CHECK IN ('NORMAL','REVERSE')` | 反向核销事实（第二批 Q18） |
| `reverse_of_id` | `BIGINT`，CHECK：`REVERSE` 时 NOT NULL 且指向 `entry_type='NORMAL'` 的行；`NORMAL` 时 NULL | 指向原核销记录 |
| `reason` | `VARCHAR(500)`，CHECK：`REVERSE` 时必填非空 | 撤销原因（第二批 Q18） |
| `written_off_at` | `TIMESTAMPTZ NOT NULL` | |
| `operator` | `VARCHAR(64) NOT NULL` | 操作人（第二批 Q18） |
| `version` / `deleted` / 审计四列 | | |

索引：

```text
uk_finance_write_off_single_reverse  ON (reverse_of_id)
                                     WHERE deleted = FALSE AND entry_type = 'REVERSE'
idx_finance_write_off_target         ON (target_type, target_id)        WHERE deleted = FALSE
idx_finance_write_off_source         ON (source_type, source_id)        WHERE deleted = FALSE
```

`uk_finance_write_off_single_reverse` 把「一条正常核销最多被反向一次」钉进库里；
重复撤销在库级失败，而不是靠服务层先查后判。

**派生口径（第二批 Q17 + D-2 / D-4，读时计算，不落库）**：

```text
netAmount（应收净额）
  = Σ receivable_item(NORMAL 单头).amount − Σ receivable_item(RED 单头).amount
  ← D-4：自动红字不封顶，因此 netAmount **允许为负**

writtenOffAmount（已核销额）
  = Σ write_off(NORMAL, target=该应收).amount − Σ write_off(REVERSE, target=该应收).amount

openAmount（未核销额，只读派生）        = max(netAmount − writtenOffAmount, 0)
overAppliedAmount（超额核销，只读派生） = max(writtenOffAmount − netAmount, 0)

settleState = writtenOffAmount = 0                    → OPEN
              openAmount = 0 且 overAppliedAmount = 0 → SETTLED
              其余                                    → PARTIAL
overAppliedAmount > 0 时页面**另外**标注「超额核销待处理」，它覆盖三态标签的展示，
但不是第四个状态值（settleState 仍取 PARTIAL）。

某收款的有效额     = amount − Σ REVERSE.amount（D-3）
某收款的已用额     = Σ write_off(NORMAL, source=该收款).amount − Σ write_off(REVERSE, source=该收款).amount
某收款的待核销余额 = 有效额 − 已用额（> 0 时即「待核销款」，第一批 Q16；D-3 保证它不为负）
```

**`overAppliedAmount` 是 Finance R1 的异常 / 待处理表达，不是 P5 的客户余额体系**（D-4）。
因此页面与导出**不得**把 `netAmount` 的负值或 `overAppliedAmount` 叫作
「客户余额」「钱包余额」「可用余额」—— 那三个词属 P5，本期不存在对应能力。
`overAppliedAmount > 0` 的业务含义是「已核销的钱超过了账上应收」，
来源只有两种：少拣后整行退货（D-4 的例子）、或先收款后红冲。它需要人工处理
（反向核销、或登记退款付款），系统**不自动**退款、不自动结转、不自动抵扣下一单。

**核销校验（服务层，全部在持有目标锁之后）**：

1. `source` 与 `target` 的结算对方必须一致（读已锁定的 source / target 行比对，**不在核销行上冗余对方列**，
   避免同一事实出现第二个权威来源），否则 `FINANCE_COUNTERPARTY_MISMATCH`（第二批 Q17 禁止跨客户/供应商核销）；
2. 本次 `amount` ≤ 目标 `openAmount`，否则 `FINANCE_WRITE_OFF_AMOUNT_EXCEEDED`
   （D-2 / D-4 下 `netAmount` 为负时 `openAmount = 0`，该应收自然不可再被核销，无需额外分支）；
3. 本次 `amount` ≤ source 待核销余额，否则同上错误码；
4. 一笔提交可含多行（M:N），在同一事务内按 §14 锁序逐行写入。

## 8. 红字 / 反向事实模型

### 8.1 统一纪律（全局不变量 1/2）

- 不 UPDATE、不 DELETE、不软删隐藏任何既有财务事实；纠错一律**新增反向事实**。
- 反向事实必须：引用原事实、带方向（`entry_type`）、必填原因、记录操作人与时间、保留完整追溯链
  （第一批 Q13、第二批 Q18）。

### 8.2 应收红字（第二批 Q27；2026-09-25 评审修订）

触发：`OrderReturnService.approve` 将退货置 `APPROVED` 的**同一事务**内调用
`FinanceReceivableService.generateRedOnReturnApproved(orderReturnId)`（F1-2C 落地形态）。

> **签名与规划稿不同，理由与 §3.3 同一条**：`approved_at` 与批准人都是 `order_return` 行上
> 已落库的列（`approve` 用 `stamp()` 把批准人写进 `updated_by`，而 `APPROVED` 之后没有任何
> 命令再改这一行），由调用方传 `now()` 或传 `ScmOperator.current()` 都会造出第二个时点与人。
> 位置固定在退货事实与退款单都已成立之后、幂等 `complete` 之前：写入失败（约束、数据异常）
> 仍整笔回滚批准 —— D-2 / D-4 禁止的是**金额上限校验**阻塞批准，不是禁止失败回滚。

生成规则：

- **逐行跳过非正金额**：`approved_quantity <= 0` 或 `approved_amount <= 0` 的 `order_return_item`
  **不生成红字明细**（评审意见三）。
- **整张退货无正金额明细时成功跳过**：不生成单头、不生成明细、不生成 0 元财务事实（与第二批 Q8 同纪律）。
- **正常应收已存在**：单头 `source_type='ORDER_RETURN'`、`original_receivable_id` 指向原应收；
  每条正金额退货行生成一条红字明细（`source_type='ORDER_RETURN_ITEM'`、`source_id=order_return_item.id`、
  `order_item_id` 回填）；**不修改原应收、不存行级原明细指针**（§3.2）。
- **正常应收尚不存在**（退货先于签收）：**不创建孤立负数应收**，本调用直接 return；
  该订单后续 `SIGNED` 时由 §3.3 第 6 步补生成。
- 红字金额来源 = `order_return_item.approved_amount`（订单域已按
  `round(approved_quantity × locked_unit_price, 4)` 算好，`OrderReturnService.java:181`），
  财务不重算、不改写。

**红字金额不设上限校验（D-2 + D-4 已裁决，两条都为 A）**：

自动红字应收是**已经成立的 `OrderReturn APPROVED` 在财务域中的事实映射**，
因此 Finance **不反向控制订单域状态机**，生成器**不做任何金额上限校验**，
只校验来源唯一（`uk_finance_receivable_source_active` / `..._item_source_active`）。

- **可生成额度不扣除既有核销额**（D-2）。反例场景已裁决为必须通过：
  应收 100 → 收款 100 → 核销 100 → 后续合法退货 20 ⇒ `OrderReturn APPROVE` 成功、
  RED Receivable = 20。Finance 不得因为已经核销 100 而阻止合法退货。
- **少拣导致的超额红字必须全额生成**（D-4）。反例场景已裁决为必须通过：
  订单结算量 5、分拣实发 3、SIGNED 应收 = 30，订单域后续批准退货 5（`approved_amount = 50`）
  ⇒ 完整生成 RED Receivable 50。
- **明确禁止**：阻止 `OrderReturn approve`、自动封顶为原应收额、静默丢弃差额、
  修改 `OrderReturn` / `approved_quantity` / `approved_amount`。
  差额的业务源头在订单域与库存域之间（退了从未出库的货），不在财务域；
  财务忠实记录已经成立的业务事实。
- **允许净应收出现负值**，由 §7 的 `openAmount` / `overAppliedAmount` 两个只读派生值表达，
  页面标注「超额核销待处理」；不叫「客户余额 / 钱包余额 / 可用余额」（那是 P5）。
- **不使用 `FINANCE_RED_AMOUNT_EXCEEDED`（41137）阻塞 `OrderReturn`**。该错误码仍保留，
  但只用于**手工** RED Payable 超额冲减等人工财务动作（§8.3）。
- 因此 §3.2 的「按 `order_item_id` 聚合全部正常应收明细」只服务**行级追溯与展示**，
  不再是可冲校验的输入；F1-2 的红字生成器**不实现**任何金额校验分支。

### 8.3 应付红字（第一批 Q13；41137 的唯一使用者）

R1 只提供**手工登记入口**：`POST /scm/finance/payable/red`，
权限 `scm:finance:payable:red`（§16），必填 `original_payable_id` + `reason` + 明细（数量、单价、金额）。
本期**不**为它接任何自动业务来源（采购退货不存在，第二批 Q13 已裁不新建采购退货模块）。

校验：累计手工红字 ≤ 原应付 `amount` − 该原应付已有红字之和（**同样不扣已核销额**，与 D-2 同口径），
不足即 `FINANCE_RED_AMOUNT_EXCEEDED`（41137）整单回滚。
这里 fail-loud 是正当的，与 §8.2 相反：手工红字应付是**人工财务动作**，
拒绝它不会回滚任何订单域状态机，而放行它会让应付账凭空出现无来源的负值。
红字应付明细的 `source_type` 取值 `'MANUAL'`、`source_id` **必须 NULL**（手工来源无外部业务行，
来源唯一索引的 `source_id IS NOT NULL` 谓词将其排除，§4.1）；其防重由「可冲上限 + 请求级幂等键」承担 ——
与手工出库单同形：两张手工单据就是两个事实，超额由 41137 拦截。

### 8.4 反向核销（第二批 Q18）

`POST /scm/finance/write-off/reverse`，权限 `scm:finance:write-off:reverse`，必填 `write_off_id` + `reason`。
生成一条 `entry_type='REVERSE'` 的新行，`reverse_of_id` 指向原行、金额与原行相同；
`uk_finance_write_off_single_reverse` 保证一条原行只被反向一次。
反向核销**不**触碰原行、不触碰收付款单与应收应付单的任何列。

## 9. Return / Refund 衔接

| 业务事实 | R1 的处理 | 依据 |
| --- | --- | --- |
| `order_return APPROVED` | 生成红字应收（§8.2）；**不**产生库存事实、不改 `settlement_*` | 第二批 Q27 |
| `order_return` 未签收即批准 | 不创建孤立负数应收；签收时补生成 | 第二批 Q27 |
| `order_refund COMPLETED` | **不等于**资金已付出；不冲减应收 | 第一批 Q19、第二批 Q27 |
| 退款真实付款 | 财务登记 `finance_payment`（`source_type='ORDER_REFUND'`），金额必须等于 `refund_amount`，来源库级唯一 | 第一批 Q19、第二批 Q26 |
| 分期 / 部分 / 多渠道退款 | 本期不做 | 第一批 Q19 |
| 退货库存处理、`RETURN_IN`、部分签收 | 本期不改 | 第二批 Q27 |

**职责划分（第二批 Q27）**：Return → 应收红冲；Refund Payment → 真实资金退付。两者互不替代，避免双重冲减。

## 10. 状态机

**财务域没有状态机。** 这是第二批 Q17（结清派生）与 Q20（无审批）的直接结论。

- 所有财务表**不含** `status` 列；不含 `approver` / `approval_state` / 待办字段。
- 「结清 / 部分核销 / 待核销」是**读时派生视图**（§7 公式），与库存预警状态「读时计算、不落库」同范式。
- 业务侧状态机（订单、分拣、配送、收货、退货、退款）**一律不由财务修改**（全局不变量 5）。
- 唯一的「状态性」约束是 DB CHECK 与来源唯一索引：它们表达「事实是否成立」，不表达流程阶段。

## 11. 来源唯一约束与反向唯一约束

照抄既有两条索引的形态（`MIG/V19:92-94`、`MIG/V63:52-54`）：

```sql
-- 来源唯一：一个业务事实最多产生一条财务事实
CREATE UNIQUE INDEX uk_finance_receivable_source_active
    ON finance_receivable (source_type, source_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_finance_receivable_item_source_active
    ON finance_receivable_item (source_type, source_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_finance_payable_source_active
    ON finance_payable (source_type, source_id) WHERE deleted = FALSE AND source_id IS NOT NULL;
CREATE UNIQUE INDEX uk_finance_payable_item_source_active
    ON finance_payable_item (source_type, source_id) WHERE deleted = FALSE AND source_id IS NOT NULL;
CREATE UNIQUE INDEX uk_finance_payment_source_active
    ON finance_payment (source_type, source_id) WHERE deleted = FALSE AND source_id IS NOT NULL;

-- 反向唯一：一条 NORMAL 事实最多被反向一次（核销 Q18 / 收付款 D-3）
CREATE UNIQUE INDEX uk_finance_write_off_single_reverse
    ON finance_write_off (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE';
CREATE UNIQUE INDEX uk_finance_receipt_single_reverse
    ON finance_receipt (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE';
CREATE UNIQUE INDEX uk_finance_payment_single_reverse
    ON finance_payment (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE';
```

**来源唯一索引只约束 `source_id IS NOT NULL` 的业务派生事实**（2026-09-25 评审意见二，§4.1 已同步）：
手工红字应付（`source_type='MANUAL'`、`source_id=NULL`）与无来源的收付款登记不在其约束范围内，
它们的防重由请求级幂等键与金额上限校验承担。应收 / 应收明细的来源恒非空
（`SALES_ORDER` / `ORDER_RETURN` / `INVENTORY_OUTBOUND_ITEM` / `ORDER_RETURN_ITEM`），
谓词保留 `deleted = FALSE` 即可。
付款的 `REVERSE` 行 `source_id` 必须 NULL（§6），因此它落在来源唯一索引之外、
只受 `uk_finance_payment_single_reverse` 约束 —— 这正是「反向行不与原行抢 `ORDER_REFUND` 来源」的实现方式。

**`external_reference` 不在本节约束之内**（§3.2 修正）：它只是资金凭据文本，
从未被裁决为全系统唯一，只建普通索引。

语义（第二批 Q26）：**幂等键防重复请求，来源唯一索引防重复事实**，两者必须同时存在；
来源唯一索引是最终数据库防线。生成器一律走 `insertOnConflictDoNothing`，
命中冲突即「已生成」，返回成功而不是报错（与 `InventoryCommandService` 的 `INVENTORY_DUPLICATE_*` 不同：
财务生成是**可重放的派生**，不是用户命令）。

**append-only 的库级兜底（§0 第 7 条）**：八张表全部带 `CONSTRAINT ck_finance_<表>_append_only
CHECK (deleted = FALSE)`，形态照 `V19:83`。它拒绝的是「用软删模拟删除历史财务事实」；
不拒绝正常的列更新（例如 `version` 递增），因此与 `@Version` 乐观锁不冲突。
仓库既有范式已验证可行：`inventory_movement` 同样带这条 CHECK，
其实体刻意**不用** `@TableLogic`、DAO 只有 insert + select，读取在 SQL 里显式写 `deleted = FALSE`。
Finance 实体沿用同一取舍，**不存在需要回退 append-only 的技术冲突**。
`finance_operation_log` 同样带该 CHECK，且不提供任何删除入口。

## 12. 金额公式与精度

- 全表金额 / 数量一律 `NUMERIC(18,4)`；Java 侧 `BigDecimal`、scale = 4、`RoundingMode.HALF_UP`（第二批 Q22）。
- 序列化沿用 `ScmFixedScale4Serializer`（null 写 null，绝不写 0）。
- 公式：

```text
receivable_item.amount = round(inventory_outbound_item.quantity × sales_order_item.locked_unit_price, 4)
receivable.amount      = Σ receivable_item.amount（同 entry_type）
payable_item.amount    = round(purchase_receipt_item.received_quantity × purchase_order_item.purchase_price, 4)
payable.amount         = Σ payable_item.amount（同 entry_type）
red_receivable_item.amount = order_return_item.approved_amount（订单域已算，财务不重算）
write_off.amount       = 登记值，CHECK > 0
```

- **展示不改变事实**：页面 / 导出可以按 2 位格式化展示，但存储与对账口径恒为 4 位（第二批 Q22）。
- **既有 2/4 位并存不在本期整理**：库存余额页 `ROUND(quantity × avg_cost, 2)`
  （`MAP/inventory/InventoryBalanceDao.xml:138,185`）与报表 4 位并存是历史现状，第二批 Q22 明确不重构。
- **禁止**把 `inventory_movement.unit_cost`（出库时库存均价）用于应收金额；
  **禁止**把 `sales_order.settlement_total_amount` 用于应收金额（第一批 Q1/Q2/Q3）。

## 13. 幂等

- 复用现有 `idempotency_record` 与 `OrderIdempotencyService`（第二批 Q26：不移动包、不合并两套、不新增第三套）。
  财务域使用 `OrderIdempotencyService`（与 delivery / inventory / sorting 同形），
  scope 命名 `"<动作>:<业务主键>"`：`FINANCE_RECEIPT_ADD:<employeeId 由服务内拼>`、
  `FINANCE_PAYMENT_ADD:…`、`FINANCE_WRITE_OFF_ADD:…`、`FINANCE_WRITE_OFF_REVERSE:<write_off_id>`、
  `FINANCE_RECEIPT_REVERSE:<receipt_id>`、`FINANCE_PAYMENT_REVERSE:<payment_id>`（D-3）、
  `FINANCE_PAYABLE_RED:…`。
- 三段式：`claim → 业务写 → complete` 同一事务；重放返回首次结果；同键异 hash 抛既有冲突码。
- **生成器（应收 / 应付 / 红字）不走幂等键**：它们不是用户命令，防重靠来源唯一索引（§11）。
- 退款付款的防重**同时**依赖：请求级 `Idempotency-Key` + `uk_finance_payment_source_active`（第二批 Q26）。
- 收付款反向的防重**同时**依赖：请求级 `Idempotency-Key` + `uk_*_single_reverse`（D-3）——
  幂等键防重复请求，反向唯一索引防「一条 NORMAL 被反向两次」这个重复事实。
- **`external_reference` 不参与任何一层防重**（§5）：它只是资金凭据文本，不是幂等键。

## 14. 并发与锁序

财务域声明自己的锁层级，插入既有纪律（「单据锁先于余额锁，余额锁最后」）之后：

```text
业务域既有锁（线路行锁 / 收货单锁 / 退货单锁 / 订单锁，由调用方在触发事务内已持有）
→ finance 单据行 FOR UPDATE，按全局 rank + id 升序：
      RECEIPT(1) → PAYMENT(2) → RECEIVABLE(3) → PAYABLE(4) → WRITE_OFF(5)
→ finance 明细 / 核销 INSERT（不加锁，靠来源唯一索引与反向唯一索引兜底）
```

- 生成器（应收 / 应付 / 红字）只 INSERT，不锁业务表（业务表锁已由调用方持有）；
  并发双触发由来源唯一索引仲裁，第二个事务 `DO NOTHING` 后成功返回。
- **但唯一索引修不了「两边都没尝试 INSERT」**（F1-2C）：签收与退货批准是两条独立事务，
  各自都可能看不到对方未提交的事实，于是签收方查不到已批准退货、批准方查不到正常应收，
  提交后红字永久缺失。因此两者必须共享一个串行点：`OrderReturnService.lock` 一开始就
  `orders.lock(orderId)`，F1-2C 让 `DeliveryRouteService.sign` 在 `markSigned` 之前按
  `route → sales_order` 锁同一张订单（与本域 `addOrders / plan / dispatch` 完全同序；
  订单 / 退货 / 退款域从不锁 `delivery_route`，因此不存在反向锁序）。
  后拿到锁的一方在 `READ COMMITTED` 下必然看见先提交的一方，两条路径于是都调用同一个红字算法，
  一次真生成、另一次命中来源唯一索引静默返回。财务自身仍不做任何业务表 `SELECT … FOR UPDATE`。
- 核销 / 反向核销 / 红字登记 / **收付款反向（D-3）**是**用户命令**，必须按上述 rank 升序
  `SELECT … FOR UPDATE` 锁住 source 与全部 target 后再校验余额并写入；两个并发核销对同一目标会串行，
  第二个看到更新后的 `openAmount`，超额即 `FINANCE_WRITE_OFF_AMOUNT_EXCEEDED`。
- 反向核销额外锁原 `write_off` 行（rank 5），配合 `uk_finance_write_off_single_reverse` 双保险。
- 收付款反向锁原收 / 付款行（rank 1 / 2），配合 `uk_finance_receipt_single_reverse` /
  `uk_finance_payment_single_reverse` 双保险；它与核销**共用同一把原收付款行锁**，
  因此「反向收付款」与「核销该收付款」天然串行 —— 这正是 D-3「反向前已用额必须 = 0」
  能被并发安全校验的前提（先查再判的乐观写法在这里不成立）。
- 财务域**不获取**任何 `inventory_balance` 行锁（全局不变量 4）。
- 所有写命令 `@Transactional(rollbackFor = Exception.class)`；version 冲突抛 `VERSION_CONFLICT(40921)`。

并发用例必须覆盖（§22）：并发签收双生成、并发核销同目标、并发反向核销同行、
并发退款双付款、并发反向同一收款、核销与红字并发（同一应收）。

## 15. 数据范围

- 解析入口唯一：`ScmDataScopeService.resolve()`；无授权 fail-closed（`scope == null` 渲染 `AND FALSE`，
  读侧短路用 `ScmDataScopeService.emptyPage`）。
- **应收**：`orderSellerScope`，谓词落在 join 出的 `sales_order.seller_id` 行上（第二批 Q23）。
- **应付**：`purchaserScope`，谓词落在 `purchase_receipt → purchase_order → purchaser_id` 的 join 行上（第二批 Q23）。
- **收款 / 付款 / 核销（D-5 已裁决）**：
  * **收款**：`customer_id → customer.seller_id → customerSellerScope`。
  * **付款**：`SUPPLIER` 侧**本期不新增 supplier scope**，沿用采购团队共享读取边界
    （P0 裁决 7：供应商主档无 owner 列，按采购团队共享读）；`CUSTOMER` 侧同收款，走 `customerSellerScope`。
  * **核销**：随其 **target** 的数据范围 —— `RECEIVABLE → orderSellerScope`、`PAYABLE → purchaserScope`。
    即「核销行的可见性 = 被核销单据的可见性」。只看得到收款、看不到某张应收的角色，
    在该应收抽屉里也看不到指向它的核销行；这是刻意的（核销行本身不是独立归属对象），
    与 Q24「有单据查询权即可见金额」一致，不额外裁剪金额列。
- **禁止 `if role == FINANCE then bypass scope`**（P0 裁决 17、第二批 Q23、D-5）。
  `SCM_FINANCE` 当前的全范围来自**正式权限配置**（已持 1302 / 1311 / 1322 / 1331 等
  `*:scope:all:query` 与全部仓库范围），**不得通过代码里的角色判断实现**。
  将来开放给部分范围的财务岗位时，仍复用同一套 scope，只调授权不改业务代码。
- 写侧判定：核销 / 反向核销 / 红字登记 / 收付款反向在锁行后调用
  `scope.getOrderSellerScope().allows(sellerId)` / `scope.getPurchaserScope().allows(purchaserId)` /
  `scope.getCustomerSellerScope().allows(customerSellerId)`，
  与 `SortingAccess` 的写侧判定同形；**不新增守卫类、不新增范围维度、不新增 `*:scope:all:query` 点**。
- `administrator_flag` 仍为 break-glass，**权限取证必须用 `administrator_flag = false` 账号**。
- 导出与列表共用同一次 `resolve()` 结果（P0 裁决 10）。

## 16. 权限矩阵（规划词汇表；按阶段发布，见 §21）

> **本节是设计稿冻结的权限词汇与号段规划，不是「已全部发布」的清单。**
> F1-1 只交付 V65（纯 DDL），**一条 `t_menu` 行都没有种**：没有 Controller 就没有受保护的端点，
> 没有 `.vue` 就没有可以点开的页面。每一行在下表标注的发布阶段才落库（§21 的发布纪律）。
> 号段 1500–1531 是 2026-09-26 实测空闲后的规划值，每次落库前必须重扫。
>
> **截至 F1-3B 的实际占用**：V66 发布 1500（隐藏目录，无 `component`）+ 1521 `receipt:add`，
> V67 发布 1522 `payment:add`，其余仍是规划值。下表 1500 / 1513 / 1514 的「发布阶段」
> 已按「第一次被真实端点使用」这条本表自己的规则回改。

菜单号段取 **1500**（实测 1422–1499 与 1500+ 均空闲；沿用 `MIG/V55:13-14` 的四条种子约定）。

| menu_id | 类型 | 名称 / 权限串 | 发布阶段 | 授予 |
| --- | --- | --- | --- | --- |
| 1500 | 目录 | 财务管理（`visible_flag = false`，无组件） | **F1-3A（V66 已发布）**：能力点必须有 `parent_id`，而目录本身不出入口 | SUPER_ADMIN、SCM_FINANCE |
| 1501 | 页面 | 应收管理 `/finance/receivables` | F1-6（`.vue` 同阶段） | 同上 |
| 1502 | 页面 | 应付管理 `/finance/payables` | F1-6 | 同上 |
| 1503 | 页面 | 收款管理 `/finance/receipts` | F1-6 | 同上 |
| 1504 | 页面 | 付款管理 `/finance/payments` | F1-6 | 同上 |
| 1505 | 页面 | 核销管理 `/finance/write-offs` | F1-6 | 同上 |
| 1511 | 按钮 | `scm:finance:receivable:query` | F1-5 | 同上 |
| 1512 | 按钮 | `scm:finance:payable:query` | F1-5 | 同上 |
| 1513 | 按钮 | `scm:finance:receipt:query` | F1-5（原规划 F1-3，见下方说明） | 同上 |
| 1514 | 按钮 | `scm:finance:payment:query` | F1-5（原规划 F1-3，同一理由） | 同上 |
| 1515 | 按钮 | `scm:finance:write-off:query` | F1-4 | 同上 |
| 1521 | 按钮 | `scm:finance:receipt:add` | **F1-3A（V66 已发布）** | 同上 |
| 1522 | 按钮 | `scm:finance:payment:add` | **F1-3B（V67 已发布）** | 同上 |
| 1523 | 按钮 | `scm:finance:write-off:add` | F1-4 | 同上 |
| 1524 | 按钮 | `scm:finance:write-off:reverse` | F1-4 | 同上 |
| 1525 | 按钮 | `scm:finance:payable:red` | F1-4 | 同上 |
| 1526 | 按钮 | `scm:finance:receipt:reverse` | F1-4 | 同上 |
| 1527 | 按钮 | `scm:finance:payment:reverse` | F1-4 | 同上 |
| 1531 | 按钮 | `scm:finance:export` | F1-5 | 同上 |

查询权限跟随**首个能读到该对象的端点**所在阶段：1515 属 F1-4、1511/1512 属 F1-5（应收应付的只读
查询端点在 F1-5 才出现）。**1513 / 1514 原定 F1-3 已回改为 F1-5** —— F1-3A 与 F1-3B 各只交付一个
写入口（`POST /receipt/add`、`POST /payment/add`），登记接口自己返回刚写入的那一张单，
库里不存在任何读收款 / 读付款的端点，因此没有可授权的读取动作；提前种 `*:query`
会得到一条「已授权但没有任何端点使用它」的权限，与「发布指向不存在 `.vue` 的页面菜单」是同一类错误。
若某阶段的实现顺序与此不同，以「该权限第一次被真实端点使用」为准，并回改本表。

- **1500 为什么在 F1-3A 就要种**：SmartAdmin 的能力点行需要一个 `parent_id`，而原生
  `menu_type=1` 目录是唯一的合法父级（V28 的数据大屏目录、V46 的业务待办入口是同一范式）。
  它带 `visible_flag = false` 且 `component IS NULL`，因此既不在侧栏出现，也不注册路由 ——
  与「发布指向不存在 `.vue` 的页面菜单」是两件事，后者由 `SmartAdminMenuComponentPgIT` 拒绝。

- **1526 / 1527 是 D-3 裁决带来的两个独立破坏性权限**（Q20 要求破坏性动作独立权限）：
  反向收款与反向付款各自一条，**不合并**成 `scm:finance:reverse`，也不隐含在 `*:add` 里 ——
  能登一笔款的人不必然是能冲掉一笔款的人。
- 授权写法照 `V64:32-40` 的 `grant_matrix(menu_id, role_code)` CTE 按 `role_code` 种，不硬编码 `role_id`。
- **不新增**金额 / 成本字段级权限与 masking（第二批 Q24）：有对应 `*:query` 即可见金额；
  成本可见性仍由既有 `scm:report:cost:query` 管报表域，财务域不借用。
- 导出端点要求「对应 `*:query` **AND** `scm:finance:export`」（P0 裁决 10 同形）。
- 生成器（应收 / 应付 / 红字应收）**不挂权限点**：它们是业务事务内的派生写，
  权限由触发命令（签收 / 收货确认 / 退货批准）既有权限承担。因此 **F1-2 不种任何权限**。
- **D-1 不新增任何回填 / 历史补生成权限**（§19）。
- **不新增任何 `*:scope:all:query`**（D-5）：财务的全范围来自既有显式授权
  （1302 / 1311 / 1322 / 1331），本期不新增范围放宽点。

错误码取 **41130–41149**（顺延分拣块，不重排已发布码值）：

| 码 | 名称 | 触发 |
| --- | --- | --- |
| 41130 | FINANCE_RECEIVABLE_NOT_FOUND | 锁不到应收单 |
| 41131 | FINANCE_PAYABLE_NOT_FOUND | 锁不到应付单 |
| 41132 | FINANCE_RECEIPT_NOT_FOUND | 锁不到收款单 |
| 41133 | FINANCE_PAYMENT_NOT_FOUND | 锁不到付款单 |
| 41134 | FINANCE_WRITE_OFF_NOT_FOUND | 锁不到核销行 |
| 41135 | FINANCE_WRITE_OFF_AMOUNT_EXCEEDED | 核销额超过目标 `openAmount` 或 source 待核销余额 |
| 41136 | FINANCE_COUNTERPARTY_MISMATCH | 跨客户 / 跨供应商核销 |
| 41137 | FINANCE_RED_AMOUNT_EXCEEDED | **仅**手工红字应付超过原单可冲金额（§8.3）；自动红字应收**永不**使用本码（D-4） |
| 41138 | FINANCE_REVERSE_REASON_REQUIRED | 反向核销 / 手工红字 / 收付款反向缺原因（DB CHECK 之外的可读错误） |
| 41139 | FINANCE_PAYMENT_SOURCE_INVALID | 退款付款来源不合法（未 COMPLETED / 金额不符 / 对方不符） |
| 41140 | FINANCE_METHOD_INVALID | 方式不在三值枚举内 |
| 41141 | FINANCE_RECEIVED_AT_INVALID | 收付时点非法 |
| 41142 | FINANCE_REVERSE_BLOCKED_BY_WRITE_OFF | 收 / 付款仍有有效核销额，必须先反向核销（D-3 的「已用额 = 0」前置） |
| 41143 | FINANCE_ALREADY_REVERSED | 该事实已被反向过（`uk_*_single_reverse` 的可读前置错误） |

## 17. 操作日志

新建 `finance_operation_log`，形态照 `order_operation_log`（`MIG/V13:114-129`）：

| 列 | 约束 |
| --- | --- |
| `business_type` | `CHECK IN ('RECEIVABLE','PAYABLE','RECEIPT','PAYMENT','WRITE_OFF')` |
| `business_id` | NOT NULL |
| `operation_type` | `CHECK IN ('GENERATE','RED_GENERATE','RECEIVE','PAY','WRITE_OFF','WRITE_OFF_REVERSE','RECEIPT_REVERSE','PAYMENT_REVERSE')` |
| `operator` / `reason` | NOT NULL / 可空 |
| `before_data` / `after_data` | `JSONB`，CHECK `jsonb_typeof = 'object'` 或 NULL |
| `created_at` / `created_by` | NOT NULL |
| `deleted` | `CHECK (deleted = FALSE)`；本表**不提供任何删除入口**（§0 第 7 条） |

白名单即 F1-1 的最终范围（八个值，含 D-3 带来的 `RECEIPT_REVERSE` / `PAYMENT_REVERSE`）。
后续阶段只**使用**这些值，不再扩充；确需新值时回到 `docs/decisions.md` 追加裁决并新开迁移。

- 唯一写入口 `FinanceOperationLogRecorder`，**必须与业务写同一事务**（照 `OrderOperationLogRecorder.java:29-42`）。
- 生成类动作（`GENERATE` / `RED_GENERATE`）的 `before_data` 为 NULL、`after_data` 为单头快照；
  核销与反向核销的 `before_data` 为目标的派生余额快照、`after_data` 为写入后的派生余额快照；
  收付款反向（`RECEIPT_REVERSE` / `PAYMENT_REVERSE`）的 `before_data` 为原行有效额快照、
  `after_data` 为反向后快照 ——
  这是「财务需要改前 / 改后金额级证据」的证明，也是不复用 `t_operate_log` 的理由
  （P1 裁决 12 的判据：通用日志不保证与事务同成同败，也不带金额快照与类型白名单）。
- 读接口 `GET /scm/finance/log/query?businessType=&businessId=`，权限取对应 `*:query`，
  前端下钻照 Wave 8 的 `operate-log-list.vue` 形态。

## 18. 查询与导出

端点（一律 `POST` + `PageParam`，照 `SmartPageUtil.convert2PageQuery/convert2PageResult`，
显式拒绝 `sortItemList`）：

```http
POST /scm/finance/receivable/query        GET /scm/finance/receivable/{id}        POST /scm/finance/receivable/export
POST /scm/finance/payable/query           GET /scm/finance/payable/{id}           POST /scm/finance/payable/export
POST /scm/finance/receipt/query           GET /scm/finance/receipt/{id}           POST /scm/finance/receipt/export
POST /scm/finance/payment/query           GET /scm/finance/payment/{id}           POST /scm/finance/payment/export
POST /scm/finance/write-off/query                                                 POST /scm/finance/write-off/export
POST /scm/finance/receipt/add             POST /scm/finance/payment/add
POST /scm/finance/write-off/add           POST /scm/finance/write-off/reverse
POST /scm/finance/receipt/reverse         POST /scm/finance/payment/reverse
POST /scm/finance/payable/red             GET  /scm/finance/log/query
```

- 查询 VO 携带**派生列**：`netAmount / writtenOffAmount / openAmount / overAppliedAmount / settleState`，
  由 SQL 内对 `finance_write_off` 的聚合子查询一次算出（避免 N+1），与明细列表同一次查询返回。
  `openAmount` / `overAppliedAmount` 的公式见 §7（D-4）；两者都是**只读派生值**，不落库、不可写。
- 收付款列表的派生列是 `effectiveAmount`（= `amount − Σ REVERSE.amount`，D-3）、`usedAmount`、
  `pendingWriteOffAmount`，同样读时算。
- 日期轴复用 `ScmReportTimeRangeResolver`（Asia/Shanghai 半开区间、366 天上限），不新写日界转换。
- 导出：新建 `finance/support/FinanceExcel.java`，照 `ScmReportExcel` 的 `row()/cell()` 实现逐字对齐
  （FastExcel 动态表头、`OffsetDateTime` 归一化为北京时间字符串、`BigDecimal.toPlainString()`、
  文件名 `SmartResponseUtil.setDownloadFileHeader`）；行数上限照 `ScmReportExportGuard`（超限 41112 同形码）。
  **不跨域引用 `report` 包**。
- 导出与列表调用同一 Service 方法，仅换 `pageSize`（P0 裁决 10）。

## 19. 数据回填策略（D-1 已裁决：不回填）

- **不自动回填**上线前已经存在的 `SIGNED` 销售订单、`CONFIRMED` 采购收货与 `APPROVED` 退货。
  Finance R1 **从正式上线后新发生的业务事实开始生成**。
  理由：P2 之后已存在的签收与收货事实若一次性生成财务事实，会把「上线前的业务」变成
  「上线日的财务事件」，`event_at` 与生成时点分离，且无法与历史对账口径对齐。
- **本期不提供历史补生成 API，不增加任何回填权限**（§16 末条）。
- 生成器仍**必须保持可重放、来源幂等**（来源唯一索引 + `insertOnConflictDoNothing`）：
  这不是为回填留的后门，而是并发双触发与事务重试下的正确性要求（§11、§14）。
- **将来如需历史迁移，单独立项、单独验收**：届时按 V37 的纪律执行
  （重放不出来的行 `RAISE EXCEPTION` 让迁移失败，不按 0 继续），并单独裁决
  `event_at` 口径与 `finance_operation_log.operator` 记谁。本期不预设结论。
- 已知后果（有意识接受）：财务页的「应收发生额」自上线日起算，
  与业务方记忆中的历史发货量对不上；历史往来仍回业务域（订单 / 收货 / 退货）查询。
  R0 的 A 类影子指标（§27.1）不受影响，它们本来就读业务表。

## 20. 前端页面线框与路由

目录与命名照既有范式（`src/views/business/scm/<域>/`、`src/api/business/scm/<域>-api.ts`、
`src/constants/business/scm/<域>-const.ts`）：

```text
src/api/business/scm/finance-api.ts
src/constants/business/scm/finance-const.ts        ← SCM_FINANCE_PERMISSION / 枚举 / 表格列 id
src/views/business/scm/finance/finance-types.ts
src/views/business/scm/finance/finance-form-model.ts
src/views/business/scm/finance/finance-errors.ts
src/views/business/scm/finance/use-finance-permission.ts
src/views/business/scm/finance/finance-receivable-list.vue
src/views/business/scm/finance/finance-payable-list.vue
src/views/business/scm/finance/finance-receipt-list.vue
src/views/business/scm/finance/finance-payment-list.vue
src/views/business/scm/finance/finance-write-off-list.vue
```

`t_menu.component` = `/business/scm/finance/finance-<x>-list.vue`，`path` = `/finance/<x>s`
（运行时 `src/router/index.ts:101` 的 `import.meta.glob('../views/**/**.vue')` 解析）。

页面线框（统一「查询 → 工具栏 → 表格 → 分页」，详情用右侧抽屉 `destroy-on-close`）：

| 页面 | 查询 | 表格列（节选） | 工具栏 / 抽屉 |
| --- | --- | --- | --- |
| 应收 | 日期范围（`event_at`）、客户、订单号、结清状态（派生筛选，服务端算）、方向 | 单号、订单号、客户、方向、金额、已核销、未核销（`openAmount`）、超额核销（`overAppliedAmount`）、结清状态、事件时点 | 导出；抽屉：明细行（出库行来源、数量、单价、金额）+ 红字引用 + 核销行 + 日志 |
| 应付 | 日期范围（`event_at`）、供应商、采购单号、结清状态、方向 | 单号、采购单号、供应商、方向、金额、已核销、未核销、结清状态、事件时点 | 导出、登记红字（`scm:finance:payable:red`）；抽屉同应收 |
| 收款 | 日期范围（`received_at`）、客户、方式、方向、是否有待核销余额 | 单号、客户、方向、金额、有效额、已用、待核销、凭据号、时点 | 登记（`scm:finance:receipt:add`）、反向（`scm:finance:receipt:reverse`，必填原因）、导出；抽屉：核销行 + 反向指向 + 日志 |
| 付款 | 日期范围（`paid_at`）、对方类型、对方、方式、来源类型、方向 | 单号、对方、方向、金额、有效额、已用、待核销、凭据号、来源、时点 | 登记（`scm:finance:payment:add`，含「来源 = 退款」选择器）、反向（`scm:finance:payment:reverse`，必填原因）、导出；抽屉同收款 |
| 核销 | 日期范围、source 单号、target 单号、方向 | 单号、source、target、金额、方向、原因、操作人、时点 | 核销（`scm:finance:write-off:add`，多行表单）、撤销（`scm:finance:write-off:reverse`）、导出 |

- 金额输入用 `InputNumber` + 字符串定点提交（照 `ScmStrictDecimalStringDeserializer` 形态，拒绝 JSON 数字字面量）。
- 所有写按钮 `v-privilege` 字面量必须落在 `finance-const.ts` 的权限集合内（契约测试钉住，§22）。
- 派生列（已核销 / 未核销 / 超额核销 / 结清 / 有效额 / 待核销）前端只格式化展示，不参与计算。
- **`overAppliedAmount > 0` 时标注「超额核销待处理」**（D-4）；`netAmount` 为负时原样显示负数并标红。
  两者都**不得**被文案叫作「客户余额」「钱包余额」「可用余额」—— 那是 P5 的概念，本期不存在。
- 收付款与核销列表**默认不隐藏反向行**（D-3），以方向列区分；否则纠错动作在页面上不可见。

## 21. Flyway 规划（按阶段发布，不提前占号）

**发布纪律（2026-09-26 修订，取代原「V65–V67 一次种完」的规划）**：
一个阶段只发布它**已经真实具备**的能力。`main` 每个阶段都必须保持可部署，因此：

- **没有 Controller 就不种 action 权限点** —— 提前种只会让生产库里出现
  「已授权但无任何端点使用它」的权限串，而 `menu_id` 一旦被真实库应用就不可回收，
  等于用一个永久号段去换一个不存在的能力。
- **没有 `.vue` 就不种页面菜单** —— `src/router/index.ts` 的
  `route.component = modules[relativePath]` 在文件缺失时得到 `undefined`，
  路由照样注册、菜单照样出现在侧栏，用户点开是空白页，而构建 / 类型检查 / 后端测试全绿。
  `visible_flag = false` **不是**解法：它只映射到 `meta.hideInMenu`，路由与 `component` 依然注册，
  深链依然落到空白页。
- 该纪律由 `SmartAdminMenuComponentPgIT` 全仓门禁强制：任何已发布的页面菜单，
  其 `component` 必须能在 `xsy-scm-web/src/views` 下找到（既有两处历史缺口走只减不增的显式基线）。

| 版本 | 阶段 | 内容 |
| --- | --- | --- |
| **V65**（已落地） | F1-1 | `V65__scm_finance.sql`：**8 张表** + 5 条序列（`finance_receivable_no_seq` 等，全局非重置）+ 全部 CHECK（含**七张事实表**的 `CHECK (deleted = FALSE)` append-only 约束；`finance_operation_log` 刻意不设 `deleted` 列）/ 5 条来源唯一索引 / 3 条反向唯一索引 / 全列 COMMENT；形态照 `V60__scm_sorting_task.sql`。**纯 DDL，零 t_menu 写入** |
| **V66**（已落地） | F1-3A | `V66__scm_finance_receipt_permission.sql`：data-only，只发布 **1500 隐藏目录 + 1521 `scm:finance:receipt:add`**，按 `role_code` 授 SUPER_ADMIN 兜底与 `SCM_FINANCE`。**不发布**任何页面菜单、也不发布 `receipt:query`（1513）—— 本阶段唯一的端点是登记接口，它返回刚写入的那一张单 |
| **V67**（已落地） | F1-3B | `V67__scm_finance_payment_permission.sql`：data-only，只补 **1522 `scm:finance:payment:add`**（父目录 1500 已在 V66 建好，不再新增目录行）。同样**不发布** `payment:query`（1514）与任何页面菜单 |
| V68+（待各阶段重扫取号） | F1-3C | 收付款反向：`receipt:reverse`(1526) 与 `payment:reverse`(1527) —— 两条独立破坏性权限，届时随各自第一次真实使用的端点落库 |
| 同上 | F1-4 | 核销 / 反向核销 / 手工红字应付：`write-off:add`、`write-off:reverse`、`payable:red`、`write-off:query` |
| 同上 | F1-5 | 五个 `*:query`（含 1511–1514）与 `scm:finance:export` |
| 同上 | F1-6 | 五个页面菜单 1501–1505（`component` 必须真实存在）+ `SCM_FINANCE` 页面授权，与 browser / deep-link 验证同一阶段落地；1500 目录已在 V66 发布，届时把 `visible_flag` 翻开而不是再种一行 |

**号段 1500–1531 是规划值，不是已占用事实**：F1-1 实测 `t_menu` 里没有任何 `scm:finance:*` 权限串、
没有任何 1500–1599 的菜单行。**F1-3B 之后占用为 1500 / 1521 / 1522 三行**（由 `ScmFinanceSchemaPgIT`
按 `containsExactly(1500, 1521, 1522)` 收紧，多一行就必须多一个真实端点或真实页面）。
每次落库前必须重扫 `t_menu` 实际占用（AGENTS.md 同一条纪律）。

执行纪律：

1. 开工前 `git fetch`，确认 `origin/main` 是本地 HEAD 的祖先或相等，重扫 `db/migration/` 最大版本与
   `t_menu` 最大 `menu_id`；基线落后或分叉时不选号（P1/P2 裁决同条纪律）。
2. 新增 / 改号后运行 `python tools/migration_checksum_guard.py sync`；**禁止**为让守卫变绿覆盖既有校验和。
   删除尚未发布的迁移要加 `--prune`（守卫默认拒绝丢弃快照条目），且**不得**用 `--force`。
3. 不 ALTER 任何既有表；不编辑任何已应用迁移。
4. **删除迁移源文件后必须清理 `sa-admin/target/classes/db/migration/` 里的同名产物**：
   Flyway 的 `locations` 是 `classpath:db/migration`，读的是编译产物而不是源目录，
   Maven 的资源拷贝又不会删除已消失的文件 —— 否则「已删除」的迁移会继续被应用，
   而 `mvn test` 全绿，只有真实库里的 `t_menu` 会暴露它。
5. 单号格式沿用 `ScmDocumentNumbers.format(prefix, number)`（前缀 + `yyyyMMdd`(Asia/Shanghai) + `%06d`），
   各表只保留自己的前缀常量与序列取号（照 `SortingConstant.TASK_NO_PREFIX` 形态）。

## 22. PostgreSQL IT 计划

沿用现有 SCM IT 基类继承链与「外部干净库 + `@Transactional` 回滚 + UUID 前缀隔离」形态
（`PgITDatabase` 从 `XSY_V2_DB_URL/USERNAME/PASSWORD` 取连接；非超管取证用
`RequestEmployee` + `administratorFlag=false` + `SmartRequestUtil.setRequestUser`，
功能权限用 `MockedStatic<StpUtil>`）。

| 测试类 | 断言要点 |
| --- | --- |
| `ScmFinanceReceivablePgIT` | 签收同事务生成单头+明细且金额 = 出库量 × `locked_unit_price`；全缺订单**跳过且不产生 0 元事实**；少拣按实发；重复触发不产生第二张（来源唯一索引）；`EXCEPTION` 不生成；红字在签收前批准时不生成、签收后补生成；**超额合法退货：approve 成功 → RED Receivable 全额生成 → 净应收可为负 → `openAmount = 0` → `overAppliedAmount` 正确 → 订单域事实（`approved_quantity` / `approved_amount` / `status`）一字未改**（D-2 / D-4，§3.3 修正） |
| `ScmFinancePayablePgIT` | 收货确认同事务生成；`DIRECT` 与 `WAREHOUSE_CONFIRM` 时点一致（都取 `confirmed_at`）；超收计入；少收关单不产生差异事实；手工红字应付引用原单、超额回滚（41137 —— **该码在本期只有这一个使用者**） |
| `ScmFinanceWriteOffPgIT` | M:N 核销；跨客户 / 跨供应商拒绝（41136）；超额拒绝（41135）；派生余额 = 核销行之和（逐行核对）；反向核销唯一（第二条撞 `uk_finance_write_off_single_reverse`，服务层先回 41143）；退款付款来源唯一、金额必须等于 `refund_amount`、未 COMPLETED 拒绝；`netAmount < 0` 的应收 `openAmount = 0` 因而不可再被核销 |
| `ScmFinanceReversePgIT` | D-3 的收付款反向：`REVERSE` 行 `reverse_of_id` / `reason` 必填、`NORMAL` 行两列必须 NULL（DB CHECK）；一条 NORMAL 最多一条 REVERSE（库级）；**已用额 > 0 时反向被拒（41142）**，先反向核销后可反向；反向行有效额归零、待核销余额不为负；Payment 的 `REVERSE` 行 `source_type/source_id` 必须 NULL，且不与原行抢 `ORDER_REFUND` 来源唯一键 |
| `ScmFinanceConcurrencyPgIT` | `@Transactional(propagation = NOT_SUPPORTED)` + `CountDownLatch` 起跑线：并发签收双生成、并发核销同目标（一成一超额）、并发反向核销同行、并发退款双付款、并发反向同一收款（一成一 41143）、核销与红字并发（同一应收）；断言「成功数 + 失败码 + 最终派生余额」三者一致 |
| `ScmFinanceRoleMatrixPgIT` | `SCM_FINANCE` 持 1511–1531 的正向取证（含 1526 / 1527 两个反向权限）；无财务权限账号接口层 30005/无权；`administrator_flag=false` 的销售 / 采购账号看不到财务端点；范围 fail-closed（无 `orderSellerScope` 授权返回 0 行）；D-5 的三类范围（收款按 `customerSellerScope`、付款供应商侧不收窄、核销随 target）各自取证；导出与列表同范围 |
| `ScmFinanceSchemaPgIT`（F1-1 交付） | 只测 schema / permission 契约，见 §22.1 |

**已删除的旧断言（§3.3 修正）**：「自动红字超额 → `FINANCE_RED_AMOUNT_EXCEEDED`(41137) →
`OrderReturn approve` 整单回滚」这条口径**作废**，不得再出现在任何 IT 或 E2E 里。
D-2 / D-4 裁决为 A 之后，自动红字**没有金额上限**，抛错阻塞 approve 正是被否决的行为。
41137 只保留给手工红字应付等**人工财务动作**（§8.3）。

- 每条 SQL 分支（空筛选 / 全筛选）都要在真实 PostgreSQL 执行，照 `ScmReportPgIT` 的「逐分支渲染全部 mapper 语句」门禁。
- 并发 IT 的「定向重复执行」是**验证流程**（命令行层面重复 N 次），不是代码里的循环 —— 仓库现有并发 IT 无重复轮次实现。

### 22.1 F1-1 实际交付的契约测试

F1-1 只交付 schema 与骨架，因此测试只钉契约，**不提前写 F1-2 的业务 IT**。三个类：

| 测试类 | 断言 |
| --- | --- |
| `ScmFinanceSchemaPgIT`（17 项，真实 PostgreSQL） | 8 张 `finance_*` 表 + 5 条序列存在、零外键；不得出现 `status` / `settled_amount` / `open_amount` / `due_date` / `approver` / 币种 / 税列；**七张事实表**的 `deleted = FALSE` append-only CHECK 实测拒绝软删，`finance_operation_log` 无 `deleted` 与 `version` 列；金额与数量恒正、单价非负、`version >= 0`、**全部 numeric 列逐列断言 (18,4)**；四类配对 CHECK 以 SAVEPOINT 隔离的坏数据实测拒绝（含「手工红字带 `source_id`」「反向付款沿用 `ORDER_REFUND` 来源」「来源与方向错配」「收付款跨侧核销」「日志类型越界」「JSONB 非 object」）；来源唯一索引谓词逐字核对且实测重复生成被拒；三条反向唯一索引实测「一条 NORMAL 只能反向一次」；实测 `external_reference` **不唯一**、手工红字可并存；**13 个 Java 枚举与 DB CHECK 白名单双向逐值相等**；**阶段边界**：财务段 `t_menu` 恰好是 1500（隐藏目录、`component IS NULL`、无 `menu_type = 2` 行）
与 1521 两行，`scm:finance:*` 权限串恰好只有 `receipt:add` 一个，授权行数 2 × 2；
且全库 `*:scope:all:query` 仍恰好是 V55 的五个维度（D-5 未新增放宽点） |
| `FinanceReadOnlyContractTest`（4 项，静态扫描，不依赖数据库） | `module/scm/finance` 的 Java 字符串字面量与 mapper XML 内**不存在**针对 `sales_order*` / `order_return*` / `order_refund` / `purchase_*` / `inventory_*` / `delivery_*` / `sorting_*` 的 INSERT / UPDATE / DELETE / TRUNCATE；mapper 注解里不写 SQL（AGENTS.md §8）；**8 个实体的 `@TableName` 全部以 `finance_` 开头**（最强边界证据：BaseMapper 的写方法因此够不到业务表）；不 `import` report 包 |
| `SmartAdminMenuComponentPgIT`（3 项，**全仓门禁**，非财务专属） | 每个已发布页面菜单（`menu_type = 2`、`component` 非空、未删除、非外链）的 `component` 都必须能在 `xsy-scm-web/src/views` 下找到；既有两处历史缺口走**只减不增**的显式基线，且基线条目一旦被修好就必须删除；财务段本阶段不存在任何页面菜单。这条门禁就是为防止「授权页面菜单 → `component` 不存在」再次发生而加的（§21 发布纪律） |

**为什么只读契约做成静态扫描而不是运行时拦截**：F1-1 一条写路径都没有，运行时拦截抓不到任何东西；
等 F1-2 接上生成器再补断言，恰好错过唯一一次「新增代码是否越界」的廉价评审时机。

## 23. E2E 验收矩阵

`xsy-scm-web/e2e/`，命名 `scm-finance-*.spec.ts`，`workers: 1 / fullyParallel: false`。

| spec | 场景 |
| --- | --- |
| `scm-finance-receivable.spec.ts` | 备货 → 订单 → 分拣（含一行少拣）→ 组单发车 → **签收** → 应收页出现单头与两行明细（少拣行金额按实发）→ 抽屉展示出库行来源 → 导出 xlsx 合法 |
| `scm-finance-receivable-red.spec.ts` | 签收后发起退货并批准 → 红字应收出现且引用原单 → 原单 `openAmount` 下降 → **再批准一笔使累计红字超过原应收的退货：approve 成功、RED 全额生成、`netAmount` 为负、`openAmount = 0`、页面标注「超额核销待处理」、不出现 41137**（D-2 / D-4） |
| `scm-finance-payable.spec.ts` | 采购 → 收货（含超收）确认 → 应付页出现 → 金额 = 有效量 × 采购价 → 少收关单后应付不追加 → 手工红字应付超额被拒（41137） |
| `scm-finance-write-off.spec.ts` | 登记收款（预收，无应收）→ 待核销余额可见 → 核销两笔应收（M:N）→ 派生结清状态正确 → 撤销核销 → 余额回退 → 重复撤销被拒 |
| `scm-finance-receipt-reverse.spec.ts` | 登错一笔收款 → 已核销时反向被拒（41142）→ 先反向核销 → 反向收款（必填原因）→ 有效额归零 → 重新登记正确收款 → 列表同时可见原行与反向行（D-3） |
| `scm-finance-payment-refund.spec.ts` | 退货批准 → 退款完成 → 财务登记退款付款（来源选择器）→ 第二次登记同一退款被库级拒绝 → 应收未被二次冲减 |
| `scm-finance-permission.spec.ts` | 非超管财务账号全流程；销售账号深链财务页 404 + 接口被拒；无导出权限账号导出按钮隐藏且接口 30005；无反向权限账号看不到反向按钮 |

- 共享夹具断言 0 pageerror；权限取证账号一律 `administrator_flag = false`。
- E2E 账号经 `tools/e2e_accounts.py` 创建（必需 `XSY_V2_PG_DB` 等变量，不给即拒绝运行）。

## 24. 分阶段实施计划 F1-1 ~ F1-8

| 阶段 | 内容 | 交付物 | 验收 | 依赖 |
| --- | --- | --- | --- | --- |
| F1-1 | **只有 V65（纯 DDL）** + 后端骨架（entity / dao / 常量 / 枚举 / 错误码 / 日志 recorder / Service 空骨架）。**不种任何菜单与权限**，不建 form / VO / 只读业务 DAO / mapper XML（随各自首个调用方与首个测试落地） | 迁移 + 骨架编译通过 + §22.1 三个契约测试类 | `migration_checksum_guard check` + `verify.py backend` + schema / 只读 / 菜单组件契约 IT | F1-0.5 收口（已完成） |
| F1-2 | 生成器：签收 → 应收、收货确认 → 应付、退货批准 → 红字应收（含补生成） | 三个触发点接入 + `ScmFinanceReceivablePgIT` / `ScmFinancePayablePgIT` | IT 全绿；触发事务回滚时财务事实同回滚 | F1-1 |
| F1-3 | 收款 / 付款登记（含退款付款来源校验） | 写命令 + 幂等 + `finance_operation_log` | IT；权限负向 | F1-1 |
| F1-4 | 核销与反向核销、手工红字应付、收付款反向（D-3） | 写命令 + 锁序 + 并发 IT | `ScmFinanceWriteOffPgIT` + `ScmFinanceReversePgIT` + `ScmFinanceConcurrencyPgIT` | F1-2、F1-3 |
| F1-5 | 查询与导出（5 页后端 + Excel） | 只读端点 + 派生列（含 `openAmount` / `overAppliedAmount` / `effectiveAmount`） | IT 逐分支；导出 xlsx 校验 | F1-4 |
| F1-6 | 前端 5 页 + 契约测试 + 权限矩阵 IT | 页面 / api / const / 契约 mjs | `lint` / `test` / `ts-ratchet` / `build` 全绿；`ScmFinanceRoleMatrixPgIT` | F1-5 |
| F1-7 | E2E 七条 + 全量回归 + 文档收口 | `e2e/scm-finance-*.spec.ts`、`docs/progress.md` 记录 | `verify.py all`；浏览器全量 0 pageerror | F1-6 |
| F1-8 | Finance R0 接轨：往来概览页 + 只读端点 + 导出（§27） | 菜单 1217 / 权限 1218 的 data-only 迁移（届时重扫号段；F1-1 只用了 V65，故本阶段从实际最大号 +1 起）+ report 域只读 finance 的 DAO | 页面 / 接口 / 导出三口径一致；A 类指标名与口径零变化；§27.2 的 stock / flow 口径成立 | F1-7 |

每阶段完成即停，不顺带实现 R2 / P5 的任何指标（对齐 P1 裁决 14 的写法）。
**D-1…D-5 已全部收口，不再有「暂定口径」或「待确认」标注**：
F1-2 的红字生成**不带任何金额校验**（§8.2，D-2 / D-4 的正式结论，不是过渡措施）；
F1-3 不提供收付款的修改 / 作废，纠错一律走 F1-4 的反向事实（§6.3，D-3）；
F1-5 / F1-6 的收付款与核销读侧范围按 §15 的 D-5 口径实现，无需再加「待确认」注释。

## 25. 风险与不变量

### 25.1 不变量（写入实现注释与 IT 断言）

1. 财务事实不可通过修改历史记录模拟纠错；冲销 / 撤销一律新增反向事实。
2. 应收、应付、收款、付款、核销必须可追溯到来源业务事实与操作人、时点。
3. 财务不写库存事实；财务不写订单状态机与 `settlement_*`。
4. `module/scm/finance` 对业务表**只读**；对 `inventory_*` 完全只读。
5. R1 不建立第二套售价 / 采购价事实源；应收单价只取 `locked_unit_price`，应付单价只取 `purchase_price`。
6. 已核销额 / 未核销额 / 超额核销 / 结清状态 / 有效额一律读时派生，不落状态列与余额列。
7. 金额恒 `NUMERIC(18,4)`、`HALF_UP`、scale 4；方向编码在 `entry_type`，金额列恒 `> 0`。
8. 来源唯一索引是防重复财务事实的最终数据库防线；幂等键只防重复请求。
9. 范围 fail-closed；用户传入的 `customerId / supplierId` 只收窄不放宽；禁止角色 bypass。
10. 查询与导出共用同一次范围解析与同一 Service 方法。
11. **自动红字应收忠实映射已成立的 `OrderReturn APPROVED`，不设金额上限、不扣已核销额、
    永不阻塞 approve**（D-2 / D-4）；净应收允许为负，负值只由 `openAmount` / `overAppliedAmount` 表达。
12. **八张财务表的 `deleted` 被 `CHECK (deleted = FALSE)` 锁死**（§0 第 7 条），
    实体不用 `@TableLogic`；`finance_operation_log` 无删除入口。

### 25.2 风险

| 风险 | 现状证据 | 处置 |
| --- | --- | --- |
| 签收与应收同事务会让 `sign` 事务变长 | `sign` 现持线路锁 + 线路行锁 | 生成器只 INSERT、不锁业务表；IT 断言回滚一致性 |
| 红字可冲不足（少拣 + 全额退货）可达 | 退货上限是 `actual_quantity`（`OrderReturnService.java:135`），应收上限是实发量 | **D-4 已裁决为 A**：全额生成 RED、净应收可为负、`overAppliedAmount` 显性表达为「超额核销待处理」；偏差源头在订单域与库存域之间，财务不兜底也不阻塞 |
| 负净应收被误读成「客户余额」 | D-4 允许 `netAmount < 0` | 文案硬约束（§7 / §20）：只叫「超额核销待处理」，禁用「客户余额 / 钱包余额 / 可用余额」；不自动退款、不自动结转 |
| 收款 / 付款录错无纠正路径 | 第二批 Q20 只列四类写动作；反向核销纠正不了登错的单据本身 | **D-3 已裁决为 A**：§6.3 的 append-only 反向 + 「反向前已用额 = 0」前置 + 1526 / 1527 独立权限 + 两个日志类型 |
| 存量已签收 / 已收货事实无财务对应 | P2 已真实出库 | **D-1 已裁决为 A**：不回填、本期无补生成 API 与回填权限；生成器保持可重放，将来历史迁移单独立项、单独验收 |
| 财务页发生额与历史记忆对不上 | D-1 的直接后果 | 页面注明「自上线日起算」；历史往来回业务域查询；R0 的 A 类影子指标不受影响（§27.1） |
| 财务域只读业务表是新边界 | 既有跨域写都走契约（`PurchaseInventoryContract`） | §0 第 2 条 + 不变量 4 + IT 断言「finance 包内无业务表写语句」（契约测试） |
| 40921 与 `ProductErrorCode` 同码值重复声明 | `ScmCommonErrorCode.java:9-12` 自述 | 本期复用不重排；在 F1-1 记录为已知技术债，不顺手重构 |

## 26. D-1 … D-5 收口落点（2026-09-26 负责人裁决，五条全部为 A）

> 权威全文在 `docs/decisions.md`「P3 Finance R1 裁决（第三批，2026-09-26）」。
> 本节只给落点索引，**不再保留候选并列表**；候选过程见提交 `caace54a` 的本稿历史版本。
> 本稿已无「待裁决 / 待补裁决 / 暂定口径 / 待确认」表述。

| D | 裁决 | 本稿落点 | 实现期硬约束 |
| --- | --- | --- | --- |
| D-1 | 不回填上线前既有的 `SIGNED` / `CONFIRMED` / `APPROVED` 事实 | §19 | 本期无历史补生成 API、无回填权限；生成器仍须可重放、来源幂等；将来历史迁移单独立项、单独验收 |
| D-2 | 自动红字的可生成额度**不扣除**既有核销额 | §7、§8.2 | 「应收 100 → 收款 100 → 核销 100 → 退货 20」必须 approve 成功且 RED = 20；Finance 不反向控制订单域状态机 |
| D-3 | Receipt / Payment 纠错 = append-only 反向事实 | §5、§6、§6.3、§11、§16、§17 | 三列 `entry_type` / `reverse_of_id` / `reason` + 两条 `uk_*_single_reverse`；反向前已用额必须 = 0；权限 1526 / 1527；日志类型 `RECEIPT_REVERSE` / `PAYMENT_REVERSE`；禁止 UPDATE / DELETE / 软删 / 改金额 / 改对方 |
| D-4 | 超额合法退货**全额**生成 RED Receivable | §7、§8.2、§20、§22、§23 | 不阻止 approve、不封顶、不静默丢差额、不改 `OrderReturn` / `approved_quantity` / `approved_amount`；净应收可为负；`openAmount = max(net − writtenOff, 0)`、`overAppliedAmount = max(writtenOff − net, 0)`，后者展示为「超额核销待处理」；41137 不用于自动红字 |
| D-5 | 收款 `customerSellerScope`；付款供应商侧沿用采购团队共享读、客户侧同收款；核销随 target | §15 | `RECEIVABLE → orderSellerScope`、`PAYABLE → purchaserScope`；禁止 `if role == FINANCE then bypass`，`SCM_FINANCE` 的全范围来自正式权限配置；将来开放给部分范围财务岗仍复用同一套 scope |

**五条裁决的共同取向**：Finance R1 是**已成立业务事实的忠实映射**，不是第二道业务闸门。
凡「财务规则会让订单域 / 采购域 / 退货域的合法动作失败」的候选一律被否决（D-2 / D-4）；
凡「用修改历史记录来纠错」的候选一律被否决（D-3）；
凡「用代码里的角色判断代替授权数据」的候选一律被否决（D-5）；
凡「本期顺手把历史数据也接进来」的候选一律被否决（D-1）。
41137 因此只剩一个使用者（手工红字应付，§8.3），这也是它没有被删除的原因。


## 27. 与 Finance R0 的接轨（= F1-8，P3 收口后的 R0 升级）

### 27.1 两类口径必须严格区分

**A 类：原业务参考指标（既有，不改名、不改口径）**。
`confirmedOrderAmount`、各销售页 `settlementAmount`、`receiptReferenceAmount`、`inboundCostAmount`、
`completedRefundAmount`、`orderAmount / submittedPurchaseAmount`、`avgTransactionPrice`、
`inventoryBookValue`（`MAP/report/ReportDao.xml:139,295,512,534,150,592,293,186`）。
它们是「订单确认额 / 收货参考额 / 入库成本额」等业务事实的只读聚合，
**任何一处都不得改名为「应收 / 应付 / 已收 / 已付」**，也不得改读 finance 表
（`docs/decisions.md` P3 全局不变量 6、调研稿 §10.3 第 2 条）。

**B 类：Finance R1 的真实财务事实**。`finance_receivable / finance_payable / finance_receipt /
finance_payment / finance_write_off`，以及由它们读时派生的**已核销金额 / 期末待收 / 期末待付**。

两类数字**必然不等**（A 按 `confirmed_at` + 订单结算量，B 按签收 / 收货确认时点 + 实发 / 实收量），
页面与导出必须以 tooltip / 列名说明各自口径，不得让用户以为在核对同一件事（调研稿 §10.3 第 3 条）。

**命名硬约束（§3.4 修正）**：数据源是 `finance_write_off` 的指标一律叫
**「已核销金额」**，**不得**叫「已收款」「已付款」——
核销是**分配关系**，不是资金动作。真正的实际资金收付来自 `finance_receipt` / `finance_payment`，
本期六指标**不含**它们（避免与核销额并列后被读成同一件事）。
同理，「待收 / 待付」必须带**期末**限定词，因为它是存量而不是本期发生额（见 §27.2）。

### 27.2 R0 读取真实往来的设计

新增一张报表中心页面「往来概览」与对应只读端点，**数据源为 finance 表**（report 域只读 finance 表，
与 report 只读业务表同形；finance 域不为此反向依赖 report）：

```http
POST /scm/report/finance/overview        六指标（见下）
POST /scm/report/finance/receivable/aging-free-detail   应收明细分页（不带账龄，账龄属 R2）
POST /scm/report/finance/payable/aging-free-detail      应付明细分页
POST /scm/report/finance/overview/export 等三个导出
```

**F1-8 第一版六指标固定为（不得增删、不得改名）**：

```text
应收发生额   应收已核销   期末待收
应付发生额   应付已核销   期末待付
```

**发生额（Flow）与期末余额（Stock）必须严格区分（§3.4 修正）**。
反例：8 月形成应收 100、9 月核销 100。若「待收 = 本期发生额 − 本期核销额」，
查询 9 月会得到 **待收 = −100** —— 一个既不是流量也不是存量的无意义数字。
因此三个指标各自的时间谓词不同：

```text
窗口统一为 [startAt, endAt)（Asia/Shanghai 半开区间，复用 ScmReportTimeRangeResolver）

应收发生额   = Σ netAmount(receivable)            WHERE event_at       ∈ [startAt, endAt)
             其中 netAmount = Σ item(NORMAL 单头).amount − Σ item(RED 单头).amount
             ← 纯流量：本期新形成的净应收，与核销无关

应收已核销   = Σ write_off(NORMAL, target=RECEIVABLE).amount
             − Σ write_off(REVERSE, target=RECEIVABLE).amount
             WHERE written_off_at ∈ [startAt, endAt)
             ← 纯流量：本期发生的核销分配额，不是本期收到的现金

期末待收     = Σ netAmount(receivable)            WHERE event_at       <  endAt
             − Σ 有效核销额(write_off)            WHERE written_off_at <  endAt
             ← 存量：截止 endAt 的全部净应收减去截止 endAt 的全部有效核销，
               **不受 startAt 影响**；再按单据逐张应用 §7 的
               openAmount = max(net − writtenOff, 0) 与
               overAppliedAmount = max(writtenOff − net, 0) 后汇总，
               因此期末待收恒 >= 0，超额部分单独以「超额核销待处理」列示，不做跨单据轧差

应付发生额 / 应付已核销 / 期末待付 同形（source = PAYMENT、target = PAYABLE）
```

**逐单派生后再汇总，不先汇总再派生**：`openAmount` 的 `max(..., 0)` 是非线性的，
在汇总值上取 max 会让「A 单超额 20、B 单待收 20」错误地显示为待收 0。
这条与 §7 的单据级公式同源，页面 / 接口 / 导出共用同一 Service 方法（P0 裁决 10）。

菜单与权限：页面 `menu_id = 1217`（报表中心 1200 目录下新页）、权限点 `1218 = scm:report:finance:query`；
导出沿用 `scm:report:export` AND 新查询权限。**初始只授 `SCM_FINANCE` 与超管**，
与第二批 Q24「财务数据只面向财务权限用户」一致；`SCM_FINANCE` 已持 1200–1216，追加 1217/1218 即可。
（1217 / 1218 在 F1-8 开工时仍须重扫号段；报表中心 1200–1216 已由 V50 占用。）

### 27.3 明确不开始

利润、毛利、账龄、客户对账、供应商对账、财务分析 —— 仍属 Finance R2（第一批 Q15、主线计划 §6）。
本节的六个指标**不含**任何账龄分桶与对账单形态，也**不含**实际资金收付额
（`finance_receipt` / `finance_payment` 的聚合），后者若需要属 R2 另行裁决。
