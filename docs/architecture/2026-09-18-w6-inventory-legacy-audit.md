# W6 Inventory 库存域 · Legacy Audit（只读审计）

> 阶段：W6-0 = Inventory Legacy Audit + Target Design（**零编码、零 migration、不开始 Mini Program**）
> 基线 HEAD：`7a94a41ebbf3bb7d9eb06f1e4ea3860a9eecc0fb`（`main`，工作区 clean）
> 远端核验：`git fetch` + `git ls-remote origin refs/heads/main` = `7a94a41`，与本地 HEAD 一致（2026-09-18 执行）
> 已冻结：`V1–V18` 不可修改；Flyway 当前 max = **V18**；W0–W5 / W5.5 / F0 全部 COMPLETE；W6 NOT STARTED
> 三源：**A** = 历史资料（sprint-3 spec / plan / 需求基线 / 负责人确认口径）· **B** = V2 正式工作区（根目录）· **C** = `project-reference-examples/xsy-scm/`
> 本文件**不含**任何实现、不新增 migration、不修改业务代码。

---

## 0. 本文件的性质与先行结论

### 0.1 本文件是什么

这是 W6（库存域第一阶段）的**只读审计报告**。它回答四个问题：

1. **V2 正式工作区当前与库存有关的真实状态是什么**（B 源现状：契约、调用点、缺席断言）；
2. **`PurchaseReceiptService.confirm(...)` 的事务、锁、幂等、行身份与 W6 接入点的精确事实**（B 源深审）；
3. **C 源 reference 的 stock 域有哪些业务语义和页面可以参考、哪些并发/幂等缺口禁止照搬**（C 源资产）；
4. **历史资料（A 源）中哪些结论已被 V2 后续裁决取代，哪些仍然有效**（A 源勘误）。

本文件**不**做设计决策；所有需要在编码前由人工拍板的问题集中在 §8 先行摘要，
完整推荐值见 `2026-09-18-w6-inventory-target-design.md` §14。

### 0.2 编码前必须由人工裁决的问题（先行摘要）

| 编号 | 问题 | 本审计推荐 |
| --- | --- | --- |
| **Q1** | 入库时点：收货确认即入库（直接入库）还是另有「仓库二次入库确认」 | 直接入库（W5 契约已按此预留；口径文档仍列为待定，需负责人最终确认） |
| **Q2** | balance 是否维护 `weight` 独立列（数量/重量双记账） | 不维护，单 `quantity`（采购单位口径），分拣/出库波次再评估 |
| **Q3** | balance 是否在 W6-1 落 `avg_cost` / `total_cost` 并随入库重算 | 不落（避免死字段），movement 保留 `unit_cost` 快照 |
| **Q4** | 库存阈值预警（`warn_min` / `warn_max`）是否纳入 W6-1 | 不纳入，明确延后波次 |
| **Q5** | 历史 CONFIRMED 收货行 backfill 的执行载体 | data-only migration（`INSERT ... ON CONFLICT DO NOTHING`），时机与是否执行需负责人确认 |
| **Q6** | `PurchaseInventoryContractAbsenceIT` 的处置 | 随 W6 接线废止，替换为接线后行为 IT |
| **Q7** | movement 表是否保留 `deleted` 列（契约索引谓词要求） | 保留（与 W5 §8.5 契约 SQL 一致），服务层禁止任何 UPDATE 路径 |
| **Q8** | 菜单号段（建议 800 系）与 migration 版本号 | 菜单 800 系；版本号实施时重新 `git fetch` 后分配（当前 max V18） |

### 0.3 结论摘要（先看这 8 条）

