package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.util.List;

/** 单条任务明细的分次事件页；累计量始终取全部历史中的最新事件。 */
public record SortingPartHistoryVO(List<SortingPartRecordVO> records,
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        BigDecimal totalQuantity, boolean hasMore, Long nextBeforeId) {
}
