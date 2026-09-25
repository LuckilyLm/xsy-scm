# Finance R1 正式设计稿（F1-0）

> 状态：**设计稿，等待设计评审**。本文只定义「做什么、怎么落」，不产生任何代码、迁移或页面。
> 基线：`origin/main @ 5ad4e08`（2026-09-25 设计评审修订时 `git fetch` 重扫；本地工作区 HEAD `ce82f00`
> 是其祖先、未分叉，两者仅差 `ScmInventoryReservationConcurrencyIT.java` 一个测试文件）。
> `db/migration/` 实际最大版本 **V64**；`t_menu` 已占用最大 **1421**；
> SCM 错误码已占用最大 **41128**。选号与选迁移版本前必须重新 `git fetch` 并重扫。
>
> 依据（按优先级）：当前代码与数据库 → 本稿 §1 所列裁决 →
> [`finance-r1-requirement-and-design-investigation.md`](finance-r1-requirement-and-design-investigation.md) →
> [`finance-r1-first-decision-sheet.md`](finance-r1-first-decision-sheet.md) →
> [`finance-r1-second-decision-sheet.md`](finance-r1-second-decision-sheet.md) →
> `docs/decisions.md`「P3 Finance R1 裁决（2026-09-25）」→ `docs/requirements/产品功能需求基线.md`。
> **不参考**蔬东坡或其他系统增加任何功能。
>
> 硬约束：27 条裁决全部已收口；本稿**只落实裁决，不新增裁决未要求的对象、状态、权限或列**。
> 实现期若发现必须补规则，回到 `docs/decisions.md` 追加裁决，不在本稿或代码里就地决定。
> 本稿末尾 §26 列出 5 项**待补裁决（D-1…D-5）**，均不阻塞 F1-1 建表，但各自阻塞对应写路径或读侧的实现；
> 其中 D-2 / D-3 / D-4 在 2026-09-25 设计评审中被**重新打开**，候选与业务例子见
> [`finance-r1-final-decision-sheet.md`](finance-r1-final-decision-sheet.md)。

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
| 收款 | `finance_receipt` | 一笔钱一单，可无应收（预收，第一批 Q16） | 财务人工登记 | 无 |
| 付款 | `finance_payment` | 一笔钱一单，可无应付（预付）；退款付款带来源 | 财务人工登记 | 无 |
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
可冲校验按 `order_item_id` **聚合该订单行的全部正常应收明细**后比较（见 §8.2）。
**不新增 allocation 表**（评审意见三）。

**来源锚点用 `inventory_outbound_item.id` 而不是 `sales_order_item_id`**：
`V63:29-33` 注释明确出库行**刻意不建** `(sales_order_item_id)` 唯一索引
（一条订单行将来可能被再出一行），所以行级唯一只能挂在出库行主键上。

### 3.3 生成规则（正常应收）

触发：`DeliveryRouteService.sign` 将某订单置 `SIGNED` 的**同一事务**内，调用
`FinanceReceivableService.generateOnSign(orderId, signedAt, operator)`。

1. 读 `delivery_route_order`（已 `SIGNED`）→ 取 `order_id`；
2. 读该订单的 `inventory_outbound_item`（`sales_order_item_id IS NOT NULL`）；
   **若为空：直接 return，不生成单头、不生成明细、不生成 0 元事实**（第二批 Q8，跳过是成功语义）；
3. 逐行 join `sales_order_item` 取 `locked_unit_price`，`amount = round(quantity × locked_unit_price, 4, HALF_UP)`；
4. `单头 amount = Σ 明细 amount`；若合计为 0（全部单价 0 的极端情形）同样跳过，不生成 0 元事实；
5. 插入单头 + 明细，`insertOnConflictDoNothing` 语义：来源唯一索引命中即视为已生成，直接返回（幂等）。
6. 读该订单**此前已 `APPROVED` 且尚未生成红字应收**的 `order_return`，逐单执行 §8 的红字生成
   （第二批 Q27 的 ①②③）。

不在 `EXCEPTION` 分支调用（第二批 Q6：`EXCEPTION` 不形成应收）。

## 4. Payable / PayableItem

### 4.1 `finance_payable`

列与 `finance_receivable` 同形，差异：

| 列 | 取值 |
| --- | --- |
| `payable_no` | 前缀 `AP` |
| `source_type` | `VARCHAR(32) NOT NULL CHECK IN ('PURCHASE_RECEIPT','MANUAL')`；与 `entry_type` 配对 CHECK：`NORMAL → 'PURCHASE_RECEIPT'`、`RED → 'MANUAL'`（2026-09-25 评审意见二） |
| `source_id` | `BIGINT`；`NORMAL` 时 NOT NULL = `purchase_receipt.id`；`RED`（手工调整）时 NULL |
| `original_payable_id` | `RED` 时 NOT NULL，指向被冲原应付 |
| `reason` | `RED` 时必填非空（第一批 Q13） |
| `purchase_order_id` | `BIGINT NOT NULL`（红字也指向原采购单） |
| `supplier_id` / `supplier_name_snapshot` | 结算对方 |
| `entry_type` | `NORMAL / RED` |
| `original_payable_id` | `RED` 时 NOT NULL |
| `amount` / `event_at` / `reason` | 同应收；`event_at` = `purchase_receipt.confirmed_at` |

**不存** `purchaser_id`（范围归属，读时 join `purchase_receipt → purchase_order`，第二批 Q23 指定路径）、
`warehouse_id`（应付不按仓收窄）、`status`、`settled_amount`、`due_date`。

索引：`uk_finance_payable_source_active ON (source_type, source_id) WHERE deleted = FALSE`、
`idx_finance_payable_supplier_event ON (supplier_id, event_at DESC) WHERE deleted = FALSE`、
`idx_finance_payable_order ON (purchase_order_id) WHERE deleted = FALSE`。

