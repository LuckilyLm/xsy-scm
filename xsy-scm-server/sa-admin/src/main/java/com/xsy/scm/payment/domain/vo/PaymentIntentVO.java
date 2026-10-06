package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** 支付意图（含其下交易流水）。 */
@Data
public class PaymentIntentVO {

    private Long id;

    private String intentNo;

    private Long customerId;

    private String customerNameSnapshot;

    private String sourceType;

    private Long sourceId;

    private String sourceNoSnapshot;

    /** 应付金额：<b>显式给出</b>，不从订单金额推断。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String method;

    private String provider;

    private String status;

    private String externalIntentId;

    /** 仅本地模拟渠道有值。 */
    private String mockScenario;

    private OffsetDateTime expireAt;

    private OffsetDateTime succeededAt;

    private OffsetDateTime closedAt;

    private String remark;

    private OffsetDateTime createdAt;

    private String createdBy;

    /** 该意图下的交易流水（一次发起一条）。 */
    private List<PaymentTransactionVO> transactions;
}
