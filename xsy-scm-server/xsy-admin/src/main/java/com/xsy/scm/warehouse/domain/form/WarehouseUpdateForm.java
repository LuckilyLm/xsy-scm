package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑仓库。{@code status} 同 {@link WarehouseAddForm}，不在字段清单内。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseUpdateForm extends WarehouseAddForm {

    @NotNull(message = "仓库 ID 不能为空")
    @Positive(message = "仓库 ID 必须大于0")
    private Long id;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
