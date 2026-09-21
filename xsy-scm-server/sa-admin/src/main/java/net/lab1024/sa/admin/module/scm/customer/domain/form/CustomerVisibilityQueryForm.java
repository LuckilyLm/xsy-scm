package net.lab1024.sa.admin.module.scm.customer.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Pattern;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerVisibilityQueryForm extends PageParam {
    private Long customerId;
    private Long skuId;
    @Pattern(regexp = "ALL_ENABLED|ALLOWLIST")
    private String visibilityPolicy;
}
