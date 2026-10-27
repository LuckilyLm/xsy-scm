package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderQuantitySourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;

/**
 * 分拣称重结果回写订单实发量。
 *
 * <p>
 * 回写的目的是让「称多少、结多少」在订单上成立：签收应收按实发量 × 锁定单价生成，分拣时称出的量必须在下一次对账前落到订单行上， 否则财务拿到的还是下单量。
 *
 * <p>
 * 只回写已确认、未发车、需称重的行：待确认订单的实数量由销售手工录入，标品的实数量恒等于下单量，发车后改实数量会与已出库的分拣记录脱节。
 * 与分拣录入同事务（{@link Propagation#MANDATORY}），任何一步失败都整体回滚，不留下「分拣记了、订单没记」的中间态。
 */
@Service
@RequiredArgsConstructor
public class OrderActualQuantitySyncService {

    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderOperationLogRecorder orderLogs;

    /**
     * 把一条分拣明细的实称量写回订单行，返回是否真的发生了变更。
     *
     * <p>
     * 订单行不满足回写条件时静默返回 {@code false}：分拣任务本身对订单状态的判断早已在候选阶段完成，这里不是错误，只是没有可回写的目标。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean sync(Long salesOrderItemId, BigDecimal sortedQuantity) {
        // 实发量不允许为 0（ck_sales_order_item_actual_quantity）：称出 0 的行保持原实发量，
        // 未交货少结的部分走退货或红字，不把订单行改成 0 这种数据库不允许的状态。
        if (salesOrderItemId == null || sortedQuantity == null || sortedQuantity.signum() <= 0)
            return false;
        var item = salesOrderItemDao.selectById(salesOrderItemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()))
            return false;
        if (!ScmProductTypeEnum.NON_STANDARD.name().equals(item.getProductTypeSnapshot()))
            return false;
        // 锁订单行：同一订单的多个分拣行可能同时录入，汇总金额要在同一把锁上重算。
        var order = salesOrderDao.lock(item.getOrderId());
        if (order == null || Boolean.TRUE.equals(order.getDeleted()))
            return false;
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus()) || order.getDispatchedAt() != null)
            return false;
        if (item.getActualQuantity() != null && item.getActualQuantity().compareTo(sortedQuantity) == 0)
            return false;

        var before = item.getActualQuantity();
        item.setActualQuantity(sortedQuantity);
        item.setActualQuantitySource(ScmOrderQuantitySourceEnum.SYSTEM.name());
        // 人工原因只对 MANUAL 有效；系统值必须清空，否则触发 ck_sales_order_item_actual_reason。
        item.setActualQuantityReason(null);
        item.setSettlementLineAmount(OrderAmountCalculator.lineAmount(sortedQuantity, item.getLockedUnitPrice()));
        item.setUpdatedAt(OffsetDateTime.now());
        item.setUpdatedBy(ScmOperator.current());
        if (salesOrderItemDao.updateById(item) != 1)
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);

        var rows = salesOrderItemDao.list(order.getId());
        order.setSettlementTotalAmount(OrderAmountCalculator.orderAmount(rows.stream()
            .map(row -> row.getSettlementLineAmount() == null ? BigDecimal.ZERO : row.getSettlementLineAmount())
            .toList()));
        order.setUpdatedAt(OffsetDateTime.now());
        order.setUpdatedBy(ScmOperator.current());
        if (salesOrderDao.updateById(order) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);

        var change = new LinkedHashMap<String, Object>();
        change.put("itemId", item.getId());
        change.put("actualQuantity", sortedQuantity.toPlainString());
        change.put("settlementTotalAmount", order.getSettlementTotalAmount().toPlainString());
        orderLogs.record(order.getId(), ScmOrderOperationTypeEnum.ACTUAL_QUANTITY, "分拣称重回写",
            before == null ? null : java.util.Map.of("actualQuantity", before.toPlainString()), change);
        return true;
    }
}
