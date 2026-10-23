package com.xsy.scm.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.OrderReceivedCorrectionDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.form.OrderReceivedCorrectionForm;
import com.xsy.scm.order.domain.vo.OrderReceivedCorrectionCandidateVO;
import com.xsy.scm.order.domain.vo.OrderReceivedCorrectionVO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_NOT_OWNED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RECEIVED_CORRECTION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RECEIVED_CORRECTION_INVALID;

@Service
@RequiredArgsConstructor
public class OrderReceivedCorrectionService {
    private final SalesOrderService salesOrderService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderReceivedCorrectionDao correctionDao;
    private final ScmDataScopeService dataScopeService;
    private final OrderIdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public List<OrderReceivedCorrectionVO> list(Long orderId) {
        salesOrderQueryService.detail(orderId);
        return correctionDao.list(orderId);
    }

    @Transactional(readOnly = true)
    public OrderReceivedCorrectionCandidateVO candidate(Long orderId, Long orderItemId) {
        salesOrderQueryService.detail(orderId);
        var item = salesOrderItemDao.selectById(orderItemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !Objects.equals(item.getOrderId(), orderId))
            throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        BigDecimal shipped = correctionDao.shippedQuantity(orderItemId);
        if (shipped == null || shipped.signum() <= 0 || correctionDao.signedAssignmentCount(orderId) == 0)
            throw new ScmBusinessException(ORDER_RECEIVED_CORRECTION_INVALID);
        var latest = correctionDao.latest(orderItemId);
        var candidate = new OrderReceivedCorrectionCandidateVO();
        candidate.setOrderItemId(orderItemId);
        candidate.setLastCorrectionId(latest == null ? 0L : latest.getId());
        candidate.setSaleUnit(item.getSaleUnitSnapshot());
        candidate.setShippedQuantity(shipped);
        candidate.setCurrentReceivedQuantity(latest == null ? shipped : latest.getReceivedQuantity());
        return candidate;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReceivedCorrectionVO record(OrderReceivedCorrectionForm form, String key) {
        var claim = idempotencyService.claim("ORDER_RECEIVED_CORRECTION:" + form.getOrderItemId(), key, form);
        if (claim.replay()) {
            salesOrderQueryService.detail(form.getOrderId());
            return idempotencyService.replay(claim, OrderReceivedCorrectionVO.class);
        }
        var order = salesOrderService.lock(form.getOrderId());
        if (!dataScopeService.resolve().getOrderSellerScope().allows(order.getSellerId()))
            throw new ScmDataScopeException();
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus())
            || correctionDao.signedAssignmentCount(order.getId()) == 0)
            throw new ScmBusinessException(ORDER_RECEIVED_CORRECTION_INVALID);
        var item = salesOrderItemDao.selectById(form.getOrderItemId());
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !Objects.equals(item.getOrderId(), order.getId()))
            throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        BigDecimal shipped = correctionDao.shippedQuantity(item.getId());
        if (shipped == null || shipped.signum() <= 0)
            throw new ScmBusinessException(ORDER_RECEIVED_CORRECTION_INVALID);
        var latest = correctionDao.latest(item.getId());
        long lastId = latest == null ? 0 : latest.getId();
        if (form.getExpectedLastCorrectionId() != lastId)
            throw new ScmBusinessException(ORDER_RECEIVED_CORRECTION_CONFLICT);
        BigDecimal previous = latest == null ? shipped : latest.getReceivedQuantity();
        BigDecimal received = ScmDecimalStrings.parseScale4Required(form.getReceivedQuantity());
        if (received.signum() < 0 || received.compareTo(shipped) > 0 || received.compareTo(previous) == 0
            || form.getReason() == null || form.getReason().isBlank())
            throw new ScmBusinessException(ORDER_RECEIVED_CORRECTION_INVALID);
        var correction = new OrderReceivedCorrectionVO();
        correction.setOrderId(order.getId());
        correction.setOrderItemId(item.getId());
        correction.setSaleUnitSnapshot(item.getSaleUnitSnapshot());
        correction.setShippedQuantity(shipped);
        correction.setPreviousReceivedQuantity(previous);
        correction.setReceivedQuantity(received);
        correction.setReason(form.getReason().trim());
        correction.setCreatedBy(ScmOperator.current());
        correction.setCreatedAt(OffsetDateTime.now());
        correctionDao.insert(correction);
        idempotencyService.complete(claim, "ORDER_RECEIVED_CORRECTION", correction.getId(), correction);
        return correction;
    }
}
