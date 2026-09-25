package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 应付单头：一张已确认收货单一条正常应付（Q9 / Q10）。
 *
 * <p><b>红字应付是另一张 {@code entry_type = 'RED'} 的单</b>（Q13），与应收红字同形，
 * 不是原单上的一列调整。它的来源是 {@code MANUAL} 且 {@code sourceId} 为 {@code null} ——
 * 手工事实没有外部业务行，因此落在 {@code uk_finance_payable_source_active} 的
 * {@code source_id IS NOT NULL} 谓词之外，防重由「可冲上限 + 请求级幂等键」承担（设计稿 §8.3）。
 *
 * <p><b>没有 purchaser_id / warehouse_id</b>：范围归属读时 join
 * {@code purchase_receipt → purchase_order → purchaser_id} 取活值（Q23 指定路径）；应付不按仓收窄。
 *
 * <p><b>没有状态列与 due_date</b>（Q15 / Q17）：结清读时派生，账期属 Finance R2。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_payable")
public class FinancePayableEntity extends FinanceRecord {

    private String payableNo;

    /**
     * {@code ScmFinancePayableSourceTypeEnum}；与 {@link #entryType} 由
     * {@code ck_finance_payable_source_pairing} 配对约束。
     */
    private String sourceType;

    /**
     * 正常 = {@code purchase_receipt.id}；手工红字**必须为 {@code null}**。
     */
    private Long sourceId;

    /**
     * 采购订单 id；红字也指向原采购单。
     */
    private Long purchaseOrderId;

    private Long supplierId;

    private String supplierNameSnapshot;

    /**
     * {@code ScmFinanceEntryTypeEnum} 的 {@code NORMAL / RED}。
     */
    private String entryType;

    /**
     * 红字必填，指向被冲的原应付；正常应付必须为 {@code null}。
     */
    private Long originalPayableId;

    /**
     * 单头金额 = 明细之和，恒 &gt; 0；含合法容差内的超收（Q11）。
     */
    private BigDecimal amount;

    /**
     * 业务事件时点 = {@code purchase_receipt.confirmed_at}。{@code DIRECT} 与
     * {@code WAREHOUSE_CONFIRM} 同口径 —— putaway 是内部库存作业，不决定应付时点（Q9）。
     */
    private OffsetDateTime eventAt;

    /**
     * 红字原因，红字必填非空（Q13）。
     */
    private String reason;
}
