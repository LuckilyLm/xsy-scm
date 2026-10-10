package com.xsy.scm.order.domain.vo;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class OrderReturnReceiptVO {
    private Long receiptId;
    private Long returnId;
    private Long warehouseId;
    private OffsetDateTime receivedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
    private List<Item> items;

    @Data
    public static class Item {
        private Long receiptItemId;
        private Long returnItemId;
        private Long sourceSalesOutMovementId;
        private Long sourceOutboundItemId;
        private String disposition;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitCost;
    }
}
