package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除供应商（POST + body 传 {@code {supplierId, version}}，统一乐观锁入口）。
 */
@Data
public class SupplierDeleteForm {

    @NotNull
    @Positive
    private Long supplierId;

    @NotNull
    @Min(0)
    private Integer version;
}
