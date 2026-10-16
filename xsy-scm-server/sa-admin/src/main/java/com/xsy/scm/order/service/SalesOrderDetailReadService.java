package com.xsy.scm.order.service;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.order.dao.SalesOrderDetailFactDao;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.payment.constant.ScmRefundFundingAllocationStatusEnum;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.support.SortingAccess;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 员工订单详情的只读事实组合；写命令及操作日志仍使用订单域原有快照。 */
@Service
@RequiredArgsConstructor
public class SalesOrderDetailReadService {

    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesOrderDetailFactDao factDao;
    private final ScmDataScopeService dataScopeService;
    private final FinanceOrderPayableQueryService orderPayableQueryService;
    private final SortingAccess sortingAccess;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesOrderDetailVO detail(Long orderId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesOrderDetailVO detail = salesOrderQueryService.detail(orderId, scope);

        boolean fundingVisible = ScmDataScopeService.hasPermission(FinancePermission.RECEIVABLE_QUERY)
                && ScmDataScopeService.hasPermission(FinancePermission.RECEIPT_QUERY);
        detail.setFundingFactsVisible(fundingVisible);
        if (fundingVisible && ScmOrderStatusEnum.CONFIRMED.name().equals(detail.getStatus())) {
            BigDecimal payable = orderPayableQueryService.payableOfConfirmedOrder(orderId);
            BigDecimal received = orderPayableQueryService.receivedAmountOf(orderId);
            SalesOrderDetailVO.FundingFact funding = new SalesOrderDetailVO.FundingFact();
            funding.setPayableAmount(payable);
            funding.setReceivedAmount(received);
            funding.setRemainingAmount(payable.subtract(received).max(BigDecimal.ZERO));
            funding.setExcessReceivedAmount(received.subtract(payable).max(BigDecimal.ZERO));
            detail.setFunding(funding);
        }

        boolean inventoryVisible = ScmDataScopeService.hasPermission(InventoryPermission.RESERVATION_QUERY)
                && !scope.warehouseNowhere();
        detail.setInventoryFactsVisible(inventoryVisible);
        if (inventoryVisible) {
            detail.setInventoryReservations(factDao.reservations(orderId, scope.getWarehouseScope()));
        }

        boolean sortingVisible = ScmDataScopeService.hasPermission(SortingPermission.TASK_QUERY)
                && !scope.warehouseNowhere();
        detail.setSortingFactsVisible(sortingVisible);
        if (sortingVisible) {
            detail.setSortingProgress(factDao.sorting(orderId, scope.getWarehouseScope(), scope.getEmployeeId(),
                    sortingAccess.crossAssignee()));
        }

        boolean deliveryVisible = ScmDataScopeService.hasPermission(DeliveryPermission.ROUTE_QUERY)
                && !scope.getDriverScope().isEmpty();
        detail.setDeliveryFactsVisible(deliveryVisible);
        if (deliveryVisible) {
            detail.setDeliveryProgress(factDao.delivery(orderId, scope.getDriverScope()));
        }

        boolean returnsVisible = ScmDataScopeService.hasPermission(OrderPermission.RETURN_QUERY);
        detail.setReturnFactsVisible(returnsVisible);
        boolean receivablesVisible = returnsVisible
                && ScmDataScopeService.hasPermission(FinancePermission.RECEIVABLE_QUERY);
        detail.setReceivableFactsVisible(receivablesVisible);
        if (returnsVisible) {
            List<SalesOrderDetailVO.ReturnFact> returns = factDao.returns(orderId);
            if (receivablesVisible && !returns.isEmpty()) {
                Map<Long, SalesOrderDetailVO.RedReceivableFact> redByReturn = factDao.redReceivables(orderId)
                        .stream().collect(Collectors.toMap(SalesOrderDetailVO.RedReceivableFact::getReturnId,
                                fact -> fact));
                for (SalesOrderDetailVO.ReturnFact row : returns) {
                    SalesOrderDetailVO.RedReceivableFact red = redByReturn.get(row.getReturnId());
                    row.setRedReceivableRecorded(red != null);
                    if (red != null) {
                        row.setRedReceivableAmount(red.getAmount());
                    }
                }
            }
            detail.setReturns(returns);
        }

        boolean refundsVisible = ScmDataScopeService.hasPermission(OrderPermission.REFUND_QUERY);
        detail.setRefundFactsVisible(refundsVisible);
        if (refundsVisible) {
            List<SalesOrderDetailVO.RefundFact> refunds = factDao.refunds(orderId);
            if (!refunds.isEmpty()) {
                Map<Long, SalesOrderDetailVO.RefundFundingFact> fundingByRefund = factDao.refundFunding(orderId)
                        .stream().collect(Collectors.toMap(SalesOrderDetailVO.RefundFundingFact::getRefundId,
                                fact -> fact));
                factDao.legacyRefundFunding(orderId).forEach(fact -> fundingByRefund.putIfAbsent(fact.getRefundId(), fact));
                for (SalesOrderDetailVO.RefundFact row : refunds) {
                    SalesOrderDetailVO.RefundFundingFact funding = fundingByRefund.get(row.getRefundId());
                    if (funding == null) {
                        continue;
                    }
                    row.setFundingSucceededAmount(funding.getSucceededAmount());
                    row.setFundingStatus(fundingStatus(funding));
                }
            }
            detail.setRefunds(refunds);
        }
        return detail;
    }

    private static String fundingStatus(SalesOrderDetailVO.RefundFundingFact funding) {
        if (funding.getSourceCount().equals(funding.getSucceededCount())) {
            return ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name();
        }
        if (funding.getFailedCount() > 0) {
            return ScmRefundFundingAllocationStatusEnum.FAILED.name();
        }
        if (funding.getProcessingCount() > 0) {
            return ScmRefundFundingAllocationStatusEnum.PROCESSING.name();
        }
        return ScmRefundFundingAllocationStatusEnum.PENDING.name();
    }
}
