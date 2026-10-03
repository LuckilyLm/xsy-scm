package com.xsy.scm.payment.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentIntentStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentTransactionStatusEnum;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.support.FinancePaymentReceiptFact;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.support.BalanceRechargeIntentFact;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付意图：创建与发起。
 *
 * <p>
 * 这一片只做 {@code ONLINE}（走渠道）。{@code BALANCE} 的余额扣减是 3-12 的内容，
 * 在那之前**明确拒绝**，避免余额意图悄悄走到外部渠道上。
 *
 * <p>
 * <b>调用顺序是刻意的</b>：先落意图（CREATED）→ 调渠道 → 落交易事实 → 推进状态机。
 * 渠道调用夹在本地事务里，因此「渠道返回成功但本地没记上」只会发生在事务提交阶段失败，
 * 那类差异由对账发现（mock 渠道的账本用独立事务写，正是为了能模拟它）。
 */
@Service
@RequiredArgsConstructor
public class PaymentIntentService {

    private static final int SCALE = 4;

    private static final String IDEMPOTENCY_SCOPE = "PAYMENT_INTENT_CREATE";

    private static final String RECHARGE_INTENT_SCOPE = "BALANCE_RECHARGE_INTENT_CREATE";

    private final PaymentIntentDao paymentIntentDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentNumberGenerator paymentNumberGenerator;

    /** 只读外部事实：按订单 id 解析正式订单（订单号 / 客户 / 业务员）。 */
    private final PaymentSourceDao paymentSourceDao;

    private final ScmDataScopeService dataScopeService;

    private final ScmIdempotencyService idempotencyService;

    private final ScmPaymentProviderRegistry providerRegistry;

    /**
     * 财务域的系统收款入口。依赖方向是 payment → finance；finance 不反向依赖支付域，
     * 因此不构成环，与 delivery → finance 是同一套做法。
     */
    private final FinanceReceiptService financeReceiptService;

    /**
     * 充值落账入口（由余额域实现）。
     *
     * <p>
     * 用 {@link ObjectProvider} 而不是直接注入：这是**真实的双向业务关系** —— 余额域要调本域
     * 创建意图，本域要在支付成功后通知余额域落账。接口定义在本域（依赖倒置，编译期单向），
     * 但 Bean 依赖仍是双向的，因此按需解析、不在构造期解环。
     *
     * <p>
     * 用 {@code getObject()} 而不是 {@code getIfAvailable()}：拿不到实现是装配错误，
     * 必须响亮失败 —— 静默跳过会让「钱进来了、钱包没加」这种最糟的情况无声发生。
     */
    private final ObjectProvider<BalanceRechargeSink> balanceRechargeSinkProvider;

