package com.xsy.scm.finance.service;

import java.math.BigDecimal;
import java.util.Map;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePayableItemSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePayableSourceTypeEnum;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePayableItemDao;
import com.xsy.scm.finance.dao.FinancePurchaseCostEntryDao;
import com.xsy.scm.finance.dao.FinancePurchaseCostSourceDao;
import com.xsy.scm.finance.domain.dto.FinancePurchaseCostSourceDto;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableItemEntity;
import com.xsy.scm.finance.domain.entity.FinancePurchaseCostEntryEntity;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.purchase.constant.ScmPurchaseCostDirectionEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancePurchaseCostAdjustmentService {
    private final FinancePurchaseCostSourceDao sourceDao;
    private final FinancePurchaseCostEntryDao entryDao;
    private final FinancePayableDao payableDao;
    private final FinancePayableItemDao payableItemDao;
    private final FinanceOperationLogRecorder operationLogs;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generate(Long adjustmentId) {
        var source = sourceDao.find(adjustmentId);
        if (source == null) {
            throw new IllegalStateException("采购价差事实不存在");
        }
        if (ScmPurchaseCostDirectionEnum.INCREASE.name().equals(source.getDirection())) {
            append(source, source.getAmount(), null);
            return;
        }
        BigDecimal remaining = source.getAmount();
        var targets = entryDao.lockTargets(source.getReceiptItemId(), source.getReceiptId(),
            source.getPurchaseOrderItemId());
        for (var locked : targets) {
            // 等待行锁期间可能新增红字，取得全部锁后用新语句重新读取可冲余额。
            var target = entryDao.capacity(locked.getPayableId(), source.getPurchaseOrderItemId());
            BigDecimal available = target.getLineAmount().subtract(target.getLineRedAmount())
                .min(target.getAmount().subtract(target.getRedAmount())).max(BigDecimal.ZERO);
            BigDecimal allocated = remaining.min(available);
            if (allocated.signum() > 0) {
                append(source, allocated, target.getPayableId());
                remaining = remaining.subtract(allocated);
            }
            if (remaining.signum() == 0) {
                break;
            }
        }
        if (remaining.signum() > 0) {
            // 已有手工红字可能占用可冲余额，不能让成本更正单独成功而应付少冲一部分。
            throw new ScmBusinessException(FinanceErrorCode.RED_AMOUNT_EXCEEDED);
        }
    }

    private void append(FinancePurchaseCostSourceDto source, BigDecimal amount, Long originalPayableId) {
        FinancePurchaseCostEntryEntity entry = new FinancePurchaseCostEntryEntity();
        entry.setAdjustmentId(source.getId());
        entry.setReceiptItemId(source.getReceiptItemId());
        entry.setOriginalPayableId(originalPayableId);
        entry.setAmount(amount);
        entry.setCreatedBy(source.getCreatedBy());
        entryDao.insert(entry);
        FinancePayableEntity payable = new FinancePayableEntity();
        payable.setPayableNo(ScmDocumentNumbers.format(FinanceConstant.PAYABLE_NO_PREFIX, payableDao.nextPayableNo()));
        payable.setSourceType(ScmFinancePayableSourceTypeEnum.PURCHASE_COST_ADJUSTMENT.name());
        payable.setSourceId(entry.getId());
        payable.setPurchaseOrderId(source.getPurchaseOrderId());
        payable.setSupplierId(source.getSupplierId());
        payable.setSupplierNameSnapshot(source.getSupplierNameSnapshot());
        payable.setEntryType(
            originalPayableId == null ? ScmFinanceEntryTypeEnum.NORMAL.name() : ScmFinanceEntryTypeEnum.RED.name());
        payable.setOriginalPayableId(originalPayableId);
        payable.setAmount(amount);
        payable.setEventAt(source.getOccurredAt());
        payable.setReason(source.getReason());
        payable.setCreatedAt(source.getOccurredAt());
        payable.setUpdatedAt(source.getOccurredAt());
        payable.setCreatedBy(source.getCreatedBy());
        payable.setUpdatedBy(source.getCreatedBy());
        payableDao.insert(payable);
        FinancePayableItemEntity item = new FinancePayableItemEntity();
        item.setPayableId(payable.getId());
        item.setSourceType(ScmFinancePayableItemSourceTypeEnum.PURCHASE_COST_ADJUSTMENT.name());
        item.setSourceId(entry.getId());
        item.setPurchaseOrderItemId(source.getPurchaseOrderItemId());
        item.setSkuId(source.getSkuId());
        item.setSkuNameSnapshot(source.getProductNameSnapshot());
        // 价差是金额更正，数量不代表再次收货；原实收数量与新旧单价保留在采购更正单。
        item.setUnitSnapshot("笔");
        item.setQuantity(BigDecimal.ONE.setScale(4));
        item.setUnitPrice(amount);
        item.setAmount(amount);
        item.setCreatedAt(source.getOccurredAt());
        item.setUpdatedAt(source.getOccurredAt());
        item.setCreatedBy(source.getCreatedBy());
        item.setUpdatedBy(source.getCreatedBy());
        payableItemDao.insert(item);
        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYABLE, payable.getId(),
            originalPayableId == null ? ScmFinanceOperationTypeEnum.GENERATE : ScmFinanceOperationTypeEnum.RED_GENERATE,
            source.getReason(), null,
            Map.of("adjustmentNo", source.getAdjustmentNo(), "amount", amount.toPlainString()));
    }
}
