package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class SalesOrderItemForm {
    private Long itemId;
    private Integer version;
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @NotBlank(message = "下单数量不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String orderedQuantity;
    @NotNull(message = "是否人工改价不能为空")
    private Boolean manualPriceOverride;
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @Size(max = 500, message = "改价原因不能超过500个字符")
    private String overrideReason;
    private Integer sortOrder;
}
