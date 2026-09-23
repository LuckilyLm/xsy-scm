# AGENTS.md

> **SmartAdmin V2 底座规则（2026-09-14 生效）：**SmartAdmin 是 V2 的**正式系统底座**，
> 不是只读参考。登录、认证、用户、员工、部门、角色、菜单、权限、数据权限、日志、字典、
> 文件、统一异常、统一响应、前端 Layout 与系统页面全部采用 SmartAdmin；旧 `auth` / `system`
> 实现不迁移。V2 只迁 xsy-scm 供应链业务域，管理后台统一 Vue3 + TypeScript。
> V2 正式工作区固定为根目录下的 `xsy-scm-server/` 与 `xsy-scm-web/`；正式工具和文档分别位于
> `tools/` 与 `docs/`。`xsy-scm-miniapp` 仍为冻结的 legacy 小程序目录。
> 禁止机械复制旧代码，禁止机械把 React 翻译成 Vue。
> 完整规则见 [`SMARTADMIN_REFERENCE_RULES.md`](./SMARTADMIN_REFERENCE_RULES.md)。

> Project: 鲜蔬源智慧供应链管理平台  
> Short name: 鲜蔬源智链  
> Code name: `xsy-scm`  
> Version: v2.0  
> Scope: Global repository-level instructions for coding agents working on this project.

---

## 1. Project Mission

`xsy-scm` is a fresh-food supply-chain management platform covering:

- Product management
- Customer management
- Supplier management
- Marketing
- Sales orders
- Purchasing
- Receiving
- Inventory
- Sorting / weighing
- Delivery
- Finance and reports
- Mall / Mini Program
- Traceability
- Data screens
- Hardware integration, especially electronic scales

The core business chain is:

```text
Customer
→ Product / Customer Pricing
→ Mall Order / Admin Order Entry
→ Sales Order
→ Order Aggregation
→ Purchase Demand
→ Purchase Order
→ Actual-weight Receiving
→ Inventory
→ Sorting
→ Actual-weight Write-back
→ Shipment
→ Delivery
→ Sign-off
→ Receivable / Payable / Profit
```

All implementation decisions should preserve this end-to-end business chain.

### Current delivery status (2026-09-18)

```text
W0   baseline                              COMPLETE
W1   Product                               COMPLETE
W2   Customer + Supplier                   COMPLETE
W3   Pricing implementation/verification   COMPLETE
W4   Sales Order                           COMPLETE
W5   Purchase                              COMPLETE
W5.5 SmartAdmin Native Feature Parity      COMPLETE
F0   Object Storage Activation             COMPLETE
W6-1 Inventory (balance/movement/inbound)  BACKEND + BROWSER VERIFIED
W6-2 Mini Program                          NOT STARTED
```

W4 = Sales Order (COMPLETE).
W5 = Purchase (COMPLETE) — purchase demand, purchase order,
receiving and the minimal `warehouse` master data, with **no inventory implementation**.
W5.5 = SmartAdmin native feature parity sync (COMPLETE) — zero new migrations,
zero menu changes, zero permission changes; the formal V2 workspace already carried the native
system and support capabilities.
The W5.5 UI and branding increment added V18; migration history is verified from the SQL directory
and Git history when needed. The native-parity statement above refers only to the parity sync.
F0 = Object Storage Activation (**COMPLETE**) — strict fileKey
prefix policy, per-folder read guard with HTTP 403 + native 30005 envelope, S3/MinIO path-style
client and presigner, canned-ACL switch, short-TTL cache exclusion, four-profile cloud ENV
alignment, multipart 20/25 MB, data-only V17, and the `deploy/minio/` local development stack.
Historical verification details are no longer duplicated here; consult Git history and the reference
project when a new change needs them.
F0 adds no SCM business domain and no business attachment tables.
**F0-DEBT-01 (adjudicated 2026-09-21, FA-0 landed in V41)** — attachments are graded by
**directory prefix only**: `public/*` is anonymous-readable with static (non-expiring) URLs,
`private/*` requires authorization. Product images are **public assets** and now upload to
`public/image/` (`FileFolderTypeEnum.PUBLIC_IMAGE`). The **write side is closed**: binding a
`product_image` row to a fileKey the SPU does not already own must reference the public prefix,
otherwise the edit right would promote someone else's private attachment to all product viewers
(confirmed data uplift, not a single-record read). `product_image.file_url` was dropped —
presigned URLs are derived values and are always recomputed from `file_key`.
The **read-side serializer bypass is mitigated**: `FileKeyVoSerializer` now filters its comma-split
keys through `FileAccessGuard.filterReadable(...)` before `FileService.getFileList()` — the same
per-key policy as the Controller `checkRead` guard, but silently dropping unreadable keys instead of
failing the whole VO — and fails closed (empty list) when there is no authenticated caller or when
*either* injected dependency (`fileService` / `fileAccessGuard`) is unwired. No fallback branch may
echo the raw `value`: that would still disclose a private attachment's key and existence. So business
VO fields (e.g. `FeedbackVO`, `EnterpriseVO`) no longer hand back URLs
for attachments the caller may not read. This is a targeted mitigation, not the confirmed target
model, which remains a
**`scm_file_relation` table** (rights to the business object ⇒ rights to its files; upload to
scratch, bind to create relation rows). Before any non-administrator business role is introduced,
OA enterprise licences and similar COMMON private assets must move to business-permission +
ownership/relation + FileService reads. Note that local storage mode maps `/upload/**` statically
with no guard, so "private" is not confidential locally; permission behaviour must be verified in
object-storage mode. Plan: [`docs/plan/attachment-asset-grading-and-file-access-plan.md`](./docs/plan/attachment-asset-grading-and-file-access-plan.md);
decision rationale: [`docs/decisions.md`](./docs/decisions.md).
W6-1 = Inventory phase 1 (**BACKEND + BROWSER VERIFIED**) — `inventory_balance` +
`inventory_movement` (append-only ledger), `DIRECT` confirmation or `WAREHOUSE_CONFIRM` putaway →
`PURCHASE_IN`, the one-shot Q5 backfill of historical CONFIRMED receipt lines, warehouse enable/disable,
and two read-only query pages (balance / movement) with menu ids 800/801/802/811/821. B1 adds command
permissions 822/823/824 for receipt putaway and warehouse enable/disable.
Current scope and decisions: [`docs/progress.md`](./docs/progress.md) and [`docs/decisions.md`](./docs/decisions.md).
Non-negotiable invariants established by W6-1:
**Q13 inventory unit invariant** — one `(warehouse_id, sku_id)` locks exactly one bookkeeping unit;
a differing unit fails loudly with `INVENTORY_UNIT_MISMATCH(41001)` and rolls the whole confirm back,
and the backfill refuses to run (`RAISE EXCEPTION`) when historical lines mix units.
**Q7 append-only ledger** — V19's `CHECK (deleted = FALSE)` rejects soft deletion only.
The subsequent static-review fix V21 adds `trg_inventory_movement_append_only` to reject
UPDATE / DELETE / TRUNCATE. V21 is applied in the local PostgreSQL database; the 2026-09-18 backend regression verifies its constraints.
The DAO declares only insert + select; reversals must be NEW reverse movements.
**Q11** — the `ON CONFLICT (...) WHERE ... DO NOTHING` conflict targets match the partial unique
indexes verbatim, with zero PostgreSQL version branching in business code.
The 2026-09-18 backend and browser regressions passed for W6-1/B1. See `docs/progress.md` for current evidence and environment-only exclusions.
W6-1 explicitly excludes Mini Program, outbound/reserve, stocktake, loss/gain, transfer, unit
conversion, warning thresholds, full costing, delivery, sorting and traceability — none of them were
touched.
> **范围变更（2026-09-19）**：上述 excludes 是 **W6-1 当时的阶段范围控制，不是对需求的否决**。
> 除 Mini Program 外，其余能力（出库/预留、盘点、报损报溢、调拨、单位转换、阈值预警、
> 完整成本核算、分拣、配送、溯源）**现已全部重新纳入范围**，归属见
> [`docs/requirements/2026-09-19-需求覆盖与待办清单.md`](./docs/requirements/2026-09-19-需求覆盖与待办清单.md)。
> 推进原则：**先搭主线功能的后台模块，小程序（W6-2）排在最后**。
Finance R0 = 报表中心只读地基 (**BACKEND IT + FRONTEND + BROWSER E2E VERIFIED**, 2026-09-23) —
`module.scm.report`：五张只读分析页、41 个只读端点、11 个 Excel 导出，加 V50 菜单权限 / V51 日期轴索引。
**Zero new financial fact tables**: no receivable / payable / payment / voucher / report_snapshot, and no write
path or order state machine was touched. Naming is a hard boundary: 已确认订单金额
(`CONFIRMED + confirmed_at + settlement_*`) is never called 营业收入; 收货确认 ≠ 库存入账; 销售出库成本
is never attributed to an order; quantities never sum across units; 历史期初 / 期末成本 is not displayed,
because movements store the per-movement `unit_cost` rather than the post-change `avg_cost`. Cost columns
need the separate `scm:report:cost:query` and are masked to `null` (rendered `—`), never to `0`.
Functional permissions are verified; **formal per-role data scope is NOT claimed** (plan §33).

