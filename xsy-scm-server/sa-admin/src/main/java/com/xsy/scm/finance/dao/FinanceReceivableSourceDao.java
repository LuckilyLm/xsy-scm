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

/** 财务生成应收时只读订单签收、实际出库和冻结折扣事实，不通过此 DAO 改订单。签收身份及时间以已保存记录为准，异常签收不能生成正常应收。 */
@Mapper
public interface FinanceReceivableSourceDao {
    long countIncompleteBundleOutbound(@Param("salesOrderId") Long salesOrderId);
    boolean hasFinalDeliveryResult(@Param("salesOrderId") Long salesOrderId);

    /** 正常应收仅由已签收订单生成；异常签收不能生成正常应收。获取不到有效签收事实必须失败。 */
    FinanceReceivableSourceDto selectSignedAssignment(@Param("deliveryRouteOrderId") Long deliveryRouteOrderId);

    /**
     * 该订单的实际出库行（明细维度），按出库行主键升序。
     *
     * <p>
     * {@code sales_order_item_id IS NOT NULL} 排除手工出库（手工出库不产生应收）；结果为空即「签收成功但零实发」，属的成功跳过，不是异常。
     */
    List<FinanceReceivableSourceLineDto> selectOutboundLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货事实（单头维度）。谓词 {@code status = 'APPROVED'} 是「退货批准才是红字来源」的库级表达；{@code PENDING / REJECTED / CANCELLED} 一律读不到，
     * 因此调用点传错 id 时会失败而不是静默生成。
     */
    FinanceReturnSourceDto selectApprovedReturn(@Param("orderReturnId") Long orderReturnId);

    /**
     * 该订单<b>全部已批准退货</b>的 id，按 id 升序（= 批准发生顺序）。
     *
     * <p>
     * 签收补生成用它遍历（的「先退后签」③）：升序保证多张退货的补生成顺序与并发到达顺序无关，红字之间互不依赖，因此顺序只影响日志可读性。
     */
    List<Long> selectApprovedReturnIds(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货的<b>有效红字行</b>：只取 {@code approved_quantity > 0} 且 {@code approved_amount > 0} 的行（第一条：逐行跳过非正金额）。
     *
     * <p>
     * 不加 {@code approved_quantity IS NOT NULL} 之类的分支：{@code > 0} 对 NULL 恒不成立，未批准的行自然被排除。
     */
    List<FinanceReturnSourceLineDto> selectApprovedReturnLines(@Param("orderReturnId") Long orderReturnId);

    Long selectOrderSellerId(@Param("salesOrderId") Long salesOrderId);

    /** 应收和退货红字使用下单时冻结的优惠分摊，不按当前活动重新计算；没有优惠时返回空分配。 */
    List<FinanceOrderDiscountLineDto> selectOrderDiscountLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已确认订单的应付行事实（明细维度），按订单行主键升序。
     *
     * <p>
     * 量取<b>实重</b>、价取订单行冻结售价：商城余额支付与签收应收必须算出同一个净额， 因此读同一组列、走同一段优惠等比分摊，不另起口径。
     */
    List<FinanceOrderPayableLineDto> selectOrderPayableLines(@Param("salesOrderId") Long salesOrderId);

    /**
     * 多张订单的应付行事实（批量），用于一次算完一批订单的应付净额，避免逐单取数。
     *
     * <p>
     * 与 {@link #selectOrderPayableLines(Long)} 读同一组列；行上带 {@code salesOrderId} 供调用方归回订单。
     */
    List<FinanceOrderPayableLineDto> selectPayableLinesByOrders(@Param("salesOrderIds") Collection<Long> salesOrderIds);

    /** 多张订单的冻结优惠行分摊（批量），与 {@link #selectOrderDiscountLines(Long)} 同源同列。 */
    List<FinanceOrderDiscountLineDto> selectDiscountLinesByOrders(
            @Param("salesOrderIds") Collection<Long> salesOrderIds);

    /**
     * 货到付款收款需要的订单事实：订单不存在或已删除返回 {@code null}。
     *
     * <p>
     * 结算约定是否为 COD 在 SQL 里判定 —— 财务域不依赖其它业务域，拿不到订单域的结算方式枚举。
     */
    FinanceCodCollectionOrderDto selectCodCollectionOrder(@Param("salesOrderId") Long salesOrderId);
}