### 4.2 `finance_payable_item`

| 列 | 语义 |
| --- | --- |
| `source_type` | `CHECK IN ('PURCHASE_RECEIPT_ITEM','MANUAL')`；正常明细取前者，红字明细取 `'MANUAL'` 且 `source_id` 为 NULL（§8.3） |
| `source_id` | `purchase_receipt_item.id`；`MANUAL` 时为 NULL，来源唯一索引的部分谓词将其排除 |
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
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | 一笔钱一单 |
| `method` | `VARCHAR(16) NOT NULL CHECK IN ('CASH','BANK_TRANSFER','OTHER')` | 第二批 Q21 固定三值，Java enum + CHECK |
| `received_at` | `TIMESTAMPTZ NOT NULL` | 收款时点，登记人填写、必填；服务端不做「不得晚于当前」之类的额外校验（与既有业务时点列同形） |
| `external_reference` | `VARCHAR(128)`，唯一索引 `WHERE deleted = FALSE AND external_reference IS NOT NULL` | 资金凭据号（第二批 Q25：只存文本，不挂附件） |
| `remark` | `VARCHAR(500)` | |
| `version` / `deleted` / 审计四列 | | |

**无来源列**：收款一律人工登记；与应收的关系只通过 `finance_write_off` 表达（Q16 允许无应收的预收）。
**无状态列**：待核销余额读时派生（Q17）。
索引：`idx_finance_receipt_customer_received ON (customer_id, received_at DESC) WHERE deleted = FALSE`。

## 6. Payment（付款）

| 列 | 类型 / 约束 | 语义 |
| --- | --- | --- |
| `payment_no` | 前缀 `PM`，唯一 | |
| `counterparty_type` | `VARCHAR(16) NOT NULL CHECK IN ('SUPPLIER','CUSTOMER')` | 付款给供应商（应付/预付）或客户（退款，第一批 Q19） |
| `counterparty_id` / `counterparty_name_snapshot` | NOT NULL | |
| `amount` | `NUMERIC(18,4) NOT NULL CHECK (amount > 0)` | |
| `method` | 同收款三值 | |
| `paid_at` | `TIMESTAMPTZ NOT NULL` | |
| `external_reference` | `VARCHAR(128)`，唯一（同收款） | |
| `source_type` | `VARCHAR(32) CHECK IN ('ORDER_REFUND')`，可空 | 退款付款的来源（第一批 Q19） |
| `source_id` | `BIGINT`，与 `source_type` 成对 CHECK | `order_refund.id` |
| `remark` / `version` / `deleted` / 审计四列 | | |

索引：

```text
uk_finance_payment_source_active      ON (source_type, source_id)
                                      WHERE deleted = FALSE AND source_id IS NOT NULL
idx_finance_payment_counterparty_paid ON (counterparty_type, counterparty_id, paid_at DESC) WHERE deleted = FALSE
```

`uk_finance_payment_source_active` 即第二批 Q26 要求的「退款付款库级唯一」：
同一 `order_refund` 最多一笔正式退款 `Payment`（第一批 Q19）。

**退款付款的登记流程**：财务在付款页选择来源类型 `ORDER_REFUND` 与某张 `order_refund`，
服务端校验该退款 `status = 'COMPLETED'`、金额等于 `order_refund.refund_amount`（第一批 Q19）、
`counterparty_type = 'CUSTOMER'` 且 `counterparty_id = order_refund.customer_id`；
随后插入 `finance_payment`，来源唯一索引兜底防重。
**`Payment` 不冲减应收**（第二批 Q27：Return 负责红冲，Refund Payment 只负责真实资金退付，避免双重冲减）。

### 6.3 收付款的 append-only 纠错候选（D-3，**待裁决，本稿不定**）

评审指出：「反向核销 + 重新登记」只能纠正**分配**，纠正不了**登错的 Receipt / Payment 本身**
（金额错、对象错、凭据号错）。按全局不变量 1/2，纠错只能新增反向事实。候选形态（评审优先评估项）：

`finance_receipt` 与 `finance_payment` 各增三列：

```text
entry_type   VARCHAR(8)  NOT NULL CHECK IN ('NORMAL','REVERSE')
reverse_of_id BIGINT     CHECK：REVERSE 时 NOT NULL 且指向同表 entry_type='NORMAL' 的行；NORMAL 时 NULL
reason       VARCHAR(500) CHECK：REVERSE 时必填非空
```

金额仍恒正；反向事实不修改原记录。配套部分唯一索引（候选）：

```text
uk_finance_receipt_single_reverse ON finance_receipt (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE'
uk_finance_payment_single_reverse ON finance_payment (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE'
```

对六个面的影响（候选成立的前提，逐条都要在 IT 里钉住）：

| 面 | 影响 |
| --- | --- |
| 核销余额 | 收付款「有效额」变为 `amount − Σ REVERSE.amount`，待核销余额 = 有效额 − 已用额；派生公式与 §7 同构，仍读时算 |
| 已用额与反向的先后 | 若一张收款已被核销后再反向，会出现「已用 > 有效额」的负待核销。候选约束：**反向前必须已用额 = 0**（即先反向其全部核销），否则 `41135` 同形错误拒绝 —— 保持派生值非负、语义不崩 |
| 退款 Payment 来源唯一索引 | `uk_finance_payment_source_active` 已带 `source_id IS NOT NULL` 谓词；反向行必须 `source_type/source_id = NULL`（CHECK：`REVERSE → source_type IS NULL`），否则反向行会与原行抢同一 `ORDER_REFUND` 来源或被误认为第二笔退款付款 |
| 查询 | 列表默认含反向行并以方向列标识（与核销页同形）；「待核销余额 > 0」筛选按有效额计算；不默认隐藏反向行，否则纠错不可见 |
| 导出 | 与查询同一 Service 方法、同一口径（P0 裁决 10）；导出列增加方向与 `reverse_of_id` 指向单号 |
| operation log | 新增 `operation_type`：`RECEIPT_REVERSE` / `PAYMENT_REVERSE`；`before_data` 为原行有效额快照、`after_data` 为反向后快照 |
| 并发 | 反向需 `FOR UPDATE` 锁原行（rank 1/2）+ `uk_*_single_reverse` 兜底「一行只被反向一次」；与核销并发时两者都锁原收付款行，串行后各自校验已用额/有效额 |