W6-2 = Mini Program — **NOT STARTED**; do not begin before the W6-1 open items in
[`docs/progress.md`](./docs/progress.md) are adjudicated.

The PostgreSQL Closure restriction against V13+ applies only to that completed phase.
W4 adds V13/V14, W5 adds V15/V16 and W6-1 adds V19/V20 normally; V1–V18 remain immutable.

```text
V17  V17__sa_config_file_upload_size.sql      F0     data-only, t_config 文件上传大小 30→20
V18  V18__scm_menu_icons.sql                  W5.5   data-only, t_menu 侧边栏图标（34 条 UPDATE）
V19  V19__scm_inventory.sql                   W6-1   inventory_balance + inventory_movement + Q5 backfill
V20  V20__scm_inventory_permissions.sql       W6-1   data-only, t_menu 库存菜单与权限（800/801/802/811/821）
V21  V21__scm_inventory_movement_append_only.sql W6-1 流水不可改删
V22  V22__scm_receipt_putaway_warehouse.sql B1 收货双入库生命周期、仓库严格停用与操作日志类型
V23  V23__scm_warehouse_putaway_permissions.sql B1 data-only，确认入库/仓库启停权限（822/823/824）
V24  V24__scm_table_column_comments.sql       chore 数据字典补全：15 表 + 323 字段 COMMENT，
                                                   并覆盖 V5 遗留的上游品牌列注释（data-only）
V25  V25__scm_inventory_outbound_reservation.sql outbound 库存出库与预留：SALES_OUT 流水类型、
                                                   reserved_quantity、出库单/预留三张表、
                                                   方向感知快照约束重建、菜单 803/804/812/813/825-829
V26  V26__scm_order_reserve_stock_permission.sql outbound data-only，订单「预留库存」权限（620）
V27  V27__scm_order_log_reserve_stock_type.sql  outbound 操作日志类型白名单加 RESERVE_STOCK
V28  V28__scm_screen_permissions.sql            B7     data-only，数据大屏隐藏目录 + 查询权限（900/901）
V29  V29__scm_inventory_stocktake.sql           stocktake 库存盘点：STOCKTAKE_GAIN/STOCKTAKE_LOSS
                                                   流水类型、四方向快照约束、盘点单两张表、
                                                   菜单 830-835
V30  V30__scm_inventory_loss_gain.sql           loss-gain 报损报溢：LOSS_REPORT/GAIN_REPORT
                                                   流水类型、六方向快照约束、报损报溢单两张表
                                                   （含审批状态机）、菜单 840-846
V31  V31__scm_inventory_transfer.sql            transfer 库存调拨：TRANSFER_OUT/TRANSFER_IN
                                                   流水类型、八方向分组快照约束、调拨单两张表
                                                   （两步式：发出 → 在途 → 收货）、菜单 850-856
V32  V32__scm_inventory_warning_threshold.sql   warning 库存阈值预警：阈值配置表（**独立于
                                                   余额表**）、预警列表（读时算状态）、
                                                   菜单 860-866
V33  V33__scm_inventory_conversion.sql          conversion 规格转换（整件拆零 / 组合拆分）：
                                                   CONVERT_OUT/CONVERT_IN 流水类型、
                                                   十方向分组快照约束、转换单两张表、
                                                   菜单 870-876
V34  V34__scm_inventory_avg_cost.sql            costing 移动加权成本：inventory_balance.avg_cost
                                                   （Q3 裁决变更，见迁移头注释）、期初按
                                                   「最近一次采购入库单价，无则 0」回填
V35  V35__scm_sales_order_import.sql             order Excel 导入来源 IMPORT
V36  V36__scm_sales_order_import_permission.sql  order Excel 模板下载/导入权限（642）
V37  V37__scm_inventory_reprice_zero_cost_balances.sql costing 数据修正：重放流水重算
                                                   「有货但 avg_cost = 0」余额行的移动加权均价
                                                   （只 UPDATE avg_cost，一行流水都不改）
V38  V38__scm_product_master_data_enhancement.sql product PCO-1 主档增强：product_spu 十二个主档
                                                   标量 + master_status（与 status 正交，归档必下架）+
                                                   CHECK，scm_uom 字典（24 条种子，活动行内编码/名称
                                                   各唯一，业务字段仍存名称快照），product_tag 与
                                                   product_tag_relation
V39  V39__scm_product_assistant_menu_permissions.sql product PCO-1 data-only，辅助资料页 405、
                                                   商品批量维护 417、单位/标签 CRUD 488-495，
                                                   仅授 SUPER_ADMIN
V40  V40__scm_geo_region_and_master_location.sql  map 地图 M0 地理数据地基：scm_region 省市两级字典
                                                   （34 省 + 414 市，中心为区划质心 GCJ-02，非地址坐标），
                                                   warehouse/customer/supplier 各加省市区编码+名称快照六列与
                                                   longitude/latitude/geom_crs（成对、取值与 CRS CHECK），
                                                   市级部分索引，末尾按自由文本地址做保守解析回填
V41  V41__scm_product_image_drop_file_url.sql     f0   F0-DEBT-01 写侧收口：删除 product_image.file_url
                                                   （预签名地址是带 TTL 的派生值，一律按 file_key 现算），
                                                   刻意不加 file_key 前缀 CHECK —— 存量私有行仍合法
V42  V42__scm_delivery_static_route.sql           delivery 物流配送 L0–L2：五张配送表（司机/车辆/线路/
                                                   停靠点/线路订单）、订单地址地理快照、坐标完整性 CHECK、
                                                   线路唯一/部分索引；原编号 V41，因与 F0 的 V41 撞号按
                                                   「保留已被真实库应用的版本号」先例重排
V43  V43__scm_delivery_permissions.sql            delivery data-only，物流配送菜单与权限
                                                   （1000–1003 / 1011–1016 / 1021–1022 / 1031–1032，
                                                   仅授 SUPER_ADMIN）；原编号 V42
V44  V44__scm_product_image_type.sql               product PCO-2：product_image 加 image_type，
                                                   存量主图回填（语义已由 V49 收口）
V45  V45__scm_product_pco2_permissions.sql         product PCO-2 data-only，导入/导出 418-419、
                                                   图片中心页 406 与按钮 496-497
V46  V46__scm_todo_permission.sql                  Wave 4 data-only，业务待办只读入口 1100-1101
                                                   （scm:todo:query，仅授入口不隐含领域权限）
V47  V47__scm_delivery_print_tracking.sql          Wave 5 配送打印追踪：delivery_route_order 加
                                                   print_count/last_printed_at/last_printed_by
                                                   （打印仅计次，不代表物理出纸成功）
V48  V48__scm_stocktake_import_permission.sql      Wave 6 data-only，盘点导入按钮权限 837
V49  V49__scm_product_image_type_gallery.sql       product PCO-2 图片类型语义收口（审计 §7.1）：
                                                   image_type 改为 GALLERY/DETAIL，主图唯一事实
                                                   回到 is_primary；V44 已应用不可改，故新增一步
V50  V50__scm_report_center_permissions.sql         report Finance R0 data-only，报表中心菜单与权限
                                                   （1200–1216，含独立的 scm:report:cost:query，仅授 SUPER_ADMIN）
V51  V51__scm_report_date_axis_indexes.sql          report 报表日期轴部分索引：sales_order.confirmed_at、
                                                   order_refund.completed_at、purchase_receipt.confirmed_at
```

