# W5 Purchase 审批记录

## 14. 最终审批（2026-09-16，全部已批准）

用户授权：**先按 6 项修订改写 Target Design，随后重跑文档内部一致性检查；若无新增需人工裁决项，
直接生成本文件并按 T0 → T23 实施，不再等待确认。**

修订后的规格依据：[`2026-09-16-w5-purchase-target-design.md`](./2026-09-16-w5-purchase-target-design.md)。
一致性检查结果：**通过**（22 项裁决在 §0.2 与 §14 编号完全一致且全部 APPROVED；错误码 40 个分段
12/6/22 勾稽一致；表 9 · 索引 31（唯一 7 / 部分 27 / 普通 4）· 序列 2 · 种子 2 与 DDL 实测一致；
端点 27 · 菜单 25 · 权限码 19 一致；附录 A 差异表 1–32 连续；无新增人工裁决项）。

### 14.1 六项修订（本次批准的核心变更）

| 编号 | 原设计 | 修订后（**已批准**） |
| --- | --- | --- |
| **Q3a** | 容差配置存 SmartAdmin **Dict** | 改用 **SmartAdmin Config**：`t_config`（V3 已建）+ `sa-base` 的 `ConfigService.getConfig(String)`；key `scm.purchase.over_receipt_tolerance_percent`；默认 `10`、范围 `0–100`；**缺失回退 10**、非法 → `PURCHASE_TOLERANCE_CONFIG_INVALID(40999)`；**V15 播种 1 条**（`ON CONFLICT (config_key) DO NOTHING`）。**禁新建 `sys_config`、禁新增 SCM 配置基础设施、禁修改 `sa-base`（`ConfigKeyEnum` 不扩充）** |
| **Q13** | `items[].demandId`（单需求），与 allocation 表 N:N 矛盾 | 保留「一行一 SKU（`uk_purchase_order_item_order_sku_active` 保留）」+ **改为 N allocations**：`items[] { id · version · skuId · quantity · price · allocations[] { demandId · quantity · demandVersion } }`；allocation 身份 = `(purchase_order_item_id, purchase_demand_id)`；retained reconciliation **按 allocation 集合对账**（`Map<(itemId,demandId), allocation>`），**禁止「一个 item 一个 allocation」算法**；新增 6 类必测用例 |
| **Q14** | `purchase_order_id` 可空 + 「至少一个 id 非空」CHECK | `purchase_order_id` 可空 + **operation-type-aware CHECK** `ck_purchase_operation_log_owner`：`DEMAND_GENERATE` **双 id 为空**；`DEMAND_ALLOCATE` **仅采购单 id**（由 `purchaseOrderItemId` 反查）；`CREATE/UPDATE/SUBMIT/CANCEL/SHORT_CLOSE/DELETE` 采购单 id 非空；`RECEIPT_*` **双 id 非空**。同步修正 §5.10 DDL、§7.4、§7.12、迁移测试 |
| **Q17** | 在 `allocate` 时用 `supplier_sku.purchase_unit` **覆盖**需求单位 | **需求单位与采购单位分离**：`purchase_demand.demand_unit_snapshot` ← `sales_order_item.sale_unit_snapshot`；`purchase_order_item.purchase_unit_snapshot` ← `supplier_sku.purchase_unit`。相等 → 允许自动分配；**不等 → 拒绝自动分配 `PURCHASE_UNIT_CONVERSION_REQUIRED(40971)`**。**禁止把 100 kg 仅改单位字符串变成 100 箱**；W5 不猜换算系数，换算能力另开波次。Q11 错误码数量/枚举/测试已重新计数 |
| **Q5** | 只定义契约，零调用点，零库存表 | 边界不变，**补 W6 Inventory bootstrap contract**：W6 首次启用库存前必须 **backfill/replay 全部历史 `purchase_receipt.status='CONFIRMED'` 的 `purchase_receipt_item`**；库存流水必须带稳定唯一源键 `source_document_type='PURCHASE_RECEIPT_ITEM'` + `source_document_item_id=receipt_item_id`（部分唯一索引兜底），使历史 backfill / 未来实时 confirm / 重试**不可重复入库**；W5 **不新增** `inventory_posted` 死字段 |
| **Q6a** | `demand_date = date(startAt)` | `demand_date = source_confirmed_at 在 Asia/Shanghai 下的 LocalDate`；`source_confirmed_at` 继续保存原订单确认时间并改为 `NOT NULL`；DB CHECK `ck_purchase_demand_date` 强制。字段名保留 `demand_date`（语义 = 来源订单确认日，**不是**生成日）。**禁止跨多日窗口把全部 demand_date 写成窗口第一天** |

### 14.2 其余 16 项（按 Target Design 推荐值批准）

