package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 分拣任务列表（按客户订单视角）。范围永远是「授权仓 ∩ 可见指派人」，
 * 这里的筛选条件只能在范围内再收窄，不能放宽。
 */
@Data
public class SortingTaskQueryForm extends PageParam {
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmSortingTaskStatusEnum.class, message = "分拣任务状态无效")
    private String status;

    @Positive(message = "仓库 ID 必须大于0")
    private Long warehouseId;

    /**
     * 仅对持建单与指派权的人有意义：他们能跨指派人看，才可以按人筛。
     */
    @Positive(message = "分拣员 ID 必须大于0")
    private Long assigneeEmployeeId;

    /**
     * {@code true} 只看未指派任务（待办队列）。
     */
    private Boolean unassignedOnly;
}