W6-1/B1 changes are **BACKEND + BROWSER VERIFIED**; see `docs/progress.md`.
V25–V27（出库 / 预留）、V29（盘点）、V30（报损报溢）、V31（调拨）、V32（阈值预警）、
V33（规格转换）与 V34（移动加权成本）的**列表页已于 2026-09-20 浏览器验收**
（与余额 / 流水 / 规格转换 / 数据大屏共 11 页全绿、0 pageerror）；
**五条写流程 E2E 已于 2026-09-20 覆盖**（出库确认、盘点确认、报损报溢审批、
调拨发出/收货、规格转换审批，`e2e/scm-inventory-write.spec.ts` 6/6）。
V31/V33 的转入成本清零缺陷已由 V37 + 代码修复（成本随货平移）。
仍未覆盖：预留的**并发**压测、阈值预警推送、分拣与配送。见 `docs/progress.md`。

> **B7 数据大屏（V28）已于 2026-09-20 完成 V1 视觉重构**：三列 420/1000/420 + 底部趋势带，
> 10 个面板、3 张图表，新增 `GET /scm/screen/data/trend?range=7d|30d` 与库存健康度
> （四档互斥且之和等于总数，判定复用 `ScmInventoryWarningStatusEnum`，**不新编算法**）。
> 大屏仍是**只读视图**：不写业务表、不维护任何独立副本。
> 中间的「供应链网络」面板已于 2026-09-21（地图 M1）改为**真实中国地图**，
> 见下方地图 M0/M1 段落。设计取舍见 `docs/decisions.md`。

> **地图模块 M0 + M1（V40）已于 2026-09-21 完成**，方案与分期见
> [`docs/requirements/2026-09-21-地图模块分期实施方案.md`](./docs/requirements/2026-09-21-地图模块分期实施方案.md)。
> M0 给 `warehouse` / `customer` / `supplier` 建立结构化地理归属：三张主档各存**编码 + 名称快照六列**
> （名称只服务展示与导出，不参与关联，必须由同一次选择写入，不事后按编码反查），
> 加 `longitude` / `latitude` / `geom_crs`；**坐标系是硬约束**——GCJ-02 与 WGS-84 混存会造成百米级
> 不可追溯偏移，任何写入经纬度的代码都要同时落 `geom_crs`。`scm_region.center_lng/lat` 是**区划质心**，
> 只用于把聚合值画在正确位置，**不是任何单位的地址坐标**。
> 本期**无区划查询接口**：前端级联复用 SmartAdmin 的 `area-cascader` 静态字典，
> `scm_region` 只服务后端解析与质心；存量地址解析只在迁移内做保守整市名匹配，**解析不出即留空**，
> 由大屏的覆盖度面板显性提示未归属量，不猜。
> M1 的大屏地图为 ECharts 省界着色 + 市级气泡，底图是**存档进仓库的官方 GeoJSON**
> （`xsy-scm-web/public/screen/china-province.json`，构建期资产，运行时不请求任何外部域名）；
> 省级数值在 Java 侧由市级行上卷，省界图例与气泡之和恒等；
> **省级节点与底图要素按 `adcode`（= `province_code`）对齐，绝不按名称**——字典用简称「香港」而
> 官方边界用全称「香港特别行政区」，按名称匹配会静默不着色。流向层 `lines` 未做（等 M2 有真实点位再评估）。
> M2（街道底图 / 选点 / 地理编码）是**唯一需要外部付费服务的一层**，路线 A/B/C 尚未裁决。

> **商品中心优化 PCO-1（V38–V39）已于 2026-09-20 完成**：主档扩展字段（助记码 / 储存方式 / 税务与保质期
> 等）、计量单位与商品标签字典、列表高级筛选、批量上下架 / 改分类 / 打标签、商品与字典删除保护。
> **`master_status` 只收窄商品可选范围**（仅 `ProductSkuOptionDao.options` 过滤），订单 / 采购 / 库存
> 写入路径不受主档状态影响；计量单位只是**取值来源**，`sale_unit` / `purchase_unit` / `unit` 继续存
> 名称快照，因此单位活动行内名称必须唯一且编辑锁定编码与名称。Excel 导入导出与图片中心仍属 PCO-2。

> **V17/V18 版本号勘误（2026-09-17）**：SCM 菜单图标迁移原本与 F0 的文件上传迁移**同时**占用
> version 17，导致 Flyway 在解析阶段抛 `Found more than one migration with version 17`，
> **任何库都无法启动**。已按「保留已被真实库应用的那个版本号」原则，把菜单图标迁移
> **改名为 V18**（内容字节不变）。F0 的 V17 保留。当前只以迁移目录和 Git 历史为准。
>
> **选版本号前必须先同步远端**：`git fetch` 后用 `git ls-remote origin refs/heads/main` 确认真值，
> 并确认远端 main 是本地 HEAD 的祖先（或两者相等）；基线落后或分叉时不得选号。当前 `db/migration/` 的版本号必须**唯一且连续**。

Architecture contracts for the completed waves:

```text
Frontend              = SmartAdmin Base + SCM Vue (Copy First + Adapt)
Backend Infrastructure = SmartAdmin Native First
Backend SCM Business   = SmartAdmin Structure + confirmed SCM business rules
```

---

## 2. Repository Structure

Actual top-level structure:

```text
xsy-scm/
├─ xsy-scm-server/           ← V2 正式后端（Java 21 + PostgreSQL）
├─ xsy-scm-web/              ← V2 正式后台（SmartAdmin Vue3 + TypeScript）
├─ xsy-scm-miniapp/          ← LEGACY，冻结只读（待 W6 迁 uni-app）
├─ tools/                    ← V2 正式工具脚本
├─ project-reference-examples/
│  └─ xsy-scm/               ← 上游源码参考（只读，用于同步与比对）
├─ docs/
├─ deploy/
│  ├─ minio/                 ← F0 本地开发与集成测试对象存储
│  └─ postgres/              ← 本地开发与集成测试 PostgreSQL（Docker Desktop 官方镜像）
└─ AGENTS.md
```

Responsibilities:

```text
xsy-scm-server
= SmartAdmin-based Spring Boot business API and core domain logic (Java 21 + PostgreSQL)

xsy-scm-web
= SmartAdmin-based Vue3 + TypeScript admin UI

project-reference-examples/xsy-scm
= Upstream xsy-scm source reference — read-only, used for diffing and design extraction

docs
= Current progress and concise project decisions; business reference remains under `project-reference-examples/xsy-scm/`

deploy
= Docker, Nginx and deployment assets
```

Do not move responsibilities across these boundaries without a clear architectural reason.

当前不存在根目录 `docker-compose.yml` 和 `xsy-device-agent/`；设备集成章节描述的是未来职责，不代表已有实现。

**Frozen directory.** `xsy-scm-miniapp/` remains frozen and read-only. The root
`xsy-scm-server/` and `xsy-scm-web/` directories are the official V2 workspaces. Do not create
a second implementation or compatibility copy elsewhere in the repository.

---

## 3. Source-of-Truth Order

Before making non-trivial changes, read the relevant project documentation.

Priority:

```text
1. Current user request
2. AGENTS.md
3. `project-reference-examples/xsy-scm/` for business requirements and legacy semantics
4. Existing implementation
5. Existing tests
6. Framework conventions
```

For UI work, read:

```text
SMARTADMIN_REFERENCE_RULES.md
project-reference-examples/xsy-scm/（只读，提取业务语义和页面参考）
docs/decisions.md
```

