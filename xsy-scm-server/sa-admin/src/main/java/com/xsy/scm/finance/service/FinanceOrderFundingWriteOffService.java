package com.xsy.scm.finance.service;

import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult.Status;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 系统订单资金核销；上游持有订单锁，本服务按资金来源 → 正常应收顺序加锁。 */
@Service
@RequiredArgsConstructor
public class FinanceOrderFundingWriteOffService {
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;
    private final FinanceReceiptDao financeReceiptDao;
    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceWriteOffDao financeWriteOffDao;
    private final FinanceOperationLogRecorder operationLogs;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public FinanceFundingAllocationResult register(Long transactionId, Long receivableId) {
        var funding = financeOrderFundingSourceDao.selectTransaction(transactionId);
        financeOrderFundingPolicy.requireSuccessful(funding);
        boolean balance = ScmPaymentMethodEnum.BALANCE.name().equals(funding.getMethod());
        String sourceType = (balance
                ? ScmFinanceWriteOffSourceTypeEnum.BALANCE_MOVEMENT
                : ScmFinanceWriteOffSourceTypeEnum.RECEIPT).name();
        Long sourceId = balance ? funding.getMovementId() : funding.getReceiptId();
        BigDecimal effective;
        if (balance) {
            CustomerBalanceMovementEntity movement = financeOrderFundingSourceDao.lockMovement(sourceId);
            if (movement == null || !Objects.equals(movement.getSourceId(), funding.getIntentId())) {
                throw invalid();
            }
            effective = movement.getAmount();
        } else {
            var receipt = financeReceiptDao.selectByIdForUpdate(sourceId);
            if (receipt == null || !ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(receipt.getEntryType())
                    || !Objects.equals(receipt.getCustomerId(), funding.getCustomerId())
                    || !Objects.equals(receipt.getSettlementCustomerId(), funding.getSettlementCustomerId())
                    || receipt.getAmount().compareTo(funding.getProviderAmount()) != 0
                    || financeOrderFundingSourceDao.isRechargeReceipt(sourceId)) {
                throw invalid();
            }
            effective = receipt.getAmount().subtract(financeReceiptDao.selectReversedAmount(sourceId));
        }
        var lockedTarget = financeReceivableDao.selectNormalTargetForUpdate(receivableId);
        FinanceReceivableEntity target = financeReceivableDao.selectById(receivableId);
        if (lockedTarget == null || target == null || !Objects.equals(funding.getOrderId(), target.getOrderId())
                || !Objects.equals(funding.getCustomerId(), target.getCustomerId())
                || !Objects.equals(funding.getSettlementCustomerId(), target.getSettlementCustomerId())) {
            throw invalid();
        }
        var existing = financeWriteOffDao.selectNormalAllocation(sourceType, sourceId, balance ? null : receivableId);
        if (existing != null) {
            if (balance && (!Objects.equals(existing.getTargetId(), receivableId)
                    || !ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(existing.getTargetType())
                    || existing.getAmount().compareTo(effective) != 0)) {
                throw invalid();
            }
            boolean reversed = financeWriteOffDao.hasReversal(existing.getId());
            return result(transactionId, reversed ? Status.REVERSED : Status.ALREADY_ALLOCATED, BigDecimal.ZERO);
        }
        BigDecimal available = effective.subtract(financeWriteOffDao.selectSourceUsedAmount(sourceType, sourceId));
        if (available.signum() < 0) {
            throw invalid();
        }
        if (available.signum() == 0) {
            return result(transactionId, Status.NO_AVAILABLE_FUNDS, BigDecimal.ZERO);
        }
        if (balance && available.compareTo(effective) != 0) {
            throw invalid();
        }
        // 订单专属资金完整关联原债权，RED 或少发导致的超额只在读侧表达。
        BigDecimal before = financeWriteOffDao
                .selectTargetWrittenOffAmount(ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name(), receivableId);
        FinanceWriteOffEntity entity = new FinanceWriteOffEntity();
        entity.setWriteOffNo(
                ScmDocumentNumbers.format(FinanceConstant.WRITE_OFF_NO_PREFIX, financeWriteOffDao.nextWriteOffNo()));
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setTargetType(ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name());
        entity.setTargetId(receivableId);
        entity.setAmount(available);
        entity.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
        OffsetDateTime now = OffsetDateTime.now();
        String operator = FinanceConstant.ORDER_FUNDING_OPERATOR;
        entity.setWrittenOffAt(now);
        entity.setOperator(operator);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        int inserted = balance
                ? financeWriteOffDao.insertBalanceOnConflictDoNothing(entity)
                : financeWriteOffDao.insert(entity);
        if (inserted == 0) {
            existing = financeWriteOffDao.selectNormalAllocation(sourceType, sourceId, null);
            if (existing == null || !Objects.equals(existing.getTargetId(), receivableId)
                    || !entity.getTargetType().equals(existing.getTargetType())
                    || existing.getAmount().compareTo(available) != 0) {
                throw invalid();
            }
            return result(transactionId,
                    financeWriteOffDao.hasReversal(existing.getId()) ? Status.REVERSED : Status.ALREADY_ALLOCATED,
                    BigDecimal.ZERO);
        }
        operationLogs.record(ScmFinanceBusinessTypeEnum.WRITE_OFF, entity.getId(),
                ScmFinanceOperationTypeEnum.WRITE_OFF, null, balanceSnapshot(target, before),
                balanceSnapshot(target, before.add(available)), operator);
        return result(transactionId, available.compareTo(effective) < 0 ? Status.PARTIALLY_AVAILABLE : Status.APPLIED,
                available);
    }

    private Map<String, Object> balanceSnapshot(FinanceReceivableEntity target, BigDecimal used) {
        BigDecimal net = target.getAmount().subtract(financeReceivableDao.selectRedAmount(target.getId()));
        return Map.of("targetId", target.getId(), "targetType", ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name(),
                "targetNo", target.getReceivableNo(), "netAmount", net.toPlainString(), "writtenOffAmount",
                used.toPlainString(), "openAmount", net.subtract(used).max(BigDecimal.ZERO).toPlainString(),
                "overAppliedAmount", used.subtract(net).max(BigDecimal.ZERO).toPlainString());
    }

    private static FinanceFundingAllocationResult result(Long transactionId, Status status, BigDecimal amount) {
        return new FinanceFundingAllocationResult(transactionId, status, amount);
    }

    private static ScmBusinessException invalid() {
        return new ScmBusinessException(FinanceErrorCode.ORDER_FUNDING_INVALID);
    }
}
