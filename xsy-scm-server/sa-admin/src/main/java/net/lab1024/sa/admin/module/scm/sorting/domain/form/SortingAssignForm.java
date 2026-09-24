package net.lab1024.sa.admin.module.scm.sorting.domain.form;

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
    @NotNull
    @Positive
    private Long assigneeEmployeeId;

    @NotNull
    @Min(0)
    private Integer version;

    @Size(max = 500)
    private String reason;
}
