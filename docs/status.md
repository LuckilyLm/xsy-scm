# 项目状态

最后更新：2026-10-03。

本轮从本地 `main` 的 `daf77d55` 继续开发，起始工作区干净；配送范围收敛阶段代码已完成，V91 尚未执行。随后完成营销券启停子阶段代码（券启停接口与权限、发券幂等与客户范围判定、前端按钮），V92 尚未执行；再完成财务与报表（ADM-01）逐项缺口盘点与两个实施切片（六个导出 + 订单表头级客户订单明细）；最后完成营销接入订单（ADM-12 3-1～3-4、3-7、3-8：优惠按订单事实在订单确认内冻结，前端可选券，订单优惠可追溯，应收与红字改按净额）。未运行测试或构建，未推送。

## 当前完成

- SCM V2 正式底座及商品、客户、供应商、定价、订单、采购、库存、分拣、配送的当前阶段已交付；Finance R1 F1-8 与 V74 采购商品每日清单已完成。详情见[有效决策](decisions.md)与[开发规划](plan/active/admin-development-roadmap.md)。
- 主线代码收口记录截至 2026-10-01：Finance 重放范围、QF-TEST-01～06 和 V74 验收已完成，见[质量基线中的收口记录](quality/java-code-quality-remediation-plan.md)及 [ADR-005](adr/005-purchase-daily-report.md)。
- 上述主线代码已于 2026-10-02 合并并推送到 `main`，交付 SHA 为 `2edb8ccf4a7129bc02be1cdf3f67bcdea75edc37`。这是既有交付记录，不代表本次文档整理已提交或推送。

## 当前进行

