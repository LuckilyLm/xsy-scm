package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 付款：一笔钱一单，可无对应应付（预付）；退款付款带 {@code ORDER_REFUND} 来源（Q19）。
 *
 * <p><b>{@code COMPLETED} 的退款不等于资金已付出</b>（Q19）：{@code order_refund} 是订单域的
 * 业务事实，真实退付由本表表达。一张退款最多一笔正式付款、金额必须等于
 * {@code order_refund.refund_amount}，由 {@code uk_finance_payment_source_active} 在库级兜底。
 *
 * <p><b>付款不冲减应收</b>（Q27）：Return 负责红冲，Refund Payment 只负责真实资金退付，
 * 两者互不替代，避免双重冲减。
 *
 * <p><b>反向行的来源必须为 {@code null}</b>（D-3，{@code ck_finance_payment_reverse_no_source}）：
 * {@code uk_finance_payment_source_active} 的谓词是 {@code source_id IS NOT NULL}，
 * 若反向行沿用原行的 {@code ORDER_REFUND} 来源，它会与原行抢同一个唯一键 ——
 * 结果是「登错一笔退款付款后再也反向不掉」，纠错路径被自己的防重索引锁死。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_payment")
public class FinancePaymentEntity extends FinanceRecord {

    private String paymentNo;

    /**
     * {@code ScmFinanceCounterpartyTypeEnum}：付给供应商（应付 / 预付）或客户（退款）。
     */
    private String counterpartyType;

    /**
     * 对方主键。{@code SUPPLIER} 侧本期不收窄范围（P0 裁决 7：供应商主档按采购团队共享读，
     * 无 supplier 维度）；{@code CUSTOMER} 侧按 {@code customer.seller_id} 收窄（D-5）。
     */
    private Long counterpartyId;

    private String counterpartyNameSnapshot;

    /**
     * 恒 &gt; 0；退款付款必须等于 {@code order_refund.refund_amount}（Q19）。反向行金额同样为正。
     */
    private BigDecimal amount;

    /**
     * {@code ScmFinancePaymentMethodEnum}，与收款共用同一套三值枚举（Q21）。
     */
    private String method;

    private OffsetDateTime paidAt;

    /**
     * {@code ScmFinanceReverseEntryTypeEnum} 的 {@code NORMAL / REVERSE}（D-3）。
     */
    private String entryType;

    /**
     * 反向行必填，指向被冲的 {@code NORMAL} 付款；正常行必须为 {@code null}。
     * 一条 {@code NORMAL} 最多一条 {@code REVERSE}（{@code uk_finance_payment_single_reverse}）。
     */
    private Long reverseOfId;

    /**
     * 反向原因，反向行必填非空。
     */
    private String reason;

    private String externalReference;

    /**
     * {@code ScmFinancePaymentSourceTypeEnum}；本期只有 {@code ORDER_REFUND}，其余情况为 {@code null}。
     * 与 {@link #sourceId} 要么都空要么都齐（{@code ck_finance_payment_source_pairing}）。
     */
    private String sourceType;

    /**
     * {@code order_refund.id}；反向行必须为 {@code null}。
     */
    private Long sourceId;

    private String remark;
}
