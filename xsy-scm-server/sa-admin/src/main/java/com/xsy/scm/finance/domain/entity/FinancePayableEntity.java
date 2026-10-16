package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 正常应付由已确认收货生成，红字应付独立新增而不改原单。应付金额、核销及结清状态分别保存事实与派生结果，历史单据只能追加纠错。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_payable")
public class FinancePayableEntity extends FinanceRecord {

    private String payableNo;

    /**
     * {@code ScmFinancePayableSourceTypeEnum}；与 {@link #entryType} 由 {@code ck_finance_payable_source_pairing} 配对约束。
     */
    private String sourceType;

    /**
     * 正常 = {@code xsy_purchase_receipt.id}；手工红字<b>必须为 {@code null}</b>。
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
     * 单头金额 = 明细之和，恒 &gt; 0；含合法容差内的超收。
     */
    private BigDecimal amount;

    /**
     * 业务事件时点 = {@code xsy_purchase_receipt.confirmed_at}。{@code DIRECT} 与 {@code WAREHOUSE_CONFIRM} 同口径 —— putaway
     * 是内部库存作业，不决定应付时点。
     */
    private OffsetDateTime eventAt;

    /**
     * 红字原因，红字必填非空。
     */
    private String reason;
}
