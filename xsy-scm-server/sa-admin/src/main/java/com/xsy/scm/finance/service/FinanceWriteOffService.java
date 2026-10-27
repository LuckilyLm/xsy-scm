package com.xsy.scm.finance.service;

import com.xsy.scm.balance.constant.ScmBalanceDirectionEnum;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
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
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceAutoWriteOffTargetDto;
import com.xsy.scm.finance.domain.dto.FinancePayableTargetDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentQueryVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAutoPlanVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 核销分配、反向核销与目标余额派生。
 */
@Service
@RequiredArgsConstructor
public class FinanceWriteOffService {

    /**
     * 自动核销单次最多分配的目标数：超出部分留待下一次继续，避免一张收款一次拖住上千行应付。
     */
    private static final int AUTO_TARGET_LIMIT = 100;

    private final FinanceWriteOffDao financeWriteOffDao;
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceCounterpartySourceDao financeCounterpartySourceDao;
    private final FinanceReceiptDao financeReceiptDao;
    private final FinancePaymentDao financePaymentDao;
    private final FinanceReceivableDao financeReceivableDao;
    private final FinancePayableDao financePayableDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 把一张收款或供应商付款分配到一或多个未结目标。加锁顺序固定为：来源单据、排序后的目标、最后写入记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceWriteOffAddResultVO add(FinanceWriteOffAddForm form, String idempotencyKey) {
        String sourceType = sourceType(form.getSourceType());
        if (form.getSourceId() == null || form.getSourceId() <= 0 || form.getItems() == null
            || form.getItems().isEmpty()) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        List<Allocation> allocations = allocations(form.getItems());
        String scopeKey = FinanceConstant.WRITE_OFF_ADD_SCOPE + ":" + sourceType + ":" + form.getSourceId();
        SourceFact source = lockSource(sourceType, form.getSourceId());
        requireNotReserved(sourceType, source);
        var claim = idempotencyService.claim(scopeKey, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceWriteOffAddResultVO.class);
        }
        return applyClaimed(sourceType, claim, source, allocations);
    }

    /**
     * 按账单日期自动核销（FIFO）：只选资金来源，目标由服务端取同结算客户/供应商的未结单据，按账单日期、单据 id 升序逐笔分配。
     *
     * <p>
     * 单次最多分配 {@value #AUTO_TARGET_LIMIT} 个目标，剩余额留在资金来源的待核销额里，再执行一次自动核销即可继续； 不新建核销表，分配结果仍是普通的 {@code NORMAL} 核销记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceWriteOffAddResultVO autoAdd(FinanceWriteOffAutoForm form, String idempotencyKey) {
        String sourceType = sourceType(form.getSourceType());
        if (form.getSourceId() == null || form.getSourceId() <= 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        String scopeKey = FinanceConstant.WRITE_OFF_AUTO_SCOPE + ":" + sourceType + ":" + form.getSourceId();
        SourceFact source = lockSource(sourceType, form.getSourceId());
        requireNotReserved(sourceType, source);
        // 幂等先于重新规划：重试时余额可能已经被第一次执行分完，不能再规划出空分配把重放变成报错
        var claim = idempotencyService.claim(scopeKey, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceWriteOffAddResultVO.class);
        }
        List<PlannedAllocation> plan = planAuto(sourceType, source, dataScopeService.resolve());
        if (plan.isEmpty()) {
            throw new ScmBusinessException(FinanceErrorCode.AUTO_WRITE_OFF_NO_OPEN_TARGET);
        }
        List<Allocation> allocations = plan.stream().map(item -> new Allocation(item.targetId(), item.amount()))
            .toList();
        return applyClaimed(sourceType, claim, source, allocations);
    }

    /**
     * 自动核销的分配预览：不加锁、不写库，按当前余额给出顺序与金额，执行前由人确认。
     */
    public List<FinanceWriteOffAutoPlanVO> autoPreview(FinanceWriteOffAutoForm form) {
        String sourceType = sourceType(form.getSourceType());
        if (form.getSourceId() == null || form.getSourceId() <= 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        SourceFact source = readSource(sourceType, form.getSourceId());
        requireNotReserved(sourceType, source);
        return planAuto(sourceType, source, dataScopeService.resolve()).stream().map(allocation -> {
            FinanceWriteOffAutoPlanVO plan = new FinanceWriteOffAutoPlanVO();
            plan.setTargetId(allocation.targetId());
            plan.setTargetNo(allocation.targetNo());
            plan.setTargetName(allocation.targetName());
            plan.setEventAt(allocation.eventAt());
            plan.setOpenAmount(allocation.openAmount());
            plan.setAllocatedAmount(allocation.amount());
            return plan;
        }).toList();
    }

    /**
     * 钱包充值收款只用于订单抵扣，不能被当成人情核销的资金来源；手工与自动两条路径共用同一拦截。
     */
    private void requireNotReserved(String sourceType, SourceFact source) {
        if (ScmFinanceWriteOffSourceTypeEnum.RECEIPT.name().equals(sourceType)
            && financeOrderFundingSourceDao.isRechargeReceipt(source.id())) {
            throw new ScmBusinessException(FinanceErrorCode.RECHARGE_RECEIPT_RESERVED);
        }
    }

    /**
     * 目标锁定、范围校验与写入。手工与自动核销共用，保证两条路径的校验与加锁顺序完全一致。
     */
    private FinanceWriteOffAddResultVO applyClaimed(String sourceType, ScmIdempotencyService.Claim claim,
        SourceFact source, List<Allocation> allocations) {
        String targetType = targetType(sourceType);
        List<Long> targetIds = allocations.stream().map(Allocation::targetId).distinct().sorted().toList();
        Map<Long, TargetFact> targets = lockTargets(targetType, targetIds);
        ScmDataScopeContext scope = dataScopeService.resolve();

        for (Long targetId : targetIds) {
            requireTargetVisible(scope, targets.get(targetId));
        }

        BigDecimal sourceUsedAmount = financeWriteOffDao.selectSourceUsedAmount(sourceType, source.id());
        BigDecimal sourceAvailable = source.effectiveAmount().subtract(sourceUsedAmount);
        BigDecimal requestedAmount = allocations.stream().map(Allocation::amount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
        if (sourceAvailable.signum() < 0 || requestedAmount.compareTo(sourceAvailable) > 0) {
            throw new ScmBusinessException(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED);
        }

        Map<Long, TargetBalance> balances = new LinkedHashMap<>();
        for (Long targetId : targetIds) {
            TargetFact target = targets.get(targetId);
            requireMatchingCounterparty(source, target);
            balances.put(targetId, balance(target));
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        List<FinanceWriteOffVO> results = new ArrayList<>(allocations.size());
        for (Allocation allocation : allocations) {
            TargetFact target = targets.get(allocation.targetId());
            TargetBalance before = balances.get(allocation.targetId());
            if (allocation.amount().compareTo(before.openAmount()) > 0) {
                throw new ScmBusinessException(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED);
            }

            FinanceWriteOffEntity writeOff = new FinanceWriteOffEntity();
            writeOff.setWriteOffNo(
                ScmDocumentNumbers.format(FinanceConstant.WRITE_OFF_NO_PREFIX, financeWriteOffDao.nextWriteOffNo()));
            writeOff.setSourceType(sourceType);
            writeOff.setSourceId(source.id());
            writeOff.setTargetType(targetType);
            writeOff.setTargetId(target.id());
            writeOff.setAmount(allocation.amount());
            writeOff.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
            writeOff.setReverseOfId(null);
            writeOff.setReason(null);
            writeOff.setWrittenOffAt(now);
            writeOff.setOperator(operator);
            writeOff.setCreatedAt(now);
            writeOff.setUpdatedAt(now);
            writeOff.setCreatedBy(operator);
            writeOff.setUpdatedBy(operator);
            if (financeWriteOffDao.insert(writeOff) != 1) {
                throw new IllegalStateException("核销记录未保存: " + writeOff.getWriteOffNo());
            }

            TargetBalance after = before.apply(allocation.amount());
            operationLogs.record(ScmFinanceBusinessTypeEnum.WRITE_OFF, writeOff.getId(),
                ScmFinanceOperationTypeEnum.WRITE_OFF, null, balanceSnapshot(target, before),
                balanceSnapshot(target, after));
            balances.put(target.id(), after);
            sourceAvailable = sourceAvailable.subtract(allocation.amount());
            results.add(writeOffVO(writeOff, source, target));
        }

        FinanceWriteOffAddResultVO result = new FinanceWriteOffAddResultVO();
        result.setItems(results);
        idempotencyService.complete(claim, "FINANCE_WRITE_OFF", results.getFirst().getWriteOffId(), result);
        return result;
    }

    /**
     * 追加一条金额相等、方向相反的核销记录；原记录永不修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceWriteOffVO reverse(FinanceWriteOffReverseForm form, String idempotencyKey) {
        FinanceWriteOffEntity initial = financeWriteOffDao.selectActiveById(form.getWriteOffId());
        if (initial == null) {
            throw new ScmBusinessException(FinanceErrorCode.WRITE_OFF_NOT_FOUND);
        }
        if (!ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(initial.getEntryType())) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }
        if (ScmFinanceWriteOffSourceTypeEnum.WAIVER.name().equals(initial.getSourceType())) {
            // 抹零反向还要把事实置为 REVERSED，只能走抹零接口，否则两边状态会不一致
            throw new ScmBusinessException(FinanceErrorCode.WAIVER_REVERSE_VIA_WAIVER);
        }
        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_REASON_REQUIRED);
        }

        SourceFact source = lockSource(initial.getSourceType(), initial.getSourceId());
        TargetFact target = lockTarget(initial.getTargetType(), initial.getTargetId());
        requireTargetVisible(dataScopeService.resolve(), target);
        requireMatchingCounterparty(source, target);

        var claim = idempotencyService.claim(FinanceConstant.WRITE_OFF_REVERSE_SCOPE + ":" + initial.getId(),
            idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceWriteOffVO.class);
        }

        FinanceWriteOffEntity original = financeWriteOffDao.selectByIdForUpdate(initial.getId());
        if (original == null) {
            throw new ScmBusinessException(FinanceErrorCode.WRITE_OFF_NOT_FOUND);
        }
        if (!ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(original.getEntryType())) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }

        TargetBalance before = balance(target);
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
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
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }

        TargetBalance after = before.apply(original.getAmount().negate());
        operationLogs.record(ScmFinanceBusinessTypeEnum.WRITE_OFF, original.getId(),
            ScmFinanceOperationTypeEnum.WRITE_OFF_REVERSE, reason, balanceSnapshot(target, before),
            balanceSnapshot(target, after));

        FinanceWriteOffVO result = writeOffVO(reversal, source, target);
        idempotencyService.complete(claim, "FINANCE_WRITE_OFF", reversal.getId(), result);
        return result;
    }

    private SourceFact lockSource(String sourceType, Long sourceId) {
        if (ScmFinanceWriteOffSourceTypeEnum.BALANCE_MOVEMENT.name().equals(sourceType)) {
            var movement = financeOrderFundingSourceDao.lockMovement(sourceId);
            if (movement == null || !ScmBalanceMovementTypeEnum.CONSUME.name().equals(movement.getType())
                || !ScmBalanceDirectionEnum.DEBIT.name().equals(movement.getDirection())) {
                throw new ScmBusinessException(FinanceErrorCode.ORDER_FUNDING_INVALID);
            }
            var customer = financeCounterpartySourceDao.selectCustomer(movement.getCustomerId());
            if (customer == null) {
                throw new ScmDataScopeException();
            }
            if (!dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
                throw new ScmDataScopeException();
            }
            return new SourceFact(sourceType, sourceId, movement.getMovementNo(), customer.getCustomerName(),
                ScmFinanceCounterpartyTypeEnum.CUSTOMER.name(), movement.getSettlementCustomerId(),
                movement.getAmount());
        }
        if (ScmFinanceWriteOffSourceTypeEnum.RECEIPT.name().equals(sourceType)) {
            FinanceReceiptEntity receipt = financeReceiptDao.selectByIdForUpdate(sourceId);
            if (receipt == null || !ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(receipt.getEntryType())) {
                throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
            }
            var customer = financeCounterpartySourceDao.selectCustomer(receipt.getCustomerId());
            if (customer == null
                || !dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
                throw new ScmDataScopeException();
            }
            BigDecimal effectiveAmount = receipt.getAmount().subtract(financeReceiptDao.selectReversedAmount(sourceId));
            return new SourceFact(sourceType, sourceId, receipt.getReceiptNo(), receipt.getCustomerNameSnapshot(),
                ScmFinanceCounterpartyTypeEnum.CUSTOMER.name(), receipt.getSettlementCustomerId(), effectiveAmount);
        }
        if (ScmFinanceWriteOffSourceTypeEnum.PAYMENT.name().equals(sourceType)) {
            FinancePaymentEntity payment = financePaymentDao.selectByIdForUpdate(sourceId);
            if (payment == null || !ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(payment.getEntryType())) {
                throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
            }
            BigDecimal effectiveAmount = payment.getAmount().subtract(financePaymentDao.selectReversedAmount(sourceId));
            return new SourceFact(sourceType, sourceId, payment.getPaymentNo(), payment.getCounterpartyNameSnapshot(),
                payment.getCounterpartyType(), payment.getCounterpartyId(), effectiveAmount);
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    /**
     * 预览用的资金来源读取：与 {@link #lockSource} 同一可见性口径，但不加锁、不写库。
     */
    private SourceFact readSource(String sourceType, Long sourceId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (ScmFinanceWriteOffSourceTypeEnum.RECEIPT.name().equals(sourceType)) {
            FinanceReceiptQueryVO receipt = financeReceiptDao.selectQueryById(sourceId);
            if (receipt == null || !ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(receipt.getEntryType())) {
                throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
            }
            requireCustomerVisible(scope, receipt.getCustomerId());
            return new SourceFact(sourceType, sourceId, receipt.getReceiptNo(), receipt.getCustomerName(),
                ScmFinanceCounterpartyTypeEnum.CUSTOMER.name(), receipt.getSettlementCustomerId(),
                receipt.getEffectiveAmount());
        }
        if (ScmFinanceWriteOffSourceTypeEnum.PAYMENT.name().equals(sourceType)) {
            FinancePaymentQueryVO payment = financePaymentDao.selectQueryById(sourceId);
            if (payment == null || !ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(payment.getEntryType())) {
                throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
            }
            return new SourceFact(sourceType, sourceId, payment.getPaymentNo(), payment.getCounterpartyName(),
                payment.getCounterpartyType(), payment.getCounterpartyId(), payment.getEffectiveAmount());
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    private void requireCustomerVisible(ScmDataScopeContext scope, Long customerId) {
        var customer = financeCounterpartySourceDao.selectCustomer(customerId);
        if (customer == null || !scope.getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 按账单日期、单据 id 的顺序把来源剩余额逐笔分到未结目标；不超额，分不完的留在来源的待核销额里。
     */
    private List<PlannedAllocation> planAuto(String sourceType, SourceFact source, ScmDataScopeContext scope) {
        BigDecimal available = source.effectiveAmount()
            .subtract(financeWriteOffDao.selectSourceUsedAmount(sourceType, source.id()));
        if (available.signum() <= 0) {
            return List.of();
        }
        List<FinanceAutoWriteOffTargetDto> candidates;
        if (ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(targetType(sourceType))) {
            candidates = financeReceivableDao.selectAutoTargets(source.counterpartyId(), scope.getOrderSellerScope(),
                AUTO_TARGET_LIMIT);
        } else {
            // 只有供应商付款能核销应付；客户退款付款的对手方是客户，不存在可分配的应付
            if (!ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(source.counterpartyType())) {
                return List.of();
            }
            candidates = financePayableDao.selectAutoTargets(source.counterpartyId(), scope.getPurchaserScope(),
                AUTO_TARGET_LIMIT);
        }
        List<PlannedAllocation> plan = new ArrayList<>(candidates.size());
        BigDecimal remaining = available;
        for (FinanceAutoWriteOffTargetDto candidate : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal amount = candidate.getOpenAmount().min(remaining).setScale(FinanceConstant.AMOUNT_SCALE,
                RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                continue;
            }
            plan.add(new PlannedAllocation(candidate.getTargetId(), candidate.getTargetNo(), candidate.getTargetName(),
                candidate.getEventAt(), candidate.getOpenAmount(), amount));
            remaining = remaining.subtract(amount);
        }
        return plan;
    }

    private Map<Long, TargetFact> lockTargets(String targetType, List<Long> targetIds) {
        Map<Long, TargetFact> targets = new LinkedHashMap<>();
        if (ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(targetType)) {
            for (FinanceReceivableTargetDto dto : financeReceivableDao.selectNormalTargetsForUpdate(targetIds)) {
                targets.put(dto.getReceivableId(),
                    new TargetFact(targetType, dto.getReceivableId(), dto.getReceivableNo(),
                        dto.getCustomerNameSnapshot(), dto.getSettlementCustomerId(), dto.getSellerId(),
                        dto.getAmount()));
            }
            if (targets.size() != targetIds.size()) {
                throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
            }
            return targets;
        }
        for (FinancePayableTargetDto dto : financePayableDao.selectNormalTargetsForUpdate(targetIds)) {
            targets.put(dto.getPayableId(), new TargetFact(targetType, dto.getPayableId(), dto.getPayableNo(),
                dto.getSupplierNameSnapshot(), dto.getSupplierId(), dto.getPurchaserId(), dto.getAmount()));
        }
        if (targets.size() != targetIds.size()) {
            throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
        }
        return targets;
    }

    private TargetFact lockTarget(String targetType, Long targetId) {
        if (ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(targetType)) {
            FinanceReceivableTargetDto dto = financeReceivableDao.selectNormalTargetForUpdate(targetId);
            if (dto == null) {
                throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
            }
            return new TargetFact(targetType, dto.getReceivableId(), dto.getReceivableNo(),
                dto.getCustomerNameSnapshot(), dto.getSettlementCustomerId(), dto.getSellerId(), dto.getAmount());
        }
        if (ScmFinanceWriteOffTargetTypeEnum.PAYABLE.name().equals(targetType)) {
            FinancePayableTargetDto dto = financePayableDao.selectNormalTargetForUpdate(targetId);
            if (dto == null) {
                throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
            }
            return new TargetFact(targetType, dto.getPayableId(), dto.getPayableNo(), dto.getSupplierNameSnapshot(),
                dto.getSupplierId(), dto.getPurchaserId(), dto.getAmount());
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    private void requireTargetVisible(ScmDataScopeContext scope, TargetFact target) {
        boolean visible = ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(target.type())
            ? scope.getOrderSellerScope().allows(target.ownerId())
            : scope.getPurchaserScope().allows(target.ownerId());
        if (!visible) {
            throw new ScmDataScopeException();
        }
    }

    private void requireMatchingCounterparty(SourceFact source, TargetFact target) {
        boolean receiptToReceivable = (ScmFinanceWriteOffSourceTypeEnum.RECEIPT.name().equals(source.type())
            || ScmFinanceWriteOffSourceTypeEnum.BALANCE_MOVEMENT.name().equals(source.type()))
            && ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(target.type())
            && source.counterpartyId().equals(target.counterpartyId());
        boolean supplierPaymentToPayable = ScmFinanceWriteOffSourceTypeEnum.PAYMENT.name().equals(source.type())
            && ScmFinanceWriteOffTargetTypeEnum.PAYABLE.name().equals(target.type())
            && ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(source.counterpartyType())
            && source.counterpartyId().equals(target.counterpartyId());
        if (!receiptToReceivable && !supplierPaymentToPayable) {
            throw new ScmBusinessException(FinanceErrorCode.COUNTERPARTY_MISMATCH);
        }
    }

    private TargetBalance balance(TargetFact target) {
        BigDecimal redAmount = ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(target.type())
            ? financeReceivableDao.selectRedAmount(target.id())
            : financePayableDao.selectRedAmount(target.id());
        BigDecimal writtenOffAmount = financeWriteOffDao.selectTargetWrittenOffAmount(target.type(), target.id());
        return TargetBalance.from(target.amount().subtract(redAmount), writtenOffAmount);
    }

    private static List<Allocation> allocations(List<FinanceWriteOffAddItemForm> forms) {
        List<Allocation> result = new ArrayList<>(forms.size());
        for (FinanceWriteOffAddItemForm form : forms) {
            if (form.getTargetId() == null || form.getTargetId() <= 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
            BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
            if (amount.signum() <= 0) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
            result.add(new Allocation(form.getTargetId(), amount));
        }
        return result;
    }

    private static String sourceType(String raw) {
        String value = StringUtils.trimToNull(raw);
        for (ScmFinanceWriteOffSourceTypeEnum candidate : List.of(ScmFinanceWriteOffSourceTypeEnum.RECEIPT,
            ScmFinanceWriteOffSourceTypeEnum.PAYMENT)) {
            if (candidate.name().equals(value)) {
                return candidate.name();
            }
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    private static String targetType(String sourceType) {
        return switch (ScmFinanceWriteOffSourceTypeEnum.valueOf(sourceType)) {
            case RECEIPT, BALANCE_MOVEMENT, WAIVER -> ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name();
            case PAYMENT -> ScmFinanceWriteOffTargetTypeEnum.PAYABLE.name();
        };
    }

    private static Map<String, Object> balanceSnapshot(TargetFact target, TargetBalance balance) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("targetType", target.type());
        snapshot.put("targetId", target.id());
        snapshot.put("targetNo", target.documentNo());
        snapshot.put("netAmount", balance.netAmount().toPlainString());
        snapshot.put("writtenOffAmount", balance.writtenOffAmount().toPlainString());
        snapshot.put("openAmount", balance.openAmount().toPlainString());
        snapshot.put("overAppliedAmount", balance.overAppliedAmount().toPlainString());
        return snapshot;
    }

    private static FinanceWriteOffVO writeOffVO(FinanceWriteOffEntity entity, SourceFact source, TargetFact target) {
        FinanceWriteOffVO vo = new FinanceWriteOffVO();
        vo.setWriteOffId(entity.getId());
        vo.setWriteOffNo(entity.getWriteOffNo());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceId(entity.getSourceId());
        vo.setSourceNo(source.documentNo());
        vo.setSourceName(source.counterpartyName());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setTargetNo(target.documentNo());
        vo.setTargetName(target.counterpartyName());
        vo.setAmount(entity.getAmount());
        vo.setEntryType(entity.getEntryType());
        vo.setReverseOfId(entity.getReverseOfId());
        vo.setReason(entity.getReason());
        vo.setWrittenOffAt(entity.getWrittenOffAt());
        vo.setOperator(entity.getOperator());
        return vo;
    }

    private record Allocation(Long targetId, BigDecimal amount) {
    }

    private record PlannedAllocation(Long targetId, String targetNo, String targetName, OffsetDateTime eventAt,
        BigDecimal openAmount, BigDecimal amount) {
    }

    private record SourceFact(String type, Long id, String documentNo, String counterpartyName, String counterpartyType,
        Long counterpartyId, BigDecimal effectiveAmount) {
    }

    private record TargetFact(String type, Long id, String documentNo, String counterpartyName, Long counterpartyId,
        Long ownerId, BigDecimal amount) {
    }

    private record TargetBalance(BigDecimal netAmount, BigDecimal writtenOffAmount, BigDecimal openAmount,
        BigDecimal overAppliedAmount) {

        private static TargetBalance from(BigDecimal netAmount, BigDecimal writtenOffAmount) {
            BigDecimal openAmount = netAmount.subtract(writtenOffAmount).max(BigDecimal.ZERO);
            BigDecimal overAppliedAmount = writtenOffAmount.subtract(netAmount).max(BigDecimal.ZERO);
            return new TargetBalance(netAmount, writtenOffAmount, openAmount, overAppliedAmount);
        }

        private TargetBalance apply(BigDecimal delta) {
            return from(netAmount, writtenOffAmount.add(delta));
        }
    }
}
