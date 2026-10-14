package com.xsy.scm.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.manager.CustomerValidator;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.constant.ScmSharedSettlementPolicy;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 商城订单确认时复核结算约定；资金动作由 Balance、Payment 和 Finance 领域执行。 */
@Service
@RequiredArgsConstructor
public class OrderSettlementConfirmationPolicy {

    private final CustomerService customerService;
    private final CustomerTypeService customerTypeService;
    private final OrderCreditService orderCreditService;

    public void validate(SalesOrderEntity order) {
        CustomerEntity buyer = customerService.requireTradable(order.getCustomerId());
        CustomerEntity settlement = customerService.requireSettlementCustomer(buyer);
        if (!Objects.equals(settlement.getId(), order.getSettlementCustomerId())
                || !ScmSettlementMethodEnum.isSupported(order.getSettlementMethod())) {
            throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
        }
        boolean shared = CustomerValidator.sharedSettlement(buyer.getId(), settlement.getId(),
                customerTypeService.require(settlement.getCustomerTypeId()).getTypeCode());
        ScmSettlementMethodEnum method = ScmSettlementMethodEnum.valueOf(order.getSettlementMethod());
        switch (method) {
            case CREDIT -> {
                if (shared && !ScmSharedSettlementPolicy.sharedCheckoutAllowed()) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
                }
                var check = orderCreditService.checkForSettlementConfirmation(settlement.getId(),
                        order.getSettlementTotalAmount());
                if (check.getCreditLimit() == null || check.getCreditLimit().signum() <= 0
                        || !Boolean.TRUE.equals(check.getAllowed())) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_CREDIT_BLOCKED);
                }
            }
            case WALLET -> {
                if (shared && !ScmSharedSettlementPolicy.sharedCheckoutAllowed()) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
                }
                // 钱包资金在支付命令中按实重与已冻结优惠原子扣减；确认本身不冒充付款。
            }
            case COD -> {
                if (!Boolean.TRUE.equals(buyer.getCodAllowed()) || Boolean.TRUE.equals(orderCreditService
                        .checkForSettlementConfirmation(settlement.getId(), BigDecimal.ZERO).getOverdue())) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
                }
            }
        }
    }
}