- ADM-02～04 已补退货接收、结算快照、核销、冻结账期、授信阻断与例外放行的业务主链；2026-10-02 曾在独立 PostgreSQL 验收库执行针对性验证，最终新增改动待用户安排验收。
- ADM-01 新增应收/应付账龄页面、查询、汇总、导出、权限与 V77 菜单代码；保留缺失到期日分组。仅开发，未运行测试或构建。
- ADM-05 采购净需求已收口：只读缺口预览按仓库、SKU、单位扣除本批可用库存、有效在途未收量和已有采购覆盖；冻结批次（V78）可创建并生成需求，新增 `POST /scm/purchase/demand/batch/detail` 回看批次头、SKU 级解释行与逐行建议（数字原样取自快照，不回表重算），页面提供冻结批次明细抽屉并在需求列表显示来源批次。本次同时修复两处此前未编译通过的缺陷（`PurchaseQueryService` 缺 `Page` 导入；批次 `summary_snapshot` 以数组写入 `Map` 字段，与 V78 的 `jsonb_typeof='object'` 约束冲突，改为 `{"rows":[...]}`），并补发从未落库的 `demand:batch:create` / `demand:batch:generate` 权限（此前两端点对所有角色 403），新增 `demand:batch:query`（V83）。仅开发，未运行测试、构建或迁移。
- ADM-01 财务与报表缺口盘点完成，见[财务与报表缺口盘点](plan/active/finance-r2-report-gap-inventory.md)：需求「财务与报表」十项逐条对照现有端点、页面与权限码，结论为经营数据、销售明细、客户订单明细（行级 + 订单表头级）、销售员业绩、采购明细、已收款/待收款/应付款、单品利润、客户利润均已有交付。两个切片已实施：切片 1 补 `/scm/report/sales/category/export`、`/sales/seller/export`、`/overview/daily/export`；切片 2 补 `/purchase/overview/export`、`/purchase/purchaser/export`、`/purchase/price-trend/export` 与订单表头级 `/sales/order/query` + `/sales/order/export`。全部复用各自列表查询方法（`byCategory` / `bySeller` / `dailyStat` / `overview` / `byPurchaser` / `priceTrend` / `orderList`）与 `exportRows` 超限守卫，权限为对应 query AND `scm:report:export`（销售员业绩不含成本，故不要求成本权限）。新增 `SalesReportVO.OrderRow` 与 `ReportDao.salesOrderList`：一行 = 一个订单，退款按 `order_id` 独立聚合；分类 / 关键词用 `EXISTS` 只判定订单是否命中，命中后金额仍按整单汇总（不按匹配行缩小）。前端补六个导出按钮与销售分析「客户订单明细」Tab，并注册表格 id（`SCM_REPORT_SALES_ORDER` 50054）。后续营销接入订单完成 3-4 后，应收改为净额，销售毛利报表直接取 `finance_receivable_item.amount` 作收入，净收入与退款反向已自动同步、报表 SQL 未改。仅开发，未测试、未构建、未执行迁移。
- ADM-01 销售毛利分析（V80）、客户对账单（V81）已提交代码，仍未执行迁移或验收；新增供应商对账（V82）代码：原收货单生成的应付、手工红字、供应商付款正反、付款核销正反分别归集，并冻结期间明细和期初事实索引；支持本人历史、Excel、浏览器打印/PDF。部分采购员/仓库授权只展示部分应付，全供应商付款单列且未分配付款留空，接口同时要求应付和付款查询权限。V82 未执行，未运行测试/构建，历史期初、权限与金额勾稽待用户验收。
- ADM-06 订单与库存事件通知已收口：订单首次确认通知此前已交付；本次补上库存阈值通知。`V84` 新增 `scm_inventory_warning_state`（按 仓库+SKU 记最近状态与纪元）与 `scm:inventory:warning:scan` 权限，注册 `InventoryWarningScanJob`（默认每 15 分钟）。跃迁检测走周期性扫描而非挂在余额写路径上（取舍见 ADR-007）；只在 `NORMAL → LOW/HIGH` 投递，异常持续期间不重复，回到正常后再次异常可再提醒；接收人取自仓库授权行且排除停用人员，正文不含金额与成本。消息类型新增 `SCM_INVENTORY_WARNING(4)`，前端补数值镜像与跳转分支（按 `thresholdId` 定位到具体预警），预警页新增「检查并发送预警通知」。仅开发，未运行测试、构建或迁移。
- ADM-07 三类单据的模板打印代码已完成：V85 模板中心与采购单；V86 新增发货单/分拣小票类型和默认模板，线路打印入口与分拣列表/详情小票入口均复用共享模板对话框，支持预览、冻结及历史重印。原配送按订单/客户和分拣登记计次流程保留，模板打印不自动登记或改变业务状态。模板选择按源单据查看权开放；打印记录列表、重印与幂等重放重判当前范围和金额权限；同一模板读取同时用于渲染与冻结，批量中断后直接重试复用已完成结果。仅开发，未运行测试、构建或迁移，现场纸张与版式待验收。
- ADM-08 异常订单分析代码已完成：V87 新增菜单权限与源表索引；`/scm/report/order-exceptions/{query,summary,export}` 共享三类事实及筛选，按分拣录入/异常签收/退货驳回时间统计，逐类沿用源查看权和数据范围。页面位于 `/report/report-order-exceptions`，来源跳转打开分拣任务、配送线路或新增的退货只读详情。数量不跨单位汇总，不创建异常处置状态。未测试、未构建、未执行迁移。
- ADM-09 地图 M2 收口已补：`supplier` 的坐标列（V40 已建）此前只存在于数据库，Java 实体、表单、详情与前端选点均缺失；本次补齐三列成组校验（服务端断言 + DB 约束 + 前端 `locationError`）、详情展示与地图选点，区划或地址变更会作废已选点位。新增[地图接入的部署与使用](architecture/map-deployment.md)（环境变量、高德控制台白名单、无外网降级、部署验收清单、不承诺能力）。仅开发，未运行测试、构建或迁移；高德商用授权、配额与真实底图结果仍须在部署环境验收。
- ADM-10 已按负责人新要求完成代码收敛：保留「仓库起点 → 有序停靠点 → 方向连线」地图与缺点断线；移除 GPS 前端页面/API/类型以及后端 Controller、Service、DAO 等运行代码。V91 仅撤回 GPS 入口授权并补建议快照不可变约束，不改 V88 或历史轨迹数据。辅助排线要求线路查询权与当前司机范围，写操作统一先锁线路再锁建议；冻结并比对线路/停靠点版本、坐标与 CRS，旧无版本建议需重新生成。历史每段距离/地址恢复自快照；前端修复应用后刷新和跨线路旧请求覆盖。仅代码完成，未测试、未构建、V91 未执行。
- ADM-11 分拣增强已实现两半：任务冻结维度（`V89` 给 `sorting_task` 加送货时间快照、预配送波次、显式指定的供应商来源与名称快照；列表按这三者筛选，送货时间为半开区间；供应商在建单时校验启用态，不从 SKU 与供应商关系反推）与电子秤链路（`sorting_scale_event` 实现「设备事件 → 稳定读数 → 人工接受」，事件键幂等、重复上报返回既有记录、采集与接收时间分列；接受经既有 `enter` 入口只写已分拣数量，要求读数已稳定且明细为标准品，非标品必须人工录入）。前端分拣列表补三个冻结维度筛选与「秤读数」抽屉（接受 / 驳回）。替代商品、容差放行、供应商代分拣未纳入（ADR-008 明确排除）。仅开发，未运行测试、构建或迁移；真实电子秤协议、稳定性与校准须在现场验收。
- ADM-12 营销基础代码已形成（`1369716c` / V90）：活动与券管理、券实例、优惠按行比例分摊及冻结。券启停子阶段已补齐：券新建即草稿、此前无启停入口导致正常流程走不到发券，本次新增 `PromotionCouponService.updateStatus`（`DRAFT/STOPPED → ACTIVE`、`ACTIVE → STOPPED`，生效中不可改内容、过期券不可再启用）、独立权限 `scm:promotion:coupon:status`（V92 菜单 1709）与券列表启用/停用按钮；发券改为要求 `Idempotency-Key`（缺失 40069，同键同内容重放回放首次结果），发券与券实例查询经 `CustomerQueryService.detail` 判客户归属范围（越权 30005），券维护补版本校验，状态与券实例状态改用枚举，并替换 `PromotionActivityService` / `PromotionDiscountService` 中残留的状态字面量（消除 `magic-string-domain-literal` 命中）。订单接入（3-1～3-4、3-7、3-8）已补齐，见[营销接入正式订单计划](plan/active/promotion-order-integration-plan.md)：新增契约 `PromotionOrderFacts`（订单域装配，行基础金额取 `ordered_line_amount`，由负责人 2026-10-03 确认），`PromotionDiscountService.freeze(facts, couponInstanceId)` 取代原 `confirm(form)`，删除客户端 `POST /scm/promotion/discount/confirm` 与 `PromotionDiscountConfirmForm`，`SalesOrderService.confirmOrder` 在订单确认事务内冻结（不再可能「订单确认了但优惠没冻结」）；`OrderConfirmForm` 新增 `couponInstanceId`（只接受「用哪张券」，活动由服务端自选）；`activity_snapshot` 改为 `{applied[], suppressed[]}` 完整冻结每条实际产生优惠的活动；订单详情确认弹窗补券选择与试算（只读不占用）；新增只读 `OrderDiscountVO` 与 `PromotionDiscountService.getByOrder`，`SalesOrderDetailVO` 嵌入 `discount` 并在详情页展示优惠合计 / 基数 / 券 / 逐条生效活动（前端只渲染不重算）；前端移除 `discountConfirm` 封装。应收净额（3-4）：`finance_receivable_item` 新增 `discount_amount`（V93），正常应收按「行毛额 − 该行优惠分摊」生成、红字按同一把尺子反向（冻结行分摊 × 该行本次金额 / 下单金额，等比），`FinanceReceivableSourceDao` 只读 `order_discount.allocations` 摊平成行、财务不重算优惠规则，毛额由 `amount + discount_amount` 还原不落库，财务详情展示毛额 / 订单优惠 / 净额；销售毛利报表直接取 `amount` 作收入，净收入与退款反向自动同步。仍待收口：券 `RESERVED → USED`/`RELEASED` 的转结编排、满赠履约来源、冻结重放（现由外层 `ORDER_CONFIRM` 幂等承担，`DISCOUNT_ALREADY_FROZEN` 作兜底）。授信检查仍按未扣优惠金额判定（属 ADM-04 口径，本轮未动）。支付与余额未开发；未测试、未构建、未执行迁移，V92/V93 未执行。
- 当前路线图仍未完成；“当前阶段完成”不表示全部产品需求完成，代码验收与生产上线分开记录。

