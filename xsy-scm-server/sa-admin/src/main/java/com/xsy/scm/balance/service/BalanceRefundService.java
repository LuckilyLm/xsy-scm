package com.xsy.scm.balance.service;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.balance.dao.BalanceRefundSourceDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.domain.dto.BalanceRefundFact;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.form.BalanceRefundForm;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 纯余额售后返还；只追加钱包 CREDIT，不产生 Finance 付款或反向核销。 */
@Service
@RequiredArgsConstructor
public class BalanceRefundService {
    private final BalanceRefundSourceDao balanceRefundSourceDao;
    private final CustomerBalanceMovementDao customerBalanceMovementDao;
    private final CustomerBalanceService customerBalanceService;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /** 订单退款完成事务中的自动派生；线上、线下或混合订单交给原资金路径处理。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void refundOnCompletion(Long refundId) {
        apply(lockSource(refundId), false);
    }

    /** 历史完成单或响应丢失的显式重试入口；不能自行指定返还金额和钱包。 */
    @Transactional(rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity refund(BalanceRefundForm form, String key) {
        BalanceRefundFact fact = lockSource(form.getRefundId());
        if (!dataScopeService.resolve().getOrderSellerScope().allows(fact.sellerId())) {
            throw new ScmDataScopeException();
        }
        var claim = idempotencyService.claim("BALANCE_ORDER_REFUND:" + fact.refundId(), key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, CustomerBalanceMovementEntity.class);
        }
        CustomerBalanceMovementEntity movement = apply(fact, true);
        idempotencyService.complete(claim, "BALANCE_MOVEMENT", movement.getId(), movement);
        return movement;
    }

    private BalanceRefundFact lockSource(Long refundId) {
        BalanceRefundFact initial = balanceRefundSourceDao.selectRefund(refundId);
        if (initial == null || balanceRefundSourceDao.lockOrder(initial.orderId()) == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        balanceRefundSourceDao.lockOrderRefunds(initial.orderId());
        BalanceRefundFact fact = balanceRefundSourceDao.selectRefund(refundId);
        if (fact == null || !Objects.equals(initial.orderId(), fact.orderId())
                || !ScmOrderRefundStatusEnum.COMPLETED.name().equals(fact.status())
                || fact.completedAt() == null
                || fact.amount() == null || fact.amount().signum() <= 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        return fact;
    }

    private CustomerBalanceMovementEntity apply(BalanceRefundFact refund, boolean explicit) {
        // 共用支付成功事实的身份与金额校验；该 Policy 只读来源，不执行任何财务写入。
        var funding = financeOrderFundingPolicy.requireCompleteOrderFunding(refund.orderId());
        boolean balance = funding.stream().anyMatch(f -> ScmPaymentMethodEnum.BALANCE.name().equals(f.getMethod()));
        boolean online = funding.stream().anyMatch(f -> ScmPaymentMethodEnum.ONLINE.name().equals(f.getMethod()));
        boolean cashAllocated = financeOrderFundingPolicy.hasReceiptFunding(refund.orderId());
        if (!balance || online || cashAllocated) {
            if (explicit) {
                throw new ScmBusinessException((online || cashAllocated) && balance ? FinanceErrorCode.REFUND_ALLOCATION_REQUIRED
                        : BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
            }
            return null;
        }
        if (refund.settlementCustomerId() == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        if (balanceRefundSourceDao.hasPendingFunding(refund.orderId())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_PENDING_PAYMENT);
        }
        if (balanceRefundSourceDao.hasCashRefund(refund.orderId())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_PATH_CONFLICT);
        }
        for (var fact : funding) {
            if (!Objects.equals(fact.getCustomerId(), refund.customerId())
                    || !Objects.equals(fact.getSettlementCustomerId(), refund.settlementCustomerId())) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
            }
        }
        if (balanceRefundSourceDao.hasInvalidReturns(refund.orderId())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        if (funding.stream().map(f -> f.getMovementId()).distinct().count() != funding.size()) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        BigDecimal principal = funding.stream().map(f -> f.getMovementAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = balanceRefundSourceDao.returnedAmount(refund.orderId());
        var existing = customerBalanceMovementDao.selectBySource(ScmBalanceSourceTypeEnum.ORDER_REFUND.name(),
                refund.refundId());
        BigDecimal projected = existing == null ? returned.add(refund.amount()) : returned;
        if (projected.compareTo(principal) > 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_AMOUNT_EXCEEDED);
        }
        return customerBalanceService.refundToSettlement(refund.refundId(), refund.customerId(),
                refund.settlementCustomerId(), refund.amount(), refund.completedAt());
    }
}
