package com.xsy.scm.balance.domain.entity;

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
 * 充值单只记录充值人、钱包和申请金额。支付是否成功由支付交易记录维护，不能再建一套充值状态。
 */
@Data
@TableName("xsy_customer_balance_recharge")
public class CustomerBalanceRechargeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String rechargeNo;

    /**
     * 钱包所有者（结算主体）。
     */
    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /**
     * 实际发起充值的业务客户。
     */
    private Long customerId;

    private String customerNameSnapshot;

    private BigDecimal amount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
