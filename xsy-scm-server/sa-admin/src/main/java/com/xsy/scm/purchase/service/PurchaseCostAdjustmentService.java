package com.xsy.scm.purchase.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.service.FinancePurchaseCostAdjustmentService;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseCostDirectionEnum;
import com.xsy.scm.purchase.dao.PurchaseCostAdjustmentDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.form.PurchaseCostAdjustmentForm;
import com.xsy.scm.purchase.domain.vo.PurchaseCostAdjustmentVO;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseCostAdjustmentService {
    private final PurchaseCostAdjustmentDao adjustmentDao;
    private final PurchaseOrderDao orderDao;
    private final PurchaseOwnerResolver ownerResolver;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;
    private final ScmDataScopeService dataScopeService;
    private final PurchaseIdempotencyService idempotencyService;
    private final FinancePurchaseCostAdjustmentService financeAdjustmentService;

    @Transactional(readOnly = true)
    public List<PurchaseCostAdjustmentVO> options(Long orderId) {
        requireVisible(orderDao.selectById(orderId));
        return adjustmentDao.options(orderId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseCostAdjustmentVO> history(Long orderId) {
        requireVisible(orderDao.selectById(orderId));
        return adjustmentDao.history(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseCostAdjustmentVO add(PurchaseCostAdjustmentForm form, String key) {
        if ((form.getReceiptItemId() == null) == (form.getAccountItemId() == null)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        var probe = source(form);
        if (probe == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_RECEIPT_NOT_FOUND);
        }
        requireVisible(orderDao.lock(probe.getPurchaseOrderId()));
        var claim = idempotencyService.claim("PURCHASE_COST_ADJUSTMENT:" + (form.getAccountItemId() == null
            ? "RECEIPT:" + form.getReceiptItemId()
            : "ACCOUNT:" + form.getAccountItemId()), key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseCostAdjustmentVO.class);
        }
        var source = source(form);
        if (source == null || !Objects.equals(source.getPreviousAdjustmentId(), form.getPreviousAdjustmentId())
            || source.getPreviousPrice()
                .compareTo(ScmDecimalStrings.parseScale4Required(form.getExpectedPrice())) != 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        BigDecimal price = ScmDecimalStrings.parseScale4Required(form.getNewPrice());
        BigDecimal previousAmount = source.getQuantity().multiply(source.getPreviousPrice()).setScale(4,
            RoundingMode.HALF_UP);
        BigDecimal nextAmount = source.getQuantity().multiply(price).setScale(4, RoundingMode.HALF_UP);
        BigDecimal difference = nextAmount.subtract(previousAmount);
        if (price.signum() < 0 || difference.signum() == 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        source.setAdjustmentNo(ScmDocumentNumbers.format("PCA", adjustmentDao.nextNumber()));
        source.setNewPrice(price);
        source.setDirection(difference.signum() > 0
            ? ScmPurchaseCostDirectionEnum.INCREASE.name()
            : ScmPurchaseCostDirectionEnum.DECREASE.name());
        source.setAmount(difference.abs());
        source.setReason(form.getReason().trim());
        source.setOccurredAt(OffsetDateTime.now());
        source.setCreatedBy(ScmOperator.current());
        adjustmentDao.insert(source);
        financeAdjustmentService.generate(source.getId());
        idempotencyService.complete(claim, "PURCHASE_COST_ADJUSTMENT", source.getId(), source);
        return source;
    }

    private PurchaseCostAdjustmentVO source(PurchaseCostAdjustmentForm form) {
        return form.getAccountItemId() == null
            ? adjustmentDao.source(form.getReceiptItemId())
            : adjustmentDao.accountSource(form.getAccountItemId());
    }

    private void requireVisible(PurchaseOrderEntity order) {
        if (order == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND);
        }
        ownerResolver.requireVisible(order.getPurchaserId());
        warehouseScopeGuard.require(order.getWarehouseId());
        if (!dataScopeService.resolve().isCostVisible()) {
            throw new ScmDataScopeException();
        }
    }
}
