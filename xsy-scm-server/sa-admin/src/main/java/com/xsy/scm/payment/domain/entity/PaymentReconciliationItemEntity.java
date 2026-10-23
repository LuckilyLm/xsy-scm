package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 逐条对账差异。
 *
 * <p>
 * 只有追加，没有 {@code deleted} / {@code version}：对账结论一旦生成就不能改写， 否则「上一次对账说平了」就无据可查。
 */
@Data
@TableName("xsy_payment_reconciliation_item")
public class PaymentReconciliationItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reconciliationId;

    /**
     * {@code LOCAL_MISSING} / {@code PROVIDER_MISSING} / {@code AMOUNT_MISMATCH} / {@code STATUS_MISMATCH}。
     */
    private String category;

    private String providerTransactionNo;

    private Long transactionId;

    private BigDecimal localAmount;

    private BigDecimal providerAmount;

    private String localStatus;

    private String providerStatus;

    private OffsetDateTime createdAt;

    private String createdBy;
}
