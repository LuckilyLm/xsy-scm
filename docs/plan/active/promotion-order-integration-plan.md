# 营销接入正式订单（ADM-12 第三阶段）计划

计划日期：2026-10-03。
状态：决策已确认；切片 3-1～3-4、3-7、3-8 代码完成，待验收。
依据：[ADR-009](../adr/009-marketing-payment-promotion-order-assistant-and-traceability.md)、[开发规划](admin-development-roadmap.md)、[项目状态](../../status.md)及当前主线代码。

## 1. 目标

把已有营销能力（活动 / 券 / 优惠试算与冻结，V90 + 券启停子阶段）从「独立试算工具」变成**正式订单的组成部分**：优惠实际作用于正式订单且可追溯，取消 / 退款按冻结分摊反向，券状态与订单同源。

## 2. 改造前的现状（代码证据，2026-10-03 改造前）

| 事项 | 改造前 |
| --- | --- |
| 冻结入口 | `POST /scm/promotion/discount/confirm`，仅要求 `scm:promotion:activity:query` |
| 取数来源 | `PromotionDiscountConfirmForm` 由客户端传 `customerId` + `lines[{orderItemId, baseAmount}]`；**服务端不从订单重建**，也不校验订单归属、状态、期望版本 |
| 重复冻结 | 已有冻结直接抛 `DISCOUNT_ALREADY_FROZEN`（41333），**不是**请求级幂等重放 |
| 订单域接入 | 订单域未检索到任何优惠服务调用；订单确认 / 取消 / 售后都不触发优惠冻结或反向 |
| 活动快照 | 冻结只记 `activityId` + `activityVersion` + `activity_snapshot`（该条活动规则），**未完整冻结所有实际生效活动**与互斥取舍 |
| 券 | 占用 / 核销 / 释放的 DAO 命令已具备（`markReserved` / `markUsed` / `markReleased`，均带 `status` + `version` 前置条件），但只有 `markReserved` 被调用 |
| 满赠 | `FULL_GIFT` 当前返回零金额，未接赠品履约来源 |
| 限时特价 | 未建（ADR-009 与 V90 均注明需先补定价决策） |

## 3. 已确认的决策（2026-10-03 负责人）

### D1 行基础金额 = `ordered_line_amount`

订单行有两列金额，语义不同：

- `ordered_line_amount` = 下单量 × 锁定单价（`SalesOrderService:302`，提交时写入）；
- `settlement_line_amount` = 实重 × 锁定单价（`SalesOrderService:406`，确认时写入）。

**决定取 `ordered_line_amount`**。后果要记住：非标品实重与下单量不一致时，优惠基数仍是下单量口径，
不随称重结果变化。若将来要改成实重口径，属于定价决策变更，需另立裁决，不能就地改代码。

### D2 冻结并入订单确认

`SalesOrderService.confirmOrder` 写完订单状态后直接调用营销域冻结，客户端不再单独调
`/discount/confirm`；权限即订单确认权限 `scm:order:confirm`。优惠成为订单确认的一部分，
不可能出现「订单确认了但优惠没冻结」。

### D3 本轮先做主链

只做 3-1～3-3；取消 / 退款反向（3-4）、满赠履约来源（3-5）、限时特价（3-6）另开切片。

## 4. 切片状态

