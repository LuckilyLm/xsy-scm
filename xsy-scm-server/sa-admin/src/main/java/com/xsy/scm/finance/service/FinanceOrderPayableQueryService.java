package com.xsy.scm.finance.service;

import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderPayableLineDto;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单资金金额的只读计算：确认后应付净额与已付金额。
 *
 * <p>
 * 签收前按确认实重与保存的优惠计算预付应付；签收后以实际出库形成的正常应收净额为准。分拣出库量可以小于确认实重， 已付超过实发应收的部分按超额资金与售后规则处理，不自动退款。本服务不写表。
 */
@Service
@RequiredArgsConstructor
public class FinanceOrderPayableQueryService {

    private final FinanceReceivableSourceDao financeReceivableSourceDao;
    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceReceiptDao financeReceiptDao;

    /**
     * 已确认订单的当前应付：签收前取确认实重预估，签收后取正常应收扣红字的净额。
     *
     * <p>
     * 调用方负责只在已确认订单上使用：确认前实重未录齐，本方法不替「能不能算」背书。
     */
    @Transactional(readOnly = true)
    public BigDecimal payableOfConfirmedOrder(Long salesOrderId) {
        FinanceReceivableEntity receivable = financeReceivableDao.selectNormalByOrder(salesOrderId);
        if (receivable != null) {
            FinanceReceivableVO summary = financeReceivableDao.selectSummaryById(receivable.getId());
            if (summary == null || summary.getNetAmount() == null) {
                throw new IllegalStateException("已生成的订单应收缺少净额: " + salesOrderId);
            }
            return summary.getNetAmount().max(BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE));
        }
        // 异常签收和 0 元正常签收不生成应收；终态交付后不能再按确认实重追收。
        if (financeReceivableSourceDao.hasFinalDeliveryResult(salesOrderId)) {
            return BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE);
        }
        Map<Long, BigDecimal> discounts = new HashMap<>();
        for (FinanceOrderDiscountLineDto line : financeReceivableSourceDao.selectOrderDiscountLines(salesOrderId)) {
            if (line.getOrderItemId() != null && line.getDiscountAmount() != null) {
                discounts.put(line.getOrderItemId(), line.getDiscountAmount());
            }
        }
        return net(financeReceivableSourceDao.selectOrderPayableLines(salesOrderId), discounts);
    }

    /**
     * 一批已确认订单的应付净额，一次取数算完。
     *
     * <p>
     * 授信敞口要按结算主体把所有「已确认未开应收」的订单加总，逐单调用会退化成 N+1 次查询；这里换成两条 IN 查询，但逐行折算仍走同一段
     * {@code discountShare}，不另写一套算法。没有行的订单不出现在结果里（按 0 处理）。
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> payableOfConfirmedOrders(Collection<Long> salesOrderIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return result;
        }
        Map<Long, Map<Long, BigDecimal>> discountsByOrder = new HashMap<>();
        for (FinanceOrderDiscountLineDto line : financeReceivableSourceDao.selectDiscountLinesByOrders(salesOrderIds)) {
            if (line.getSalesOrderId() != null && line.getOrderItemId() != null && line.getDiscountAmount() != null) {
                discountsByOrder.computeIfAbsent(line.getSalesOrderId(), key -> new HashMap<>())
                    .put(line.getOrderItemId(), line.getDiscountAmount());
            }
        }
        Map<Long, List<FinanceOrderPayableLineDto>> linesByOrder = new HashMap<>();
        for (FinanceOrderPayableLineDto line : financeReceivableSourceDao.selectPayableLinesByOrders(salesOrderIds)) {
            if (line.getSalesOrderId() != null) {
                linesByOrder.computeIfAbsent(line.getSalesOrderId(), key -> new ArrayList<>()).add(line);
            }
        }
        for (Map.Entry<Long, List<FinanceOrderPayableLineDto>> entry : linesByOrder.entrySet()) {
            result.put(entry.getKey(), net(entry.getValue(), discountsByOrder.getOrDefault(entry.getKey(), Map.of())));
        }
        return result;
    }

    /**
     * 逐行计算 {@code 实重 × 锁定价 − 该行承担的优惠}，行先舍入再汇总。
     */
    private static BigDecimal net(List<FinanceOrderPayableLineDto> lines, Map<Long, BigDecimal> discounts) {
        BigDecimal payable = BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE);
        for (FinanceOrderPayableLineDto line : lines) {
            BigDecimal gross = line.getActualQuantity().multiply(line.getLockedUnitPrice())
                .setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);
            payable = payable.add(gross.subtract(FinanceReceivableService.discountShare(line.getOrderItemId(), gross,
                line.getOrderedLineAmount(), discounts)));
        }
        return payable;
    }

    /**
     * 已付金额：该订单全部已成功支付记录的本金合计（余额消费与在线实收都以登记的本金为准）。
     *
     * <p>
     * 不含货到付款收款：这个数是支付记录，用于余额支付命令计算待付；客户看到的「已付」还要加上交付时实收的钱，见 {@link #receivedAmountOf(Long)}。
     */
    @Transactional(readOnly = true)
    public BigDecimal paidAmountOf(Long salesOrderId) {
        return financeOrderFundingSourceDao.selectOrderFunding(salesOrderId).stream()
            .map(FinanceOrderFundingDto::getAmount)
            .reduce(BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE), BigDecimal::add);
    }

    /**
     * 实收金额：支付记录、COD 分笔收款，以及人工收款对该订单正常应收的有效核销。
     *
     * <p>
     * 新订单的结算方式互斥；账期和历史 COD 人工补收通过有效核销归属订单，避免财务已结清而客户仍看到待付。
     */
    @Transactional(readOnly = true)
    public BigDecimal receivedAmountOf(Long salesOrderId) {
        BigDecimal collected = financeReceiptDao.sumCodCollectedByOrder(salesOrderId);
        BigDecimal manualSupplement = financeReceiptDao.sumManualCollectedByOrder(salesOrderId);
        return paidAmountOf(salesOrderId)
            .add(collected == null ? BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE) : collected)
            .add(manualSupplement == null ? BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE) : manualSupplement);
    }
}
