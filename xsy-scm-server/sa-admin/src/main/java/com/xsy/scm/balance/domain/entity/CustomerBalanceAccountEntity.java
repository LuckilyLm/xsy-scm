package com.xsy.scm.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 客户余额账户（钱包）。
 *
 * <p>
 * <b>刻意没有余额字段</b>：余额恒等于 {@code SUM(CREDIT) − SUM(DEBIT)}，由流水推导。
 * 存一份「当前余额」就要维护它，而任何一次漏更新都会让余额与流水永久漂移，
 * 且没人说得清该信哪个。这张表的作用只有一个：给扣款提供一个**稳定的并发锁锚点**
 * （{@code SELECT ... FOR UPDATE}）。
 *
 * <p>
 * 钱包属于**结算主体**：集团下属单位共用集团钱包。
 */
@Data
@TableName("customer_balance_account")
public class CustomerBalanceAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 钱包所有者（结算主体）。 */
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
