package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 只读统计已完成任务的最终确认量；不作为分次劳动报酬或跨单位绩效结算依据。
 */
@Data
public class SortingPerformanceVO {
    private Long warehouseId;
    private String warehouseNameSnapshot;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String confirmedBy;
    private String saleUnitSnapshot;
    private Long taskCount;
    private Long lineCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedQuantity;
    private OffsetDateTime lastConfirmedAt;
}
