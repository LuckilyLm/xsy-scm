# Finance R1 需求确认与设计调研（开工前）

> 状态：**调研稿，等待负责人裁决**。本文不含实现承诺，不产生任何表结构、接口或页面变更。
> 调研基线：`main @ ce82f00`；`db/migration/` 实际最大版本 **V64**；`t_menu` 已占用最大 `menu_id` **1421**；
> SCM 业务错误码已占用最大 **41128**（41001 起）。以上均为本次实扫结果，不是引用规划稿的快照号。
> 事实源顺序：当前代码与数据库 → `docs/requirements/产品功能需求基线.md` →
> `docs/plan/pre-enhancement-mainline-development-guide.md` → `docs/progress.md` → `docs/decisions.md` →
> `docs/requirements/2026-09-19-需求覆盖与待办清单.md`。参考系统（蔬东坡）只用于页面布局、交互与业务概念理解，
> 本文凡引用参考系统之处都显式标注，不作为需求依据。

## 0. 结论摘要

1. **Finance R1 是纯绿地**。全库 64 个迁移与全部 Java/XML 中不存在应收、应付、收款、付款、核销、凭证、
   资金账户任何一张事实表或任一列（逐关键词命中结果见 §2.6）。SmartAdmin 原生 `t_oa_invoice` / `t_oa_bank`
   属 OA 示例域，与 SCM 无外键、无服务引用，不能当作财务地基。
2. **应收与应付的金额都不是库里现成的一列**。销售侧 `inventory_outbound_item` 只有数量没有价格
   （`MIG/V25:83-98` + `MIG/V63:20-26`），采购侧 `purchase_receipt_item` 只有数量与实重没有价格
   （`MIG/V15:272-300`），`inventory_movement` 只有 `unit_cost` 没有金额列。
   因此「量 × 价」这个乘积**必须新落库为财务事实**，而不能每次读时现算——这是 R1 与 R0 的分工边界。
3. **时点选择只解决一半问题**。应收还有独立的**数量口径**问题：订单结算量（`actual_quantity`）、
   分拣实发量（`sorted_quantity`）、出库实发量（`outbound_item.quantity`）三者在本业务里**天然不等**
   （少拣、整行缺货、跨仓预留都会造成差异），且 P1 裁决 1 明确分拣不回写订单。
   应付侧同理：`purchase_order_item.line_amount` 按下单量算，实收量在收货行上，两者不等。
4. **两个候选时点的差异可能只在时点、不在金额**。应付的「收货确认」与「正式入库」两个候选
   数值完全相同（同为 `received_quantity × purchase_price`），差异只出现在 `WAREHOUSE_CONFIRM` 模式
   与部分入库的场景；而「采购单下达」与它们的差异是**口径**（承诺额 vs 实收额）。
5. **退货/退款现状不足以直接接进应收**。`order_return` 无完成态、无仓库列、不产生任何库存事实
   （`RETURN_IN` 在服务端零命中），且不校验订单是否已出库/已签收；`order_refund` 与
   `settlement_total_amount` 无任何上限关系，`status=COMPLETED` 只代表业务动作完成、不代表资金付款
   （`MIG/V57:7-9` 已明文「实际退款付款属后续 Finance 权限」）。
6. **数据范围地基缺一个维度**。`ScmDataScopeContext` 现有五维全是负责人/仓库语义
   （`warehouseScope / customerSellerScope / orderSellerScope / purchaserScope / driverScope` + `costVisible`），
   没有「结算对方」维；应收按客户、应付按供应商收窄在现有地基上**无答案**，必须裁决。
7. **R0 已经建好一套命名合规的金额近似口径**，其中三个指标与 R1 的事实高度同值
   （`receiptReferenceAmount`、`inboundCostAmount`、`confirmedOrderAmount`），是双份口径风险最高的三处。
8. 本文 §11 汇总 **26 条必须负责人裁决的问题**，其中 11 条继承自
   `docs/plan/finance-reporting-r0-plan.md` §46（其中 2 条已被 P2 裁决改变前提，见 §11 备注）。
   未裁决前不进入正式设计，不生成 Flyway，不新增 Controller / Service / Mapper，不改前端。

## 1. 当前业务事实链

### 1.1 证据口径

`SRV/` = `xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/`；
`MIG/` = `xsy-scm-server/sa-admin/src/main/resources/db/migration/`；
`MAP/` = `xsy-scm-server/sa-admin/src/main/resources/mapper/scm/`；
`WEB/` = `xsy-scm-web/src/`。

以下每条都经代码或 DDL 核对。凡只在 `AGENTS.md` / `docs/` 读到、代码未验证的，标「仅文档口径」。

### 1.2 销售履约链（订单 → 分拣 → 配送 → 出库 → 流水）

```text
sales_order DRAFT → PENDING → CONFIRMED → CANCELLED
                     （无履约态；OrderStateMachine.java:13 注释「Fulfillment never enters this state graph」）
   ↓ 确认时冻结金额
sales_order_item.settlement_line_amount = actual_quantity × locked_unit_price
   （SRV/order/service/SalesOrderService.java:296；头金额 :299；公式与舍入 SRV/order/manager/OrderAmountCalculator.java:13,21-33）
   ↓ 订单确认不自动预留库存（同文件 :303 注释），预留是显式动作（菜单 620）
sorting_task / sorting_task_item
   planned_quantity_snapshot = 建单那一刻冻结的 actual_quantity（SRV/sorting/service/SortingTaskService.java:311）
   sorted_quantity + result(NORMAL|SHORT|OUT_OF_STOCK|OVER) + reason；分拣域不写任何金额（grep amount|price|cost 零命中）
   ↓ 配送资格：CONFIRMED ∧ 全部有效明细被 COMPLETED 任务覆盖（SRV/delivery/service/DeliveryEligibilityPolicy.java:35-38）
delivery_route PLANNED → DISPATCHED → COMPLETED
   dispatch（SRV/delivery/service/DeliveryRouteService.java:304-355）单事务顺序：
   幂等 claim → 锁线路(version) → 必须 PLANNED → 取 ACTIVE 订单 → 按 orderId 升序锁订单行并重查资格
   → 取 sorted_quantity（不回读 actual_quantity）→ 库存域一条原子命令 → 线路置 DISPATCHED + outbound_id
   → 全部订单 PENDING→IN_TRANSIT → 幂等 complete
   ↓ 库存域唯一写入口 SRV/inventory/service/InventoryFulfillmentService.java:117-145
   锁预留 → 按 (warehouse_id, sku_id) 升序预锁余额 → **先**归还预留 → 逐行 SALES_OUT → 出库单直生 CONFIRMED
   ↓
inventory_movement (SALES_OUT)  unit_cost = 持锁后读到的 inventory_balance.avg_cost
   （SRV/inventory/service/InventoryCommandService.java:329；append-only 由 MIG/V21:13-19 触发器强制）
   ↓ 逐单签收：只改 fulfillment_status / signed_at / signed_by / sign_reason
   （SRV/delivery/service/DeliveryRouteService.java:368-388）零库存、零金额、零订单写入；EXCEPTION 不反冲 SALES_OUT
   ↓ 完成线路：仅 DISPATCHED ∧ countUnfinished = 0（同文件 :394-402）
```

链上三个必须记住的事实：

- **一条线路 = 一张出库单**，由部分唯一索引 `uk_inventory_outbound_source_active`（`MIG/V63:52-54`）钉住；
  出库明细按 `sales_order_item` 逐行保留来源、同 SKU 不合并（`MIG/V63:20-26`）。
  这条 `delivery_route → inventory_outbound → inventory_outbound_item → sales_order_item → sorting_task_item`
  链是 R1 做订单级应收与 R2 做成本归属的唯一前提（P2 裁决 3）。
- **整条线路实发为 0 时不生成出库单**，`outboundId = null` 是成功语义
  （`InventoryFulfillmentService.java:132-134`）。
- **手工出库单是另一条路**：`SRV/inventory/service/InventoryOutboundService.confirm:114-150` 同样产生
  `SALES_OUT`，但**不写** `sales_order_item_id`、不写 `source_document_*`。库里因此已存在
  「扣了货但无法归属任何客户」的出库事实，R1 必须显式处置（§11 Q5）。

### 1.3 采购入库链（需求 → 采购单 → 收货 → 入库 → 成本）

