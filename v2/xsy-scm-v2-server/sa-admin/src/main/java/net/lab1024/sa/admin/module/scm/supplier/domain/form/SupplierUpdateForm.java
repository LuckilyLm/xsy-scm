package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑供应商。
 *
 * <p><b>刻意不含 {@code status}</b>：legacy 不变量 S6 —— 更新路径不触碰状态，
 * 状态只能通过 {@code /scm/supplier/updateStatus} 变更。即使客户端在请求体里塞了
 * {@code status}，Jackson 也会忽略（无对应属性）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierUpdateForm extends SupplierAddForm {

    @NotNull
    @Positive
    private Long supplierId;

    @NotNull
    @Min(0)
    private Integer version;
}