1. **V2 正式工作区零库存资产**：无任何库存表、无任何库存实体、无任何库存写入代码。唯一与库存相关的代码是两个**未被注册为 Bean、零调用点**的契约接口（`PurchaseInventoryContract` / `OrderInventoryContract`）及其 NoOp 实现，且由 `PurchaseInventoryContractAbsenceIT` 以数据库实测断言「跑完整收货流程后全库无库存类表」。
2. **W6 的接入点已被 W5 精确预留**：`PurchaseReceiptService.confirm` 末尾有一行注释占位（`PurchaseReceiptService.java:338`），契约 Javadoc 明确「single call site … inside the same transaction, immediately after the purchase-side writes」。
3. **confirm 的既有纪律（事务/锁序/幂等/行身份）全部可直接复用**：W6 不需要改动任何既有锁序，只需要把库存余额锁**追加在锁序最末端**。
4. **C 源 reference 的业务语义可参考**（余额+流水、加权平均重算公式、before/after 快照），但其**并发防护是缺失的**：全程无 `FOR UPDATE`、余额 get-or-create 是裸 query-then-insert（唯一约束下并发首建会抛裸 DB 错误）、流水无源身份唯一索引（重试可重复入库）。**禁止照搬实现，只能照搬语义**。
5. **C 源 DDL 与 V2 精度纪律不一致**：reference 数量/重量为 `NUMERIC(18,3)`，V2 全部数量列为 `NUMERIC(18,4)`（4 位定点字符串协议）。W6 沿用 V2 的 18,4。
6. **A 源 sprint-3 spec 的「严格禁止超收」已被 W5 的「容差内允许超收」（Q3a）取代**；A 源的 `{code,message,data}` 信封与 React 技术栈描述均已被 V2 裁决取代。A 源的库存业务语义（余额维度 `warehouse+sku`、无批次、PURCHASE_IN、同事务、锁序）与 V2 一致，仍然有效。
7. **「直接入库 vs 二次入库确认」在负责人确认口径（2026-09-09）中仍列为待定**，但 W5 TD §4.3/§8 已把收货确认定为唯一入库时刻（收货单 2 状态 Q7/Q7a）。W6-1 按直接入库设计，需负责人追认（Q1）。
8. **需求基线的库存范围**（出库/盘点/报损/报溢/转换/预警/移动加权平均）远大于 W6-1；W6-1 只交付 `PURCHASE_IN` 单向写入 + 两个只读查询页，其余全部落在明确的排除清单里。

---

## 1. 审计范围与方法

### 1.1 范围

