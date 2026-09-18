# W6 Inventory 库存域（第一阶段） · Approval（人类裁决记录）

> 阶段：W6-0 = Audit + Target Design → **裁决完成，允许进入 W6-1 实施**
> 裁决日期：**2026-09-18**
> 裁决人：负责人（人类）
> 裁决范围：Q1–Q13（含 Q5 顺序修正、Q7 硬化、Q11 修订、Q12 条件化、Q13 新增单位不变量、InboundFact 契约演进）
> 对应设计合同：[`2026-09-18-w6-inventory-target-design.md`](./2026-09-18-w6-inventory-target-design.md)
> 输入审计：[`2026-09-18-w6-inventory-legacy-audit.md`](./2026-09-18-w6-inventory-legacy-audit.md)
> 基线：HEAD `7a94a41`（`main` = `origin/main`）；Flyway max = V18 → 本次取 V19 / V20

---

## 0. 裁决总览

| # | 裁决 | 一句话结论 | 是否改变设计 |
| --- | --- | --- | --- |
| Q1 | APPROVED | 确认收货即直接入库，不做二次入库确认 | 追认既有设计 |
| Q2 | APPROVED | 余额不保留独立 `weight` 列 | 无 |
| Q3 | APPROVED | 不落 `avg_cost` / `total_cost`；只留 movement 成本快照 | 无 |
| Q4 | APPROVED | 不纳入 `warn_min` / `warn_max` | 无 |
| Q5 | **APPROVED WITH CORRECTION** | backfill 用 data-only migration，且**回放顺序必须修正** | **是** |
| Q6 | APPROVED | AbsenceIT 废止，由 W6 行为 IT 替代 | 无 |
| Q7 | **APPROVED WITH HARDENING** | 保留 `deleted` 列，但新增 `CHECK (deleted = FALSE)` 把 append-only 变成 DB 约束 | **是（增强）** |
| Q8 | APPROVED | 菜单 800/801/802/811/821；实施前重新核验 migration 版本 | 无（含前置动作） |
| Q9 | APPROVED | 不加 `movement_no`，人类可读来源用 `receipt_no` | 无 |
| Q10 | APPROVED | `quantity >= 0` 本期冻结 | 无 |
| Q11 | **REVISED** | 删除「依赖 PG15+」错误前提；统一用与部分唯一索引完全匹配的 `ON CONFLICT (...) WHERE ... DO NOTHING` | **是** |
| Q12 | **APPROVED WITH CONDITION** | 仅当启用仓库恰好 1 个时才默认带出；否则只给普通筛选 | **是（条件化）** |
| Q13 | **新增** | Inventory Unit Invariant：余额锁一个记账单位，异单位必须显式失败 | **是（新增设计项）** |
| Q13-附 | 批准 | `InboundFact` 正式扩展 `occurredAt` / `operator` | **是（契约演进）** |

> **裁决的净效果**：Q1–Q4 / Q6 / Q9 / Q10 是对既有设计的追认（设计不变）；
> Q5 / Q7 / Q11 / Q12 / Q13 / Q13-附 **实质改变了设计**，Target Design 已按裁决逐条修订。

---

## 1. 逐条裁决原文与实施约束

### Q1 — 入库时点：APPROVED

**裁决**：确认收货即直接入库。**不实现**仓库二次入库确认。

**实施约束**：

- `PurchaseReceiptService.confirm` 是唯一入库时刻（收货单 2 状态 DRAFT→CONFIRMED 单向）；
- **不新增**入库确认状态机、不新增入库确认权限点、不新增入库单表；
- 负责人确认口径（2026-09-09）中「二次入库确认」一项就此关闭，不再是待定项。

---

### Q2 — 余额是否保留 `weight` 列：APPROVED

**裁决**：`inventory_balance` **不保留**独立 `weight` 列。

**实施约束**：数量语义为**采购单位口径**（标品 = 件数，非标品 = 实重），单 `quantity NUMERIC(18,4)` 已完整表达；
数量/重量双记账属分拣波次，届时按需演进，**本期不留死列**。

---

### Q3 — 成本列与加权平均：APPROVED

**裁决**：

```text
W6-1 不落 avg_cost / total_cost。
inventory_movement.unit_cost 继续保存采购成本事实快照。
移动加权平均延期到销售出库 / 财务波次。
```

