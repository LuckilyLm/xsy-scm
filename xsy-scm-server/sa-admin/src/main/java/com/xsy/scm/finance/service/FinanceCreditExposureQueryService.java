package com.xsy.scm.finance.service;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.dao.CustomerCreditDao;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 结算主体的授信敞口只读取数；客户锁与订单裁定属于调用方。
 *
 * <p>
 * <b>未开应收的占用与签收应收同金额口径</b>：只有账期订单参与预占；历史空结算方式订单沿用旧口径。 已开正常应收无论原结算方式如何，按未结净额计入。订单金额逐单走
 * {@link FinanceOrderPayableQueryService} 的冻结优惠折算。
 */
@Service
@RequiredArgsConstructor
public class FinanceCreditExposureQueryService {

    private final CustomerCreditDao customerCreditDao;
    private final FinanceOrderPayableQueryService payableQueryService;

    public FinanceCreditExposureDto summarize(Long settlementCustomerId, ScmValueScope orderSellerScope) {
        FinanceCreditExposureDto exposure = customerCreditDao.selectExposure(settlementCustomerId, orderSellerScope);
        if (exposure == null) {
            exposure = new FinanceCreditExposureDto();
        }
        exposure.setConfirmedOrderAmount(confirmedOrderAmount(settlementCustomerId));
        return exposure;
    }

    private BigDecimal confirmedOrderAmount(Long settlementCustomerId) {
        List<Long> openOrderIds = customerCreditDao.selectConfirmedOrderIdsWithoutReceivable(settlementCustomerId);
        if (openOrderIds == null || openOrderIds.isEmpty()) {
            return BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE);
        }
        Map<Long, BigDecimal> payableByOrder = payableQueryService.payableOfConfirmedOrders(openOrderIds);
        BigDecimal total = BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE);
        for (Long orderId : openOrderIds) {
            // 订单没有可算的行时按 0 处理：CONFIRMED 订单必然有行，这里只防「行被并发删除」这类边界。
            total = total
                .add(payableByOrder.getOrDefault(orderId, BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE)));
        }
        return total;
    }
}
