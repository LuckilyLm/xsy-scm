# 余额支付、订单资金核销与售后返还

状态（2026-10-07）：c1 / c2 / c3 已进入现有自动化基线；充值回调钱包入账时点仍待裁决，混合及多交易退款分摊 d 未实现。最新代码与验证边界统一见[项目状态](../../status.md)。

## 范围与金额裁决

- 本次实现内部余额支付及 ONLINE / BALANCE 的订单资金自动核销及 c3 纯余额售后返还。
- 2026-10-04 负责人确认：订单专属资金全额关联原正常应收。支付 100、实发应收 70 时，核销 100、超额核销 30；先退后签生成 RED 时同样保留资金全额用途。
- `overAppliedAmount = max(writtenOffAmount - netAmount, 0)` 只表达超额应用，不自动退款、增加钱包或抵扣另一订单。
- 售后保留 NORMAL、RED 与历史核销；不调用核销 reverse。REVERSE 只作整笔纠错，自动重放不恢复它。
- c3 纯余额售后返还已实现；d 混合及多交易退款分摊未实现，不选择退款优先级或比例。

## 实现入口

复用 `POST /scm/payment/intent/create`，权限 `scm:payment:intent:create`，要求 `Idempotency-Key`。

- BALANCE 固定 `provider=INTERNAL_BALANCE`、`sourceType=SALES_ORDER`，禁止模拟剧本；金额显式、恒正、最多 4 位小数。
- 仅已确认且具有冻结结算主体的订单可新支付；订单与客户范围相交，来源客户由订单核对。重复请求仍重新校验范围。
- 内部支付共享 Intent / Transaction 建单与状态推进；不调用外部 provider，不产生 callback、mock ledger 或 FinanceReceipt。
- `BalanceConsumptionSink` 返回已持久化消费事实。钱包按结算主体加锁，锁内来源查重、校验身份金额、重新汇总可用余额，再追加 `CONSUME / DEBIT + PAYMENT_INTENT`。
- 消费时点、Transaction.paidAt 与 Intent.succeededAt 使用同一时点；内部交易编号使用本地 transactionNo。
- 并发开户与来源冲突使用 `ON CONFLICT DO NOTHING`，读取冲突事实后逐项校验；删除原无来源消费入口。
- ONLINE 成功仍按实际渠道实收登记 Receipt。系统收款重新读取交易并校验 ONLINE、成功状态、客户、实收与时点，结算主体继承订单或充值请求冻结身份。

## 自动核销

`FinanceOrderFundingSettlementService` 要求上游事务持有订单锁，Finance 只读订单 / 支付 / 钱包事实。

1. 正常签收：确保 NORMAL → 补已批准退货 RED → 同事务结算。
2. 支付成功：确保 ONLINE 收款或 BALANCE 消费 → 同事务结算。
3. 受控内部重放复用同一入口；没有 NORMAL 合法保留未分配资金，不创建零额应收或孤立核销。

系统核销由 `FinanceOrderFundingWriteOffService` 独立承担，避免继续扩大人工分配服务。它重新读取来源 ID 对应的全部身份与金额，不接受调用方指定金额。一次编排先按稳定顺序锁全部资金来源，再锁正常应收；不要求配送人员具备人工核销权限。

- BALANCE 仅完整核销同订单 NORMAL；每笔消费最多一条 NORMAL 核销，冲突读取校验。整笔反向后不再自动补回，不再次扣钱包。
- ONLINE 使用 Receipt 有效金额减已分配金额。已人工分配其他目标的部分不搬动；同源同目标已有 NORMAL 时保留人工处理，已 REVERSE 时停止重放。
- 编排返回逐来源分配结果：已应用、已有分配、已反向、无可用资金、部分可用、无应收。保留人工结果与资金不足的情况留日志，不宣称整单结清。
- 人工核销仅允许 RECEIPT / PAYMENT，继续执行净未核销额上限；不能提交 BALANCE_MOVEMENT。
- 充值 Receipt 不可人工核订单，待核销额显示为 0，并标识“已转钱包权益”；系统收款仍禁止独立人工冲正。

## 退款与并发保护