**不采用的替代**：允许修改 / 软删收付款单（违反不变量 1/2 与第二批 Q20 的单步生效语义）；
把登错交给「再登记一笔相反方向的收款」表达（会污染 `method` / 凭据号语义，且预收余额口径失真）。
候选的最终取舍与「反向前是否强制已用额 = 0」见
[`finance-r1-final-decision-sheet.md`](finance-r1-final-decision-sheet.md) **D-3**。

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

**派生口径（第二批 Q17，读时计算，不落库）**：

```text
某应收的已核销额 = Σ write_off(NORMAL, target=该应收).amount − Σ write_off(REVERSE, target=该应收).amount
某应收的未核销额 = 该应收净额 − 已核销额
   其中 应收净额 = Σ receivable_item(NORMAL 单头).amount − Σ receivable_item(RED 单头).amount
结清状态       = 未核销额 = 0 且 应收净额 > 0 → SETTLED；未核销额 = 应收净额 → OPEN；其余 → PARTIAL
某收款的已用额 = Σ write_off(NORMAL, source=该收款).amount − Σ write_off(REVERSE, source=该收款).amount
某收款的待核销余额 = amount − 已用额（> 0 时即「待核销款」，第一批 Q16）
```

**负数情形的展示口径随 D-2 / D-4 裁决**：若裁决允许「未核销额为负」或「应收净额为负」，
上述三态不足以表达，需补一个派生展示值（例如「超额红字 / 多收待处理」）；
在裁决前，派生查询对负值**原样返回负数并由前端标红提示**，不折叠进 SETTLED / OPEN / PARTIAL 任何一态。

**核销校验（服务层，全部在持有目标锁之后）**：

1. `source` 与 `target` 的结算对方必须一致（读已锁定的 source / target 行比对，**不在核销行上冗余对方列**，
   避免同一事实出现第二个权威来源），否则 `FINANCE_COUNTERPARTY_MISMATCH`（第二批 Q17 禁止跨客户/供应商核销）；
2. 本次 `amount` ≤ 目标未核销额，否则 `FINANCE_WRITE_OFF_AMOUNT_EXCEEDED`；
3. 本次 `amount` ≤ source 待核销余额，否则同上错误码；
4. 一笔提交可含多行（M:N），在同一事务内按 §14 锁序逐行写入。

## 8. 红字 / 反向事实模型

### 8.1 统一纪律（全局不变量 1/2）

- 不 UPDATE、不 DELETE、不软删隐藏任何既有财务事实；纠错一律**新增反向事实**。
- 反向事实必须：引用原事实、带方向（`entry_type`）、必填原因、记录操作人与时间、保留完整追溯链
  （第一批 Q13、第二批 Q18）。

### 8.2 应收红字（第二批 Q27；2026-09-25 评审修订）

触发：`OrderReturnService.approve` 将退货置 `APPROVED` 的**同一事务**内，调用
`FinanceReceivableService.generateRedOnReturnApproved(orderId, returnId, approvedAt, operator)`。

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

可冲校验（**D-2 / D-4 重新打开，本节只列约束与冲突，不替负责人定口径**）：

- 校验粒度：按 `order_item_id` 聚合该订单行的**全部**正常应收明细得到「该行已挂应收」，
  再减去该行已有红字，得到「该行可冲」；逐行比较本次红字金额。
- **硬约束（评审意见四）**：Finance R1 **不得无意中阻止原本合法的 `OrderReturn` approve**。
  生成器抛错会让 approve 事务整体回滚，等于财务规则反向控制了订单域状态机，违反全局不变量 5 的精神。
- 反例场景（必须被候选方案解释）：正常应收 100 → 收款 100 → 核销 100 → 之后批准退货 20。
  若「可冲 = 净额 − 已红字 − 已核销」，则可冲为 0，红字 20 会被拒，approve 被财务阻塞 —— **不可接受**。
  候选方案（不扣已核销 / 只校验 ≤ 净额 / 完全不校验 / 封顶生成 / 超额转新事实）及其对派生公式、
  核销校验、退款付款、查询导出、并发的影响，见
  [`finance-r1-final-decision-sheet.md`](finance-r1-final-decision-sheet.md) **D-2 / D-4**。
- 在 D-2 / D-4 收口前，F1-2 **不实现**红字金额校验的任一候选；生成器先按「不校验金额、只校验来源唯一」
  落地并在 IT 中以反例钉住现状风险，待裁决后补校验。

### 8.3 应付红字（第一批 Q13）

R1 只提供**手工登记入口**：`POST /scm/finance/payable/red`，
权限 `scm:finance:payable:red`（§16），必填 `original_payable_id` + `reason` + 明细（数量、单价、金额）。
本期**不**为它接任何自动业务来源（采购退货不存在，第二批 Q13 已裁不新建采购退货模块）。
校验：累计红字 ≤ 原应付可冲金额（同 D-2 口径），不足即 `FINANCE_RED_AMOUNT_EXCEEDED` 整单回滚。
红字应付明细的 `source_type` 取值 `'MANUAL'`、`source_id` 为 NULL（手工来源无外部业务行，
来源唯一索引的部分谓词将其排除）；其防重由「可冲上限 + 请求级幂等键」承担 ——
与手工出库单同形：两张手工单据就是两个事实，超额由 `FINANCE_RED_AMOUNT_EXCEEDED` 拦截。

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