For business workflow changes, read the corresponding reference-project documents first, then append the current decision or progress to `docs/` when needed.

For hardware work, read the corresponding hardware material in `project-reference-examples/xsy-scm/` when it exists.

Do not invent business rules that are not defined. If a workflow rule is unclear, preserve the existing behavior and clearly flag the ambiguity.

---

## 4. Default Technology Stack

V2 baseline is SmartAdmin v3.31. Do not upgrade Spring Boot / MyBatis-Plus / Sa-Token or other
upstream dependency versions unless a compile failure requires it — and report before doing so.

### Frontend (V2)

```text
Vue 3
Vite
TypeScript
Ant Design Vue
Pinia
Vue Router
Axios
Apache ECharts
v-privilege permission directive
```

The legacy React stack (React, @ant-design/pro-components, TanStack Query, Zustand,
React Hook Form, Zod, DataV-React) is **frozen** and must not be introduced into the root V2 frontend.

### Backend (V2)

```text
Java 21
Spring Boot 3.5.4
MyBatis-Plus 3.5.12
Sa-Token 1.44.0 + Redis (Bearer token)
Spring Validation
Flyway (sole schema-evolution mechanism)
OpenAPI 3 / knife4j
PostgreSQL
Lombok (no MapStruct)
```

`Spring Security` is retained only where SmartAdmin's own crypto/utility layer uses it
(e.g. `Argon2PasswordEncoder`); authentication itself is Sa-Token based.

### Mini Program

```text
Target: uni-app + Vue3
Current: xsy-scm-miniapp (Taro + React) — frozen, migrates at W6
```

### Device Integration

```text
xsy-device-agent
Windows local service
WebSocket / HTTP
RS232 / USB virtual serial / TCP/IP
Vendor SDK or DLL when required
```

### Deployment

```text
Docker Compose first
Nginx
Containerized application
Managed or containerized PostgreSQL depending on environment
```

---

## 5. Architecture Principles

Use a modular monolith by default.

Do not introduce the following without an explicit, demonstrated requirement:

```text
Microservices
Kafka
Kubernetes
Distributed transactions
Independent search engine
Complex event infrastructure
```

Preferred rule:

```text
simple and testable
> fashionable and distributed
```

Domain modules should remain clearly separated even inside one Spring Boot application.

---

## 6. Backend Package Rules

沿用 SmartAdmin 原生包与领域分层，不新建 legacy `com.xianshuyuan.scm` 包树：

```text
net.lab1024.sa.base                         框架与通用能力
net.lab1024.sa.admin.module.system          原生系统模块
net.lab1024.sa.admin.module.scm.<domain>     SCM 业务模块
  controller / service / manager / dao / constant
  domain/entity / domain/form / domain/vo / domain/dto
```

只创建当前领域实际需要的层，不机械增加空层。自定义 SQL 放对应模块的
`src/main/resources/mapper/` XML；简单 CRUD 优先 MyBatis-Plus。
系统能力不迁 legacy auth/system，不另建第二套认证、组织和权限实现。
详细边界见 [SmartAdmin 底座规则](SMARTADMIN_REFERENCE_RULES.md)。

---

## 7. Domain Rules

### 7.1 Product

The product domain must be able to evolve toward:

- Three-level categories
- Standard and non-standard products
- Multiple specifications
- Multiple suppliers
- Default purchaser
- Default supplier
- Customer-specific visibility
- Customer-specific price
- Market/time-sensitive price
- Contract price
- Product images
- On/off shelf state

Do not collapse these concepts into a single `product` table if doing so blocks future rules.

---

### 7.2 Customer

Keep separate concepts for:

- Customer
- Customer type
- Customer group
- Group subsidiary
- Settlement entity
- Address
- Contact
- Credit term
- Salesperson
- Product visibility
- Customer-specific price

Group-level settlement must not be assumed to be the same as order ownership.

---

### 7.3 Orders

Keep order header and order items separate.

Typical order sources:

```text
MALL
ADMIN
MOBILE_ASSISTANT
IMPORT
```

Order state changes must be explicit and auditable.

Do not directly mutate order status from unrelated modules.

Important actions such as:

- price modification
- quantity modification
- actual-weight modification
- cancellation
- refund
- return

must leave an operation log.

---

### 7.4 Purchasing

Purchasing must support:

```text
Sales Order
→ Purchase Demand
→ Aggregation
→ Split by supplier / purchaser / category
→ Purchase Order
→ One or more Receipts
→ Actual-weight Receiving
→ Inventory
```

Do not assume one purchase order can only be received once.

---

### 7.5 Inventory

Every stock change must create an immutable or append-only inventory movement record.

Typical movement types:

```text
PURCHASE_IN
SALES_OUT
RETURN_IN
PURCHASE_RETURN_OUT
STOCKTAKE_IN
STOCKTAKE_OUT
LOSS
GAIN
TRANSFER_IN
TRANSFER_OUT
ADJUSTMENT
```

Never update inventory quantity silently without a movement record.

Do not allow inventory code to bypass transaction boundaries.

Cost calculation is expected to use weighted-average logic, but exact accounting rules must follow confirmed finance requirements.

---

### 7.6 Sorting and Weight

For non-standard fresh products:

```text
ordered quantity
!=
final actual weight
```

The final actual weight may be created during sorting and can affect final settlement.

Do not overwrite the original ordered quantity. Preserve both:

```text
ordered quantity
actual / settled quantity
```

Weight changes must be auditable.

---

### 7.7 Delivery

Delivery concepts should remain separate:

- Route
- Delivery task
- Order assignment
- Vehicle
- Driver
- Vehicle location
- Track
- Sign-off

Do not store GPS track points directly in unrelated sales-order tables.

---

### 7.8 Finance

Initial finance scope focuses on:

```text
Receivable
Receipt
Payable
Payment
Customer statement
Supplier statement
Sales income
Purchase cost
Product profit
Customer profit
```

Do not expand into a complete general-ledger accounting suite unless explicitly requested.

---

## 8. Database Rules

Default database: PostgreSQL.

Always consider:

- Primary keys
- Unique constraints
- Check constraints
- Proper numeric precision
- Timestamps
- Indexes
- Transaction boundaries
- Migration scripts
- Slow-query impact

### Foreign Key Policy

**Do not create database foreign-key constraints in this project.**

Relationship integrity is enforced through:

- application/domain validation
- service-layer checks
- transactional business logic
- unique/check constraints where appropriate
- explicit indexes on relationship columns
- audit and reconciliation jobs for critical data when needed

Relationship columns such as `customer_id`, `product_id`, `order_id`, `supplier_id` and `warehouse_id` should still be modeled clearly and indexed according to query patterns, but they must not use `FOREIGN KEY` constraints.

Reasons for this project-level rule include:

- simpler data migration and import
- lower coupling between large business tables
- easier batch operations and historical-data handling
- fewer deployment and schema-change constraints
- business consistency is handled explicitly by the application layer

Do not silently add foreign keys through ORM annotations, Flyway migrations or schema-generation tools.

Schema changes must use Flyway migrations.

Do not:

```text
manually alter production schema
edit old applied migrations
create database foreign-key constraints
store large images directly in relational tables
skip important unique/check constraints and rely only on Java validation
```

Use object storage for images and large attachments. Production storage must be S3-compatible.
SCM uploads must go through SmartAdmin `FileService`; do not create a second upload facility,
write business binaries directly to disk, or bypass validation/metadata via `IFileStorageService`.
File access details and the mandatory pre-RBAC COMMON asset debt are maintained in the F0 Target Design.

Money fields should normally use:

```text
NUMERIC / DECIMAL
```

Never use floating-point types for financial values.

Weight fields must use a precision that supports the real scale resolution.

---

### MyBatis / MyBatis-Plus SQL Rules

Do not write SQL in mapper annotations.

Forbidden:

```java
@Select("SELECT ...")
@Update("UPDATE ...")
@Delete("DELETE ...")
@Insert("INSERT ...")
```

