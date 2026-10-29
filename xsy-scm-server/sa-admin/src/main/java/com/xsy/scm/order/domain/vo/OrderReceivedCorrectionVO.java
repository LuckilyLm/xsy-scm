package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class OrderReceivedCorrectionVO {
    private Long id;
    private Long orderId;
    private Long orderItemId;
    /** 明细行的商品与规格快照：页面不能拿 orderItemId 当「订单明细」展示。 */
    private String productName;
    private String skuName;
    private String saleUnitSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal shippedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal previousReceivedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    private String reason;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    private OffsetDateTime createdAt;
}
