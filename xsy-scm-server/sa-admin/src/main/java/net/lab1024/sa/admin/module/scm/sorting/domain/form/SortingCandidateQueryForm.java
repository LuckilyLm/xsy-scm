package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 建单选行用的候选订单行：已确认订单上尚未被任何活动任务占用的有效明细。
 */
@Data
public class SortingCandidateQueryForm extends PageParam {
    @Size(max = 100)
    private String keyword;

    @Positive
    private Long customerId;

    @Positive
    private Long orderId;
}
