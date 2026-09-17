# W5 Purchase 采购域 · Legacy Audit（只读审计）

> 阶段：W5 = Purchase（Audit + Target Design，**零编码**）
> 基线 HEAD：`cd9f2b88c558d601e57f8ad48241158afe78c780`（`feature/sprint5`，工作区 clean）
> 已冻结：`V1–V14` 不可修改；`SmartAdmin v3.31 + PostgreSQL`；W1/W2/W3/W4 全部 COMPLETE
> 三源：**A** = legacy（`95a5423`）· **B** = V2 正式工作区（根目录）· **C** = `project-reference-examples/xsy-scm/`
> 本文件**不含**任何实现、不新增 migration、不修改业务代码。

---

## 0. 本文件的性质与前置裁决项

### 0.1 本文件是什么

这是 W5（采购域）的**只读审计报告**。它回答三个问题：

1. **采购域的真实业务规则是什么**（A 源 legacy 事实）；
2. **哪些资产可以直接复制、哪些必须重写、哪些禁止复制**（C 源资产）；
3. **V2 已经提供哪些契约、W5 必须新建哪些能力、边界画在哪里**（B 源现状）。

本文件**不**做设计决策。所有需要在编码前由人工拍板的问题集中在 §9，并在
`docs/architecture/2026-09-16-w5-purchase-target-design.md` §14 给出完整推荐值。

### 0.2 编码前必须由人工裁决的问题（先行摘要）

完整论证见 §9，此处只列标题，便于快速定位：

| 编号 | 问题 | 本审计推荐 |
| --- | --- | --- |
| **Q1** | W5 是否建立**仓库主数据**（`warehouse`） | 建（最小化），否则采购单无法绑定仓库 |
| **Q2** | 采购单状态机取 **A 源 6 状态**还是 **C 源 5 状态** | A 源 6 状态 |
| **Q3** | 超收规则：**可配置容差（A）** 还是 **静默标记（C）** | A 源可配置容差，超出整笔回滚 |
| **Q3a** | 容差配置载体：A 源自建 `sys_config` 还是 SmartAdmin 字典 | SmartAdmin 字典（Native First） |
| **Q4** | 收货模式：W5 是否实现 **DIRECT + DEFERRED 双模式** | W5 只实现 DIRECT；DEFERRED 延后 W6，且 4 个模式/投递列**不建** |
| **Q4a** | `receipt_mode` 是否在 W5 暴露给前端 | 不适用（该字段 W5 不建） |
| **Q5** | **Inventory 边界**：W5 是否创建库存表 / 是否写库存 | 只定义契约、**零调用点、零库存表** |
| **Q6** | 采购需求（`purchase_demand`）汇总生成是否属于 W5 | 属于 W5，但去掉 A 源死表 `generation_batch` |
| **Q7** | 收货单确认口径：**一单一次确认（spec §4.4）** 还是 **一单多次确认（V10）** | 一单一次确认（采 P1 更晚口径） |
| **Q8** | 单号机制：PG sequence 还是 SmartAdmin `SerialNumberService` | PG sequence（与 W4 一致） |
| **Q9** | 采购退货 / 无单收货 / 询价比价 / 二维码协同 是否属于 W5 | 全部**不做** |
| **Q10** | 数据权限（按采购员/供应商过滤）是否属于 W5 | 不做（同 W4 U7） |
| **Q11** | 错误码段分配（A 源与 W4 大范围撞码） | 重新分配到 `40080+` / `40480+` / `40980+` |
| **Q12** | 是否同步更新 `AGENTS.md` 路线图与 `MEMORY.md` | 是（避免文档自相矛盾） |
| **Q13** | 采购单行与采购需求是 **1:1** 还是 **N:N** | 1 行 1 需求（加 `(order_id, sku_id)` 唯一） |

> **编号对齐说明（重要）**：本表为**审计阶段**的 15 项（Q1–Q13 + 子裁决 Q3a/Q4a）。
> `target-design` 在细化过程中**新增了 8 项**（少收关单、生成入参、`PARTIALLY_CONFIRMED` 去留、
> 单号前缀、日志 id 可空、称重表无 version/deleted、`scale_precision` 去留、需求快照采购单位），
> 并把编号展开为 **Q1–Q17 + 5 个子项（Q2a/Q3a/Q6a/Q7a/Q8a），共 22 项**。
> **唯一权威的裁决清单是 `2026-09-16-w5-purchase-target-design.md` §14。**
> 对应关系：审计 Q3a → 设计 Q3a；审计 Q4a 并入设计 Q4；审计 Q1–Q13 → 设计同名项（Q3a 保留）。

### 0.3 本审计对「Purchase vs Receiving vs Inventory」的立场（重要）

用户指令 §3 要求明确回答三个问题。审计阶段的事实结论（**设计口径见 target-design §3**）：

| 问题 | 审计事实 |
| --- | --- |
| Receiving 是 Purchase 聚合内部命令，还是独立聚合？ | **A 源把它做成独立聚合**（`PurchaseReceipt` 有自己的单号序列、自己的状态机、自己的版本字段），但**业务上完全由采购单驱动**（`create` 要求采购单处于 `SUBMITTED`/`PARTIALLY_RECEIVED`，且收货单仓库强制继承采购单仓库）。**C 源同样把它做成独立表但无状态机**。→ 见 §9.4 与 target-design §3。 |
| Inventory 从什么时候开始拥有库存事实？ | **A 源：收货确认事务内**（`InventoryApplicationService.postPurchaseIn` 带 `Propagation.MANDATORY`，与收货同事务）。**C 源：`directStock=true` 时收货即入库**。**V2 现状：完全没有库存域**。→ 这是 W5 最大的边界风险，见 §9.5。 |
| 创建/提交采购单是否影响库存？ | **两源均否**。A 源 `create`/`submit` 只写采购单与需求分配；C 源 `add`/`generate` 只写采购单与明细。 |

**本审计的硬立场**：W5 **不得**创建任何临时库存余额表、不得实现库存余额/占用/流水/出库。
W5 只定义 `PurchaseInventoryContract`（**零调用点**），与 W4 的
`OrderInventoryContract` / `NoOpOrderInventoryContract` 保持同一纪律。理由见 §4.5 与 §9.5。

---

## 1. 审计范围与方法

### 1.1 范围

**在范围内**

```text
A 源：purchase/**  · inventory/**（仅采购相邻调用） · supplier/**（含 Warehouse）
      purchase 相关 Flyway（V8–V11 / V27 / V28 / V33） · purchase 测试 · purchase 设计规格
B 源：xsy-scm-server/sa-admin/**/module/scm/** · xsy-scm-web/src/**（SCM 范围）
      V1–V14 migration · SmartAdmin 基础设施（幂等/单号/权限/响应/异常/字典/序列化）
C 源：postgresql/05-采购.sql · **/purchase/** · **/receiving/** · xsy-scm-web/**/purchase/**
      C 的 purchase Controller/Service/Manager/DAO/Mapper XML/Form/VO/Vue/enum/menu/permission/SQL
文档源：docs/architecture/** · AGENTS.md · SMARTADMIN_REFERENCE_RULES.md
        project-reference-examples/xsy-scm/docs/requirement/05-采购管理.md · 06-库存管理.md · 00-总览与通用约定.md §13
```

**明确不在范围内**

```text
销售出库 / 库存占用与释放 / 盘点 / 报损报溢 / 调拨 / 库存调整 / 批次与保质期
分拣 / 称重设备协议 / 配送 / 应付账款 / 付款 / 对账
采购退货（退供应商） · 供应商门户 · 询价比价 · 二维码协同
```

### 1.2 三个来源与方法

| 源 | 定位 | 本审计的实际用法 | 可信度 |
| --- | --- | --- | --- |
| **A** legacy `95a5423` | **业务规则唯一事实源** | 逐文件读取实体/服务/迁移/规格/测试 | 规则权威，但**代码自身有缺陷**（§3.13） |
| **B** V2 根工作区 | 正式工程 | 读 `module/scm/**`、V1–V14、前端 SCM 资产 | 契约权威，**不可绕开** |
| **C** `project-reference-examples/xsy-scm/` | **资产库非主干** | 只提取**前端页面交互**与**字段形状** | **最低**：`@Version`=0、0 测试、DDL 与代码不一致 |

**为什么 A 源必须取自 `95a5423`（而非 `803a862`）**

```bash
# 803a862 本身已不含 legacy 采购域（W4 阶段已实测同一模式）
git ls-tree -r --name-only 803a862 | grep -c 'com/xianshuyuan/scm/purchase'   # → 0
git ls-tree -r --name-only 95a5423 | grep -c 'com/xianshuyuan/scm/purchase'   # → 66
git merge-base --is-ancestor 95a5423 803a862   # → YES
git rev-list --count 95a5423..803a862          # → 1
```

→ **`95a5423`（= `803a862^`）是最后包含 legacy 采购域的提交**，本审计全部 A 源证据取自该提交。

### 1.3 方法

1. **不读二手总结**：W4 文档里的采购相关内容一律回源核对（`95a5423` blob / C 源文件 / V2 工作区）。
2. **迁移优先**：DDL 事实以 `V8→V33` 的**最终叠加态**为准，而不是某一版；A 源状态机在 V27 后才成型。
3. **代码 vs DDL vs 规格三方对账**：任何不一致都登记为 A-D 或 K 项，不擅自选边。
4. **C 源只做减分**：C 的每一处引用都先验证「它引用的列/表是否真的存在」，据此判定可否复制。
5. **零修改**：审计期间未改动任何业务代码、未新增 migration。

### 1.4 结论摘要（先看这 12 条）

1. **A 源采购域是一个成熟但自我演进过 3 轮的域**：V8（建表）→ V10/V11（多次确认 + 只追加流水）→ V27/V28/V33（P1：需求来源追溯 + 独立收货记录 + 可配置超收容差 + 少收关单）。**最终口径以 V27/V28/V33 + P1 规格为准**，不是 V8。
2. **A 源采购单最终是 6 状态**：`DRAFT / SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED / CANCELLED`。C 源是 5 状态且**两个状态不可达**。
3. **A 源超收最终是「可配置容差内允许」**（`purchase.over_receipt_tolerance_percent` 默认 10，V33 显式删掉了 V11 的严格约束）。C 源是「不拒绝 + 静默标记 OVER」。
4. **A 源收货是「一采购单多张独立收货单」**（P1 口径），**不是**「一张收货单多次确认」（V10 旧口径）。两套机制在 A 源内部并存过，P1 明确选择了前者。
5. **A 源把库存写入绑死在收货事务内**（`Propagation.MANDATORY`）。**这与 W5「不得实现库存」直接冲突**，必须在 W5 重划边界（§9.5）。
6. **V2 已有 `supplier` + `supplier_sku`，且结构与 A 源 V8 的 `supplier_sku` 逐列一致** → W5 **必须复用**，不得新建第二套供应商商品关系表（回应用户 §4）。
7. **V2 完全没有仓库、库存、库存流水**。A 源采购单 `warehouse_id NOT NULL`，C 源**硬编码 `warehouseId=1L`**。→ Q1 是 W5 的第一道门。
8. **C 源采购后端在 PostgreSQL 上直接报错**：`PurchaseItemMapper.queryPage` 查 `t_purchase_item.supplier_id`，`ReceiveMapper.queryPage` 查 `t_receive.receive_type/supplier_id/product_id/sku_id/remark`，**这些列在 C 的 DDL 里根本不存在**；`PurchaseOrderMapper` 用 MySQL `INSTR`。→ C 后端**零可复制**。
9. **C 源采购单状态机有 2 个死状态**：`CANCELLED(5)` 与 `INVALID(3)` 无任何代码路径可达；`receive_flag` 按累计量写单条记录 → 第二次收货后第一条的标记即过期。
10. **C 源采购无操作日志、无快照列、无 version、无幂等**。→ 与 W4 已建立的「operation log / snapshot / version / idempotency」四条纪律全部冲突。
11. **C 源前端 4 个采购页面（1567 行）是 W5 唯一可复制资产**，但需 30+ 项强制适配（删 `resizable`、换枚举、补 version/幂等、明细页降级为表单内表格）。
12. **错误码必须整体重分配**：A 源的 `40050/40051/40060/40450/40452/40460/40461/40950–40956/40960–40963/50060` 与 V2 W4 的 `40060–40074/40460–40463/40960–40970` **大面积撞码**。

---

## 2. 事实源全景

### 2.1 源的可信度排序（本审计实际使用）

```text
1. 用户本次指令                        （最高，范围与边界的最终裁决）
2. AGENTS.md §7.4/§7.5 + 需求 00-总览 §13 已定口径（G-01…G-08）
3. A 源 legacy `95a5423`（业务规则唯一事实源，但代码缺陷需登记）
4. B 源 V2 根工作区（正式契约，不可绕开）
5. A 源设计规格（spec/plan，可能落后于迁移，需与迁移对账）
6. C 源（资产库，仅前端交互与字段形状）
7. C 源需求文档 05/06（含大量「待确认」，不是已定口径）
```

### 2.2 A 源证据（legacy，`95a5423`）

```bash
git ls-tree -r --name-only 95a5423 | grep -E 'purchase|receiv|inventory' | wc -l   # 130（含 docs/web）
```