    /**
     * 创建支付意图并向渠道发起。
     *
     * <p>
     * <b>应付金额只认入参</b>：不从订单金额推断，也不从已收金额倒算 ——
     * 一张订单可以只收一部分、也可以拆成余额 + 在线支付两条意图。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentIntentEntity create(PaymentIntentCreateForm form, String idempotencyKey) {
        // 幂等：**收钱也要幂等**。这里往下会真的调用 provider.createIntent()，
        // 后台双击 / 网络重试 / 前端超时重试都会造出第二个意图与第二笔渠道交易。
        var claim = idempotencyService.claim(IDEMPOTENCY_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PaymentIntentEntity.class);
        }

        ScmPaymentMethodEnum method = ScmPaymentMethodEnum.of(form.getMethod());
        if (method == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_STATE_INVALID);
        }
        if (method == ScmPaymentMethodEnum.BALANCE) {
            // 余额支付（用余额抵扣）不从这里开：余额消费要与应收核销一起设计（3-12c）
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_ENABLED);
        }
        ScmPaymentProvider provider = providerRegistry.require(form.getProvider());
        ScmPaymentMockScenarioEnum scenario = resolveScenario(form);
        // 来源身份由**正式订单事实**决定，不采信客户端提交的客户与单号
        PaymentOrderFact order = requireOrder(form);
        return createInternal(new IntentDraft(ScmPaymentSourceTypeEnum.SALES_ORDER.name(), order.orderId(),
                order.orderNo(), order.customerId(), order.customerNameSnapshot(),
                form.getAmount().setScale(SCALE, RoundingMode.HALF_UP), method, provider, scenario, form.getRemark()),
                claim);
    }

    /**
     * 余额充值：**内部契约**，只由余额域调用。
     *
     * <p>
     * 刻意不放开公开的 {@code /scm/payment/intent/create}：客户端若能自己拼
     * {@code sourceType=BALANCE_RECHARGE + sourceId=任意充值 id}，就等于绕过了
     * 「先有充值事实、再有钱要付」这条顺序。
     *
     * <p>
     * <b>{@code method} 恒为 {@code ONLINE}</b>：这是往余额里充钱，资金来源仍是外部在线支付；
     * {@code BALANCE} 表示「用余额抵扣」，是相反的方向。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentIntentEntity createForBalanceRecharge(BalanceRechargeIntentFact fact, String idempotencyKey) {
        var claim = idempotencyService.claim(RECHARGE_INTENT_SCOPE + ":" + fact.rechargeId(), idempotencyKey, fact);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PaymentIntentEntity.class);
        }
        ScmPaymentProvider provider = providerRegistry.require(fact.provider());
        ScmPaymentMockScenarioEnum scenario = null;
        if (fact.mockScenario() != null && !fact.mockScenario().isBlank()) {
            if (!ScmPaymentProviderEnum.MOCK.name().equals(fact.provider())
                    || ScmPaymentMockScenarioEnum.of(fact.mockScenario()) == null) {
                throw new ScmBusinessException(PaymentErrorCode.PAYMENT_MOCK_SCENARIO_INVALID);
            }
            scenario = ScmPaymentMockScenarioEnum.of(fact.mockScenario());
        }
        return createInternal(new IntentDraft(ScmPaymentSourceTypeEnum.BALANCE_RECHARGE.name(), fact.rechargeId(),
                fact.rechargeNo(), fact.customerId(), fact.customerName(),
                fact.amount().setScale(SCALE, RoundingMode.HALF_UP), ScmPaymentMethodEnum.ONLINE, provider, scenario,
                fact.remark()), claim);
    }

    /**
     * 意图创建的共享实现（订单支付与余额充值都走这里）。
     *
     * <p>
     * 抽出来是为了让两条入口共用**同一套**「先落意图 → 调渠道 → 落交易 → 推进状态机」的顺序：
     * 各写一份迟早会出现「一条路径记得先落交易、另一条忘了」。
     */
    private PaymentIntentEntity createInternal(IntentDraft draft, ScmIdempotencyService.Claim claim) {
        String operator = ScmOperator.current();
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentNo(paymentNumberGenerator.nextIntentNo());
        intent.setCustomerId(draft.customerId());
        // 冻结**正式**客户名与业务单号：存 id 字符串不仅是显示问题 ——
        // 支付成功后会顺着 customer_id 进 Finance 收款事实，来源身份必须从一开始就是对的
        intent.setCustomerNameSnapshot(draft.customerName());
        intent.setSourceType(draft.sourceType());
        intent.setSourceId(draft.sourceId());
        intent.setSourceNoSnapshot(draft.sourceNo());
        intent.setAmount(draft.amount());
        intent.setMethod(draft.method().name());
        intent.setProvider(draft.provider().provider().name());
        intent.setStatus(ScmPaymentIntentStatusEnum.CREATED.name());
        intent.setMockScenario(draft.scenario() == null ? null : draft.scenario().name());
        intent.setRemark(draft.remark());
        intent.setCreatedBy(operator);
        intent.setUpdatedBy(operator);
        paymentIntentDao.insert(intent);

        // 向渠道发起
        ScmPaymentProvider.IntentResult result = draft.provider().createIntent(new ScmPaymentProvider.IntentRequest(
                intent.getIntentNo(), intent.getAmount(), intent.getSourceNoSnapshot(), draft.scenario()));

        // 落交易事实：渠道交易号是回调匹配的入口，先落库再改状态，回调永远找得到它
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setTransactionNo(paymentNumberGenerator.nextTransactionNo());
        transaction.setIntentId(intent.getId());
        transaction.setProvider(draft.provider().provider().name());
        transaction.setProviderTransactionNo(result.providerTransactionNo());
        transaction.setAmount(intent.getAmount());
        transaction.setStatus(ScmPaymentTransactionStatusEnum.PENDING.name());
        transaction.setCreatedBy(operator);
        transaction.setUpdatedBy(operator);
        paymentTransactionDao.insert(transaction);

        if (result.externalIntentId() != null) {
            paymentIntentDao.bindExternalIntent(intent.getId(), result.externalIntentId(), operator);
        }
        // 发起过就是发起过：先落到 PENDING，再按渠道结果推进 —— 状态机因此不需要
        // CREATED → SUCCEEDED 这条捷径，「发起了几次」也仍然可数
        transition(intent.getId(), ScmPaymentIntentStatusEnum.CREATED, ScmPaymentIntentStatusEnum.PENDING, operator);
        // 同步成功时渠道会一并回报金额（result.amount()），落进 provider_amount；
        // 延迟 / 失败时为空，等回调再报。**本地绝不拿应付金额去顶替它**。
        applyOutcome(intent.getId(), transaction.getId(), result.outcome(), result.failureCode(),
                result.failureMessage(), result.amount(), operator);
        PaymentIntentEntity saved = paymentIntentDao.selectById(intent.getId());
        idempotencyService.complete(claim, "PAYMENT_INTENT", saved.getId(), saved);
        return saved;
    }

