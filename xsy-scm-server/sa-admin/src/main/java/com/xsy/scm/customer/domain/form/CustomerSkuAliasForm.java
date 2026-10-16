package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerSkuAliasForm {
    @NotNull(message = "客户ID不能为空")
    @Positive(message = "客户ID必须大于0")
    private Long customerId;
    @NotNull(message = "商品规格ID不能为空")
    @Positive(message = "商品规格ID必须大于0")
    private Long skuId;
    @Size(max = 100, message = "客户商品别名不能超过100个字符")
    private String alias;
    private Integer version;
}
