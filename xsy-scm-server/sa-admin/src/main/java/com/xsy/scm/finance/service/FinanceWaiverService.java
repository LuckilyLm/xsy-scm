package com.xsy.scm.finance.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWaiverStatusEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWaiverDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinanceWaiverEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWaiverAddForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWaiverVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 抹零：对账时主动放弃的零头。
 *
 * <p>
 * 抹零本身不是收款，也不改应收金额：它写成一条 {@code source_type = 'WAIVER'} 的核销行， 未结额、账龄、对账单因此复用同一套「净额 - 核销净额」公式，不需要第二套余额。
 * 加锁顺序与核销一致：先应收，再抹零行，最后写核销与日志。
 */
@Service
@RequiredArgsConstructor
public class FinanceWaiverService {

    private final FinanceWaiverDao financeWaiverDao;
    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceWriteOffDao financeWriteOffDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 登记抹零：金额不得超过应收当前未结额，原因必填。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceWaiverVO add(FinanceWaiverAddForm form, String idempotencyKey) {
        BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
        String reason = StringUtils.trimToNull(form.getReason());
        if (amount.signum() <= 0 || reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_RECEIVABLE_INVALID);
        }

        // 先锁应收：与核销、红冲共用同一行锁，避免并发把未结额各算一遍
        FinanceReceivableTargetDto target = financeReceivableDao.selectNormalTargetForUpdate(form.getReceivableId());
        if (target == null) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_RECEIVABLE_INVALID);
        }
        requireVisible(target.getSellerId());

        var claim = idempotencyService.claim(FinanceConstant.WAIVER_ADD_SCOPE + ":" + form.getReceivableId(),
            idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceWaiverVO.class);
        }

        BigDecimal before = receivableBalance(target);
        if (amount.compareTo(before) > 0) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_AMOUNT_EXCEEDED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinanceWaiverEntity waiver = new FinanceWaiverEntity();
        waiver
            .setWaiverNo(ScmDocumentNumbers.format(FinanceConstant.WAIVER_NO_PREFIX, financeWaiverDao.nextWaiverNo()));
        waiver.setReceivableId(target.getReceivableId());
        waiver.setCustomerId(target.getCustomerId());
        waiver.setSettlementCustomerId(target.getSettlementCustomerId());
        waiver.setAmount(amount);
        waiver.setReason(reason);
        waiver.setCreatedAt(now);
        waiver.setUpdatedAt(now);
        waiver.setCreatedBy(operator);
        waiver.setUpdatedBy(operator);
        if (financeWaiverDao.insert(waiver) != 1) {
            throw new IllegalStateException("抹零记录未保存: " + waiver.getWaiverNo());
        }

        FinanceWriteOffEntity writeOff = newWriteOff(waiver, amount, reason, now, operator);
        if (financeWriteOffDao.insert(writeOff) != 1) {
            throw new IllegalStateException("抹零核销记录未保存: " + writeOff.getWriteOffNo());
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.WAIVER, waiver.getId(), ScmFinanceOperationTypeEnum.WAIVER_ADD,
            reason, null, waiverSnapshot(target, writeOff, amount, before.subtract(amount)));
        operationLogs.record(ScmFinanceBusinessTypeEnum.WRITE_OFF, writeOff.getId(),
            ScmFinanceOperationTypeEnum.WRITE_OFF, reason, balanceSnapshot(target, before),
            balanceSnapshot(target, before.subtract(amount)));

        FinanceWaiverVO result = waiverVO(waiver, target, writeOff, null);
        idempotencyService.complete(claim, "FINANCE_WAIVER", waiver.getId(), result);
        return result;
    }

    /**
     * 反向抹零：追加一条反向核销行把未结额放回来；抹零表不写状态，是否已反向由这条反向行推导。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceWaiverVO reverse(FinanceWaiverReverseForm form, String idempotencyKey) {
        FinanceWaiverEntity initial = financeWaiverDao.selectById(form.getWaiverId());
        if (initial == null) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_NOT_FOUND);
        }
        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_REASON_REQUIRED);
        }

        // 与核销反向同一加锁顺序：先应收，再抹零行
        FinanceReceivableTargetDto target = financeReceivableDao.selectNormalTargetForUpdate(initial.getReceivableId());
        if (target == null) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_RECEIVABLE_INVALID);
        }
        requireVisible(target.getSellerId());

        var claim = idempotencyService.claim(FinanceConstant.WAIVER_REVERSE_SCOPE + ":" + form.getWaiverId(),
            idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceWaiverVO.class);
        }

        FinanceWaiverEntity waiver = financeWaiverDao.selectByIdForUpdate(form.getWaiverId());
        if (waiver == null) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_NOT_FOUND);
        }
        FinanceWriteOffEntity original = financeWriteOffDao
            .selectNormalAllocation(ScmFinanceWriteOffSourceTypeEnum.WAIVER.name(), waiver.getId(), null);
        if (original == null) {
            throw new IllegalStateException("抹零核销记录缺失: " + waiver.getWaiverNo());
        }
        if (financeWriteOffDao.hasReversal(original.getId())) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_ALREADY_REVERSED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        BigDecimal before = receivableBalance(target);
        FinanceWriteOffEntity reversal = new FinanceWriteOffEntity();
        reversal.setWriteOffNo(
            ScmDocumentNumbers.format(FinanceConstant.WRITE_OFF_NO_PREFIX, financeWriteOffDao.nextWriteOffNo()));
        reversal.setSourceType(original.getSourceType());
        reversal.setSourceId(original.getSourceId());
        reversal.setTargetType(original.getTargetType());
        reversal.setTargetId(original.getTargetId());
        reversal.setAmount(original.getAmount());
        reversal.setEntryType(ScmFinanceReverseEntryTypeEnum.REVERSE.name());
        reversal.setReverseOfId(original.getId());
        reversal.setReason(reason);
        reversal.setWrittenOffAt(now);
        reversal.setOperator(operator);
        reversal.setCreatedAt(now);
        reversal.setUpdatedAt(now);
        reversal.setCreatedBy(operator);
        reversal.setUpdatedBy(operator);
        if (financeWriteOffDao.insertReverseOnConflictDoNothing(reversal) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_ALREADY_REVERSED);
        }

        BigDecimal after = before.add(original.getAmount());
        operationLogs.record(ScmFinanceBusinessTypeEnum.WAIVER, waiver.getId(),
            ScmFinanceOperationTypeEnum.WAIVER_REVERSE, reason,
            waiverSnapshot(target, original, original.getAmount(), before),
            waiverSnapshot(target, reversal, original.getAmount(), after));
        operationLogs.record(ScmFinanceBusinessTypeEnum.WRITE_OFF, original.getId(),
            ScmFinanceOperationTypeEnum.WRITE_OFF_REVERSE, reason, balanceSnapshot(target, before),
            balanceSnapshot(target, after));

        FinanceWaiverVO result = waiverVO(waiver, target, original, reversal);
        idempotencyService.complete(claim, "FINANCE_WAIVER", waiver.getId(), result);
        return result;
    }

    private void requireVisible(Long sellerId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (!scope.getOrderSellerScope().allows(sellerId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 应收当前未结额：净额（金额 - 红字）减去核销净额，与核销、应收列表同一口径。
     */
    private BigDecimal receivableBalance(FinanceReceivableTargetDto target) {
        BigDecimal netAmount = target.getAmount()
            .subtract(financeReceivableDao.selectRedAmount(target.getReceivableId()));
        BigDecimal writtenOffAmount = financeWriteOffDao
            .selectTargetWrittenOffAmount(ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name(), target.getReceivableId());
        return netAmount.subtract(writtenOffAmount).max(BigDecimal.ZERO);
    }

    private FinanceWriteOffEntity newWriteOff(FinanceWaiverEntity waiver, BigDecimal amount, String reason,
        OffsetDateTime now, String operator) {
        FinanceWriteOffEntity writeOff = new FinanceWriteOffEntity();
        writeOff.setSourceType(ScmFinanceWriteOffSourceTypeEnum.WAIVER.name());
        writeOff.setSourceId(waiver.getId());
        writeOff.setTargetType(ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name());
        writeOff.setTargetId(waiver.getReceivableId());
        writeOff.setAmount(amount);
        writeOff.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
        writeOff.setReverseOfId(null);
        writeOff.setReason(reason);
        writeOff.setWrittenOffAt(now);
        writeOff.setOperator(operator);
        writeOff.setCreatedAt(now);
        writeOff.setUpdatedAt(now);
        writeOff.setCreatedBy(operator);
        writeOff.setUpdatedBy(operator);
        writeOff.setWriteOffNo(
            ScmDocumentNumbers.format(FinanceConstant.WRITE_OFF_NO_PREFIX, financeWriteOffDao.nextWriteOffNo()));
        return writeOff;
    }

    private static Map<String, Object> balanceSnapshot(FinanceReceivableTargetDto target, BigDecimal openAmount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("targetType", ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name());
        snapshot.put("targetId", target.getReceivableId());
        snapshot.put("targetNo", target.getReceivableNo());
        snapshot.put("openAmount", openAmount.toPlainString());
        return snapshot;
    }

    private static Map<String, Object> waiverSnapshot(FinanceReceivableTargetDto target, FinanceWriteOffEntity writeOff,
        BigDecimal amount, BigDecimal openAmount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receivableId", target.getReceivableId());
        snapshot.put("receivableNo", target.getReceivableNo());
        snapshot.put("amount", amount.toPlainString());
        snapshot.put("writeOffNo", writeOff.getWriteOffNo());
        snapshot.put("openAmount", openAmount.toPlainString());
        return snapshot;
    }

    /**
     * 抹零回应；{@code reversal} 为空表示有效，非空表示已反向——状态从反向核销行推导，不读抹零表的列。
     */
    private static FinanceWaiverVO waiverVO(FinanceWaiverEntity waiver, FinanceReceivableTargetDto target,
        FinanceWriteOffEntity writeOff, FinanceWriteOffEntity reversal) {
        FinanceWaiverVO vo = new FinanceWaiverVO();
        vo.setWaiverId(waiver.getId());
        vo.setWaiverNo(waiver.getWaiverNo());
        vo.setReceivableId(waiver.getReceivableId());
        vo.setReceivableNo(target.getReceivableNo());
        vo.setCustomerId(waiver.getCustomerId());
        vo.setCustomerName(target.getCustomerNameSnapshot());
        vo.setSettlementCustomerId(waiver.getSettlementCustomerId());
        vo.setAmount(waiver.getAmount());
        vo.setReason(waiver.getReason());
        vo.setStatus(
            reversal == null ? ScmFinanceWaiverStatusEnum.NORMAL.name() : ScmFinanceWaiverStatusEnum.REVERSED.name());
        if (reversal != null) {
            vo.setReversedAt(reversal.getWrittenOffAt());
            vo.setReversedBy(reversal.getOperator());
            vo.setReverseReason(reversal.getReason());
        }
        vo.setWriteOffId(writeOff.getId());
        vo.setWriteOffNo(writeOff.getWriteOffNo());
        vo.setCreatedAt(waiver.getCreatedAt());
        vo.setCreatedBy(waiver.getCreatedBy());
        return vo;
    }
}