**B 源（V2 正式工作区，必审）**：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/
├─ product/            实体与唯一键（sku 身份、productType、saleUnit）
├─ warehouse/          最小仓库主数据（W5 交付）
├─ purchase/           采购域全部（重点：PurchaseReceiptService.confirm）
│   └─ support/PurchaseInventoryContract.java + NoOp 实现
└─ order/support/      OrderInventoryContract + NoOp 实现
xsy-scm-server/sa-admin/src/main/resources/db/migration/V6/V13/V15/V16/V17/V18
xsy-scm-server/sa-admin/src/test/.../PurchaseInventoryContractAbsenceIT.java
xsy-scm-web/src/{api,views}/business/scm/   （页面与接口形态参照）
```

**C 源（reference，只读参考）**：

```text
project-reference-examples/xsy-scm/xsy-scm-server/.../business/stock/**   （7 个子域全量清单）
project-reference-examples/xsy-scm/postgresql/06-库存.sql                 （DDL 事实源）
project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/erp/stock/**  （页面）
project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/stock/**  （API）
```

**A 源（历史资料，非冻结合同）**：

```text
docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md
docs/superpowers/plans/2026-09-07-purchase-receiving-inventory-design.md
docs/requirements/产品功能需求基线.md
docs/requirements/2026-09-09-负责人确认口径.md（spec 引用的裁决文件，一并核对）
```

### 1.2 方法

1. 先核验 git 基线（fetch + ls-remote + 本地 HEAD 比对）；
2. B 源逐文件读源码（契约、服务、DAO、Mapper XML、实体、错误码、Controller）；
3. B 源 DDL 逐约束读（V15 收货/仓库表 + V6 SKU 唯一键 + V16/V18 菜单结构）；
4. C 源按「实体 → 写入引擎 → DDL → 页面 → API」顺序读，标注可参考语义与禁止照搬项；
5. A 源全文读，逐条与 V2 现行裁决比对，标记「已取代 / 仍有效 / 仍待定」。

---

## 2. B 源：V2 正式工作区库存相关现状（事实清单）

### 2.1 契约资产（唯一已存在的「库存代码」）

**`PurchaseInventoryContract`**（`purchase/support/PurchaseInventoryContract.java`）：

| 成员 | 形态 | 语义 |
| --- | --- | --- |
| `SOURCE_DOCUMENT_TYPE` | `String` 常量 = `"PURCHASE_RECEIPT_ITEM"` | 库存流水源身份的文档类型；W6 流水表必须持久化该值 + `source_document_item_id = InboundFact.receiptItemId`，并建部分唯一索引 |
| `InboundFact` | record（12 字段） | 一次「已确认收货行」的入库事实：`purchaseOrderId / receiptId / receiptItemId / warehouseId / skuId / warehouseCode / warehouseName / skuCode / skuName / unit / quantity / unitCost / idempotencyKey` |
| `Availability` | record（`available`, `reserved`） | 可用量探测；**`null` 返回 = 库存能力未启用**，与「可用量为 0」严格区分（W3 `UNPRICED ≠ 0 元` 同一纪律） |
| `postInbound(InboundFact)` | 写方法 | W6 调用点（W5 零调用） |
| `queryAvailability(skuId, warehouseId)` | 读方法 | W6 调用点（W5 零调用） |

**`NoOpPurchaseInventoryContract`**：`final` 类，`postInbound` 空实现、`queryAvailability` 返回 `null`。
**关键事实：W5 既没有把它注册为 Bean，也没有注册接口的其他实现** ——
`PurchaseInventoryContractAbsenceIT.inventoryContractIsNotRegisteredAsBean()` 断言
`applicationContext.getBeanNamesForType(PurchaseInventoryContract.class)` 为空。

**`OrderInventoryContract`**（`order/support/`）：`reserve(Reserve) / release(Release) / queryAvailability` 三个方法，
`NoOpOrderInventoryContract` 同样**非 Bean、零调用点**。W4 销售域当前不消费库存。
W6-1 **不实现** reserve/release（用户锁定的排除项），但 W6 设计必须声明与该契约的边界（见 Target Design §6.7）。

### 2.2 缺席断言 IT（W6 接线后会失效的测试）

`PurchaseInventoryContractAbsenceIT`（PG IT，继承 `ScmW5PgITBase`）两条断言：

1. `PurchaseInventoryContract` 不注册为 Bean；
2. 完整收货流程（含实重、含超收）跑完后，**全库不存在任何库存类表**。

W6 实现真实 Bean 后，断言 1 必然失败 —— 该测试是 **W5 阶段边界**的守护，W6 接线时必须
废止并替换为「接线后行为测试」（Q6）。这不是回归，是阶段边界的正常交接。

### 2.3 数据库现状（与 W6 直接相关的部分）

| 表 | 关键约束 / 列 | 与 W6 的关系 |
| --- | --- | --- |
| `warehouse`（V15） | `uk_warehouse_code_active (warehouse_code) WHERE deleted=FALSE`；`status IN ('ENABLED','DISABLED')`；种子 `WH001 默认仓库`（G-03 单仓库口径） | balance 的仓库维度；**无删除/停用端点**（W5 G1 缺口已登记） |
| `purchase_order_item`（V15） | `received_quantity NUMERIC(18,4)` 只增不减；`purchase_price NUMERIC(18,4) >= 0`；`purchase_unit_snapshot VARCHAR(32)`；`uk (purchase_order_id, sku_id) WHERE deleted=FALSE` | 入库数量与成本快照的**权威来源** |
| `purchase_receipt`（V15） | `status IN ('DRAFT','CONFIRMED')`；`warehouse_id NOT NULL` + `warehouse_code_snapshot / warehouse_name_snapshot`；确认态 CHECK（`confirmed_at` 与 `operator` 同生同灭） | 源单头；快照列是 W6 事实装配的取值点 |
| `purchase_receipt_item`（V15） | `uk_purchase_receipt_item_receipt_order_item_active (purchase_receipt_id, purchase_order_item_id) WHERE deleted=FALSE`；5 个对账数量由 CHECK 恒等式强制（P24）；`actual_weight/weight_unit/weighing_source` 三列同生同灭（CHECK）；`received_quantity NUMERIC(18,4)` | **`id` 即 W6 流水的 `source_document_item_id`**（确认后不可变、软删不释放 id）；实重三字段是事实装配的取值点 |
| `product_sku`（V6） | `uk_product_sku_code_active (sku_code) WHERE deleted=FALSE`；`product_type IN ('STANDARD','NON_STANDARD')`；`sale_unit` | SKU 身份与单位语义来源 |
| `idempotency_record`（V13，W4 建） | `uk_idempotency_record_scope_key_active`；W5 复用（scope 拼操作者） | W6 幂等防重的既有底座 |
| 菜单（V16 + V18 图标） | SCM 号段：商品 401–487 · 价格 501–541 · 订单 601–641 · 采购 701–753；按钮以十位子段挂父菜单（如 731–735 采购收货） | W6 菜单号段规划输入（现最大 753） |
| Flyway | max = **V18**（V17 F0 数据迁移、V18 W5.5 菜单图标数据迁移） | W6 版本号实施时再定（AGENTS 强制先 fetch origin） |

### 2.4 B 源结论

```text
V2 与库存的关系 = 一份精确预留的契约 + 一个精确预留的调用点 + 一张干净的白纸（零表、零实体、零写入）。
W6 要做的不是「改造」，而是「兑现」：实现契约、填入调用点、建两张表、开两个只读查询页。
```

---

## 3. B 源深审：`PurchaseReceiptService.confirm(...)`

### 3.1 方法事实（源码逐段）

位置：`sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/service/PurchaseReceiptService.java`（W5 Target Design §4.3 的 15 步实现）。

**签名与事务**：

```java
@Transactional(rollbackFor = Exception.class)
public PurchaseReceiptVO confirm(PurchaseReceiptConfirmForm form, String idempotencyKey)
```

单事务、任何异常整笔回滚（含 40989 超收、40988 状态非法、VERSION_CONFLICT）。

**执行序列（与 W6 相关的完整事实）**：

```text
 1. 幂等 claim（INSERT 竞争，scope = operator + ":PURCHASE_RECEIPT_CONFIRM:" + receiptId）
    ├─ 同 key + 同哈希 → 重放首次真实结果（不重复执行副作用）
    └─ 同 key + 异哈希 → 40990；键缺失 40084 / 超长 40085
 2. probe = receiptDao.selectById(id)            （无锁读，仅为拿 purchaseOrderId）
 3. order    = purchaseOrderDao.lock(...)        ← FOR UPDATE ①采购单
 4. receipt  = purchaseReceiptDao.lock(...)      ← FOR UPDATE ②收货单
 5. version(receipt.version, form.version)       （乐观锁校验，失败 VERSION_CONFLICT）
 6. 状态校验：receipt 必须 DRAFT（40988）；order 必须 receivable（40991）
 7. orderItems  = orderItemDao.lockByOrderId()   ← FOR UPDATE ③采购行（ORDER BY id ASC）
 8. receiptItems = receiptItemDao.lockByReceiptId() ← FOR UPDATE ④收货行
 9. requestedLines：请求行集合必须 == 活动行集合（缺行/多行/重复 → 40998）
10. 容差解析：t_config `scm.purchase.over_receipt_tolerance_percent`，缺省 10，非法 → 40999
11. 逐行（receiptItems 顺序）：
    ├─ 行版本校验（input.version vs line.version）
    ├─ effective = STANDARD ? declared（实重三字段必须全空）
    │              : NON_STANDARD ? actualWeight（>0 且来源 MANUAL，40083）
    ├─ ceiling = planned × (1 + tolerance/100)；effective > ceiling − received → 40989 整笔回滚
    ├─ accumulateReceived：received_quantity += effective（唯一累计入口，持行锁）
    ├─ updateReconciliation：cumulative/remaining/over/difference + 实重三字段（P24 恒等式由 CHECK 兜底）
    └─ actualWeight != null → 追加 receipt_weighing_record（只追加审计事实，来源 MANUAL）
12. 全部活动行收齐判定 → 状态机 RECEIVED / PARTIALLY_RECEIVED → 采购单落库
13. 收货单 → CONFIRMED + receivedAt/confirmedAt/operator
14. 操作日志（RECEIPT_CONFIRM，before/after 快照）
15. 【W6 接入点】第 338 行注释：
    `// §4.3 第 15 步（W6）：PurchaseInventoryContract.postInbound(...) —— W5 零调用点`
    位于操作日志之后、idempotencyService.complete(claim, ...) 之前
16. 幂等 complete（与业务写入同一事务，result_data 存首次真实结果）
```

### 3.2 事务边界

- **一个事务包住全部副作用**：采购行累计、对账快照、称重记录、采购单状态、收货单状态、操作日志、幂等记录。
- W6 的库存写入必须进入**同一个事务**（契约 Javadoc 明文「inside the same transaction」），
  即：库存余额/流水失败 → 收货确认整体回滚（含 `received_quantity` 累计），不存在「采购侧成功、库存失败」的中间态。
- 幂等 `complete` 在库存写入**之后**，保证「重放返回的结果 = 库存已写入的成功结果」。

### 3.3 锁序（W6 必须延续并追加）

W5 冻结的锁序（`PurchaseReceiptService` 类注释 + §7.9）：

```text
采购单 → 收货单 → 采购行（id 升序）→ 收货行（id 升序）
```

全库只有本类同时持有「收货单」与「采购行」两把锁，与 `order.create`（需求 → 采购单 → 采购行）不构成环。
**W6 的追加**：库存余额锁必须排在收货行之后（锁序最末端），且多把余额锁之间必须按
确定性顺序（建议 `(warehouse_id, sku_id)` 升序）获取 —— 详见 Target Design §8。

### 3.4 幂等

| 层 | 机制 | W6 影响 |
| --- | --- | --- |
| 请求级 | `Idempotency-Key` + `idempotency_record`（INSERT 竞争 + 同事务 complete） | confirm 重放时**不会再次进入**库存写入路径（claim.replay 直接返回） |
| 数据级 | 收货行身份唯一（`uk (receipt_id, purchase_order_item_id) WHERE deleted=FALSE`） | 一个收货行一生只 confirm 一次（状态机 DRAFT→CONFIRMED 单向） |
| W6 兜底 | 流水表部分唯一索引 `uk (source_document_type, source_document_item_id) WHERE deleted=FALSE`（W5 §8.5 预留 SQL） | 即使未来出现 backfill 与实时 confirm 交叠、或手工重放，DB 层拒绝重复入库 |

### 3.5 收货行唯一身份（W6 源身份的基石）

- `purchase_receipt_item.id`：IDENTITY 主键，**一经生成不可变、不可复用**（软删只是 `deleted=true`，不释放 id）；
- 收货行在 `create` 时按采购单全部活动行自动生成（不允许挑行），确认前不产生任何副作用；
- 因此 `source_document_item_id = purchase_receipt_item.id` 满足「稳定唯一源键」的全部要求 ——
  这是 W5 TD §8.5 三条防重路径（backfill / 实时 confirm / 重试）共享的锚点。

### 3.6 W6 可直接取用的事实字段（事实装配清单）

| InboundFact 字段 | 取值来源（confirm 上下文内） |
| --- | --- |
| `purchaseOrderId` | `order.getId()`（FOR UPDATE 后的权威值） |
| `receiptId` | `receipt.getId()` |
| `receiptItemId` | 收货行循环内的 `line.getId()` |
| `warehouseId` | `order.getWarehouseId()`（= `receipt.getWarehouseId()`，收货单继承） |
| `skuId` | `line.getSkuId()`（快照列，不可变） |
| `warehouseCode / warehouseName` | `receipt.getWarehouseCodeSnapshot() / getWarehouseNameSnapshot()`（确认时刻快照） |
| `skuCode / skuName` | `line.getSkuCodeSnapshot() / getSkuNameSnapshot()`（确认时刻快照） |
| `unit` | `orderItem.getPurchaseUnitSnapshot()`（标品与非标品统一：非标品实重单位即采购单位，代码中 `weightUnit` 同源） |
| `quantity` | 循环内计算的 `effective`（STANDARD=申报数量；NON_STANDARD=实重），`NUMERIC(18,4)` 定点 |
| `unitCost` | `orderItem.getPurchasePrice()`（`NUMERIC(18,4) >= 0`，CHECK 保证） |
| `idempotencyKey` | W6 侧建议 `SOURCE_DOCUMENT_TYPE + ":" + receiptItemId`（真正的防重由 DB 唯一索引承担） |

### 3.7 W6 接入点的形态判断

- 接入点位置已在 W5 冻结：**操作日志之后、幂等 complete 之前、同一事务内**；
- 调用粒度：`postInbound(InboundFact)` 是**行级**接口 —— confirm 循环内逐行收集事实，
  在循环结束后按确定性顺序调用（或由实现方内部处理），锁序纪律见 Target Design §8；
- 接线动作本身**不需要任何 schema 变更**（W5 TD §8.1 第 4 条：「一行调用（无迁移）」），
  schema 变更全部来自 W6 自己的两张新表 + 菜单。

---

## 4. C 源：reference stock 域资产审计（只读参考）

### 4.1 模块清单（`business/stock`，共 7 个子域）

| 子域 | 核心类 | W6-1 相关性 |
| --- | --- | --- |
| StockBalance 库存余额 | `StockBalanceEntity/Dao/Service/Controller` + `StockBalanceMapper.xml` | **高**（余额表结构 + 只读查询页参考） |
| StockFlow 库存流水 | `StockFlowEntity/Dao/Service/Controller` + `StockFlowMapper.xml` | **高**（流水表结构 + 只读查询页参考） |
| StockOperate 写入引擎 | `StockOperateManager`（inbound/outbound/checkAdjust） | **高**（业务语义参考；实现禁止照搬，见 4.4） |
| StockAdjust 调整单 | 报损/报溢/盘点调整/规格转换单据流 | 排除（W6-1 无报损报溢） |
| StockCheck 盘点单 | 全盘/抽盘单据流 | 排除（W6-1 无盘点） |
| ProductConvert 规格转换 | 转换单据流 | 排除（W6-1 无转换） |
| 枚举 | `StockFlowTypeEnum`(1–8)、`StockBizTypeEnum`(1–6)、`FlowDirectionEnum`(入/出) 等 | **高**（movementType 语义对照） |

### 4.2 余额表 `t_stock_balance`（DDL 事实：`postgresql/06-库存.sql`）

```text
balance_id  IDENTITY PK
product_id / sku_id / warehouse_id(NOT NULL DEFAULT 1) / batch_id（G-03 保留未启用）
quantity    NUMERIC(18,3)   ← 与 V2 的 18,4 纪律不一致
weight      NUMERIC(18,3)   ← 数量/重量双记账（分拣场景产物）
avg_cost    NUMERIC(18,4)   ← 加权平均成本单价，每次入库实时重算
total_cost  NUMERIC(18,2)   ← 结存总成本
warn_min / warn_max          ← 阈值预警
create_user_id / create_user_name / create_time / update_time / deleted_flag
CONSTRAINT uk_sku_warehouse UNIQUE (sku_id, warehouse_id)   ← 无 deleted 谓词的普通唯一约束
```

业务语义（实体注释）：**余额由流水推导，任何模块不得直接 UPDATE 余额，必须走库存业务层**。

### 4.3 流水表 `t_stock_flow`（append-only）

```text
flow_id PK；flow_no；product_id / sku_id / warehouse_id / batch_id
flow_type  smallint：1 采购入库 2 销售出库 3 退货入库 4 报损 5 报溢 6 盘点调整 7 规格转换出 8 规格转换入
biz_type   smallint：1 采购 2 订单 3 分拣 4 盘点 5 报损报溢 6 规格转换
biz_id     bigint（关联业务单 ID）
direction  smallint：1 入 2 出
quantity / weight        NUMERIC(18,3)（正数）
unit_price NUMERIC(18,4)；amount NUMERIC(18,2)
before_quantity / after_quantity       ← 变动前后快照（可追溯性核心）
before_avg_cost / after_avg_cost       ← 成本快照
operate_by / operate_time / create_time / update_time
无 deleted、无 version —— 纯追加，「冲销使用反向流水」
索引：idx (product_id, sku_id)、idx_biz (biz_type, biz_id)、idx (operate_time)
```

**关键缺口：没有任何源身份唯一索引** —— `(biz_type, biz_id)` 只是普通索引，重复入库（重试/重放）在 DB 层不被拒绝。

### 4.4 写入引擎 `StockOperateManager`（语义可参考，实现禁止照搬）

**inbound（入库，W6 的 PURCHASE_IN 语义来源）**：

```java
加权平均重算：
  afterQty  = beforeQty + qty
  afterAvg  = (beforeQty×beforeAvg + qty×price) / afterQty    // scale 4, HALF_UP
  afterTotal = afterQty × afterAvg                            // scale 2, HALF_UP
余额：getOrCreateBalance → 字段直接 set → updateById
流水：writeFlow(IN, before/after 快照, 单价, 金额, 数量, 重量)
```

**outbound（出库）**：余额不足抛 `STOCK_NOT_ENOUGH`；成本不变（`afterAvg = beforeAvg`）。
**checkAdjust（盘点调整）**：余额直接置为目标绝对值，按差异方向生成入/出流水。

**并发与幂等缺口（W6 必须修复、禁止照搬的部分）**：

| # | 缺口 | 后果 | W6 对策（见 Target Design） |
| --- | --- | --- | --- |
| G-1 | 余额读写全程**无 `SELECT ... FOR UPDATE`** | 并发入库丢失更新（后写覆盖先写） | 余额行锁 + `version` |
| G-2 | `getOrCreateBalance` 是裸 query-then-insert，无 `ON CONFLICT` 处理 | 并发首建同一 `(sku, warehouse)` 撞 `uk_sku_warehouse`，抛裸 DB 异常 | `INSERT ... ON CONFLICT DO NOTHING` + 重读锁定 |
| G-3 | 流水无源身份唯一索引 | 重试/重放重复入库 | `uk (source_document_type, source_document_item_id) WHERE deleted=FALSE` |
| G-4 | `wh()` 缺省硬编码 `warehouseId = 1L` | 仓库语义被隐式折叠 | 仓库 ID 必填（采购单绑定，收货单继承） |
| G-5 | `LocalDateTime` 无时区 | 跨环境语义漂移 | V2 统一 `TIMESTAMPTZ` / `OffsetDateTime` |

### 4.5 reference 前端（Copy First + Adapt 的复制源）

| 文件 | 行数 | 形态 | W6 适配点 |
| --- | --- | --- | --- |
| `views/business/erp/stock/stock-balance-list.vue` | 153 | 只读表格：查询（商品ID/规格ID 裸数字输入）→ 表格（余额ID/商品ID/规格ID/仓库ID/批次ID/数量/重量/加权平均成本/总成本/预警下限/上限/更新时间）→ 分页；`v-privilege="'stock:balance:query'"`；TableOperator 列设置 | ① 裸 ID 输入改为 V2 选择器/编码关键字；② 列展示 SKU 编码/名称（后端 VO 联表），不再暴露裸 ID 为主列；③ 批次列移除（G-03 永不启用）；④ 预警列按 Q4 裁决移除；⑤ 权限串改 `scm:inventory:*` |
| `views/business/erp/stock/stock-flow-list.vue` | 181 | 只读表格：查询（商品ID/规格ID/流水类型/关联业务/方向 SmartEnum）→ 表格（流水号/类型/业务/方向/数量/重量/单价/金额/前后快照/操作时间） | ① SmartEnum 换 V2 `constants` 枚举模式（W4/W5 范式）；② 流水类型先只含 `PURCHASE_IN`；③ 来源单据列改为采购收货单链接 |
| `api/business/stock/stock-balance-api.ts` / `stock-flow-api.ts` | 10/10 | `postRequest('/stock/balance/query')` 单端点 | 换 `/scm/inventory/**` 路径 + TypeScript 类型定义（V2 范式） |

**页面结论**：reference 两个页面都很薄（合计 334 行），复制价值低、**范式价值高**
（smart-query-form / TableOperator / 分页 / v-privilege 的骨架与 V2 完全同源）。
W6 按 Copy First + Adapt 复制骨架后重点做查询条件与列的适配。

---

## 5. A 源：历史资料逐条勘误与沿用

### 5.1 sprint-3 spec（2026-09-04）

| 条目 | spec 内容 | V2 现行裁决 | 状态 |
| --- | --- | --- | --- |
| 收货→库存事务 | 确认事务内更新采购进度 + 库存余额 + 流水，任一失败全部回滚 | W5 TD §8 / 契约 Javadoc 同口径 | **沿用** |
| 锁序 | 采购单 → 采购单行 → 库存余额 | W5 已细化为 4 段锁序；W6 追加余额段于末尾 | **沿用（细化）** |
| 库存粒度 | `warehouseId + skuId`，不启用批次 | G-03 永久不启用批次 | **沿用** |
| movement 只追加 | 每次库存变化必须产生不可变 `InventoryMovement` | AGENTS §7.5 同口径 | **沿用** |
| 负库存 | 不提供负库存写命令 | W6-1 无出库，问题不存在；balance CHECK `quantity >= 0` 建议现在冻结 | **沿用** |
| 首建并发 | 唯一索引 + 行锁 + ON CONFLICT 或等价策略 | C 源未做到；W6 必须做到 | **沿用（C 源反面教材）** |
| 超收 | 「严格禁止超收」 | W5 Q3a：容差内允许（默认 10%，t_config 可配） | **已被取代** |
| 响应信封 | `{code,message,data}` + `{records,page,pageSize,total}` | SmartAdmin `ResponseDTO` + `PageResult`（AGENTS §9） | **已被取代** |
| 技术栈 | React Admin + ProTable | Vue3 + TypeScript + Ant Design Vue | **已被取代** |
| 幂等作用域 | 6 个 scope 清单 | W5 实际 `PURCHASE_RECEIPT_CREATE/CONFIRM` 等，scope 拼操作者 | **沿用（形态升级）** |

### 5.2 sprint-3 plan（2026-09-07）

沿用部分（与 V2 一致的精确口径）：

- confirm 23 步序列中的库存步骤（17 锁定或创建余额 → 18 记录变动前数量 → 19 增加余额 → 20 追加 PURCHASE_IN 流水）；
- 每条 PURCHASE_IN 恒等式 `afterQuantity = beforeQuantity + movementQuantity`；
- 「同一确认批次不得产生两条相同来源的入库流水」→ W5 §8.5 演进为源身份部分唯一索引；
- 首建并发四件套：活动唯一索引 + 行锁 + `INSERT ... ON CONFLICT` + 明确冲突返回；
- 迁移纪律：不改历史迁移、不建外键、序列发号不用 `MAX+1`。

被取代部分：

- plan 的「收货确认批次表（confirmation）」结构 —— W5 最终以「一单多收货单 + 收货行级 confirm」实现，
  不存在独立确认批次表；plan 建议的源唯一性 `(source_type, source_id, source_item_id, confirmation_id)`
  相应简化为 W5 §8.5 冻结的 `(source_document_type, source_document_item_id)`；
- `{records,page,...}` 分页与 React 前端设计。

### 5.3 需求基线（2026-09-11）· 库存管理条目

```text
实际重量入库、出库、盘点、报损、报溢和多规格转换        ← 入库 = W6-1 唯一交付；其余全部排除
库存与商城商品、订单联动，支持库存阈值预警              ← 商城联动 = W6/商城波次；预警 = Q4 待裁
采购成本移动加权平均核算                                ← Q3 待裁（W6-1 建议只留 unit_cost 快照）
```

### 5.4 负责人确认口径（2026-09-09）

- 「收货入库：支持两种操作模式（直接入库 / 二次入库确认）……」—— **仍待定项**。
  W5 TD §4.3（Q7/Q7a 收货单 2 状态）与 §8 契约事实上选择了「confirm 即入库时刻」。
  W6-1 按直接入库设计，**需负责人追认**（Q1）；若最终确认二次入库模式，W6 需要重开设计。
- 「计算库存」选项（采购需求汇总时的实时库存抵扣）：**W6-1 不做**（W5 TD §8.4 已定为 W6+ 专项）。

---

## 6. 差距清单（B 源白纸 × C 源语义 × A 源合同）

| # | 能力 | B 源现状 | W6-1 交付 |
| --- | --- | --- | --- |
| 1 | 库存余额存储 | 无 | `inventory_balance` 表 + 实体 + DAO |
| 2 | 库存流水存储 | 无 | `inventory_movement` 表（append-only）+ 实体 + DAO |
| 3 | 收货→入库写入 | 契约 + 注释占位，零调用 | 契约实现 Bean + confirm 接线（同事务） |
| 4 | 重复入库防护 | 无 | 源身份部分唯一索引（W5 §8.5 预留 SQL 落地） |
| 5 | 历史数据回放 | 无 | backfill（载体待裁 Q5，取数口径 W5 §8.5 已冻结） |
| 6 | 余额查询 | 无 | 只读分页 API + Vue 页 |
| 7 | 流水查询 | 无 | 只读分页 API + Vue 页 |
| 8 | 权限/菜单 | 无 | `scm:inventory:*` 权限 + 800 系菜单（版本号实施时定） |
| 9 | 幂等/并发测试 | 无 | PG IT：并发首建、并发收货、重放、回滚原子性 |
| 10 | 出库/占用/盘点/报损/转换/预警/成本核算 | 无（OrderInventoryContract 零调用） | **全部排除**，契约边界保持 |

---

## 7. 风险与注意事项（实施前必须知道的事实）

1. **`PurchaseInventoryContractAbsenceIT` 会随接线失效** —— 这是设计使然，不是回归；W6 必须同步替换该测试（Q6），否则 W6 的 CI 永远红。
2. **confirm 的 `effective` 是唯一权威入库数量**：标品 = 申报数量、非标品 = 实重；`planned_quantity` 永不覆盖、实重三字段同生同灭。W6 不得重新发明数量口径，只能消费循环内已算出的 `effective`。
3. **`weightUnit` 仅在实重存在时非空**（`PurchaseReceiptService.java:286`）：`String weightUnit = actualWeight == null ? null : orderItem.getPurchaseUnitSnapshot()`——库存侧的 `unit` 应统一取 `purchase_unit_snapshot`，不要误用 `weightUnit`（标品会拿到 null）。
4. **收货单与采购单的仓库快照列是事实源**：`warehouse_id` 三处引用（需求/采购单/收货单）由服务层守卫（`PurchaseWarehouseReferenceGuard`），无 DB 外键；W6 余额表引用仓库同样走服务层校验 + 快照留痕，禁止引入外键（AGENTS §8）。
5. **G-03：批次/保质期永久不启用**（W5 TD §3.4）——余额表不得预留 `batch_id` 死列。
6. **W5 仓库域没有删除/停用端点**（G1 缺口已登记）——W6-1 同样不新增仓库管理能力，余额表对仓库的引用校验复用 `WarehouseService.require`。
7. **错误码纪律**：采购域错误码已冻结（40080–40999 区间内的 40 个码）；W6 错误码使用新的统一 SCM 区间，禁止复用/改号既有码（AGENTS §9）。
8. **版本号纪律**：本审计时点 Flyway max = V18，但 Target Design 中的版本号只是**占位**；实施选号前必须重新 `git fetch` + `git ls-remote` 核验（AGENTS V17/V18 勘误条款）。

---

## 8. 待人工裁决问题（汇总）

见 §0.2 先行摘要表（Q1–Q8）。完整论证、推荐值与影响分析见
[`2026-09-18-w6-inventory-target-design.md`](./2026-09-18-w6-inventory-target-design.md) §14。

---

## 9. 审计覆盖声明

- 本审计基于 HEAD `7a94a41` 的真实工作区与 reference 快照，全部结论可溯源到具体文件与行号；
- B 源深审覆盖 `PurchaseReceiptService`（512 行全文）、两个契约 + NoOp、缺席 IT、相关 DAO/Mapper XML、
  V6/V13/V15/V16/V17/V18 迁移 DDL/DML、`PurchaseReceiptQuantityCalculator`、`PurchaseIdempotencyService`、
  `PurchaseWarehouseReferenceGuard`、`PurchaseReceiptController`；
- C 源覆盖 stock 全部 7 个子域的清单与关键文件（实体/引擎/DDL/两个页面/两个 API），
  StockAdjust/StockCheck/ProductConvert 仅做结构级审阅（W6-1 排除项，不深审）；
- A 源三份资料全文读完，另核对 spec 引用的 `2026-09-09-负责人确认口径.md`；
- 未运行任何测试、未启动任何服务、未修改任何文件（只读审计）。