## 未开始或待打通

- 财务 R2：缺口盘点已完成（[清单](plan/active/finance-r2-report-gap-inventory.md)），两个切片（六个导出 + 订单表头级客户订单明细）代码已形成、待验收；销售毛利、客户/供应商冻结对账版本代码已形成、待验收。
- 业务闭环：销售退货回库、集团统一结算、账期授信联动已有代码主链，最终场景仍待验收；采购净需求预览、冻结批次与回看代码已形成，待验收。
- 后台完善：三类单据可配置打印模板、异常订单分析代码已完成，待验收。订单与库存事件通知代码已形成，待验收。
- 地图/现场增强：供应商点位、估算排线、分拣冻结筛选与秤事件已有代码。配送已收敛为计划路线示意，GPS 运行入口已移除、撤权 migration 待执行；地图目标环境与实体秤验收另行安排。
- 营销与支付、推广二维码、移动协同/订单助手、扫码溯源未形成完整模块。营销已接通订单确认冻结、前端券选择、优惠可追溯与应收净额；券状态转结（`RESERVED → USED`/`RELEASED`）、满赠履约与限时特价待做；支付/余额未开发。W6-2 客户商城仍未开始，legacy 目录冻结。
- 具体建议顺序、前置规则和完成标准以[开发规划清单](plan/active/admin-development-roadmap.md)为准；实施边界已由 ADR-006～010 确认。

