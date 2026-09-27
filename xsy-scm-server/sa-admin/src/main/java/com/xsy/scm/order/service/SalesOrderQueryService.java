package com.xsy.scm.order.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;

import com.xsy.scm.order.domain.form.OrderLogQueryForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

import com.xsy.scm.order.domain.vo.OrderAddressSnapshotVO;
import com.xsy.scm.order.domain.vo.OrderOperationLogVO;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;

import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;


import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class SalesOrderQueryService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderAddressSnapshotDao orderAddressSnapshotDao;
    private final OrderOperationLogDao orderOperationLogDao;
    private final net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao employeeDao;
    private final ScmDataScopeService dataScopeService;

    /** 最近成交参考价取数条数区间：limit 最大 10。 */
    private static final int RECENT_PRICE_MIN_LIMIT = 1;
    private static final int RECENT_PRICE_MAX_LIMIT = 10;

    public PageResult<SalesOrderVO> query(SalesOrderQueryForm salesOrderQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        // 维度里一个授权 id 都没有时直接给空分页：不必让数据库跑一次恒假的 IN，
        // 也避免把空集合送进 IN () 变成非法 SQL。
        if (dataScopeContext.getOrderSellerScope().isEmpty()) return ScmDataScopeService.emptyPage(salesOrderQueryForm);
        var page = SmartPageUtil.convert2PageQuery(salesOrderQueryForm);
        var salesOrders = salesOrderDao.query(page, salesOrderQueryForm, dataScopeContext.getOrderSellerScope());
        return SmartPageUtil.convert2PageResult(page, salesOrders.stream().map(this::order).toList());
    }

    public SalesOrderVO order(SalesOrderEntity salesOrder) {
        var salesOrderVO = new SalesOrderVO();
        BeanUtils.copyProperties(salesOrder, salesOrderVO);
        salesOrderVO.setOrderId(salesOrder.getId());
        return salesOrderVO;
    }

    /**
     * 详情读（HTTP 入口用的默认形态）：按当前调用者的订单业务员范围判定，越权 30005。
     *
     * <p>范围判定放在这里而不是各个 Controller 里，是为了让「新加的读入口自动带范围」。
     * 写路径需要的是同一行的<b>未收窄</b>快照，见 {@link #detailSnapshot(Long)}。
     */
    public SalesOrderDetailVO detail(Long orderId) {
        return detail(orderId, dataScopeService.resolve());
    }

    /**
     * 详情读 + 显式范围：调用方已经解析过范围时用它，避免同一请求里解析两次。
     *
     * <p>订单不存在仍按 {@code ORDER_NOT_FOUND} 处理；存在但负责人不在授权范围内按 30005 拒绝
     * ——否则列表收窄只是「藏起来」，猜到 id 直接调详情就能读到别人的整单（含价格与收货地址）。
     */
    public SalesOrderDetailVO detail(Long orderId, ScmDataScopeContext dataScopeContext) {
        var salesOrder = salesOrderDao.selectById(orderId);
        if (salesOrder == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        if (!dataScopeContext.getOrderSellerScope().allows(salesOrder.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return detailSnapshot(salesOrder);
    }

    /**
     * 详情快照（<b>刻意不收范围</b>）：写路径在同一事务里取操作日志的前后镜像
     * （create/submit/confirm/cancel/actual-quantity，见 {@code SalesOrderService}），
     * 读的是自己刚写下的行，不是「查看别人的单」。数据范围只约束读接口，不约束写流程的自取快照。
     */
    public SalesOrderDetailVO detailSnapshot(Long orderId) {
        var salesOrder = salesOrderDao.selectById(orderId);
        if (salesOrder == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        return detailSnapshot(salesOrder);
    }

    private SalesOrderDetailVO detailSnapshot(SalesOrderEntity salesOrder) {
        var salesOrderDetailVO = new SalesOrderDetailVO();
        BeanUtils.copyProperties(order(salesOrder), salesOrderDetailVO);
        salesOrderDetailVO.setItems(
                salesOrderItemDao.list(salesOrder.getId()).stream()
                        .sorted(Comparator.comparing(SalesOrderItemEntity::getSortOrder)
                                .thenComparing(SalesOrderItemEntity::getId))
                        .map(salesOrderItem -> {
            var salesOrderItemVO = new SalesOrderItemVO();
            BeanUtils.copyProperties(salesOrderItem, salesOrderItemVO);
            salesOrderItemVO.setItemId(salesOrderItem.getId());
            return salesOrderItemVO;
        }).toList());
        salesOrderDetailVO.setAddress(
                orderAddressSnapshotDao.list(salesOrder.getId()).stream().findFirst().map(addressSnapshot -> {
            var addressSnapshotVO = new OrderAddressSnapshotVO();
            BeanUtils.copyProperties(addressSnapshot, addressSnapshotVO);
            return addressSnapshotVO;
        }).orElse(null));
        return salesOrderDetailVO;
    }

    /**
     * 某客户某 SKU 的最近成交参考价（只读）：仅供录单旁证，不回算当前价格、不参与定价。
     *
     * <p>刻意不按调用者范围收窄：它是录单时的取价旁证入参，客户与 SKU 都由前端选择，
     * 真正的下单校验在 {@code SalesOrderService} 的归属判定里做（读不到该客户就建不了单）。
     */
    public List<OrderRecentPriceVO> recentPrices(Long customerId, Long skuId, int limit) {
        int n = Math.min(Math.max(limit, RECENT_PRICE_MIN_LIMIT), RECENT_PRICE_MAX_LIMIT);
        return salesOrderItemDao.recentPrices(customerId, skuId, n);
    }

    /**
     * 操作日志列表：日志行没有归属列，范围经父订单生效（见 OrderOperationLogMapper.xml）。
     */
    public PageResult<OrderOperationLogVO> logs(OrderLogQueryForm logQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        if (dataScopeContext.getOrderSellerScope().isEmpty()) return ScmDataScopeService.emptyPage(logQueryForm);
        var page = SmartPageUtil.convert2PageQuery(logQueryForm);
        var operationLogs = orderOperationLogDao.query(page, logQueryForm, dataScopeContext.getOrderSellerScope());
        var employeeIds = operationLogs.stream()
                .map(OrderOperationLogEntity::getOperator)
                .map(operator -> Long.valueOf(operator.substring(operator.indexOf(':') + 1)))
                .distinct()
                .toList();
        var employeeNameById = employeeIds.isEmpty()
                ? Map.<Long, String>of()
                : employeeDao.selectBatchIds(employeeIds).stream()
                        .collect(Collectors.toMap(
                                employee -> employee.getEmployeeId(), employee -> employee.getActualName()));
        return SmartPageUtil.convert2PageResult(page, operationLogs.stream().map(operationLog -> {
            var operationLogVO = new OrderOperationLogVO();
            BeanUtils.copyProperties(operationLog, operationLogVO);
            operationLogVO.setLogId(operationLog.getId());
            operationLogVO.setOperatorName(
                    employeeNameById.getOrDefault(
                            Long.valueOf(operationLog.getOperator().split(":")[1]), operationLog.getOperator()));
            return operationLogVO;
        }).toList());
    }
}
