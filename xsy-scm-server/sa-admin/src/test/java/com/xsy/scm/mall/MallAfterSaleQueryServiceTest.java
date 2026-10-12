package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleRefundVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleQueryService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderRefundFundingSourceDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.dto.OrderRefundBalanceFact;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 商城售后投影的口径：展示分类、退款明细与进度节点全部来自正式事实；内部字段不下发，归属不符即 404。
 */
class MallAfterSaleQueryServiceTest {
    private static final Long RETURN_ID = 33L;
    private static final Long ORDER_ID = 21L;

    private final OrderReturnDao orderReturnDao = mock(OrderReturnDao.class);
    private final OrderReturnItemDao orderReturnItemDao = mock(OrderReturnItemDao.class);
    private final OrderRefundDao orderRefundDao = mock(OrderRefundDao.class);
    private final OrderRefundFundingSourceDao orderRefundFundingSourceDao = mock(OrderRefundFundingSourceDao.class);
    private final SalesOrderDao salesOrderDao = mock(SalesOrderDao.class);
    private final SalesOrderItemDao salesOrderItemDao = mock(SalesOrderItemDao.class);
    private final RefundFundingAllocationDao refundFundingAllocationDao = mock(RefundFundingAllocationDao.class);
    private final MallOrderQueryService orderQueryService = mock(MallOrderQueryService.class);
    private final MallAfterSaleQueryService service = new MallAfterSaleQueryService(orderReturnDao,
            orderReturnItemDao, orderRefundDao, orderRefundFundingSourceDao, salesOrderDao, salesOrderItemDao,
            refundFundingAllocationDao, orderQueryService);
    private final RequestMallCustomer customer = customer();

    @Test
    void detailProjectsAllocationRowsAsTheOriginalFundingBreakdown() {
        OrderRefundEntity refund = refund("PENDING");
        stubDetail(returnEntity("APPROVED"), refund);
        when(refundFundingAllocationDao.listByRefund(refund.getId()))
                .thenReturn(List.of(allocation("ONLINE", "20.0000", "SUCCEEDED"),
                        allocation("BALANCE", "10.0000", "PROCESSING")));

        MallAfterSaleVO result = service.detail(customer, RETURN_ID);

        assertThat(result.getDisplayStatus()).isEqualTo("REFUNDING");
        assertThat(result.getRefund().getAllocations()).hasSize(2);
        MallAfterSaleRefundVO.Allocation online = result.getRefund().getAllocations().getFirst();
        assertThat(online.getMethod()).isEqualTo("ONLINE");
        assertThat(online.getAllocatedAmount()).isEqualByComparingTo("20.0000");
        assertThat(online.getStatus()).isEqualTo("SUCCEEDED");
        // 进度：审核已通过、退款未完结
        assertThat(result.getProgress()).extracting("code").containsExactly("SUBMITTED", "REVIEW", "REFUND");
        assertThat(result.getProgress().get(2).getDone()).isFalse();
    }

    @Test
    void legacyBalanceReturnIsShownAsOneBalanceRow() {
        OrderRefundEntity refund = refund("COMPLETED");
        stubDetail(returnEntity("APPROVED"), refund);
        when(refundFundingAllocationDao.listByRefund(refund.getId())).thenReturn(List.of());
        when(orderRefundFundingSourceDao.selectBalanceReturns(List.of(refund.getId())))
                .thenReturn(List.of(new OrderRefundBalanceFact(refund.getId(), 55L, new BigDecimal("30.0000"))));

        MallAfterSaleVO result = service.detail(customer, RETURN_ID);

        assertThat(result.getDisplayStatus()).isEqualTo("REFUNDED");
        assertThat(result.getRefund().getAllocations()).hasSize(1);
        MallAfterSaleRefundVO.Allocation balance = result.getRefund().getAllocations().getFirst();
        assertThat(balance.getMethod()).isEqualTo("BALANCE");
        assertThat(balance.getAllocatedAmount()).isEqualByComparingTo("30.0000");
        assertThat(balance.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getProgress().get(2).getText()).isEqualTo("退款完成");
    }

