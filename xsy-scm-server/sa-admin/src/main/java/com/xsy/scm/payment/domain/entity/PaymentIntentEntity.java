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
 * 支付意图：这笔钱打算怎么收。
 *
 * <p>
 * {@code amount} 是**显式给出**的应付金额，不由订单金额推断：一张订单可能只收一部分，
 * 也可能拆成「余额 + 在线支付」两条资金来源（各自一条意图）。
 */
@Data
@TableName("payment_intent")
public class PaymentIntentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String intentNo;

    private Long customerId;

    private String customerNameSnapshot;

    /** 当前只有 {@code SALES_ORDER}。 */
    private String sourceType;

    private Long sourceId;

    private String sourceNoSnapshot;

    private BigDecimal amount;

    /** {@code ONLINE} / {@code BALANCE}。 */
    private String method;

    /** {@code MOCK} / {@code WECHAT}。 */
    private String provider;

    private String status;

    private String externalIntentId;

    /** 仅 MOCK：回放剧本。表上有 CHECK 保证非 mock 渠道带不上。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mockScenario;

    private OffsetDateTime expireAt;

    private OffsetDateTime succeededAt;

    private OffsetDateTime closedAt;

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
