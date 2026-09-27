package com.xsy.scm.supplier.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑供应商。
 *
 * <p><b>刻意不含 {@code status}</b>：更新路径不触碰状态，
 * 状态只能通过 {@code /scm/supplier/updateStatus} 变更。即使客户端在请求体里塞了
 * {@code status}，Jackson 也会忽略（无对应属性）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierUpdateForm extends SupplierAddForm {

    @NotNull(message = "供应商 ID 不能为空")
    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
