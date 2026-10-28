package com.xsy.scm.finance.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePostingRefundStatusEnum;
import com.xsy.scm.finance.constant.ScmFinancePostingStatusEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinancePostingDao;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.entity.FinancePostingEntity;
import com.xsy.scm.finance.domain.entity.FinancePostingVoidEntity;
import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingUpdateForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 过账特殊单据的登记、修改与作废。
 *
 * <p>
 * 这三件事都不写应收/应付、不写核销、不动余额：过账只是财务对外账目的留痕。 唯一会随它变化的是操作日志 —— 现场要求「所有操作需留痕」， 所以每次登记、修改、作废都按「过账 + id」追加一条，改前改后的内容都进日志。
 *
 * <p>
 * 加锁顺序与其它财务命令一致：先锁过账行，再写日志；作废另由 {@code uk_finance_posting_void_posting} 兜底，重复作废不会出现两条事实。
 */
@Service
@RequiredArgsConstructor
public class FinancePostingService {

    private final FinancePostingDao financePostingDao;
    private final FinanceCounterpartySourceDao counterpartySourceDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 登记过账：客户按注册快照保存，金额必须大于 0，税点与返款状态按白名单校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePostingVO add(FinancePostingAddForm form, String idempotencyKey) {
        BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_AMOUNT_INVALID);
        }
        BigDecimal taxRate = parseTaxRate(form.getTaxRate());
        String refundStatus = refundStatus(form.getRefundStatus());
        String remark = StringUtils.trimToNull(form.getRemark());

        FinanceCustomerFactDto customer = counterpartySourceDao.selectCustomer(form.getCustomerId());
        if (customer == null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_CUSTOMER_NOT_FOUND);
        }
        requireVisible(customer);

        var claim = idempotencyService.claim(FinanceConstant.POSTING_ADD_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinancePostingVO.class);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinancePostingEntity posting = new FinancePostingEntity();
        posting.setPostingNo(
            ScmDocumentNumbers.format(FinanceConstant.POSTING_NO_PREFIX, financePostingDao.nextPostingNo()));
        posting.setCustomerId(customer.getCustomerId());
        posting.setCustomerNameSnapshot(customer.getCustomerName());
        posting.setSettlementCustomerId(customer.getSettlementCustomerId());
        posting.setSettlementCustomerNameSnapshot(customer.getSettlementCustomerName());
        posting.setDocumentDate(form.getDocumentDate());
        posting.setAmount(amount);
        posting.setTaxRate(taxRate);
        posting.setRefundStatus(refundStatus);
        posting.setRemark(remark);
        posting.setCreatedAt(now);
        posting.setUpdatedAt(now);
        posting.setCreatedBy(operator);
        posting.setUpdatedBy(operator);
        if (financePostingDao.insert(posting) != 1) {
            throw new IllegalStateException("过账记录未保存: " + posting.getPostingNo());
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.POSTING, posting.getId(),
            ScmFinanceOperationTypeEnum.POSTING_ADD, remark, null, snapshot(posting));

        FinancePostingVO result = postingVO(posting, null);
        idempotencyService.complete(claim, "FINANCE_POSTING", posting.getId(), result);
        return result;
    }

    /**
     * 修改未作废的过账单。客户不可改：换客户等于换一张单，应作废后重新登记。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePostingVO update(FinancePostingUpdateForm form, String idempotencyKey) {
        BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_AMOUNT_INVALID);
        }
        BigDecimal taxRate = parseTaxRate(form.getTaxRate());
        String refundStatus = refundStatus(form.getRefundStatus());
        String remark = StringUtils.trimToNull(form.getRemark());

        FinancePostingEntity posting = requireLockedPosting(form.getPostingId());
        requireVersion(posting, form.getVersion());
        if (financePostingDao.selectVoidByPostingId(posting.getId()) != null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_ALREADY_VOIDED);
        }
        var claim = idempotencyService.claim(FinanceConstant.POSTING_UPDATE_SCOPE + ":" + posting.getId(),
            idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinancePostingVO.class);
        }

        Map<String, Object> before = snapshot(posting);
        posting.setDocumentDate(form.getDocumentDate());
        posting.setAmount(amount);
        posting.setTaxRate(taxRate);
        posting.setRefundStatus(refundStatus);
        posting.setRemark(remark);
        posting.setUpdatedAt(OffsetDateTime.now());
        posting.setUpdatedBy(ScmOperator.current());
        if (financePostingDao.updateById(posting) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.POSTING, posting.getId(),
            ScmFinanceOperationTypeEnum.POSTING_UPDATE, remark, before, snapshot(posting));

        FinancePostingVO result = postingVO(posting, null);
        idempotencyService.complete(claim, "FINANCE_POSTING", posting.getId(), result);
        return result;
    }

    /**
     * 作废过账：追加一条作废事实，原记录保留；作废后不可修改、不可再次作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePostingVO voidPosting(FinancePostingVoidForm form, String idempotencyKey) {
        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_VOID_REASON_REQUIRED);
        }

        FinancePostingEntity posting = requireLockedPosting(form.getPostingId());
        requireVersion(posting, form.getVersion());
        if (financePostingDao.selectVoidByPostingId(posting.getId()) != null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_ALREADY_VOIDED);
        }
        var claim = idempotencyService.claim(FinanceConstant.POSTING_VOID_SCOPE + ":" + posting.getId(), idempotencyKey,
            form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinancePostingVO.class);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinancePostingVoidEntity voidFact = new FinancePostingVoidEntity();
        voidFact.setPostingId(posting.getId());
        voidFact.setReason(reason);
        voidFact.setVoidedAt(now);
        voidFact.setCreatedAt(now);
        voidFact.setUpdatedAt(now);
        voidFact.setCreatedBy(operator);
        voidFact.setUpdatedBy(operator);
        if (financePostingDao.insertVoidOnConflictDoNothing(voidFact) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_ALREADY_VOIDED);
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.POSTING, posting.getId(),
            ScmFinanceOperationTypeEnum.POSTING_VOID, reason, snapshot(posting), voidSnapshot(posting, reason, now));

        FinancePostingVO result = postingVO(posting, voidFact);
        idempotencyService.complete(claim, "FINANCE_POSTING", posting.getId(), result);
        return result;
    }

    /**
     * 读取并锁住过账行；不存在 → 41166。范围校验在锁后做，避免锁住一个自己看不到的客户。
     */
    private FinancePostingEntity requireLockedPosting(Long postingId) {
        FinancePostingEntity posting = financePostingDao.selectByIdForUpdate(postingId);
        if (posting == null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_NOT_FOUND);
        }
        Long sellerId = financePostingDao.selectCustomerSellerId(postingId);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (!scope.getCustomerSellerScope().allows(sellerId)) {
            throw new ScmDataScopeException();
        }
        return posting;
    }

    private static void requireVersion(FinancePostingEntity posting, Integer version) {
        if (!posting.getVersion().equals(version)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    private void requireVisible(FinanceCustomerFactDto customer) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (!scope.getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    private static BigDecimal parseTaxRate(String raw) {
        BigDecimal taxRate = ScmDecimalStrings.parseScale4(raw);
        if (taxRate != null && (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_TAX_RATE_INVALID);
        }
        return taxRate;
    }

    /**
     * 返款状态白名单；不填按未返款登记。
     */
    private static String refundStatus(String raw) {
        String value = StringUtils.trimToNull(raw);
        if (value == null) {
            return ScmFinancePostingRefundStatusEnum.PENDING.name();
        }
        for (ScmFinancePostingRefundStatusEnum candidate : ScmFinancePostingRefundStatusEnum.values()) {
            if (candidate.name().equals(value)) {
                return value;
            }
        }
        throw new ScmBusinessException(FinanceErrorCode.POSTING_REFUND_STATUS_INVALID);
    }

    private static Map<String, Object> snapshot(FinancePostingEntity posting) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("postingNo", posting.getPostingNo());
        snapshot.put("customerId", posting.getCustomerId());
        snapshot.put("customerName", posting.getCustomerNameSnapshot());
        snapshot.put("documentDate", posting.getDocumentDate().toString());
        snapshot.put("amount", posting.getAmount().toPlainString());
        snapshot.put("taxRate", posting.getTaxRate() == null ? null : posting.getTaxRate().toPlainString());
        snapshot.put("refundStatus", posting.getRefundStatus());
        snapshot.put("remark", posting.getRemark());
        return snapshot;
    }

    private static Map<String, Object> voidSnapshot(FinancePostingEntity posting, String reason,
        OffsetDateTime voidedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>(snapshot(posting));
        snapshot.put("voidedAt", voidedAt.toString());
        snapshot.put("voidReason", reason);
        return snapshot;
    }

    /**
     * 过账回应；{@code voidFact} 为空表示有效。
     */
    private static FinancePostingVO postingVO(FinancePostingEntity posting, FinancePostingVoidEntity voidFact) {
        FinancePostingVO vo = new FinancePostingVO();
        vo.setPostingId(posting.getId());
        vo.setPostingNo(posting.getPostingNo());
        vo.setCustomerId(posting.getCustomerId());
        vo.setCustomerName(posting.getCustomerNameSnapshot());
        vo.setSettlementCustomerId(posting.getSettlementCustomerId());
        vo.setSettlementCustomerName(posting.getSettlementCustomerNameSnapshot());
        vo.setDocumentDate(posting.getDocumentDate());
        vo.setAmount(posting.getAmount());
        vo.setTaxRate(posting.getTaxRate());
        vo.setRefundStatus(posting.getRefundStatus());
        vo.setRemark(posting.getRemark());
        vo.setStatus(
            voidFact == null ? ScmFinancePostingStatusEnum.NORMAL.name() : ScmFinancePostingStatusEnum.VOIDED.name());
        if (voidFact != null) {
            vo.setVoidedAt(voidFact.getVoidedAt());
            vo.setVoidedBy(voidFact.getCreatedBy());
            vo.setVoidReason(voidFact.getReason());
        }
        vo.setVersion(posting.getVersion());
        vo.setCreatedAt(posting.getCreatedAt());
        vo.setUpdatedAt(posting.getUpdatedAt());
        vo.setCreatedBy(posting.getCreatedBy());
        vo.setUpdatedBy(posting.getUpdatedBy());
        return vo;
    }
}
