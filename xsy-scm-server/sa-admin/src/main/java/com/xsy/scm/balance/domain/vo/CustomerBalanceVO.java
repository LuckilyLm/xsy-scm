package com.xsy.scm.balance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

/** 客户余额视图。 */
@Data
public class CustomerBalanceVO {

    /** 实际业务客户（查询入口传进来的那个）。 */
    private Long customerId;

    private String customerName;

    /** 钱包所有者（结算主体）；普通客户与 customerId 相同。 */
    private Long settlementCustomerId;

    private String settlementCustomerName;

    /** 钱包余额 = SUM(CREDIT) − SUM(DEBIT)；没有账户时是 0。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal balance;

    /** 账户 id；没有账户时为 {@code null}（此时 balance 恒为 0）。 */
    private Long accountId;
}
