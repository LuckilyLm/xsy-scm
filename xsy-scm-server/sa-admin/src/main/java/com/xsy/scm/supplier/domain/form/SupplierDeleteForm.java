package com.xsy.scm.supplier.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除供应商（POST + body 传 {@code {supplierId, version}}，统一乐观锁入口）。
 */
@Data
public class SupplierDeleteForm {

    @NotNull(message = "供应商 ID 不能为空")
    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