When MyBatis-Plus built-in CRUD or wrappers cannot express a query clearly, place the custom SQL in a mapper XML file under:

```text
src/main/resources/mapper/
```

Mapper interfaces should contain only method signatures and necessary parameter/result declarations. Keep SQL, result maps, reusable fragments and database-specific statements in XML.

---

## 9. API Rules

Default API style:

```text
REST
JSON
OpenAPI 3
```

Base path:

```text
/api
```

**V2 response envelope (Q5 — SmartAdmin `ResponseDTO`, mandatory):**

```json
{
  "code": 0,
  "level": "",
  "msg": "操作成功",
  "ok": true,
  "data": {},
  "dataType": ""
}
```

`code = 0` means success (`OK_CODE`). The legacy `message` field and the legacy `PageData`
envelope are **not** supported in V2 — do not add compatibility shims for them.

**V2 pagination (SmartAdmin `PageResult`):**

```json
{
  "code": 0,
  "msg": "操作成功",
  "ok": true,
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 100,
    "pages": 5,
    "list": [],
    "emptyFlag": false
  }
}
```

Request side uses `PageParam{ pageNum, pageSize, searchCount, sortItemList[] }`,
converted via `SmartPageUtil.convert2PageQuery` / `convert2PageResult`.

Rules:

- Validate input at the API boundary.
- Return stable error codes. Preserve existing in-use SCM business error codes
  (e.g. `40921`, `40933`, `40926`, `40963`, `40970`, `40971`) — do not renumber them for
  tidiness. New V2 error codes use a planned unified SCM range.
- Do not expose stack traces to clients.
- Make mutation semantics explicit.
- Use idempotency (`Idempotency-Key` + `idempotency_record`) where repeated external requests
  can create financial, stock or order side effects.
- Document public or shared API contracts.
- Method-level authorization uses `@SaCheckPermission("scm:<domain>:<action>")`.

---

## 10. Frontend Architecture

V2 管理后台使用 SmartAdmin Vue3 原生结构。旧 React 技术约定仅保留在
页面参考以 `project-reference-examples/xsy-scm/` 为主；本仓库不维护独立的旧 UI/UX 指导。

```text
xsy-scm-web/src/
├─ api/            接口定义（含 scm/ 业务域）
├─ views/          system/ 原生页面与 scm/ 业务页面
├─ components/     通用与业务组件
├─ layout/         SmartAdmin 原生 Layout
├─ router/         静态路由 + buildRoutes 动态菜单
├─ store/          Pinia
├─ directives/     privilege 等指令
├─ constants/
├─ theme/          主题与样式变量
├─ utils/
└─ types/
```

动态路由沿用 SmartAdmin `buildRoutes`（name = menuId）；按钮用 `v-privilege`，
接口通过既有请求封装。复用 Layout、菜单、Tabs、表格、表单、弹窗、上传与字典组件，
不引入 React、ProComponents、TanStack Query、Zustand、React Hook Form 或 Zod。

---

## 11. UI Design System

The project has three visual systems:

```text
Admin Theme
Screen Theme
Mall Theme
```

Do not mix them.

---

### 11.1 Admin Theme

Used by ERP / SCM management pages.

Style:

```text
professional
clean
restrained
high information density
efficient
modern Chinese enterprise SaaS / ERP
```

Primary colors:

```text
Primary          #00B96B
Primary Hover    #20C77A
Primary Active   #009A59

Background       #F5F7F9
Container        #FFFFFF
Text             #1F2329
Secondary Text   #4E5969
Border           #E5E6EB

Sidebar          #202631
Sidebar Hover    #2C3440
```

Avoid:

```text
glassmorphism
huge rounded corners
large marketing gradients
excessive shadows
landing-page style layouts
oversized typography
decorative animation
```

---

### 11.2 Admin Layout

采用 SmartAdmin 原生 Layout、侧栏、Header、Tabs 与页面配置，尺寸随既有主题与用户配置。
不恢复 legacy 双级侧栏，不为单页创建第二套全局布局。

### 11.3 Standard Business Page

默认信息顺序：标题 / Tabs → 查询 → 操作栏 → 表格 → 汇总 → 分页。
使用 Ant Design Vue 与 SmartAdmin 既有业务组件；复杂编辑按已有 Modal / Drawer 范式实现。
复用分页、查询重置、加载状态和权限处理，不引入 legacy ProComponents。

---

## 12. Table Rules

Tables are the primary interaction pattern in this ERP.

Default behavior:

```text
Header        light gray
Row           white
Hover         light green
Selected      #E8F8F0
```

Alignment:

```text
Text / names     left
Quantity         right
Money            right
Status           center
Actions          right
```

Money:

```text
¥ 1,280.50
```

Use:

```css
font-variant-numeric: tabular-nums;
```

Status should use consistent `Tag` semantics.

Dangerous actions such as delete, void or cancel require confirmation.

---

## 13. Form Rules

Prefer:

```text
2 columns for standard forms
2~3 columns for complex forms
```

Use common selectors:

```text
ProductSelector
CustomerSelector
SupplierSelector
WarehouseSelector
DriverSelector
VehicleSelector
ScaleDeviceSelector
```

Use:

```text
InputNumber for money / numeric input
DatePicker / RangePicker for dates
```

Do not build multiple incompatible versions of the same business selector.

---

## 14. Shared Frontend Components

新增公共组件前搜索 `xsy-scm-web/src/components/` 与已有业务页面。
商品、客户、供应商、仓库等选择器，以及金额、状态、上传和字典展示应优先复用。
仅在重复使用或具有明确业务职责时提取组件，不预建尚未进入当前阶段的设备、库存或大屏组件。

## 15. Design Tokens

沿用 `xsy-scm-web/src/theme/` 下的颜色、变量与 Less 主题，以及既有 Ant Design Vue 主题配置。
优先使用已有 token / 变量，禁止为单页散落随机颜色或另建一套主题目录。
Admin、Screen、Mall 保持各自的视觉边界。

---

## 16. Data Screen Rules

Data screens use a separate visual system.

Default colors:

```text
Background          #06152F
Panel               #071E42
Panel Secondary     #092851
Border              #1565B8
Primary Blue        #00A8FF
Cyan                #20E3FF
Primary Text        #EAF6FF
Secondary Text      #8FB7D9
Highlight           #FFD166
Danger              #FF5B5B
```

Reference structure:

```text
Header / Title / Time
│
├─ Left KPI / Progress
├─ Center Map / Main Chart
├─ Right Ranking / Messages
└─ Bottom Screen Navigation
```

Design base:

```text
1920 × 1080
```

Use overall scale for large-screen adaptation rather than freely reflowing the layout.

Data screens are read-only views of business data.

Never maintain independent copies of order counts, customer counts or financial values inside screen-specific storage.

---

## 17. Mall Rules

Mall / Mini Program UI may be visually richer than the admin system.

Allowed themes include:

```text
default fresh-food green
Spring Festival
618
summer
Dragon Boat Festival
Mid-Autumn Festival
New Year
opening promotion
seasonal campaigns
```

Admin configuration pages for mall themes still use the Admin Theme.

Do not let mall campaign styling leak into ERP pages.

---

## 18. Hardware Integration Rules

Electronic scales and other shop-floor devices are first-class project integrations.

Default boundary:

```text
Physical Device
→ xsy-device-agent
→ localhost WebSocket / HTTP
→ xsy-scm-web
→ xsy-scm-server
```

Do not make Vue business components depend directly on:

```text
COM3 / COM4
vendor binary protocol
baud rate
vendor DLL
raw serial frame
```

Those belong inside `xsy-device-agent`.

---

## 19. Device Agent Responsibilities

`xsy-device-agent` should own:

- Device discovery
- Serial / TCP connection
- Driver / SDK integration
- Reconnect
- Raw protocol parsing
- Stable-weight determination
- Unit normalization
- Device status
- Diagnostics
- Raw event logging where necessary

Prefer adapter-based design:

```text
ScaleAdapter
├─ SerialScaleAdapter
├─ TcpScaleAdapter
└─ VendorSdkScaleAdapter
```