| 类别 | 数量 | 说明 |
| --- | --- | --- |
| `purchase/**/*.java` | 66 | 3 Controller / 4 Service / 4 Manager / 12 Mapper / 13 Entity / 10 DTO / 5 VO / 3 ErrorCodes / 2 NumberGenerator |
| `purchase/**/*.xml` | 13 | Mapper XML |
| `inventory/**/*.java` | 8 | Controller / Service / 2 Entity / 2 Mapper / `PurchaseInCommand` / `InventoryMovementType` |
| `inventory/**/*.xml` | 2 | `InventoryMapper.xml` / `InventoryMovementMapper.xml` |
| `supplier/**`（含 Warehouse） | 26 | `SupplierService` / `SupplierSkuService` / `WarehouseService` / 3 Entity / 3 Mapper / 4 Controller(含 Test) |
| Flyway | 7 | `V8` `V9` `V10` `V11` `V27` `V28` `V33` |
| 前置 Flyway | 1 | `V1`（建 `supplier` + `warehouse`） |
| 后端测试 | 8 | 见 §3.12 |
| 前端（React） | 13 | 8 页面 + 1 组件 + 2 api/types + 2 模型测试 |
| 设计文档 | 5 | 见 §2.5 |

### 2.3 B 源证据（V2 正式工作区）

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/
  common/**   （ScmOperator / ScmErrorCode / ScmCommonErrorCode / ScmBusinessException
               / ScmExceptionHandler / ScmFixedScale4Serializer
               / ScmStrictDecimalStringDeserializer / ScmDecimalStrings / JsonbStringMapTypeHandler）
  product/**  （W1）
  customer/** （W2）
  supplier/** （W2，含 SupplierSkuService / SupplierSkuSyncManager / OrderableSkuVO）
  pricing/**  （W3）
  order/**    （W4，含 support/OrderInventoryContract + NoOpOrderInventoryContract）
xsy-scm-server/sa-admin/src/main/resources/db/migration/V1..V14（14 个，全部冻结）
xsy-scm-web/src/api/business/scm/** · src/views/business/scm/** · src/constants/business/scm/**
  src/types/business/scm/**
```

### 2.4 C 源证据（`project-reference-examples/xsy-scm/`）

```text
postgresql/05-采购.sql                    4 表：t_purchase_item / t_purchase_order / t_receive / t_supplier
postgresql/07-补充模块P1.sql              3 表：t_inquiry / t_inquiry_item / t_inquiry_quote
postgresql/99-初始化数据.sql              菜单 900–944、权限 purchase:*、种子 t_purchase_* / t_receive
postgresql/06-库存.sql                    5 表：t_stock_balance / t_stock_flow / t_stock_check / t_stock_check_item / t_stock_adjust
xsy-scm-server/.../business/purchase/**   66 java + 8 mapper xml
xsy-scm-server/.../business/stock/**      （库存域，仅用于确认 C 的库存耦合）
xsy-scm-web/src/views/business/purchase/** 6 页面（含 inquiry/supplier）
xsy-scm-web/src/constants/business/purchase/purchase-const.ts
xsy-scm-web/src/api/business/purchase/**  6 api
docs/requirement/05-采购管理.md · 06-库存管理.md
```

### 2.5 文档源

| 文档 | 位置 | 本审计用途 |
| --- | --- | --- |
| `AGENTS.md` §7.4 Purchasing / §7.5 Inventory | 仓库根 | 采购链路与库存铁律 |
| 需求 `00-总览与通用约定.md` §13 | C 源 | **G-01…G-08 已定口径**（G-03 单仓库不启用批次） |
| 需求 `05-采购管理.md` | C 源 | 采购需求原文 + **05-01…05-06 待确认** |
| 需求 `06-库存管理.md` | C 源 | 库存边界（06-01/06-02/06-04 已定） |
| spec `2026-09-04-sprint-3-purchase-receiving-inventory-design.md` | A 源（548 行） | **A 源最完整的设计规格**（含 §12 MVP 决策记录） |
| spec `2026-09-11-p1-purchase-receiving-upgrade.md` | A 源（21 行） | **P1 最终口径**（覆盖旧规格的收货/容差/关单） |
| plan `2026-09-07-purchase-receiving-inventory-design.md` | A 源（1201 行） | 实施计划（与 spec 对账用） |
| `docs/architecture/2026-09-16-w4-order-target-design.md` | V2 | 结构范式与边界纪律（Inventory contract 先例） |

---

## 3. A 源（legacy）采购域资产清单

### 3.1 分层文件（legacy 包结构，**V2 不沿用**）

```text
com.xianshuyuan.scm.purchase
├─ controller/   PurchaseDemandController · PurchaseOrderController · PurchaseReceiptController
├─ service/      PurchaseDemandService · PurchaseOrderService · PurchaseReceiptApplicationService
│                PurchaseOrderNumberGenerator · PurchaseReceiptNumberGenerator
│                PurchaseDemandErrorCodes · PurchaseOrderErrorCodes · PurchaseReceiptErrorCodes
├─ entity/       PurchaseDemandEntity · PurchaseDemandAllocationEntity · PurchaseDemandStatus
│                PurchaseDemandGenerationBatchEntity
│                PurchaseOrderEntity · PurchaseOrderItemEntity · PurchaseOrderStatus
│                PurchaseReceiptEntity · PurchaseReceiptItemEntity · PurchaseReceiptStatus
│                PurchaseReceiptMode · PurchaseReceiptPutawayStatus
│                PurchaseReceiptConfirmationEntity · PurchaseReceiptConfirmationItemEntity
│                ReceiptWeighingRecordEntity · ReceiptWeighingSource · PurchaseOperationLogEntity
├─ dto/          PurchaseDemandGenerateRequest · PurchaseDemandAllocationRequest
│                PurchaseOrderSaveRequest · PurchaseOrderItemRequest · PurchaseOrderCancelRequest
│                PurchaseOrderShortCloseRequest · PurchaseOrderVersionRequest
│                PurchaseReceiptCreateRequest · PurchaseReceiptConfirmRequest
│                PurchaseReceiptConfirmItemRequest · PurchaseReceiptPutawayRequest
├─ mapper/       12 个 Mapper（含 PurchaseReceivingConfigMapper）
└─ vo/           PurchaseOrderResponse · PurchaseOrderItemResponse · PurchaseDemandAllocationResponse
                 PurchaseOperationLogResponse · PurchaseReceiptConfirmResult

com.xianshuyuan.scm.inventory
├─ controller/   InventoryController
├─ service/      InventoryApplicationService · PurchaseInCommand
└─ entity/       InventoryEntity · InventoryMovementEntity · InventoryMovementType

com.xianshuyuan.scm.supplier
├─ service/      SupplierService · SupplierSkuService · WarehouseService
└─ entity/       SupplierEntity · SupplierSkuEntity · WarehouseEntity
```

> **V2 不沿用此分层**。V2 落点为 `module/scm/purchase/{constant,controller,dao,domain/{entity,form,vo},manager,service,support}`，
> 与 W1–W4 完全一致（见 target-design §2.1）。

### 3.2 枚举（逐值，A 源最终态）

**`PurchaseOrderStatus`**（6 值，`purchase_order.status`）

| 值 | 含义 | 可流转到（依据 spec §4.3 + `PurchaseOrderService` + V27） |
| --- | --- | --- |
| `DRAFT` | 草稿，可编辑行/需求分配 | `SUBMITTED` · `CANCELLED` |
| `SUBMITTED` | 已提交，只允许收货或取消 | `PARTIALLY_RECEIVED` · `RECEIVED` · `CANCELLED` |
| `PARTIALLY_RECEIVED` | 至少一次确认收货，仍有可收量 | `PARTIALLY_RECEIVED` · `RECEIVED` · `SHORT_CLOSED` |
| `RECEIVED` | 达完成条件（含容差内超收），只读 | — |
| `SHORT_CLOSED` | 少收关单，终态（V27 引入） | — |
| `CANCELLED` | 取消，终态（必须有 `cancel_reason`） | — |

**`PurchaseReceiptStatus`**（3 值，`purchase_receipt.status`）

| 值 | 含义 | 引入版本 |
| --- | --- | --- |
| `DRAFT` | 已建单未确认，**不产生任何副作用** | V8 |
| `PARTIALLY_CONFIRMED` | 部分行已确认（**V10 口径，P1 已不推荐**） | V10 |
| `CONFIRMED` | 本收货单已完成确认 | V8 |

**`PurchaseReceiptMode`**（2 值，V27 引入）

| 值 | 含义 | 库存副作用 |
| --- | --- | --- |
| `DIRECT` | 收货确认即入库 | **确认事务内**写库存余额 + `PURCHASE_IN` 流水 |
| `DEFERRED` | 先收货、后二次入库确认 | 确认只置 `PENDING_PUTAWAY`；`putaway` 命令才写库存 |

**`PurchaseReceiptPutawayStatus`**（3 值，V27 引入）

`NOT_APPLICABLE`（DIRECT 或未确认）· `PENDING_PUTAWAY`（DEFERRED 已确认待入库）· `PUTAWAY_COMPLETED`

**`PurchaseDemandStatus`**（5 值）

`PENDING` · `PARTIALLY_ALLOCATED` · `ALLOCATED` · `FULFILLED` · `CANCELLED`
（注：`FULFILLED` / `CANCELLED` 在 A 源代码中**无写入路径**，见 §3.13 A-D2）

**`ReceiptWeighingSource`**（1 值）`MANUAL`（V9 把 CHECK 收紧为只允许 `MANUAL`；spec §12 明确 `DEVICE` 延后）

**`InventoryMovementType`**（1 实现 + 10 预留）
实现：`PURCHASE_IN`；预留：`SALES_OUT` `RETURN_IN` `PURCHASE_RETURN_OUT` `STOCKTAKE_IN`
`STOCKTAKE_OUT` `LOSS` `GAIN` `TRANSFER_IN` `TRANSFER_OUT` `ADJUSTMENT`

### 3.3 DDL（V8 → V33 的**最终叠加态**）

**7 个迁移的演进职责**

| 版本 | 职责 | 关键动作 |
| --- | --- | --- |
| `V8` | 建采购/收货/库存骨架 | 3 序列 + `supplier_sku`（**改 V1 已存在表**）+ `purchase_demand` + `purchase_demand_allocation` + `purchase_order` + `purchase_order_item` + `purchase_operation_log` + `purchase_receipt` + `purchase_receipt_item` + `receipt_weighing_record` + `inventory` + `inventory_movement` |
| `V9` | 精度与约束修正 | `actual_weight`→`NUMERIC`（去 scale）；`weighing_source` 收紧为只允许 `MANUAL`；`inventory.quantity >= 0` **被删除**（允许负库存字段级）；流水约束改为「类型↔来源单据」配对 |
| `V10` | 支持多次确认 | `purchase_receipt` 状态加 `PARTIALLY_CONFIRMED`；**新增 `purchase_receipt_confirmation` + `purchase_receipt_confirmation_item`**（带幂等 scope/key/hash + `confirmation_id` 进流水） |
| `V11` | 收紧入库投递 | `ck_purchase_order_item_received_not_over_planned`（**严格禁超收**）+ **`inventory_movement` 只追加触发器**（`trg_inventory_movement_append_only`） |
| `V27` | **P1：需求来源追溯 + 独立收货 + 可配置容差** | 建 `sys_config`（种子 `purchase.over_receipt_tolerance_percent=10`）+ `purchase_demand_generation_batch`；`purchase_demand` 加 `generation_batch_id`/`target_warehouse_id`/`source_confirmed_at`/`calculate_inventory`/`original_required_quantity`/`inventory_deducted_quantity`；`purchase_order` 加 `SHORT_CLOSED` + `short_closed_at`/`short_close_reason`；`purchase_receipt` 加 `receipt_mode`/`putaway_status`/`putaway_at`/`putaway_operator`/`reason`，**删掉 `uk_purchase_receipt_order_active`**（恢复「一单多收货单」）；`purchase_receipt_item` 加 `planned_quantity`/`cumulative_received_quantity`/`remaining_quantity`/`over_receipt_quantity`/`receipt_difference`；`inventory_movement` 加 `receipt_id` |
| `V28` | 回填 P1 新增列 | `original_required_quantity` / `inventory_deducted_quantity` / 收货行 5 个 P1 数量列，并加 `ck_purchase_receipt_item_reconciliation` |
| `V33` | **允许容差内超收** | `DROP CONSTRAINT IF EXISTS ck_purchase_order_item_received_not_over_planned` |

**最终表清单（15 张，A 源）**

```text
采购域（9）  purchase_demand · purchase_demand_allocation · purchase_demand_generation_batch
             purchase_order · purchase_order_item · purchase_operation_log
             purchase_receipt · purchase_receipt_item · purchase_receipt_confirmation
             purchase_receipt_confirmation_item · receipt_weighing_record   （实为 11 张）
库存域（2）  inventory · inventory_movement
基础（3）    supplier · warehouse · supplier_sku
配置（1）    sys_config
```

**关键字段（`purchase_order`，A 源最终态）**

```text
id BIGINT IDENTITY PK
order_no VARCHAR(64) NOT NULL          ← uk_purchase_order_no_active
supplier_id BIGINT NOT NULL            + supplier_code_snapshot / supplier_name_snapshot
purchaser_id BIGINT                    （可空，负责人）
warehouse_id BIGINT NOT NULL           + warehouse_code_snapshot / warehouse_name_snapshot
planned_arrival_date DATE
status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'   ← CHECK 6 值
total_amount NUMERIC(18,4) NOT NULL DEFAULT 0
remark VARCHAR(500)
cancel_reason VARCHAR(500)             ← CHECK：CANCELLED 时非空
short_closed_at TIMESTAMPTZ            ← V27
short_close_reason VARCHAR(500)        ← V27，CHECK：SHORT_CLOSED 时非空
submitted_at TIMESTAMPTZ
cancelled_at TIMESTAMPTZ
version INTEGER NOT NULL DEFAULT 0 · deleted BOOLEAN NOT NULL DEFAULT FALSE
created_at/updated_at TIMESTAMPTZ · created_by/updated_by VARCHAR(64)
```

**关键字段（`purchase_order_item`，A 源最终态）**

```text
id · purchase_order_id · spu_id · sku_id
spu_code_snapshot · product_name_snapshot · sku_code_snapshot · sku_name_snapshot
spec_values_snapshot JSONB · purchase_unit_snapshot · product_type_snapshot
planned_quantity NUMERIC(18,4) NOT NULL (>0)
received_quantity NUMERIC(18,4) NOT NULL DEFAULT 0    ← V11 曾加 ≤planned，V33 删除
purchase_price NUMERIC(18,4) NOT NULL (>=0)
line_amount NUMERIC(18,4) NOT NULL (>=0)
sort_order INTEGER · version · deleted · 审计列
```

**关键字段（`purchase_receipt`，A 源最终态）**

```text
id · receipt_no · purchase_order_id · purchase_order_no_snapshot
warehouse_id · warehouse_code_snapshot · warehouse_name_snapshot
status VARCHAR(20) DEFAULT 'DRAFT'         ← DRAFT / PARTIALLY_CONFIRMED / CONFIRMED
receipt_mode VARCHAR(16)                   ← V27：DIRECT / DEFERRED
putaway_status VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLICABLE'   ← V27
putaway_at · putaway_operator · reason
received_at · confirmed_at · operator · remark
version · deleted · 审计列
```

**关键字段（`purchase_receipt_item`，A 源最终态）**

```text
id · purchase_receipt_id · purchase_order_item_id · sku_id
spu_code_snapshot · product_name_snapshot · sku_code_snapshot · sku_name_snapshot
spec_values_snapshot · purchase_unit_snapshot · product_type_snapshot
received_quantity NUMERIC(18,4) NOT NULL DEFAULT 0   ← V10 放宽为 >=0
actual_weight NUMERIC · weight_unit · weighing_source(MANUAL) · correction_reason
planned_quantity NUMERIC(18,4) NOT NULL              ← V27/V28
cumulative_received_quantity NUMERIC(18,4) NOT NULL DEFAULT 0   ← V27
remaining_quantity NUMERIC(18,4) NOT NULL DEFAULT 0             ← V27
over_receipt_quantity NUMERIC(18,4) NOT NULL DEFAULT 0          ← V27
receipt_difference NUMERIC(18,4) NOT NULL DEFAULT 0             ← V27（可为负）
sort_order · version · deleted · 审计列
```

### 3.4 状态机（A 源，逐条）

**采购单（`PurchaseOrderService` 实现，非声明式策略表）**

| 命令 | 前置状态 | 校验 | 后置状态 | 幂等 scope |
| --- | --- | --- | --- | --- |
| `create` | — | 供应商启用 + 仓库启用 + 需求锁定校验 | `DRAFT` | `PURCHASE_ORDER_CREATE` |
| `update` | 仅 `DRAFT` | `version` 必须相等；retained reconciliation | `DRAFT` | 无（靠 version） |
| `submit` | 仅 `DRAFT` | `version` 相等 | `SUBMITTED` | `PURCHASE_ORDER_SUBMIT:{id}` |
| `cancel` | 仅 `DRAFT` / `SUBMITTED` | `version` 相等；`reason` 必填 | `CANCELLED` | `PURCHASE_ORDER_CANCEL:{id}` |
| `shortClose` | 仅 `PARTIALLY_RECEIVED` | `version` 相等；**至少一行已收 且 至少一行未收齐**；`reason` 必填 | `SHORT_CLOSED` | `PURCHASE_ORDER_SHORT_CLOSE:{id}` |
| 收货确认驱动 | `SUBMITTED` / `PARTIALLY_RECEIVED` | 全部行 `received >= planned` | `RECEIVED` / `PARTIALLY_RECEIVED` | — |

**关键事实：`PARTIALLY_RECEIVED` 不允许取消**（spec §4.3 明文：「`PARTIALLY_RECEIVED` 不允许取消」）。
取消已部分收货的采购单在 A 源是**不可表达**的操作；需求 `05-采购管理.md` §6 的
「采购单取消需处理已收货部分」**在 A 源未实现**（见 §8 G-3）。

**收货单（`PurchaseReceiptApplicationService` 实现）**

| 命令 | 前置 | 后置 |
| --- | --- | --- |
| `create` | 采购单 ∈ {`SUBMITTED`, `PARTIALLY_RECEIVED`}；仓库继承采购单 | `DRAFT`，逐采购行生成收货行（`received_quantity=0`，快照 `planned/cumulative/remaining/over/difference`） |
| `confirm` | 收货单 ≠ `CONFIRMED`；`version` 相等；采购单 ∈ {`SUBMITTED`, `PARTIALLY_RECEIVED`} | 采购单收齐 → `CONFIRMED`，否则 `PARTIALLY_CONFIRMED`；`DIRECT` → `PUTAWAY_COMPLETED` 且写库存；`DEFERRED` → `PENDING_PUTAWAY` |
| `putaway` | `putaway_status == PENDING_PUTAWAY`；`version` 相等 | `PUTAWAY_COMPLETED`，写库存 |

**`confirm` 的逐行规则（`validateLines` + `confirmLine`）**

```text
1. 请求行去重（同一 receiptItemId 出现两次 → INVALID_QUANTITY）
2. receiptItem 必须属于本收货单，且 version 必须相等
3. purchaseItem 必须存在（行归属校验）
4. declaredQuantity = parsePositiveQuantity(...)         （>0，scale<=4，整数位<=14）
5. 若 productTypeSnapshot == NON_STANDARD：
      actualWeight 必填且 >0；weightSource 必须 == MANUAL；effectiveQuantity = actualWeight
   否则（STANDARD）：
      actualWeight / weightSource / correctionReason 必须全为空，否则 INVALID_QUANTITY
6. maximum   = plannedQuantity × (1 + tolerancePercent/100)
   remaining = maximum − purchaseItem.receivedQuantity
   若 effectiveQuantity > remaining → OVER_RECEIVED（整笔回滚）
7. 写入 confirmation + confirmation_item（+ 非标品写 weighing_record）
8. 若 mode != DEFERRED → inventory.postPurchaseIn(...)
9. purchaseItem.receivedQuantity += effectiveQuantity（乐观锁）
10. 回写 receiptItem 的 received/cumulative/remaining/over/difference + actualWeight/weightUnit/source/reason
```

**注意 A 源的两处语义瑕疵（登记为 A-D5/A-D6，见 §3.13）**

- 收货单状态 `CONFIRMED` 的判据是**整张采购单是否收齐**，而非本收货单的行是否全部确认；
- 请求中可以**只提交部分行**，未提交的行保持 `received_quantity=0`，但收货单仍可能被判为 `CONFIRMED`。

### 3.5 收货模式与入库状态机（V27 / P1）

```text
DIRECT    : create(DRAFT) → confirm(写库存 + PUTAWAY_COMPLETED)
DEFERRED  : create(DRAFT) → confirm(PENDING_PUTAWAY, 不写库存) → putaway(写库存 + PUTAWAY_COMPLETED)
```

`putaway` 的防重（A 源实现）：

```text
幂等 scope   PURCHASE_RECEIPT_PUTAWAY:{receiptId}
二级防重     inventory_movement 的 uk_inventory_movement_receipt_confirmation
             = (source_document_type, source_document_id, source_document_item_id, confirmation_id)
             + InventoryApplicationService.postPurchaseIn 内的 selectBySource 先查
```

### 3.6 采购需求与分配（A 源）

**来源（`PurchaseDemandService.generateInternal`）**

```text
输入：salesOrderIds[]（显式订单 ID 列表）
校验：每个订单必须 status == CONFIRMED，否则 SOURCE_INVALID
遍历：该订单的活动订单行
      跳过 actualQuantity == null 或 <= 0 的行
      每个订单行至多一条活动需求（uk_purchase_demand_source_active）
      requiredQuantity = 订单行的 actualQuantity          ← 注意：取「实数量」而非「订购量」
      demandDate = LocalDate.now()
并发：selectActiveByIdForUpdate + insertIfAbsent，插入冲突则重读（收敛为同一行）
```

> **A 源 `generate` 的输入是「订单 ID 列表」，而 P1 规格写的是「半开时间段 `[startAt, endAt)` + 仓库 + calculateInventory」。**
> 代码是**旧版**（V27 之前），P1 规格描述的批次/库存抵扣**未落地**（§3.13 A-D1/A-D3）。这是 W5 必须裁决的一处（Q6）。

**分配（`PurchaseOrderService.lockAndValidateDemands` / `allocateDemand` / `applyDemandDelta`）**

```text
按 demandId 升序锁定（确定性锁序，避免死锁）
每个 demandId：
  同一需求在多行出现时 SKU 必须一致
  allocated + requested <= requiredQuantity，否则 OVER_ALLOCATED
  若 demand 已有分配，则 (supplierId, warehouseId) 必须与本次一致，否则 ALLOCATION_CONFLICT
唯一键：uk_purchase_demand_allocation_source_active = (purchase_order_item_id, purchase_demand_id)
```

**草稿编辑 = retained reconciliation（`reconcileItems`）**

```text
保留行：必须带 id + version，且 SKU 不得变、demandId 不得替换（DEMAND_REPLACEMENT_NOT_ALLOWED）
新增行：id == null → createItem + newAllocation
删除行：不在保留集内 且 receivedQuantity == 0 → 软删 allocation + 软删 orderItem
        若 receivedQuantity > 0 → INVALID_STATE（不允许删除已收货行）
需求侧：按 demandId 升序统一重算 allocatedQuantity 与 status
        归零 → supplierId/warehouseId 置 null，status = PENDING
```

### 3.7 数量 / 重量 / 金额与精度（A 源）

| 项 | A 源规则 |
| --- | --- |
| 传输形态 | **十进制字符串**（spec §3.3 明文） |
| 存储 | `NUMERIC(18,4)`（数量、价格、金额统一） |
| 计算 | `BigDecimal` + `RoundingMode.HALF_UP`，scale 固定 4 |
| 行金额 | `lineAmount = round(plannedQuantity × purchasePrice, 4, HALF_UP)` |
| 总金额 | `totalAmount = Σ lineAmount`（`create`/`update` 时计算） |
| 数量校验 | `>0`、`scale<=4`、整数位 `<=14`（`parseQuantity`/`parsePositiveQuantity`） |
| 价格校验 | `>=0`、`scale<=4`、整数位 `<=14`（`parsePrice`） |
| 非标品 | **实收以 `actualWeight` 为准**，`received_quantity` 不覆盖 `planned_quantity`（spec §3.3 明文） |
| 对外序列化 | `decimal()` = `setScale(4, HALF_UP).toPlainString()` |
| **实际金额** | **A 源无 `actual_amount` 字段**（C 源有但从不写入）。A 源用 `purchase_order_item.received_quantity × purchase_price` 派生 |

### 3.8 幂等（A 源，完整作用域表）

| 作用域 | 命令 | 备注 |
| --- | --- | --- |
| `PURCHASE_DEMAND_GENERATE` | 生成采购需求 | 无 id 后缀（全局） |
| `PURCHASE_ORDER_CREATE` | 建采购单 | 全局 |
| `PURCHASE_ORDER_SUBMIT:{id}` | 提交 | 按单隔离 |
| `PURCHASE_ORDER_CANCEL:{id}` | 取消 | 按单隔离 |
| `PURCHASE_ORDER_SHORT_CLOSE:{id}` | 少收关单 | 按单隔离 |
| `PURCHASE_RECEIPT_CONFIRM:{id}` | 收货确认 | 按单隔离 |
| `PURCHASE_RECEIPT_PUTAWAY:{id}` | 二次入库 | 按单隔离 |

**实现**：`IdempotencyService.claim(scope, key, request)` → 规范化请求哈希 + INSERT 竞争 + 同 key 异 hash → 409。
**A 源缺陷**：`claim` 的 scope **不含操作者**（V2 W4 的 `OrderIdempotencyService` 把 `ScmOperator.current()` 拼进 scope，防止跨操作者重放）→ 见 §3.13 A-D19。

### 3.9 操作日志（A 源）

`purchase_operation_log`：`purchase_order_id` + `purchase_receipt_id`(可空) + `operation_type` + `operator` + `reason` + `before_data`/`after_data` JSONB。

**写入的操作类型（实测）**：`CREATE` · `UPDATE` · `SUBMIT` · `CANCEL` · `SHORT_CLOSE` · `RECEIPT_CREATE` · `RECEIPT_CONFIRM` · `RECEIPT_PUTAWAY`

**A 源缺陷**：`after_data` 只写 `{"status":..., "version":...}` 或 `{"state":...}`，
**不是全量前后快照**（V2 W4 的 `order_operation_log` 写全量 before/after）→ 见 §3.13 A-D15。
**注意**：`ck_purchase_operation_log_type` **不存在**（A 源没有对 operation_type 做白名单 CHECK），与 V2 W4 的
`ck_order_operation_log_type`（6 值白名单）不同。

### 3.10 错误码（A 源，逐条）

| 类 | 码 | 含义 | **与 V2 W4 冲突？** |
| --- | --- | --- | --- |
| `PurchaseDemandErrorCodes` | 40450 | 采购需求不存在 | 否 |
| | 40950 | 销售订单状态不允许生成采购需求 | 否 |
| | 40951 | 采购需求版本冲突 | 否 |
| | 40952 | 采购需求分配冲突 | 否 |
| | 40956 | 采购需求已存在 | 否 |
| | **40050** | 分配数量超过需求 | 否 |
| `PurchaseOrderErrorCodes` | 40452 | 采购单不存在 | 否 |
| | 40955 | 采购单版本冲突 | 否 |
| | **40953** | 采购单状态不允许此操作 | 否 |
| | **40954** | 保留采购行不允许替换采购需求来源 | **是**：W4 `ORDER_RETURN_ORDER_NOT_CONFIRMED`? 否。W4 40954 未占用 → 不冲突 |
| | **40051** | 采购数量格式不正确 | 否 |
| `PurchaseReceiptErrorCodes` | **40460** | 收货单不存在 | **是**：W4 `ORDER_NOT_FOUND=40460` |
| | **40960** | 采购订单已存在收货单 | **是**：W4 `ORDER_STATE_INVALID=40960` |
| | **40961** | 收货单状态不允许此操作 | **是**：W4 `ORDER_ORIGINAL_INVALID=40961` |
| | **40962** | 收货单版本冲突 | **是**：W4 `ORDER_ACTUAL_NOT_ALLOWED=40962` |
| | **40461** | 收货行不存在 | **是**：W4 `ORDER_ITEM_NOT_FOUND=40461` |
| | **40963** | 本次收货数量超过剩余可收数量 | **是**：W4 `ORDER_ACTUAL_QUANTITY_REQUIRED=40963` |
| | **40060** | 收货数量或重量不正确 | **是**：W4 `ORDER_SUPPLEMENT_REASON_REQUIRED=40060` |
| | 50060 | 采购超收容差系统参数无效 | 否 |
| `InventoryApplicationService` | **40970** | 库存余额并发冲突 | **是**：W4 `ORDER_RETURN_ORDER_NOT_CONFIRMED=40970` |

→ **W5 必须整体重分配**（Q11）。可复用且不冲突的 V2 已有码：`40921`（VERSION_CONFLICT）、`40000`（VALIDATION_ERROR）、`40430`/`40930`/`40949`/`40030`（W1–W3）。

### 3.11 API 契约（A 源，`/api` 前缀）

```http
GET  /api/suppliers · POST /api/suppliers · PUT /api/suppliers/{id} · POST /api/suppliers/{id}/status
GET  /api/warehouses · POST /api/warehouses · PUT /api/warehouses/{id} · POST /api/warehouses/{id}/status
GET  /api/suppliers/{id}/skus · PUT /api/suppliers/{id}/skus

GET  /api/purchase-demands
POST /api/purchase-demands/generate
POST /api/purchase-demands/allocate

GET  /api/purchase-orders · POST /api/purchase-orders
GET  /api/purchase-orders/{id} · PUT /api/purchase-orders/{id}
POST /api/purchase-orders/{id}/submit
POST /api/purchase-orders/{id}/cancel
POST /api/purchase-orders/{id}/short-close          ← V27 新增

GET  /api/purchase-receipts · POST /api/purchase-receipts
GET  /api/purchase-receipts/{id} · PUT /api/purchase-receipts/{id}
POST /api/purchase-receipts/{id}/confirm            ← 需 Idempotency-Key
POST /api/purchase-receipts/{id}/putaway            ← 需 Idempotency-Key，V27 新增

GET /api/inventories · GET /api/inventory-movements  ← W5 不实现
```

**幂等键**：`create`/`submit`/`cancel`/`short-close`/`confirm`/`putaway` 全部要求 `Idempotency-Key` 头。

### 3.12 测试覆盖（A 源，12 个测试类）

| 层 | 测试类 | 覆盖 |
| --- | --- | --- |
| 单元 | `PurchaseOrderServiceTest` | 采购单状态矩阵 / 金额 / 需求分配 |
| 单元 | `PurchaseDemandServiceTest` | 需求生成去重与来源校验 |
| 单元 | `PurchaseReceiptApplicationServiceTest` | 多次收货累计 / 剩余量 / 标品 vs 非标品 |
| 单元 | `InventoryApplicationServiceTest` | `PURCHASE_IN` 流水与余额一致性 |
| 单元 | `SupplierServiceTest` / `SupplierSkuServiceTest` | 供应商与 SKU 关系 |
| Controller | `PurchaseOrderControllerTest` / `PurchaseDemandControllerTest` / `SupplierControllerTest` / `SupplierControllerCrudTest` / `WarehouseControllerTest` | Web 层 |
| IT | `PurchasePersistenceIT` | 采购持久化 + 部分唯一索引 |
| IT | `PurchaseReceiptInventoryIT` | **收货确认事务 + 库存余额/流水累计 + 重复确认只入一次** |
| IT | `ProcurementOptimisticLockIT` | 采购乐观锁 |
| 前端 | `purchaseDemandGenerationModel.test.ts` / `receiptFormModel.test.ts` | 汇总模型 / 收货剩余量 |

→ **A 源的测试矩阵是 W5 测试设计的直接依据**（target-design §11 逐条映射）。

### 3.13 A 源自身的缺陷（A-D1 … A-D23，**W5 不复制**）

| 编号 | 缺陷 | 证据 | W5 处置 |
| --- | --- | --- | --- |
| **A-D1** | `purchase_demand_generation_batch` 表 + `PurchaseDemandGenerationBatchMapper` **零调用点**（死表/死代码） | 全仓 grep `PurchaseDemandGenerationBatchMapper` → 0 命中 | **不迁**；幂等改用 W4 的 `idempotency_record` |
| **A-D2** | `purchase_demand.fulfilled_quantity` **从未被写入**（`generate` 设 0，此后无更新）；`PurchaseDemandStatus.FULFILLED` / `CANCELLED` 无写入路径 | `PurchaseDemandService` 全文 | 不迁 `fulfilled_quantity`；状态只保留 `PENDING`/`PARTIALLY_ALLOCATED`/`ALLOCATED` |
| **A-D3** | P1 规格的 `calculateInventory` / 库存抵扣**未落地**：`original_required_quantity` / `inventory_deducted_quantity` 在代码中从未赋值（V28 回填后恒为 `required`/`0`） | `generateInternal` 无相关代码 | 不迁；「是否扣减实时库存」列为 **Q6 子问题**，推荐 W5 不做库存抵扣 |
| **A-D4** | `receipt_weighing_record.scale_precision` 在 `confirmLine` **未赋值** | `confirmLine` 只设 raw/confirmed/unit/source/reason | W5 要么删除该列，要么显式赋值（推荐**删除**，W5 无设备精度来源） |
| **A-D5** | 收货单 `CONFIRMED` 的判据是**整张采购单收齐**，而非本收货单行全部确认 | `receipt.setStatus(complete ? CONFIRMED : PARTIALLY_CONFIRMED)`，`complete` 由 `purchaseRows` 决定 | W5 改为**按本收货单语义**判定（见 target-design §4.2） |
| **A-D6** | `putaway()` 未校验 `receiptMode == DEFERRED`（仅靠 `putaway_status` 间接约束）；`confirm()` 也未禁止对 `PARTIALLY_CONFIRMED` 重复确认 | `putaway` 全文 | W5 只实现 `DIRECT`，该分支不存在 |
| **A-D7** | `PurchaseOrderService.update` 对 `request.version()` 为 `null` 时返回 `VERSION_CONFLICT`，语义不精确（应为「版本必填」） | `Objects.equals(order.getVersion(), request.version())` | W5 显式区分「版本缺失」与「版本冲突」 |
| **A-D8** | `total_amount` 只在 `create`/`update` 计算，**收货后不重算**；无「实际金额」字段 | 全文 | W5 明确：`total_amount` = 计划金额（不随收货变），实际金额由行派生（见 target-design §7.5） |
| **A-D9** | `receipt_weighing_record` 与 `purchase_receipt_item.actual_weight` **双写冗余** | `confirmLine` 同时写两处 | W5 保留一张（见 target-design §5.4 裁决） |
| **A-D10** | 收货单状态机在 spec §4.4 写 `DRAFT → CONFIRMED`，代码却有 `PARTIALLY_CONFIRMED`（V10），P1 又改用独立收货单 → **A 源三处口径并存** | spec §4.4 vs V10 vs P1 规格 | W5 采 P1 口径（Q7） |
| **A-D11** | `PurchaseOrderService.listResponses()` 对每张单调 `detail()` → **N+1 查询** | 全文 | W5 用分页 + 批量装配 |
| **A-D12** | 所有写操作 `createdBy`/`operator` 硬编码 `"SYSTEM"`（spec §2.2 明说「操作者沿用 SYSTEM」） | 全文 | W5 用 `ScmOperator.current()` |
| **A-D13** | `InventoryApplicationService.postPurchaseIn` 用 `Propagation.MANDATORY`，**把库存写入绑死在收货事务内** | 注解 | W5 **重划边界**（§9.5） |
| **A-D14** | 幂等 scope **不含操作者** → 不同操作者可用同一 key 重放他人结果 | `idempotency.claim("PURCHASE_ORDER_CREATE", key, request)` | W5 用 W4 的 `OrderIdempotencyService` 模式（scope 前缀 `ScmOperator.current()`） |
| **A-D15** | 操作日志 `after_data` 只写 status/version，**非全量前后快照** | `log()` 方法 | W5 写全量 before/after（同 W4） |
| **A-D16** | `purchase_operation_log` **无 `operation_type` 白名单 CHECK** | V8 DDL | W5 加 CHECK 白名单 |
| **A-D17** | `purchase_receipt_confirmation` 用「先查后插」(`selectActiveByOrderItemAndDemandForUpdate` 类) 而非 INSERT 竞争 | `allocateDemand` | W5 采 INSERT 竞争（同 W4） |
| **A-D18** | 自建 `sys_config` 配置表 | V27 | 与 SmartAdmin 字典重复 → **Q5** |
| **A-D19** | 错误码与 V2 W4 **大面积撞码**（§3.10） | — | **Q11** 重分配 |
| **A-D20** | `inventory.quantity >= 0` 在 V9 被删除 → 字段级允许负库存，但业务层未定义负库存语义 | V9 | W5 不实现库存，无需处置；W6 需明确 |
| **A-D21** | `purchase_order` 的 CHECK 白名单在 V27 才与枚举对齐（V8 无 `SHORT_CLOSED`） | V8 vs V27 | W5 一次到位 |
| **A-D22** | `supplier_sku` 在 V8 用 `ALTER TABLE ... ADD COLUMN` 之前，V1 已存在同名表 → 若迁移顺序错会重复建表 | V1 + V8 | W5 复用 V2 的 `supplier_sku`，不重复建 |
| **A-D23** | `purchase_demand_allocation` 唯一键 `(purchase_order_item_id, purchase_demand_id)` **允许一行多需求**，但 `reconcileItems` 用 `Map<itemId, allocation>`（`allocationByItemId`）**假设一行只有一个 allocation** → 草稿编辑时「一行多需求」的分配会静默丢失（只保留最后一个） | `PurchaseOrderService.reconcileItems` 的 `allocationByItemId.put(...)` 是覆盖写 | **W5 必须二选一**：① 一行一需求（加 `uk_purchase_order_item_order_sku_active`）② 一行多需求（修 reconcile 为 `Map<(itemId,demandId), allocation>`）→ 见 target-design §14 **Q13**，推荐 ① |
| **A-D24** | 采购需求的**采购单位快照错取销售单位**：`demand.setPurchaseUnitSnapshot(row.getSaleUnitSnapshot())` —— 需求来源是销售订单行，但 `purchase_unit` 应来自 `supplier_sku.purchase_unit`（销售单位 ≠ 采购单位） | `PurchaseDemandService.generateInternal` | W5 改为**在 `allocate` 时用 `supplier_sku.purchase_unit` 覆盖**（需求生成时尚未确定供应商）→ target-design §14 **Q17** |

---

## 4. 必须保留的业务不变量（P1–P26）

> 编号 P1–P26 供 W5 验收对账使用（对应 target-design §15 DoD 的勾选项）。

### 4.1 采购单聚合与写入口

| 编号 | 不变量 |
| --- | --- |
| **P1** | 采购单是聚合根；**禁止独立明细写入口**（A 源 `PurchaseOrderService.update` 的 retained reconciliation 是唯一入口） |
| **P2** | 草稿编辑必须「保留原行 ID + SKU + 需求来源」；不得把已有 allocation 改绑到其他 SKU 或需求 |
| **P3** | 省略且**尚未收货**的草稿行软删除，其 allocation 同事务软删除并释放需求已分配量 |
| **P4** | 已有收货、已提交或终态的采购单**不得编辑** |
| **P5** | 采购单必须人工明确**供应商 + 仓库**；同一单所有行必须匹配单头的供应商与仓库 |
| **P6** | 金额由服务端重算：`lineAmount = round(planned × price, 4, HALF_UP)`；`totalAmount = Σ lineAmount`；客户端不得提交累计量或库存余额 |

### 4.2 采购需求与分配

| 编号 | 不变量 |
| --- | --- |
| **P7** | 采购需求来源只能是**已确认（`CONFIRMED`）销售订单**的活动订单行 |
| **P8** | **每个活动订单行至多一条活动需求**（`uk_purchase_demand_source_active`），重复生成返回已有结果 |
| **P9** | 分配不得超需求：`allocated + requested <= requiredQuantity` |
| **P10** | 同一需求在多行出现时 SKU 必须一致；(supplier, warehouse) 必须一致 |
| **P11** | 活动 allocation 以 `(purchase_order_item_id, purchase_demand_id)` 唯一；相同组合不同数量 → 冲突 |
| **P12** | 需求锁定必须按 `demandId` **升序**（确定性锁序） |

### 4.3 状态与命令

| 编号 | 不变量 |
| --- | --- |
| **P13** | 状态只能通过**显式命令端点**转换，不提供通用状态更新接口 |
| **P14** | 仅 `DRAFT` / `SUBMITTED` 可取消；**`PARTIALLY_RECEIVED` 不允许取消** |
| **P15** | 少收关单只允许从 `PARTIALLY_RECEIVED` 发起，且必须「至少一行已收 + 至少一行未收齐 + 原因非空」 |
| **P16** | 一张采购单允许**多张独立收货单**；每次到货新增一条记录，**不修改历史记录** |
| **P17** | 收货单创建**不改变库存**；只有确认（或二次入库）才产生库存副作用 |
| **P18** | 累计实收 ≥ 计划量（含容差内超收）→ `RECEIVED`；>0 但未达 → `PARTIALLY_RECEIVED` |
| **P19** | 已关单 / 已完成采购单**禁止新收货** |

### 4.4 数量 / 重量 / 金额

| 编号 | 不变量 |
| --- | --- |
| **P20** | 传输与存储统一 **4 位定点**（`NUMERIC(18,4)` + 字符串 + `HALF_UP`） |
| **P21** | 标品以 `receivedQuantity` 为累计实收量与入库量；**非标品以确认后的 `actualWeight` 为累计实收量与入库量** |
| **P22** | **原计划数量永不被覆盖**（`planned_quantity` 只读） |
| **P23** | 累计有效收货 ≤ `plannedQuantity × (1 + tolerance/100)`；超出**整笔回滚** |
| **P24** | 每行收货的 `remaining / over_receipt / receipt_difference` 必须与 `planned / cumulative` **恒等式成立** |

### 4.5 幂等、并发、审计与边界

| 编号 | 不变量 |
| --- | --- |
| **P25** | 6 个命令必须有幂等键；相同 key + 相同规范化请求哈希 → 返回原结果；相同 key + 不同哈希 → 409 |
| **P26** | 库存变更必须**只能经库存业务层**产生只追加流水；W5 **不得**直接写库存余额、**不得**建临时库存表；W5 只定义契约且**零调用点** |

---

## 5. C 源采购资产审计（`project-reference-examples/xsy-scm/`）

### 5.1 资产清单

| 层 | 资产 | 行数 |
| --- | --- | --- |
| SQL | `postgresql/05-采购.sql`（4 表） | 187 |
| SQL | `postgresql/07-补充模块P1.sql`（询价 3 表） | — |
| SQL | `postgresql/99-初始化数据.sql`（菜单 900–944 + 种子） | — |
| 后端 | `purchase/**/*.java` | 66 文件 / 4361 行 |
| 后端 | `purchase/**/*.xml` | 8 文件 / 398 行 |
| 前端 | `views/business/purchase/purchase-list.vue` | 349 |
| 前端 | `views/business/purchase/purchase-item-list.vue` | 345 |
| 前端 | `views/business/purchase/purchase-receive-list.vue` | 384 |
| 前端 | `views/business/purchase/purchase-generate.vue` | 143 |
| 前端 | `views/business/purchase/inquiry-list.vue` | 662 |
| 前端 | `views/business/purchase/supplier-list.vue` | 328 |
| 前端 | `constants/business/purchase/purchase-const.ts` | 72 |
| 前端 | `api/business/purchase/*.ts` | 6 文件 / 97 行 |

### 5.2 C 的 5 状态机 vs A 源的 6 状态

| A 源（6） | C 源（5，数字） | 差异 |
| --- | --- | --- |
| `DRAFT` | **无** | C 无草稿态：`add` 直接落 `PENDING(1)` |
| `SUBMITTED` | `PURCHASING(2)`「采购中」 | C 需先 `accept` 从 1→2；A 源的 `SUBMITTED` 是 `submit` 命令的结果 |
| `PARTIALLY_RECEIVED` | `PARTIAL_RECEIVED(3)` | 语义近似 |
| `RECEIVED` | `COMPLETED(4)` | 语义近似 |
| `SHORT_CLOSED` | **无** | C 不支持少收关单 |
| `CANCELLED` | `CANCELLED(5)` | **C 中不可达**（无 cancel 端点） |
| — | `PENDING(1)`「待接单」 | **A 源无「供应商接单」概念**（供应商不是系统用户，需求 05-03 待确认） |

**C 的采购明细状态**（3 值）：`PENDING(1)` / `PARTIAL(2)` / `DONE(3)` — 与 A 源概念等价但无 `over` 表达。
**C 的收货单状态**（3 值）：`RECEIVED(1)` / `STOCKED(2)` / `INVALID(3)` — **`INVALID` 不可达**。
**C 的收货标记**（3 值）：`NORMAL(1)` / `UNDER(2)` / `OVER(3)` — A 源**无此概念**（A 源用 `over_receipt_quantity` + `receipt_difference` 表达差异）。

→ **W5 采 A 源 6 状态**（Q2）。C 的 `PENDING(待接单)` 是「供应商接单」语义，
而 W5 明确不做供应商门户；C 的 `CANCELLED` / `INVALID` 是死状态，复制它们等于复制不可达代码。

### 5.3 C 的缺陷清单（H1–H31，**W5 不复制**）

#### 5.3.1 功能性缺陷（S1，**在 PostgreSQL 上直接不可用**）

| 编号 | 缺陷 | 证据 | 影响 |
| --- | --- | --- | --- |
| **H1** | `PurchaseItemMapper.queryPage` 查 `t_purchase_item.supplier_id`，但 `05-采购.sql` 的 `t_purchase_item` **无此列** | DDL 14 列 vs Mapper SELECT 15 列；`99-初始化数据.sql:1265-1273` 的 INSERT 也是 14 值 | PG 报错 `column "supplier_id" does not exist` |
| **H2** | `ReceiveMapper.queryPage` 查 `t_receive.receive_type / supplier_id / product_id / sku_id / remark`，DDL **无这 5 列** | DDL 16 列（含 `receive_flag`）vs Mapper SELECT 18 列；种子 16 值 | PG 报错，5 列缺失 |
| **H3** | `PurchaseOrderMapper.xml` 用 MySQL `INSTR(purchase_no, #{...})` | 第 18 行 | PG 无 `INSTR` → 报错（与 W4 H11 同类） |
| **H4** | `PurchaseOrderMapper.queryPage` / `ReceiveMapper.queryByItemId` / `PurchaseItemMapper.queryByPurchaseId` 用 `SELECT *` | — | 静默丢值（与 W4 H12 同类） |
| **H5** | `ReceiveService.add` **无幂等键、无乐观锁** | 全文 | 重复提交 → 重复累计 `received_quantity` + 重复入库 |
| **H6** | `ReceiveService.buildStockForm` / `buildNoOrderStockForm` **硬编码 `warehouseId = 1L`** | 第 318 / 335 行 | 无仓库概念；单仓库假设被写死 |
| **H7** | `ReceiveService.add` / `confirmInbound` 在采购事务内**直接调 `StockOperateService.purchaseInbound`** | 第 146 / 174 行 | 采购与库存强耦合（与 W5 边界冲突） |
| **H8** | `receive_flag` 按**累计量**计算后写**单条收货记录** | `add` 第 131-133 行 | 第二次收货后，第一条记录的 flag 即成为陈旧数据 |
| **H9** | `resolveItemStatus` 用 `>=` → 超收静默转 `DONE(3)` | 第 281 行 | 超收被静默吞掉 |
| **H10** | 超收**无容差、无审批、无拒绝**，仅打标 | `ReceiveFlagEnum` | 与需求 05-02 冲突；库存与账面对不上 |
| **H11** | `PurchaseStatusEnum.CANCELLED(5)` **无任何代码路径可达** | 全模块 grep `cancel` → 0 命中 | 死状态 |
| **H12** | `ReceiveStatusEnum.INVALID(3)` **无任何代码路径可达** | `delete` 走 `batchUpdateDeleted` 逻辑删除 | 死状态 |
| **H13** | `PurchaseItemService.add/update/delete/batchDelete` 是**独立明细写入口** | Controller 4 个端点 | 破坏聚合根（与 W4 H20 同类） |
| **H14** | `PurchaseOrderService.update` **无状态检查、无 version** | 第 74-78 行 | 可修改已收货采购单 |
| **H15** | `reassignSupplier` 只排除 `COMPLETED`/`CANCELLED` → 已部分收货时仍可改绑 | 第 111-113 行 | 单据头与明细供应商快照不一致 |
| **H16** | `PurchaseOrderGenerateService` 依赖 `t_order`（数字 status `NOT IN (1,11,12)`）/ `t_product_supplier`（SPU 级）/ `t_stock_balance` | `PurchaseGenerateMapper.xml` | **三处都与 V2 契约不符**（V2 是 `sales_order` 字符串 status / `supplier_sku` SKU 级 / 无库存表） |
| **H17** | `PurchaseOrderGenerateService.generate` 注释说「初始为**采购中**态」，但 `PurchaseOrderService.add` 落 `PENDING(1)` 待接单 | 注释 vs 代码 | 注释与实现不一致（需核实 `PurchaseOrderGenerateManager`） |
| **H18** | 金额 `numeric(18,2)` / 数量 `numeric(18,3)` | DDL | 与 V2 冻结 `NUMERIC(18,4)` 冲突 |
| **H19** | `deleted_flag smallint` | DDL | 与 V2 `deleted BOOLEAN` 冲突 |
| **H20** | `timestamp`（无时区） | DDL | 与 V2 `TIMESTAMPTZ` 冲突 |
| **H21** | `status smallint` 数字枚举 | DDL | 与 V2 字符串枚举冲突 |
| **H22** | 全部表**无 `version` 列** | DDL | 无乐观锁（与 W4 H16 同类） |
| **H23** | 权限码 `purchase:*` **无 `scm:` 前缀**；`purchase:accept` / `purchase:generate` / `purchase:generate:preview` / `purchase:item:reassignSupplier` / `purchase:receive:confirmInbound` / `purchase:receive:addNoOrder` / `purchase:receive:relatePurchase` **均未授权任何角色** | `99-初始化数据.sql:344-358` 只覆盖 `query/add/update/delete/batchDelete` | 端点存在但无角色可用（与 W4 H14 同类） |
| **H24** | 菜单 component 路径 `/business/purchase/*.vue` | `99-初始化数据.sql:335-338` | 与 V2 的 `/business/scm/purchase/*.vue` 不符（与 W4 H15 同类） |
| **H25** | 采购单种子单号混用 `PO20260908001` 与 `CGD202609090001`，与注释「CGD + yyyyMMdd + 4 位」不符 | `99-初始化数据.sql:1276-1283` | 单号规则在 C 内不自洽 |
| **H26** | **采购单与收货单无操作日志表** | 05-采购.sql 只有 4 表 | 无审计（与 V2 W4 纪律冲突） |
| **H27** | 采购单/收货单**无快照列**（仅 `create_user_name`） | DDL | 商品/供应商改名后历史单据失真 |
| **H28** | 4 个页面全部使用 `resizable` + `@resizeColumn` + `handleResizeColumn` | 各页 columns 定义 | V2 无 `TableHeaderCell` → 必须删（与 W4 同类） |
| **H29** | `purchase-item-list.vue` 是**独立明细列表页** | 全文 | 应降级为表单内可编辑表格（同 W4 的 order-item-list） |
| **H30** | `purchase-list.vue` 用 `a-input-number` 直接填 `supplierId` / `buyerId` | 第 8 / 87 行 | 无供应商/员工选择器 |
| **H31** | C **无仓库主数据、无仓库页面** | 全仓 grep `warehouse` → 仅库存域列名 | 仓库概念缺失 |

#### 5.3.2 C 与 A 源的建模冲突（**一律采 A 源**）

| 冲突点 | C 源 | A 源 | 采 |
| --- | --- | --- | --- |
| 采购单状态 | 5 值数字，含「待接单」 | 6 值字符串，含 `SHORT_CLOSED` | **A** |
| 超收 | 静默标记 `OVER` | 可配置容差 + 超出拒绝 | **A** |
| 供应商商品关系 | `t_product_supplier`（**SPU 级**） | `supplier_sku`（**SKU 级**） | **A**（且 V2 W2 已按 SKU 级落地） |
| 收货模式 | `directStock` 布尔 | `DIRECT`/`DEFERRED` + putaway | **A**（W5 只做 DIRECT） |
| 仓库 | 硬编码 `1L` | `warehouse` 主数据 + 单据快照 | **A**（Q1） |
| 采购需求 | 无需求实体（直接生成采购单） | `purchase_demand` + allocation + 来源追溯 | **A** |
| 采购单价来源 | `t_product_supplier.supply_price` | `supplier_sku.reference_price`（仅参考，需人工确认） | **A** |
| 单号 | SmartAdmin `SerialNumberService`（内存锁） | PG sequence | **A**（Q8） |

### 5.4 C 的可复制资产（**仅前端**）

| 资产 | 复制方式 | 适配要点 |
| --- | --- | --- |
| `purchase-list.vue`（349） | 复制 → 剪枝 → 适配 | 6 状态枚举、供应商/仓库选择器、补 version、去 `actual_amount`、补 submit/cancel/short-close、删 `resizable` |
| `purchase-receive-list.vue`（384） | 复制 → 剪枝 → 适配 | `DIRECT` 模式、容差提示、去 `receive_flag` 改 `over_receipt_quantity`/`receipt_difference`、补 confirm、删 `resizable` |
| `purchase-item-list.vue`（345） | **降级为表单内可编辑表格组件** | 修 H13/H29 |
| `purchase-generate.vue`（143） | 复制 → 适配 | 汇总来源改 `sales_order`、去库存抵扣、改 API |
| `purchase-const.ts`（72） | 骨架 | 5 值数字 → 6 值字符串；新增 `RECEIPT_MODE`；删 `SUPPLIER_STATUS`/`INQUIRY_STATUS` |
| `api/business/purchase/*.ts`（6） | 骨架 | `/purchase/**` → `/scm/purchase/**`；补 `Idempotency-Key`；补 `version` |
| `inquiry-list.vue`（662） | **不复制** | 询价不属于 W5（Q9） |
| `supplier-list.vue`（328） | **不复制** | W2 已有 `supplier-list.vue`（V2 版本） |

---

## 6. V2 已提供的正式契约（**不允许再造第二套**）

### 6.1 Supplier + SupplierSku（W2，`V8__scm_customer_supplier.sql`）

```text
supplier        id · supplier_code · name · status(ENABLED/DISABLED) · contact_name · contact_phone
                · address · remark · version · deleted · 审计列
supplier_sku    id · supplier_id · sku_id · supplier_code_snapshot · supplier_name_snapshot
                · sku_code_snapshot · sku_name_snapshot · spec_values_snapshot JSONB
                · purchase_unit · reference_price NUMERIC(18,4) · purchaser_id · is_default BOOLEAN
                · status(ENABLED/DISABLED) · version · deleted · 审计列
                uk_supplier_sku_active = (supplier_id, sku_id) WHERE deleted = FALSE
                idx_supplier_sku_purchaser = (purchaser_id) WHERE deleted = FALSE AND purchaser_id IS NOT NULL
```

**逐列比对结论：V2 的 `supplier_sku` 与 A 源 V8 的 `supplier_sku` 结构一致**（列名、类型、约束、索引全部对应）。

→ **W5 必须直接复用**：`supplier_id` / `sku_id` / `purchase_unit` / `reference_price` / `purchaser_id` / `is_default`。
**W5 不得新建任何「供应商-商品」主数据表**（回应用户 §4）。
W2 已知约束：**允许同一供应商多条 `is_default = TRUE`**（R12），**禁止**加唯一默认约束。

**W2 现有服务**（可直接注入）：`SupplierService` · `SupplierSkuService` · `SupplierQueryService` ·
`SupplierValidator` · `SupplierSkuSyncManager` · `OrderableSkuVO`（"可采购 SKU"查询）

### 6.2 Product / SKU（W1，只读）

```text
product_spu / product_sku（含 product_type STANDARD|NON_STANDARD、spec_values JSONB、shelf_status）
W5 只读：spu_code / product_name / sku_code / sku_name / spec_values / product_type / 采购单位来源 supplier_sku.purchase_unit
```

### 6.3 Pricing（W3，`module/scm/pricing/**`）

`PriceSource` 严格 `{AGREEMENT, CUSTOMER_TYPE, MARKET}`；半开区间 `[from, to)`；命中即返回、逐 SKU 独立、不叠加。
**W5 不调用 Pricing**：采购价与销售价是两个独立概念，A 源用 `supplier_sku.reference_price` + 人工确认单价。

### 6.4 Sales Order（W4，`V13__scm_sales_order.sql`）

W5 需要读取的字段（**只读，不写**）：

```text
sales_order        id · order_no · customer_id · status(DRAFT/PENDING/CONFIRMED/CANCELLED)
                   · confirmed_at · ordered_total_amount · deleted
sales_order_item   id · order_id · spu_id · sku_id · spu_code_snapshot · product_name_snapshot
                   · sku_code_snapshot · spec_name_snapshot · spec_values_snapshot
                   · sale_unit_snapshot · product_type_snapshot
                   · ordered_quantity · actual_quantity · actual_quantity_source
                   · locked_unit_price · sort_order · deleted
```

**汇总来源口径（A 源）**：`status = 'CONFIRMED'` 且 `actual_quantity` 非空且 `> 0`；
`requiredQuantity = actual_quantity`（**取实数量，不是订购量**）。

### 6.5 基础设施（SmartAdmin Native First，**全部复用**）

| 能力 | V2 位置 | W5 用法 |
| --- | --- | --- |
| 认证 / 权限 | Sa-Token `@SaCheckPermission` | `scm:purchase:*` |
| 员工 / 操作者 | `ScmOperator.current()` → `userType:userId` | 所有写操作 |
| 权限码 / 菜单 | `t_menu` + `t_role_menu`（V7/V9/V11/V14 范式） | V16 |
| 统一响应 | `ResponseDTO` / `PageResult` | 全部 |
| 业务异常 | `ScmBusinessException` + `ScmErrorCode` + `ScmExceptionHandler` | 全部 |
| 乐观锁 | `@Version` + `ScmCommonErrorCode.VERSION_CONFLICT(40921)` | 全部可变实体 |
| 逻辑删除 | `@TableLogic(value="false", delval="true")`（仅 SCM Entity 显式声明） | 全部 SCM Entity |
| 定点数 | `ScmFixedScale4Serializer`（+ `nullsUsing`）/ `ScmStrictDecimalStringDeserializer` / `ScmDecimalStrings` | 全部数量金额 |
| JSONB | `JsonbStringMapTypeHandler` / `OrderJsonbTypeHandler` | 快照字段 |
| 分页 | `PageResult` + MP `Page` | 列表查询 |
| 字典 | SmartAdmin 字典模块（`/support/dict/*`） | **超收容差配置（Q5）** |
| 幂等 | `idempotency_record`（V13）+ `OrderIdempotencyService` 模式 | 6 个命令 |
| 操作日志 | `order_operation_log` 模式（全量 before/after + type 白名单 CHECK） | `purchase_operation_log` |
| 单号 | PG sequence + `OrderNumberGenerator.format(prefix, n)` | `purchase_order_no_seq` / `purchase_receipt_no_seq` |
| PG IT 基类 | `ScmW2PgITBase` / `ScmW3PgITBase` | W5 IT 基类 |

### 6.6 V2 **没有**的能力（W5 必须新建或延后）

| 能力 | V2 现状 | W5 处置 |
| --- | --- | --- |
| **仓库主数据** | **完全不存在** | **Q1：新建最小 `warehouse`** |
| 库存余额 / 流水 / 占用 | **完全不存在** | **W5 不建**，只定义契约（P26） |
| 采购需求 / 采购单 / 收货 | 完全不存在 | W5 新建 |
| 批次 / 保质期 | 不存在（G-03 已定不启用） | **W5 不做** |
| 采购退货 | 不存在 | **W5 不做**（Q9） |
| 询价比价 | 不存在 | **W5 不做**（Q9） |
| 二维码 / 供应商协同 | 不存在（SmartAdmin 有文件服务） | **W5 不做**（Q9） |

---

## 7. 文档事实源与冲突（K1–K12）

| 编号 | 冲突 | 双方 | 处置 |
| --- | --- | --- | --- |
| **K1** | W4 是否等于 Purchase | 仓库文档（`迁移审计报告:1007`、`root-promotion-report:152`）曾写 `W4 = purchase`；实际指令 `W4 = Sales Order`、`W5 = Purchase` | **已解决**：U9 已同步 `AGENTS.md`（`W4 = Sales Order (APPROVED)` / `W5 = Purchase`）。本审计复核确认一致 |
| **K2** | 单仓库 vs 单据必须绑仓库 | 需求 G-03「单仓库，`warehouse_id` 保留暂不使用」 vs A 源 `purchase_order.warehouse_id NOT NULL` | **G-03 讲的是「不做多仓」**，不是「不绑仓库」。W5 建单仓库主数据 + 单据绑定（Q1） |
| **K3** | 收货单流程 | spec §4.4 `DRAFT → CONFIRMED` vs V10 `PARTIALLY_CONFIRMED` vs P1「独立收货记录」 | 采 **P1**（Q7） |
| **K4** | 超收 | spec §12「严格禁止超收」 vs V33「容差内允许」 vs C「静默标记」 | 采 **V33**（Q3） |
| **K5** | 少收关单 | spec §12「少收不能人工关单」 vs V27/P1「可少收关单」 | 采 **V27/P1** |
| **K6** | 操作者 | A 源 spec §2.2「操作者沿用 `SYSTEM`」 vs V2 `ScmOperator.current()` | 采 **V2**（Native First） |
| **K7** | 采购价来源 | 需求 05 §5.1「商品预设默认供应商/采购员直接带出」 vs A 源「不自动选择默认供应商，必须人工明确」 | 采 **A 源**（spec §12 明文「多供应商选择：延期」） |
| **K8** | 采购汇总维度 | 需求 05 §5.1「按供应商/采购员/品类」 vs A 源「按供应商 + 仓库（每供应商一张单）」 | 采 **A 源**；C 的 `category_id` 不迁 |
| **K9** | 采购单号规则 | 需求 G-01「`前缀 + yyyyMMdd + 4 位流水`，**按日重置**」 vs A 源「PG sequence **全局单调递增** + 至少 6 位」 | 采 **A 源**（与 W4 的 `SO` 单号纪律一致，且 G-01 的「按日重置」在并发下不安全）→ 需在 Q8 明确 |
| **K10** | 重量精度 | 需求 05 §6「重量 `DECIMAL(18,3)` kg」 vs A 源 `NUMERIC(18,4)` vs V2 冻结 4 位 | 采 **V2 冻结 4 位**（Q9 子项）；前端展示可按需截断 |
| **K11** | 应付生成时点 | 需求 G-02/09-02「收货入库后即生成应付」 | **W5 不做**（财务域），登记为 W7+ |
| **K12** | 采购需求生成触发 | 需求 05-01「实时/定时/手动待确认」 vs A 源「显式命令 + 订单 ID 列表」 vs P1「半开时间段」 | 采 **P1 半开时间段 `[startAt, endAt)`**（Q6 子项） |

---

## 8. 盲区与无事实源（G1–G14）

| 编号 | 盲区 | 说明 |
| --- | --- | --- |
| **G1** | 采购单「取消已部分收货」的处理 | 需求 05 §6 明确要求，**A 源不可表达**（`PARTIALLY_RECEIVED` 不允许取消）；A 源用 `SHORT_CLOSED` 替代。→ W5 采 `SHORT_CLOSED`，把「冲销」登记为后续 |
| **G2** | 超收容差的**审批流** | 需求 05-02「允差范围、是否需审批」未定；A 源只做数值容差，无审批 |
| **G3** | 采购退货（退供应商）流程 | 需求 05-06 未定；A 源 spec §2.2 明确排除；C 源零实现 |
| **G4** | 供应商是否为系统用户 | 需求 05-03 未定；A 源 spec §2.2 明确排除供应商门户 |
| **G5** | 询价比价 | 需求 05-04 未定；C 有完整 Inquiry 模块，A 源无 |
| **G6** | 二维码协同与供应商回填 | 需求 05 §5.2 有描述，A 源无实现，C 源有 `qrcode_url` 字段但无实现 |
| **G7** | 「计算库存」扣减时点与并发口径 | A 源 P1 规格说「由专项设计确认，服务端不得静默扣减」；代码未实现（A-D3） |
| **G8** | 采购成本口径 | A 源 spec §5.5「若采购价口径尚未由财务确认，则只作为业务记录，不宣称完成成本核算」 |
| **G9** | 实重（`actual_weight`）与数量（`received_quantity`）的**换算关系** | A 源两者并存但不做换算（非标品直接把 `actualWeight` 当 `effectiveQuantity`）；单位换算规则无事实源 |
| **G10** | 采购单是否允许多仓库行 | A 源「同一采购单所有行必须匹配单头仓库」；C 源无仓库概念 |
| **G11** | 收货单行是否允许部分提交确认 | A 源允许（`validateLines` 只校验请求中出现的行）；语义未在任何文档定义（A-D5） |
| **G12** | `planned_arrival_date` 与 `expect_arrive_time` 的取舍 | A 源用 `DATE`；C 源用 `TIMESTAMP` + 名称 `expect_arrive_time` |
| **G13** | 采购单列表默认排序与分页规模 | 无事实源，按 V2 惯例（`created_at DESC`） |
| **G14** | W5 是否需要「采购员数据权限」 | 需求未提；A 源有 `purchaser_id` 字段但无过滤逻辑 |

---

## 9. 前置裁决项与推荐取值

> 以下 12 项**必须在编码前由人工拍板**。每项给出推荐值与理由。target-design §14 是同一份清单的批准表。

### 9.1 Q1 — 仓库主数据（**W5 第一道门**）

**事实**：A 源 `purchase_order.warehouse_id NOT NULL` + 仓库编码/名称快照；库存粒度 `warehouseId + skuId`；
C 源硬编码 `warehouseId = 1L`；**V2 完全没有仓库**；需求 G-03 已定「单仓库、不启用批次」。

**选项**

| 选项 | 内容 | 后果 |
| --- | --- | --- |
| **A（推荐）** | W5 建最小 `warehouse`（`warehouse_code` / `name` / `status` / `address` / `remark` + 审计列），种子 1 个默认仓库；提供只读列表 + 最小 CRUD；采购单/收货单绑仓库并快照 | 与 A 源一致；W6 库存域可直接复用；单仓库约束用**种子数据**表达而非硬编码 |
| B | W5 不建仓库，采购单不绑仓库 | 偏离 A 源；W6 必须回头改 `purchase_order` 加列（违反「迁移只追加」的成本）；且 G-03 说「字段保留暂不使用」指的是**多仓能力**，不是「没有仓库」 |
| C | W5 建仓库但采购单不绑 | 仓库成为孤儿主数据，无意义 |

**推荐 A**。理由：A 源 + W6 库存域 + G-03 的「单仓库」语义三者都要求「有仓库、只有一个」。
用**种子数据 + 前端不提供新建入口**（或提供但默认禁用）表达单仓库，比硬编码 `1L` 可审计得多。

### 9.2 Q2 — 采购单状态机

**推荐 A 源 6 状态**：`DRAFT / SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED / CANCELLED`。

理由：(1) A 源是业务规则唯一事实源；(2) C 的 `PENDING(待接单)` 是供应商接单语义，而 W5 不做供应商门户（G4）；
(3) C 的 `CANCELLED`/`INVALID` 是不可达死状态；(4) `SHORT_CLOSED` 有 V27 迁移 + P1 规格 + `shortClose` 实现三重支撑，
且它正好填补 G1（取消已部分收货）的语义空缺。

**子裁决 Q2a**：W5 是否实现 `SHORT_CLOSED`（少收关单）？**推荐实现**——零库存依赖、A 源完整定义、
且不做的话「部分收货后无法终止采购单」会成为运营死角。

### 9.3 Q3 — 超收规则

**推荐 A 源口径**：容差百分比配置（默认 10，范围 0–100），
`累计有效收货 ≤ planned × (1 + tolerance/100)`，**超出整笔回滚**（不部分接受）。
C 源的「不拒绝 + 静默标记」会直接导致「账面采购量与库存入库量不一致」，不可采。

**子裁决 Q3a**：容差配置载体。
**推荐用 SmartAdmin 字典**（`t_dict` + `/support/dict/*`）而不是 A 源自建 `sys_config`。
理由：`sys_config` 与 SmartAdmin 字典职责重叠，新建即为「第二套配置」；且 V2 已在
`SMARTADMIN_REFERENCE_RULES.md` 里锁定「SmartAdmin Native First」。

### 9.4 Q4 — 收货模式

**推荐 W5 只实现 `DIRECT`**。理由：`DEFERRED` 的 `putaway` 命令**必须写库存**才能完成，
而 W5 明确不实现库存 → `DEFERRED` 在 W5 内**必然留下无法收尾的 `PENDING_PUTAWAY`**。

**关于 `receipt_mode` / putaway 三列是否建 —— 设计阶段口径已收紧（以 target-design §5.4 为准）**

审计阶段曾倾向「字段照建（默认 `DIRECT` / `NOT_APPLICABLE`），W6 追加 `DEFERRED` 时免改表」。
**目标设计阶段推翻此建议**，改为 **4 列全部不建**，理由：

1. `receipt_mode` 在 W5 恒为 `DIRECT` → 是**死字段**，与 W4 拒绝 `fulfillment_status` 的纪律直接冲突；
2. `putaway_status` / `putaway_at` / `putaway_operator` 描述的是**库存投递状态**，属库存域语义；
   W5 不实现库存 → 建了必然写假值；
3. 「迁移只追加」约束的是**已应用的迁移不可改**，**不是**「不许在未来迁移里 ALTER ADD COLUMN」——
   W6 用 `ALTER TABLE ... ADD COLUMN` 追加即可，代价为零。

**子裁决 Q4a**：`receipt_mode` 是否在 W5 暴露给前端？**不暴露**（W5 根本没有这个字段）。

### 9.5 Q5 — Inventory 边界（**用户最强调，本审计的硬立场**）

**推荐口径**

```text
1. Receiving = Purchase 聚合内部命令（同一模块 module/scm/purchase/**）
   —— 不建独立 receiving 包，不建独立 Receiving 聚合根服务
   —— 但 purchase_receipt / purchase_receipt_item 是独立表（有自己的单号与版本）

2. Inventory 从「收货确认（confirm）」开始拥有库存事实
   —— W5 不实现库存：不建余额表、不建流水表、不实现占用/出库
   —— W5 只定义 PurchaseInventoryContract + NoOpPurchaseInventoryContract（零调用点）

3. 逐问回答（用户指令 §3 的 7 个问题）
   创建采购单  → 不影响库存（只写 purchase_order / _item / allocation）
   提交采购单  → 不影响库存（只改 status + submitted_at）
   收货（confirm）→ W5 只产生「已收货事实」（received_quantity / cumulative / difference）
                    库存事实由 W6 的库存域在消费「收货确认」时创建
   部分收货    → 累计在 purchase_order_item.received_quantity
                + purchase_receipt_item.cumulative_received_quantity（同事务、乐观锁）
   重复收货    → Idempotency-Key + idempotency_record（scope = PURCHASE_RECEIPT_CONFIRM:{id}）
                同一 key 同 hash 返回原结果；同 key 异 hash → 409
   超收        → 容差内允许；超出整笔回滚（Q3）
   退货        → W5 不做（G3）
   warehouse_id→ 属于「采购单」（收货单继承并快照）；库存域只做引用，不定义仓库
   batch / lot → W5 不做（G-03 已定不启用）
```

**为什么 W5 不能「顺手」实现库存**：(1) 用户指令明文禁止；(2) 库存域需要余额、流水、
加权平均成本、占用/释放、盘点/报损等一整套，任何「临时余额表」都会成为 W6 的迁移债；
(3) W4 已建立 `OrderInventoryContract` + `NoOpOrderInventoryContract` 的先例，
W5 用同一模式即可保持架构一致。

### 9.6 Q6 — 采购需求（汇总生成）是否属于 W5

**推荐属于 W5**，理由：它是「以销定采」的核心（`AGENTS.md` §7.4 链路的第一段），
且 A 源有完整实体 + 唯一索引 + 分配算法 + 测试。

**但必须做三处剪枝**

```text
(a) 不迁 purchase_demand_generation_batch（A-D1 死表）→ 幂等改用 idempotency_record
(b) 不迁 fulfilled_quantity（A-D2 死字段）→ 状态只保留 PENDING / PARTIALLY_ALLOCATED / ALLOCATED
(c) 不迁 calculate_inventory 库存抵扣（A-D3 未实现 + G7 无口径）→ 建议量 = 需求量
```

**子裁决 Q6a**：生成入参采「订单 ID 列表」（A 源代码）还是「半开时间段 `[startAt, endAt)`」（P1 规格）？
**推荐半开时间段**——P1 是更晚的口径，且运营上是「按天汇总」而不是「逐单选单」。

**备选**：若用户希望压缩 W5 规模，可把采购需求整体延后到 W5.5，W5 只做「手工建采购单」。
本审计**不推荐**——那会让 W5 失去「以销定采」的核心价值。

### 9.7 Q7 — 收货单确认口径

**推荐「一单一次确认」**（spec §4.4 的 `DRAFT → CONFIRMED`），**不建** `purchase_receipt_confirmation`
与 `purchase_receipt_confirmation_item` 两张表，幂等直接用 W4 的 `idempotency_record`。

理由：(1) P1 规格明确「一张采购单可有多张**独立**收货记录」「查询应区分历史确认批次与 P1 独立收货记录」——
A 源自己承认两套并存，且 P1 更晚更权威；(2)「多次收货」的语义已由「一采购单多收货单」完全满足
（AGENTS.md §7.4 只要求这一点）；(3) 去掉 2 张表 + 1 个确认批次序列，显著降低 W5 复杂度与迁移风险。

**子裁决 Q7a**：`purchase_receipt.status` 是否保留 `PARTIALLY_CONFIRMED`？
**推荐不保留**（只 `DRAFT` / `CONFIRMED`），并把 A-D5 的语义瑕疵一并修掉：
状态按**本收货单**语义判定（本单所有行已提交 → `CONFIRMED`）。

### 9.8 Q8 — 单号机制

**推荐 PG sequence**（`purchase_order_no_seq` / `purchase_receipt_no_seq`），
格式 `PO` / `PR` + `yyyyMMdd`（Asia/Shanghai）+ 至少 6 位、超 999999 自然扩位，
**全局单调递增、不按日重置**，与 W4 的 `OrderNumberGenerator.format(prefix, n)` 完全一致。

理由：A 源即如此（`PurchaseOrderNumberGenerator`）；V2 的 `SerialNumberService` 唯一 `@Service` 实现是
`SerialNumberInternService`（**内存锁 + `ConcurrentHashMap`**）→ 多实例部署会生成重复单号；
另有 `SerialNumberMysqlService`（名字即 MySQL 专用）；且 PG 收口已发现
`SerialNumberRecordDao.selectRecordIdBySerialNumberIdAndDate` 是 dead code。
→ 偏离 Native First 的理由必须写进设计（target-design §7.6）。

**子裁决 Q8a**：前缀是否沿用 A 源（`PO` / `PR`）？**推荐沿用**（避免与 W4 的 `SO`/`RT`/`RF` 混淆）。

### 9.9 Q9 — 明确排除项（4 项）

| 项 | 事实 | 推荐 |
| --- | --- | --- |
| 采购退货（退供应商） | 需求 05-06 未定；A 源 spec §2.2 排除；C 源零实现 | **不做** |
| 无单收货（`NO_ORDER` + `relatePurchase`） | **A 源无**；C 源独有 | **不做**（无事实源 + 会产生无来源库存事实） |
| 询价比价（`t_inquiry*`） | **A 源无**；C 源独有（662 行页面） | **不做**（需求 05-04 未定） |
| 二维码 / 供应商协同 | 需求 05 §5.2 有描述；A 源无实现；C 源有 `qrcode_url` 字段无实现 | **不做**（G6） |

### 9.10 Q10 — 数据权限

**推荐 W5 不实现**（同 W4 的 U7）。A 源有 `purchaser_id` 但无过滤逻辑；
需求未提；V2 的数据权限基础设施在 SmartAdmin 侧，SCM 域接入需单独设计。

### 9.11 Q11 — 错误码段分配

**A 源与 V2 W4 大面积撞码**（§3.10）。**推荐重分配**：

```text
采购域 BAD_REQUEST  40080 – 40089
采购域 NOT_FOUND    40480 – 40489
采购域 CONFLICT     40980 – 40999
复用不冲突         40921(VERSION_CONFLICT) · 40000(VALIDATION_ERROR)
                   40430/40930/40949/40030（W1–W3 已占用，语义不同则不复用）
```

### 9.12 Q12 — 文档同步

**推荐同步更新**：`AGENTS.md` 的 Current delivery status 块（`W5 Purchase` → `APPROVED` / `COMPLETE`）、
`docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md` 的 Roadmap 表、以及本项目 `MEMORY.md`。
理由：W4 阶段已因未同步产生过「文档自相矛盾」（K1），不应重演。

### 9.13 Q13 — 采购单行 ↔ 采购需求的基数（**审计过程中发现**）

**事实**：spec §4.2 写「允许一条采购单行分配多条需求，也允许同一需求拆分到多条采购单行」，
`uk_purchase_demand_allocation_source_active = (purchase_order_item_id, purchase_demand_id)` 也确实允许 N:N。
**但 A 源代码只能处理 1:N**：

```java
// PurchaseOrderService.reconcileItems —— 按 itemId 建索引，多 allocation 会互相覆盖
var allocationByItemId = new HashMap<Long, PurchaseDemandAllocationEntity>();
for (var allocation : existingAllocations) {
    allocationByItemId.put(allocation.getPurchaseOrderItemId(), allocation);   // ← 覆盖写
    oldTotals.merge(allocation.getPurchaseDemandId(), allocation.getAllocatedQuantity(), BigDecimal::add);
}
// 后续 `allocationByItemId.get(item.getId())` 只能取到「最后一个」allocation
```

→ **A 源「规格说 N:N，代码只能 1:N」**（登记为 A-D23）。

**三个候选模型**

| 模型 | 行身份 | 需求↔行 | DB 约束 | 代价 |
| --- | --- | --- | --- | --- |
| **M1（推荐）** | `(purchase_order_id, sku_id)` 唯一 | 一需求 → 一行；一行 → 多需求 | `uk_purchase_order_item_order_sku_active` + `uk_purchase_demand_allocation_source_active(item_id, demand_id)` | 放弃「同一需求拆到多行」（无需求文档支撑） |
| M2 | 一行一需求 | 1:1 | 两个唯一键 | 一行一需求会让「同 SKU 多客户需求」产生多行 → 破坏聚合根的行身份 |
| M3 | 无行级唯一 | N:N | 只有 allocation 唯一键 | **必须修 `reconcileItems`** 为 `Map<(itemId,demandId), allocation>`，并解决「同一行内多需求的数量合并语义」 |

**推荐 M1**。理由：(1) 它是唯一能用 DB 唯一索引真正强制不变量的模型；
(2) 与 W4 的 `uk_sales_order_item_order_sku_active (order_id, sku_id)` 完全同构，跨波次一致；
(3) 「同一需求拆分到多行」没有任何需求文档支撑（需求 05 §5.1 只说「按维度汇总生成采购单」），
且与 P10「同一需求必须落在同一 (supplier, warehouse)」叠加后实际不可表达；
(4) 让 `reconcileItems` 的差量同步逻辑天然正确（行身份 = SKU）。

**注意**：采 M1 后，`PurchaseOrderService.reconcileItems` 中「同一 demandId 在多行出现则 SKU 必须一致」
的校验变为**不可达**（一行一 SKU 已由唯一键保证），可删除。

---

## 10. 审计结论

### 10.1 可复用性总评

| 源 | 后端可复用度 | 前端可复用度 |
| --- | --- | --- |
| **A** legacy | **规则 100% 复用，代码 0% 复用**（分层、错误码、`SYSTEM` 操作者、自建配置表、库存耦合全部不合 V2） | 0%（React） |
| **B** V2 | **基础设施 100% 复用**（Supplier/SupplierSku/幂等/日志/单号/定点数/权限/异常） | 100%（作为 Copy First 的目标形态） |
| **C** | **0% 复用**（PG 上直接报错：H1/H2/H3） | **4 个采购页面（1567 行）+ 常量骨架**可复制 |

### 10.2 W5 的边界（一句话）

> **W5 交付「采购需求 → 采购单 → 多次收货 → 累计差异」的完整可审计闭环，
> 并在收货确认处停止；库存事实归 W6，W5 只留一个零调用点的契约。**

### 10.3 交付物

```text
docs/architecture/2026-09-16-w5-purchase-audit.md          （本文件）
docs/architecture/2026-09-16-w5-purchase-target-design.md  （目标设计）
```

### 10.4 编码前必须完成

```text
1. 人工批准 target-design §14 的完整裁决表（Q1–Q17 + Q2a/Q3a/Q6a/Q7a/Q8a，共 22 项）
   —— 本审计 §0.2 的 15 项是它的子集，编号已对齐
2. 按 Q11 确认错误码段
3. 按 Q6 确认采购需求是否属于 W5（若否，W5 规模显著缩小）
4. 按 Q7 确认收货单确认口径（决定是否建 2 张 confirmation 表）
5. 按 Q5 确认 Inventory 边界（W5 零库存实现）
```

---

## 附录 A：审计证据索引

### A.1 A 源（legacy `95a5423`）—— 命令可复现

```bash
cd "D:/Browser Download/xsy-scm"

# 采购域文件全集
git ls-tree -r --name-only 95a5423 | grep -E 'scm/(purchase|inventory|supplier)/'
git ls-tree -r --name-only 95a5423 | grep -icE 'purchase|receiv'        # 130

# 迁移
git ls-tree -r --name-only 95a5423 | grep -E 'V(8|9|10|11|27|28|33)__'

# 枚举
git cat-file -p 95a5423:xsy-scm-server/src/main/java/com/xianshuyuan/scm/purchase/entity/PurchaseOrderStatus.java
git cat-file -p 95a5423:.../purchase/entity/PurchaseReceiptStatus.java
git cat-file -p 95a5423:.../purchase/entity/PurchaseReceiptMode.java
git cat-file -p 95a5423:.../purchase/entity/PurchaseReceiptPutawayStatus.java
git cat-file -p 95a5423:.../purchase/entity/PurchaseDemandStatus.java
git cat-file -p 95a5423:.../purchase/entity/ReceiptWeighingSource.java
git cat-file -p 95a5423:.../inventory/entity/InventoryMovementType.java

# 核心服务
git cat-file -p 95a5423:.../purchase/service/PurchaseOrderService.java               # 770 行
git cat-file -p 95a5423:.../purchase/service/PurchaseReceiptApplicationService.java  # 499 行
git cat-file -p 95a5423:.../purchase/service/PurchaseDemandService.java              # 213 行
git cat-file -p 95a5423:.../inventory/service/InventoryApplicationService.java       # 62 行
git cat-file -p 95a5423:.../inventory/service/PurchaseInCommand.java                 # 12 行

# 错误码与单号
git cat-file -p 95a5423:.../purchase/service/PurchaseOrderErrorCodes.java
git cat-file -p 95a5423:.../purchase/service/PurchaseReceiptErrorCodes.java
git cat-file -p 95a5423:.../purchase/service/PurchaseDemandErrorCodes.java
git cat-file -p 95a5423:.../purchase/service/PurchaseOrderNumberGenerator.java
git cat-file -p 95a5423:.../purchase/service/PurchaseReceiptNumberGenerator.java

# 设计规格
git cat-file -p 95a5423:docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md   # 548 行
git cat-file -p 95a5423:docs/superpowers/specs/2026-09-11-p1-purchase-receiving-upgrade.md                 # 21 行

# A-D1 死表证明（零调用点）
for f in $(git ls-tree -r --name-only 95a5423 | grep '\.java$'); do
  git cat-file -p "95a5423:$f" 2>/dev/null | grep -q "PurchaseDemandGenerationBatchMapper" && echo "$f"
done
# → 无输出

# 可达性证明
git merge-base --is-ancestor 95a5423 803a862 && echo YES
```

### A.2 B 源（V2）关键路径

```text
xsy-scm-server/sa-admin/src/main/resources/db/migration/V8__scm_customer_supplier.sql
  → supplier（99-119 行）· supplier_sku（126-154 行）· customer_type 种子（160-163 行）
xsy-scm-server/sa-admin/src/main/resources/db/migration/V13__scm_sales_order.sql
  → sales_order（7-42）· sales_order_item（44-101）· order_operation_log（114-129）
    · idempotency_record（131-146）· 索引（211-235）
xsy-scm-server/sa-admin/src/main/resources/db/migration/V14__scm_sales_order_permissions.sql
  → 22 条 t_menu（601-641）+ t_role_menu + setval（菜单 id 范式）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/common/**
  → ScmOperator · ScmErrorCode · ScmCommonErrorCode · ScmBusinessException
    · ScmExceptionHandler · ScmFixedScale4Serializer · ScmStrictDecimalStringDeserializer
    · ScmDecimalStrings
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/support/**
  → OrderInventoryContract · NoOpOrderInventoryContract（W5 契约的先例）
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/service/OrderNumberGenerator.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/service/OrderIdempotencyService.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/supplier/service/SupplierSkuService.java
xsy-scm-web/src/api/business/scm/order-api.ts（Provenance 头范式）
tools/ts_baseline_ratchet.py（SCM_PREFIXES 白名单）
tools/verify_w4_legacy.py（断言 A–I 范式）
```

### A.3 C 源关键路径

```text
project-reference-examples/xsy-scm/postgresql/05-采购.sql
  → t_purchase_item（22-58）· t_purchase_order（63-103）· t_receive（108-148）· t_supplier（153-185）
project-reference-examples/xsy-scm/postgresql/99-初始化数据.sql
  → 菜单 900-944（334-358）· 种子 t_purchase_item/order/receive（1264-1289）· setval（1384-1386）
project-reference-examples/xsy-scm/xsy-scm-server/.../purchase/constant/*.java
  → PurchaseStatusEnum · PurchaseItemStatusEnum · ReceiveStatusEnum · ReceiveFlagEnum
    · ReceiveTypeEnum · SupplierSortModeEnum · InquiryStatusEnum
project-reference-examples/xsy-scm/xsy-scm-server/.../purchase/service/ReceiveService.java        # 344 行
project-reference-examples/xsy-scm/xsy-scm-server/.../purchase/service/PurchaseOrderGenerateService.java  # 189 行
project-reference-examples/xsy-scm/xsy-scm-server/.../purchase/service/PurchaseOrderService.java  # 116 行
project-reference-examples/xsy-scm/xsy-scm-server/.../purchase/service/PurchaseItemService.java   # 125 行
project-reference-examples/xsy-scm/xsy-scm-server/.../resources/mapper/business/purchase/*.xml
project-reference-examples/xsy-scm/docs/requirement/05-采购管理.md
project-reference-examples/xsy-scm/docs/requirement/06-库存管理.md
project-reference-examples/xsy-scm/docs/requirement/00-总览与通用约定.md（§13 G-01…G-08）
```

### A.4 仓库内文档

```text
AGENTS.md                                          §7.4 Purchasing / §7.5 Inventory
SMARTADMIN_REFERENCE_RULES.md                      SmartAdmin Native First
docs/architecture/2026-09-16-w4-order-target-design.md     结构范式 + Inventory 契约先例
docs/architecture/2026-09-16-w4-order-audit.md             A/C 源审计方法范式
docs/architecture/2026-09-14-w1-product-target-design.md   W1 范式
docs/architecture/2026-09-15-w2-customer-supplier-target-design.md  W2 范式（supplier_sku 来源）
docs/architecture/2026-09-15-w3-pricing-target-design.md   W3 范式
```

---

## 附录 B：不变量覆盖清单（W5 验收对账用）

| 编号 | 不变量 | 来源 | W5 落点（target-design 章节） |
| --- | --- | --- | --- |
| P1 | 采购单是聚合根，禁独立明细写入口 | A 源 spec §4.3 | §6.1 API · §7.8 |
| P2 | 草稿编辑保留原行 ID/SKU/需求来源 | A 源 `reconcileItems` | §7.8 |
| P3 | 省略且未收货的草稿行软删 + 释放需求 | A 源 spec §4.3 | §7.8 |
| P4 | 已有收货/已提交/终态不得编辑 | A 源 `update` | §4.1 状态表 |
| P5 | 必须人工明确供应商 + 仓库，全行匹配单头 | A 源 spec §4.2 | §7.2 · §7.4 |
| P6 | 金额服务端重算，客户端不得提交累计量 | A 源 spec §3.3 | §7.5 |
| P7 | 需求来源只能是 `CONFIRMED` 订单行 | A 源 `generateInternal` | §7.4 |
| P8 | 每个活动订单行至多一条活动需求 | `uk_purchase_demand_source_active` | §5.2 |
| P9 | 分配不得超需求 | A 源 `lockAndValidateDemands` | §7.4 |
| P10 | 同一需求 SKU / (supplier,warehouse) 一致 | A 源 `ALLOCATION_CONFLICT` | §7.4 |
| P11 | allocation 唯一键 + 数量冲突 | `uk_purchase_demand_allocation_source_active` | §5.3 |
| P12 | 需求按 demandId 升序锁定 | A 源 `sorted()` | §7.10 锁序 |
| P13 | 状态只经显式命令转换 | A 源 spec §4.3 | §4.1 |
| P14 | 仅 DRAFT/SUBMITTED 可取消 | A 源 `cancel` | §4.1 |
| P15 | 少收关单前置条件 | A 源 `shortClose` | §4.1 |
| P16 | 一采购单多收货单，不改历史 | A 源 spec §4.4 + P1 | §3.3 · §5.4 |
| P17 | 收货单创建不改库存 | A 源 `create` | §3.3 |
| P18 | 累计达计划 → RECEIVED；>0 未达 → PARTIALLY_RECEIVED | A 源 `confirm` | §4.2 |
| P19 | 已关单/已完成禁止新收货 | P1 规格 | §4.1 · §4.2 |
| P20 | 4 位定点，字符串传输，HALF_UP | V2 冻结 + A 源 | §7.5 |
| P21 | 标品用数量、非标品用实重作为累计实收 | A 源 `validateLines` | §7.5 |
| P22 | 计划数量永不覆盖 | A 源 spec §3.3 | §5.4 |
| P23 | 累计 ≤ planned × (1 + tolerance/100)，超出整笔回滚 | A 源 `validateLines` + V33 | §7.5 · §7.7 |
| P24 | remaining / over / difference 恒等式 | V28 `ck_..._reconciliation` | §5.4 |
| P25 | 6 命令幂等；同 key 异 hash → 409 | A 源 spec §6.6 | §7.9 |
| P26 | 库存只经库存业务层；W5 零库存实现，只定义契约 | 用户指令 §3 + AGENTS.md §7.5 | §8 |
