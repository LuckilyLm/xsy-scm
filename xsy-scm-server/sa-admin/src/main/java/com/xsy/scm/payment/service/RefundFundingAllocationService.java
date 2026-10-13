package com.xsy.scm.payment.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmRefundFundingAllocationStatusEnum;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.domain.vo.RefundFundingAllocationVO;
import com.xsy.scm.payment.domain.vo.RefundFundingResultVO;
import com.xsy.scm.payment.support.RefundBalanceSink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单级资金退款：先冻结比例分摊，再逐来源幂等执行。 */
@Service
@RequiredArgsConstructor
public class RefundFundingAllocationService {
    private static final int SCALE = 4;
    private static final String IDEMPOTENCY_SCOPE = "REFUND_FUNDING_EXECUTE";

    private final RefundFundingAllocator allocator;
    private final PaymentSourceDao paymentSourceDao;
    private final RefundFundingAllocationDao allocationDao;
    private final PaymentTransactionDao paymentTransactionDao;
    private final PaymentRefundService paymentRefundService;
    private final RefundBalanceSink refundBalanceSink;
    private final FinanceOrderFundingPolicy fundingPolicy;
    private final RefundFundingCompletionService refundFundingCompletionService;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    @Transactional(rollbackFor = Exception.class)
    public RefundFundingResultVO execute(RefundFundingExecuteForm form, String key) {
        PaymentOrderRefundFact initial = paymentSourceDao.selectOrderRefund(form.getRefundId());
        if (initial == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        var order = paymentSourceDao.lockOrder(initial.orderId());
        if (order == null || !dataScopeService.resolve().getOrderSellerScope().allows(order.sellerId())) {
            throw new ScmDataScopeException();
        }
        paymentSourceDao.lockOrderRefunds(initial.orderId());
        PaymentOrderRefundFact locked = paymentSourceDao.selectOrderRefund(form.getRefundId());
        if (locked == null || !initial.orderId().equals(locked.orderId()) || !locked.completed()
                || locked.completedAt() == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        initial = locked;
        paymentSourceDao.lockOrderFunding(initial.orderId());
        requireVisible(initial.customerId());
        List<RefundFundingAllocationEntity> existingPlan = allocationDao.lockByRefund(initial.refundId());
        var claim = idempotencyService
                .claim(IDEMPOTENCY_SCOPE + ":" + initial.refundId() + ":" + retryRound(existingPlan), key, form);
        if (claim.replay()) {
            requireVisible(initial.customerId());
            return idempotencyService.replay(claim, RefundFundingResultVO.class);
        }
        List<RefundFundingAllocationEntity> rows = existingPlan;
        // 旧退款未记录逐资金来源的分摊，不能把这部分本金再次交给新路径使用。
        if (paymentSourceDao.hasLegacyRefundFunding(initial.orderId())) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_DUPLICATED);
        }
        if (paymentSourceDao.selectActiveRefundPayment(initial.refundId()) != null && rows.isEmpty()) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_DUPLICATED);
        }
        if (rows.isEmpty()) {
            rows = freeze(initial, fundingPolicy.requireCompleteOrderFunding(initial.orderId()));
        }
        executeRows(initial, rows, form.getMockScenario());
        rows = allocationDao.lockByRefund(initial.refundId());
        RefundFundingResultVO result = result(initial, rows);
        result.setFinancePaymentId(refundFundingCompletionService.complete(initial.refundId(), ScmOperator.current()));
        idempotencyService.complete(claim, "REFUND_FUNDING", initial.refundId(), result);
        return result;
    }

    @Transactional(readOnly = true)
    public RefundFundingResultVO detail(Long refundId) {
        PaymentOrderRefundFact refund = paymentSourceDao.selectOrderRefund(refundId);
        if (refund == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        var order = paymentSourceDao.selectOrder(refund.orderId());
        if (order == null || !dataScopeService.resolve().getOrderSellerScope().allows(order.sellerId())) {
            throw new ScmDataScopeException();
        }
        requireVisible(refund.customerId());
        return result(refund, allocationDao.listByRefund(refundId));
    }

    private List<RefundFundingAllocationEntity> freeze(PaymentOrderRefundFact refund,
            List<FinanceOrderFundingDto> facts) {
        List<Source> sources = facts.stream()
                .sorted(Comparator.comparing(FinanceOrderFundingDto::getPaidAt)
                        .thenComparing(FinanceOrderFundingDto::getMethod)
                        .thenComparing(FinanceOrderFundingDto::getTransactionId))
                .map(fact -> source(fact)).filter(source -> source.remaining().signum() > 0).toList();
        BigDecimal total = sources.stream().map(Source::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requested = refund.refundAmount().setScale(SCALE, RoundingMode.UNNECESSARY);
        if (sources.isEmpty() || requested.signum() <= 0 || requested.compareTo(total) > 0) {
            throw new ScmBusinessException(PaymentErrorCode.REFUND_FUNDING_INVALID);
        }
        List<BigDecimal> amounts = allocator.allocate(requested, sources.stream().map(Source::remaining).toList());
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        for (int i = 0; i < sources.size(); i++) {
            if (amounts.get(i).signum() == 0) {
                continue;
            }
            Source source = sources.get(i);
            RefundFundingAllocationEntity row = new RefundFundingAllocationEntity();
            row.setOrderRefundId(refund.refundId());
            row.setOrderId(refund.orderId());
            row.setCustomerId(refund.customerId());
            row.setSettlementCustomerId(source.settlementCustomerId());
            row.setSourceMethod(source.method());
            row.setSourceId(source.sourceId());
            row.setSourcePrincipal(source.remaining());
            row.setAllocatedAmount(amounts.get(i));
            row.setStatus(ScmRefundFundingAllocationStatusEnum.PENDING.name());
            row.setSourceSucceededAt(source.succeededAt());
            row.setSortOrder(i);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            allocationDao.insertIgnoreDuplicate(row);
        }
        return allocationDao.lockByRefund(refund.refundId());
    }

    private Source source(FinanceOrderFundingDto fact) {
        String method = fact.getMethod();
        Long sourceId = ScmPaymentMethodEnum.BALANCE.name().equals(method)
                ? fact.getMovementId()
                : fact.getTransactionId();
        BigDecimal principal = ScmPaymentMethodEnum.BALANCE.name().equals(method)
                ? fact.getMovementAmount()
                : fact.getProviderAmount();
        BigDecimal allocated = allocationDao.sumAllocatedBySource(method, sourceId);
        return new Source(method, sourceId, fact.getSettlementCustomerId(), fact.getPaidAt(),
                principal.subtract(allocated == null ? BigDecimal.ZERO : allocated).max(BigDecimal.ZERO));
    }

    private void executeRows(PaymentOrderRefundFact refund, List<RefundFundingAllocationEntity> rows,
            String mockScenario) {
        String operator = ScmOperator.current();
        for (RefundFundingAllocationEntity row : rows) {
            if (ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name().equals(row.getStatus())) {
                continue;
            }
            if (ScmRefundFundingAllocationStatusEnum.PROCESSING.name().equals(row.getStatus())) {
                continue;
            }
            if (ScmRefundFundingAllocationStatusEnum.FAILED.name().equals(row.getStatus())) {
                allocationDao.resetFailed(row.getId(), operator);
            }
            allocationDao.markProcessing(row.getId(), operator);
            try {
                Long resultId;
                if (ScmPaymentMethodEnum.BALANCE.name().equals(row.getSourceMethod())) {
                    // 返还时刻取「执行时刻」，不取 refund.completedAt()：分摊只由人工与重试入口触发，
                    // 钱包真正加钱发生在这一行执行的时候，两者可以相隔很久。旧钱包路径
                    // （CustomerBalanceService.refundToSettlement）与退款完成同事务，才要求 occurred_at 等于
                    // completed_at 并被 hasInvalidReturns 校验；本路径没有那条约束，沿用业务完成时间会让
                    // 客户流水与商城「资金到账时间」都显示成退款单完成时刻。
                    resultId = refundBalanceSink.refundAllocation(row.getId(), refund.customerId(),
                            row.getSettlementCustomerId(), row.getAllocatedAmount(), OffsetDateTime.now());
                } else {
                    var transaction = paymentTransactionDao.lockById(row.getSourceId());
                    paymentRefundService.executeAllocation(row.getId(), refund.refundId(), transaction,
                            row.getAllocatedAmount(), "售后退款 " + refund.refundNo(), mockScenario);
                    // ONLINE 的成功与金额差异只由支付结果同步推进，不能仅凭渠道成功覆盖其判定。
                    continue;
                }
                allocationDao.markSucceeded(row.getId(), resultId, operator);
            } catch (RuntimeException exception) {
                allocationDao.markFailed(row.getId(), exception.getClass().getSimpleName(), exception.getMessage(),
                        operator);
            }
        }
    }

    private RefundFundingResultVO result(PaymentOrderRefundFact refund, List<RefundFundingAllocationEntity> rows) {
        RefundFundingResultVO result = new RefundFundingResultVO();
        result.setRefundId(refund.refundId());
        result.setRefundAmount(refund.refundAmount());
        result.setSucceededAmount(rows.stream()
                .filter(row -> ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name().equals(row.getStatus()))
                .map(RefundFundingAllocationEntity::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setStatus(status(rows));
        result.setFinancePaymentId(paymentSourceDao.selectActiveRefundPayment(refund.refundId()));
        result.setAllocations(rows.stream().map(this::vo).toList());
        return result;
    }

    private static String status(List<RefundFundingAllocationEntity> rows) {
        if (rows.isEmpty()) {
            return ScmRefundFundingAllocationStatusEnum.PENDING.name();
        }
        if (rows.stream()
                .allMatch(row -> ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name().equals(row.getStatus()))) {
            return ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name();
        }
        if (rows.stream().anyMatch(row -> ScmRefundFundingAllocationStatusEnum.FAILED.name().equals(row.getStatus()))) {
            return ScmRefundFundingAllocationStatusEnum.FAILED.name();
        }
        return rows.stream()
                .anyMatch(row -> ScmRefundFundingAllocationStatusEnum.PROCESSING.name().equals(row.getStatus()))
                        ? ScmRefundFundingAllocationStatusEnum.PROCESSING.name()
                        : ScmRefundFundingAllocationStatusEnum.PENDING.name();
    }

    private RefundFundingAllocationVO vo(RefundFundingAllocationEntity row) {
        RefundFundingAllocationVO vo = new RefundFundingAllocationVO();
        vo.setAllocationId(row.getId());
        vo.setSourceMethod(row.getSourceMethod());
        vo.setSourceId(row.getSourceId());
        vo.setSourcePrincipal(row.getSourcePrincipal());
        vo.setAllocatedAmount(row.getAllocatedAmount());
        vo.setStatus(row.getStatus());
        vo.setResultSourceId(row.getResultSourceId());
        vo.setFailureCode(row.getFailureCode());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setSourceSucceededAt(row.getSourceSucceededAt());
        return vo;
    }

    private void requireVisible(Long customerId) {
        if (!paymentSourceDao.customerVisible(customerId, dataScopeService.resolve().getCustomerSellerScope())) {
            throw new ScmDataScopeException();
        }
    }

    private static long retryRound(List<RefundFundingAllocationEntity> rows) {
        return rows.stream().map(RefundFundingAllocationEntity::getVersion).filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
    }

    private record Source(String method, Long sourceId, Long settlementCustomerId, OffsetDateTime succeededAt,
            BigDecimal remaining) {
    }
}
