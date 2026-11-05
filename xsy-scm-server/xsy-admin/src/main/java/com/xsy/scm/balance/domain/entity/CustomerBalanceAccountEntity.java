package com.xsy.scm.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 客户余额账户按结算主体管理。余额由流水计算，不另存可修改的余额；账户行用作并发扣款的加锁对象。
 */
@Data
@TableName("xsy_customer_balance_account")
public class CustomerBalanceAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 余额账户所有者（结算主体）。 */
    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