```text
purchase_demand（一销售行 → 一需求，uk ON sales_order_item_id；无任何金额列）
   ↓ 归集工作台只按 SKU + 单位分组（MAP/purchase/PurchaseDemandDao.xml:151-243）；
     不存在按供应商/采购员的自动拆分算法，供应商由「首次分配」人工固定
     （SRV/purchase/manager/PurchaseDemandAllocator.java:94-118；单位不一致直接拒 40971）
purchase_order  DRAFT → SUBMITTED → PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED / CANCELLED
   total_amount = Σ round(planned_quantity × purchase_price, 4, HALF_UP)
   （SRV/purchase/manager/PurchaseAmountCalculator.java:16-18,53-68；
     类注释 :20-21 明写「收货不改变 total_amount，W5 没有实际金额字段」）
   ↓ 收货：一张采购单对应多张收货记录
purchase_receipt  status DRAFT|CONFIRMED；receipt_mode DIRECT|WAREHOUSE_CONFIRM；
                  putaway_status PENDING|COMPLETED + putaway_at/putaway_by（MIG/V22:17-21，CHECK :49-66）
purchase_receipt_item  **无价格列、无金额列**；
   有效数量规则（SRV/purchase/manager/PurchaseReceiptQuantityCalculator.java:145-169）：
     STANDARD 取申报量且实重三字段必须全空；NON_STANDARD 取 actual_weight（必填 > 0、来源须 MANUAL）
     → 非标品 received_quantity 与 actual_weight 是同一数字的两份存储
   超收上限 = planned × (1 + t_config 容差/100)，默认 10%，不落库（同文件 :78-82）
   ↓ 收货确认（SRV/purchase/service/PurchaseReceiptService.java:236-412）：
     幂等 claim → 锁采购单 → 锁收货单 → 锁采购行 → 锁收货行 → 累加 received_quantity
     → 写五量与实重 → 追加 receipt_weighing_record → 采购单状态推进 → 收货单 CONFIRMED → 日志 → postInbound
     DIRECT 在同一事务写 PURCHASE_IN；WAREHOUSE_CONFIRM 确认时**不写库存**，putaway 才写（:388, :432-498）
   ↓
inventory_movement (PURCHASE_IN)  quantity = 有效数量，unit_cost = purchase_order_item.purchase_price
   （装配点 PurchaseReceiptService.java:602；契约 SRV/purchase/support/PurchaseInventoryContract.java:51-52；
     MIG/V19:182-183 历史回填同一口径）
inventory_balance.avg_cost  只有入方向重算：newAvg = (旧量·旧均价 + 入量·入价)/(旧量+入量)，
   HALF_UP 到 4 位（SRV/inventory/service/InventoryCommandService.java:192-205,158-159）；
   SALES_OUT 不改均价（MAP/inventory/InventoryBalanceDao.xml:63-71 的 decrementQuantity 不动该列）
```

链上三个必须记住的事实：

- **`purchase_receipt.received_at` 与 `confirmed_at` 写同一个 `now`**
  （`PurchaseReceiptService.java:377-380`），二者不携带独立信息，不能作为「两个时点」使用。
- **采购退货不存在**：全库（64 迁移 + 全部 Java/XML）grep `purchase_return|PURCHASE_RETURN_OUT` 零命中。
  应付的减少目前**没有任何业务事实来源**（§11 Q13）。
- **`AGENTS.md` §7.5 列出的 `RETURN_IN` / `PURCHASE_RETURN_OUT` 两个流水类型在库里不存在**：
  `inventory_movement.movement_type` 白名单最终为 10 值（`MIG/V33:33-37`），不含二者。仅文档口径，未落地。

### 1.4 金额与数量在库里的真实分布

| 域 | 载体 | 语义 | 是否等于「财务金额」 |
| --- | --- | --- | --- |
| 订单头 | `sales_order.settlement_total_amount` | 确认时冻结的结算总额 | 否：按 `actual_quantity`，与实发量可不等 |
| 订单头 | `sales_order.ordered_total_amount` | 下单参考额 | 否 |
| 订单行 | `sales_order_item.settlement_line_amount` | `actual_quantity × locked_unit_price` | 否，同上 |
| 订单行 | `sales_order_item.locked_unit_price` | 确认时冻结单价 | 是**价**，但无对应「实发量」列 |
| 出库单 | `inventory_outbound_item.quantity` | 实发量（带订单行来源） | 是**量**，无对应价 |
| 出库单 | 无金额/单价列 | — | 金额缺口所在 |
| 流水 | `inventory_movement.unit_cost` | SALES_OUT = 出库时均价；PURCHASE_IN = 采购单价 | 是**成本**，不是售价/应付 |
| 配送 | `delivery_route_order.order_amount_snapshot` | 订购总金额快照 | 否：`MIG/V42:190` 注释明写「非发货结算金额」 |
| 退货 | `order_return.approved_amount` / `order_return_item.approved_amount` | 批准退货金额 | 是，但无库存事实、不冲减任何下游 |
| 退款 | `order_refund.refund_amount` | 由退货批准时行合计自动生成 | 是，但无付款事实、与结算额无上限关系 |
| 采购单 | `purchase_order_item.line_amount` / `purchase_order.total_amount` | 下单量 × 采购价 | 否：承诺额，非实收额 |
| 收货行 | 无价格/金额列 | 只有量与实重 | 金额缺口所在 |
| 客户 | `customer.credit_limit / credit_period_* / settle_day / settle_mode` | 账期与结算方式主数据 | **建好后无任何表或服务消费**（仅 `MIG/V24:46-52` 列注释与三个 Java enum） |
| 供应商 | 无结算周期/账期/付款条件/税率列 | — | 缺口所在 |

`AGENTS.md` §7.8 与 §29 提到的 `Receivable / Receipt / Payable / Payment / Customer statement /
Supplier statement` 与 `Refund + finance record / Payment + receivable update` —— **仅文档口径**，
代码与库里均无对应实现。

### 1.5 精度与舍入现状

- 全库金额与数量统一 `NUMERIC(18,4)`（约 60 处），**无 `NUMERIC(12,2)`、无 `DECIMAL`**；
  例外只有比率 `NUMERIC(8,4)`（`product_spu.loss_rate/tax_rate`，`MIG/V38:25,30`）与经纬度。
- Java 侧统一 `RoundingMode.HALF_UP` + scale 4，序列化经 `SRV/common/json/ScmFixedScale4Serializer.java:51,60`
  （null 写 null，绝不写 0；:24-32 要求同时声明 `using` 与 `nullsUsing`）。
- 金额上限 `99999999999999.9999`，越界抛错（`OrderAmountCalculator.java:14,21-22`、
  `PurchaseAmountCalculator.java:31-47`）。
- **没有独立的「金额舍入工具类」**，`common/` 下只有序列化器与字符串解析；R1 若要采用 2 位金额，
  需要新开一处统一入口并说明与既有 4 位事实的对账关系（§11 Q22）。

### 1.6 「财务表是否存在」的逐关键词检索结果

在 `MIG/` 全量 64 个迁移与 `SRV/` 全量 Java/XML 上检索：

| 关键词 | 命中 |
| --- | --- |
| `receivable` `payable` `voucher` `receipt_voucher` `write_off` `writeoff` `payment` `paid` `settlement_bill` | 0 |
| `应收` `应付` `核销` `凭证` `收款` | 0 |
| `statement` | 1，且是 PL/SQL 关键字 `FOR EACH STATEMENT`（`MIG/V21:16`） |
| `invoice` | 全部在 SmartAdmin 原生 OA（`MIG/V5:651-682` + `MIG/V3:287-295` 菜单）与 `product_spu.invoice_name`（字符串品名，`MIG/V38:27`） |
| `account` | 仅 `t_oa_bank.account_name/number`（`MIG/V5:543-544`）与图标名 |
| `finance` / `ledger` | 全部是注释（`MIG/V50:2`、`V51:2,27-29`、`V57:8,21,36`；ledger 指库存流水） |
| `付款` `结算` | 仅注释（`MIG/V57:8`「实际退款付款仍属后续 Finance 权限」、`V42:190`、`V24:46-52`） |
| `账期` `credit` | 仅 `customer` 主数据列（`MIG/V8:45-50`） |

自证文本：`MIG/V50:4`「报表中心是只读分析层，不引入任何财务事实表」；
`MIG/V38:60`「财务模块未上线，不改变订单金额口径」。

## 2. 当前数据库可复用结构

R1 不需要新建的系统性能力（可直接复用，不重做）：

