package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商状态变更（独立端点，S8）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierStatusForm extends SupplierDeleteForm {

    @NotNull
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
}
