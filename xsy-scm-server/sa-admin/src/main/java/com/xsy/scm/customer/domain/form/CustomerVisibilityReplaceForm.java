package com.xsy.scm.customer.domain.form;

import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 客户可售商品整体替换：策略与清单一次提交，服务端按提交结果增删。
 */
@Data
public class CustomerVisibilityReplaceForm {

    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @NotNull(message = "可见性策略不能为空")
    @Pattern(regexp = CustomerVisibilityPolicy.PATTERN, message = "商品可见性策略无效")
    private String visibilityPolicy;

    /** ALL_ENABLED 时必须为空；ALLOWLIST 时为该客户最终保留的规格。 */
    @Valid
    private List<CustomerSkuVisibilityItemForm> visibilities;
}
