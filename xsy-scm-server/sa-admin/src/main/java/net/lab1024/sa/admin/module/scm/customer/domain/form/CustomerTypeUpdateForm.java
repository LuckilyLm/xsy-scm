package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户类型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerTypeUpdateForm extends CustomerTypeAddForm {

    @NotNull
    @Positive
    private Long typeId;

    @NotNull
    @Min(0)
    private Integer version;
}
