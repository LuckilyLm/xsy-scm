package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinanceCodCollectionOrderDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderPayableLineDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableSourceDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableSourceLineDto;
import com.xsy.scm.finance.domain.dto.FinanceReturnSourceDto;
import com.xsy.scm.finance.domain.dto.FinanceReturnSourceLineDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 财务生成应收时只读订单签收、实际出库和已保存的优惠数据，不通过此 DAO 改订单。签收身份与时间以已保存记录为准，异常签收不能生成正常应收。
 */
@Mapper
public interface FinanceReceivableSourceDao {
    long countIncompleteBundleOutbound(@Param("salesOrderId") Long salesOrderId);

    boolean hasFinalDeliveryResult(@Param("salesOrderId") Long salesOrderId);

    /**
     * 正常应收仅由已签收订单生成；异常签收不能生成正常应收。获取不到有效签收记录必须失败。
     */
    FinanceReceivableSourceDto selectSignedAssignment(@Param("deliveryRouteOrderId") Long deliveryRouteOrderId);

    /**
     * 该订单的实际出库行（明细维度），按出库行主键升序。
     *
     * <p>
     * {@code sales_order_item_id IS NOT NULL} 排除手工出库（手工出库不产生应收）；结果为空表示「签收成功但没有实发」，正常跳过，不是异常。
     */
    List<FinanceReceivableSourceLineDto> selectOutboundLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货数据（单头维度）。条件 {@code status = 'APPROVED'} 表示只有已批准退货才能作为红字来源；{@code PENDING / REJECTED / CANCELLED} 一律读不到，调用点传错
     * id 时会失败而不是悄悄生成。
     */
    FinanceReturnSourceDto selectApprovedReturn(@Param("orderReturnId") Long orderReturnId);

    /**
     * 该订单全部已批准退货的 id，按 id 升序（即批准顺序）。
     *
     * <p>
     * 签收补生成时用它遍历（「先退后签」场景）：升序与并发到达顺序无关；红字之间互不依赖，顺序只影响日志可读性。
     */
    List<Long> selectApprovedReturnIds(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货的有效红字行：只取 {@code approved_quantity > 0} 且 {@code approved_amount > 0} 的行，非正金额逐行跳过。
     *
     * <p>
     * 不加 {@code approved_quantity IS NOT NULL} 分支：{@code > 0} 对 NULL 恒不成立，未批准的行自然排除。
     */
    List<FinanceReturnSourceLineDto> selectApprovedReturnLines(@Param("orderReturnId") Long orderReturnId);

    Long selectOrderSellerId(@Param("salesOrderId") Long salesOrderId);

    /**
     * 应收和退货红字使用下单时保存的优惠分摊，不按当前活动重新计算；没有优惠时返回空列表。
     */
    List<FinanceOrderDiscountLineDto> selectOrderDiscountLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已确认订单的应付行数据（明细维度），按订单行主键升序。
     *
     * <p>
     * 数量取实重，价格取订单行上保存的售价：商城余额支付与签收应收必须算出同一个净额，所以读同一组列、用同一段优惠分摊，不另起算法。
     */
    List<FinanceOrderPayableLineDto> selectOrderPayableLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 多张订单的应付行数据（批量），用于一次算完一批订单的应付净额，避免逐单取数。
     *
     * <p>
     * 与 {@link #selectOrderPayableLines(Long)} 读同一组列；行上带 {@code salesOrderId} 供调用方归回订单。
     */
    List<FinanceOrderPayableLineDto> selectPayableLinesByOrders(@Param("salesOrderIds") Collection<Long> salesOrderIds);

    /**
     * 多张订单的冻结优惠行分摊（批量），与 {@link #selectOrderDiscountLines(Long)} 同源同列。
     */
    List<FinanceOrderDiscountLineDto> selectDiscountLinesByOrders(
        @Param("salesOrderIds") Collection<Long> salesOrderIds);

    /**
     * 货到付款收款需要的订单数据：订单不存在或已删除返回 {@code null}。
     *
     * <p>
     * 是否 COD 结算在 SQL 里判定：财务域不依赖其它业务域，拿不到订单域的结算方式枚举。
     */
    FinanceCodCollectionOrderDto selectCodCollectionOrder(@Param("salesOrderId") Long salesOrderId);
}