Adding a new vendor should not require changing sales-order or sorting business logic.

---

## 20. Standard Weight Event

Frontend should consume a normalized event such as:

```json
{
  "deviceId": "SCALE-01",
  "status": "STABLE",
  "grossWeight": 12.56,
  "tareWeight": 0.35,
  "netWeight": 12.21,
  "unit": "kg",
  "timestamp": "2026-09-02T15:20:30"
}
```

Typical statuses:

```text
STABLE
UNSTABLE
DISCONNECTED
ERROR
STALE
```

Do not confirm stale cached weight after device disconnection.

---

## 21. Weight Audit Rules

Every confirmed weight should be traceable.

Preserve where applicable:

```text
device_id
business_type
business_id
business_item_id
gross_weight
tare_weight
net_weight
unit
stable
raw_data
operator_id
weighed_at
source
```

Weight source should distinguish at least:

```text
SCALE
MANUAL
IMPORT
SYSTEM
```

Manual override must preserve:

- original automatic weight
- modified weight
- reason
- operator
- timestamp

Never silently overwrite a scale-derived value.

---

## 22. Weight UI Rules

Weight pages are operational interfaces, not normal CRUD forms.

Priorities:

```text
large readable weight
clear device state
minimal clicks
safe confirmation
fast recovery
```

Recommended:

```text
Weight number    48~72px
Unit             18~24px
Primary buttons  40~48px height
```

Typical workflow:

```text
Live Weight
→ Stable
→ User Confirmation
→ Business Record
```

Do not automatically commit rapidly changing live weight into final business records.

---

## 23. Printing and QR Code Rules

Printing should be centralized.

Expected document types:

- Purchase order
- Receiving note
- Sorting slip
- Product label
- Shipment note
- Delivery note
- Statement

QR code use cases:

- Purchase-task QR
- Traceability QR
- Order QR
- Salesperson promotion QR

Prefer shared services such as:

```text
PrintTemplate
QrCodeService
```

Do not implement printing independently in each business module.

---

## 24. Security and Permission Rules

Use RBAC first.

> **V2 认证与鉴权（Q4 — 采用 SmartAdmin Sa-Token，不迁 legacy Spring Security 会话方案）**
>
> ```text
> Header：Authorization: Bearer <token>
> Sa-Token token-style: simple-uuid，有效期 30 天，登录态存 Redis
> loginId 形如 "2:44"（userType:employeeId）
> 接口鉴权：@SaCheckPermission("scm:<domain>:<action>")
> 前端按钮权限：v-privilege="'scm:<domain>:<action>'"
> administratorFlag 绕过权限校验
> ```
>
> 禁止套用 legacy 的 Spring Session JDBC 方案；
> 禁止为适配 legacy 而修改 SmartAdmin 核心认证实现。
> 当前不是 SaaS，不引入 `tenant_id` 与多租户。

Permission layers:

```text
User
→ Role
→ Menu
→ Button / Action
→ API
→ Data scope
```

Potential data scopes:

```text
Warehouse
Customer
Supplier
Purchaser
Salesperson
Department
```

Sensitive actions require authorization and audit logging.

Never expose secrets in:

- source code
- client bundle
- logs
- screenshots
- test fixtures
- committed config files

---

## 25. Audit Logging

Record important business mutations, including:

- Create
- Edit
- Delete
- Approve
- Cancel
- Void
- Order price modification
- Actual-weight modification
- Weight manual override
- Device bind / unbind
- Device configuration change
- Inventory adjustment
- Finance operation
- Permission change
- Login failure

Audit records should include enough context to identify:

```text
who
when
where
what object
what operation
before
after
```

---

## 26. Error Handling

Backend:

- Use structured domain exceptions.
- Map exceptions to stable API error responses.
- Avoid generic `catch (Exception)` unless rethrowing with context.
- Never swallow transaction failures.

Frontend:

Every network-backed page must consider:

```text
Loading
Empty
Error
Success
Retry
```

Hardware UI additionally considers:

```text
Device disconnected
Device stale
Unstable weight
Reconnect
Manual fallback
```

Avoid Toast storms for high-frequency device events.

---

## 27. State Management

- 服务端数据：SmartAdmin 既有 API / 请求封装与页面查询流程。
- 跨页面客户端状态：Pinia，沿用 `src/store/`。
- 页面局部状态：Vue `ref` / `reactive` / `computed` 与 composables。
- 需要保留的筛选、Tab 或分页：按已有路由约定使用 query 参数。

不重复维护同一份服务端状态，不引入第二套全局状态或请求缓存框架。

---

## 28. Validation

Validation should exist at multiple boundaries:

```text
Frontend UX validation
+
Backend authoritative validation
+
Database constraints
```

Do not rely only on frontend validation.

Important domains requiring strong backend validation:

- Price
- Weight
- Inventory
- Settlement
- Refund
- Payment
- Permissions
- Device-confirmed business actions

---

## 29. Transactions and Concurrency

Use database transactions for business operations that must remain atomic.

Examples:

```text
Purchase receipt + inventory movement
Sorting confirmation + actual-weight write-back
Shipment + inventory deduction
Refund + finance record
Payment + receivable update
```

When duplicate requests are realistic, add idempotency protection.

Do not solve concurrency by hiding buttons only in the UI.

---

## 30. Performance

Before optimizing, identify the actual bottleneck.

Always avoid:

- N+1 queries
- full-table scans on large operational tables
- unbounded list APIs
- loading all dashboard data in one huge transaction
- sending huge raw device event histories to pages

Use pagination for operational tables.

Use aggregate APIs for dashboards and screens.

---

## 31. Testing Strategy

Testing must be **risk-based and proportional**.

The goal is not maximum test count. The goal is sufficient confidence for important business behavior.

Preferred layers:

```text
Core domain unit tests
→ Critical database/API integration tests
→ A small number of key browser/E2E flows
→ Hardware integration verification where applicable
```

Backend tools:

```text
JUnit 5
AssertJ
Spring Boot Test
Testcontainers where it materially improves confidence
```

Frontend tools:

```text
Vitest
Playwright
```

### Avoid Over-Testing

Do not over-test routine implementation details.

Avoid:

- writing large test suites for trivial CRUD getters/setters
- testing framework behavior already covered by Spring, Vue or Ant Design Vue
- duplicating the same business assertion at unit, integration and E2E layers without a clear reason
- creating snapshot tests for large unstable UI trees by default
- mocking every dependency when a simpler integration test is clearer
- adding E2E tests for every button, field and pagination case
- writing tests only to increase coverage percentage
- forcing 100% line or branch coverage
- adding Testcontainers to simple pure unit tests
- testing generated code, DTO boilerplate or library internals

Prefer tests around high-risk behavior:

- price calculation
- customer-specific pricing
- order state transitions
- purchase aggregation and split rules
- partial receiving
- inventory movement and concurrency
- actual-weight write-back
- refund / payment / settlement
- permissions and data scope
- device weight confirmation and manual override
- idempotency
- critical reporting calculations

For ordinary CRUD pages and APIs, a focused happy-path test plus important validation/error cases is usually enough.

Do not replace unit / integration tests with a large number of brittle E2E tests.

---

## 32. Required Verification Before Completion

For frontend changes, run when available:

```text
npm run lint
npm run typecheck
npm run test
npm run build
```

For backend changes, run the project’s equivalent:

```text
compile
unit tests
integration tests where affected
package/build
```

触碰 `db/migration/` 时先跑 `python tools/migration_checksum_guard.py check`（`tools/verify.py` 的后端阶段已前置该检查）。
Flyway 按行 CRC32 校验已应用迁移的内容，源码格式化工具重排这些 SQL 的字节会让**所有存量库在启动期 validate 阶段失败**；
修复方向是还原为已被真实库应用的字节，而不是 `flyway repair`（repair 是改历史去迁就错误的文件）。
新增或改号用 `sync` 更新快照，禁止为了让守卫变绿而覆盖既有校验和。

