package com.xsy.scm.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.manager.CustomerValidator;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
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
    private final FinanceOrderPayableQueryService payableQueryService;

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
                if (shared && !ScmSharedSettlementPolicy
                        .sharedCheckoutAllowed(Boolean.TRUE.equals(settlement.getSharedSettlementEnabled()))) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
                }
                // 占用额取正式净额（实重 × 锁定价 − 已冻结优惠等比分摊），与签收生成的应收同一把尺子。
                // 确认命令已把优惠冻结排在本判定之前，因此这里的优惠额是已成立的事实，不是预估。
                // 按优惠前毛额判定会让客户被自己的优惠挡住（额度够、实际账单更小，却确认不了）。
                var check = orderCreditService.checkForSettlementConfirmation(settlement.getId(),
                        payableQueryService.payableOfConfirmedOrder(order.getId()));
                if (check.getCreditLimit() == null || check.getCreditLimit().signum() <= 0
                        || !Boolean.TRUE.equals(check.getAllowed())) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_CREDIT_BLOCKED);
                }
            }
            case WALLET -> {
                if (shared && !ScmSharedSettlementPolicy
                        .sharedCheckoutAllowed(Boolean.TRUE.equals(settlement.getSharedSettlementEnabled()))) {
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
