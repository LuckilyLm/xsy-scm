package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceConstant;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceErrorCode;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePaymentMethodEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceCounterpartySourceDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePaymentDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePaymentSourceDao;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceCustomerFactDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceRefundFactDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceSupplierFactDto;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePaymentEntity;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinancePaymentAddForm;
import net.lab1024.sa.admin.module.scm.finance.domain.vo.FinancePaymentVO;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 付款域服务。
 *
 * <p><b>F1-3B 交付的是 {@code NORMAL} 付款登记一条命令</b>，且只有两种合法组合：
 * {@code SUPPLIER} + 无来源（供应商付款 / 预付），{@code CUSTOMER} + {@code ORDER_REFUND}
 * （客户退款付款）。本期不支持 {@code CUSTOMER} + 无来源 —— 客户提现 / 余额退款 / 营销返现
 * 都没有需求基线（P5 范畴）。
 *
 * <p><b>退款付款不冲减应收</b>（第二批 Q27）：Return 已经通过红字应收处理过应收，
 * 本命令只表达「钱真的付出去了」，因此绝不写 {@code finance_write_off}、绝不改任何
 * {@code finance_receivable} 行 —— 否则同一笔退货被冲减两次。
 *
 * <p><b>反向付款在此实现</b>（F1-3C，{@code scm:finance:payment:reverse}，D-3）：反向行的
 * {@code source_type / source_id} 必须为 NULL（{@code ck_finance_payment_reverse_no_source}），
 * 否则会与原行抢 {@code uk_finance_payment_source_active}，把登错的退款付款变成永远纠不掉。
 * 本方法不复用给反向用 —— 反向要锁原行（{@code LOCK_RANK_PAYMENT}）并校验「已用额 = 0」。
 *
 * <p><b>不设第二套幂等基建</b>：复用既有 {@code idempotency_record} 与
 * {@link OrderIdempotencyService}（三段式同一事务，第二批 Q26）。
 */
@Service
@RequiredArgsConstructor
public class FinancePaymentService {

    private static final String REFUND_STATUS_COMPLETED = "COMPLETED";

    private final FinancePaymentDao payments;
    private final FinancePaymentSourceDao paymentSource;
    private final FinanceCounterpartySourceDao counterpartySource;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService scopeService;
    private final OrderIdempotencyService idempotency;

