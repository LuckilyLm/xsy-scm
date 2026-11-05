package com.xsy.scm.balance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 客户余额视图。
 */
@Data
public class CustomerBalanceVO {

    /** 实际业务客户（查询入口传进来的那个）。 */
    private Long customerId;

    private String customerName;

    /** 余额账户所有者（结算主体）；普通客户与 customerId 相同。 */
    private Long settlementCustomerId;

    private String settlementCustomerName;

    /**
     * 可用余额 = SUM(CREDIT) − SUM(DEBIT)；没有账户时是 0。
     *
     * <p>
     * 每次现算，账户表不存 balance 字段：存一份就要维护，漏更新会让余额与流水永久不一致。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableBalance;

    /** 账户 id；没有账户时为 {@code null}（此时余额恒为 0）。 */
    private Long accountId;
}