**实施约束**：

- `inventory_balance` **无**成本列；
- `inventory_movement.unit_cost` = confirm 时刻的 `purchase_order_item.purchase_price`（事实快照，可空列但实时路径恒非空）；
- **不实现**任何加权平均重算、不产出成本报表；
- reference 的重算公式（Legacy Audit §4.4）仅作为未来波次的实现参考，**本期不写进代码**。

---

### Q4 — 库存阈值预警：APPROVED

**裁决**：`warn_min` / `warn_max` **不纳入** W6-1。

**实施约束**：余额表无预警列；不发明阈值配置与提醒规则；预警能力随未来波次整体设计。

---

### Q5 — Backfill 载体、时机与顺序：APPROVED WITH CORRECTION

**裁决（原文要点）**：

```text
历史 CONFIRMED 收货使用 data-only migration backfill。
与首次库存 schema migration 同波次执行，
并且必须在新 W6 应用开始接受业务请求前完成。
```

**修正（这是本次裁决的关键点）**：

```text
禁止仅 ORDER BY purchase_receipt_item.id。

统一使用：
  receipt.confirmed_at ASC,
  receipt.id ASC,
  receipt_item.id ASC

窗口 SUM 的 ORDER BY 必须使用同样顺序，
保证 occurred_at 顺序与 before/after_quantity 回放顺序一致。
```

**实施约束**：

| # | 约束 |
| --- | --- |
| 1 | backfill 与建表 DDL **同文件**（`V19__scm_inventory.sql`，DDL 之后）执行；不拆成独立迁移 |
| 2 | 时序：Flyway 在应用装配阶段跑完迁移才对外提供服务 → 「新 W6 应用开始接受业务请求前完成」由框架天然保证 |
| 3 | 回放排序键 = `(receipt.confirmed_at ASC, receipt.id ASC, receipt_item.id ASC)`，**禁止**仅按 `receipt_item.id` |
| 4 | 窗口累计 `SUM(...) OVER (PARTITION BY warehouse_id, sku_id ORDER BY <同序> ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING)` —— **ORDER BY 与 INSERT 输出同序** |
| 5 | `occurred_at = receipt.confirmed_at`、`operator = receipt.operator`（回放语义 = 「在确认时刻发生」） |
| 6 | 幂等：`INSERT ... ON CONFLICT (...) WHERE ... DO NOTHING`（可重复执行、零增量） |
| 7 | 内置对账断言：`Σ movement.quantity = Σ received_quantity`，`Σ balance.quantity = Σ movement.quantity`，不一致即迁移失败 |

> 修正的实质：`receipt_item.id` 的生成顺序**不等于**确认时间顺序（草稿收货单可以后建先确认），
> 只按 id 排序会让 `before/after_quantity` 的回放顺序与 `occurred_at` 的时间顺序**不一致**，
> 从而让「按时间回放」与「按快照回放」得到两条不同的历史。这是数据可信性问题，不是风格问题。

---

### Q6 — AbsenceIT 处置：APPROVED

**裁决**：`PurchaseInventoryContractAbsenceIT` 废止，由 **W6 contract / behavior PG IT** 正式替代。

**实施约束**：

- 删除该 IT（其两条断言在接线后必然失败，是阶段边界的正常交接，不是回归）；
- 替代物必须同时覆盖：**契约 Bean 唯一性**（原断言 1 的正向形态）+ **入库行为**（原断言 2 的替代）；
- 删除处保留一行注释指向 W6 IT，保证审计连续性。

---

### Q7 — movement append-only 硬化：APPROVED WITH HARDENING

**裁决（原文要点）**：

```text
inventory_movement 保留 deleted 列，
继续匹配 W5 的 partial unique index 契约。

但新增 DB 约束：
  CHECK (deleted = FALSE)

movement 必须真正 append-only。
禁止 soft delete / update 历史流水；
未来冲销必须新增反向 movement。
```

**实施约束**：

