package com.xsy.scm.finance.service;

import com.xsy.scm.balance.constant.ScmBalanceDirectionEnum;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.payment.constant.ScmPaymentIntentStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentTransactionStatusEnum;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 系统资金登记与退款共同使用的只读来源校验。 */
@Component
@RequiredArgsConstructor
public class FinanceOrderFundingPolicy {
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;

    public void requireSuccessful(FinanceOrderFundingDto fact) {
        if (fact == null || fact.getCustomerId() == null || fact.getSettlementCustomerId() == null
                || !ScmPaymentIntentStatusEnum.SUCCEEDED.name().equals(fact.getIntentStatus())
                || !ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(fact.getTransactionStatus())
                || !Objects.equals(fact.getProvider(), fact.getTransactionProvider())
                || fact.getProviderAmount() == null || fact.getProviderAmount().signum() <= 0
                || fact.getPaidAt() == null || fact.getAmount() == null || fact.getTransactionAmount() == null
                || fact.getAmount().compareTo(fact.getTransactionAmount()) != 0) {
            throw invalid();
        }
        if (ScmPaymentMethodEnum.BALANCE.name().equals(fact.getMethod())) {
            if (!ScmPaymentProviderEnum.INTERNAL_BALANCE.name().equals(fact.getProvider())
                    || !ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(fact.getSourceType())
                    || fact.getOrderId() == null || fact.getMovementId() == null || fact.getReceiptId() != null
                    || !ScmBalanceMovementTypeEnum.CONSUME.name().equals(fact.getMovementType())
                    || !ScmBalanceDirectionEnum.DEBIT.name().equals(fact.getMovementDirection())
                    || !ScmBalanceSourceTypeEnum.PAYMENT_INTENT.name().equals(fact.getMovementSourceType())
                    || !Objects.equals(fact.getMovementIntentId(), fact.getIntentId())
                    || !Objects.equals(fact.getCustomerId(), fact.getMovementCustomerId())
                    || !Objects.equals(fact.getSettlementCustomerId(), fact.getMovementSettlementCustomerId())
                    || fact.getMovementOccurredAt() == null || !fact.getPaidAt().isEqual(fact.getMovementOccurredAt())
                    || fact.getMovementAmount() == null
                    || fact.getAmount().compareTo(fact.getMovementAmount()) != 0
                    || fact.getAmount().compareTo(fact.getProviderAmount()) != 0) {
                throw invalid();
            }
        } else if (!ScmPaymentMethodEnum.ONLINE.name().equals(fact.getMethod())
                || (ScmPaymentProviderEnum.of(fact.getProvider()) != ScmPaymentProviderEnum.MOCK
                && ScmPaymentProviderEnum.of(fact.getProvider()) != ScmPaymentProviderEnum.WECHAT)) {
            throw invalid();
        }
        if (fact.getReceiptId() != null && (!Objects.equals(fact.getCustomerId(), fact.getReceiptCustomerId())
                || !Objects.equals(fact.getSettlementCustomerId(), fact.getReceiptSettlementCustomerId())
                || fact.getReceiptAmount() == null || fact.getProviderAmount().compareTo(fact.getReceiptAmount()) != 0)) {
            throw invalid();
        }
    }

    public List<FinanceOrderFundingDto> requireCompleteOrderFunding(Long orderId) {
        List<FinanceOrderFundingDto> facts = financeOrderFundingSourceDao.selectOrderFunding(orderId);
        for (FinanceOrderFundingDto fact : facts) {
            requireSuccessful(fact);
            if (!Objects.equals(orderId, fact.getOrderId())
                    || (ScmPaymentMethodEnum.ONLINE.name().equals(fact.getMethod()) && fact.getReceiptId() == null)) {
                throw invalid();
            }
        }
        return facts;
    }

    public void requireCashRefundAllowed(Long orderId, boolean requireOnline) {
        List<FinanceOrderFundingDto> facts = requireCompleteOrderFunding(orderId);
        boolean balance = facts.stream().anyMatch(f -> ScmPaymentMethodEnum.BALANCE.name().equals(f.getMethod()));
        boolean online = facts.stream().anyMatch(f -> ScmPaymentMethodEnum.ONLINE.name().equals(f.getMethod()));
        if (balance) {
            throw new ScmBusinessException(online ? FinanceErrorCode.REFUND_ALLOCATION_REQUIRED
                    : FinanceErrorCode.BALANCE_REFUND_NOT_ENABLED);
        }
        if (requireOnline && !online) {
            throw invalid();
        }
    }

    private static ScmBusinessException invalid() {
        return new ScmBusinessException(FinanceErrorCode.ORDER_FUNDING_INVALID);
    }
}
