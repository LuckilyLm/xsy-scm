package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 业务退款按原成功资金来源拆分出的分摊记录。
 */
@Data
@TableName("xsy_refund_funding_allocation")
public class RefundFundingAllocationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderRefundId;
    private Long orderId;
    private Long customerId;
    private Long settlementCustomerId;
    private String sourceMethod;
    private Long sourceId;
    private BigDecimal sourcePrincipal;
    private BigDecimal allocatedAmount;
    private String status;
    private Long resultSourceId;
    private String failureCode;
    private String failureMessage;
    private OffsetDateTime sourceSucceededAt;
    private Integer sortOrder;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