    /**
     * 登记一笔 {@code NORMAL} 付款。
     *
     * <p><b>整条链必须同事务</b>：幂等 claim、付款事实、操作日志、幂等 complete 要么一起成，
     * 要么一起不成。退款来源撞 {@code uk_finance_payment_source_active} 时同样整笔回滚 ——
     * 留下「已付款但无日志」或「claim 已占但无结果」都是不可接受的半成品。
     *
     * <p>本命令<b>不获取</b>任何业务表行锁，也不按 rank 锁财务行（§14 的财务行锁只属于
     * 核销 / 反向 / 红字这类要读余额的命令）；并发双付款的仲裁点就是来源唯一索引本身：
     * 后到者在该索引上等前者提交后重新检查谓词，插入返回 0 即按 41139 拒绝。
     *
     * @param idempotencyKey 请求级幂等键；同键同内容重放首次结果，同键异内容按既有语义报冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public FinancePaymentVO add(FinancePaymentAddForm form, String idempotencyKey) {
        var claim = idempotency.claim(FinanceConstant.PAYMENT_ADD_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotency.replay(claim, FinancePaymentVO.class);
        }

        FinancePaymentEntity payment = register(form);
        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYMENT, payment.getId(),
                ScmFinanceOperationTypeEnum.PAY, null, null, snapshot(payment));

        FinancePaymentVO result = vo(payment);
        idempotency.complete(claim, "FINANCE_PAYMENT", payment.getId(), result);
        return result;
    }

    /**
     * 付款事实本身（不含幂等三段式）。
     *
     * <p>两种模式的判定顺序刻意是「先形态、后来源、再范围、最后落库」，并且
     * <b>CUSTOMER 侧的一切不通过都收敛到同一个 41139</b>：退款不存在、退款不属于我、
     * 状态未完成、金额或对方不符、已付过 —— 全部同一个码。若把「不属于我」换成
     * 范围异常（30005）而「不存在」保持 41139，就等于是给调用者一个「这张退款存在且不是你的」
     * 的探测信号，正是 F1-3A 在客户维度上刻意避免的那件事。
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
        payment.setExternalReference(trimToNull(form.getExternalReference()));
        payment.setRemark(trimToNull(form.getRemark()));

        if (ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(counterpartyType)) {
            fillSupplier(payment, form);
        } else {
            fillCustomerRefund(payment, form, amount);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        payment.setPaymentNo(ScmDocumentNumbers.format(
                FinanceConstant.PAYMENT_NO_PREFIX, payments.nextPaymentNo()));
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setCreatedBy(operator);
        payment.setUpdatedBy(operator);

        // 0 行 = 撞 uk_finance_payment_source_active。供应商付款的 source_id 为 NULL、
        // 不在该索引内，所以这里不存在「把别的冲突误吞成已付过」的空间。
        if (payments.insertNormalOnConflictDoNothing(payment) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        return payment;
    }

    /**
     * 模式 A：供应商付款 / 预付。<b>没有任何应付、没有采购单也可以付</b>（Q16 预付），
     * 因此这里不接受也不校验 payableId / purchaseOrderId / purchaseReceiptId。
     * 只判供应商存在性与 deleted；{@code status} 不做前置（停用的供应商也可能要结清历史债务）。
     */
    private void fillSupplier(FinancePaymentEntity payment, FinancePaymentAddForm form) {
        if (form.getSourceType() != null || form.getSourceId() != null) {
            // 供应商付款没有业务来源；带了来源就是模式错误（库层 ck_finance_payment_source_pairing 同向）
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceSupplierFactDto supplier = counterpartySource.selectSupplier(form.getCounterpartyId());
        if (supplier == null) {
            // 供应商侧没有范围判定（D-5），因此「不存在」不是敏感信号，用参数错误而不是 41139：
            // 41139 的文案是「退款付款来源不合法」，挂在这里会误导排查。
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        payment.setCounterpartyId(supplier.getSupplierId());
        payment.setCounterpartyNameSnapshot(supplier.getSupplierName());
        payment.setSourceType(null);
        payment.setSourceId(null);
    }

    /**
     * 模式 B：客户退款付款。来源必须是 {@code ORDER_REFUND}，退款必须 {@code COMPLETED}，
     * 金额与对方必须与 {@code order_refund} **逐值一致**（Q19 / Q27）。
     */
    private void fillCustomerRefund(FinancePaymentEntity payment, FinancePaymentAddForm form,
                                    BigDecimal amount) {
        if (!ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name().equals(trimToNull(form.getSourceType()))
                || form.getSourceId() == null) {
            // 本期唯一的客户侧付款就是退款付款；无来源的「客户付款」没有需求基线（属 P5）
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceRefundFactDto refund = paymentSource.selectOrderRefund(form.getSourceId());
        if (refund == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        FinanceCustomerFactDto customer = counterpartySource.selectCustomer(refund.getCustomerId());
        if (customer == null
                || !scopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            // 越权与「退款指向的客户不存在」同码：见 register() 的说明
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_SOURCE_INVALID);
        }
        if (!REFUND_STATUS_COMPLETED.equals(refund.getStatus())) {
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
        // 也不回读订单快照 —— 付款的语义是「真实付款发生时的对方」（设计稿 §6）。
        payment.setCounterpartyNameSnapshot(customer.getCustomerName());
        payment.setSourceType(ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name());
        payment.setSourceId(refund.getRefundId());
    }

    private static String counterpartyType(String raw) {
        String value = trimToNull(raw);
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
     * 方式与收款共用 {@link ScmFinancePaymentMethodEnum}（Q21：Java enum + DB CHECK，不入字典）。
     */
    private static String method(String raw) {
        String value = trimToNull(raw);
        for (ScmFinancePaymentMethodEnum candidate : ScmFinancePaymentMethodEnum.values()) {
            if (candidate.name().equals(value)) {
                return candidate.name();
            }
        }
        throw new ScmBusinessException(FinanceErrorCode.METHOD_INVALID);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
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
        return vo;
    }
}