    /** 意图创建草稿：把两条入口的差异收在一处，共享实现只认它。 */
    private record IntentDraft(String sourceType, Long sourceId, String sourceNo, Long customerId,
            String customerName, BigDecimal amount, ScmPaymentMethodEnum method, ScmPaymentProvider provider,
            ScmPaymentMockScenarioEnum scenario, String remark) {
    }

    /**
     * 把渠道的发起结果落到状态机上。
     *
     * <p>
     * 抽成方法是为了让回调路径能复用同一段判定：发起时同步成功与之后回调成功，
     * 落到本地必须是**同一条**状态转换，不能各写一份。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyOutcome(Long intentId, Long transactionId, ScmPaymentProvider.Outcome outcome, String failureCode,
            String failureMessage, BigDecimal providerAmount, String operator) {
        switch (outcome) {
            case SUCCEEDED -> {
                boolean firstTime = paymentTransactionDao.markSucceeded(transactionId, providerAmount, operator) == 1;
                if (firstTime) {
                    transition(intentId, ScmPaymentIntentStatusEnum.PENDING, ScmPaymentIntentStatusEnum.SUCCEEDED,
                            operator);
                }
                // **无论是否首次都确保 Finance 收款事实存在**：
                // 「渠道已成功、本地事务当时失败」的场景靠下一次回调 / 对账重新驱动恢复，
                // 而恢复的入口就是这一句。注册本身按来源键幂等，重复调用不会多记一笔收款。
                registerFinanceReceipt(intentId, transactionId, operator);
            }
            case FAILED -> {
                if (paymentTransactionDao.markFailed(transactionId, failureCode, failureMessage, operator) != 1) {
                    return;
                }
                transition(intentId, ScmPaymentIntentStatusEnum.PENDING, ScmPaymentIntentStatusEnum.FAILED, operator);
            }
            case PENDING -> {
                // 渠道还没给结果（延迟回调 / 等支付）：什么都不做，等回调
            }
            default -> throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_STATE_INVALID);
        }
    }

    /**
     * 支付成功 → Finance 收款事实（ADM-12 3-11a）。
     *
     * <p>
     * <b>同一事务</b>：Finance 写失败就整笔回滚，本地不会留下「已成功但没登记收款」的半截事实。
     * 渠道事实不回滚 —— 那正是对账要发现、并靠下一次回调重新驱动的差异。
     *
     * <p>
     * <b>唯一来源键</b>：{@code source_type = PAYMENT_TRANSACTION} + {@code source_id = transactionId}。
     * 支付域的回调事件幂等是第一层，这个来源键是第二层：无论本地被驱动多少次，
     * Finance 只有一条正常收款事实。
     *
     * <p>
     * 金额取 {@code provider_amount}（渠道实收），时间取交易的成功时刻 —— 都不取本地应付金额与本地当前时间。
     */
    private void registerFinanceReceipt(Long intentId, Long transactionId, String operator) {
        PaymentTransactionEntity transaction = paymentTransactionDao.selectById(transactionId);
        if (transaction == null
                || !ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(transaction.getStatus())) {
            // 交易没成功就没有收款事实可言：这里是唯一的守卫，调用方不必各自判一遍
            return;
        }
        PaymentIntentEntity intent = paymentIntentDao.selectById(intentId);
        if (intent == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_NOT_FOUND);
        }
        financeReceiptService.registerFromPaymentTransaction(new FinancePaymentReceiptFact(transaction.getId(),
                intent.getCustomerId(), transaction.getProviderAmount(), transaction.getPaidAt(),
                transaction.getProviderTransactionNo()));

