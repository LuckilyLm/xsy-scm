package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.service.FinancePayableService;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseIdempotencyResourceTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseAccountDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.dao.PurchaseShareDao;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.vo.PurchaseAccountVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareFeedbackVO;
import com.xsy.scm.purchase.manager.PurchaseOrderStateMachine;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final PurchaseShareDao shareDao;
    private final PurchasePriceService priceService;

    @Transactional(readOnly = true)
    public List<PurchaseShareFeedbackVO> feedback(Long orderId) {
        var order = orderDao.selectById(orderId);
        if (order == null)
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND);
        ownerResolver.requireVisible(order.getPurchaserId());
        warehouseScopeGuard.require(order.getWarehouseId());
        if (!dataScopeService.resolve().isCostVisible())
            throw new ScmDataScopeException();
        return shareDao.latestFeedback(orderId);
    }

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
        var feedback = shareDao.latestFeedback(order.getId()).stream()
            .collect(Collectors.toMap(PurchaseShareFeedbackVO::getPurchaseOrderItemId, Function.identity()));
        for (var line : lines) {
            var input = inputs.get(line.getId());
            if (input == null || !Objects.equals(input.getVersion(), line.getVersion())
                || !Objects.equals(input.getPurchaseUnit(), line.getPurchaseUnitSnapshot())) {
                throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
            }
            if (input.getFeedbackId() != null && (feedback.get(line.getId()) == null
                || !input.getFeedbackId().equals(feedback.get(line.getId()).getId()))) {
                throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
            }
            BigDecimal expected = input.getExpectedPrice() == null
                ? null
                : ScmDecimalStrings.parseScale4Required(input.getExpectedPrice());
            if ((expected == null) != (line.getPurchasePrice() == null)
                || (expected != null && expected.compareTo(line.getPurchasePrice()) != 0)) {
                throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
            }
            BigDecimal price = input.getConfirmedPrice() == null
                ? line.getPurchasePrice()
                : ScmDecimalStrings.parseScale4Required(input.getConfirmedPrice());
            if (price == null)
                throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_PRICE_PENDING);
            if (price.signum() < 0)
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            if (ScmDecimalStrings.parseScale4Required(input.getQuantity()).signum() < 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
        }
        // 采用回填价必须经过原补价/改价权限与审计；任何一行失败，价格和应付一起回滚。
        for (var line : lines) {
            var input = inputs.get(line.getId());
            if (input.getConfirmedPrice() == null || (line.getPurchasePrice() != null && line.getPurchasePrice()
                .compareTo(ScmDecimalStrings.parseScale4Required(input.getConfirmedPrice())) == 0))
                continue;
            var priceForm = new PurchasePriceForm();
            // 补价/改价只接受四位定点字符串；按服务端解析后的规范形态写入，避免客户端少写小数位被拒。
            priceForm.setPrice(ScmDecimalStrings.parseScale4Required(input.getConfirmedPrice()).toPlainString());
            priceForm.setReason(input.getFeedbackId() == null ? "确认采购时核对单价" : "确认采购时核对现场回填报价");
            if (ScmDataScopeService.hasPermission(PurchasePermission.PRICE_ADJUST)) {
                priceService.adjust(line.getId(), priceForm, key);
            } else if (Boolean.TRUE.equals(line.getPricePending())
                && ScmDataScopeService.hasPermission(PurchasePermission.PRICE_FILL)) {
                priceService.fill(line.getId(), priceForm, key);
            } else {
                throw new ScmDataScopeException();
            }
        }
        order = orderDao.lock(order.getId());
        lines = orderItemDao.lockByOrderId(order.getId());
        PurchaseAccountVO account = new PurchaseAccountVO();
        account.setAccountNo(ScmDocumentNumbers.next());
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
