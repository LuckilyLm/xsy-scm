package com.xsy.scm.finance.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
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
import com.xsy.scm.finance.dao.FinancePayableSourceDao;
import com.xsy.scm.finance.domain.dto.FinancePayableTargetDto;
import com.xsy.scm.finance.domain.dto.FinancePayableSourceDto;
import com.xsy.scm.finance.domain.dto.FinancePayableSourceLineDto;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableItemEntity;
import com.xsy.scm.finance.domain.form.FinancePayableRedForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedItemForm;
import com.xsy.scm.finance.domain.vo.FinancePayableRedVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应付域服务。
 *
 * <p>
 * 收货确认会在同一事务内生成正常应付；本服务读取采购事实，只写入财务应付记录与操作日志。
 */
@Service
@RequiredArgsConstructor
public class FinancePayableService {

    private final FinancePayableDao financePayableDao;
    private final FinancePayableItemDao financePayableItemDao;
    private final FinancePayableSourceDao financePayableSourceDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 追加一张手工红字应付。先锁正常应付行再检查累计红字金额，让同一来源的并发红字串行执行。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePayableRedVO red(FinancePayableRedForm form, String idempotencyKey) {
        FinancePayableTargetDto original = financePayableDao.selectNormalTargetForUpdate(form.getOriginalPayableId());
        if (original == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
        }
        if (!dataScopeService.resolve().getPurchaserScope().allows(original.getPurchaserId())) {
            throw new ScmDataScopeException();
        }

        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_REASON_REQUIRED);
        }
        var claim = idempotencyService.claim(FinanceConstant.PAYABLE_RED_SCOPE + ":" + original.getPayableId(),
            idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinancePayableRedVO.class);
        }

        List<FinancePayableItemEntity> items = manualRedItems(original.getPayableId(), form.getItems());
        BigDecimal redAmount = items.stream().map(FinancePayableItemEntity::getAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
        BigDecimal existingRedAmount = financePayableDao.selectRedAmount(original.getPayableId());
        if (redAmount.signum() <= 0 || existingRedAmount.add(redAmount).compareTo(original.getAmount()) > 0) {
            throw new ScmBusinessException(FinanceErrorCode.RED_AMOUNT_EXCEEDED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinancePayableEntity red = new FinancePayableEntity();
        red.setPayableNo(
            ScmDocumentNumbers.format(FinanceConstant.PAYABLE_NO_PREFIX, financePayableDao.nextPayableNo()));
        red.setSourceType(ScmFinancePayableSourceTypeEnum.MANUAL.name());
        red.setSourceId(null);
        red.setPurchaseOrderId(original.getPurchaseOrderId());
        red.setSupplierId(original.getSupplierId());
        red.setSupplierNameSnapshot(original.getSupplierNameSnapshot());
        red.setEntryType(ScmFinanceEntryTypeEnum.RED.name());
        red.setOriginalPayableId(original.getPayableId());
        red.setAmount(redAmount);
        red.setEventAt(now);
        red.setReason(reason);
        red.setCreatedAt(now);
        red.setUpdatedAt(now);
        red.setCreatedBy(operator);
        red.setUpdatedBy(operator);
        if (financePayableDao.insert(red) != 1) {
            throw new IllegalStateException("手工红字应付未保存: " + red.getPayableNo());
        }

        for (FinancePayableItemEntity item : items) {
            item.setPayableId(red.getId());
            if (financePayableItemDao.insert(item) != 1) {
                throw new IllegalStateException("手工红字应付明细未保存: " + item.getPurchaseOrderItemId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYABLE, red.getId(), ScmFinanceOperationTypeEnum.RED_GENERATE,
            reason, null, redSnapshot(red, items.size()));

        FinancePayableRedVO result = redVO(red, items.size());
        idempotencyService.complete(claim, "FINANCE_PAYABLE", red.getId(), result);
        return result;
    }

    private List<FinancePayableItemEntity> manualRedItems(Long originalPayableId,
        List<FinancePayableRedItemForm> forms) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();
        List<FinancePayableItemEntity> items = new ArrayList<>(forms.size());
        for (FinancePayableRedItemForm form : forms) {
            FinancePayableItemEntity originalItem = financePayableItemDao.selectOriginalItem(originalPayableId,
                form.getPurchaseOrderItemId());
            if (originalItem == null) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }

            BigDecimal quantity = ScmDecimalStrings.parseScale4Required(form.getQuantity());
            BigDecimal unitPrice = ScmDecimalStrings.parseScale4Required(form.getUnitPrice());
            BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
            if (quantity.signum() <= 0 || unitPrice.signum() < 0 || amount.signum() <= 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
            BigDecimal expectedAmount = quantity.multiply(unitPrice).setScale(FinanceConstant.AMOUNT_SCALE,
                RoundingMode.HALF_UP);
            if (amount.compareTo(expectedAmount) != 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }

            FinancePayableItemEntity item = new FinancePayableItemEntity();
            item.setSourceType(ScmFinancePayableItemSourceTypeEnum.MANUAL.name());
            item.setSourceId(null);
            item.setPurchaseOrderItemId(originalItem.getPurchaseOrderItemId());
            item.setSkuId(originalItem.getSkuId());
            item.setSkuNameSnapshot(originalItem.getSkuNameSnapshot());
            item.setUnitSnapshot(originalItem.getUnitSnapshot());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setCreatedBy(operator);
            item.setUpdatedBy(operator);
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> redSnapshot(FinancePayableEntity red, int itemCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payableNo", red.getPayableNo());
        snapshot.put("sourceType", red.getSourceType());
        snapshot.put("sourceId", red.getSourceId());
        snapshot.put("purchaseOrderId", red.getPurchaseOrderId());
        snapshot.put("supplierId", red.getSupplierId());
        snapshot.put("supplierNameSnapshot", red.getSupplierNameSnapshot());
        snapshot.put("entryType", red.getEntryType());
        snapshot.put("originalPayableId", red.getOriginalPayableId());
        snapshot.put("amount", red.getAmount().toPlainString());
        snapshot.put("eventAt", red.getEventAt().toString());
        snapshot.put("reason", red.getReason());
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }

    private FinancePayableRedVO redVO(FinancePayableEntity red, int itemCount) {
        FinancePayableRedVO vo = new FinancePayableRedVO();
        vo.setPayableId(red.getId());
        vo.setPayableNo(red.getPayableNo());
        vo.setOriginalPayableId(red.getOriginalPayableId());
        vo.setSupplierId(red.getSupplierId());
        vo.setSupplierName(red.getSupplierNameSnapshot());
        vo.setEntryType(red.getEntryType());
        vo.setAmount(red.getAmount());
        vo.setEventAt(red.getEventAt());
        vo.setReason(red.getReason());
        vo.setItemCount(itemCount);
        return vo;
    }

    /**
     * 收货确认 → 正常应付。
     *
     * <p>
     * 必须与触发它的事务同成败，因此传播级别是 {@code MANDATORY} 而不是 {@code REQUIRED}：后者在没有外层事务时会自己提交， 造出「收货单还是草稿、应付已经入账」的孤立记录，而
     * {@code confirm} 后续库存写入失败时也没有回滚它的机会。
     *
     * <p>
     * 不是用户命令，因此不使用 {@code Idempotency-Key}：防重复请求由 {@code confirm} 自身的幂等与「只有 DRAFT 可确认」的状态校验承担；
     * 防重复数据由数据库来源唯一索引承担，命中即视为已生成并成功返回。
     *
     * <p>
     * 不锁任何行、不碰任何业务表：业务锁已由 {@code confirm} 持有，并发的双触发由 {@code uk_finance_payable_source_active} 仲裁。
     *
     * @param purchaseReceiptId
     *            刚被置为 {@code CONFIRMED} 的收货单 id
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnReceiptConfirm(Long purchaseReceiptId) {
        FinancePayableSourceDto source = financePayableSourceDao.selectConfirmedReceipt(purchaseReceiptId);
        if (source == null) {
            // 走到这里说明调用点把未确认（或不存在）的收货单当成已确认收货，跳过会留下
            // 「业务以为财务已挂账、财务什么都没记」的缺口，因此必须失败并让收货确认一起回滚。
            throw new IllegalStateException("收货单未处于 CONFIRMED 状态，不能生成应付: " + purchaseReceiptId);
        }

        List<
            FinancePayableSourceLineDto> lines = financePayableSourceDao.selectConfirmedReceiptLines(purchaseReceiptId);
        generate(source, lines);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnPurchaseAccount(Long accountId) {
        var source = financePayableSourceDao.selectPurchaseAccount(accountId);
        if (source == null) {
            throw new IllegalStateException("采购确认事实不存在: " + accountId);
        }
        generate(source, financePayableSourceDao.selectPurchaseAccountLines(accountId));
    }

    private void generate(FinancePayableSourceDto source, List<FinancePayableSourceLineDto> lines) {
        List<FinancePayableItemEntity> items = toItems(lines);
        BigDecimal amount = items.stream().map(FinancePayableItemEntity::getAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);

        if (amount.signum() <= 0) {
            // 没有有效明细，或单价为 0 导致金额为 0：不产生 0 元财务记录，也不建空单头；
            // 0 元记录会永久挂在待核销列表里且无法核销。
            return;
        }

        FinancePayableEntity payable = header(source, amount);
        if (financePayableDao.insertOnConflictDoNothing(payable) == 0) {
            return;
        }
        for (FinancePayableItemEntity item : items) {
            item.setPayableId(payable.getId());
            if (financePayableItemDao.insertOnConflictDoNothing(item) != 1) {
                // 单头是本次刚插入的，因此这里撞键不是重放，而是同一收货行被挂到了两张应付单上。
                throw new IllegalStateException("收货行已挂在别的应付单上，本次生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYABLE, payable.getId(), ScmFinanceOperationTypeEnum.GENERATE,
            null, null, generatedSnapshot(payable, items.size()));
    }

    /**
     * 应付单头。{@code originalPayableId} 与 {@code reason} 保持 {@code null} —— {@code ck_finance_payable_source_pairing} 要求
     * NORMAL 行两者皆空。
     */
    private FinancePayableEntity header(FinancePayableSourceDto source, BigDecimal amount) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        FinancePayableEntity payable = new FinancePayableEntity();
        payable.setPayableNo(
            ScmDocumentNumbers.format(FinanceConstant.PAYABLE_NO_PREFIX, financePayableDao.nextPayableNo()));
        payable.setSourceType(source.getPurchaseAccountId() == null
            ? ScmFinancePayableSourceTypeEnum.PURCHASE_RECEIPT.name()
            : ScmFinancePayableSourceTypeEnum.PURCHASE_ACCOUNT.name());
        payable.setSourceId(
            source.getPurchaseAccountId() == null ? source.getPurchaseReceiptId() : source.getPurchaseAccountId());
        payable.setPurchaseOrderId(source.getPurchaseOrderId());
        payable.setSupplierId(source.getSupplierId());
        payable.setSupplierNameSnapshot(source.getSupplierNameSnapshot());
        payable.setEntryType(ScmFinanceEntryTypeEnum.NORMAL.name());
        payable.setAmount(amount);
        payable.setEventAt(source.getConfirmedAt());
        payable.setCreatedAt(now);
        payable.setUpdatedAt(now);
        payable.setCreatedBy(operator);
        payable.setUpdatedBy(operator);
        return payable;
    }

    /**
     * 明细装配：数量取收货行的有效量、价格取采购行的结算单价，金额按 4 位小数 HALF_UP 舍入。单头金额是已按 4 位舍入的行金额之和， 不是「先求和再舍入」——后者会让单头与明细对不上账，对账时也没人能解释那半分钱的差额。
     */
    private List<FinancePayableItemEntity> toItems(List<FinancePayableSourceLineDto> lines) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal amount = line.getQuantity().multiply(line.getUnitPrice()).setScale(FinanceConstant.AMOUNT_SCALE,
                RoundingMode.HALF_UP);

            FinancePayableItemEntity item = new FinancePayableItemEntity();
            item.setSourceType(line.getPurchaseAccountItemId() == null
                ? ScmFinancePayableItemSourceTypeEnum.PURCHASE_RECEIPT_ITEM.name()
                : ScmFinancePayableItemSourceTypeEnum.PURCHASE_ACCOUNT_ITEM.name());
            item.setSourceId(line.getPurchaseAccountItemId() == null
                ? line.getPurchaseReceiptItemId()
                : line.getPurchaseAccountItemId());
            item.setPurchaseOrderItemId(line.getPurchaseOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setAmount(amount);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setCreatedBy(operator);
            item.setUpdatedBy(operator);
            return item;
        }).toList();
    }

    /**
     * 财务生成日志的金额和时间写入 JSON 前须转成字符串，避免通用 JSONB 转换器无法序列化时间类型。
     */
    private Map<String, Object> generatedSnapshot(FinancePayableEntity payable, int itemCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payableNo", payable.getPayableNo());
        snapshot.put("sourceType", payable.getSourceType());
        snapshot.put("sourceId", payable.getSourceId());
        snapshot.put("purchaseOrderId", payable.getPurchaseOrderId());
        snapshot.put("supplierId", payable.getSupplierId());
        snapshot.put("supplierNameSnapshot", payable.getSupplierNameSnapshot());
        snapshot.put("entryType", payable.getEntryType());
        snapshot.put("amount", payable.getAmount().toPlainString());
        snapshot.put("eventAt", payable.getEventAt().toString());
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }
}
