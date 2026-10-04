package com.xsy.scm.payment.service;

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
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentRefundCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款服务：支付域**自己的**退款事实。
 *
 * <p>
 * 链路：
 *
 * <pre>
 * 业务退款申请 / 售后退款单
 *        ↓
 * PaymentRefund（CREATED）
 *        ↓
 * ScmPaymentProvider.refund(...)
 *        ↓
 * PaymentRefund（PROCESSING）── 渠道回调 / 查单 ──▶ SUCCEEDED | FAILED
 * </pre>
 *
 * <p>
 * <b>渠道退款成功按实退登记 Finance 付款事实</b>，不冲减或反向应收及历史核销。
 *
 * <p>
 * 三条硬约束：
 * <ol>
 * <li><b>资金来源必须是 {@code PaymentTransaction}</b>，且该交易必须是成功的 —— 没收到钱就退钱是账外行为；</li>
 * <li><b>累计成功退款不得超过原支付成功金额</b>；</li>
 * <li><b>同一业务退款来源只能映射一笔有效退款</b>（表上有唯一索引兜底）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private static final int SCALE = 4;

    private static final String IDEMPOTENCY_SCOPE = "PAYMENT_REFUND_CREATE";

    private final PaymentRefundDao paymentRefundDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentIntentDao paymentIntentDao;

    /** 只读外部事实：订单 / 退款单 / 财务付款，用于来源校验与互斥。 */
    private final PaymentSourceDao paymentSourceDao;

    /**
     * 财务域的系统退款付款入口。依赖方向是 payment → finance；finance 不反向依赖支付域， 因此不构成环（与 payment → finance 收款是同一套做法）。
     */
    private final FinancePaymentService financePaymentService;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;

    private final PaymentNumberGenerator paymentNumberGenerator;

    private final ScmPaymentProviderRegistry providerRegistry;

    private final ScmIdempotencyService idempotencyService;
    private final ScmDataScopeService dataScopeService;

    /**
     * 发起退款。
     *
     * <p>
     * <b>重复请求靠业务幂等键回放首次结果</b>，不是「发现已有就随便返回一个」： 前者回答「这次请求的结果是什么」，后者会在两次请求参数不同时给出一个看似成功的错误答案。
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
            return idempotencyService.replay(claim, PaymentRefundEntity.class);
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

        // **业务来源校验必须早于 provider.refund()**：这里任何一条不成立，渠道的钱都还没动。
        // 留到 3-11b 由 Finance 侧拒绝就晚了 —— 那时渠道已经把钱退出去了，数据库拒绝没有意义。
        //
        // 当前阶段唯一受支持的正式退款来源就是售后退款，因此**强制要求**来源，不接受无来源退款：
        // 无来源的渠道退款会在 Finance 侧落不下付款事实（付款必须挂业务退款单），
        // 而钱那时已经退出去了。将来若需要「渠道技术退款 / 人工补退」，另开内部命令，
        // 不要借这个正式入口绕开业务退款事实。
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

        ScmPaymentProvider provider = providerRegistry.require(transaction.getProvider());
        ScmPaymentProvider.RefundResult result = provider.refund(
                new ScmPaymentProvider.RefundRequest(refund.getRefundNo(), transaction.getProviderTransactionNo(),
                        amount, form.getReason(), ScmPaymentMockScenarioEnum.of(refund.getMockScenario())));

        // 发起过就是发起过：先落 PROCESSING，再按渠道结果推进（与支付意图同一套纪律）
        if (paymentRefundDao.markProcessing(refund.getId(), result.providerRefundNo(), operator) != 1) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
        applyOutcome(refund.getId(), result.outcome(), result.providerRefundNo(), result.amount(), result.failureCode(),
                result.failureMessage(), operator);
        PaymentRefundEntity saved = paymentRefundDao.selectById(refund.getId());
        idempotencyService.complete(claim, "PAYMENT_REFUND", saved.getId(), saved);
        return saved;
    }

    /**
     * 把渠道的退款结果落到状态机上。
     *
     * <p>
     * 发起时与回调时**复用同一段**判定：两处各写一份，迟早会出现「回调说成功、本地还停在处理中」。 受影响行数 != 1 说明这笔退款已被处理过（重复回调），直接返回，由幂等层回答「已处理」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyOutcome(Long refundId, ScmPaymentProvider.Outcome outcome, String providerRefundNo,
            BigDecimal providerAmount, String failureCode, String failureMessage, String operator) {
        switch (outcome) {
            case SUCCEEDED -> {
                if (providerAmount == null || providerAmount.signum() <= 0) {
                    // 没有渠道实退金额就不算退成功：硬写会让「退了多少」无从回答，
                    // 3-11b 也无法据此登记资金反向事实。宁可失败，让渠道重试带全信息。
                    throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_PROVIDER_AMOUNT_MISSING);
                }
                paymentRefundDao.markSucceeded(refundId, providerRefundNo,
                        providerAmount.setScale(SCALE, RoundingMode.HALF_UP), operator);
                // **无论是否首次都确保 Finance 付款事实存在**：这样「渠道已退、本地在上次落账前失败」
                // 可以靠下一次回调恢复 —— 与收款的 registerFinanceReceipt 同一条纪律。
                registerFinancePayment(refundId, operator);
            }
            case FAILED -> paymentRefundDao.markFailed(refundId, failureCode, failureMessage, operator);
            case PENDING -> {
                // 渠道还没给结果：停在 PROCESSING，等回调
            }
            default -> throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_STATE_INVALID);
        }
    }

    /**
     * 业务退款来源（{@code ORDER_REFUND}）的前置校验。
     *
     * <p>
     * 顺序即纪律，且**必须在 {@code provider.refund()} 之前**完成：
     * <ol>
     * <li>锁 {@code order_refund} —— 线上退款与人工退款付款（财务域 {@code CUSTOMER + ORDER_REFUND}） 必须互斥，两边锁同一行才能关掉「先查后写」的窗口；</li>
     * <li>退款单必须存在且 {@code COMPLETED}；</li>
     * <li>退款对象必须与原支付客户一致（否则会把 A 的付款退给 B）；</li>
     * <li>提交金额必须与应退额**逐值一致**（差一分钱，两条路径就各退一部分）；</li>
     * <li>不存在人工登记的退款付款；</li>
     * <li>不存在已有的线上退款。</li>
     * </ol>
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
        PaymentIntentEntity intent = paymentIntentDao.selectById(transaction.getIntentId());
        if (intent == null || !refund.customerId().equals(intent.getCustomerId())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        // **P0 边界**：只有订单支付的交易能走订单退款链。
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
     * 退款成功 → Finance 付款事实（ADM-12 3-11b）。
     *
     * <p>
     * 复用财务域 {@code finance_payment} 的 {@code CUSTOMER + ORDER_REFUND}，唯一来源键是
     * {@code source_id = order_refund.id}：无论本地被驱动多少次，Finance 只有一条正常付款事实。
     *
     * <p>
     * <b>金额不一致时只保留渠道成功事实，不生成 Finance 付款、也不回滚。</b> 渠道实际退了 98 而业务应退 100 是可能发生的；若因此把整笔已验签的退款成功回滚，
     * 每次重复回调都会因为同一个永久差异失败，本地永远停在「退款处理中」， 反而丢掉「渠道确实已经退钱」这个最重要的事实。这类记录天然可查
     * （{@code SUCCEEDED AND provider_amount <> amount}），留给后续退款对账处理。
     */
    private void registerFinancePayment(Long refundId, String operator) {
        PaymentRefundEntity refund = paymentRefundDao.selectById(refundId);
        if (refund == null || !ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(refund.getStatus())
                || refund.getProviderAmount() == null) {
            return;
        }
        if (!ScmPaymentSourceTypeEnum.ORDER_REFUND.name().equals(refund.getSourceType())
                || refund.getSourceId() == null) {
            // 没有业务退款单就落不下付款事实（付款必须挂来源）；当前入口已强制要求来源，这里只是兜底
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
                        refund.getProviderAmount(), refund.getRefundedAt(), refund.getProviderRefundNo()));
    }

    /** 按渠道退款号定位退款（退款回调的匹配入口）。 */
    @Transactional(readOnly = true)
    public PaymentRefundEntity findByProviderRefundNo(String provider, String providerRefundNo) {
        return paymentRefundDao.selectByProviderRefundNo(provider, providerRefundNo);
    }

    /**
     * 可退本金 = **渠道实际成功捕获/结算的金额** − 已成功退款合计。
     *
     * <p>
     * 刻意**不** fallback 到本地应付金额：本地应付 100、渠道实收 98 时，按 100 退必然被渠道拒； 而更糟的是「本地根本没记下渠道实收」时静默按 100 退 —— 那会掩盖支付结果落库不完整，
     * 等接真实渠道时才以「退款被渠道拒」的形式暴露出来。缺可信金额就**拒绝退款**。
     */
    private BigDecimal refundableOf(PaymentTransactionEntity transaction) {
        if (transaction.getProviderAmount() == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_PROVIDER_AMOUNT_MISSING);
        }
        BigDecimal refunded = paymentRefundDao.sumSucceededByTransaction(transaction.getId());
        return transaction.getProviderAmount().subtract(refunded == null ? BigDecimal.ZERO : refunded)
                .max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String resolveScenario(String provider, String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return null;
        }
        if (!ScmPaymentProviderEnum.MOCK.name().equals(provider) || ScmPaymentMockScenarioEnum.of(scenario) == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_MOCK_SCENARIO_INVALID);
        }
        return scenario;
    }
}
