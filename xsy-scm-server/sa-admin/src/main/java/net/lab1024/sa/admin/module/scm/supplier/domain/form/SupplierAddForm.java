package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增供应商。
 *
 * <p><b>刻意不含 {@code status}</b>：legacy 不变量 S7 要求新建供应商强制为 {@code ENABLED}，
 * 由 Service 显式设置，不接受客户端指定。
 */
@Data
public class SupplierAddForm {

    @NotBlank
    @Size(max = 64)
    private String supplierCode;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 100)
    private String contactName;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 255)
    private String address;

    @Size(max = 500)
    private String remark;
}
