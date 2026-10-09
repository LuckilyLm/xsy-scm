# 当前决策索引

本文件只列出当前仍有效的决策和未决事项。正式约束以 ADR 正文为准；旧决策全文见
[2026-09-28 决策归档](archive/decisions/decisions-2026-09-28.md)。

## 工程边界

- SmartAdmin 系统底座边界见 [SmartAdmin 底座规则](architecture/smartadmin-foundation.md)。
- Java 质量、包结构和文档治理见 [整改基线](quality/java-code-quality-remediation-plan.md)。
- 注释怎么写、哪些不许写见[源码注释规范](architecture/code-comment-guidelines.md)（由 `test/scm-comment-noise-contract.test.mjs` 钉住）。
- 已应用的数据库迁移不可修改；新变更只追加新版本。

## P0 基线收口裁决：角色模型、SCM 数据范围与 F0 附件收口

现行规则见 [ADR-001：数据范围与附件授权](adr/001-data-scope-and-file-access.md)。

## P1 分拣管理裁决

现行规则见 [ADR-002：分拣事实边界](adr/002-sorting-fact-boundary.md)。

## P2 物流配送 L3 裁决

现行规则见 [ADR-003：配送出库与签收](adr/003-delivery-outbound-and-signoff.md)。

## P3 Finance R1 裁决

现行规则见 [ADR-004：Finance R1 财务事实](adr/004-finance-r1-facts.md)；设计细节见
[Finance R1 设计](plan/active/finance-r1-design.md)。

## 其他仍有效的历史决策

采购商品每日清单的可配置调度、提交日期口径、快照与权限规则见
[ADR-005：采购商品每日清单](adr/005-purchase-daily-report.md)。

移动加权成本、调拨成本平移、订单来源、商品主档、大屏和地图 M0/M1 的决策仍可从
[完整归档](archive/decisions/decisions-2026-09-28.md)查阅。与当前 ADR 不一致的旧口径已被后续决策取代。

## 当前已明确的实现边界

- 调拨页已提供“在途库存”只读报表（`/scm/inventory/transfer/in-transit`），在途量不计入任何仓库余额；不再列为缺少报表。
- 当前定价顺序为协议价 → 客户类型价 → 市场价；额外时价语义及活动价叠加仍需另行设计。
- 超收容差、少收关单、直接入库与仓库二次确认均已落地；采购库存对比预览不等于净采购建议。

## 地图当前边界

- M0 的地区、坐标和 CRS 字段及 M1 中国地图大屏已实现。客户、仓库、供应商和停靠点均已接入通用选点组件（供应商点位见 ADM-09）。
- `map-provider.ts` 根据 `VITE_AMAP_KEY` 加载高德 JSAPI，支持安全代理/安全码配置、Geocoder 与 Driving 插件；配置缺失可手工录入经纬度。代码可配置不代表生产环境已配置或获得商用授权。
- 2026-10-07 负责人确认：配送路线展示升级为高德驾车规划的道路路线（见 ADR-008 修订）。顺序事实仍来自 XSY 的停靠点顺序：高德不决定顺序、不自动应用排线建议、不成为发车前置；算路失败降级为点间连线。实时 GPS 定位、轨迹采集/回放仍不纳入本期；GPS 运行代码已按 ADR-008 移除，V91 撤权尚未执行，历史轨迹表与数据保留。
- 现有辅助排线仅作为可选工具，司机端单独建设；实时路况、实时导航与到达时间承诺不在当前范围。坐标必须成对且带 CRS，地图不能绕过业务范围或直接改变库存。
- 商用服务商、授权、配额和部署路线在实施时核实，不沿用历史调研的版本、费用或预排迁移号。

## 详情页面化与业务编码（2026-10-07 负责人确认）

- **UI：** 复杂业务详情改用独立页面，Drawer 只负责创建 / 编辑与轻量查看。配送线路详情为首个样板：隐藏菜单 + `/delivery/routes/:id?tab=` 深链，多面板工作台不再用 1440px 抽屉模拟页面；后续订单、采购、库存详情沿用同一模板。`workspace` 档不再新增用例。
- **编码：** 系统内部编码一律由后端生成，用户只填有业务含义的信息。三类划分：系统业务编号（订单 / 采购 / 线路 / 出库 / 退款等）必须后台生成；内部主数据编码（客户 / 供应商 / 仓库，后续商品 / 分类 / 品牌 / 客户类型 / 司机 / 车辆内部编码）默认后台生成；外部真实标识（商品条码、车牌号、税号）仍人工录入。
- **落地：** 新增 `ScmBusinessNoService`（`common.no`，底层 PG 序列 `customer_code_seq` / `supplier_code_seq` / `warehouse_code_seq`，V111）；主数据编码不带日期（`CUS000001` / `SUP000001` / `WH000001`），交易单号沿用 `ScmDocumentNumbers`（前缀 + yyyyMMdd + 序号）。客户端提交的编码不再采信，各表唯一索引是最后一道保险。

