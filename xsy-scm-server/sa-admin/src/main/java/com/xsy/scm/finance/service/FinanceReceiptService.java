package com.xsy.scm.finance.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.contract.CustomerSettlementAccountValidator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceiptMethodEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceiptSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.finance.support.FinancePaymentReceiptFact;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收款域服务。
 *
 * <p>
 * 只登记 {@code NORMAL} 收款。更正错误收款应追加反向事实，不修改、作废或软删原记录。
 *
 * <p>
 * <b>收款登记与核销是两件事</b>：登记一笔钱只产生 「一笔待核销款」这一事实，绝不自动创建 {@code finance_write_off}、绝不改 {@code finance_receivable} /
 * {@code sales_order} / {@code customer.credit_limit}。 允许预收：没有对应应收也可以登记收款。核销由单独命令完成。
 *
 * <p>
 * <b>不设第二套幂等基建</b>：复用既有 {@code idempotency_record} 与 {@link ScmIdempotencyService}（三段式 {@code claim → 写 → complete}
 * 同一事务）， 与 delivery / inventory / sorting 同一形态。 {@code external_reference} 只是资金凭据文本，不参与任何一层防重。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceiptService {

    private final FinanceReceiptDao financeReceiptDao;
    private final FinanceCounterpartySourceDao financeCounterpartySourceDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;
    private final CustomerSettlementAccountValidator customerSettlementAccountValidator;

    /**
     * 登记一笔 {@code NORMAL} 收款。
     *
     * <p>
     * <b>整条链必须同事务</b>：幂等 claim、收款事实、操作日志、幂等 complete 要么一起成， 要么一起不成。留下「收款已落库、日志没落」或「claim 已占、结果为空」都是不可接受的半成品 ——
     * 前者是证据链断裂，后者会让同一 key 的后续重放读到空结果。
     *
     * <p>
     * 范围判定在读到客户事实之后立刻做，且拒绝方式与「客户不存在」共用 {@link ScmDataScopeException}（对外 30005）：能分辨「存在但无权」就等于把主键探测
     * 变成了一个可用信号，因此有权与无权的客户查询返回相同结果。
     *
     * @param idempotencyKey
     *            请求级幂等键；同键同内容重放首次结果，同键异内容按既有语义报冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceReceiptVO add(FinanceReceiptAddForm form, String idempotencyKey) {
        FinanceCustomerFactDto customer = requireAuthorizedCustomer(form.getCustomerId());
        var claim = idempotencyService.claim(FinanceConstant.RECEIPT_ADD_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            FinanceReceiptVO replayed = idempotencyService.replay(claim, FinanceReceiptVO.class);
            requireAuthorizedCustomer(replayed.getCustomerId());
            return replayed;
        }

        customerSettlementAccountValidator.validateSettlementRelationship(customer.getCustomerId());
        FinanceReceiptEntity receipt = register(form, customer);
        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIPT, receipt.getId(), ScmFinanceOperationTypeEnum.RECEIVE,
                null, null, snapshot(receipt));

        FinanceReceiptVO result = vo(receipt);
        idempotencyService.complete(claim, "FINANCE_RECEIPT", receipt.getId(), result);
        return result;
    }

    /**
     * 追加一条 {@code REVERSE} 收款事实。原记录保持不变；该命令与核销共用原收款行锁， 因而在并发下也能保证反向前的有效已用额为 0。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceReceiptVO reverse(FinanceReceiptReverseForm form, String idempotencyKey) {
        FinanceReceiptEntity original = financeReceiptDao.selectByIdForUpdate(form.getReceiptId());
        if (original == null) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
        }
        requireAuthorizedCustomer(original.getCustomerId());
        if (!ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(original.getEntryType())) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }
        if (original.getSourceType() != null) {
            // 人工 reverse 的语义是「整笔登记错了，撤销这笔登记」，而支付退款是**业务退款**：
            // 可能部分、可能多次（100 退 30 再退 20）。现有 REVERSE 模型表达不了，
            // 硬套会把「退款」记成「纠错」，审计上再也分不开。退款资金反向另行设计（3-11b）。
            throw new ScmBusinessException(FinanceErrorCode.SYSTEM_RECEIPT_REVERSE_FORBIDDEN);
        }

        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_REASON_REQUIRED);
        }

        var claim = idempotencyService.claim(FinanceConstant.RECEIPT_REVERSE_SCOPE + ":" + original.getId(),
                idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinanceReceiptVO.class);
        }

        BigDecimal effectiveWriteOffAmount = financeReceiptDao.selectEffectiveWriteOffAmount(original.getId());
        if (effectiveWriteOffAmount == null || effectiveWriteOffAmount.signum() != 0) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_BLOCKED_BY_WRITE_OFF);
        }

        FinanceReceiptEntity reversal = newReverseRecord(original, reason);
        if (financeReceiptDao.insertReverseOnConflictDoNothing(reversal) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIPT, original.getId(),
                ScmFinanceOperationTypeEnum.RECEIPT_REVERSE, reason,
                effectiveAmountSnapshot(original, original.getAmount()),
                effectiveAmountSnapshot(reversal, BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE)));

        FinanceReceiptVO result = vo(reversal);
        idempotencyService.complete(claim, "FINANCE_RECEIPT", reversal.getId(), result);
        return result;
    }

    /**
     * 系统入口：支付交易成功后登记收款事实（ADM-12 3-11a）。
     *
     * <p>
     * <b>不复用人工的 {@code add(FinanceReceiptAddForm)}</b>：那个入口的字段假设「有人在填」， 方式、时点、外部凭据都由人给。系统来源必须由财务域自己决定这些 ——
     * 让支付域去凑一个人工表单， 等于把财务规则复制到支付域，两边迟早不一致。
     *
     * <p>
     * 三条与人工入口不同的地方：
     * <ol>
     * <li>方式固定 {@code ONLINE_PAYMENT}，不取调用方输入；</li>
     * <li>金额取**渠道实际成功捕获/结算的金额**（{@code providerAmount}），不取本地应付金额 —— 本地应付 100、渠道实收 98 时凭空登记 100 会让账实不符；</li>
     * <li>时点取渠道成功时间，不是本地登记时间。</li>
     * </ol>
     *
     * <p>
     * <b>幂等两层</b>：这里先按来源键查一次（常见的重复回调），库上的 {@code uk_finance_receipt_source_active} 是并发下真正的仲裁者。第二层刻意不吞异常： 事务里吞掉唯一键冲突会让
     * PostgreSQL 的整个事务进入失败状态，反而更危险； 让冲突冒出来，渠道重试时前置查询就能命中已有事实。
     *
     * @return 已存在的或新登记的收款事实
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceReceiptEntity registerFromPaymentTransaction(FinancePaymentReceiptFact fact) {
        if (fact == null || fact.transactionId() == null || fact.customerId() == null || fact.providerAmount() == null
                || fact.succeededAt() == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_RECEIPT_AMOUNT_MISSING);
        }
        BigDecimal amount = fact.providerAmount().setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_RECEIPT_AMOUNT_MISSING);
        }

        var funding = financeOrderFundingSourceDao.selectTransaction(fact.transactionId());
        financeOrderFundingPolicy.requireSuccessful(funding);
        if (!ScmPaymentMethodEnum.ONLINE.name().equals(funding.getMethod())
                || !fact.customerId().equals(funding.getCustomerId())
                || amount.compareTo(funding.getProviderAmount()) != 0
                || !fact.succeededAt().isEqual(funding.getPaidAt())) {
            throw new ScmBusinessException(FinanceErrorCode.ORDER_FUNDING_INVALID);
        }
        FinanceReceiptEntity existing = financeReceiptDao
                .selectBySource(ScmFinanceReceiptSourceTypeEnum.PAYMENT_TRANSACTION.name(), fact.transactionId());
        if (existing != null) {
            if (!existing.getCustomerId().equals(funding.getCustomerId())
                    || !existing.getSettlementCustomerId().equals(funding.getSettlementCustomerId())
                    || existing.getAmount().compareTo(amount) != 0) {
                throw new ScmBusinessException(FinanceErrorCode.ORDER_FUNDING_INVALID);
            }
            return existing;
        }

        // 客户与结算主体同样按客户域规则解析：系统来源不是绕过这条纪律的理由
        FinanceCustomerFactDto customer = financeCounterpartySourceDao.selectCustomer(fact.customerId());
        if (customer == null) {
            throw new ScmDataScopeException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = funding.getOperator() == null
                ? FinanceConstant.ORDER_FUNDING_OPERATOR
                : funding.getOperator();
        FinanceReceiptEntity receipt = new FinanceReceiptEntity();
        receipt.setReceiptNo(
                ScmDocumentNumbers.format(FinanceConstant.RECEIPT_NO_PREFIX, financeReceiptDao.nextReceiptNo()));
        receipt.setCustomerId(customer.getCustomerId());
        receipt.setCustomerNameSnapshot(customer.getCustomerName());
        receipt.setSettlementCustomerId(funding.getSettlementCustomerId());
        receipt.setSettlementCustomerNameSnapshot(funding.getSettlementCustomerName());
        receipt.setAmount(amount);
        receipt.setMethod(ScmFinanceReceiptMethodEnum.ONLINE_PAYMENT.name());
        receipt.setReceivedAt(fact.succeededAt());
        receipt.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
        receipt.setReverseOfId(null);
        receipt.setReason(null);
        receipt.setExternalReference(StringUtils.trimToNull(fact.providerTransactionNo()));
        receipt.setRemark(null);
        receipt.setSourceType(ScmFinanceReceiptSourceTypeEnum.PAYMENT_TRANSACTION.name());
        receipt.setSourceId(fact.transactionId());
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);
        receipt.setCreatedBy(operator);
        receipt.setUpdatedBy(operator);
        if (financeReceiptDao.insert(receipt) != 1) {
            throw new IllegalStateException("支付收款登记未落库: " + receipt.getReceiptNo());
        }
        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIPT, receipt.getId(), ScmFinanceOperationTypeEnum.RECEIVE,
                null, null, snapshot(receipt), operator);
        return receipt;
    }

    /**
     * 创建收款事实本身（不含幂等三段式）；反向收款由追加反向事实的命令处理。
     */
    private FinanceCustomerFactDto requireAuthorizedCustomer(Long customerId) {
        FinanceCustomerFactDto customer = financeCounterpartySourceDao.selectCustomer(customerId);
        if (customer == null || !dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return customer;
    }

    private FinanceReceiptEntity newReverseRecord(FinanceReceiptEntity original, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinanceReceiptEntity reversal = new FinanceReceiptEntity();
        reversal.setReceiptNo(
                ScmDocumentNumbers.format(FinanceConstant.RECEIPT_NO_PREFIX, financeReceiptDao.nextReceiptNo()));
        reversal.setCustomerId(original.getCustomerId());
        reversal.setCustomerNameSnapshot(original.getCustomerNameSnapshot());
        reversal.setSettlementCustomerId(original.getSettlementCustomerId());
        reversal.setSettlementCustomerNameSnapshot(original.getSettlementCustomerNameSnapshot());
        reversal.setAmount(original.getAmount());
        reversal.setMethod(original.getMethod());
        reversal.setReceivedAt(now);
        reversal.setEntryType(ScmFinanceReverseEntryTypeEnum.REVERSE.name());
        reversal.setReverseOfId(original.getId());
        reversal.setReason(reason);
        // 反向单据是本次纠错事实，不沿用原资金凭据或备注。
        reversal.setExternalReference(null);
        reversal.setRemark(null);
        reversal.setCreatedAt(now);
        reversal.setUpdatedAt(now);
        reversal.setCreatedBy(operator);
        reversal.setUpdatedBy(operator);
        return reversal;
    }

    private static Map<String, Object> effectiveAmountSnapshot(FinanceReceiptEntity receipt,
            BigDecimal effectiveAmount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receiptId", receipt.getId());
        snapshot.put("receiptNo", receipt.getReceiptNo());
        snapshot.put("entryType", receipt.getEntryType());
        snapshot.put("reverseOfId", receipt.getReverseOfId());
        snapshot.put("effectiveAmount", effectiveAmount.toPlainString());
        snapshot.put("reason", receipt.getReason());
        return snapshot;
    }

    private FinanceReceiptEntity register(FinanceReceiptAddForm form, FinanceCustomerFactDto customer) {

        BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
        if (amount.signum() <= 0) {
            // 0 元收款不是财务事实：库级 CHECK (amount > 0) 是第二层，这里先给出可解释的 40000。
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        // 客户域只读契约先验证当前结算关系；快照值仍来自 FinanceCounterpartySourceDao，财务域不重算或替换。
        // 已声明结算主体却读不到未删除的那一行说明主档悬空：这两列在 finance_receipt 上都是 NOT NULL，
        // 宁可拒绝登记，也不能把付款客户自己的名字写成结算主体快照（历史事实不可改，写错就永久错）。
        if (customer.getSettlementCustomerId() == null || customer.getSettlementCustomerName() == null) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();

        FinanceReceiptEntity receipt = new FinanceReceiptEntity();
        receipt.setReceiptNo(
                ScmDocumentNumbers.format(FinanceConstant.RECEIPT_NO_PREFIX, financeReceiptDao.nextReceiptNo()));
        receipt.setCustomerId(customer.getCustomerId());
        receipt.setCustomerNameSnapshot(customer.getCustomerName());
        receipt.setSettlementCustomerId(customer.getSettlementCustomerId());
        receipt.setSettlementCustomerNameSnapshot(customer.getSettlementCustomerName());
        receipt.setAmount(amount);
        receipt.setMethod(method(form.getMethod()));
        receipt.setReceivedAt(form.getReceivedAt());
        receipt.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
        // REVERSE 专用列在 NORMAL 行上必须为空（ck_finance_receipt_entry_pairing）。
        receipt.setReverseOfId(null);
        receipt.setReason(null);
        receipt.setExternalReference(StringUtils.trimToNull(form.getExternalReference()));
        receipt.setRemark(StringUtils.trimToNull(form.getRemark()));
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);
        receipt.setCreatedBy(operator);
        receipt.setUpdatedBy(operator);
        if (financeReceiptDao.insert(receipt) != 1) {
            throw new IllegalStateException("收款登记未落库: " + receipt.getReceiptNo());
        }
        return receipt;
    }

    /**
     * 方式取值以 {@link ScmFinanceReceiptMethodEnum} 为唯一来源，必须与 {@code ck_finance_receipt_method} 的白名单一致；不用 {@code valueOf}
     * 直抛，是为了给用户一个业务码而不是栈异常。
     *
     * <p>
     * 枚举里没有 {@code BALANCE}，因此这里不需要额外禁止它：余额消费不是实际资金动作， 不该出现在收款方式里（见 {@link ScmFinanceReceiptMethodEnum} 的说明）。
     */
    private static String method(String raw) {
        String value = StringUtils.trimToNull(raw);
        ScmFinanceReceiptMethodEnum candidate = ScmFinanceReceiptMethodEnum.of(value);
        if (candidate == null) {
            throw new ScmBusinessException(FinanceErrorCode.METHOD_INVALID);
        }
        return candidate.name();
    }

    private Map<String, Object> snapshot(FinanceReceiptEntity receipt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receiptNo", receipt.getReceiptNo());
        snapshot.put("customerId", receipt.getCustomerId());
        snapshot.put("customerNameSnapshot", receipt.getCustomerNameSnapshot());
        snapshot.put("amount", receipt.getAmount().toPlainString());
        snapshot.put("method", receipt.getMethod());
        // 时间一律落 ISO 字符串：JSONB 侧的 JsonbObjectMapTypeHandler 用的是未注册
        // JavaTimeModule 的裸 ObjectMapper，直接放 OffsetDateTime 会在写入时抛异常。
        snapshot.put("receivedAt", receipt.getReceivedAt().toString());
        snapshot.put("externalReference", receipt.getExternalReference());
        snapshot.put("remark", receipt.getRemark());
        snapshot.put("entryType", receipt.getEntryType());
        snapshot.put("sourceType", receipt.getSourceType());
        snapshot.put("sourceId", receipt.getSourceId() == null ? null : receipt.getSourceId().toString());
        return snapshot;
    }

    private FinanceReceiptVO vo(FinanceReceiptEntity receipt) {
        FinanceReceiptVO vo = new FinanceReceiptVO();
        vo.setReceiptId(receipt.getId());
        vo.setReceiptNo(receipt.getReceiptNo());
        vo.setCustomerId(receipt.getCustomerId());
        vo.setCustomerName(receipt.getCustomerNameSnapshot());
        vo.setSettlementCustomerId(receipt.getSettlementCustomerId());
        vo.setSettlementCustomerName(receipt.getSettlementCustomerNameSnapshot());
        vo.setAmount(receipt.getAmount());
        vo.setMethod(receipt.getMethod());
        vo.setReceivedAt(receipt.getReceivedAt());
        vo.setExternalReference(receipt.getExternalReference());
        vo.setRemark(receipt.getRemark());
        vo.setEntryType(receipt.getEntryType());
        vo.setReverseOfId(receipt.getReverseOfId());
        vo.setReason(receipt.getReason());
        return vo;
    }
}
