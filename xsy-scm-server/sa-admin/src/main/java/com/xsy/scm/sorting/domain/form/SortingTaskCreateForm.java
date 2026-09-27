package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 建单：一次把若干**订单行**并入一个新任务。仓库由操作人显式选择，不从订单/客户/线路推断。
 */
@Data
public class SortingTaskCreateForm {
    @NotNull(message = "仓库不能为空")
    @Positive(message = "仓库 ID 必须大于0")
    private Long warehouseId;

    /**
     * 可留空（未指派），未指派任务只有持建单与指派权的人可见。
     */
    @Positive(message = "分拣员 ID 必须大于0")
    private Long assigneeEmployeeId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotEmpty(message = "订单明细不能为空")
    private List<@NotNull(message = "订单明细 ID 不能为空") @Positive(message = "订单明细 ID 必须大于0") Long> salesOrderItemIds;
}