## ADM-01～17 实施决策基线

[开发规划清单](plan/active/admin-development-roadmap.md)描述工作包与完成标准；实施前业务裁决以以下 Accepted ADR 为准：

- ADM-01～04：[ADR-006：Finance R2、退货、集团结算与授信](adr/006-finance-r2-returns-settlement-and-credit.md)。
- ADM-05～08：[ADR-007：后台净需求、通知、打印与异常聚合](adr/007-admin-operations-demand-notification-print-and-exceptions.md)。
- ADM-09～11：[ADR-008：配送路线示意、辅助排线与分拣设备](adr/008-map-routing-gps-and-sorting-devices.md)。
- ADM-12～15：[ADR-009：营销支付、推广归属、订单助手与溯源](adr/009-marketing-payment-promotion-order-assistant-and-traceability.md)。
- ADM-16～17：[ADR-010：客户商城与员工移动端双产品](adr/010-customer-mall-and-employee-mobile-products.md)。

上述 ADR 统一冻结收入与出库成本、退货回库、结算方与授信、净采购、站内通知、打印快照、异常聚合、地图/路由/设备 provider、营销支付、推广与溯源，以及双移动产品的实施边界。既有 ADR-001～004 的数据范围、附件、分拣、配送和 Finance 追加事实不变量继续有效；外部支付、地图、设备、微信发布等生产验收不得由本地 mock、构建或代码交付替代。

## 营销优惠生命周期、赠品与限时特价口径（2026-10-03 负责人确认）

对 ADR-009 的三条实施口径收敛；ADR-009 的边界不变，本段只固定此前未明确的状态与成本归属。

### 优惠券生命周期

```text
确认订单      AVAILABLE → RESERVED   （冻结优惠时占用）
正常签收      RESERVED  → USED       （与应收同一时点、同一事务）
异常签收      保持 RESERVED           （EXCEPTION 既不形成应收，也不算用掉）
部分/全额退款 保持 USED
现有订单取消  不处理券                （CONFIRMED 已是订单终态，cancel 碰不到 RESERVED）
未来撤销确认  RESERVED  → RELEASED   （当前无触发点，保留语义）
需要返券      重新 issue 一张新券，不复活旧券
```

- 券核销挂**正常签收**而不是退款：签收是订单级不可逆终态，也正是应收的形成时点，券「是否已被一笔真实成交使用过」与它对齐。
- **退款不恢复券**：退款反向的是金额（按冻结分摊），券回答的是权益是否已被使用过，两者不能混；允许 `USED → AVAILABLE` 会让「100 元订单用 20 元券、退款后券回来、再用于下一单」变成重复营销权益。若业务确需「全额退款返券」，另做重新发券（新实例），审计上才分得清「原券用掉了」与「补偿了一张新券」。
- `RELEASED` 是**历史终态**，不等于 `AVAILABLE`：它是「这张券被释放过」的痕迹。要把权益还给客户，应原券 `RELEASED` + 重新 `issue` 新券。

### 满赠（赠品）

- **不减订单收入**：赠品金额恒为 0，`FULL_GIFT` 的优惠额保持 0 是对的。
- **赠品必须走正式库存出库**，出库来源明确为促销赠品；赠品出库成本**计入该订单的履约成本**，因此销售毛利要扣掉赠品成本。
- **不把赠品塞成普通 `sales_order_item`**：那会污染采购需求、销售数量、销售额与客户购买商品分析。用独立冻结事实记录赠品（订单 / 活动 / SKU / 数量 / 单位 / 快照）。
- 赠品权益在**订单确认时冻结**（`order_promotion_gift`，快照不可变），出库、分拣、小票与成本归集都读它，不在各自环节重算活动规则。
- 门槛按**订单基础合计**判定，不按逐条作用后的剩余金额：同单另有满减不该把满赠门槛压没。
- **运行口径（2026-10-03 确认）**：赠品出库挂**发车**时点，与销售出库同一时点、同一仓库；赠品库存不足时**不阻断订单确认**，在出库时报错、由仓库补货后重试；赠品**进分拣清单与小票**（仓库照单备货），分拣不写回赠品数量。