| # | 约束 |
| --- | --- |
| 1 | 表**保留** `deleted BOOLEAN NOT NULL DEFAULT FALSE` —— 与 W5 §8.5 预留的部分唯一索引 `WHERE deleted = FALSE AND source_document_item_id IS NOT NULL` 逐字一致，避免契约漂移 |
| 2 | **新增** `CONSTRAINT ck_inventory_movement_append_only CHECK (deleted = FALSE)`：把 append-only 从「服务层纪律」升级为**数据库约束** |
| 3 | 任何 `UPDATE ... SET deleted = TRUE`、任何对历史流水的 `UPDATE` 在 DB 层**直接失败** |
| 4 | 表**无** `version` / `updated_at` / `updated_by`（对齐 `receipt_weighing_record` 的只追加纪律） |
| 5 | DAO **只提供** insert + select，**不得**出现任何 update 方法 |
| 6 | 未来冲销（如 `PURCHASE_RETURN_OUT`）**必须新增反向 movement**，不得修改/删除历史行 |

---

### Q8 — 菜单号段与 Migration 版本号：APPROVED

**裁决**：

```text
菜单使用：800 / 801 / 802 / 811 / 821。

V19/V20 仍只是占位。
实施前重新：
  git fetch
  git ls-remote origin refs/heads/main
检查真实 migration max 与重复版本，
再分配连续版本。
```

**实施约束与核验结果（2026-09-18 实施前执行）**：

```text
git fetch origin                             → 成功
git ls-remote origin refs/heads/main         → 7a94a41ebbf3bb7d9eb06f1e4ea3860a9eecc0fb
git rev-parse HEAD                           → 7a94a41ebbf3bb7d9eb06f1e4ea3860a9eecc0fb
git merge-base --is-ancestor <remote> HEAD   → 真（远端是本地祖先或相等，未分叉）
db/migration/ 版本号                          → V1…V18 唯一且连续，max = V18，无重复
结论                                          → 取 V19（schema + backfill）、V20（菜单权限）
```

- 菜单号段：`800 库存管理（目录）/ 801 库存余额 / 802 库存流水 / 811 余额查询（按钮）/ 821 流水查询（按钮）`；
- 现最大菜单 id = 753（W5 仓库），800 系无冲突；`t_menu` 序列随迁移推进到 `max + 1`；
- 权限码：`scm:inventory:balance:query` / `scm:inventory:movement:query`。

---

### Q9 — 流水号：APPROVED

**裁决**：W6-1 **不增加** `movement_no`；人类可读来源使用 `receipt_no`。

**实施约束**：溯源链路 = `inventory_movement.source_document_id → purchase_receipt.receipt_no`（查询侧联表取）；
不引入发号器、不新增列。若未来财务要求票据化，加列属**加法演进**。

---

### Q10 — 负库存：APPROVED

**裁决**：`inventory_balance.quantity >= 0` **本期冻结**；负库存策略留到销售出库波次明确裁决。

**实施约束**：

- `CONSTRAINT ck_inventory_balance_quantity CHECK (quantity >= 0)` 现在写入迁移；
- W6-1 唯一写路径是入库（正数增量），负库存在本阶段不可能发生；
- 冻结该 CHECK 的目的是让未来出库波次必须**显式**决策负库存策略才能绕过，而不是默认放开。

---

### Q11 — `ON CONFLICT` 写法：REVISED

**裁决（原文要点）**：

```text
删除"partial unique index conflict target 依赖 PG15+"这一错误前提。

统一使用与 partial unique index 完全匹配的
  ON CONFLICT (...) WHERE ... DO NOTHING。

实际 PostgreSQL 版本只记录到验收证据，
不在业务代码中制造 PG15 分支。
PG IT 必须真实验证该 SQL。
```

**实施约束**：

| # | 约束 |
| --- | --- |
| 1 | 原设计 §14 Q11 中的「带 index predicate 的部分唯一索引作冲突目标需 PG 15+」是**错误前提**，已从设计文档删除 |
| 2 | 统一写法：`INSERT INTO inventory_balance (...) VALUES (...) ON CONFLICT (warehouse_id, sku_id) WHERE deleted = FALSE DO NOTHING` —— **冲突目标与部分唯一索引完全匹配**（含 `WHERE` 谓词） |
| 3 | **业务代码零 PG 版本分支**：不得出现 `if (pgVersion >= 15)` 之类的判断，也不得为兼容性退化为无目标 `ON CONFLICT DO NOTHING` |
| 4 | 实际 PostgreSQL 版本**只记录到验收证据**（本环境 = Docker `postgres:18-alpine`，实测 18.6） |
| 5 | PG IT 必须在真实 PostgreSQL 上**执行该 SQL**（并发首建用例 #5 直接覆盖），不能只靠代码审阅 |