For browser-facing work, verify:

```text
Console
Network
Main user path
Loading state
Empty state
Error state
Scrolling
Common desktop resolutions
```

For hardware-facing work, verify at least:

```text
Disconnected
Connected
Unstable weight
Stable weight
Reconnect
Zero weight
Repeated submit
Manual override
```

Do not claim completion if required verification was skipped. State what was not run and why.

---

## 33. Hardware Mocking

Before real hardware arrives, provide a mock provider that can simulate:

```text
Disconnected
Connected idle
Rapid weight changes
Stable weight
Device error
Reconnect
Stale data
Zero weight
Over-threshold weight
```

Business UI should be testable without a physical scale.

Do not block frontend and backend development on hardware delivery.

---

## 34. Git and Change Discipline

Before editing:

1. Read relevant code.
2. Read relevant tests.
3. Check current conventions.
4. Confirm the requested scope.

While editing:

- Keep changes focused.
- Do not refactor unrelated code.
- Do not mass-format unrelated files.
- Do not replace frameworks or major libraries without approval.
- Preserve user changes.
- Keep the primary checkout on its starting branch unless the user requests a switch.
- Check worktree changes and ancestry before cleanup; do not discard unmerged or untracked work.
- Follow [docs/README.md](docs/README.md) for the concise documentation boundary; use the Git commands below for repository hygiene.
- Avoid generated noise.

After editing:

- Review diff.
- Run relevant checks.
- Explain material behavior changes.

---

## 34.1 代码注释与说明规范

**注释说明意图、约束和例外，代码表达执行步骤，文档保存完整设计与历史。**
适用于项目新增和本次修改的 Java、Vue/TypeScript、SQL、脚本与测试代码；不要求补齐每个类、方法或字段的注释。

### 内容与位置

| 位置 | 应写内容 |
| --- | --- |
| 类 / 模块 / 组件 | 职责与关键边界；名称已足够清楚时可省略。不要列接口、字段、权限和功能清单 |
| 方法 / 函数 | 调用方必须知道的前置条件、副作用、返回值特殊语义、异常；不逐行讲解实现 |
| 字段 / 类型 | 单位、精度、时区、null 与零的区别、快照与实时值等业务含义；不重复名称和类型 |
| 复杂分支附近 | 为什么需要这个判断、排序或兼容处理，以及删除它会破坏什么约束 |
| SQL / Mapper | 非显然的索引谓词、锁序、回填顺序和数据口径；数据库 COMMENT 写业务含义，不写“库存域 id”式占位说明 |
| 测试 | 难以从断言看出的场景前提与特殊夹具原因；不复述断言，不在注释中宣称测试已通过 |

涉及事务原子性、并发锁序、幂等、防重、不可变账本、金额/数量单位、权限边界的说明，不能为缩短篇幅而删掉。
同一约束在最接近其责任代码的位置说明一次；其他位置只有存在独立误用风险时才补充。

### 表达与格式

- 业务说明优先用简体中文，标识符和标准术语保留原文；不做逐句中英双写。
- 通常用 1–3 句。行内说明优先 1–2 行；长说明先压缩，仍复杂则链接到 `docs/`，代码旁保留关键约束。
- Java 对外契约用 Javadoc，标识符用 `{@code ...}`，代码引用用 `{@link ...}`；不混用 Markdown 的反引号和 `**加粗**`。
- TypeScript 对外契约按需用 JSDoc；局部原因用 `//`；Vue 模板用 `<!-- -->`，样式用 `/* */`。文件头只在确有模块级说明时保留。
- 不新增装饰性长分隔线、emoji、连续感叹号、“为什么必须”式长篇论证；需要分组时使用简短标题。
- 不新增作者、日期、邮箱、AI 工具名、修改流水账或注释掉的旧代码。保留已有版权、许可证和必要的上游来源声明。
- TODO / FIXME 必须写清问题与处理条件，并关联现有问题号或文档；没有跟踪编号时使用明确的文件路径，不虚构编号，不留“以后优化”。

### 代码与文档的边界

- 代码注释描述当前行为。波次进度、裁决过程、修复历史、提交号、测试数量和验收结论写入对应 `docs/` 或提交说明。
- 设计引用给出能定位的文件路径，必要时附小节；不单独写含义不明的“Q13”“§6.2”。禁止大段复制设计文档到类头。
- 修改实现时同步检查附近注释；发现表述失实应修正，不能保留“绝不会失败”“已保证安全”等无依据断言。
- 不把框架指令当普通注释删除：保留有用途的类型检查、lint、覆盖率、打包标记及被工具读取的 SQL 分段标记。
- 已应用的 Flyway 迁移、冻结哈希、生成文件、只读参考和 legacy 冻结目录不因注释整理而修改。

### 示例与提交检查

```java
// 差：获取余额，计算新余额。
// 好：持有余额行锁后读取期初数量，避免并发入库生成错误快照。

// 差：W6 新增，已通过全部测试，未来绝不会出现重复入库。
// 好：来源行已存在时抛错，让收货确认与本次库存写入一起回滚。
```

注释整理限于明确选定的文件，不顺带改逻辑或批量格式化。提交前检查差异，确认有效代码、模板和样式未变，关键约束仍在，引用可定位。纯注释/文档整理做静态差异检查即可，不运行无关业务测试；混有逻辑修改时按 §31–32 及当前用户要求处理。

---

## 35. Dependency Rules

Before adding a dependency, ask:

1. Does the project already have an equivalent?
2. Is this needed in production or only for development?
3. Is it actively maintained?
4. What is the license?
5. Does it materially reduce complexity?

Do not add:

- a second major UI library
- a second state-management framework
- overlapping date libraries
- redundant HTTP clients
- large dependencies for trivial utility functions

---

## 35.1 Java Productivity and Code Generation Tools

For Java backend development, prefer mature, compile-time or framework-native tools over repetitive handwritten boilerplate when they clearly improve maintainability.

The agent should actively inspect the current project dependencies and conventions before manually implementing repetitive Java code.

### General Rule

Before writing repetitive infrastructure or boilerplate code, ask:

```text
1. Does the project already include a tool that solves this?
2. Is there a mature compile-time library commonly used for this problem?
3. Will the dependency remove meaningful repetitive code?
4. Will the generated behavior remain explicit, predictable and easy to debug?
5. Does it fit the existing Spring Boot / MyBatis-Plus architecture?
```

If the answer is yes, prefer the established tool instead of manually reproducing the same functionality.

The agent may add a small, mature dependency without separate approval when:

- it solves a recurring engineering concern rather than a one-off convenience;
- no equivalent dependency already exists in the project;
- it is actively maintained and compatible with the project's Java / Spring Boot version;
- it does not introduce a new architectural paradigm;
- it materially reduces boilerplate or error-prone mapping code;
- its behavior is deterministic and understandable by developers;
- the change is limited in scope and documented in the dependency file.

Do not add a dependency merely to save a few trivial lines of code.

### Lombok

Prefer Lombok for routine Java boilerplate when Lombok is already present or when the project contains enough DTOs, entities, value objects or configuration classes to justify it.

Typical allowed uses:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
```

Prefer:

```java
@Getter
@Setter
public class ProductDTO {
    private Long id;
    private String name;
}
```

over manually writing repetitive getters and setters.

For Spring dependency injection, prefer constructor injection, for example:

```java
@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductMapper productMapper;
}
```

Do not manually write constructors whose only purpose is Spring dependency injection when Lombok can generate them clearly.

Do not use Lombok indiscriminately.

Avoid or carefully evaluate:

```text
@Data on persistence entities
@EqualsAndHashCode on entities with mutable/database identity
@ToString on objects containing sensitive data or cyclic relationships
@Builder where framework construction semantics become unclear
```

Prefer explicit annotations such as `@Getter` and `@Setter` when `@Data` would generate more behavior than required.

Business behavior must never be hidden inside Lombok-generated mechanisms.

### Object Mapping

V2 基线为 Lombok，**不引入 MapStruct**。按对应 SCM 领域已有实现使用显式映射；
SmartAdmin 原生模块保留既有 `SmartBeanUtil` 用法，不为统一风格批量重构底座。

映射必须明确处理允许写入的字段，不能复制主键、审计字段或权限字段而绕过校验。
权限判断、金额计算、状态流转和数据库访问放在业务层，不隐藏在映射工具中。

---

### Object Construction

Use builders when objects contain many optional or clearly named fields and builder construction improves readability.

Example:

```java
OrderQuery.builder()
    .customerId(customerId)
    .status(status)
    .startTime(startTime)
    .endTime(endTime)
    .build();