| 能力 | 现有实现 | 复用要点与限制 |
| --- | --- | --- |
| 幂等 | 表 `idempotency_record`（`MIG/V13:131-146`）+ 部分唯一索引 `uk_idempotency_record_scope_key_active`（:223）；服务侧三段式 `claim → 业务写 → complete` 同一事务（`SRV/purchase/service/PurchaseIdempotencyService.java:68-122`） | Header 是字面量 `"Idempotency-Key"`，无拦截器无 AOP，必须 service 显式写；scope 已拼操作者（:76）防跨用户重放；**重放语义 = 返回首次结果**（:103-105），同键异 hash 抛 40990；`result_data` 是 JSONB（CHECK object） |
| 乐观锁 | 15 个 SCM 实体带 `@Version`；冲突码 `VERSION_CONFLICT(40921)`（`SRV/common/error/ScmCommonErrorCode.java:21`） | 两种范式并存：MP `@Version` + `updateById != 1`，或显式比对（`SalesOrderService.java:383-385`）。注意 40921 与 `ProductErrorCode` **同码值重复声明**（`ScmCommonErrorCode.java:9-12` 已自述待重构） |
| 悲观锁与锁序 | `SELECT … FOR UPDATE` 是唯一范式（main 内 34 处 XML）；**无分布式锁**（Redisson 只是 `sa-base` 依赖，未使用） | 锁序纪律有文档锚点：「单据锁先于余额锁，余额锁按 `(warehouse_id, sku_id)` 升序且永远是最后获取的锁」（`SRV/inventory/dao/InventoryBalanceDao.java:45` 等 8 处）；采购链按 id 升序（`PurchaseOrderDao.java:40`）。R1 必须显式声明自己的锁层级插入此序列 |
| 操作日志 | `order_operation_log`（`MIG/V13:114-129`，JSONB `before_data/after_data` + 类型白名单 CHECK，9 值见 `MIG/V59:20-24`）；唯一写入口 `SRV/order/manager/OrderOperationLogRecorder.java:29-42`（须同事务）；同形态还有 `purchase_operation_log`（`MIG/V15:355`）与定价两张 | P1 裁决 12 的口径：优先复用 SmartAdmin 通用 `@OperateLog` / `t_operate_log`，只有能证明通用日志满足不了追溯时才新建领域日志表。财务需要「改前/改后金额」级别的对账证据，这是「能否证明」的判据所在 |
| 数据范围 | `SRV/common/scope/ScmDataScopeService.java:68-90` 唯一解析口；`ScmDataScopeContext` 五维 + `costVisible`（:19-34）；`ScmValueScope`（all/none/of）；写侧守卫 `ScmWarehouseScopeGuard.require/requireAll/requireAny`（:33-67） | 范围谓词一律落在事实表行上，`scope == null` 渲染 `AND FALSE`；无授权即 0 行，不用 null 表达「全部」；拒绝对外表现为 30005 |
| 只读查询范式 | `PageParam` + `SmartPageUtil.convert2PageQuery/convert2PageResult` + `page.setOptimizeCountSql(false)`（代表例 `SRV/report/service/SalesReportService.java:44-51`）；日界唯一入口 `SRV/report/support/ScmReportTimeRangeResolver.java:21,27,46`（Asia/Shanghai 半开区间、366 天上限） | 报表侧显式拒绝 `sortItemList`（`SalesReportService.java:103-107`）；R1 若要排序需另立规则 |
| Excel 导出 | `SRV/report/support/ScmReportExcel.java:13,47`（`cn.idev.excel.FastExcel` 动态表头，无 `@ExcelProperty` 模型）、文件名 `SmartResponseUtil.setDownloadFileHeader`（:42）、`cell()` 统一 OffsetDateTime/BigDecimal → 字符串（:81-95）；行数上限守卫 `ScmReportExportGuard.java:19-22`（超限 41112） | 已踩过两个坑：FastExcel 无 `OffsetDateTime` Converter 导致 HTTP 200 里夹异常、BigDecimal 需 `toPlainString()`（`docs/progress.md` Finance R0 记录）。前端走 `postDownload`，不硬编码文件名 |
| 前端页面范式 | `WEB/views/business/scm/report/` 五页 + `report-components/`（kpi-card / bar / line / pie / date-range / drilldown-drawer）+ `report-model.ts` 纯函数层 + `use-report-query.ts`（含竞态令牌）+ `use-report-permission.ts`；`v-privilege` 示例 `report-inventory-list.vue:36,73,256` | 报表中心是只读页；R1 有写操作，需按既有 Modal/Drawer + 确认弹窗范式，不能照抄只读页 |
| 菜单与权限种子写法 | 目录 `menu_type=1, parent_id=0, sort=menu_id`；菜单 `menu_type=2` + `path` + `/business/scm/.../*.vue`；按钮 `menu_type=3, perms_type=1, api_perms == web_perms, context_menu_id == parent_id, sort == menu_id`；授权按 `role_code` 种不硬编码 `role_id`；末尾 `setval` 重置序列（约定见 `MIG/V55:13-14`、`MIG/V50:16-59`、`MIG/V56:10-11,120-130`） | 号段实扫：600 订单、700 采购、800–876 库存、900 大屏、1000–1032 配送（1017–1019 = L3 按钮）、1100 待办、1200–1216 报表、1301–1342 范围与金额、1400–1421 分拣。**1422 起至 1499 未见占用，1500+ 完全空闲** |
| 错误码 | SCM 统一 `SRV/common/error/ScmErrorCode.java`；41001–41128 已按域连续占用（库存 41001–41066、配送 41100–41119、报表 41110–41112、分拣 41120–41128） | `AGENTS.md` §9 明令不重排已发布码值。R1 可用 **41130–41199** 或另开 **41200+** |

**不可复用/需决策的存量**：

- `IdempotencyRecordDao` 与 `IdempotencyRecordEntity` 挂在 `SRV/order/` 包下，却被 purchase / inventory /
  delivery / sorting 反向依赖（历史遗留）。R1 复用前需决定归属，否则财务再往 order 包挂一次依赖。
- `idempotency_record` **无 TTL、无清理任务**（`SRV/` 内 grep `Scheduled|SmartJob` 与 idempotency 组合零命中；
  `MIG/V53` 的 cleanup job 只清 `sa_file` scratch）。收款/核销属高频小额，永久累积是否需要治理，未收口。
- `t_dict` / `t_dict_data` 建表于 `MIG/V1:105-146`，但 SCM 迁移里 **0 条字典种子**；
  所有枚举一律走 Java enum + DB CHECK。R1 的「收款方式」若需要可配置，是本项目第一次引入 SCM 字典，需单独裁决。

## 3. Finance R1 需求范围

来源：`docs/plan/pre-enhancement-mainline-development-guide.md` §5（第 187–240 行）、
`docs/requirements/产品功能需求基线.md`「财务与报表」节（第 62–67 行）、本次负责人指令。

本期**只做**九项：

1. 应收事实
2. 应付事实
3. 收款事实
4. 付款事实
5. 核销关系
6. 财务操作日志
7. 财务权限
8. 财务明细查询
9. 财务明细导出

需求基线里落在本期范围内的原文只有两条：「已收款、待收款和应付款报表及导出」（第 65 行）
与客户账期主数据（第 21 行，已存在但无消费方）。基线第 66 行「单品利润和客户利润」属 R2。

完成标准（主线计划 §5 原文）：应收/应付来源明确；收付款可追溯；核销关系可追溯；
退款/退货有明确财务处理方式；Finance R0 报表升级后口径仍一致；财务权限通过正向和负向验证。

## 4. 明确不做范围

**属 Finance R2**（主线计划 §6，本文一律不实现、不预建表、不预建列）：
客户利润、商品利润、订单利润、销售毛利、应收账龄、应付账龄、客户对账、供应商对账、财务分析。

**属 P5 营销与结算**（主线计划 §7）：
满减、满赠、优惠券、限时特价、在线支付、余额充值、余额支付、商城账期支付、商城货到付款、营销活动体系。

**不得扩展**（P1/P2 已裁决排除，主线计划 §11）：
GPS 与轨迹回放、自动路线优化、司机 App、自动采购、绩效（司机/采购员/分拣员）、智能推荐、
参考系统独有能力。

**本次调研额外确认「参考系统有、XSY 需求基线没有要求、因此不纳入」**：
`docs/reference/` 的蔬东坡流程图第 8 步「收货结算 → 可对账或批量对账 → 可结算或批量结算 →
结算后系统生成资金流水」，以及角色流程图中「财务做账、老板查看客户应收款/客户应付款」。
其中「结算单 / 对账 / 资金流水」属 R2 与 P5，本期不建；「客户余额」属 R2。
`finance-reporting-r0-plan.md` §27（第 1570–1606 行）已把这一批页面显式归入后续阶段。

**同样不做**（避免范围蔓延，需求未要求）：会计凭证与总账、发票管理、税率与含税计算、多币种、
坏账准备、信用额度实时占用控制（`customer.credit_limit` 现有列本期仍不消费，若要接管须追加裁决）、
资金账户余额（`t_oa_bank` 不接管）。

## 5. 应收候选形成时点分析

### 5.0 三个共同前提（无论选哪个时点都成立）

1. **金额必须新算并落库**。可用输入只有：`inventory_outbound_item.quantity`（实发量，带订单行来源）
   与 `sales_order_item.locked_unit_price`（确认时冻结单价）。库里没有「实发金额」这一列。
2. **量与价是两个独立决策**。时点决定「何时挂账」，数量口径决定「挂多少」：
   `actual_quantity`（订单结算量）≠ `sorted_quantity`（分拣实发量）≠ `outbound_item.quantity`（出库实发量）。
   P1 裁决 1 已确定分拣不回写订单，所以第一个量与后两个量在少拣/缺货时必然分叉。
3. **`SALES_OUT.unit_cost` 不是售价**，是出库时库存移动加权均价（`InventoryCommandService.java:329`）。
   它服务成本与 R2 毛利，**不能**用作应收金额的任何输入。

### 5.1 候选 A：订单确认时点（R0 现状口径）

- 载体：`sales_order.status='CONFIRMED'` + `confirmed_at`（`MIG/V13:16,25,33`），金额 `settlement_total_amount`。
- 优点：唯一写入者明确（`confirmOrder`）；金额已冻结、不随下游变动；无需读任何履约表；
  与 R0 报表口径完全一致，不产生第二套「本期销售额」。