### 限时特价

- **不改基础定价链**：协议价 → 客户类型价 → 市场价 保持不动，限时特价不参与这层的优先级竞争，也**不进 `PriceResolver`**。
- 它作为 Promotion 在**基础价之后**作用：`基础价解析 → 限时特价 → 满减/折扣 → 优惠券`。
- 冻结记录仍能回答「原基础价是多少、为什么卖这个价、按哪个活动哪一版、减了多少」：订单行金额仍是基础价口径，让利作为**独立事实**落 `order_discount.special_discount_amount` 与逐行分摊，**不把订单行的价格来源改成「限时活动」**。
- 保护规则：**限时特价只能把基础价往下压，不能抬高**（特价 ≥ 该行单价时让利为 0）。
- 分配口径：让利按**行**归集（`行基础金额 − 数量 × 特价`），不参与按金额比例分摊 —— 特价针对某个 SKU，摊到别的行上会让退款反向错行；同一行只让一次价（已被更靠前的特价命中过的行跳过），因此「按优先级顺序作用」在特价上依然成立，且每条活动记进快照的让利额之和恰好等于总让利。

## 其他未决事项

- 多仓默认选择是否需要扩展；当前订单和线路不推断默认仓，由操作者显式选择。
- 期初 `avg_cost` 的业务口径；当前采用最近一次采购入库单价，无历史时为 0。已应用迁移不得为此回改。

地图商用授权、配额与目标环境调用，以及微信主体、审核发布和真实支付属于 ADR-008～010 明确的外部生产验收项，不再作为实施前业务规则未决事项。

## 订单资金全额核销与纯余额返还（2026-10-04）

- 负责人确认采用全额关联：支付 100、正常应收 70 时，订单专属资金核销 100，超额 30 单独显示，不自动退款或增加钱包。售后 RED 不改历史核销，人工 REVERSE 保持整笔纠错语义。
- 纯余额售后返还属于 c3，已完成代码实施。退款完成与原钱包 CREDIT 同事务；每退款来源唯一，累计不超原消费本金，与整单渠道 / 人工退款互斥。混合及多交易退款来源分摊仍属 d 阶段，不预设分摊顺序或比例。
- 实现、权限迁移及未验证边界见[余额支付与订单资金核销](plan/active/balance-payment-order-settlement-design.md)。

## 外部评审 P1/P2 修复：并发锁序与财务口径（2026-10-09）

来源：外部代码评审 `XSY-SCM-Frontend-Backend-Review-2026-10-09-latest.md`（基线 `a9b98f80`）。本批只处理评审给出的发布门禁与三类正确性缺陷。

### PUR-01：需求分配 vs 采购单取消

- **根因是锁序不一致，不是「少读一次」**。`update` / `cancel` / `delete` 全是「锁采购单 → 锁需求」，
  而 `allocate` 原本是「锁需求 → 无锁读采购单」。这个反向让 `cancel` 出现幻读窗口：
  cancel 读「本单没有分配」→（无分配时早退，不取需求锁）→ allocate 插入并提交 → cancel 提交 CANCELLED，
  于是分配永久悬挂在已取消的单上、需求 `allocated_quantity` 不回落。
  实测时间戳可证：分配 `created_at` 比单据 `cancelled_at` **早 294ms**，即分配先落库、取消后提交，取消侧却什么都没删。
- **修法：把 `allocate` 改成与其它命令同向 —— 先锁采购单行，再锁需求行**。
  这样分配与取消在单据行上串行，幻读窗口不存在。
  早期误判「先取单锁会与 `update` 成环」是错的：`update` 本来就是「单 → 需求」，
  同向才是对齐；`create` 虽先锁需求再插新单，但新单行尚不存在、无人可与之竞争，不参与成环。
- `releaseAllocations` 随之回到**单次读**：所有写分配的命令都持单据锁，读到什么删什么即可，
  无需加锁重读。此前试过的「锁前发现 + 锁内重读」在「首次读为空 → 早退」时依然漏判，已被证伪。
- **回归用例**`PurchaseAllocationCancelRacePgIT`（PG，无外层事务，真两线程）：
  顺序版断言「已取消单再分配 → 40982」；并发版断言「无论谁先赢，单据必为 CANCELLED、
  该单无任何活动分配、需求 `allocated_quantity` 回落 0 且退回 PENDING」。
  反向验证：只回退 `allocate` 的锁序改动即复现失败（活分配 `allocatedQuantity=5.0000` 悬挂在 CANCELLED 单上）。

