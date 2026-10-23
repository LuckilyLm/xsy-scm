package com.xsy.scm.order.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单详情跨域只读查询；只读取正式履约、应收与退款数据。
 */
@Mapper
public interface SalesOrderDetailFactDao {

    List<SalesOrderDetailVO.ReservationFact> reservations(@Param("orderId") Long orderId,
        @Param("scope") ScmValueScope warehouseScope);

    List<SalesOrderDetailVO.SortingFact> sorting(@Param("orderId") Long orderId,
        @Param("scope") ScmValueScope warehouseScope, @Param("employeeId") Long employeeId,
        @Param("crossAssignee") boolean crossAssignee);

    SalesOrderDetailVO.DeliveryFact delivery(@Param("orderId") Long orderId, @Param("scope") ScmValueScope driverScope);

    List<SalesOrderDetailVO.ReturnFact> returns(@Param("orderId") Long orderId);

    List<SalesOrderDetailVO.RedReceivableFact> redReceivables(@Param("orderId") Long orderId);

    List<SalesOrderDetailVO.RefundFact> refunds(@Param("orderId") Long orderId);

    List<SalesOrderDetailVO.RefundFundingFact> refundFunding(@Param("orderId") Long orderId);

    List<SalesOrderDetailVO.RefundFundingFact> legacyRefundFunding(@Param("orderId") Long orderId);
}