    @Test
    void approvedWithoutRefundEndsAtNothingToRefund() {
        stubDetail(returnEntity("APPROVED"), null);

        MallAfterSaleVO result = service.detail(customer, RETURN_ID);

        assertThat(result.getDisplayStatus()).isEqualTo("REFUNDED");
        assertThat(result.getRefund()).isNull();
        assertThat(result.getProgress()).extracting("text").containsExactly("已提交申请", "审核通过", "无需退款");
    }

    @Test
    void rejectedReturnExposesTheDecisionReasonOnTheTerminalNode() {
        OrderReturnEntity entity = returnEntity("REJECTED");
        entity.setDecisionReason("商品已签收完好");
        entity.setRejectedAt(OffsetDateTime.now());
        stubDetail(entity, null);

        MallAfterSaleVO result = service.detail(customer, RETURN_ID);

        assertThat(result.getDisplayStatus()).isEqualTo("REJECTED");
        assertThat(result.getProgress()).extracting("code").containsExactly("SUBMITTED", "REJECTED");
        assertThat(result.getProgress().get(1).getDetail()).isEqualTo("商品已签收完好");
    }

    @Test
    void returnOwnedByAnotherCustomerIsNotFound() {
        OrderReturnEntity entity = returnEntity("PENDING");
        entity.setCustomerId(999L);
        when(orderReturnDao.selectById(RETURN_ID)).thenReturn(entity);

        assertThatThrownBy(() -> service.detail(customer, RETURN_ID))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.AFTERSALE_NOT_FOUND));
    }

    private void stubDetail(OrderReturnEntity entity, OrderRefundEntity refund) {
        when(orderReturnDao.selectById(RETURN_ID)).thenReturn(entity);
        when(orderReturnItemDao.listByReturnIds(List.of(RETURN_ID))).thenReturn(List.of(returnItem()));
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(ORDER_ID);
        order.setOrderNo("SO-1");
        when(salesOrderDao.selectBatchIds(List.of(ORDER_ID))).thenReturn(List.of(order));
        when(salesOrderItemDao.listByOrderIds(List.of(ORDER_ID))).thenReturn(List.of(orderItem()));
        when(orderRefundDao.listByReturnIds(List.of(RETURN_ID)))
                .thenReturn(refund == null ? List.of() : List.of(refund));
        when(orderQueryService.skuImageUrls(any())).thenReturn(Map.of());
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(7L);
        result.setCustomerId(9L);
        return result;
    }

    private static OrderReturnEntity returnEntity(String status) {
        OrderReturnEntity entity = new OrderReturnEntity();
        entity.setId(RETURN_ID);
        entity.setOrderId(ORDER_ID);
        entity.setCustomerId(9L);
        entity.setStatus(status);
        entity.setReason("商品损坏");
        entity.setApprovedAmount(new BigDecimal("30.0000"));
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private static OrderReturnItemEntity returnItem() {
        OrderReturnItemEntity item = new OrderReturnItemEntity();
        item.setId(301L);
        item.setReturnId(RETURN_ID);
        item.setOrderItemId(101L);
        item.setRequestedQuantity(new BigDecimal("2.0000"));
        item.setLockedUnitPrice(new BigDecimal("15.0000"));
        return item;
    }

    private static SalesOrderItemEntity orderItem() {
        SalesOrderItemEntity item = new SalesOrderItemEntity();
        item.setId(101L);
        item.setOrderId(ORDER_ID);
        item.setSkuId(11L);
        item.setProductNameSnapshot("上海青");
        return item;
    }

    private static OrderRefundEntity refund(String status) {
        OrderRefundEntity refund = new OrderRefundEntity();
        refund.setId(44L);
        refund.setReturnId(RETURN_ID);
        refund.setOrderId(ORDER_ID);
        refund.setCustomerId(9L);
        refund.setRefundAmount(new BigDecimal("30.0000"));
        refund.setStatus(status);
        if ("COMPLETED".equals(status)) {
            refund.setCompletedAt(OffsetDateTime.now());
        }
        return refund;
    }

    private static RefundFundingAllocationEntity allocation(String method, String amount, String status) {
        RefundFundingAllocationEntity allocation = new RefundFundingAllocationEntity();
        allocation.setSourceMethod(method);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        allocation.setStatus(status);
        return allocation;
    }
}
