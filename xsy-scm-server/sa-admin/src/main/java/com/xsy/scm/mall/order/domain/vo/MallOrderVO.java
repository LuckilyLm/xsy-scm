package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MallOrderVO {
    private Long orderId;
    private String orderNo;
    private String status;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedTotalAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementTotalAmount;
    private OffsetDateTime expectDeliveryTime;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime confirmedAt;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private List<MallOrderItemVO> items = new ArrayList<>();
}
