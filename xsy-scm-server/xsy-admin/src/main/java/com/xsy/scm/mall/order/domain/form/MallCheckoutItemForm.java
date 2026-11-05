package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 结算明细项：只提交「哪个规格、多少数量」，价格一律由服务端解析。
 */
@Data
public class MallCheckoutItemForm {
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @NotNull(message = "下单数量不能为空")
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,4})?$", message = "数量必须为大于0、最多4位小数的数字")
    private String quantity;
}