## 11. 来源唯一约束

照抄既有两条索引的形态（`MIG/V19:92-94`、`MIG/V63:52-54`）：

```sql
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
CREATE UNIQUE INDEX uk_finance_write_off_single_reverse
    ON finance_write_off (reverse_of_id) WHERE deleted = FALSE AND entry_type = 'REVERSE';
```

**来源唯一索引只约束 `source_id IS NOT NULL` 的业务派生事实**（2026-09-25 评审意见二）：
手工红字应付（`source_type='MANUAL'`、`source_id=NULL`）与无来源的收付款登记不在其约束范围内，
它们的防重由请求级幂等键与金额上限校验承担。应收 / 应收明细的来源恒非空
（`SALES_ORDER` / `ORDER_RETURN` / `INVENTORY_OUTBOUND_ITEM` / `ORDER_RETURN_ITEM`），
谓词保留 `deleted = FALSE` 即可。

语义（第二批 Q26）：**幂等键防重复请求，来源唯一索引防重复事实**，两者必须同时存在；
来源唯一索引是最终数据库防线。生成器一律走 `insertOnConflictDoNothing`，
命中冲突即「已生成」，返回成功而不是报错（与 `InventoryCommandService` 的 `INVENTORY_DUPLICATE_*` 不同：
财务生成是**可重放的派生**，不是用户命令）。

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
  `FINANCE_PAYABLE_RED:…`。
- 三段式：`claim → 业务写 → complete` 同一事务；重放返回首次结果；同键异 hash 抛既有冲突码。
- **生成器（应收 / 应付 / 红字）不走幂等键**：它们不是用户命令，防重靠来源唯一索引（§11）。
- 退款付款的防重**同时**依赖：请求级 `Idempotency-Key` + `uk_finance_payment_source_active`（第二批 Q26）。

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
- 核销 / 反向核销 / 红字登记是**用户命令**，必须按上述 rank 升序 `SELECT … FOR UPDATE`
  锁住 source 与全部 target 后再校验余额并写入；两个并发核销对同一目标会串行，
  第二个看到更新后的未核销额，超额即 `FINANCE_WRITE_OFF_AMOUNT_EXCEEDED`。
- 反向核销额外锁原 `write_off` 行（rank 5），配合 `uk_finance_write_off_single_reverse` 双保险。
- 财务域**不获取**任何 `inventory_balance` 行锁（全局不变量 4）。
- 所有写命令 `@Transactional(rollbackFor = Exception.class)`；version 冲突抛 `VERSION_CONFLICT(40921)`。

并发用例必须覆盖（§22）：并发签收双生成、并发核销同目标、并发反向核销同行、
并发退款双付款、核销与红字并发（同一应收）。

## 15. 数据范围

- 解析入口唯一：`ScmDataScopeService.resolve()`；无授权 fail-closed（`scope == null` 渲染 `AND FALSE`，
  读侧短路用 `ScmDataScopeService.emptyPage`）。
- **应收**：`orderSellerScope`，谓词落在 join 出的 `sales_order.seller_id` 行上（第二批 Q23）。
- **应付**：`purchaserScope`，谓词落在 `purchase_receipt → purchase_order → purchaser_id` 的 join 行上（第二批 Q23）。
- **收款 / 付款 / 核销**：跟随其对方的可见性 —— 收款按 `customer_id` 经 `customer.seller_id` 用
  `customerSellerScope` 收窄；付款按 `counterparty`：`SUPPLIER` 侧**不收窄**（P0 裁决 7：供应商主档按采购团队共享读，
  无 supplier 维度），`CUSTOMER` 侧同收款；核销行随其 target 的可见性。
  （此条是 Q23 未逐项展开的部分，实现前需在 F1-1 评审确认，见 §26 D-5。）
- 写侧判定：核销 / 反向核销 / 红字登记在锁行后调用
  `scope.getOrderSellerScope().allows(sellerId)` / `scope.getPurchaserScope().allows(purchaserId)`，
  与 `SortingAccess` 的写侧判定同形；**不新增守卫类、不新增范围维度、不新增 `*:scope:all:query` 点**。
- 禁止 `if role == FINANCE then bypass`（P0 裁决 17、第二批 Q23）；
  `administrator_flag` 仍为 break-glass，**权限取证必须用 `administrator_flag = false` 账号**。
- 导出与列表共用同一次 `resolve()` 结果（P0 裁决 10）。

## 16. 权限矩阵

菜单号段取 **1500**（实测 1422–1499 与 1500+ 均空闲；沿用 `MIG/V55:13-14` 的四条种子约定）。

| menu_id | 类型 | 名称 / 权限串 | 授予 |
| --- | --- | --- | --- |
| 1500 | 目录 | 财务管理 | SUPER_ADMIN、SCM_FINANCE |
| 1501 | 页面 | 应收管理 `/finance/receivables` | 同上 |
| 1502 | 页面 | 应付管理 `/finance/payables` | 同上 |
| 1503 | 页面 | 收款管理 `/finance/receipts` | 同上 |
| 1504 | 页面 | 付款管理 `/finance/payments` | 同上 |
| 1505 | 页面 | 核销管理 `/finance/write-offs` | 同上 |
| 1511 | 按钮 | `scm:finance:receivable:query` | 同上 |
| 1512 | 按钮 | `scm:finance:payable:query` | 同上 |
| 1513 | 按钮 | `scm:finance:receipt:query` | 同上 |
| 1514 | 按钮 | `scm:finance:payment:query` | 同上 |
| 1515 | 按钮 | `scm:finance:write-off:query` | 同上 |
| 1521 | 按钮 | `scm:finance:receipt:add` | 同上 |
| 1522 | 按钮 | `scm:finance:payment:add` | 同上 |
| 1523 | 按钮 | `scm:finance:write-off:add` | 同上 |
| 1524 | 按钮 | `scm:finance:write-off:reverse` | 同上 |
| 1525 | 按钮 | `scm:finance:payable:red` | 同上 |
| 1531 | 按钮 | `scm:finance:export` | 同上 |

