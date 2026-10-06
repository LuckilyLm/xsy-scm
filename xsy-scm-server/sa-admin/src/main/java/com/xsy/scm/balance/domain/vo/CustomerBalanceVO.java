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

    /**
     * 可用余额 = SUM(CREDIT) − SUM(DEBIT)；没有账户时是 0。
     *
     * <p>
     * <b>每次现算</b>，账户表上没有可缓存的 balance 字段：存一份就要维护它， 任何一次漏更新都会让余额与流水永久漂移，且没人说得清该信哪个。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableBalance;

    /** 账户 id；没有账户时为 {@code null}（此时 balance 恒为 0）。 */
    private Long accountId;
}
