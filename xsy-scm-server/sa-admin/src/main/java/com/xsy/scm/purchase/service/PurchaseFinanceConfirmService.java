package com.xsy.scm.purchase.service;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.purchase.dao.PurchaseAccountDao;
import com.xsy.scm.purchase.domain.vo.PurchaseAccountVO;
import com.xsy.scm.finance.service.FinancePayableService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseIdempotencyResourceTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.purchase.manager.PurchaseOrderStateMachine;
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
    private final PurchaseAccountDao accountDao;
    private final FinancePayableService payableService;

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAccountVO confirm(PurchaseFinanceConfirmForm form, String key) {
        var order = orderDao.lock(form.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND);
        }
        ownerResolver.requireVisible(order.getPurchaserId());
        warehouseScopeGuard.require(order.getWarehouseId());
        if (!dataScopeService.resolve().isCostVisible()) {
            throw new ScmDataScopeException();
        }
        var claim = idempotencyService.claim("PURCHASE_ACCOUNT_CONFIRM:" + order.getId(), key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseAccountVO.class);
        }
        if (!Objects.equals(order.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        // 新确认与历史收货共用采购单行锁，防止同一采购通过两个入口重复挂账。
        if (!ScmPurchaseStatusEnum.SUBMITTED.name().equals(order.getStatus())
            || accountDao.accountId(order.getId()) != null || accountDao.hasConfirmedReceipt(order.getId())) {
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
            if (ScmDecimalStrings.parseScale4Required(input.getQuantity()).signum() < 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
        }
        PurchaseAccountVO account = new PurchaseAccountVO();
        account.setAccountNo(ScmDocumentNumbers.format("PAC", accountDao.nextNumber()));
        account.setPurchaseOrderId(order.getId());
        account.setSupplierId(order.getSupplierId());
        account.setSupplierNameSnapshot(order.getSupplierNameSnapshot());
        account.setWarehouseId(order.getWarehouseId());
        account.setRemark(form.getRemark());
        account.setConfirmedAt(OffsetDateTime.now());
        account.setConfirmedBy(ScmOperator.current());
        var items = new ArrayList<PurchaseAccountVO.Item>();
        for (var line : lines) {
            var item = new PurchaseAccountVO.Item();
            item.setPurchaseOrderItemId(line.getId());
            item.setSkuId(line.getSkuId());
            item.setProductNameSnapshot(line.getProductNameSnapshot());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getPurchaseUnitSnapshot());
            item.setQuantity(ScmDecimalStrings.parseScale4Required(inputs.get(line.getId()).getQuantity()));
            item.setUnitPrice(line.getPurchasePrice());
            item.setAmount(item.getQuantity().multiply(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP));
            items.add(item);
        }
        if (items.stream().map(PurchaseAccountVO.Item::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum() <= 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        accountDao.insert(account);
        for (var item : items) {
            item.setAccountId(account.getId());
            accountDao.insertItem(item);
        }
        account.setItems(items);
        PurchaseOrderStateMachine.transition(order.getStatus(), ScmPurchaseStatusEnum.ACCOUNTED.name());
        order.setStatus(ScmPurchaseStatusEnum.ACCOUNTED.name());
        order.setUpdatedAt(account.getConfirmedAt());
        order.setUpdatedBy(account.getConfirmedBy());
        if (orderDao.updateById(order) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        payableService.generateOnPurchaseAccount(account.getId());
        idempotencyService.complete(claim, ScmPurchaseIdempotencyResourceTypeEnum.PURCHASE_ACCOUNT.name(),
            account.getId(), account);
        return account;
    }
}
