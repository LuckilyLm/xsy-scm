package com.xianshuyuan.scm.product.dto;

import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record ProductSkuSaveRequest(
    Long id,
    Integer version,
    @NotBlank(message = "SKU 编码不能为空")
    @Size(max = 64, message = "SKU 编码不能超过64个字符")
    String skuCode,
    @Size(max = 64, message = "条码不能超过64个字符")
    String barcode,
    @NotBlank(message = "规格名称不能为空")
    @Size(max = 150, message = "规格名称不能超过150个字符")
    String specName,
    @NotNull(message = "规格快照不能为空")
    Map<String, String> specValues,
    @NotBlank(message = "销售单位不能为空")
    @Size(max = 32, message = "销售单位不能超过32个字符")
    String saleUnit,
    @NotNull(message = "商品类型不能为空")
    ProductType productType,
    @NotNull(message = "市场价不能为空")
    @DecimalMin(value = "0.0000", message = "市场价不能小于零")
    BigDecimal marketPrice,
    @NotNull(message = "SKU 状态不能为空")
    ShelfStatus status,
    boolean defaultSku,
    @NotNull(message = "SKU 排序不能为空")
    Integer sortOrder
) {
}
