package com.xsy.scm.mall.order.service;

import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.manager.CustomerValidator;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.constant.ScmSharedSettlementPolicy;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.domain.vo.MallSettlementOptionVO;
import com.xsy.scm.order.service.OrderCreditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城结算方式可用性投影（只读）。
 *
 * <p>
 * 只回答「此刻客户可选哪些结算方式、为什么不可选」，不产生任何资金动作，也不替代授权校验：
 * <ul>
 * <li><b>余额</b>：读结算主体钱包的可用余额（集团下属单位共用集团钱包，因此这里读的是结算主体而非下单客户， 避免把别家主体的余额当成自己的）；</li>
 * <li><b>账期</b>：复用 {@link OrderCreditService#checkSelfSettlement} 的正式授信口径（敞口 = 正常应收净额 + 已确认未开应收订单 +
 * 本单金额，叠加超限与逾期判定），不是「有额度就可用」；</li>
 * <li><b>货到付款</b>：客户须由后台开通且无逾期，交付时仍按实际到账确认收款。</li>
 * </ul>
 *
 * <p>
 * <b>共享结算的资金可见性</b>：集团共享结算下（判定见 {@link CustomerValidator#sharedSettlement}）不返回任何金额， 与商城钱包 / 账期概览同口径 ——
 * 成员账号不能通过结算页读到集团钱包总额或集团授信总额。 是否允许成员用共享资金下单由 {@link ScmSharedSettlementPolicy#sharedCheckoutAllowed} 统一裁定。
 */
@Service
@RequiredArgsConstructor
public class MallSettlementService {

    private static final int SCALE = 4;

    private static final String SHARED_WALLET_REASON = "集团共享结算，共享钱包由集团主体统一收付，暂不支持在商城中直接使用";
    private static final String SHARED_CREDIT_REASON = "集团共享结算，账期由集团主体统一办理";

    private final CustomerService customerService;
    private final CustomerTypeService customerTypeService;
    private final CustomerBalanceService customerBalanceService;
    private final OrderCreditService orderCreditService;

    @Transactional(readOnly = true)
    public List<MallSettlementOptionVO> options(RequestMallCustomer customer, BigDecimal payableAmount) {
        return options(customer, payableAmount, payableAmount);
    }

    /** 余额按优惠后应付预估，账期按确认命令使用的优惠前商品金额保守校验。 */
    @Transactional(readOnly = true)
    public List<MallSettlementOptionVO> options(RequestMallCustomer customer, BigDecimal payableAmount,
            BigDecimal creditExposureAmount) {
        BigDecimal amount = payableAmount == null
                ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : payableAmount.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal creditAmount = creditExposureAmount == null
                ? amount
                : creditExposureAmount.setScale(SCALE, RoundingMode.HALF_UP);
        CustomerEntity buyer = customerService.requireTradable(customer.getCustomerId());
        CustomerEntity settlement = customerService.requireSettlementCustomer(buyer);
        boolean shared = CustomerValidator.sharedSettlement(buyer.getId(), settlement.getId(),
                customerTypeService.require(settlement.getCustomerTypeId()).getTypeCode());

        List<MallSettlementOptionVO> options = new ArrayList<>();
        options.add(creditOption(settlement, shared, creditAmount));
        options.add(walletOption(settlement, shared, amount));
        options.add(codOption(buyer, settlement));
        return options;
    }

    /**
     * 账期：先按共享可见性收口，再用正式授信口径判定。
     *
     * <p>
     * 「有额度」不等于「可用」：逾期、或本单占用后超限，都会让账期不可选 —— 避免结算页允许选择、后台确认时又被拒。
     */
    private MallSettlementOptionVO creditOption(CustomerEntity settlement, boolean shared, BigDecimal amount) {
        MallSettlementOptionVO option = base(ScmSettlementMethodEnum.CREDIT, null, !shared);
        if (shared) {
            option.setEnabled(ScmSharedSettlementPolicy.sharedCheckoutAllowed());
            option.setDisabledReason(SHARED_CREDIT_REASON);
            return option;
        }
        CustomerCreditCheckVO check = orderCreditService.checkSelfSettlement(settlement.getId(), amount);
        BigDecimal creditLimit = check.getCreditLimit();
        if (creditLimit == null || creditLimit.signum() <= 0) {
            option.setAvailableAmount(BigDecimal.ZERO.setScale(SCALE));
            option.setEnabled(Boolean.FALSE);
            option.setDisabledReason("当前客户未配置授信额度");
            return option;
        }
        BigDecimal used = nonzero(check.getOpenReceivableAmount()).add(nonzero(check.getConfirmedOrderAmount()));
        option.setAvailableAmount(
                creditLimit.subtract(used).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP));
        if (Boolean.TRUE.equals(check.getOverdue())) {
            option.setEnabled(Boolean.FALSE);
            option.setDisabledReason("存在逾期账单，暂不能使用账期结算");
            return option;
        }
        if (Boolean.TRUE.equals(check.getOverLimit())) {
            option.setEnabled(Boolean.FALSE);
            option.setDisabledReason("本单将超出剩余授信额度");
            return option;
        }
        option.setEnabled(Boolean.TRUE);
        option.setNote("账期按优惠前商品金额占用，确认时按实重与授信规则重验");
        return option;
    }

    private MallSettlementOptionVO walletOption(CustomerEntity settlement, boolean shared, BigDecimal amount) {
        MallSettlementOptionVO option = base(ScmSettlementMethodEnum.WALLET, null, !shared);
        if (shared) {
            option.setEnabled(ScmSharedSettlementPolicy.sharedCheckoutAllowed());
            option.setDisabledReason(SHARED_WALLET_REASON);
            return option;
        }
        BigDecimal balance = customerBalanceService.balanceOfSettlement(settlement.getId()).setScale(SCALE,
                RoundingMode.HALF_UP);
        option.setAvailableAmount(balance);
        if (balance.compareTo(amount) < 0) {
            option.setEnabled(Boolean.FALSE);
            option.setDisabledReason("钱包可用余额不足");
        } else {
            option.setEnabled(Boolean.TRUE);
            option.setNote("余额在结算主体钱包内扣减，实际支付以支付结果为准");
        }
        return option;
    }

    private MallSettlementOptionVO codOption(CustomerEntity buyer, CustomerEntity settlement) {
        MallSettlementOptionVO option = base(ScmSettlementMethodEnum.COD, null, Boolean.TRUE);
        if (!Boolean.TRUE.equals(buyer.getCodAllowed())) {
            option.setDisabledReason("当前客户未开通货到付款");
            return option;
        }
        if (Boolean.TRUE
                .equals(orderCreditService.checkSelfSettlement(settlement.getId(), BigDecimal.ZERO).getOverdue())) {
            option.setDisabledReason("存在逾期账单，暂不能使用货到付款");
            return option;
        }
        option.setEnabled(Boolean.TRUE);
        option.setNote("货到付款只记录结算约定，交付时按实际交付金额收款");
        return option;
    }

    private static BigDecimal nonzero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value;
    }

    private static MallSettlementOptionVO base(ScmSettlementMethodEnum method, BigDecimal availableAmount,
            boolean amountVisible) {
        MallSettlementOptionVO option = new MallSettlementOptionVO();
        option.setMethod(method.name());
        option.setLabel(method.getDesc());
        option.setAvailableAmount(availableAmount);
        option.setAmountVisible(amountVisible);
        return option;
    }
}
