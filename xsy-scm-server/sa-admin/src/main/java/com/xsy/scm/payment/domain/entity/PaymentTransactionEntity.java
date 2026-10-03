package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 交易事实：渠道那一次流水。
 *
 * <p>
 * {@code amount}（本地应付）与 {@code providerAmount}（渠道回报）**分开两列**：
 * 渠道回报不覆盖本地金额，不一致时由对账发现，而不是静默改账。
 *
 * <p>
 * 表上有 append-only CHECK，因此这个实体不提供删除语义。
 */
@Data
@TableName("payment_transaction")
public class PaymentTransactionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String transactionNo;

    private Long intentId;

    private String provider;

    /** 渠道交易号：回调匹配与对账的主键。 */
    private String providerTransactionNo;

    private BigDecimal amount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal providerAmount;

    private String status;

    private OffsetDateTime paidAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureMessage;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