## 验证与部署边界

- 2026-10-03 用户明确要求只开发，测试由其安排；此后不运行测试、构建或数据库迁移。V77、V80～V93 与其对应代码均尚未验收，既有测试结果不能覆盖后续改动。ADM-05 收口的两处编译缺陷由静态排查发现并修复；后续 ADM-05～ADM-12 新代码均未在本轮执行构建或运行验收。

- 最近一次记录的全量验收为 2026-10-01：后端 1,261 tests（0 failures/errors/skipped），Web 单测 258/258，浏览器 E2E 156 passed；质量与迁移门禁通过。本轮销售毛利、客户/供应商对账开发未重跑这些验证。
- 前端 TS 棘轮无新增错误，但直接全仓 `vue-tsc --noEmit` 仍有历史类型错误；lint 保留 3 条既有 warning，构建有既有提示。不将其表述为全仓零问题。
- 2026-10-03 只读静态扫描（`quality_guard.py scan`，未运行 verify、未改基线）显示质量守卫存在历史漂移：`magic-string-domain-literal` 10 条、`stage-comment` 3 条命中未被 `tools/quality/baseline/` 覆盖（均来自 V84～V87 阶段，非本次营销改动）。营销域本次已清零（券/活动状态与券实例状态改用枚举、替换裸字面量，命中由 9 条降为 0）。上述漂移会使 `quality_guard.py check` FAIL，需在授权验证时一并处理，本次未修改基线也未修复非营销域命中。
- V68–V74 最近记录只应用于本地验收 / scratch 数据库，未应用生产库；本轮未查询生产环境，部署时需单独核验。
- 最近收口证据保留在质量基线与 ADR-005；旧过程和阶段快照从 Git 历史追溯，不另建副本文档。历史测试数字和 Git 推送不等于当前生产环境验收。

## 下一步

按[开发规划第二节](plan/active/admin-development-roadmap.md#2-下一轮开发顺序按最新代码重新排定)推进：配送路线范围收敛代码已完成；营销券启停子阶段（券启停、按客户范围发券、重试防重复）代码已完成；财务与报表缺口盘点完成并交付两个切片；营销接入订单（按订单事实在订单确认内冻结、前端券选择、订单优惠可追溯、应收净额与红字反向）代码已完成。接下来补券状态转结与满赠履约来源，随后分阶段建设支付、独立员工端/订单助手、客户商城/推广及批次包装溯源。每个实现阶段单独本地提交并更新进度；默认不测试、不构建、不迁移、不推送。