| 编号 | 最终裁决 | 状态 |
| --- | --- | --- |
| Q1 | 建最小 `warehouse` 主数据（9 表之一）+ 1 条默认仓库种子；单仓库由种子 + 前端不提供新建入口表达 | APPROVED |
| Q2 | 采购单 6 状态 `DRAFT/SUBMITTED/PARTIALLY_RECEIVED/RECEIVED/SHORT_CLOSED/CANCELLED` | APPROVED |
| Q2a | 实现少收关单（`shortClose` + `SHORT_CLOSED` 终态） | APPROVED |
| Q3 | 可配置容差（默认 10，范围 0–100）+ 超出**整笔回滚** | APPROVED |
| Q4 | W5 只做 `DIRECT`；不建 `receipt_mode` / putaway 三列（W6 `ALTER` 追加） | APPROVED |
| Q6 | 采购需求属于 W5；剪枝 generation batch / `fulfilled_quantity` / 库存抵扣 | APPROVED |
| Q7 | 收货单一单一次确认；不建 `purchase_receipt_confirmation(_item)` 两表 | APPROVED |
| Q7a | 不保留 `PARTIALLY_CONFIRMED`；`confirm` 必须提交本单全部行 | APPROVED |
| Q8 | 单号 = PG sequence 全局单调递增、不按日 reset；`PO`/`PR` + `yyyyMMdd` + ≥6 位，超 999999 自然扩位 | APPROVED |
| Q8a | 前缀 `PO`（采购单）/ `PR`（收货单） | APPROVED |
| Q9 | 采购退货 / 无单收货 / 询价比价 / 二维码协同 **全部不做** | APPROVED |
| Q10 | 不做数据权限 | APPROVED |
| Q11 | 错误码段 `40080–40091`（12）· `40480–40485`（6）· `40971–40999`（22）= **40 个码**，与 W1–W4 零交集 | APPROVED |
| Q12 | 文档同步 `AGENTS.md` + 迁移审计报告 Roadmap + `MEMORY.md` + `AGENTS.md §6` 补 `warehouse` | APPROVED |
| Q15 | `receipt_weighing_record` 无 `version`/`deleted`（只追加审计事实） | APPROVED |
| Q16 | 删除 `receipt_weighing_record.scale_precision`（A-D4：A 源从不赋值） | APPROVED |

### 14.3 不可动摇的约束（本次实施必须同时满足）

```text
1. V1–V14 零修改（sha256 逐字节一致）；V15 / V16 只追加
2. SmartAdmin Native First（auth/authz、Config、Dict、log、ResponseDTO、分页、MP、Flyway 全部用原生）
3. Frontend Copy First + Adapt（C 已有采购页面复制 → 剪枝 → 适配，A1–A32 逐项核销）
4. project-reference-examples/xsy-scm/** 零修改
5. W1–W4 正式域零业务修改（product / customer / supplier / pricing / order / common）
6. W5 不实现库存：零库存表、零库存写入、PurchaseInventoryContract 零调用点
7. 采购单/收货单不做业务员数据权限；不新增 pay_status / actual_weight / receipt_mode / inventory_posted 等死字段
```

### 14.4 一致性修复（**非裁决项**，均为文档自相矛盾，按已批准裁决唯一推导）

重跑内部一致性检查时发现 6 处**自相矛盾**（不是新的设计选择，任何一处都不需要人工重新裁决，
因为按已批准的裁决只有一个自洽解）。已在 Target Design 中就地修正：

| # | 位置 | 矛盾 | 修正 |
| --- | --- | --- | --- |
| F1 | §5.5 | 文字声称「新增 3 条状态时间戳约束」，DDL 只写了 1 条；且那 1 条 `status='DRAFT' OR submitted_at IS NOT NULL` 会**拒绝「草稿直接取消」**（DRAFT→CANCELLED 时 `submitted_at` 仍为 NULL） | 补齐 3 条：`ck_purchase_order_submitted_at`（只对 `SUBMITTED/PARTIALLY_RECEIVED/RECEIVED/SHORT_CLOSED` 要求非空）、`ck_purchase_order_cancelled_at`、`ck_purchase_order_short_closed_at` |
| F2 | §5.3 vs §7.4 | `ck_purchase_demand_assignment` 要求「未分配时 supplier 与 warehouse 同时为空」，但 §7.4 的 `generate` **必填 warehouseId** → 新需求必然违反该 CHECK | CHECK 放宽为「`allocated_quantity = 0` 或两者非空」；并在 §7.4 写明：`warehouse` 由 generate 固定、`supplier` 由**第一次分配**固定 |
| F3 | §6.1 vs §6.2 | §6.1 写「1 条仓库种子」，§6.2 写「2 条种子」 | 统一为 **2 条种子**（`warehouse` 1 + `t_config` 1） |
| F4 | §7.2 | `PurchaseOrderVO` 列了 `confirmedAt`，但 `purchase_order` **没有** `confirmed_at` 列 | 删除该字段并注明收货完成时间见各收货单 |
| F5 | §2.1 / B.1 vs §7.7 | 模块树列了 `warehouse/constant/WarehouseErrorCode`，但 §7.7 把 2 个仓库码放在 `PurchaseErrorCode` 里 → `warehouse → purchase` 反向依赖（正是 §2.1 独立 `warehouse` 域要避免的） | 拆成两个枚举：`PurchaseErrorCode` **38** + `WarehouseErrorCode` **2** = **40**；`PURCHASE_WAREHOUSE_DISABLED` 留在采购侧（它是采购规则） |
| F6 | §0.2 / §7.7 | §0.2 的 Q11 行仍写「36 个码 / `40080–40089`」，§7.7 的段注释头也仍是 `40080–40089` | 统一为 40 个码与 `40080–40091` |

修正后**重跑**内部一致性检查：**全部通过**（表 9 · 索引 31（唯一 7 / 部分 27 / 普通 4）· 序列 2 · 种子 2 ·
端点 27 · 菜单 25 · 权限码 19 · 错误码 40（38+2）· 附录 A 1–32 连续 · §0.2 与 §14 的 22 项编号一致且全 APPROVED）。
**无新增需人工裁决项**，故按用户授权直接进入 T0 → T23。

---

实施与验收结果见 [W5 验收报告](./2026-09-16-w5-purchase-验收报告.md)。
本文件的 APPROVED 记录用户对设计与编码的授权：**批准后直接执行 T0 → T23，W5 验收完成后停止，不进入 W6。**
