package com.xsy.scm.order.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;

import com.xsy.scm.order.domain.form.OrderRefundCompleteForm;
import com.xsy.scm.order.domain.form.OrderRefundQueryForm;

import com.xsy.scm.order.domain.vo.OrderRefundVO;

import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.SalesOrderDao;

import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderValidator;

import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_REFUND_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_REFUND_STATUS_INVALID;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class OrderRefundService {
    private final OrderRefundDao orderRefundDao;
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderService salesOrderService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final OrderOperationLogRecorder orderLogs;
    private final ScmDataScopeService dataScopeService;

    public PageResult<OrderRefundVO> query(OrderRefundQueryForm orderRefundQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        if (dataScopeContext.getOrderSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(orderRefundQueryForm);
        }
        var page = SmartPageUtil.convert2PageQuery(orderRefundQueryForm);
        return SmartPageUtil.convert2PageResult(page,
                orderRefundDao.query(page, orderRefundQueryForm,
                    dataScopeContext.getOrderSellerScope()).stream().map(this::vo).toList());
    }

    private OrderRefundVO vo(OrderRefundEntity refundEntity) {
        var refundVO = new OrderRefundVO();
        BeanUtils.copyProperties(refundEntity, refundVO);
        refundVO.setRefundId(refundEntity.getId());
        return refundVO;
    }

    /** 退款单详情读（HTTP 入口）：可见性跟随父订单的负责人范围。 */
    public OrderRefundVO detail(Long refundId) {
        return detail(refundId, dataScopeService.resolve());
    }

    /**
     * 退款单详情读 + 显式范围。退款金额是财务事实，读不到原订单的人也不能读到它，
     * 因此父订单缺失或不在范围内都按 30005 处理。
     */
    public OrderRefundVO detail(Long refundId, ScmDataScopeContext dataScopeContext) {
        var refundEntity = orderRefundDao.selectById(refundId);
        if (refundEntity == null) throw new ScmBusinessException(ORDER_REFUND_NOT_FOUND);
        var order = salesOrderDao.selectById(refundEntity.getOrderId());
        if (order == null || !dataScopeContext.getOrderSellerScope().allows(order.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return vo(refundEntity);
    }

    /**
     * 未收窄的详情：退款完成命令在同一事务里取改前/改后镜像并回传结果，
     * 该路径的门槛是订单锁与状态机，不是读范围。
     */
    public OrderRefundVO detailSnapshot(Long refundId) {
        var refundEntity = orderRefundDao.selectById(refundId);
        if (refundEntity == null) throw new ScmBusinessException(ORDER_REFUND_NOT_FOUND);
        return vo(refundEntity);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundVO complete(OrderRefundCompleteForm refundCompleteForm, String key) {
        var claim = orderIdempotencyService.claim(
                "ORDER_REFUND_COMPLETE:" + refundCompleteForm.getRefundId(), key, refundCompleteForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, OrderRefundVO.class);
        var before = detailSnapshot(refundCompleteForm.getRefundId());
        salesOrderService.lock(before.getOrderId());
        var refundEntity = orderRefundDao.lock(refundCompleteForm.getRefundId());
        SalesOrderService.version(refundEntity.getVersion(), refundCompleteForm.getVersion());
        if (!ScmOrderRefundStatusEnum.PENDING.name().equals(refundEntity.getStatus()))
            throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        refundEntity.setStatus(ScmOrderRefundStatusEnum.COMPLETED.name());
        refundEntity.setExternalReference(OrderValidator.trim(refundCompleteForm.getExternalReference()));
        refundEntity.setCompletedAt(OffsetDateTime.now());
        refundEntity.setUpdatedAt(refundEntity.getCompletedAt());
        refundEntity.setUpdatedBy(ScmOperator.current());
        try {
            if (orderRefundDao.updateById(refundEntity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        }
        var result = detailSnapshot(refundEntity.getId());
        // 退款完成同样是必须留痕的订单状态变更。镜像取未收窄的 detailSnapshot，
        // 日志要记真实状态，而不是按调用者读范围裁过的视图。
        orderLogs.record(refundEntity.getOrderId(), ScmOrderOperationTypeEnum.REFUND,
                "退款单 " + refundEntity.getRefundNo() + " 已完成", Map.of("status", before.getStatus()),
                Map.of("status", result.getStatus()));
        orderIdempotencyService.complete(claim, ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name(),
            refundEntity.getId(), result);
        return result;
    }
}