| 切片 | 内容 | 状态 |
| --- | --- | --- |
| 3-1 | 服务端按订单事实冻结：新增契约 `PromotionOrderFacts`（订单域装配，含 `salesOrderId` / `customerId` / 行 `orderedLineAmount`）；`PromotionDiscountService.freeze(facts, couponInstanceId)` 取代原 `confirm(form)`；活动不由客户端指定（服务端按生效规则与互斥组自选）；删除 `POST /scm/promotion/discount/confirm` 与 `PromotionDiscountConfirmForm`；`OrderConfirmForm` 新增 `couponInstanceId`（只接受「用哪张券」，券是客户权益） | 代码完成 |
| 3-2 | 冻结的请求级幂等：由外层命令承担 —— 冻结是订单确认事务的一部分，重复确认由 `ORDER_CONFIRM` 的 `Idempotency-Key` 回放首次结果，不会第二次进入冻结。`DISCOUNT_ALREADY_FROZEN` 保留为兜底，供非幂等路径调用时拒绝而非覆盖 | 代码完成 |
| 3-3 | 完整活动快照：`activity_snapshot` 由「单条规则」改为 `{"applied":[{activityId, activityCode, activityName, activityType, version, rule, discountAmount}...],"suppressed":[...]}`，冻结**每一条实际产生优惠的活动**（不同互斥组可叠加，可能不止一条）；`activity_id` / `activity_version` 保留为主活动（第一条产生优惠的活动）。形状仍是 `object`，`order_discount` 的 `jsonb_typeof='object'` 约束不需要改表 | 代码完成 |
| 3-4 | 订单优惠落到应收净额：正常应收按「行毛额 − 该行优惠分摊」生成，红字按同一把尺子反向；新增 `finance_receivable_item.discount_amount`（V93），毛额由 `amount + discount_amount` 还原不落库；财务详情展示毛额 / 订单优惠 / 净额 | 代码完成 |
| 3-5 | 满赠履约来源（赠品 SKU → 库存出库 / 成本口径） | 未开始 |
| 3-6 | 限时特价（改基础价，与 ADR-009「活动价在基础价之后计算」冲突） | 未开始 |
| 3-7 | 前端券选择入口：确认弹窗拉客户 `AVAILABLE` 券实例、切换即试算（只读不占用）、确认时提交 `couponInstanceId` | 代码完成 |
| 3-8 | 订单优惠可追溯：新增只读 `OrderDiscountVO` 与 `PromotionDiscountService.getByOrder`，订单详情嵌入 `discount`（优惠合计 / 基数 / 券 / 逐条生效活动 / 被挤掉的活动），前端只渲染不重算 | 代码完成 |

## 5. 本轮留下的明确待办

- **授信检查仍按未扣优惠的金额**：`confirmOrder` 里 `checkForSettlementConfirmation` 用的是 `settlementTotalAmount`，**没有减去优惠**。授信占用口径属于 ADM-04，改它需要单独确认，本轮刻意未动；否则会悄悄改变授信额度判定。
- **订单确认通知在冻结之后发送**：冻结失败即整笔回滚，不会出现「已发确认通知但优惠没冻结」。
- **架构测试未覆盖 `promotion` 域**：`ScmArchitectureTest.CONCRETE_DOMAINS` 列了 14 个业务域，但没有 `..scm.promotion..`，因此分层方向与「`common` 不反向依赖业务域」两条规则对营销域不生效。属于既有缺口，本轮未动测试（加了会引入无法在本轮验证的失败面），需单独处理。
- **3-4 的口径（负责人 2026-10-03 确认）**：
  - 应收**直接记净额**：`finance_receivable_item.amount` = 行毛额 − 该行优惠分摊；红字按同一份冻结分摊反向。不新增「优惠抵减事实」类型。
  - 折算规则：**冻结的订单行优惠 × 该行本次金额 / 该订单行下单金额**（等比）。少发或部分退货时该行承担的优惠按比例变小，不会整额落到部分金额上（整额会减成负数，被既有 `amount >= 0` 拒掉）。该式与「已确认优惠 × 退货金额 / 已出库金额」恒等，因此红字不必回读正常应收明细。
  - **不落 `gross_amount`**：毛额由 `amount + discount_amount` 精确还原，多存一列派生态会与「不落余额 / 结清状态列」的纪律冲突。
  - **授信占用口径不变**：确认时仍按未扣优惠的 `settlementTotalAmount` 判定（更保守，不因优惠放宽额度）。
  - 副作用（正向）：销售毛利报表直接取 `finance_receivable_item.amount` 作为收入，因此**净收入与退款反向自动同步**，利润 SQL 不需要改。
- **券的 `RESERVED → USED` / `RELEASED` 未接**：冻结时已占用（`markReserved`），但订单签收后转核销、取消后释放仍无触发点。`markUsed` / `markReleased` 已具备，只差编排，属下一片。

## 6. 不在本切片

- 支付与余额（intent / callback / refund / reconciliation、余额追加流水、账期与货到付款）属于 ADM-12 后续阶段，必须在本主链成立后推进。
- 不新建第二套订单状态机；`order_discount` 是追加的不可变快照，反向只能追加事实。
- 不改 Finance 的收入口径：优惠接入后由 Finance 侧另行同步净收入与退款反向分摊（见[财务与报表缺口盘点](finance-r2-report-gap-inventory.md) 3.3）。
