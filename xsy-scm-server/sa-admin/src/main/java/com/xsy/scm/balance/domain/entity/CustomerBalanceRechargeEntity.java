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
 * 充值请求事实（ADM-12 3-12b）。
 *
 * <p>
 * <b>只有业务身份，没有支付状态机</b>：{@code CREATED / PAYING / SUCCESS / FAILED} 那些状态
 * 已经由 {@code payment_intent} 与 {@code payment_transaction} 表达。再造一套就会出现
 * 「两个地方都说自己知道充值成没成功」，而它们迟早会不一致。
 *
 * <p>
 * 它回答的问题只有一个：**谁准备往哪个钱包充多少钱、业务编号是什么**。
 */
@Data
@TableName("customer_balance_recharge")
public class CustomerBalanceRechargeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String rechargeNo;

    /** 钱包所有者（结算主体）。 */
    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /** 实际发起充值的业务客户。 */
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
