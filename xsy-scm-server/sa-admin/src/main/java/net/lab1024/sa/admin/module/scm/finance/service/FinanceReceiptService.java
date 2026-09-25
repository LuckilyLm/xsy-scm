package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceConstant;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceErrorCode;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePaymentMethodEnum;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceiptDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceiptSourceDao;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceCustomerFactDto;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceiptEntity;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinanceReceiptAddForm;
import net.lab1024.sa.admin.module.scm.finance.domain.vo.FinanceReceiptVO;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收款域服务。
 *
 * <p><b>F1-3A 交付的是 {@code NORMAL} 收款登记一条命令</b>。
 * <b>F1-3C 在此实现</b>反向收款（{@code scm:finance:receipt:reverse}，D-3）：登错的收款只能新增一条
 * {@code entry_type='REVERSE'} 的反向事实纠正，不提供修改 / 作废 / 软删；反向前该单「已用额」必须为 0
 * （否则已用 &gt; 有效额会造出无法解释的负待核销余额），前置检查必须建立在
 * {@code FinanceConstant.LOCK_RANK_RECEIPT} 的行锁之上，先查再判在这里不成立。
 *
 * <p><b>收款登记与核销是两件事</b>（第二批 Q17 / Q18）：登记一笔钱只产生
 * 「一笔待核销款」这一事实，绝不自动创建 {@code finance_write_off}、绝不改
 * {@code finance_receivable} / {@code sales_order} / {@code customer.credit_limit}。
 * 允许预收（{@code Q16}）：没有对应收款照收不误。核销命令属 F1-4。
 *
 * <p><b>不设第二套幂等基建</b>：复用既有 {@code idempotency_record} 与
 * {@link OrderIdempotencyService}（三段式 {@code claim → 写 → complete} 同一事务），
 * 与 delivery / inventory / sorting 同一形态（第二批 Q26）。
 * {@code external_reference} 只是资金凭据文本，不参与任何一层防重。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceiptService {

    private final FinanceReceiptDao receipts;
    private final FinanceReceiptSourceDao receiptSource;
    private final FinanceOperationLogRecorder operationLogs;
    private final ScmDataScopeService scopeService;
    private final OrderIdempotencyService idempotency;

    /**
     * 登记一笔 {@code NORMAL} 收款。
     *
     * <p><b>整条链必须同事务</b>：幂等 claim、收款事实、操作日志、幂等 complete 要么一起成，
     * 要么一起不成。留下「收款已落库、日志没落」或「claim 已占、结果为空」都是不可接受的半成品
     * —— 前者是证据链断裂，后者会让同一 key 的后续重放读到空结果。
     *
     * <p>范围判定在读到客户事实之后立刻做，且拒绝方式与「客户不存在」共用
     * {@link ScmDataScopeException}（对外 30005）：能分辨「存在但无权」就等于把主键探测
     * 变成了一个可用信号（P0 裁决、第二批 Q23 / D-5）。
     *
     * @param idempotencyKey 请求级幂等键；同键同内容重放首次结果，同键异内容按既有语义报冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceReceiptVO add(FinanceReceiptAddForm form, String idempotencyKey) {
        var claim = idempotency.claim(FinanceConstant.RECEIPT_ADD_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotency.replay(claim, FinanceReceiptVO.class);
        }

        FinanceReceiptEntity receipt = register(form);
        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIPT, receipt.getId(),
                ScmFinanceOperationTypeEnum.RECEIVE, null, null, snapshot(receipt));

        FinanceReceiptVO result = vo(receipt);
        idempotency.complete(claim, "FINANCE_RECEIPT", receipt.getId(), result);
        return result;
    }

    /**
     * 收款事实本身（不含幂等三段式）。F1-3C 的反向收款不复用它 ——
     * 反向要带 {@code reverse_of_id} 与原行「已用额 = 0」前置，是另一条命令。
     */
    private FinanceReceiptEntity register(FinanceReceiptAddForm form) {
        FinanceCustomerFactDto customer = receiptSource.selectCustomer(form.getCustomerId());
        if (customer == null
                || !scopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }

        BigDecimal amount = ScmDecimalStrings.parseScale4Required(form.getAmount());
        if (amount.signum() <= 0) {
            // 0 元收款不是财务事实：库级 CHECK (amount > 0) 是第二层，这里先给出可解释的 40000。
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();

        FinanceReceiptEntity receipt = new FinanceReceiptEntity();
        receipt.setReceiptNo(ScmDocumentNumbers.format(
                FinanceConstant.RECEIPT_NO_PREFIX, receipts.nextReceiptNo()));
        receipt.setCustomerId(customer.getCustomerId());
        receipt.setCustomerNameSnapshot(customer.getCustomerName());
        receipt.setAmount(amount);
        receipt.setMethod(method(form.getMethod()));
        receipt.setReceivedAt(form.getReceivedAt());
        receipt.setEntryType(ScmFinanceEntryTypeEnum.NORMAL.name());
        // REVERSE 专用列在 NORMAL 行上必须为空（ck_finance_receipt_entry_pairing）。
        receipt.setReverseOfId(null);
        receipt.setReason(null);
        receipt.setExternalReference(trimToNull(form.getExternalReference()));
        receipt.setRemark(trimToNull(form.getRemark()));
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);
        receipt.setCreatedBy(operator);
        receipt.setUpdatedBy(operator);
        if (receipts.insert(receipt) != 1) {
            throw new IllegalStateException("收款登记未落库: " + receipt.getReceiptNo());
        }
        return receipt;
    }

    /**
     * 方式取值以 {@link ScmFinancePaymentMethodEnum} 为唯一来源，与 V65 的
     * {@code ck_finance_receipt_method} 三值逐字一致（ScmFinanceSchemaPgIT 双向钉死）；
     * 不用 {@code valueOf} 直抛，是为了给用户一个业务码而不是栈异常。
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
        return snapshot;
    }

    private FinanceReceiptVO vo(FinanceReceiptEntity receipt) {
        FinanceReceiptVO vo = new FinanceReceiptVO();
        vo.setReceiptId(receipt.getId());
        vo.setReceiptNo(receipt.getReceiptNo());
        vo.setCustomerId(receipt.getCustomerId());
        vo.setCustomerName(receipt.getCustomerNameSnapshot());
        vo.setAmount(receipt.getAmount());
        vo.setMethod(receipt.getMethod());
        vo.setReceivedAt(receipt.getReceivedAt());
        vo.setExternalReference(receipt.getExternalReference());
        vo.setRemark(receipt.getRemark());
        vo.setEntryType(receipt.getEntryType());
        return vo;
    }
}
