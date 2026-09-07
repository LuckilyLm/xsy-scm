package com.xianshuyuan.scm.supplier.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xianshuyuan.scm.common.api.DecimalStringDeserializer;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SupplierSkuSaveRequest(
        Long id,
        @NotNull Long supplierId,
        @NotNull Long skuId,
        @NotBlank @Size(max = 32) String purchaseUnit,
        @Pattern(regexp = "^\\d{1,14}(\\.\\d{1,4})?$", message = "参考价格式不正确")
        @JsonDeserialize(using = DecimalStringDeserializer.class) String referencePrice,
        @Positive Long purchaserId,
        boolean defaultSupplier,
        EnabledStatus status,
        Integer version
) {
}