- **若 D-3 采纳候选 A**（收付款 append-only 反向），追加两个权限点：
  `1526 = scm:finance:receipt:reverse`、`1527 = scm:finance:payment:reverse`（Q20 要求破坏性动作独立权限）；
  D-3 收口前 V66 不包含这两行。
- 授权写法照 `V64:32-40` 的 `grant_matrix(menu_id, role_code)` CTE 按 `role_code` 种，不硬编码 `role_id`。
- **不新增**金额 / 成本字段级权限与 masking（第二批 Q24）：有对应 `*:query` 即可见金额；
  成本可见性仍由既有 `scm:report:cost:query` 管报表域，财务域不借用。
- 导出端点要求「对应 `*:query` **AND** `scm:finance:export`」（P0 裁决 10 同形）。
- 生成器（应收 / 应付 / 红字应收）**不挂权限点**：它们是业务事务内的派生写，
  权限由触发命令（签收 / 收货确认 / 退货批准）既有权限承担。

错误码取 **41130–41149**（顺延分拣块，不重排已发布码值）：

| 码 | 名称 | 触发 |
| --- | --- | --- |
| 41130 | FINANCE_RECEIVABLE_NOT_FOUND | 锁不到应收单 |
| 41131 | FINANCE_PAYABLE_NOT_FOUND | 锁不到应付单 |
| 41132 | FINANCE_RECEIPT_NOT_FOUND | 锁不到收款单 |
| 41133 | FINANCE_PAYMENT_NOT_FOUND | 锁不到付款单 |
| 41134 | FINANCE_WRITE_OFF_NOT_FOUND | 锁不到核销行 |
| 41135 | FINANCE_WRITE_OFF_AMOUNT_EXCEEDED | 核销额超过目标未核销额或 source 待核销余额 |
| 41136 | FINANCE_COUNTERPARTY_MISMATCH | 跨客户 / 跨供应商核销 |
| 41137 | FINANCE_RED_AMOUNT_EXCEEDED | 红字超过原单可冲金额 |
| 41138 | FINANCE_REVERSE_REASON_REQUIRED | 反向核销 / 红字缺原因（DB CHECK 之外的可读错误） |
| 41139 | FINANCE_PAYMENT_SOURCE_INVALID | 退款付款来源不合法（未 COMPLETED / 金额不符 / 对方不符） |
| 41140 | FINANCE_METHOD_INVALID | 方式不在三值枚举内 |
| 41141 | FINANCE_RECEIVED_AT_INVALID | 收付时点非法 |

## 17. 操作日志

新建 `finance_operation_log`，形态照 `order_operation_log`（`MIG/V13:114-129`）：

| 列 | 约束 |
| --- | --- |
| `business_type` | `CHECK IN ('RECEIVABLE','PAYABLE','RECEIPT','PAYMENT','WRITE_OFF')` |
| `business_id` | NOT NULL |
| `operation_type` | `CHECK IN ('GENERATE','RED_GENERATE','RECEIVE','PAY','WRITE_OFF','WRITE_OFF_REVERSE')` |
| `operator` / `reason` | NOT NULL / 可空 |
| `before_data` / `after_data` | `JSONB`，CHECK `jsonb_typeof = 'object'` 或 NULL |
| `created_at` / `created_by` | NOT NULL |

