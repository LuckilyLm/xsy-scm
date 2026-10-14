package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.domain.form.FinanceCodCollectionForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 货到付款收款确认（PAY-03，真实 PostgreSQL）。
 *
 * <p>
 * 钉住一条边界：<b>签收不是已收款</b>。签收只生成应收，钱到没到要由一笔真实收款回答，因此
 * 「订单约定为 COD <b>且</b>已有正常应收」是这条命令的前置 —— 拒收与异常签收不生成应收，自然也就收不到钱。
 *
 * <p>
 * <b>为什么必须跑真实库</b>：防重靠收款来源唯一索引 {@code uk_finance_receipt_source_active}，
 * 金额上限靠核销命令自己的「不得超过可核销余额」判定，回滚靠同事务 —— 三件都在库上。
 *
 * <p>
 * <b>夹具为什么直接建应收</b>：签收链路（分拣 → 线路 → 发车 → 签收）由 {@code ScmFinanceReceivablePgIT} 覆盖；
 * 本类要测的是「应收已经成立之后，COD 收款怎么落」，因此把正常应收作为输入固定下来 ——
 * 而「有没有应收」正是本命令判定能否收款的那一条前置。
 */
@DisplayName("货到付款收款确认（PAY-03，PG IT）")
class FinanceCodCollectionPgIT extends ScmW5PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 下单 2.0000 × 客户价 1.2000 = 应收 2.4000。 */
    private static final BigDecimal RECEIVABLE_AMOUNT = new BigDecimal("2.4000");

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Test
    @DisplayName("已签收的 COD 订单：登记实收并核销应收，收款来源指向订单")
    void collectedCodIsRecordedWithItsOrderAsSourceAndWrittenOff() {
        CodOrder order = signedCodOrder("ok");

        FinanceReceiptVO receipt = collect(order, RECEIVABLE_AMOUNT.toPlainString(), "CASH", "ok");

        assertThat(jdbc.queryForObject("SELECT source_type FROM xsy_finance_receipt WHERE id = ?", String.class,
                receipt.getReceiptId())).isEqualTo("ORDER_COD_COLLECTION");
        assertThat(jdbc.queryForObject("SELECT source_id FROM xsy_finance_receipt WHERE id = ?", Long.class,
                receipt.getReceiptId())).as("来源指向订单，这笔钱才答得出是哪张单的").isEqualTo(order.orderId());
        assertThat(receipt.getMethod()).as("方式是真实资金形式，不是 COD 本身").isEqualTo("CASH");
        assertThat(receipt.getAmount()).isEqualByComparingTo(RECEIVABLE_AMOUNT);
        assertThat(receipt.getSettlementCustomerId()).isEqualTo(order.customerId());

        assertThat(writeOffCount(receipt.getReceiptId(), order.receivableId())).isEqualTo(1);
        assertThat(openAmount(order.receivableId())).as("收清后应收无未结余额").isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("少收按实收登记，应收剩余仍未结")
    void partialCollectionLeavesTheRemainderOpen() {
        CodOrder order = signedCodOrder("partial");

        FinanceReceiptVO receipt = collect(order, "1.0000", "BANK_TRANSFER", "partial");

        assertThat(receipt.getAmount()).isEqualByComparingTo("1.0000");
        assertThat(writeOffCount(receipt.getReceiptId(), order.receivableId())).isEqualTo(1);
        assertThat(openAmount(order.receivableId())).as("剩余 1.4000 仍未结").isEqualByComparingTo("1.4000");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("多收被拒，且整笔回滚：不留下一张收不到对应应收的收款单")
    void overCollectionIsRejectedAndRollsBackTheReceipt() {
        // 「整笔回滚」只在真提交时暴露：测试外层事务会把失败行留着等回滚，断言就没有意义。
        CodOrder order = signedCodOrder("over");

        assertThatThrownBy(() -> collect(order, "3.0000", "CASH", "over"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED));

        assertThat(receiptCount(order.orderId())).as("收款与核销同事务，核销不过就不该留下收款").isZero();
        assertThat(openAmount(order.receivableId())).isEqualByComparingTo(RECEIVABLE_AMOUNT);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("重复登记被唯一索引挡下；同一幂等键重放返回首次结果")
    void duplicateCollectionIsRejectedByTheSourceUniqueIndex() {
        // 唯一索引冲突会让 PostgreSQL 把当前事务标记为已中止，因此这条路径必须在真事务里走：
        // 生产上 collectCod 自己持有事务并整体回滚，测试外层事务会把中止状态传染给后续断言。
        CodOrder order = signedCodOrder("dup");
        String key = prefix + ":cod-dup";
        // 幂等指纹包含请求内容，因此重放必须复用同一个表单对象，否则会被判成「同键异内容」
        FinanceCodCollectionForm request = form(order, "2.4000", "CASH");
        FinanceReceiptVO first = financeReceiptService.collectCod(request, key);

        FinanceReceiptVO replayed = financeReceiptService.collectCod(request, key);
        assertThat(replayed.getReceiptId()).isEqualTo(first.getReceiptId());

        // 换一个幂等键再收一次：请求级幂等放行，但来源唯一索引不允许第二笔事实
        assertThatThrownBy(() -> financeReceiptService.collectCod(request, prefix + ":cod-dup-again"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(FinanceErrorCode.COD_COLLECTION_ALREADY_REGISTERED));

        assertThat(receiptCount(order.orderId())).isEqualTo(1);
        assertThat(writeOffCount(first.getReceiptId(), order.receivableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("未签收、非 COD 与不存在的订单：都在写任何资金事实之前被拒")
    void unsignedNonCodAndMissingOrdersAreRejectedBeforeAnyFact() {
        // 约定为 COD 但还没有应收（未签收 / 拒收 / 异常签收）：没有可收的钱
        CodOrder unsigned = codOrder("unsigned", false);
        assertThatThrownBy(() -> collect(unsigned, "2.4000", "CASH", "unsigned"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_APPLICABLE));
        assertThat(receiptCount(unsigned.orderId())).isZero();

        // 有应收但不是 COD 约定：账期的钱走应收核销，不走 COD 收款
        CodOrder credit = codOrder("credit", true);
        jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'CREDIT' WHERE id = ?", credit.orderId());
        evictMybatisCache();
        assertThatThrownBy(() -> collect(credit, "2.4000", "CASH", "credit"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_APPLICABLE));
        assertThat(receiptCount(credit.orderId())).isZero();

        FinanceCodCollectionForm missing = new FinanceCodCollectionForm();
        missing.setOrderId(Long.MAX_VALUE);
        missing.setAmount("2.4000");
        missing.setMethod("CASH");
        missing.setReceivedAt(OffsetDateTime.now());
        assertThatThrownBy(() -> financeReceiptService.collectCod(missing, prefix + ":cod-missing"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_FOUND));
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record CodOrder(Long orderId, Long customerId, Long receivableId) {
    }

    /** 一张约定为 COD、且已经签收（有正常应收）的订单。 */
    private CodOrder signedCodOrder(String tag) {
        return codOrder(tag, true);
    }

    private CodOrder codOrder(String tag, boolean signed) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("cod-" + tag);
        Long orderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
        jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'COD' WHERE id = ?", orderId);
        evictMybatisCache();
        Long receivableId = signed ? normalReceivable(orderId, RECEIVABLE_AMOUNT.toPlainString()) : null;
        return new CodOrder(orderId, customerId, receivableId);
    }

    private FinanceReceiptVO collect(CodOrder order, String amount, String method, String key) {
        return financeReceiptService.collectCod(form(order, amount, method), prefix + ":cod-" + key);
    }

    private FinanceCodCollectionForm form(CodOrder order, String amount, String method) {
        FinanceCodCollectionForm form = new FinanceCodCollectionForm();
        form.setOrderId(order.orderId());
        form.setAmount(amount);
        form.setMethod(method);
        form.setReceivedAt(OffsetDateTime.now());
        form.setExternalReference("COD-" + UUID.randomUUID().toString().substring(0, 8));
        return form;
    }

    private Long normalReceivable(Long orderId, String amount) {
        OffsetDateTime eventAt = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        return jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', CAST(? AS numeric), ?
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-COD-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
    }

    private Integer receiptCount(Long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_receipt"
                + " WHERE source_type = 'ORDER_COD_COLLECTION' AND source_id = ? AND deleted = FALSE",
                Integer.class, orderId);
    }

    private Integer writeOffCount(Long receiptId, Long receivableId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT'"
                + " AND source_id = ? AND target_type = 'RECEIVABLE' AND target_id = ? AND entry_type = 'NORMAL'"
                + " AND deleted = FALSE", Integer.class, receiptId, receivableId);
    }

    /** 应收未结额 = 应收金额 − 正常核销 + 反向核销。 */
    private BigDecimal openAmount(Long receivableId) {
        return jdbc.queryForObject("""
                SELECT (SELECT amount FROM xsy_finance_receivable WHERE id = ?)
                     - COALESCE((SELECT SUM(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)
                         FROM xsy_finance_write_off
                         WHERE target_type = 'RECEIVABLE' AND target_id = ? AND deleted = FALSE), 0)
                """, BigDecimal.class, receivableId, receivableId);
    }
}
