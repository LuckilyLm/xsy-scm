package com.xianshuyuan.scm.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xianshuyuan.scm.common.api.DecimalStringDeserializer;
import jakarta.validation.constraints.*;

public record SalesOrderItemSaveRequest(
        Long id,
        @Min(value = 0, message = "版本号不正确") Integer version,
        @NotNull(message = "SKU不能为空") Long skuId,
        @NotBlank(message = "订购数量不能为空") @Pattern(regexp = "^\\d{1,14}(\\.\\d{1,4})?$", message = "订购数量格式不正确") @JsonDeserialize(using = DecimalStringDeserializer.class) String orderedQuantity,
        @Pattern(regexp = "^\\d{1,14}(\\.\\d{1,4})?$", message = "单价格式不正确") @JsonDeserialize(using = DecimalStringDeserializer.class) String unitPrice,
        boolean manualPriceOverride,
        String overrideReason
) {
}
