package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 指派 / 改派：改派保留已录入的分拣量，只换受指派人。
 */
@Data
public class SortingAssignForm {
    @NotNull(message = "分拣员不能为空")
    @Positive(message = "分拣员 ID 必须大于0")
    private Long assigneeEmployeeId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @Size(max = 500, message = "指派原因不能超过500个字符")
    private String reason;
}
