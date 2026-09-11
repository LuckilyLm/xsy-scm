package com.xianshuyuan.scm.mall.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xianshuyuan.scm.common.api.DecimalStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MallCheckoutItemRequest(
        @NotNull(message = "商品不能为空") Long skuId,
        @NotBlank(message = "数量不能为空")
        @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,4})?$", message = "数量格式不正确")
        @JsonDeserialize(using = DecimalStringDeserializer.class) String quantity
) {
}