- PaymentRefund 发起前锁订单，再锁交易和退款单；要求原 Intent 为 ONLINE / SALES_ORDER，且退款单 orderId、customerId 与原支付相同。
- 渠道退款前读取全订单成功资金，缺 Receipt / Movement 或身份金额冲突均拒绝。
- 纯余额拒绝渠道与人工现金退款；混合订单明确要求来源分摊。纯 ONLINE 保留单交易可退本金限制。
- 人工客户退款付款锁退款单再检查资金结构，纯线下退款仍保留。
- 新余额支付在订单锁内按 ID 锁既有退款单；已发起渠道或人工资金退款的订单拒绝继续余额支付，防止退款后新增余额资金绕过整单来源保护。

## c3 纯余额售后返还

- `OrderRefundService.complete` 在原订单与退款锁内调用 `BalanceRefundService.refundOnCompletion`；钱包追加失败时退款完成状态、日志与幂等结果整体回滚。
- 来源须为已完成的正额退款单，客户及结算主体匹配原订单的全部成功余额消费；原始消费、交易与意图金额及身份复用只读校验。重复消费来源不能重复计入可退本金。
- 累计本金 = 本订单全部唯一余额消费金额；累计返还读取该订单全部 `ORDER_REFUND` 来源 CREDIT。校验历史返还身份、金额与发生时点后，本次累计不得超过消费本金。
- 每张退款单最多一笔 `REFUND / CREDIT + ORDER_REFUND`。发生时点取退款完成时点。钱包锁内再次查重并比较金额和身份，重复请求不会重复增额。
- 返还原冻结结算主体的钱包，不根据客户当前集团关系改派；不新建一个替代钱包。无来源通用消费与通用公开 credit 原语均不保留为绕过入口。
- 检查整单全部关联退款是否存在 PaymentRefund 或 FinancePayment，任何已发起资金退款都阻止再返还余额。不会产生 FinancePayment、RED 或 WriteOff REVERSE。
- 有成功 ONLINE 资金或有效人工 Receipt 核销的订单不视为纯余额；完成业务退款时不猜测返还份额，显式余额返还命令拒绝并提示来源分摊。纯线下、纯 ONLINE 完成命令保留原行为。
- 只有余额成功但仍有未完成支付时拒绝返还，待支付结果明确后重试；已发生余额返还后禁止新增支付，避免事后变成混合资金。需要继续购货应另建订单。
- `POST /scm/balance/refund` 是已完成退款单的显式返还 / 重试入口，权限 `scm:balance:refund`，要求 `Idempotency-Key`，入参仅退款单 ID，按父订单范围授权。
- 退款查询批量读取余额返还流水，读时派生 `balanceMovementId` / `balanceReturnedAmount`，不新增退款资金状态列。列表可查看真实返还金额和来源流水；COMPLETED 且未返还的记录可发起有权限的重试。
- 同步修复退款查询继承销售订单状态枚举导致 COMPLETED 筛选失效的问题；退款完成及重放重验父订单范围。

## 前端与迁移

- 订单详情新增支付与支付记录入口，金额由操作人填写；内部结算编号、渠道状态、余额来源流水均可追溯。
- 当前 ONLINE 页面明确标示本地模拟；未声称已接入微信真实支付。
- 核销列表支持余额来源查询与流水查看，人工新增选项仍只有收款、付款；应收详情显示超额金额。
- 支付意图与交易查询补客户及订单数据范围；余额流水查询继续受钱包结算主体范围限制。
- 新增 `V106__scm_balance_payment_order_settlement.sql`，扩方法 / 渠道 / 来源配对，复用已有余额来源唯一索引，新增余额来源唯一 NORMAL 核销约束。
- 不修改既有 migration 或历史资金事实。V106 严格校验历史来源配对；若历史存在无来源 CONSUME 等不合法行，迁移会拒绝，不能通过改写历史或弱化约束绕过。
- 新增 V107，仅登记余额返还重试权限；余额返还继续复用已有来源唯一索引，不新增第二套退款账。

## 验证状态

2026-10-05 当前 V109 隔离库后端全套 **1,384 tests / 0 failures / 0 errors / 0 skipped**，主 Playwright **158 passed / 0 skipped / 0 unexpected / 0 flaky**；黄金支付/财务链 **36 passed / 1 skipped / 0 failed**。唯一 skip 为充值回调后的钱包入账链（D-39/J-18），因到账时点仍需业务方裁决。数据库测试使用 Docker Desktop 新建隔离库，未改原 `xsy_scm` V76。

测试证明的是本地 PostgreSQL、Redis、mock 支付与 MinIO 环境中的代码行为，不代表真实支付渠道回调或生产部署。d 混合 / 多交易退款来源分摊仍未实现；不得推断退款优先级或比例。
