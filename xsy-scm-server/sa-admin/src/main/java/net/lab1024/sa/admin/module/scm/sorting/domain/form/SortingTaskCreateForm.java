package net.lab1024.sa.admin.module.scm.sorting.domain.form;

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
    @NotNull
    @Positive
    private Long warehouseId;

    /**
     * 可留空（未指派），未指派任务只有持建单与指派权的人可见。
     */
    @Positive
    private Long assigneeEmployeeId;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    private List<@NotNull @Positive Long> salesOrderItemIds;
}
