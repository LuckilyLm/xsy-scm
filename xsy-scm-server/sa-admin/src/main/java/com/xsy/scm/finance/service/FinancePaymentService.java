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
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentMethodEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.dao.FinancePaymentSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.dto.FinanceRefundFactDto;
import com.xsy.scm.finance.domain.dto.FinanceSupplierFactDto;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.form.FinancePaymentAddForm;
import com.xsy.scm.finance.domain.form.FinancePaymentReverseForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 付款域服务。
 *
 * <p>
 * 只接受 {@code NORMAL} 付款，且只有两种合法组合： {@code SUPPLIER} + 无来源（供应商付款 / 预付），{@code CUSTOMER} + {@code ORDER_REFUND}
 * （客户退款付款）。客户付款必须对应退款来源；不接受无来源的客户付款。
 *
 * <p>
 * <b>退款付款不冲减应收</b>：Return 已经通过红字应收处理过应收， 本命令只表达「钱真的付出去了」，因此绝不写 {@code finance_write_off}、绝不改任何
 * {@code finance_receivable} 行 —— 否则同一笔退货被冲减两次。
 *
 * <p>
 * <b>不设第二套幂等基建</b>：复用既有 {@code idempotency_record} 与 {@link ScmIdempotencyService}（三段式同一事务）。
 */
@Service
@RequiredArgsConstructor
public class FinancePaymentService {

    private final FinancePaymentDao financePaymentDao;
    private final FinancePaymentSourceDao financePaymentSourceDao;
    private final FinanceCounterpartySourceDao financeCounterpartySourceDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 登记一笔 {@code NORMAL} 付款。
     *
     * <p>
     * <b>整条链必须同事务</b>：幂等 claim、付款事实、操作日志、幂等 complete 要么一起成， 要么一起不成。退款来源撞 {@code uk_finance_payment_source_active}
     * 时同样整笔回滚 —— 留下「已付款但无日志」或「claim 已占但无结果」都是不可接受的半成品。
     *
     * <p>
     * 本命令<b>不获取</b>任何业务表行锁或财务余额锁；并发双付款的仲裁点是来源唯一索引： 后到者在该索引上等前者提交后重新检查谓词，插入返回 0 即按 41139 拒绝。
     *
     * @param idempotencyKey
     *            请求级幂等键；同键同内容重放首次结果，同键异内容按既有语义报冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePaymentVO add(FinancePaymentAddForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(FinanceConstant.PAYMENT_ADD_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            if (ScmFinanceCounterpartyTypeEnum.CUSTOMER.name().equals(counterpartyType(form.getCounterpartyType()))) {
                requireCustomerPaymentVisible(form.getCounterpartyId());
            }
            return idempotencyService.replay(claim, FinancePaymentVO.class);
        }

        FinancePaymentEntity payment = register(form);
        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYMENT, payment.getId(), ScmFinanceOperationTypeEnum.PAY, null,
                null, snapshot(payment));