- 唯一写入口 `FinanceOperationLogRecorder`，**必须与业务写同一事务**（照 `OrderOperationLogRecorder.java:29-42`）。
- 生成类动作（`GENERATE` / `RED_GENERATE`）的 `before_data` 为 NULL、`after_data` 为单头快照；
  核销与反向核销的 `before_data` 为目标的派生余额快照、`after_data` 为写入后的派生余额快照 ——
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
POST /scm/finance/payable/red             GET  /scm/finance/log/query
```

- 查询 VO 携带**派生列**：`netAmount / writtenOffAmount / openAmount / settleState`，
  由 SQL 内对 `finance_write_off` 的聚合子查询一次算出（避免 N+1），与明细列表同一次查询返回。
- 日期轴复用 `ScmReportTimeRangeResolver`（Asia/Shanghai 半开区间、366 天上限），不新写日界转换。
- 导出：新建 `finance/support/FinanceExcel.java`，照 `ScmReportExcel` 的 `row()/cell()` 实现逐字对齐
  （FastExcel 动态表头、`OffsetDateTime` 归一化为北京时间字符串、`BigDecimal.toPlainString()`、
  文件名 `SmartResponseUtil.setDownloadFileHeader`）；行数上限照 `ScmReportExportGuard`（超限 41112 同形码）。
  **不跨域引用 `report` 包**。
- 导出与列表调用同一 Service 方法，仅换 `pageSize`（P0 裁决 10）。

## 19. 数据回填策略

- **默认不回填**：R1 自上线日起对新的签收 / 收货确认 / 退货批准生效。
  理由：P2 之后已存在的签收与收货事实若一次性生成财务事实，会把「上线前的业务」变成
  「上线日的财务事件」，`event_at` 与生成时点分离，且无法与历史对账口径对齐。
- 生成器本身**可重放**（来源唯一索引 + `insertOnConflictDoNothing`），因此将来若负责人决定回填，
  无需改表：按订单 / 收货单分批调用同一生成器即可，重复调用天然幂等。
- **是否回填、回填范围与 `event_at` 口径，属待补裁决 D-1**（§26）。在 D-1 收口前，F1-2 不实现任何回填入口。

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
| 应收 | 日期范围（`event_at`）、客户、订单号、结清状态（派生筛选，服务端算） | 单号、订单号、客户、金额、已核销、未核销、结清状态、事件时点 | 导出；抽屉：明细行（出库行来源、数量、单价、金额）+ 红字引用 + 核销行 + 日志 |
| 应付 | 日期范围（`event_at`）、供应商、采购单号、结清状态 | 单号、采购单号、供应商、金额、已核销、未核销、结清状态、事件时点 | 导出、登记红字（`scm:finance:payable:red`）；抽屉同应收 |
| 收款 | 日期范围（`received_at`）、客户、方式、是否有待核销余额 | 单号、客户、金额、方式、已用、待核销、凭据号、时点 | 登记（`scm:finance:receipt:add`）、导出；抽屉：核销行 + 日志 |
| 付款 | 日期范围（`paid_at`）、对方类型、对方、方式、来源类型 | 单号、对方、金额、方式、已用、待核销、凭据号、来源、时点 | 登记（`scm:finance:payment:add`，含「来源 = 退款」选择器）、导出；抽屉同收款 |
| 核销 | 日期范围、source 单号、target 单号、方向 | 单号、source、target、金额、方向、原因、操作人、时点 | 核销（`scm:finance:write-off:add`，多行表单）、撤销（`scm:finance:write-off:reverse`）、导出 |

- 金额输入用 `InputNumber` + 字符串定点提交（照 `ScmStrictDecimalStringDeserializer` 形态，拒绝 JSON 数字字面量）。
- 所有写按钮 `v-privilege` 字面量必须落在 `finance-const.ts` 的权限集合内（契约测试钉住，§22）。
- 派生列（已核销 / 未核销 / 结清）前端只格式化展示，不参与计算。

## 21. Flyway 规划（只规划，不生成）

| 版本 | 名称 | 内容 |
| --- | --- | --- |
| V65 | `V65__scm_finance.sql` | **8 张表** + 5 条序列（`finance_receivable_no_seq` 等，全局非重置）+ 全部 CHECK / 部分唯一索引 / 全列 COMMENT；形态照 `V60__scm_sorting_task.sql` |
| V66 | `V66__scm_finance_menus_permissions.sql` | data-only：1500–1531 菜单与权限点；形态照 `V61__scm_sorting_menus_permissions.sql`（含末尾 `setval`） |
| V67 | `V67__scm_finance_roles.sql` | data-only：`grant_matrix` CTE 按 `role_code` 授 `SCM_FINANCE` 与超管兜底；形态照 `V64:32-40` |

执行纪律：

1. 开工前 `git fetch`，确认 `origin/main` 是本地 HEAD 的祖先或相等，重扫 `db/migration/` 最大版本与
   `t_menu` 最大 `menu_id`；基线落后或分叉时不选号（P1/P2 裁决同条纪律）。
2. 新增 / 改号后运行 `python tools/migration_checksum_guard.py sync`；**禁止**为让守卫变绿覆盖既有校验和。
3. 不 ALTER 任何既有表；不编辑任何已应用迁移。
4. 单号格式沿用 `ScmDocumentNumbers.format(prefix, number)`（前缀 + `yyyyMMdd`(Asia/Shanghai) + `%06d`），
   各表只保留自己的前缀常量与序列取号（照 `SortingConstant.TASK_NO_PREFIX` 形态）。

## 22. PostgreSQL IT 计划

沿用现有 SCM IT 基类继承链与「外部干净库 + `@Transactional` 回滚 + UUID 前缀隔离」形态
（`PgITDatabase` 从 `XSY_V2_DB_URL/USERNAME/PASSWORD` 取连接；非超管取证用
`RequestEmployee` + `administratorFlag=false` + `SmartRequestUtil.setRequestUser`，
功能权限用 `MockedStatic<StpUtil>`）。

| 测试类 | 断言要点 |
| --- | --- |
| `ScmFinanceReceivablePgIT` | 签收同事务生成单头+明细且金额 = 出库量 × `locked_unit_price`；全缺订单**跳过且不产生 0 元事实**；少拣按实发；重复触发不产生第二张（来源唯一索引）；`EXCEPTION` 不生成；红字在签收前批准时不生成、签收后补生成；红字超额整单回滚 |
| `ScmFinancePayablePgIT` | 收货确认同事务生成；`DIRECT` 与 `WAREHOUSE_CONFIRM` 时点一致（都取 `confirmed_at`）；超收计入；少收关单不产生差异事实；手工红字应付引用原单、超额回滚 |
| `ScmFinanceWriteOffPgIT` | M:N 核销；跨客户 / 跨供应商拒绝（41136）；超额拒绝（41135）；派生余额 = 核销行之和（逐行核对）；反向核销唯一（第二条 41134/冲突）；退款付款来源唯一、金额必须等于 `refund_amount`、未 COMPLETED 拒绝 |
| `ScmFinanceConcurrencyPgIT` | `@Transactional(propagation = NOT_SUPPORTED)` + `CountDownLatch` 起跑线：并发签收双生成、并发核销同目标（一成一超额）、并发反向核销同行、并发退款双付款、核销与红字并发；断言「成功数 + 失败码 + 最终派生余额」三者一致 |
| `ScmFinanceRoleMatrixPgIT` | `SCM_FINANCE` 持 1511–1531 的正向取证；无财务权限账号接口层 30005/无权；`administrator_flag=false` 的销售 / 采购账号看不到财务端点；范围 fail-closed（无 `orderSellerScope` 授权返回 0 行）；导出与列表同范围 |

- 每条 SQL 分支（空筛选 / 全筛选）都要在真实 PostgreSQL 执行，照 `ScmReportPgIT` 的「逐分支渲染全部 mapper 语句」门禁。
- 并发 IT 的「定向重复执行」是**验证流程**（命令行层面重复 N 次），不是代码里的循环 —— 仓库现有并发 IT 无重复轮次实现。

## 23. E2E 验收矩阵

`xsy-scm-web/e2e/`，命名 `scm-finance-*.spec.ts`，`workers: 1 / fullyParallel: false`。

| spec | 场景 |
| --- | --- |
| `scm-finance-receivable.spec.ts` | 备货 → 订单 → 分拣（含一行少拣）→ 组单发车 → **签收** → 应收页出现单头与两行明细（少拣行金额按实发）→ 抽屉展示出库行来源 → 导出 xlsx 合法 |
| `scm-finance-receivable-red.spec.ts` | 签收后发起退货并批准 → 红字应收出现且引用原单 → 原单未核销额下降 → 再次批准超额退货被拒（页面提示 41137） |
| `scm-finance-payable.spec.ts` | 采购 → 收货（含超收）确认 → 应付页出现 → 金额 = 有效量 × 采购价 → 少收关单后应付不追加 |
| `scm-finance-write-off.spec.ts` | 登记收款（预收，无应收）→ 待核销余额可见 → 核销两笔应收（M:N）→ 派生结清状态正确 → 撤销核销 → 余额回退 → 重复撤销被拒 |
| `scm-finance-payment-refund.spec.ts` | 退货批准 → 退款完成 → 财务登记退款付款（来源选择器）→ 第二次登记同一退款被库级拒绝 → 应收未被二次冲减 |
| `scm-finance-permission.spec.ts` | 非超管财务账号全流程；销售账号深链财务页 404 + 接口被拒；无导出权限账号导出按钮隐藏且接口 30005 |

- 共享夹具断言 0 pageerror；权限取证账号一律 `administrator_flag = false`。
- E2E 账号经 `tools/e2e_accounts.py` 创建（必需 `XSY_V2_PG_DB` 等变量，不给即拒绝运行）。

## 24. 分阶段实施计划 F1-1 ~ F1-8

| 阶段 | 内容 | 交付物 | 验收 | 依赖 |
| --- | --- | --- | --- | --- |
| F1-1 | V65–V67 + 后端骨架（entity / dao / 只读业务 DAO / 常量 / 错误码 / 日志 recorder） | 迁移 + 空 Service 编译通过 | `migration_checksum_guard check` + `verify.py backend` 编译段 | 设计评审通过 |
| F1-2 | 生成器：签收 → 应收、收货确认 → 应付、退货批准 → 红字应收（含补生成） | 三个触发点接入 + `ScmFinanceReceivablePgIT` / `ScmFinancePayablePgIT` | IT 全绿；触发事务回滚时财务事实同回滚 | F1-1 |
| F1-3 | 收款 / 付款登记（含退款付款来源校验） | 写命令 + 幂等 + `finance_operation_log` | IT；权限负向 | F1-1 |
| F1-4 | 核销与反向核销、手工红字应付 | 写命令 + 锁序 + 并发 IT | `ScmFinanceWriteOffPgIT` + `ScmFinanceConcurrencyPgIT` | F1-2、F1-3 |
| F1-5 | 查询与导出（5 页后端 + Excel） | 只读端点 + 派生列 | IT 逐分支；导出 xlsx 校验 | F1-4 |
| F1-6 | 前端 5 页 + 契约测试 + 权限矩阵 IT | 页面 / api / const / 契约 mjs | `lint` / `test` / `ts-ratchet` / `build` 全绿；`ScmFinanceRoleMatrixPgIT` | F1-5 |
| F1-7 | E2E 六条 + 全量回归 + 文档收口 | `e2e/scm-finance-*.spec.ts`、`docs/progress.md` 记录 | `verify.py all`；浏览器全量 0 pageerror | F1-6 |
| F1-8 | Finance R0 接轨：往来概览页 + 只读端点 + 导出（§27） | 菜单 1217 / 权限 1218 的 data-only 迁移（届时重扫号段，不进 V66）+ report 域只读 finance 的 DAO | 页面 / 接口 / 导出三口径一致；A 类指标名与口径零变化 | F1-7 |

每阶段完成即停，不顺带实现 R2 / P5 的任何指标（对齐 P1 裁决 14 的写法）。
**D-1…D-5 未收口前的实现边界**：F1-2 的红字生成**不带金额校验**（§8.2，避免阻塞合法 approve），
并以反例 IT 钉住「未校验」的现状风险；F1-3 不提供收付款的修改 / 作废 / 反向（§6.3 候选待 D-3）；
F1-5 / F1-6 的收付款与核销读侧范围按 §15 暂定口径实现，并在 IT 与页面注释标注「待 D-5 确认」。

## 25. 风险与不变量

### 25.1 不变量（写入实现注释与 IT 断言）

1. 财务事实不可通过修改历史记录模拟纠错；冲销 / 撤销一律新增反向事实。
2. 应收、应付、收款、付款、核销必须可追溯到来源业务事实与操作人、时点。
3. 财务不写库存事实；财务不写订单状态机与 `settlement_*`。
4. `module/scm/finance` 对业务表**只读**；对 `inventory_*` 完全只读。
5. R1 不建立第二套售价 / 采购价事实源；应收单价只取 `locked_unit_price`，应付单价只取 `purchase_price`。
6. 已核销额 / 未核销额 / 结清状态 / 可冲金额一律读时派生，不落状态列与余额列。
7. 金额恒 `NUMERIC(18,4)`、`HALF_UP`、scale 4；方向编码在 `entry_type`，金额列恒 `> 0`。
8. 来源唯一索引是防重复财务事实的最终数据库防线；幂等键只防重复请求。
9. 范围 fail-closed；用户传入的 `customerId / supplierId` 只收窄不放宽；禁止角色 bypass。
10. 查询与导出共用同一次范围解析与同一 Service 方法。

### 25.2 风险

| 风险 | 现状证据 | 处置 |
| --- | --- | --- |
| 签收与应收同事务会让 `sign` 事务变长 | `sign` 现持线路锁 + 线路行锁 | 生成器只 INSERT、不锁业务表；IT 断言回滚一致性 |
| 红字可冲不足（少拣 + 全额退货）可达 | 退货上限是 `actual_quantity`（`OrderReturnService.java:135`），应收上限是实发量 | **D-4 重新打开**：fail-loud 会阻塞合法 approve，已被评审否决为默认；候选见 final sheet |
| 「可冲金额」定义未裁（是否扣已核销） | 第二批 Q27 只说「不得超过可冲金额」；100→收 100→核 100→退 20 场景下扣已核销会把可冲算成 0 | **D-2 重新打开**：候选见 final sheet；F1-2 先不校验 |
| 收款 / 付款录错无纠正路径 | 第二批 Q20 只列四类写动作；反向核销纠正不了登错的单据本身 | **D-3 重新打开**：§6.3 给出 append-only 反向候选与六面影响，待裁决 |
| 存量已签收 / 已收货事实无财务对应 | P2 已真实出库 | D-1；默认不回填，生成器可重放为将来回填留路 |
| 财务域只读业务表是新边界 | 既有跨域写都走契约（`PurchaseInventoryContract`） | §0 第 2 条 + 不变量 4 + IT 断言「finance 包内无业务表写语句」（契约测试） |
| 40921 与 `ProductErrorCode` 同码值重复声明 | `ScmCommonErrorCode.java:9-12` 自述 | 本期复用不重排；在 F1-1 记录为已知技术债，不顺手重构 |

## 26. 待补裁决（不阻塞 F1-1，各自阻塞对应阶段）

| 编号 | 问题 | 阻塞阶段 | 状态与本稿立场 |
| --- | --- | --- | --- |
| D-1 | 存量已签收 / 已收货事实是否回填财务事实 | F1-2 的回填入口（不阻塞 F1-2 主路径） | 暂定不回填；生成器保持可重放，为将来回填留路 |
| D-2 | 「可冲金额」是否扣除已核销额 | F1-2 红字生成的金额校验 | **2026-09-25 评审重新打开**：100→收 100→核 100→退 20 场景下「扣已核销」会把可冲算成 0 并阻塞合法 approve。候选与影响见 final sheet；收口前生成器不做金额校验 |
| D-3 | 收款 / 付款单录错的纠正路径 | F1-3 的纠错入口（不阻塞登记） | **重新打开**：§6.3 给出 `entry_type/reverse_of_id/reason` 的 append-only 候选与六面影响；收口前不提供修改 / 作废 / 反向 |
| D-4 | 少拣时红字超过可冲金额的处置 | F1-2 红字生成的金额校验（与 D-2 同一点） | **重新打开**：fail-loud 已被评审否决为默认（会阻塞 approve）；候选见 final sheet |
| D-5 | 收款 / 付款 / 核销三类的行级范围口径（第二批 Q23 只裁了应收 / 应付） | F1-5 / F1-6 的读侧与权限 IT | 暂定：收款按 `customerSellerScope`；付款的供应商侧不收窄、客户侧同收款；核销随 target |

候选方案、业务例子与影响分析集中在
[`finance-r1-final-decision-sheet.md`](finance-r1-final-decision-sheet.md)，本稿不替负责人决定。

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
finance_payment / finance_write_off`，以及由它们读时派生的已收 / 待收 / 已付 / 待付。

