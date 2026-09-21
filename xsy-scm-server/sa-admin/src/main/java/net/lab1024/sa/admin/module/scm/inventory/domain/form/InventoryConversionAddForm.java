package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建 / 编辑待审核的规格转换单。
 *
 * <p>创建与编辑共用同一表单：转换单只有待审核态可改，字段集合完全一致。
 *
 * <p><b>折算关系由两个数量显式声明</b>（{@code sourceQuantity} : {@code targetQuantity}），
 * 系统不推断、不校验「合理区间」—— 一箱到底是 9.5 kg 还是 10 kg 是业务事实。
 * 但两个数量都必须为正（DB 也有 CHECK）。
 *
 * <p><b>两个单位也由单据声明</b>（{@code sourceUnit} / {@code targetUnit}）：
 * 折算关系本身含单位。执行时与各自余额的记账单位比对，不一致直接失败（41059 / 41060），
 * 不做隐式换算。
 */
@Data
public class InventoryConversionAddForm {

    @NotNull
    private Long warehouseId;

    /**
     * {@code SPLIT} 整件拆零 / {@code COMBINE} 组合拆分。
     */
    @NotBlank
    @Pattern(regexp = "SPLIT|COMBINE")
    private String convertType;

    @Size(max = 200)
    private String reason;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    @Valid
    private List<Item> items;

    /**
     * 转换明细行：源 SKU 出 N（单位 U₁） → 目标 SKU 入 M（单位 U₂）。
     */
    @Data
    public static class Item {

        @NotNull
        private Long sourceSkuId;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 14, fraction = 4)
        private BigDecimal sourceQuantity;

        @NotBlank
        @Size(max = 32)
        private String sourceUnit;

        @NotNull
        private Long targetSkuId;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 14, fraction = 4)
        private BigDecimal targetQuantity;

        @NotBlank
        @Size(max = 32)
        private String targetUnit;

        @Size(max = 500)
        private String remark;
    }
}