---

### Q12 — 余额页默认仓库：APPROVED WITH CONDITION

**裁决（原文要点）**：

```text
如果系统恰好只有一个 ENABLED warehouse，
余额页默认带出该仓库。

如果启用仓库数量 != 1，
不得自动选择任意仓库，只提供普通筛选。
```

**实施约束**：

- **前端**落点：余额页加载时调用既有 `GET /scm/warehouse/list`（只返回 `ENABLED`）；
  `length === 1` → 把该仓库写入 `queryForm.warehouseId`；否则保持 `undefined`；
- **后端不做**任何隐式默认：`warehouseId` 为空即不过滤；
- 多仓化时该默认值自动退化为普通筛选，**无需改后端、无需新增端点**；
- 不引入「上次选择的仓库」之类的新状态。

---

### Q13（新增）— Inventory Unit Invariant

**裁决**：

```text
当前 inventory_balance 只有 warehouse_id + sku_id + quantity，
但正式模型 SupplierSku.purchaseUnit 是 supplier+sku 维度，
同一 SKU 理论上可能存在不同采购单位。

禁止把不同单位的数量静默相加。

修改 inventory_balance：
  unit VARCHAR(32) NOT NULL

首笔入库：
  unit = InboundFact.unit。

后续同 warehouse+sku 入库：
  fact.unit 必须等于 balance.unit，
  否则抛 INVENTORY_UNIT_MISMATCH
  并使 PurchaseReceiptService.confirm 整体回滚。

Backfill 在写入前必须检查：
  同 warehouse_id + sku_id
  COUNT(DISTINCT purchase_unit_snapshot)
  如 > 1：
    migration FAIL，
    不得静默汇总。

W6-1 不实现单位换算。
单位转换放到后续 Product Conversion / Inventory Unit 波次。
```

**实施约束**：

| # | 约束 | 落点 |
| --- | --- | --- |
| 1 | `inventory_balance.unit VARCHAR(32) NOT NULL` | V19 DDL |
| 2 | 首笔入库写入 `fact.unit`（= `purchase_order_item.purchase_unit_snapshot`） | `InventoryCommandService.postPurchaseInbound` |
| 3 | 后续同键入库 `fact.unit != balance.unit` → `INVENTORY_UNIT_MISMATCH(41001)`，confirm **整体回滚** | 同上（在持余额行锁之后判定） |
| 4 | 部分唯一索引仍是 `(warehouse_id, sku_id) WHERE deleted = FALSE` —— `unit` **不进**唯一键（一个键只允许一行、只允许一个单位） | V19 DDL |
| 5 | backfill 前置检查：同 `(warehouse_id, sku_id)` 的 `COUNT(DISTINCT purchase_unit_snapshot) > 1` → `RAISE EXCEPTION`，**migration FAIL**，绝不静默汇总 | V19 backfill 段 |
| 6 | backfill 生成余额行时 `unit` 取该组唯一单位（前置检查已保证唯一） | V19 backfill 段 |
| 7 | **不实现**单位换算；`sale_unit` 不参与库存口径 | 全域 |
| 8 | 查询页展示 `unit`（余额）/ `unit_snapshot`（流水） | 前端两页 |

**为什么必须显式失败而不是自动换算**：库存的核心可信度来自「余额 = 流水的净和」。
一旦允许把 `箱` 与 `kg` 静默相加，余额会变成一个**没有物理意义**的数字，且错误在写入时不可见、
只会在未来出库/盘点时以「账实不符」的形式暴露。因此 W6-1 的选择是：**遇到单位冲突就停**。

---

### Q13-附 — `InboundFact` 时间/操作者闭环（人类批准的 contract evolution）

**裁决（原文要点）**：

```text
inventory_movement 定义了 occurred_at / operator，
并要求实时与 backfill 都使用 receipt.confirmed_at / operator。
当前 InboundFact 无这两个字段，设计不闭合。

W6 实施时允许正式扩展 PurchaseInventoryContract.InboundFact：
  OffsetDateTime occurredAt
  String operator

PurchaseReceiptService.confirm 传入：
  receipt.getConfirmedAt()
  receipt.getOperator()

禁止 InventoryCommandService 自行用 OffsetDateTime.now()
或 ambient operator 替代已经冻结的收货确认事实。

这是 W6 contract evolution，
不改变 W5 既有业务语义。
```

