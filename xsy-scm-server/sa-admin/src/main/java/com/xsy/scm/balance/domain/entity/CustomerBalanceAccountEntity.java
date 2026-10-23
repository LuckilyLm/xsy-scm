package com.xsy.scm.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 客户钱包按结算主体管理。余额由收入与支出流水计算，不另存一份可修改余额；账户记录用作并发扣款的加锁对象。
 */
@Data
@TableName("xsy_customer_balance_account")
public class CustomerBalanceAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 钱包所有者（结算主体）。
     */
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
