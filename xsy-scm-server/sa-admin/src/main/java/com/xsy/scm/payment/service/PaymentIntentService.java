package com.xsy.scm.payment.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentIntentStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentTransactionStatusEnum;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
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

    private final PaymentIntentDao paymentIntentDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentNumberGenerator paymentNumberGenerator;

    private final ScmPaymentProviderRegistry providerRegistry;

    /**
     * 创建支付意图并向渠道发起。
     *
     * <p>
     * <b>应付金额只认入参</b>：不从订单金额推断，也不从已收金额倒算 ——
     * 一张订单可以只收一部分、也可以拆成余额 + 在线支付两条意图。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentIntentEntity create(PaymentIntentCreateForm form) {
        ScmPaymentMethodEnum method = ScmPaymentMethodEnum.of(form.getMethod());
        if (method == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_STATE_INVALID);
        }
        if (method == ScmPaymentMethodEnum.BALANCE) {
            // 余额支付在 3-12 落地前不开放
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_ENABLED);
        }
        ScmPaymentProvider provider = providerRegistry.require(form.getProvider());
        ScmPaymentMockScenarioEnum scenario = resolveScenario(form);

        String operator = ScmOperator.current();
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentNo(paymentNumberGenerator.nextIntentNo());
        intent.setCustomerId(form.getCustomerId());
        // 客户名快照由订单域保证（这里只存调用方给的业务单号快照），避免支付域反向依赖客户域
        intent.setCustomerNameSnapshot(String.valueOf(form.getCustomerId()));
        intent.setSourceType(form.getSourceType());
        intent.setSourceId(form.getSourceId());
        intent.setSourceNoSnapshot(String.valueOf(form.getSourceId()));
        intent.setAmount(form.getAmount().setScale(SCALE, RoundingMode.HALF_UP));
        intent.setMethod(method.name());
        intent.setProvider(provider.provider().name());
        intent.setStatus(ScmPaymentIntentStatusEnum.CREATED.name());
        intent.setMockScenario(scenario == null ? null : scenario.name());
        intent.setRemark(form.getRemark());
        intent.setCreatedBy(operator);
        intent.setUpdatedBy(operator);
        paymentIntentDao.insert(intent);

        // 向渠道发起
        ScmPaymentProvider.IntentResult result = provider.createIntent(new ScmPaymentProvider.IntentRequest(
                intent.getIntentNo(), intent.getAmount(), intent.getSourceNoSnapshot(), scenario));

        // 落交易事实：渠道交易号是回调匹配的入口，先落库再改状态，回调永远找得到它
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setTransactionNo(paymentNumberGenerator.nextTransactionNo());
        transaction.setIntentId(intent.getId());
        transaction.setProvider(provider.provider().name());
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
        // 同步成功时渠道还没回报金额（mock 与真实渠道都一样，金额以回调/查单为准），
        // 因此 provider_amount 留空，由回调或对账补齐 —— 不用本地应付金额去顶替它。
        applyOutcome(intent.getId(), transaction.getId(), result.outcome(), result.failureCode(),
                result.failureMessage(), null, operator);
        return paymentIntentDao.selectById(intent.getId());
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
                if (paymentTransactionDao.markSucceeded(transactionId, providerAmount, operator) != 1) {
                    // 已被处理过（重复回调）：不改状态，交由幂等层回答「已处理」
                    return;
                }
                transition(intentId, ScmPaymentIntentStatusEnum.PENDING, ScmPaymentIntentStatusEnum.SUCCEEDED,
                        operator);
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
