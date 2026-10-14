package com.xsy.scm.finance.service;

import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderPayableLineDto;
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
 * 订单资金金额的只读口径：确认后应付净额与已付金额。
 *
 * <p>
 * 应付净额与签收应收共用同一把尺子（实重 × 冻结售价 − 冻结优惠等比分摊）， 客户在确认后支付的钱与签收后形成的应收才不会出现两个答案。 授信敞口也读这里，因此「占用多少额度」与「最终开多少应收」同样只有一个答案。本服务不写任何表。
 */
@Service
@RequiredArgsConstructor
public class FinanceOrderPayableQueryService {

    private final FinanceReceivableSourceDao financeReceivableSourceDao;
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceReceiptDao financeReceiptDao;

    /**
     * 已确认订单的应付净额：逐行 {@code 实重 × 锁定价} 扣冻结优惠等比分摊后求和（行先舍入再汇总，与应收单头同口径）。
     *
     * <p>
     * 调用方负责只在已确认订单上使用：确认前实重未录齐，本方法不替「能不能算」背书。
     */
    @Transactional(readOnly = true)
    public BigDecimal payableOfConfirmedOrder(Long salesOrderId) {
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
     * 授信敞口要按结算主体把所有「已确认未开应收」的订单加总，逐单调用会退化成 N+1 次查询；这里换成两条 IN 查询， 但逐行折算仍走同一段
     * {@code discountShare}，不另写一套口径。没有行的订单不出现在结果里（按 0 处理）。
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

    /** 逐行 {@code 实重 × 锁定价 − 该行承担的冻结优惠}，行先舍入再汇总。 */
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
     * 已付金额：该订单全部已成功支付事实的本金合计（余额消费与在线实收都以登记的本金为准）。
     *
     * <p>
     * <b>不含货到付款收款</b>：这个数是「支付事实」，用在余额支付命令算待付的口径上； 客户看到的「已付」还要算上交付时真正收到的钱，见 {@link #receivedAmountOf(Long)}。
     */
    @Transactional(readOnly = true)
    public BigDecimal paidAmountOf(Long salesOrderId) {
        return financeOrderFundingSourceDao.selectOrderFunding(salesOrderId).stream()
                .map(FinanceOrderFundingDto::getAmount)
                .reduce(BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE), BigDecimal::add);
    }

    /**
     * 实收金额：该订单<b>实际收到的钱</b> —— 支付事实（余额 / 在线）+ 货到付款收款。
     *
     * <p>
     * 两种结算方式互斥，因此两项不会同时有值：余额 / 账期 / 在线走支付事实，货到付款走收款事实。 客户侧的「已付」用这个数，否则 COD 订单收了钱仍会一直显示全额待付。
     */
    @Transactional(readOnly = true)
    public BigDecimal receivedAmountOf(Long salesOrderId) {
        BigDecimal collected = financeReceiptDao.sumCodCollectedByOrder(salesOrderId);
        return paidAmountOf(salesOrderId)
                .add(collected == null ? BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE) : collected);
    }
}
