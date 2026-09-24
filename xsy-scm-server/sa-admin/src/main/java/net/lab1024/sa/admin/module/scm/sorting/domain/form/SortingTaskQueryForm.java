package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 分拣任务列表（按客户订单视角）。范围永远是「授权仓 ∩ 可见指派人」，
 * 这里的筛选条件只能在范围内再收窄，不能放宽。
 */
@Data
public class SortingTaskQueryForm extends PageParam {
    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "PENDING|SORTING|COMPLETED|CANCELLED")
    private String status;

    @Positive
    private Long warehouseId;

    /**
     * 仅对持建单与指派权的人有意义：他们能跨指派人看，才可以按人筛。
     */
    @Positive
    private Long assigneeEmployeeId;

    /**
     * {@code true} 只看未指派任务（待办队列）。
     */
    private Boolean unassignedOnly;
}