- 缺点：与「销售订单金额不得直接等于应收」这条硬约束正面冲突——少拣、整行缺货、
  甚至从未发车的订单都会全额挂账；`CANCELLED` 只在 `CONFIRMED` 之前可达
  （`OrderStateMachine.java:21-23`），所以确认后即使一货未发也收不回这笔应收。
- 对模型影响：最小，只需读 `sales_order(_item)`；但把「接单」当「收入」，
  后续任何纠正都要靠红字事实，财务纠错成本前移。

### 5.2 候选 B：发车 / 正式出库

- 载体：`inventory_outbound.status='CONFIRMED'` + `confirmed_at` + `source_document_type='DELIVERY_ROUTE'`
  （`MIG/V25:60-76`、`MIG/V63:42-54`），行级 `inventory_outbound_item.sales_order_item_id + quantity`。
- 优点：应收量与库存扣减量**同源**，「账上有货出去 = 账上有钱该收」；出库单与流水 append-only，
  天然可做幂等重放游标；与 P2 裁决 3 那条追溯链完全对齐。
- 缺点：客户尚未确认收到货；`EXCEPTION`（含拒收）不反冲 `SALES_OUT`（P2 裁决 13），
  所以选 B 就必须同时裁决「拒收如何冲应收」——否则货在卡车上被拒收，应收照挂。
  手工出库单（无订单行来源）需要单独处置。
- 对模型影响：应收事实按 `inventory_outbound_item` 生成，来源类型 + 行 id 做防重锚点
  （沿用 `uk_inventory_movement_source_active` / `uk_inventory_outbound_source_active` 的既有范式）；
  一线路一出库单，故一批次可生成多行应收。

### 5.3 候选 C：签收（订单级 `SIGNED`）

- 载体：`delivery_route_order.fulfillment_status='SIGNED'` + `signed_at/signed_by`（`MIG/V63:65-84`）。
- 优点：商业语义最强（客户已到手）；签收是终态、单向、带时点与操作人，天然可审计；
  订单级粒度与应收粒度一致。
- 缺点：`EXCEPTION` 不是 `SIGNED`，异常单在异常处理完成前**不产生应收**，
  而库存已经扣减 → 「库存账已减、应收账未挂」的背离期需要一张显式的观察视图（属 R1 还是 R2 要裁决）；
  签收是逐单动作，会出现「同一条线路部分订单已挂账」，与 P2「整条线路原子发车」的对称性不同；
  长期未签收的线路会持续 0 应收，需要业务接受这个后果。
- 对模型影响：应收来源类型 = `DELIVERY_ROUTE_ORDER`（+ `fulfillment_status` 判定），
  金额仍需回落到 `sales_order_item` 取价，因为 `delivery_route_order` 上只有
  `order_amount_snapshot`（订购总额，`MIG/V42:176`），不是发货结算额。

### 5.4 候选 D：配送完成（线路 `COMPLETED`）

- 载体：`delivery_route.status='COMPLETED'` + `completed_at/completed_by`（`MIG/V63:97,106-109`）。
- 优点：批处理最简单——线路完成要求全部活动订单已进终态（`DeliveryRouteService.java:394-402`），
  一次挂账、无中间态；与「一张线路一张出库单」的粒度一致。
- 缺点：`COMPLETED` 要求 `SIGNED | EXCEPTION` 全部终态，所以**异常单也会被挂应收**
  （除非额外判定履约状态，那等于把 D 变成 C+D 的复合）；发车到完成之间可能跨天甚至跨人，
  挂账时点比签收更晚，与账期/信用起算的关系更弱。
- 对模型影响：应收来源类型 = `DELIVERY_ROUTE`，粒度是线路级批处理 + 订单行明细；
  需要新增「完成时点」到应收的生成器，且要能处理完成后被纠错的场景（当前无任何反完成入口）。

### 5.5 候选 E：财务确认（新建人工环节）

- 载体：**当前不存在**，需要新建财务单据与状态机。
- 优点：给财务一个纠错与批量处理的窗口，能吸收 B/C/D 各自的边界问题（拒收、异常、跨仓、手工出库）；
  与「应收需要人工核价/加费用/减折让」这类现实需求兼容。
- 缺点：多一道人工环节，需要待办队列与新的权限点；且如果这个环节叫「对账确认」，
  就与 R2 的「客户对账」撞范围（`finance-reporting-r0-plan.md` §27 把对账划入 R1 之后的规划，
  但主线计划 §6 把「客户对账」划入 R2 —— **两份规划本身不一致，需负责人定口径**，见 §11 Q4）。
- 对模型影响：最大。等于新增一层「财务单据」，B/C/D 变成「应收候选/暂挂」而非应收。

### 5.6 时点之外的独立子问题

无论选 A–E 哪一个，以下都必须单独裁决，否则实现仍会被迫替负责人选边：

- **数量口径**：订单结算量 / 分拣实发量 / 出库实发量（§5.0 第 2 条）。
- **单价来源**：`locked_unit_price` 直乘，还是按 `settlement_line_amount` 比例分摊到实发量
  （两者在 `actual_quantity ≠ 实发量` 时结果不同，因为 `settlement_line_amount` 已含人工改价）。
- **粒度**：订单级、订单行级还是出库行级。R2 的订单成本归属要求**行级可追溯**
  （P2 裁决 3 已把 `sales_order_item_id` 留在出库行上，正是为此）；
  核销又天然是「一笔应收对应多笔收款」，粒度过细会让核销界面不可用。
- **补单**：`order_source='SUPPLEMENT'` + `original_order_id`（`MIG/V13:14-15,34-37`）是向前追加金额的既有机制，
  应收必须覆盖它，否则同一笔业务会被拆成两张不相干的账。
- **线路实发为 0**：`outboundId = null` 时不生成出库单（`InventoryFulfillmentService.java:132-134`），
  应收应为 0 且不产生事实，需要明确写进验收。

## 6. 应付候选形成时点分析

### 6.0 共同前提

- 应付金额 = **实收有效量 × `purchase_order_item.purchase_price`**，库里同样没有落库的乘积列。
  现成算式只存在于报表读时聚合：`SUM(ri.received_quantity * oi.purchase_price)`（`MAP/report/ReportDao.xml:512`）
  与 `ROUND(ri.received_quantity * oi.purchase_price, 4)`（同文件 :907）。
- 「实收有效量」的定义已在代码里收口：标品取申报量、非标品取 `actual_weight`
  （`PurchaseReceiptQuantityCalculator.java:145-169`），单位统一 `purchase_unit_snapshot`。
  非标品的 `received_quantity` 与 `actual_weight` 同值，所以两者不构成额外候选。
- **全链路无含税标识、无税率列**（`MIG/V15:189-228` 的采购行 CHECK 只有 `>= 0`），
  `purchase_price` 是否含税在 DDL 注释与代码注释中均未定义（`MIG/V38:60` 只说税率字段不影响订单金额口径）。
  应付金额的语义必须先由负责人定义，不能由实现侧猜（§11 Q14）。

### 6.1 候选 A：采购单下达

- 载体：`purchase_order.status='SUBMITTED'` + `submitted_at`（`MIG/V15:151`，CHECK :169-171），金额 `total_amount`。
- 优点：时点确定、单据唯一、金额已落库。
- 缺点：**这是承诺额不是债务发生额**。多次收货、少收、超收、`SHORT_CLOSED` 都会让实收与下单不等；
  与「采购订单金额不得直接等于应付」正面冲突。
- 定位建议范围：可作为「已提交采购金额」参考列（R0 已如此使用，`ReportDao.xml:494-495`），**不作为应付来源**。

### 6.2 候选 B：收货确认

- 载体：`purchase_receipt.status='CONFIRMED'` + `confirmed_at` + `operator`（`MIG/V15:255-258`；
  写入 `PurchaseReceiptService.java:378-381`），实收量在 `purchase_receipt_item.received_quantity`。
- 优点：与「货已点到」的业务时点一致，两种 `receipt_mode` 下时点统一（`DIRECT` 与 `WAREHOUSE_CONFIRM`
  都在 confirm 时确定实收量）；金额与候选 C 完全同值。
- 缺点：**`WAREHOUSE_CONFIRM` 模式下确认时货尚未入账**，此时挂应付意味着「账上欠钱但仓里还没落地」；
  项目既有口径明确「收货确认 ≠ 库存入账」（`docs/decisions.md` B1 段、`AGENTS.md` W6-1），
  选 B 就要接受应付与库存账分叉。
- 数据陷阱：`received_at` 与 `confirmed_at` 同值（`PurchaseReceiptService.java:377-380`），
  不能拿这两个列构造「两个时点」。

### 6.3 候选 C：正式入库（`PURCHASE_IN`）

- 载体：`inventory_movement` `movement_type='PURCHASE_IN'`，`quantity` = 有效量、
  `unit_cost` = 采购单价、`occurred_at` = 物理入库时刻、`source_document_item_id` 1:1 回溯收货行
  （`MIG/V19:54-100`、`PurchaseReceiptService.java:601-602`、`InventoryCommandService.java:131-151`）。
