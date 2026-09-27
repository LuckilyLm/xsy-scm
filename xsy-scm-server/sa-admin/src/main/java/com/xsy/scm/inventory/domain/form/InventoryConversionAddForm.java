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

/**
 * 新建 / 编辑待审核的规格转换单。
 *
 * <p>
 * 创建与编辑共用同一表单：转换单只有待审核态可改，字段集合完全一致。
 *
 * <p>
 * <b>折算关系由两个数量显式声明</b>（{@code sourceQuantity} : {@code targetQuantity}）， 系统不推断、不校验「合理区间」—— 一箱到底是 9.5 kg 还是 10 kg 是业务事实。
 * 但两个数量都必须为正（DB 也有 CHECK）。
 *
 * <p>
 * <b>两个单位也由单据声明</b>（{@code sourceUnit} / {@code targetUnit}）： 折算关系本身含单位。执行时与各自余额的记账单位比对，不一致直接失败（41059 / 41060）， 不做隐式换算。
 */
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

        @NotNull(message = "来源 SKU 不能为空")
        private Long sourceSkuId;

        @NotNull(message = "来源数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "来源数量必须大于0")
        @Digits(integer = 14, fraction = 4, message = "来源数量最多14位整数和4位小数")
        private BigDecimal sourceQuantity;

        @NotBlank(message = "来源单位不能为空")
        @Size(max = 32, message = "来源单位不能超过32个字符")
        private String sourceUnit;

        @NotNull(message = "目标 SKU 不能为空")
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
