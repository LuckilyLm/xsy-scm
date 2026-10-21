package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmCreditPeriodTypeEnum;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCreditService {
    private final CustomerService customerService;
    private final FinanceCreditExposureQueryService financeCreditExposureQueryService;
    private final ScmDataScopeService dataScopeService;

    public CustomerCreditCheckVO check(Long customerId, BigDecimal requestedAmount) {
        var customer = customerService.requireTradable(customerId);
        if (!dataScopeService.resolve().getOrderSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return calculate(customer, requestedAmount);
    }

    public CustomerCreditCheckVO checkSettlement(Long settlementCustomerId, BigDecimal requestedAmount) {
        return calculateSettlement(customerService.require(settlementCustomerId), requestedAmount, true,
                dataScopeService.resolve().getOrderSellerScope());
    }

    /** Called after the order owner is authorized; uses its frozen settlement account. */
    public CustomerCreditCheckVO checkForSettlementConfirmation(Long settlementCustomerId, BigDecimal requestedAmount) {
        var settlement = customerService.lockCreditAccount(settlementCustomerId);
        return calculateSettlement(settlement, requestedAmount, false,
                dataScopeService.resolve().getOrderSellerScope());
    }

    /** 微信小程序结算时按完整结算主体核对授信与逾期情况，最终结果仍需在订单确认时重新校验。 */
    public CustomerCreditCheckVO checkSelfSettlement(Long settlementCustomerId, BigDecimal requestedAmount) {
        return calculateSettlement(customerService.require(settlementCustomerId), requestedAmount, true,
                ScmValueScope.all());
    }

    private CustomerCreditCheckVO calculate(CustomerEntity customer, BigDecimal requestedAmount) {
        var settlement = customerService.requireSettlementCustomer(customer);
        return calculateSettlement(settlement, requestedAmount, true, dataScopeService.resolve().getOrderSellerScope());
    }

    private CustomerCreditCheckVO calculateSettlement(CustomerEntity settlement, BigDecimal requestedAmount,
            boolean requireVisibleExposure, ScmValueScope orderSellerScope) {
        if (requestedAmount != null && requestedAmount.signum() < 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        var exposureFacts = financeCreditExposureQueryService.summarize(settlement.getId(), orderSellerScope);
        if (requireVisibleExposure && !Boolean.TRUE.equals(exposureFacts.getExposureVisible())) {
            throw new ScmDataScopeException();
        }
        BigDecimal open = exposureFacts.getOpenReceivableAmount();
        BigDecimal confirmed = exposureFacts.getConfirmedOrderAmount();
        BigDecimal requested = requestedAmount == null ? BigDecimal.ZERO : requestedAmount;
        BigDecimal exposure = open.add(confirmed).add(requested);
        var overdueDate = exposureFacts.getEarliestOverdueDate();
        boolean overLimit = settlement.getCreditLimit() != null && settlement.getCreditLimit().signum() > 0
                && exposure.compareTo(settlement.getCreditLimit()) > 0;
        CustomerCreditCheckVO result = new CustomerCreditCheckVO();
        result.setSettlementCustomerId(settlement.getId());
        result.setCreditLimit(settlement.getCreditLimit());
        result.setOpenReceivableAmount(open);
        result.setConfirmedOrderAmount(confirmed);
        result.setRequestedOrderAmount(requested);
        result.setProjectedExposure(exposure);
        result.setOverLimit(overLimit);
        result.setOverdue(overdueDate != null);
        result.setEarliestOverdueDate(overdueDate);
        if (ScmCreditPeriodTypeEnum.BY_AMOUNT.name().equals(settlement.getCreditPeriodType())
                && settlement.getCreditAmountThreshold() != null
                && exposure.compareTo(settlement.getCreditAmountThreshold()) >= 0) {
            result.setAmountThresholdHint("授信占用已达到金额阈值 " + settlement.getCreditAmountThreshold().toPlainString());
        }
        result.setAllowed(!overLimit && overdueDate == null);
        return result;
    }
}