- 优点：与库存账同源，「应付发生额 = 入库成本额」成立；append-only + 源行唯一索引
  （`uk_inventory_movement_source_active`，`MIG/V19:92-94`）是**现成的防重锚点**，
  应收/应付生成器可直接沿用这套范式；`DIRECT` 与 `WAREHOUSE_CONFIRM` 的时点差异被如实表达
  （`occurred_at` 取物理入库时刻，这是 B1 已有裁决）。
- 缺点：`WAREHOUSE_CONFIRM` 模式下若仓管迟迟不 putaway，则货已点到、应付未挂，
  与候选 B 的缺点正好对称；应付时点被仓库作业节奏影响。
- 对模型影响：应付事实按收货行 1:1 生成，来源类型 + 行 id 防重，可重放。

### 6.4 候选 D：对账确认 / 候选 E：财务确认

- 载体：**均不存在**——无表、无状态、无权限（`MIG/V50` 只有只读报表权限）。
- 优点：与供应商实际结算流程（月结、按对账单开票付款）匹配；能吸收超收/少收/价格争议。
- 缺点：选它们等于**在 R1 里新建一个前置状态机**，而「供应商对账」按主线计划 §6 属 R2；
  与 §4 的禁止范围直接冲突，需要负责人明确是否把「对账」提前。
- 与 §5.5 的对称问题：如果应付走「对账确认」而应收不走「财务确认」（或反之），
  两侧财务单据的形态会不对称，这本身可以是正确选择，但必须是**有意识的选择**而不是各自默认。

### 6.5 时点之外的独立子问题

- **粒度**：按收货行、按采购单行累计、还是按采购单。多次收货是需求确认口径 #2（已确认决策），
  所以「按采购单一次性生成」与「一张收货一条应付」是两种不同形态；R0 §46 第 5 问就是这个问题。
- **超收**：容差来自 `t_config`（`MIG/V15:37-40`，默认 10%）且不落库，超收部分是否计入应付、
  是否需要把容差固化为事实。
- **少收关单**：`SHORT_CLOSED` + `short_closed_at` + reason（`MIG/V15:165-176`）——
  承诺与实收之差是否留一个「差异关闭」事实，还是仅在采购单上可见。
- **应付的减少**：采购退货不存在，所以除红字事实外**没有任何业务动作能减少应付**（§11 Q13）。
- **供应商主档缺结算字段**：无账期、无付款条件、无结算主体（`MIG/V8` 的 `supplier` 列全集已核对）。
  若应付要支持到期日计算，输入数据本身缺失；但「到期日/账龄」属 R2，
  需要裁决的是**R1 是否要为它补主数据**（§11 Q15）。

## 7. 收款 / 付款 / 核销概念模型

以下是**概念对象与必须满足的关系**，不是表设计。表、列、索引在 §11 裁决后另行出正式设计稿。

### 7.1 五个概念对象

| 对象 | 职责 | 必须携带的语义 |
| --- | --- | --- |
| 应收事实 | 记录「应向某客户收取的一笔金额」 | 结算对方（客户）、金额、业务事件时点、来源单据类型 + 单据 id + 行 id、负责人（业务员）、生成方式（自动/人工）、版本 |
| 应付事实 | 记录「应向某供应商支付的一笔金额」 | 结算对方（供应商）、金额、业务事件时点、来源单据类型 + 单据 id + 行 id、负责人（采购员）、版本 |
| 收款事实 | 记录「实际收到一笔钱」 | 付款方、金额、收款时点、**收款方式**、资金凭据（单号/流水号）、附件、经办人、版本 |
| 付款事实 | 记录「实际付出一笔钱」 | 收款方、金额、付款时点、付款方式、资金凭据、附件、经办人、版本 |
| 核销关系 | 记录「某笔收/付款中的多少钱，抵了哪笔应收/应付」 | 指向收款/付款事实、指向应收/应付事实、本次核销金额、核销时点与操作人、是否已撤销 |

### 7.2 关系基数（决定核销模型形态）

```text
来源业务事实 : 应收/应付 = 1 : 1（防重靠「来源类型 + 来源行 id」唯一，沿用既有部分唯一索引范式）
应收 : 应付 = 无直接关系（不做应收抵应付的自动轧差）
收款 : 应收 = M : N，通过核销关系行展开
付款 : 应付 = M : N，通过核销关系行展开
```

M:N 是需求侧的硬事实（一笔款覆盖多张单、一张单分多次付），
因此**核销关系必须是独立的行级对象**，不能塞成应收上的一个 `paid_amount` 累加列——
那样会丢掉「这笔钱抵的是哪张单」的可追溯性，违反「核销是独立可追溯关系」。
（是否**同时**在应收上冗余「已核销额」，见 §11 Q17。）

### 7.3 必须守住的五条纪律

1. **收款是独立事实**：不依附于任何应收存在（是否允许，见 §11 Q16 预收）。
   把收款建成「应收的子表」会让无单收款无处可放，进而迫使实现侧伪造一张假应收。
2. **不允许直接修改历史财务事实模拟冲销**。与 Q7 追加式账本同一纪律，库里已有先例话术：
   「在途调拨不可取消，只能靠一张反向调拨单冲回」（`docs/decisions.md` 调拨段）。
   因此冲销 = 新增反向事实并指向原事实，而不是 UPDATE 原事实或改状态。
3. **金额一律定点类型**：沿用 `NUMERIC(18,4)` + `ScmFixedScale4Serializer`；
   若财务单据改用 2 位，必须同时定义与 4 位业务事实的对账规则（§11 Q22）。
4. **财务写操作幂等**：收款、付款、核销、生成器都要 `Idempotency-Key` + 同事务三段式
   （`claim → 写 → complete`），并且生成器额外要有**来源唯一索引**做兜底防重
   —— 幂等键防的是重复请求，唯一索引防的是重放与回填。
5. **查询与导出同一套范围**：沿用 P0 裁决 10（`scm:report:export` 只代表允许导出，绝不扩大查询范围）。

### 7.4 状态与派生值的取舍（待裁决，不预设）

库里对「可派生的状态」有两种既有范式：

- **派生态落库**：`inventory_balance.avg_cost`（活状态，顺序相关，必须由余额行承载 —— Q3 裁决变更）；
- **派生态不落库**：库存预警状态读时计算（`docs/decisions.md` 预警段，理由「落库会多出一个会漂移的副本」）。

应收的「未核销余额 / 结清状态」属于同一类问题：可由核销关系行聚合得出，也可落库维护。
选落库则需要「核销即改余额」的同一事务纪律与对账 IT；选读时则需要保证聚合查询在数据量下的性能。
**本文不选边**（§11 Q17）。

### 7.5 财务操作日志

P1 裁决 12 的口径是先复用 SmartAdmin 通用 `@OperateLog` / `t_operate_log`，
只有能证明通用日志满足不了追溯时才新建领域日志表。判据在这里变得可操作：
财务需要「改前金额 / 改后金额 / 经办人 / 时点」级别的可对账证据，
且核销撤销必须留下与被撤销事实的关联。`order_operation_log` 的 JSONB `before_data/after_data`
（`MIG/V13:120-121`）+ 类型白名单 CHECK + 唯一写入口（`OrderOperationLogRecorder.java:29-42`）
是仓库内**已被验证可满足该需求**的形态（Wave 8 已实现按业务对象精确下钻）。
是否照此建 `finance_operation_log`，属正式设计范围，本文不预设。

## 8. 退货 / 退款衔接现状

### 8.1 现状（三档标注）

| 事实 | 载体 | 判定 |
| --- | --- | --- |
| 退货单与明细 | `order_return` / `order_return_item`（`MIG/V13:148-188`），含 `approved_amount`、`approved_quantity`、`locked_unit_price` | **已实现** |
| 退货状态机 | `PENDING / APPROVED / REJECTED / CANCELLED`（`MIG/V13:162`），`SRV/order/service/OrderReturnService.java` create:111 / approve:163 / reject:217 / cancel:222 | **已实现**，但**无「完成/入库」态** |
| 退货金额 | `order_return.approved_amount`（approve 时由行合计生成，`OrderReturnService.java:181-190`） | **已实现**，但**不改 `settlement_total_amount`、不改订单任何金额列** |
| 退货产生库存事实 | 无：`RETURN_IN` 在服务端零命中；`OrderReturnService` 不引用任何库存 DAO | **不存在** |
| 退货要求订单已出库/已签收 | 只要求 `CONFIRMED`（`OrderReturnService.java:115`） | **不存在该校验** |
| 退款单 | `order_refund`（`MIG/V13:190-210`），`return_id NOT NULL` + `uk_order_refund_return_active`（:232） | **已实现**，与退货 1:1，**不能独立发起** |
| 退款状态 | `PENDING / COMPLETED`，完成必须带 `completed_at`（`MIG/V13:205-208`）；`OrderRefundService.complete:86-113` | **仅状态**：只翻状态 + `external_reference`，**零资金事实** |
| 退款金额上限校验 | 只有 `refund_amount > 0`（`MIG/V13:204`）与「合计 > 0」；与 `settlement_total_amount` 无任何关系 | **不存在该校验** |
| 支付方式 / 退款渠道 / 审批人列 | `order_refund` 无这三类列 | **不存在** |
| 在线支付 / 余额 / 货到付款 | 全服务端 `payment` 仅命中 `sa-base/UnexpectedErrorCode.java:22`；miniapp 零命中；迁移无支付表 | **不存在**（属 P5） |
| 订单改价/改量历史表 | 无独立历史表；留痕靠 `order_operation_log` 的整单 JSONB 快照 | **不存在**（快照即证据） |
| 财务侧红字 / 冲销概念 | 服务端零命中；只在 `docs/requirement/09-财务报表管理.md`（参考项目镜像文档）里出现 | **不存在** |

