package com.xsy.scm.customer.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Pattern;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerVisibilityQueryForm extends PageParam {
    private Long customerId;
    private Long skuId;
    @Pattern(regexp = CustomerVisibilityPolicy.PATTERN, message = "商品可见性策略无效")
    private String visibilityPolicy;
}
