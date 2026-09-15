package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户状态变更（独立端点，C7）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerStatusForm extends CustomerDeleteForm {

    @NotNull
    @Pattern(regexp = "POTENTIAL|COOPERATING|SUSPENDED|BLACKLIST")
    private String status;
}
