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
 * <b>已确认未开应收的占用与签收应收同口径</b>：SQL 只给出「哪些订单还没开应收」，金额逐单走 {@link FinanceOrderPayableQueryService}（实重 × 锁定价 −
 * 冻结优惠等比分摊）。此前按订单的 {@code settlement_total_amount}（优惠前）加总，会让额度占用长期大于最终账单，客户被自己的优惠挡住。
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
