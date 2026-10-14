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

    /**
     * 客户自助场景（商城结算预览）：结算主体由客户本人解析而来，因此本维度不收窄业务员范围。
     *
     * <p>
     * <b>只放开范围，不放开规则</b>：敞口仍取正式事实（正常应收净额 + 已确认未开应收的订单）， 超限与逾期判定复用下面同一段代码。之所以必须传
     * {@link ScmValueScope#all()}：员工侧的范围谓词是为了不让人看到 「只统计了自己可见订单」的残缺敞口，而客户读的是自己的结算主体，本来就应该看到完整敞口。
     *
     * <p>
     * 本方法<b>只读不锁</b>，仅供展示与前置提示；订单确认仍走 {@link #checkForSettlementConfirmation} 在订单锁内做最终裁定。
     */
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
