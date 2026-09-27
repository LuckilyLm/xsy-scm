package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑仓库。`status` 同 {@link WarehouseAddForm}，不在字段清单内。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseUpdateForm extends WarehouseAddForm {

    @NotNull
    @Positive
    private Long id;

    @NotNull
    @Min(0)
    private Integer version;
}
