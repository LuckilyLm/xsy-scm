package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 建单选行用的候选订单行：已确认订单上尚未被任何活动任务占用的有效明细。
 */
@Data
public class SortingCandidateQueryForm extends PageParam {
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    @Positive(message = "订单 ID 必须大于0")
    private Long orderId;
}