```

Prefer constructors when the object has only a small number of mandatory arguments and the meaning remains obvious.

Do not create builders purely because Lombok provides `@Builder`.

### Utility Libraries

Before manually implementing common utility behavior, inspect existing project dependencies.

Examples include:

```text
string handling
collection handling
date/time handling
JSON serialization
validation
HTTP communication
object mapping
file handling
ID generation
retry
caching
```

Prefer, in order:

```text
JDK standard library
→ Spring Framework utilities
→ existing project dependency
→ small mature third-party dependency
→ custom implementation
```

Examples:

```text
java.time
instead of custom date arithmetic

Spring Validation / Jakarta Validation
instead of handwritten request validation

Jackson
instead of custom JSON serialization

Spring utilities
instead of introducing another utility library for a trivial helper
```

Do not introduce large "utility collections" only because one helper method is convenient.

### Annotation Processors and Compile-Time Generation

Compile-time generation is generally preferred over runtime magic when both solve the same problem cleanly.

Examples:

```text
Lombok
framework-supported annotation processors
```

are acceptable because generated behavior is established during compilation.

When adding annotation processors, ensure that:

```text
Maven / Gradle compilation works
IDE annotation processing works
CI build works
generated sources do not need to be manually edited
```

Never modify generated source files directly.

### Dependency Introduction Decision

The agent does not need to ask for confirmation before adding a lightweight Java productivity dependency such as Lombok (MapStruct remains excluded by the V2 baseline) when all of the following are true:

```text
the dependency is clearly appropriate
AND
the project does not already provide an equivalent
AND
the task would otherwise introduce substantial repetitive code
AND
the dependency is compatible with the current stack
AND
it does not alter system architecture or runtime infrastructure
```

The agent should explain the dependency addition in the final change summary.

Explicit approval is required before introducing dependencies that:

```text
change the persistence framework
change the web framework
introduce a new RPC framework
introduce a workflow engine
introduce a message broker
introduce a distributed framework
introduce a new ORM
replace MyBatis-Plus
significantly affect application runtime behavior
add large transitive dependency trees
```

### Preferred Java Development Principle

When implementing Java backend code, follow this preference:

```text
framework-native capability
> mature compile-time generation
> existing project utility
> explicit reusable abstraction
> repetitive handwritten boilerplate
```

But for business logic:

```text
explicit business code
> clever abstraction
> hidden framework magic
```

Use tools to remove mechanical code, not to hide domain behavior.

The goal is:

```text
less boilerplate
+
more compile-time safety
+
clearer domain code
+
consistent project conventions
```

not simply fewer lines of code.

---

## 35.2 Frontend Productivity and Framework Tools

选择顺序：Vue / TypeScript / 浏览器原生能力 → Ant Design Vue → SmartAdmin 已有组件与
composables → 项目现有依赖 → 必要的小型专用依赖 → 自定义实现。

- 表格与表单沿用 SmartAdmin 范式，使用 Ant Design Vue Form 校验；服务端仍做权威校验。
- 重复且有明确职责的交互提取 composable；简单状态不为抽象而抽象。
- 接口沿用既有 Axios 封装，不另建客户端；统一认证、错误、超时与响应处理。
- 使用明确的领域类型、可辨识联合和类型推导，避免 `any`、无依据断言及重复接口定义。
- 简单转换使用 TypeScript 原生方法；复杂业务转换提取具名函数，不塞入模板。
- 代码生成仅用于稳定、可复现的合同，生成内容不得混入手写业务逻辑。
- 引入小型专用依赖须确认无重复能力、维护情况和包体积影响，并在交付中说明。
- 替换 UI、状态、路由、HTTP、表单或构建框架，或引入大型运行时依赖，须获得明确批准。

## 36. UI Skill Guidance

先读本文件、SmartAdmin 底座规则与对应波次设计，再使用当前环境实际安装的 Skill。
可按任务使用 Impeccable / frontend-design 改善界面，以及 Playwright 验证真实页面。
本仓库没有 `.skills/xsy-scm-design/SKILL.md`；旧文档中的该路径是历史模板，不能作为必读入口。
不使用 React 专用 Skill 指导 V2 Vue 实现，不让多个设计 Skill 独立重设计同一页面。
项目的业务规则与 SmartAdmin 原生边界优先。

---

## 37. Agent Workflow

For a non-trivial task, use this sequence:

```text
1. Understand request
2. Read relevant docs
3. Inspect existing code
4. Inspect existing tests
5. Identify affected domain boundaries
6. Make a short implementation plan
7. Implement the smallest coherent change
8. Add / update tests
9. Run verification
10. Review diff
11. Report result and remaining risks
```

Do not start coding a complex feature before reading the existing implementation.

---

## 38. New Page Workflow

Before implementing a new ERP page, identify:

```text
Page purpose
User role
Tabs
Filters
Table fields
Actions
Status values
Summary data
Pagination
Create method
Edit method
Detail method
Required permissions
API dependencies
```

Then identify reusable components.

Default structure:

```text
Tab
→ Search
→ Toolbar
→ Table
→ Summary
→ Pagination
```

---

## 39. Feature Delivery Priority

Default delivery order:

```text
Foundation
→ Product
→ Customer
→ Supplier
→ Order
→ Purchase
→ Actual-weight Receiving
→ Inventory
→ Actual-weight Sorting
→ Shipment
→ Delivery
→ Finance / Reports
→ Data Screens
→ Mall / Mini Program
→ Traceability / Advanced Features
```

Hardware protocol research and device-agent PoC should begin early, before receiving and sorting are finalized.

---

## 40. Do Not Guess Critical Business Rules

Stop and flag ambiguity for rules involving:

- final settlement quantity
- customer contract pricing
- price-effective time
- group settlement
- purchase split logic
- partial receiving closure
- negative inventory
- stock costing
- sorting tolerance
- refund accounting
- payment allocation
- route optimization
- hardware protocol semantics

When the requirement is not explicit, do not silently invent a permanent rule.

---

## 41. Production Safety

Never execute destructive production operations without explicit approval.

Examples:

```text
DROP DATABASE
TRUNCATE production tables
delete Docker volumes
delete PostgreSQL data
destroy namespaces / PVCs
overwrite production secrets
change production DNS
force-push protected branches
```

For risky migrations:

- back up first
- provide rollback strategy
- verify row counts
- verify constraints
- test in non-production environment

---

## 42. Definition of Done

A task is not complete only because code was written.

A change is complete when applicable items are satisfied:

```text
Requirement implemented
Architecture boundaries respected
Types compile
Relevant tests pass
No unnecessary or duplicate tests were added
Build passes
No new console errors
No obvious network errors
UI follows project design system
Permissions are enforced
Audit logging added where required
Database migration included where required
Critical business action is transactional
Hardware state is handled where applicable
Documentation updated for material design changes
```

If any item was not verified, state it clearly.

---

## 43. Project Principle Summary

The project should optimize for:

```text
Correct business flow
Clear domain boundaries
Auditability
Reliable inventory and weight data
Consistent ERP UX
Testability
Maintainability
Recoverability
```

Prefer:

```text
clear over clever
modular over distributed
auditable over implicit
reusable over duplicated
stable over fashionable
proportional testing over coverage chasing
application-managed relationships over database foreign keys
```

The main goal is not to build the most technically complex system.

The goal is to build a reliable fresh-food supply-chain system whose:

```text
orders
prices
weights
inventory
purchases
deliveries
and settlements
```

can always be understood, verified and traced.
