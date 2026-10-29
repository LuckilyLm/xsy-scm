package com.xsy.scm.purchase.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.constant.ScmWeighingSourceEnum;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseFinanceConfirmService {
    private final PurchaseOrderDao orderDao;
    private final PurchaseOrderItemDao orderItemDao;
    private final PurchaseOwnerResolver ownerResolver;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;
    private final ScmDataScopeService dataScopeService;
    private final PurchaseIdempotencyService idempotencyService;
    private final PurchaseReceiptService receiptService;

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO confirm(PurchaseFinanceConfirmForm form, String key) {
        var order = orderDao.lock(form.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND);
        }
        ownerResolver.requireVisible(order.getPurchaserId());
        warehouseScopeGuard.require(order.getWarehouseId());
        if (!dataScopeService.resolve().isCostVisible()) {
            throw new ScmDataScopeException();
        }
        var claim = idempotencyService.claim("PURCHASE_FINANCE_CONFIRM:" + order.getId(), key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseReceiptVO.class);
        }
        if (!Objects.equals(order.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        // 已经分批收货的采购仍走正式收货页，避免用整单快捷确认重复记录已经收到的数量。
        if (!ScmPurchaseStatusEnum.SUBMITTED.name().equals(order.getStatus())) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }
        var lines = orderItemDao.lockByOrderId(order.getId());
        Map<Long, PurchaseFinanceConfirmForm.Item> inputs = new HashMap<>();
        for (var input : form.getItems()) {
            if (inputs.putIfAbsent(input.getOrderItemId(), input) != null) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
        }
        if (inputs.size() != lines.size() || lines.isEmpty()) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        for (var line : lines) {
            var input = inputs.get(line.getId());
            if (input == null || !Objects.equals(input.getVersion(), line.getVersion())
                || !Objects.equals(input.getPurchaseUnit(), line.getPurchaseUnitSnapshot())) {
                throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
            }
            if (Boolean.TRUE.equals(line.getPricePending()) || line.getPurchasePrice() == null) {
                throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_PRICE_PENDING);
            }
            if (line.getPurchasePrice()
                .compareTo(ScmDecimalStrings.parseScale4Required(input.getExpectedPrice())) != 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
            }
            PurchaseReceiptQuantityCalculator.declared(input.getQuantity());
        }
        PurchaseReceiptCreateForm create = new PurchaseReceiptCreateForm();
        create.setPurchaseOrderId(order.getId());
        create.setReceiptMode(ScmReceiptModeEnum.DIRECT.name());
        create.setRemark(form.getRemark());
        PurchaseReceiptVO receipt = receiptService.create(create, key);
        PurchaseReceiptConfirmForm confirmation = new PurchaseReceiptConfirmForm();
        confirmation.setId(receipt.getId());
        confirmation.setVersion(receipt.getVersion());
        var receiptInputs = new ArrayList<PurchaseReceiptConfirmForm.Item>();
        for (var line : receipt.getItems()) {
            var input = inputs.get(line.getPurchaseOrderItemId());
            PurchaseReceiptConfirmForm.Item confirmed = new PurchaseReceiptConfirmForm.Item();
            confirmed.setReceiptItemId(line.getId());
            confirmed.setVersion(line.getVersion());
            confirmed.setReceivedQuantity(input.getQuantity());
            if (ScmProductTypeEnum.NON_STANDARD.name().equals(line.getProductType())) {
                confirmed.setActualWeight(input.getQuantity());
                confirmed.setWeightSource(ScmWeighingSourceEnum.MANUAL.name());
            }
            confirmed.setLots(input.getLots());
            receiptInputs.add(confirmed);
        }
        confirmation.setItems(receiptInputs);
        PurchaseReceiptVO result = receiptService.confirm(confirmation, key);
        idempotencyService.complete(claim, "PURCHASE_RECEIPT", result.getId(), result);
        return result;
    }
}