**实施约束**：

| # | 约束 |
| --- | --- |
| 1 | `InboundFact` record 增加两个组件：`OffsetDateTime occurredAt`、`String operator`（追加在 `idempotencyKey` 之后，保持既有组件顺序不变） |
| 2 | 装配点在 `PurchaseReceiptService.confirm`：**在收货单 CONFIRMED 落库（`setConfirmedAt` / `setOperator`）之后**再装配事实 |
| 3 | `InventoryCommandService` **禁止**使用 `OffsetDateTime.now()`；**禁止**使用 ambient `ScmOperator.current()` 作为流水的 `operator` |
| 4 | 库存侧对两个字段做**非空断言**，缺失即 `INVENTORY_PARAM_INVALID(41003)` |
| 5 | 不改变 W5 既有业务语义：W5 零调用点，扩展 record 组件不影响任何现有代码 |
| 6 | backfill 与实时路径**同口径**：两条路径的 `occurred_at` 都等于 `receipt.confirmed_at` |

---

## 2. 裁决后的实施边界（W6-1 允许 / 禁止）

**允许（严格只做这些）**：

```text
Inventory Balance（表 + 实体 + DAO + 只读查询页）
Inventory Movement（表 + 实体 + DAO + 只读查询页）
Purchase Receipt → PURCHASE_IN（契约实现 + confirm 接线）
Backfill（V19 内 data-only 段）
两个只读查询页（余额、流水）
PG IT + Playwright
V19 / V20 两个迁移
```

**禁止（本阶段一律不得进入）**：

```text
Mini Program（含 uni-app 迁移）
销售库存占用 / 出库（OrderInventoryContract.reserve/release 保持零实现零调用）
盘点
报损报溢
调拨
规格转换 / 单位换算
预警
完整成本核算（移动加权平均）
配送
分拣
溯源
```

**停止条件**：W6-1 实现完成后**停止于 W6-1 验收**，
**不自动进入 W6-2 或 Mini Program**。

---

## 3. 实施前置条件核对表

```text
[x] Q1–Q13 全部裁决完成（本文件）
[x] Target Design 已按裁决逐条修订（含 Q5 顺序、Q7 硬化、Q11 修订、Q12 条件化、Q13、InboundFact）
[x] 远端基线核验：fetch + ls-remote = 本地 HEAD = 7a94a41，未分叉
[x] Flyway 版本核验：V1…V18 唯一连续，max = V18 → 取 V19 / V20
[x] 错误码段核验：全仓 4xxxx 已占用 40000–40091 / 40410–40499 / 40910–40999 → 库存取 41001–41003
[x] 本地数据库环境就绪：Docker `postgres:18-alpine`（18.6），127.0.0.1:15432，库 xsy_scm，schema xsy_v2
[x] 菜单号段核验：现最大 753 → 800 系无冲突
```

**结论：允许进入 W6-1 实现。**

---

## 4. 与裁决相关的风险登记

| # | 风险 | 处置 |
| --- | --- | --- |
| R1 | 历史 CONFIRMED 收货数据中，同 `(warehouse, sku)` 存在多种采购单位 → **backfill 迁移直接失败，应用无法启动** | 这是 Q13 要求的**显式失败**行为，不是缺陷。处置：迁移报错信息必须包含冲突的 `(warehouse_id, sku_id)` 与单位清单，便于人工裁决（改历史数据 or 拆账），**不得**临时放宽约束上线 |
| R2 | `PurchaseInventoryContractAbsenceIT` 删除后，W5 阶段的「零库存」边界不再有测试守护 | 由 W6 IT #17（Bean 唯一性）+ #1（真实入库行为）替代；边界从「无库存」变为「有且仅有一条入库写路径」 |
| R3 | 单位不变量让「同 SKU 换供应商换采购单位」的入库**直接失败** | 符合 Q13 裁决意图；单位换算能力（Product Conversion / Inventory Unit 波次）是解锁路径，本期不做 |
| R4 | 本地库从原生 PG 换成容器 PG（18.3 → 18.6），历史验收证据的版本号不再一致 | 验收报告中记录新的实际版本；Q11 已明确「版本只记录到验收证据，不进入业务代码分支」，因此不构成阻塞 |