### PAY-01：跨午夜对账漏配

- 对账**双窗口取数**，不改成「只查成功交易」：已成功按 `paid_at` 落结算日，未成功（含 `INITIATED`）
  按 `created_at` 落发起日，两个窗口的结果按 `provider_transaction_no` 合并去重。
- **为什么不简单换成 `listSucceededBetween`**：那会丢掉 `STATUS_MISMATCH`（渠道成功、本地仍挂 INITIATED）
  的检出能力，DAO 注释已明确警告不能只看成功笔。`provider_transaction_no` 非空且唯一，按它合并安全。

### PAY-02：退款累计额口径

- `sumSucceededByTransaction` 改按 **`provider_amount`**（渠道实退）而非 `amount`（本地请求额）求和，
  否则部分退款 / 渠道改额后累计额虚高。
- 不需要 `COALESCE(provider_amount, amount)` 兜底：V101 的
  `ck_payment_refund_success_provider_amount` 已保证 `SUCCEEDED ⇒ provider_amount NOT NULL`。
  该不变量本身由 `PaymentRefundSumBasisPgIT` 断言。

### SEC-01 / SEC-02：订单族与退货族写入口的归属门禁

- **缺陷是「读收窄、写不设防」的错配**：列表与详情早就按 `orderSellerScope` 只给出本人负责的行，
  但订单的 `update / submit / actualQuantity / cancel / delete / reserveStock` 与退货的
  `create / approve / reject / cancel` 只判「单据存在 + 状态合法」。于是持有功能点权限的普通人员
  **猜到 id 就能改别人的单、动别人的数量、占别人的库存、替别人批退货退款** —— 行级范围退化成「藏起来」。
  反向验证：只回退这两处改动，新 IT 14 条里 **13 条失败**，失败形态正是「本该抛异常却成功了」；
  `reserveStock` 那条甚至真的写出了预留行（报「可用库存不足」），证明当时确实已经占了别人的货。
- **功能点权限 ≠ 行级归属**：`@SaCheckPermission` 回答「能不能用这个功能」，
  `orderSellerScope.allows()` 回答「能不能对这个单用」，两者不可互相替代，写入口两者都要过。
- **门禁刻意不放进 `SalesOrderService#lock`**：`lock` 还被退货、退款等模块调用，
  在那层加守卫会顺带改掉这些路径的语义（退款读的是同一张单，但它有自己的读侧收窄）。
  门禁加在**对外写命令入口**，与既有的 `confirmOrder` 用同一套判定，形成一致口径。
- **SEC-02 放在 `OrderReturnService#lock`**：退货单自身没有负责人列，归属完全由父销售订单决定；
  `create / approve / reject / cancel` 全部经过这个私有 `lock`，一处收口即全覆盖。
  `lockForReceipt` 的唯一调用方 `OrderReturnReceiptService.receive` 在入口已调过带范围的 `detail()`，
  重复判定不改变其语义。
- **幂等重放也必须过范围，且要分两层看**：`ScmIdempotencyService` 的 scope 是
  `operator + ":" + scope`，operator 取 `ScmOperator.current()` = `userType:userId`，
  因此 A 与 B 的重放键在存储层就是两行 —— **跨人重放结构上不可能**。
  真正要堵的是另一条：**同一个人换了负责范围后重放同一个旧键**会直接命中 `replay` 分支、
  跳过写命令里的门禁。所以每个 `claim.replay()` 分支都在 `replay` **之前**补一次带范围的读
  （订单走 `salesOrderQueryService.detail`，退货走 `OrderReturnService#detail`）
  —— 后者本身会重新判 `orderSellerScope`。`replay` 只做 JSON 反序列化、不做任何重校验，这一点不能依赖。
- **回归用例** `ScmOrderWriteScopePgIT`（真实 PG + 真服务 + 真事务）：
  双销售员 A/B，功能权限相同、数据范围不同，逐一覆盖 6 个订单写入口 + 4 个退货写入口，
  每条都断「他人的单拒绝（且业务数据零留痕）+ 自己的单成功 + 幂等重放不绕开」；
  另覆盖 `seller_id IS NULL` 的未分配单：普通销售不可写、持 `ORDER_ALL_PERM` 者可写。
  不用 Mockito 打桩 DAO：缝隙正在「读用的范围」与「写用的范围」两条路径之间，打桩会把它擦掉。
