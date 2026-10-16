package com.xsy.scm.inventory.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.inventory.constant.ScmInventoryConversionTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 规格转换由源规格、目标规格以及两端数量和单位明确表达。待审核可修改，审批后按该折算关系同时执行出库和入库，不做隐式单位换算。 */
@Data
public class InventoryConversionAddForm {

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /**
     * {@code SPLIT} 整件拆零 / {@code COMBINE} 组合拆分。
     */
    @NotBlank(message = "转换类型不能为空")
    @ScmEnumValue(enumClass = ScmInventoryConversionTypeEnum.class, message = "转换类型无效")
    private String convertType;

    @Size(max = 200, message = "转换原因不能超过200个字符")
    private String reason;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotEmpty(message = "转换明细不能为空")
    @Valid
    private List<Item> items;

    /**
     * 转换明细行：源 SKU 出 N（单位 U₁） → 目标 SKU 入 M（单位 U₂）。
     */
    @Data
    public static class Item {

        @NotNull(message = "来源商品规格不能为空")
        private Long sourceSkuId;

        @NotNull(message = "来源数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "来源数量必须大于0")
        @Digits(integer = 14, fraction = 4, message = "来源数量最多14位整数和4位小数")
        private BigDecimal sourceQuantity;

        @NotBlank(message = "来源单位不能为空")
        @Size(max = 32, message = "来源单位不能超过32个字符")
        private String sourceUnit;

        @NotNull(message = "目标商品规格不能为空")
        private Long targetSkuId;

        @NotNull(message = "目标数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "目标数量必须大于0")
        @Digits(integer = 14, fraction = 4, message = "目标数量最多14位整数和4位小数")
        private BigDecimal targetQuantity;

        @NotBlank(message = "目标单位不能为空")
        @Size(max = 32, message = "目标单位不能超过32个字符")
        private String targetUnit;

        @Size(max = 500, message = "备注不能超过500个字符")
        private String remark;
    }
}