### 8.2 与既有裁决的一致性核对

- `docs/decisions.md` P0 第二批第 17 条：销售主管持退货/退款**业务审批**，
  「不代表财务付款、资金操作或查看采购成本；Finance R1 的实际退款付款另归财务权限」——
  与 `MIG/V57:7-9` 的注释一致。**结论：R1 必须新增资金操作权限点，不能复用 623/624/632。**
- P2 裁决 13：签收 `EXCEPTION`（含拒收）不反冲 `SALES_OUT`，「退货应由后续退货流程以新增反向事实处理」。
  **结论：R1 面对的既有事实是「货已扣、客户已拒收、库存不回」，这条缺口只能由退货入库或应收红字之一来补。**
- P2 裁决 19 明确「退货入库单」不在 P2 范围。

### 8.3 R1 需要衔接什么（不含规则发明）

1. 应收与退货的关系：退货批准额是否冲减应收、以什么事实形式冲减（贷项/红字/新负数应收）。
2. 退款与付款事实的关系：`order_refund.COMPLETED` 是否**就是**一笔付款事实，
   还是 R1 另建付款单、`order_refund` 退化为纯业务单。**这是双份资金事实风险最高的一处**（§11 Q19）。
3. 财务角色对退货/退款的可见性：`SCM_FINANCE` 当前不含 62x/63x（`MIG/V56:120-128`），
   即财务看不到退货与退款页面与接口。
4. 未出库即退货：当前无校验，若应收在出库/签收时点形成，会出现「冲减一笔尚未产生的应收」。
5. 异常签收（拒收）：库存已减、应收是否挂、货如何回库，三件事分散在 P2 裁决 13 与本节，需一次收口。

## 9. 权限与数据范围影响

### 9.1 功能权限

- 命名沿用 `scm:<domain>:<action>`；`@SaCheckPermission` 在方法级。
- 需要与既有分工兼容的三条硬约束：
  1. **业务审批 ≠ 资金操作**（P0 第二批第 17 条），收/付款与核销必须是新权限点，且**不得**顺手给 `SCM_SALES_LEAD`。
  2. **制单与审批分离**的先例已在报损报溢落地（「新建」与「审批」两个独立权限，且 `approver != creator`）；
     财务是否照此拆分（例如核销撤销需独立权限），属裁决项（§11 Q20）。
  3. 成本可见性已有独立权限 `scm:report:cost:query`（菜单 1215），
     金额可见性也已有独立权限先例 `scm:delivery:amount:query`（菜单 1342，司机默认隐藏金额）。
     **应付金额暴露采购单价、应收金额暴露售价**，两者是否分别受独立金额/成本权限裁剪，必须裁决（§11 Q24）。
- 号段：`menu_id` 1422–1499 与 1500+ 均空闲，建议 R1 取 **1500 段**（报表中心占 1200–1216、
  分拣占 1400–1421，留出缓冲便于将来分拣/报表补点）。沿用 `MIG/V55:13-14` 的四条种子约定
  （`menu_id == sort`、`context_menu_id == parent_id`、`api_perms == web_perms`、`perms_type = 1`）。
- 错误码：建议 **41130–41199**（顺延分拣块）或另开 **41200+**；不重排任何已发布码值。
- 迁移号：V65 起，且**开工前必须重新同步远端并重扫**（`AGENTS.md` §8 与 P1/P2 裁决 13/20 的同一条纪律）。

### 9.2 数据范围

现有五维（`ScmDataScopeContext.java:19-34`）：`warehouseScope`、`customerSellerScope`、`orderSellerScope`、
`purchaserScope`、`driverScope`，加 `costVisible`。

**核实结论：没有可用于财务范围的「结算对方」维度。** 五维全是负责人/仓库语义；
既没有按 `customer_id` 的行范围维，也没有 `supplier_id` 维（V55 亦无 supplier scope 点，
因为 P0 裁决 7 明确「不给 supplier 强加 owner 列」，供应商主档按采购团队共享读）。
因此 R1 面临三种可选路径，**必须由负责人选定**（§11 Q23）：

1. 新增第六/第七个范围维度（客户维、供应商维）——与 P1 裁决 7「不新增第六个范围维度」的精神冲突，需明确豁免；
2. 复用 `customerSellerScope`（应收按业务员收窄）+ 不给应付收窄（沿用供应商共享读的既有取向）；
3. 财务单据只受功能权限控制、不受行级范围控制——**与 P0「fail-closed、没有授权范围就查不到数据」的原则相悖，
   实现方不建议此路**，但列出以供裁决。

同时必须遵守的既有不变量（`AGENTS.md` P0 段、`docs/decisions.md` P0 裁决 2）：

- 没有「null 表示全部」；用户传入的 `customerId` / `supplierId` 只能**缩小**范围；
- 「本人」以业务 owner 字段为准，`created_by` 永远只是审计字段；
- 放宽必须是显式的 `*:scope:all:query` 授权，**不允许** `if role == FINANCE then bypass`
  （`MIG/V57:10-11` 已把这条钉成注释）；
- 页面查询与 Excel 导出共用同一次范围解析（R0 的实现是 Controller `exportRows` 直接调同一 Service 方法，
  仅换 `pageSize`，见 `SRV/report/controller/ScmReportController.java:449-457`）；
- 成本/金额无权限时**抹成 `null`（前端渲染 `—`），绝不抹成 0**；
- `administrator_flag` 仍绕过范围，所以**权限取证必须用 `administrator_flag = false` 的正式角色账号**。

### 9.3 财务附件

P0 裁决 15 已定：财务回单采用比普通业务对象更严格的权限
——「能看业务对象 **且** 有财务附件权限」，映射写死在服务端 `biz_type` 策略注册表
（`FINANCE_RECEIPT → scm:finance:attachment:query`），**不在数据库存权限串**。
该 `biz_type` 目前只有裁决、没有消费方。R1 的收/付款单若要挂回单，是它第一次落地（§11 Q25）。
约束提醒：附件权限行为只能在**对象存储模式**下取证，本地存储把 `/upload/**` 静态直出。

### 9.4 并发与事务要求

- 财务写操作必须显式声明锁层级并插入既有锁序（§2 表），
  特别是「核销」同时触碰收款事实与应收事实，两者加锁顺序必须唯一，否则与 P2 裁决 22 同类
  （两条路径各自持锁、交错到不一致状态才提交）。
- 生成器（从履约/入库事实派生应收应付）必须可重放且与来源行 1:1 唯一，
  且不得在来源事实的事务里同步跑财务写（避免把配送/采购事务变长、把锁序复杂化）——
  同事务 vs 异步补偿是正式设计（F1-0）要定的实现形态，本文不预设。
- `@Transactional(rollbackFor = Exception.class)` 是既有纪律（scm main 内 123 处，无一遗漏 `rollbackFor`）。

## 10. 与 Finance R0 的关系

### 10.1 R0 的真实交付面（含一处文档计数勘误）

`SRV/report/`：Controller 1 + Service 5 + DAO 1 + Mapper XML 1（`MAP/report/ReportDao.xml` 1258 行 / 25 个 select）。
**实测 37 个 `@PostMapping` = 26 只读查询 + 11 导出**，与 `AGENTS.md` 及 `docs/progress.md`
所称「41 个只读端点、11 个 Excel 导出」不符（差 4）。R1 开工前应订正该口径，
不能沿用被引用的数字作为验收基线。

成本脱敏发生在 **Java 运行期**而非序列化期：`SRV/report/support/ScmReportAccess.java:34,49`
判定后由各 Service 显式置 `null`（`OverviewReportService.java:49-53,64-74` 等四处），
前端 `WEB/views/business/scm/report/report-model.ts:166` 渲染 `—`、:198 整列隐藏。

**报表 SQL 完全不读配送与分拣表**（`MAP/report/ReportDao.xml` 内 `delivery|sorting` 命中 0），
所以 R0 的销售口径至今停留在「订单确认」，与履约事实无交集。

### 10.2 R0 已存在的「影子金额」与 R1 的冲突点

R0 命名合规（后端 report 包内无一处正向使用「应收/应付/收入」，只有 4 处「刻意不存在」声明），
但金额近似口径已经全部建好。冲突按风险排序：

