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

/**
 * 商城订单确认时复核结算约定；资金动作由余额、支付和财务模块执行。
 */
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
                // 共享账期一律拒绝：余额授权不覆盖账期，集团账期由集团主体统一办理。
                if (shared) {
                    throw new ScmBusinessException(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED);
                }
                // 占用额取正式净额（实重 × 锁定价 − 已按比例分摊的优惠），与签收生成的应收一致。
                // 优惠在确认时已先保存，按优惠前毛额判定会让额度够的客户被自己的优惠挡住。
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
                // 余额在支付命令中按实重与已保存的优惠原子扣减；确认本身不代表已付款。
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
