package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增客户类型。
 *
 * <p>状态随表单带入、没有独立的状态端点（legacy 不变量 T8）：客户类型是低频字典数据，
 * 单独开一个状态端点没有收益。
 */
@Data
public class CustomerTypeAddForm {

    @NotBlank
    @Size(max = 64)
    private String typeCode;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status = "ENABLED";
}
