package com.xsy.scm.payment.service;

import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.support.FinancePaymentRefundFact;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentRefundStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentTransactionStatusEnum;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentRefundCreateForm;
import com.xsy.scm.payment.domain.vo.PaymentRefundRecoveryLogVO;

import java.util.List;

import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款必须关联已成功收取的款项，累计退款不得超过原支付金额；重复退款请求不得生成重复的财务付款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private static final int SCALE = 4;

    private static final String IDEMPOTENCY_SCOPE = "PAYMENT_REFUND_CREATE";

    private final PaymentRefundDao paymentRefundDao;

    private final RefundFundingAllocationDao refundFundingAllocationDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentIntentDao paymentIntentDao;

    /**
     * 只读外部事实：订单 / 退款单 / 财务付款，用于来源校验与互斥。
     */
    private final PaymentSourceDao paymentSourceDao;

    /**
     * 财务域的系统退款付款入口。依赖方向是 payment → finance；finance 不反向依赖支付域，因此不构成环（与 payment → finance 收款是同一套做法）。
     */
    private final FinancePaymentService financePaymentService;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;

    private final PaymentNumberGenerator paymentNumberGenerator;

    private final ScmPaymentProviderRegistry providerRegistry;

    private final ScmIdempotencyService idempotencyService;
    private final ScmDataScopeService dataScopeService;

    private final RefundFundingCompletionService refundFundingCompletionService;

    /**
     * 发起退款。
     *
     * <p>
     * <b>重复请求靠业务幂等键回放首次结果</b>，不是「发现已有就随便返回一个」：前者回答「这次请求的结果是什么」，后者会在两次请求参数不同时给出一个看似成功的错误答案。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundEntity create(PaymentRefundCreateForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(IDEMPOTENCY_SCOPE + ":" + form.getTransactionId(), idempotencyKey, form);

        PaymentTransactionEntity initial = paymentTransactionDao.selectById(form.getTransactionId());
        PaymentIntentEntity originalIntent = initial == null
            ? null
            : paymentIntentDao.selectById(initial.getIntentId());
        if (originalIntent == null
            || !ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(originalIntent.getSourceType())
            || paymentSourceDao.lockOrder(originalIntent.getSourceId()) == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        var order = paymentSourceDao.selectOrder(originalIntent.getSourceId());
        var scope = dataScopeService.resolve();
        if (!scope.getOrderSellerScope().allows(order.sellerId())
            || !paymentSourceDao.customerVisible(originalIntent.getCustomerId(), scope.getCustomerSellerScope())) {
            throw new ScmDataScopeException();
        }
        if (claim.replay()) {
            PaymentRefundEntity original = idempotencyService.replay(claim, PaymentRefundEntity.class);
            return paymentRefundDao.selectById(original.getId());
        }
        financeOrderFundingPolicy.requireCashRefundAllowed(originalIntent.getSourceId(), true);
        if (!ScmPaymentMethodEnum.ONLINE.name().equals(originalIntent.getMethod())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        PaymentTransactionEntity transaction = paymentTransactionDao.lockById(form.getTransactionId());
        if (transaction == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_TRANSACTION_NOT_FOUND);
        }
        if (!ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(transaction.getStatus())) {
            // 只有真正收到的钱能退
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }

        BigDecimal amount = form.getAmount().setScale(SCALE, RoundingMode.HALF_UP);

        // 业务来源校验必须早于 provider.refund()：渠道的钱一旦退出去就无法回退，
        // 那时才由 Finance 侧拒绝已经没有意义 —— 数据库拒绝改变不了资金已经动了的事实。
        //
        // 正式退款必须关联售后退款事实：付款事实要挂在业务退款单上，
        // 无来源的渠道退款在 Finance 侧落不下账。
        requireOrderRefundSource(form, transaction, amount);

        BigDecimal refundable = refundableOf(transaction);
        if (amount.compareTo(refundable) > 0) {
            throw new ScmBusinessException(FinanceErrorCode.REFUND_ALLOCATION_REQUIRED);
        }

        String operator = ScmOperator.current();
        PaymentRefundEntity refund = new PaymentRefundEntity();
        refund.setRefundNo(paymentNumberGenerator.nextRefundNo());
        refund.setIntentId(transaction.getIntentId());
        refund.setTransactionId(transaction.getId());
        refund.setProvider(transaction.getProvider());
        refund.setAmount(amount);
        refund.setSourceType(form.getSourceType());
        refund.setSourceId(form.getSourceId());
        refund.setStatus(ScmPaymentRefundStatusEnum.CREATED.name());
        refund.setMockScenario(resolveScenario(transaction.getProvider(), form.getMockScenario()));
        refund.setReason(form.getReason());
        refund.setCreatedBy(operator);
        refund.setUpdatedBy(operator);
        paymentRefundDao.insert(refund);

        providerRegistry.require(transaction.getProvider());
        // 本地意图与固定退款号先提交；恢复任务只能读取已提交记录，渠道调用不在这个事务中发生。
        if (paymentRefundDao.markProcessing(refund.getId(), null, operator) != 1) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        PaymentRefundEntity saved = paymentRefundDao.selectById(refund.getId());
        idempotencyService.complete(claim, "PAYMENT_REFUND", saved.getId(), saved);
        return saved;
    }

    /**
     * 把渠道的退款结果落到状态机上。
     *
     * <p>
     * 发起时与回调时<b>复用同一段</b>判定：两处各写一份，迟早会出现「回调说成功、本地还停在处理中」。受影响行数 != 1 说明这笔退款已被处理过（重复回调），直接返回，由幂等层回答「已处理」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyOutcome(Long refundId, ScmPaymentProvider.Outcome outcome, String providerRefundNo,
        BigDecimal providerAmount, String failureCode, String failureMessage, String operator) {
        lockRefundOrder(refundId);
        PaymentRefundEntity current = paymentRefundDao.lockById(refundId);
        if (current == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_NOT_FOUND);
        }
        if (providerRefundNo != null
            && paymentRefundDao.updateProviderReference(refundId, providerRefundNo, operator) != 1) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        if (ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(current.getStatus())
            && (outcome != ScmPaymentProvider.Outcome.SUCCEEDED || providerAmount == null
                || current.getProviderAmount().compareTo(providerAmount) != 0)) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_MISMATCH);
        }
        if (ScmPaymentRefundStatusEnum.FAILED.name().equals(current.getStatus())
            && outcome != ScmPaymentProvider.Outcome.FAILED) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        switch (outcome) {
            case SUCCEEDED -> {
                if (providerAmount == null || providerAmount.signum() <= 0) {
                    // 没有渠道实退金额就不算退成功：硬写会让「退了多少」无从回答，
                    // 无法据此登记资金反向事实。宁可失败，让渠道重试带全信息。
                    throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_PROVIDER_AMOUNT_MISSING);
                }
                paymentRefundDao.markSucceeded(refundId, providerRefundNo,
                    providerAmount.setScale(SCALE, RoundingMode.HALF_UP), operator);
                // <b>无论是否首次都确保 Finance 付款事实存在</b>：这样「渠道已退、本地在上次落账前失败」
                // 可以靠下一次回调恢复 —— 与收款的 registerFinanceReceipt 同一条纪律。
                registerFinancePayment(refundId, operator);
            }
            case FAILED -> {
                paymentRefundDao.markFailed(refundId, failureCode, failureMessage, operator);
                syncFundingAllocation(refundId, operator);
            }
            case PENDING -> {
                // 渠道还没给结果：停在 PROCESSING，等回调
            }
            default -> throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
    }

    /**
     * 渠道退款前先锁原退款单，检查已完成状态、退款客户、金额和既有付款记录。线上与人工退款付款互斥，同一退款单不能重复支付。
     */
    private void requireOrderRefundSource(PaymentRefundCreateForm form, PaymentTransactionEntity transaction,
        BigDecimal amount) {
        if (!ScmPaymentSourceTypeEnum.ORDER_REFUND.name().equals(form.getSourceType()) || form.getSourceId() == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        PaymentOrderRefundFact refund = paymentSourceDao.lockOrderRefund(form.getSourceId());
        if (refund == null || !refund.completed()) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        if (refundFundingAllocationDao.existsByOrder(refund.orderId())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_DUPLICATED);
        }
        PaymentIntentEntity intent = paymentIntentDao.selectById(transaction.getIntentId());
        if (intent == null || !refund.customerId().equals(intent.getCustomerId())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        // <b>范围边界</b>：只有订单支付的交易能走订单退款链。
        // 充值支付（BALANCE_RECHARGE）若从这里退走渠道的钱，钱包里的 RECHARGE 并不会被撤销 ——
        // 结果是「公司退了 100、钱包还剩 100」，等于白送一笔余额。
        // 充值退款要单独设计（先查未消费余额 → DEBIT 钱包 → 再退渠道），不借这条链。
        if (!ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(intent.getSourceType())
            || !refund.orderId().equals(intent.getSourceId())
            || !ScmPaymentMethodEnum.ONLINE.name().equals(intent.getMethod())
            || !transaction.getProvider().equals(intent.getProvider())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        if (amount.compareTo(refund.refundAmount().setScale(SCALE, RoundingMode.HALF_UP)) != 0) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_MISMATCH);
        }
        // 与人工退款付款互斥：同一张退款单被退两次（人工一次、渠道一次）是最难查的一类账
        if (paymentSourceDao.selectActiveRefundPayment(refund.refundId()) != null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_DUPLICATED);
        }
        // 线上重复：同一张退款单只映射一笔渠道退款
        if (paymentRefundDao.selectBySource(form.getSourceType(), form.getSourceId()) != null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_DUPLICATED);
        }
    }

    /**
     * 渠道退款成功后登记一笔客户退款付款。以退款单 ID 防重，不重复冲减应收。
     */
    private void registerFinancePayment(Long refundId, String operator) {
        PaymentRefundEntity refund = paymentRefundDao.selectById(refundId);
        if (refund == null || !ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(refund.getStatus())
            || refund.getProviderAmount() == null) {
            return;
        }
        if (ScmBalanceSourceTypeEnum.REFUND_FUNDING_ALLOCATION.name().equals(refund.getSourceType())) {
            syncFundingAllocation(refund, operator);
            return;
        }
        if (!ScmPaymentSourceTypeEnum.ORDER_REFUND.name().equals(refund.getSourceType())
            || refund.getSourceId() == null) {
            return;
        }
        if (refund.getProviderAmount().compareTo(refund.getAmount()) != 0) {
            log.warn("渠道实退金额与申请金额不一致，暂不生成 Finance 付款事实：refundNo={} 申请={} 实退={}", refund.getRefundNo(),
                refund.getAmount(), refund.getProviderAmount());
            return;
        }
        PaymentIntentEntity intent = paymentIntentDao.selectById(refund.getIntentId());
        if (intent == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_NOT_FOUND);
        }
        financePaymentService.registerFromPaymentRefund(
            new FinancePaymentRefundFact(refund.getId(), refund.getSourceId(), intent.getCustomerId(),
                refund.getProviderAmount(), refund.getRefundedAt(), refund.getProviderRefundNo()),
            operator);
    }

    private void syncFundingAllocation(Long paymentRefundId, String operator) {
        syncFundingAllocation(paymentRefundDao.selectById(paymentRefundId), operator);
    }

    private void syncFundingAllocation(PaymentRefundEntity refund, String operator) {
        syncFundingAllocation(refund, operator, true);
    }

    private void syncFundingAllocation(PaymentRefundEntity refund, String operator, boolean finishOrderRefund) {
        if (refund == null || !ScmBalanceSourceTypeEnum.REFUND_FUNDING_ALLOCATION.name().equals(refund.getSourceType())
            || refund.getSourceId() == null) {
            return;
        }
        var allocation = refundFundingAllocationDao.selectById(refund.getSourceId());
        if (allocation == null) {
            return;
        }
        if (ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(refund.getStatus())) {
            if (refund.getProviderAmount() == null || refund.getProviderAmount().compareTo(refund.getAmount()) != 0
                || refund.getProviderAmount().compareTo(allocation.getAllocatedAmount()) != 0) {
                log.warn("退款分摊实退金额不一致，保留渠道事实并等待对账：refundNo={} 分摊={} 实退={}", refund.getRefundNo(),
                    allocation.getAllocatedAmount(), refund.getProviderAmount());
                return;
            }
            refundFundingAllocationDao.markSucceeded(allocation.getId(), refund.getId(), operator);
            if (finishOrderRefund) {
                refundFundingCompletionService.complete(allocation.getOrderRefundId(), operator);
            }
        } else if (ScmPaymentRefundStatusEnum.FAILED.name().equals(refund.getStatus())) {
            refundFundingAllocationDao.markFailed(allocation.getId(), refund.getFailureCode(),
                refund.getFailureMessage(), operator);
        }
    }

    /**
     * 单条 ONLINE 准备失败只回滚到保存点；调用方持有订单锁并记录失败分摊。
     */
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public PaymentRefundEntity executeAllocation(Long allocationId, Long orderRefundId,
        PaymentTransactionEntity transaction, BigDecimal amount, String reason, String mockScenario) {
        String sourceType = ScmBalanceSourceTypeEnum.REFUND_FUNDING_ALLOCATION.name();
        PaymentRefundEntity existing = paymentRefundDao.selectBySource(sourceType, allocationId);
        if (existing != null && ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(existing.getStatus())) {
            // 外层执行完所有来源后统一登记 Finance；保存点内只恢复这条分摊行。
            syncFundingAllocation(existing, ScmOperator.current(), false);
            return existing;
        }
        if (transaction == null || !ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(transaction.getStatus())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        BigDecimal value = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (value.signum() <= 0 || value.compareTo(refundableOf(transaction)) > 0) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_EXCEEDED);
        }
        String operator = ScmOperator.current();
        PaymentRefundEntity refund = existing;
        if (refund == null) {
            refund = new PaymentRefundEntity();
            refund.setRefundNo(paymentNumberGenerator.nextRefundNo());
            refund.setIntentId(transaction.getIntentId());
            refund.setTransactionId(transaction.getId());
            refund.setProvider(transaction.getProvider());
            refund.setAmount(value);
            refund.setSourceType(sourceType);
            refund.setSourceId(allocationId);
            refund.setStatus(ScmPaymentRefundStatusEnum.CREATED.name());
            refund.setMockScenario(resolveScenario(transaction.getProvider(), mockScenario));
            refund.setReason(reason);
            refund.setCreatedBy(operator);
            refund.setUpdatedBy(operator);
            paymentRefundDao.insert(refund);
        } else if (ScmPaymentRefundStatusEnum.FAILED.name().equals(refund.getStatus())) {
            paymentRefundDao.resetFailed(refund.getId(), operator,
                resolveScenario(transaction.getProvider(), mockScenario));
            refund = paymentRefundDao.lockById(refund.getId());
        } else {
            return refund;
        }
        providerRegistry.require(transaction.getProvider());
        if (paymentRefundDao.markProcessing(refund.getId(), null, operator) != 1) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        return paymentRefundDao.selectById(refund.getId());
    }

    @Transactional(readOnly = true)
    public PaymentRefundEntity requireVisibleRefund(Long refundId) {
        PaymentRefundEntity refund = paymentRefundDao.selectById(refundId);
        PaymentIntentEntity intent = refund == null ? null : paymentIntentDao.selectById(refund.getIntentId());
        var order = intent == null ? null : paymentSourceDao.selectOrder(intent.getSourceId());
        var scope = dataScopeService.resolve();
        if (order == null || !ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(intent.getSourceType())
            || !scope.getOrderSellerScope().allows(order.sellerId())
            || !paymentSourceDao.customerVisible(intent.getCustomerId(), scope.getCustomerSellerScope())) {
            throw new ScmDataScopeException();
        }
        return refund;
    }

    @Transactional(readOnly = true)
    public List<PaymentRefundRecoveryLogVO> recoveryLogs(Long refundId) {
        requireVisibleRefund(refundId);
        return paymentRefundDao.recoveryLogs(refundId);
    }

    private void lockRefundOrder(Long refundId) {
        PaymentRefundEntity refund = paymentRefundDao.selectById(refundId);
        PaymentIntentEntity intent = refund == null ? null : paymentIntentDao.selectById(refund.getIntentId());
        if (intent == null || !ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(intent.getSourceType())
            || paymentSourceDao.lockOrder(intent.getSourceId()) == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        paymentSourceDao.lockOrderRefunds(intent.getSourceId());
    }

    /**
     * 按渠道退款号定位退款（退款回调的匹配入口）。
     */
    @Transactional(readOnly = true)
    public PaymentRefundEntity findByProviderRefundNo(String provider, String providerRefundNo) {
        return paymentRefundDao.selectByProviderRefundNo(provider, providerRefundNo);
    }

    /**
     * 可退金额以渠道实际收款减去已成功及处理中退款为准，不使用本地应付金额猜测。缺少可靠渠道实收金额时拒绝退款。
     */
    private BigDecimal refundableOf(PaymentTransactionEntity transaction) {
        if (transaction.getProviderAmount() == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_PROVIDER_AMOUNT_MISSING);
        }
        BigDecimal refunded = paymentRefundDao.sumReservedByTransaction(transaction.getId());
        return transaction.getProviderAmount().subtract(refunded == null ? BigDecimal.ZERO : refunded)
            .max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String resolveScenario(String provider, String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return null;
        }
        if (!ScmPaymentProviderEnum.MOCK.name().equals(provider)
            || (ScmPaymentMockScenarioEnum.of(scenario) != ScmPaymentMockScenarioEnum.SUCCESS
                && ScmPaymentMockScenarioEnum.of(scenario) != ScmPaymentMockScenarioEnum.FAILURE)) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_MOCK_SCENARIO_INVALID);
        }
        return scenario;
    }
}
