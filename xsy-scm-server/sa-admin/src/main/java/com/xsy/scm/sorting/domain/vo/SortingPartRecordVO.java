package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 分次作业记录只表示分拣过程，不生成库存 / 财务数据。
 */
@Data
public class SortingPartRecordVO {
    private Long id;
    private Long taskId;
    private Long taskItemId;
    private String eventType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantityDelta;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal totalQuantity;
    private String note;
    private String createdBy;
    private OffsetDateTime createdAt;
}
