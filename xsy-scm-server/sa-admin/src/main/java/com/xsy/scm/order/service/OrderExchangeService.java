package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.dao.OrderExchangeLinkDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.form.OrderExchangeCreateForm;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_EXCHANGE_EXISTS;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_EXCHANGE_NOT_READY;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderExchangeService {
    private final OrderReturnDao returnDao;
    private final OrderReturnItemDao returnItemDao;
    private final OrderReturnReceiptItemDao receiptItemDao;
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderService salesOrderService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final OrderExchangeLinkDao exchangeLinkDao;
    private final OrderIdempotencyService idempotencyService;
    private final ScmDataScopeService dataScopeService;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO create(OrderExchangeCreateForm form, String key) {
        var claim = idempotencyService.claim("ORDER_EXCHANGE_CREATE:" + form.getReturnId(), key, form);
        if (claim.replay()) {
            var returned = returnDao.selectById(form.getReturnId());
            requireVisible(returned);
            var result = idempotencyService.replay(claim, SalesOrderDetailVO.class);
            salesOrderQueryService.detail(result.getOrderId());
            return result;
        }
        var returned = returnDao.lock(form.getReturnId());
        requireVisible(returned);
        Long existingReplacement = exchangeLinkDao.replacementOrderId(returned.getId());
        if (existingReplacement != null)
            throw new ScmBusinessException(ORDER_EXCHANGE_EXISTS);
        if (!ScmOrderReturnStatusEnum.APPROVED.name().equals(returned.getStatus()))
            throw new ScmBusinessException(ORDER_EXCHANGE_NOT_READY);
        var items = returnItemDao.list(returned.getId());
        if (items.isEmpty() || items.stream()
            .noneMatch(item -> item.getApprovedQuantity() != null && item.getApprovedQuantity().signum() > 0))
            throw new ScmBusinessException(ORDER_EXCHANGE_NOT_READY);
        for (var item : items) {
            BigDecimal approved = item.getApprovedQuantity();
            if (approved == null || receiptItemDao.receivedQuantity(item.getId()).compareTo(approved) < 0)
                throw new ScmBusinessException(ORDER_EXCHANGE_NOT_READY);
        }
        var original = salesOrderDao.selectById(returned.getOrderId());
        var replacement = form.getOrder();
        if (original == null || !Objects.equals(original.getCustomerId(), replacement.getCustomerId()))
            throw new ScmBusinessException(ORDER_EXCHANGE_NOT_READY);
        replacement.setOrderSource(ScmOrderSourceEnum.SUPPLEMENT.name());
        replacement.setOriginalOrderId(original.getId());
        if (replacement.getSupplementReason() == null || replacement.getSupplementReason().isBlank())
            replacement.setSupplementReason("换货：退货单" + returned.getReturnNo());
        var result = salesOrderService.create(replacement, key + ":replacement");
        exchangeLinkDao.insert(returned.getId(), original.getId(), result.getOrderId(), ScmOperator.current());
        idempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(), result.getOrderId(),
            result);
        return result;
    }

    @Transactional(readOnly = true)
    public Long replacementOrderId(Long returnId) {
        var returned = returnDao.selectById(returnId);
        requireVisible(returned);
        Long replacementOrderId = exchangeLinkDao.replacementOrderId(returnId);
        if (replacementOrderId != null)
            salesOrderQueryService.detail(replacementOrderId);
        return replacementOrderId;
    }

    private void requireVisible(OrderReturnEntity returned) {
        if (returned == null || Boolean.TRUE.equals(returned.getDeleted()))
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        var original = salesOrderDao.selectById(returned.getOrderId());
        if (original == null || Boolean.TRUE.equals(original.getDeleted())
            || !dataScopeService.resolve().getOrderSellerScope().allows(original.getSellerId()))
            throw new ScmDataScopeException();
    }
}
