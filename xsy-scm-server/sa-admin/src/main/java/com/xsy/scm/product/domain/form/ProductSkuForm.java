package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

@Data
public class ProductSkuForm {
    private Long skuId;
    private Integer version;
    @NotBlank(message = "商品规格编码不能为空")
    @Size(max = 64, message = "商品规格编码不能超过64个字符")
    private String skuCode;
    @Size(max = 64, message = "条形码不能超过64个字符")
    private String barcode;
    @NotBlank(message = "商品规格名称不能为空")
    @Size(max = 150, message = "商品规格名称不能超过150个字符")
    private String specName;
    @NotNull(message = "规格值不能为空")
    @Size(max = 30, message = "规格值不能超过30个字符")
    private Map<@NotBlank(message = "规格项名称不能为空") @Size(max = 100, message = "规格项名称不能超过100个字符") String,
            @NotBlank(message = "规格值不能为空") @Size(max = 150, message = "规格值不能超过150个字符") String> specValues = new LinkedHashMap<>();
    @NotBlank(message = "销售单位不能为空")
    @Size(max = 32, message = "销售单位不能超过32个字符")
    private String saleUnit;
    @NotNull(message = "商品类型不能为空")
    @ScmEnumValue(enumClass = ScmProductTypeEnum.class, message = "商品类型无效")
    private String productType;
    @NotNull(message = "市场价不能为空")
    @DecimalMin(value = "0.0000", message = "市场价不能小于0.0000")
    @Digits(integer = 14, fraction = 4, message = "市场价最多14位整数和4位小数")
    private BigDecimal marketPrice;
    @NotNull(message = "商品规格状态不能为空")
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品规格状态无效")
    private String status;
    @NotNull(message = "默认商品规格标记不能为空")
    private Boolean defaultFlag;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
}
