package com.xsy.scm.report.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 一条源事实，数量只用于分拣明细，不跨单位汇总。 */
@Data
public class ScmOrderExceptionRowVO {
    private String exceptionType;
    private Long sourceId;
    private Long sourceRowId;
    private String sourceNo;
    private Long orderId;
    private String orderNo;
    private String customerName;
    private Long warehouseId;
    private String warehouseName;
    private String productName;
    private String unit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal differenceQuantity;
    private String sourceStatus;
    private String reason;
    private OffsetDateTime occurredAt;
}