两类数字**必然不等**（A 按 `confirmed_at` + 订单结算量，B 按签收 / 收货确认时点 + 实发 / 实收量），
页面与导出必须以 tooltip / 列名说明各自口径，不得让用户以为在核对同一件事（调研稿 §10.3 第 3 条）。

### 27.2 R0 读取真实往来的设计

新增一张报表中心页面「往来概览」与对应只读端点，**数据源为 finance 表**（report 域只读 finance 表，
与 report 只读业务表同形；finance 域不为此反向依赖 report）：

```http
POST /scm/report/finance/overview        应收发生额 / 已收 / 待收 / 应付发生额 / 已付 / 待付 六指标
POST /scm/report/finance/receivable/aging-free-detail   应收明细分页（不带账龄，账龄属 R2）
POST /scm/report/finance/payable/aging-free-detail      应付明细分页
POST /scm/report/finance/overview/export 等三个导出
```

口径（与 §7 派生公式同源，页面 / 接口 / 导出共用同一 Service 方法，P0 裁决 10）：

```text
应收发生额 = Σ receivable(NORMAL).amount − Σ receivable(RED).amount        按 event_at 窗
已收       = Σ write_off(NORMAL, target=RECEIVABLE).amount − Σ write_off(REVERSE, 同 target).amount
             按 written_off_at 窗
待收       = 应收发生额 − 已收（同一窗口集合内的净额差，逐单派生后汇总，不做跨单位/跨对方轧差）
应付发生额 / 已付 / 待付 同形（source=PAYMENT、target=PAYABLE）
```

菜单与权限：页面 `menu_id = 1217`（报表中心 1200 目录下新页）、权限点 `1218 = scm:report:finance:query`；
导出沿用 `scm:report:export` AND 新查询权限。**初始只授 `SCM_FINANCE` 与超管**，
与第二批 Q24「财务数据只面向财务权限用户」一致；`SCM_FINANCE` 已持 1200–1216，追加 1217/1218 即可。

### 27.3 明确不开始

利润、毛利、账龄、客户对账、供应商对账、财务分析 —— 仍属 Finance R2（第一批 Q15、主线计划 §6）。
本节的六个指标**不含**任何账龄分桶与对账单形态。