| R0 指标（`MAP/report/ReportDao.xml` 行号） | 实质 | 与 R1 的冲突 |
| --- | --- | --- |
| `receiptReferenceAmount`（:512 聚合、:907 明细、:709/:819 维度页） | `received_quantity × purchase_price` | **与「收货确认时点生成应付」逐字同值**，双份口径风险最高 |
| `inboundCostAmount` / 供应商 TOP `amount`（:171/:254/:534/:607/:676/:726） | `SUM(m.quantity × m.unit_cost) WHERE PURCHASE_IN` | 若应付选「正式入库时点」，本数即应付发生额；且它已是 **TOP10 排序键**（:722-746，接口要求 cost 权限 :224），R1 换口径后排名会分叉 |
| `confirmedOrderAmount` / 各销售页 `settlementAmount`（:139、:295/:329/:380/:440/:471） | `SUM(settlement_total/line_amount)` WHERE `CONFIRMED + confirmed_at` | 应收原值候选，但时间轴是 `confirmed_at`；**R0 从不读 `signed_at`**，两处「本期销售额」必然不等 |
| `completedRefundAmount`（:150/:229/:367-373/:428-435） | `SUM(order_refund.refund_amount)` COMPLETED | R1 一旦把退款变成冲减，这里就与 R1 净值分叉（R0 明确「不冲减」，`ReportOverviewVO.java:32`） |
| `orderAmount` / `submittedPurchaseAmount`（:592/:642/:753、:157/:238/:495） | 采购承诺额 | 若被当作应付基准，与 §6.1 的「承诺额 ≠ 应付」冲突 |
| `avgTransactionPrice` / `avgPurchasePrice` / `weightedAvgPrice`（:293、:623、:870） | 行金额 ÷ 数量 | R1 的真实结算单价（若含费用分摊）将与之不等 |
| `inventoryBookValue` / `totalLossCostAmount`（:186/:1189、:1144） | `qty × avg_cost` / 损耗流水成本 | R1 若引入存货/损益科目口径即成镜像副本 |

### 10.3 三条关系规则（本文立场，非裁决）

1. **R1 建事实，R0/R2 读事实**。R1 不得新增与上表列别名同义的输出；
   应收/应付事实必须自带单据号 + 事件时点 + 核销链，而不是又一个聚合查询。
2. **R0 若改读 R1，必须保留现有指标名**。把 `confirmedOrderAmount` 改名成「收入」或「应收」
   会同时违反 R0 的命名边界与 `AGENTS.md` 的口径约束。
3. **不等是正常的，但必须可见**。R0 的「已确认订单金额」与 R1 的「应收发生额」在时点与数量口径上
   必然不等（一个按 `confirmed_at` + 订单结算量，一个按履约时点 + 实发量）。
   两处都要在页面上用 tooltip 写明口径，不能让用户以为在核对同一件事——
   这正是 R0 已经建立的做法（`finance-reporting-r0-plan.md` §1 第 110 行）。

## 11. 必须由负责人裁决的问题

继承 `finance-reporting-r0-plan.md` §46 的 11 问（下表标 `[R0§46-n]`），并按本次调研新增。
**其中两问的前提已被 P2 裁决改变**：§46-2「部分签收如何产生应收」——P2 裁决 12 明确不做部分签收，
问题转化为「异常签收（`EXCEPTION`）是否产生应收」；§46-10「销售订单行如何绑定真正 `SALES_OUT` 成本」——
P2 裁决 3 已把 `sales_order_item_id` 留在出库行上，链路已通，该问转为 R2 成本归属的实施问题而非 R1 前置。

### A. 应收（阻塞 §12 的 F1-1 / F1-2）

| # | 问题 | 候选 | 影响 |
| --- | --- | --- | --- |
| Q1 | 应收形成时点 | A 订单确认 / B 发车即出库 / C 签收 / D 配送完成 / E 财务确认（§5.1–5.5） | 决定生成器读哪张事实、是否需要「未签收观察视图」、是否需要人工环节与待办 |
| Q2 | 应收数量口径 | 订单结算量 / 分拣实发量 / 出库实发量 | 少拣与整行缺货时三者不等；选前者等于「未发的货也挂账」 |
| Q3 | 应收单价来源 | `locked_unit_price` 直乘 / 按 `settlement_line_amount` 比例分摊 | 后者保留人工改价痕迹，前者实现更简单且可解释 |
| Q4 | 应收单据粒度 | 订单级 / 订单行级 / 出库行级 | 行级是 R2 成本归属的前提（P2 裁决 3 已备好来源列）；过细则核销界面不可用。**同时决定「对账确认」是否属 R1**（两份规划对此不一致，见 §5.5） |
| Q5 | 手工出库单（无 `sales_order_item_id`）是否产生应收 | 不产生 / 产生但挂「其他出库」类型 / 禁止手工出库 | 库里已存在这类 `SALES_OUT`，不裁决等于留一个「扣货不入账」的洞 |
| Q6 | 异常签收（拒收）如何处理应收 | 不挂应收 / 挂后自动红冲 / 挂后等人工处置 | 库存已减不回（P2 裁决 13），三侧账必须一次说清 |
| Q7 | 补单（`SUPPLEMENT` + `original_order_id`）如何进应收 | 独立一笔 / 合并到原单 | 影响客户维度合计与核销体验 |
| Q8 | 实发为 0 的线路 | 不生成事实（当前出库侧即如此） | 需写进验收，防止生成器造 0 额应收 |

### B. 应付（阻塞 §12 的 F1-1 / F1-3）

| # | 问题 | 候选 | 影响 |
| --- | --- | --- | --- |
| Q9 `[R0§46-4]` | 应付形成时点 | A 采购单下达 / B 收货确认 / C 正式入库 / D 对账确认 / E 财务确认（§6.1–6.4） | B 与 C 金额相同、时点不同，差异只在 `WAREHOUSE_CONFIRM` 与部分入库；D/E 需新建状态机且与 R2 撞范围 |
| Q10 `[R0§46-5]` | 应付粒度 | 收货行 1:1 / 采购单行累计 / 采购单 | 多次收货是已确认口径 #2，决定是否需要「同一采购单多张应付」 |
| Q11 `[R0§46-6]` | 超收部分是否计入应付 | 计入 / 拒绝超收 / 需审批 | 容差在 `t_config` 且不落库，超收部分当前无独立事实 |
| Q12 | `SHORT_CLOSED` 少收差异 | 不留事实（只按实收挂账）/ 留「差异关闭」事实 | 影响财务能否回答「承诺与实付的差去哪了」 |
| Q13 | 应付如何减少 | 允许应付红字 / 必须先建采购退货 | **采购退货不存在**，不裁决则应付只增不减 |
| Q14 | `purchase_price` 是否含税 | 不含税 / 含税 / 需新增税字段 | 全链路无税标识；这决定应付金额的语义，不能由实现侧猜 |
| Q15 | 是否为到期日补供应商结算主数据 | 本期不补（属 R2）/ 本期补主数据但不做账龄 | 供应商无账期/付款条件列；账龄属 R2 不得提前实现 |

### C. 收款 / 付款 / 核销（阻塞 §12 的 F1-4）

| # | 问题 | 候选 | 影响 |
| --- | --- | --- | --- |
| Q16 `[R0§46-9]` | 预收 / 预付是否允许 | 允许（收款可无应收）/ 禁止 | 决定收款事实能否独立存在，以及是否需要「待核销收款」概念 |
| Q17 `[R0§46-7,8]` | 一笔收款核销多笔应收、一笔应收分多次核销 | 允许（M:N，需核销关系行）/ 只允许 1:1 | M:N 是 §7.2 的前提；同时决定「已核销额」是派生还是落库（§7.4） |
| Q18 | 核销可否撤销 | 反向核销事实 / 不可撤销只能再核销一次纠正 | 与 §7.3 第 2 条纪律一致，但需明确产品语义 |
| Q19 `[R0§46-3]` | 退款与付款事实的关系 | `order_refund.COMPLETED` 即一笔付款 / R1 另建付款单且退款退化为业务单 / 两者并存 | **双份资金事实风险最高的一处**；选错会出现「同一笔退款两条账」 |
| Q20 | 是否需要制单/审批分离 | 收付款与核销各自独立权限 / 审批环节 / `approver != creator` | 报损报溢已有先例；财务是否需要同等制衡属业务决定 |
| Q21 | 收款方式枚举范围 | 现金 / 银行转账 / 票据（本期）+ 货到付款 / 在线 / 余额（P5） | 后三者属 P5 禁止范围；枚举需可扩展但本期不实现，且 SCM 目前**无任何字典种子先例**（§2） |
| Q22 `[R0§46-11]` | 财务金额精度 | 沿用 `NUMERIC(18,4)` / 财务单据改 2 位 | 改 2 位必须同时给出与 4 位业务事实的对账规则，否则永远差尾差 |

### D. 权限、范围与工程（阻塞 §12 的 F1-6）