        FinancePaymentVO result = vo(payment);
        idempotencyService.complete(claim, "FINANCE_PAYMENT", payment.getId(), result);
        return result;
    }

    /**
     * 追加一条 {@code REVERSE} 付款事实。原记录保持不变；反向行不继承退款来源，避免与原付款争用来源唯一键。
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePaymentVO reverse(FinancePaymentReverseForm form, String idempotencyKey) {
        FinancePaymentEntity original = financePaymentDao.selectByIdForUpdate(form.getPaymentId());
        if (original == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
        }
        requireAuthorizedCounterparty(original);
        if (!ScmFinanceReverseEntryTypeEnum.NORMAL.name().equals(original.getEntryType())) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }

        String reason = StringUtils.trimToNull(form.getReason());
        if (reason == null) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_REASON_REQUIRED);
        }

        var claim = idempotencyService.claim(FinanceConstant.PAYMENT_REVERSE_SCOPE + ":" + original.getId(),
                idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, FinancePaymentVO.class);
        }

        BigDecimal effectiveWriteOffAmount = financePaymentDao.selectEffectiveWriteOffAmount(original.getId());
        if (effectiveWriteOffAmount == null || effectiveWriteOffAmount.signum() != 0) {
            throw new ScmBusinessException(FinanceErrorCode.REVERSE_BLOCKED_BY_WRITE_OFF);
        }

        FinancePaymentEntity reversal = newReverseRecord(original, reason);
        if (financePaymentDao.insertReverseOnConflictDoNothing(reversal) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.ALREADY_REVERSED);
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYMENT, original.getId(),
                ScmFinanceOperationTypeEnum.PAYMENT_REVERSE, reason,
                effectiveAmountSnapshot(original, original.getAmount()),
                effectiveAmountSnapshot(reversal, BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE)));

        FinancePaymentVO result = vo(reversal);
        idempotencyService.complete(claim, "FINANCE_PAYMENT", reversal.getId(), result);
        return result;
    }

    /**
     * 付款事实本身（不含幂等三段式）。
     *
     * <p>
     * 两种模式的判定顺序刻意是「先形态、后来源、再范围、最后落库」，并且 <b>CUSTOMER 侧的一切不通过都收敛到同一个 41139</b>：退款不存在、退款不属于我、 状态未完成、金额或对方不符、已付过 ——
     * 全部同一个码。若把「不属于我」换成 范围异常（30005）而「不存在」保持 41139，就等于是给调用者一个「这张退款存在且不是你的」 的探测信号，因此客户范围校验也使用相同的失败响应。
     */
    private FinancePaymentEntity register(FinancePaymentAddForm form) {
        String counterpartyType = counterpartyType(form.getCounterpartyType());
        BigDecimal amount = amount(form.getAmount());

        FinancePaymentEntity payment = new FinancePaymentEntity();
        payment.setCounterpartyType(counterpartyType);
        payment.setAmount(amount);
        payment.setMethod(method(form.getMethod()));
        payment.setPaidAt(form.getPaidAt());
        payment.setEntryType(ScmFinanceReverseEntryTypeEnum.NORMAL.name());
        // REVERSE 专用列在 NORMAL 行上必须为空（ck_finance_payment_entry_pairing）。
        payment.setReverseOfId(null);
        payment.setReason(null);
        payment.setExternalReference(StringUtils.trimToNull(form.getExternalReference()));
        payment.setRemark(StringUtils.trimToNull(form.getRemark()));

        if (ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(counterpartyType)) {
            fillSupplier(payment, form);
        } else {
            fillCustomerRefund(payment, form, amount);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        payment.setPaymentNo(
                ScmDocumentNumbers.format(FinanceConstant.PAYMENT_NO_PREFIX, financePaymentDao.nextPaymentNo()));
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setCreatedBy(operator);
        payment.setUpdatedBy(operator);

        // 0 行 = 撞 uk_finance_payment_source_active。供应商付款的 source_id 为 NULL、
        // 不在该索引内，所以这里不存在「把别的冲突误吞成已付过」的空间。
        if (financePaymentDao.insertNormalOnConflictDoNothing(payment) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        return payment;
    }

    private void requireAuthorizedCounterparty(FinancePaymentEntity payment) {
        if (ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(payment.getCounterpartyType())) {
            return;
        }
        if (!ScmFinanceCounterpartyTypeEnum.CUSTOMER.name().equals(payment.getCounterpartyType())) {
            throw new ScmDataScopeException();
        }
        FinanceCustomerFactDto customer = financeCounterpartySourceDao.selectCustomer(payment.getCounterpartyId());
        if (customer == null || !dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    private FinancePaymentEntity newReverseRecord(FinancePaymentEntity original, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        FinancePaymentEntity reversal = new FinancePaymentEntity();
        reversal.setPaymentNo(
                ScmDocumentNumbers.format(FinanceConstant.PAYMENT_NO_PREFIX, financePaymentDao.nextPaymentNo()));
        reversal.setCounterpartyType(original.getCounterpartyType());
        reversal.setCounterpartyId(original.getCounterpartyId());
        reversal.setCounterpartyNameSnapshot(original.getCounterpartyNameSnapshot());
        reversal.setAmount(original.getAmount());
        reversal.setMethod(original.getMethod());
        reversal.setPaidAt(now);
        reversal.setEntryType(ScmFinanceReverseEntryTypeEnum.REVERSE.name());
        reversal.setReverseOfId(original.getId());
        reversal.setReason(reason);
        reversal.setExternalReference(null);
        reversal.setSourceType(null);
        reversal.setSourceId(null);
        reversal.setRemark(null);
        reversal.setCreatedAt(now);
        reversal.setUpdatedAt(now);
        reversal.setCreatedBy(operator);
        reversal.setUpdatedBy(operator);
        return reversal;
    }

    private static Map<String, Object> effectiveAmountSnapshot(FinancePaymentEntity payment,
            BigDecimal effectiveAmount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentId", payment.getId());
        snapshot.put("paymentNo", payment.getPaymentNo());
        snapshot.put("entryType", payment.getEntryType());
        snapshot.put("reverseOfId", payment.getReverseOfId());
        snapshot.put("effectiveAmount", effectiveAmount.toPlainString());
        snapshot.put("reason", payment.getReason());
        return snapshot;
    }

    /**
     * 模式 A：供应商付款 / 预付。<b>没有任何应付、没有采购单也可以付</b>（预付）， 因此这里不接受也不校验 payableId / purchaseOrderId / purchaseReceiptId。
     * 只判供应商存在性与 deleted；{@code status} 不做前置（停用的供应商也可能要结清历史债务）。
     */
    private void fillSupplier(FinancePaymentEntity payment, FinancePaymentAddForm form) {
        if (form.getSourceType() != null || form.getSourceId() != null) {
            // 供应商付款没有业务来源；带了来源就是模式错误（库层 ck_finance_payment_source_pairing 同向）
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceSupplierFactDto supplier = financeCounterpartySourceDao.selectSupplier(form.getCounterpartyId());
        if (supplier == null) {
            // 供应商侧没有范围判定，因此「不存在」不是敏感信号，用参数错误而不是 41139：
            // 41139 的文案是「退款付款来源不合法」，挂在这里会误导排查。
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        payment.setCounterpartyId(supplier.getSupplierId());
        payment.setCounterpartyNameSnapshot(supplier.getSupplierName());
        payment.setSourceType(null);
        payment.setSourceId(null);
    }

    /**
     * 模式 B：客户退款付款。来源必须是 {@code ORDER_REFUND}，退款必须 {@code COMPLETED}， 金额与对方必须与 {@code order_refund} **逐值一致**。
     */
    private void fillCustomerRefund(FinancePaymentEntity payment, FinancePaymentAddForm form, BigDecimal amount) {
        if (!ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name().equals(StringUtils.trimToNull(form.getSourceType()))
                || form.getSourceId() == null) {
            // 客户付款必须关联已完成退款；无来源的客户付款不符合受支持的业务形态。
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceRefundFactDto refund = financePaymentSourceDao.selectOrderRefund(form.getSourceId());
        if (refund == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceCustomerFactDto customer = financeCounterpartySourceDao.selectCustomer(refund.getCustomerId());
        if (customer == null || !dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            // 越权与「退款指向的客户不存在」同码：见 register() 的说明
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        if (!refund.isCompleted()) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        if (!refund.getCustomerId().equals(form.getCounterpartyId())) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        if (amount.compareTo(refund.getRefundAmount()) != 0) {
            // scale 4 逐值判等，不允许四舍五入到 2 位再比：refund_amount 与付款金额都是 18,4
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }

        payment.setCounterpartyId(refund.getCustomerId());
        // 名称取付款发生时客户主档并冻结，绝不采用前端提交的任何名称；
        // 也不回读订单快照 —— 付款的语义是「真实付款发生时的对方」。
        payment.setCounterpartyNameSnapshot(customer.getCustomerName());
        payment.setSourceType(ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name());
        payment.setSourceId(refund.getRefundId());
    }

    /**
     * CUSTOMER 付款重放仍须按当前客户归属校验可见性，且使用付款来源既有的防枚举错误码。
     */
    private void requireCustomerPaymentVisible(Long customerId) {
        FinanceCustomerFactDto customer = financeCounterpartySourceDao.selectCustomer(customerId);
        if (customer == null || !dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
    }

    private static String counterpartyType(String raw) {
        String value = StringUtils.trimToNull(raw);
        for (ScmFinanceCounterpartyTypeEnum candidate : ScmFinanceCounterpartyTypeEnum.values()) {
            if (candidate.name().equals(value)) {
                return candidate.name();
            }
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    /**
     * 金额形态与正负：与收款同一条契约（严格字符串 + scale 4），库级 {@code CHECK (amount > 0)} 是第二层。
     */
    private static BigDecimal amount(String raw) {
        BigDecimal amount = ScmDecimalStrings.parseScale4Required(raw);
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        return amount;
    }

    /**
     * 方式与收款共用 {@link ScmFinancePaymentMethodEnum}，并由数据库 CHECK 约束。
     */
    private static String method(String raw) {
        String value = StringUtils.trimToNull(raw);
        for (ScmFinancePaymentMethodEnum candidate : ScmFinancePaymentMethodEnum.values()) {
            if (candidate.name().equals(value)) {
                return candidate.name();
            }
        }
        throw new ScmBusinessException(FinanceErrorCode.METHOD_INVALID);
    }

    private Map<String, Object> snapshot(FinancePaymentEntity payment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentNo", payment.getPaymentNo());
        snapshot.put("counterpartyType", payment.getCounterpartyType());
        snapshot.put("counterpartyId", payment.getCounterpartyId());
        snapshot.put("counterpartyNameSnapshot", payment.getCounterpartyNameSnapshot());
        snapshot.put("amount", payment.getAmount().toPlainString());
        snapshot.put("method", payment.getMethod());
        // 时间一律落 ISO 字符串：JSONB 侧的 typeHandler 用的是未注册 JavaTimeModule 的裸 ObjectMapper。
        snapshot.put("paidAt", payment.getPaidAt().toString());
        snapshot.put("externalReference", payment.getExternalReference());
        snapshot.put("sourceType", payment.getSourceType());
        snapshot.put("sourceId", payment.getSourceId());
        snapshot.put("remark", payment.getRemark());
        snapshot.put("entryType", payment.getEntryType());
        return snapshot;
    }

    private FinancePaymentVO vo(FinancePaymentEntity payment) {
        FinancePaymentVO vo = new FinancePaymentVO();
        vo.setPaymentId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setCounterpartyType(payment.getCounterpartyType());
        vo.setCounterpartyId(payment.getCounterpartyId());
        vo.setCounterpartyName(payment.getCounterpartyNameSnapshot());
        vo.setAmount(payment.getAmount());
        vo.setMethod(payment.getMethod());
        vo.setPaidAt(payment.getPaidAt());
        vo.setExternalReference(payment.getExternalReference());
        vo.setSourceType(payment.getSourceType());
        vo.setSourceId(payment.getSourceId());
        vo.setRemark(payment.getRemark());
        vo.setEntryType(payment.getEntryType());
        vo.setReverseOfId(payment.getReverseOfId());
        vo.setReason(payment.getReason());
        return vo;
    }
}