        // 充值来源还要把**钱包权益**同时记上：两条事实回答两个不同问题 ——
        // Finance 收款说「公司实际进账了多少」，余额流水说「这笔钱形成了多少钱包权益」。
        // 不是重复记账：一个回答资金，一个回答权益。
        if (ScmPaymentSourceTypeEnum.BALANCE_RECHARGE.name().equals(intent.getSourceType())) {
            balanceRechargeSinkProvider.getObject().rechargeFromPayment(intent.getSourceId(), intent.getCustomerId(),
                    transaction.getProviderAmount(), transaction.getId(), transaction.getPaidAt());
        }
    }

    /**
     * 状态转换的唯一入口：先判状态机，再带「当前状态」做条件更新。
     *
     * <p>
     * 受影响行数 != 1 说明有人先改了 —— 宁可报错也不覆盖，因为支付状态是外部事实的投影。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long intentId, ScmPaymentIntentStatusEnum from, ScmPaymentIntentStatusEnum to,
            String operator) {
        if (!ScmPaymentIntentStatusEnum.canTransition(from.name(), to.name())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_STATE_INVALID);
        }
        if (paymentIntentDao.updateStatus(intentId, from.name(), to.name(), operator) != 1) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_STATE_INVALID);
        }
    }

    /**
     * 按订单 id 解析并校验正式订单事实。
     *
     * <p>
     * 校验四件事：来源类型受支持、订单存在、**订单客户与提交的客户一致**、订单在当前调用者的
     * 订单数据范围内。
     *
     * <p>
     * 越权与「订单不存在」**共用同一个拒绝**（{@link ScmDataScopeException}，对外 30005）：
     * 能分辨「存在但无权」就等于把订单主键探测变成了可用信号，与 Finance 收款的纪律一致。
     *
     * <p>
     * 金额仍由调用方显式给出（允许部分支付、余额 + 在线支付拆分），但**来源身份不能由客户端自己拼**。
     */
    private PaymentOrderFact requireOrder(PaymentIntentCreateForm form) {
        if (!ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(form.getSourceType())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_SOURCE_INVALID);
        }
        PaymentOrderFact order = paymentSourceDao.selectOrder(form.getSourceId());
        if (order == null || !dataScopeService.resolve().getOrderSellerScope().allows(order.sellerId())) {
            throw new ScmDataScopeException();
        }
        if (!order.customerId().equals(form.getCustomerId())) {
            // 订单客户与提交客户不一致：这条不挡住，支付成功后的 Finance 收款会挂到别的客户名下
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_SOURCE_INVALID);
        }
        return order;
    }

    /** 仅本地模拟渠道可带剧本；真实渠道带剧本一律拒收（DDL 也有同义 CHECK 兜底）。 */
    private static ScmPaymentMockScenarioEnum resolveScenario(PaymentIntentCreateForm form) {
        if (form.getMockScenario() == null || form.getMockScenario().isBlank()) {
            return null;
        }
        if (!ScmPaymentProviderEnum.MOCK.name().equals(form.getProvider())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_MOCK_SCENARIO_INVALID);
        }
        ScmPaymentMockScenarioEnum scenario = ScmPaymentMockScenarioEnum.of(form.getMockScenario());
        if (scenario == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_MOCK_SCENARIO_INVALID);
        }
        return scenario;
    }
}