| # | 问题 | 候选 | 影响 |
| --- | --- | --- | --- |
| Q23 | 财务数据的行级范围维度 | 新增客户维/供应商维 / 复用 `customerSellerScope` 且应付不收窄 / 只受功能权限 | 现有五维无结算对方维（§9.2）；第三选项与 P0 fail-closed 原则相悖 |
| Q24 | 应收金额与应付金额是否分别受权限裁剪 | 各开独立金额权限 / 复用 `scm:report:cost:query` / 不裁剪 | 已有两个先例：成本权限 1215、配送金额权限 1342；应付暴露采购单价、应收暴露售价 |
| Q25 | 财务回单附件是否本期落地 | 落地 `FINANCE_RECEIPT` biz_type + `scm:finance:attachment:query` / 本期不挂附件 | P0 裁决 15 已定规则但无消费方；落地需在对象存储模式下取证 |
| Q26 | 幂等基建归属与治理 | `IdempotencyRecordDao/Entity` 从 `scm/order` 提到 `scm/common` / 保持现状；`idempotency_record` 是否需要 TTL 清理 | 现状是 5 个模块反向依赖 order 包，且**全 server 无任何清理机制**，财务高频小额会永久累积 |

## 12. 建议实施阶段拆分

前置：**§11 全部 26 问裁决完毕**，裁决结果写入 `docs/decisions.md`「P3 Finance R1 裁决」并同步
`AGENTS.md` 与 `docs/progress.md`。裁决前不进入正式设计。

| 阶段 | 内容 | 交付物 | 依赖 |
| --- | --- | --- | --- |
| F1-0 | 裁决收口 + 正式设计稿（表结构、状态机、锁序声明、权限矩阵、页面线框） | `docs/plan/finance-r1-design.md`；`docs/decisions.md` 新增裁决节 | §11 |
| F1-1 | 事实表与约束（应收 / 应付 / 收款 / 付款 / 核销关系 + 财务操作日志） | Flyway（V65 起，**先同步远端并重扫版本号与菜单号段**）：CHECK、部分唯一索引、列注释齐备；无外键 | F1-0 |
| F1-2 | 事实生成器：从履约/入库事实派生应收/应付，来源行 1:1 防重、可重放、含存量回填 | Service + DAO + PostgreSQL IT（含「重放不产生第二条」「来源缺失即失败不猜」两类反例） | F1-1，Q1/Q2/Q3/Q4/Q5/Q9/Q10 |
| F1-3 | 收款 / 付款单据与财务操作日志 | 写命令 + 幂等 + 乐观锁 + 日志；附件绑定（若 Q25 落地） | F1-1，Q16/Q19/Q21/Q25 |
| F1-4 | 核销（含撤销，若 Q18 允许） | 核销关系行写入 + 余额一致性对账 IT + 锁序用例 | F1-2, F1-3，Q17/Q18 |
| F1-5 | 财务明细查询与导出 | 只读接口 + Excel 导出（复用 `ScmReportExcel` / `ScmReportExportGuard` / `ScmReportTimeRangeResolver`） | F1-2..F1-4 |
| F1-6 | 权限、正式角色授权、数据范围与前端页面 | data-only 迁移（菜单 1500 段 + 角色授权）、`SCM_FINANCE` 扩展、前端页面与 `v-privilege` | 全部，Q23/Q24 |
| F1-7 | 验收：后端全量回归 + PostgreSQL IT + 非超管角色权限正负向 + Playwright 整链（下单→分拣→发车→签收→应收→收款→核销；采购→收货→入库→应付→付款→核销）+ 文档收口 | `docs/progress.md` 记录真实计数 | 全部 |

排期原则：F1-2 与 F1-3 可并行（不同表、不同锁），F1-4 必须串行在后。
每阶段完成即停，不顺带实现 R2 的任何指标（对齐 P1 裁决 14「完成即停止」的写法）。

## 13. 风险与不变量

### 13.1 建议纳入的不变量（待裁决确认后写入 `decisions.md`）

1. **应收/应付事实与来源业务行 1:1 唯一**，用部分唯一索引在库里强制
   （沿用 `uk_inventory_outbound_source_active`、`uk_inventory_movement_source_active` 的既有范式）。
   幂等键防重复请求，唯一索引防重放与回填——两者都要，不能只靠一个。
2. **历史财务事实不可修改**。冲销一律新增反向事实并指向原事实；
   与 `trg_inventory_movement_append_only`（`MIG/V21:13-19`）同纪律。
3. **金额一律 `BigDecimal` + `NUMERIC(18,4)` + `HALF_UP`**，null 与 0 严格区分
   （`ScmFixedScale4Serializer` 的 null 语义不得绕过）。
4. **财务不写库存、库存不写财务**。跨域只经领域命令，如同 P2 裁决 4 的「配送不得直接改库存」。
5. **财务不写订单状态机、不回写 `settlement_*`**。`OrderStateMachine.java:13` 的
   「Fulfillment never enters this state graph」应升级为「财务也不进入」。
6. **应收/应付与核销不产生第二套金额口径**：R0 的既有指标名保留原语义，
   R1 不复用其列别名、不把自己的数字伪装成「销售额/采购额」。
7. **财务范围 fail-closed**：无授权范围返回 0 行；传入的 `customerId/supplierId` 只收窄不放宽；
   放宽必须是显式 `*:scope:all:query`；不写 `if role == FINANCE then bypass`。
8. **查询与导出共用一次范围解析**，导出权限只代表允许导出。
9. **所有财务写操作 `@Transactional(rollbackFor = Exception.class)`**，并显式声明锁层级插入既有锁序。
10. **日界一律 Asia/Shanghai 半开区间**，复用 `ScmReportTimeRangeResolver`，不新写一套。

### 13.2 风险清单

| 风险 | 现状证据 | 后果 | 处置方向 |
| --- | --- | --- | --- |
| 双份资金事实 | `order_refund` 已有金额与 `completed_at`，但无付款事实 | 同一笔退款两条账，对不上且无人知哪条权威 | Q19 必须先裁 |
| 手工出库单无归属 | `InventoryOutboundService.confirm:114-150` 不写订单行来源 | 货已扣、无客户、无应收 | Q5 |
| 拒收不回库存 | P2 裁决 13 + `RETURN_IN` 不存在 | 库存减、应收挂或都不挂，两种选择都有账实背离 | Q6 + Q13 |
| 采购退货缺失 | 全库零命中 | 应付只增不减 | Q13 |
| 无税标识 | `MIG/V15:189-228` 采购行无税列 | 应付金额语义未定义，报表与财务口径无法对齐 | Q14 |
| 存量回填 | P2 已真实出库（dev 库已有 `SALES_OUT`），但无应收 | 上线时点选择直接决定是否要一次性回填历史；Q7 期初 `avg_cost` 口径本身也是「可调整项」（`docs/decisions.md` 未决段） | F1-2 生成器必须可重放 |
| 幂等记录无治理 | 全 server 无 TTL/清理 | 财务高频小额永久累积 | Q26 |
| 基建归属错位 | `IdempotencyRecordDao/Entity` 在 `scm/order` 包，5 模块反向依赖 | R1 再往 order 包挂依赖，域边界继续腐化 | Q26 |
| 40921 重复声明 | `ScmCommonErrorCode.java:9-12` 自述与 `ProductErrorCode` 同码值 | 财务复用同一码值会让排障歧义 | F1-0 顺带收口，不重排已发布码 |
| 文档计数失真 | `AGENTS.md` / `progress.md` 称 41 只读端点，实测 37（26+11） | 验收基线引用错数 | R1 开工前订正 |
| 成本口径误用 | `SALES_OUT.unit_cost` 是均价不是售价；`InventoryOutboundFact.unitCost` 的 Javadoc「出库暂无成本核算」已与 V34 现状不符（`InventoryCommandService.java:329` 实际写均价） | 把成本当收入挂应收，或按已失效注释读代码 | F1-0 修正该注释；R1 明确金额输入只用 `locked_unit_price` |
| 权限取证走样 | `administrator_flag` 绕过范围 | 超管通过被当成权限证据 | 验收必须用 `administrator_flag = false` 账号（P0 裁决 5） |

## 14. 本次调研未收口项

以下**未验证**，不作为本文结论的一部分：

1. `docs/progress.md` 中是否另有 P3 相关未决条目全文（只读了当前状态表、待办段与 Finance R0 / P2 记录）。
2. `xsy-scm-miniapp/`（冻结目录）内是否存在第二套签收/发车/结算入口——未查。
3. `t_oa_invoice` / `t_oa_bank` 能否作为 R1 票据与账户基础设施——未评估（本文按「不能」处理，
   理由是 OA 与 SCM 无服务依赖、且 SmartAdmin 底座规则要求系统能力不重造但业务事实不借用示例域）。
4. `customer` 的 `settle_mode / credit_* / settle_day` 六列是否由 R1 接管、口径是否需要变更——未验证。
5. 前端收货工作台与采购页展示金额的来源是否全部走 R0 报表接口——未逐一核对。
6. `MIG/V24` 列注释是否已为上述金额列补充业务语义——未逐条读。
7. `docs/plan/current-module-optimization-from-sdongpo-v17.4.md`（参考系统对照稿）中的财务部分——
   按其自身状态头已过期，未作为输入。
8. 一次性 IT 数据库与真实 dev 库中存量 `SALES_OUT` / `PURCHASE_IN` 的实际行数（回填规模评估需要）——未统计。
