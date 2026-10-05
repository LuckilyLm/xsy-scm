package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建 / 编辑预警阈值配置。
 *
 * <p>
 * 创建与编辑共用同一表单：配置只有「一个 (仓库, SKU) 一条」这一种形态， 拆成两个类只会制造两份需要同步维护的校验规则。
 *
 * <p>
 * <b>上下限至少填一个、下限不得大于上限</b>：表单层的 {@code @DecimalMin} 只能挡住「为负」， 跨字段的两条判据由服务层给出可归因的 41051（表单校验的错误码是 40000，信息量更少）。
 */
@Data
public class InventoryWarningThresholdAddForm {

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    /**
     * 预警下限；可为空表示不设下限。
     */
    @DecimalMin(value = "0", inclusive = true, message = "最低预警数量不能小于0")
    @Digits(integer = 14, fraction = 4, message = "最低预警数量最多14位整数和4位小数")
    private BigDecimal warnMin;

    /**
     * 预警上限；可为空表示不设上限。
     */
    @DecimalMin(value = "0", inclusive = true, message = "最高预警数量不能小于0")
    @Digits(integer = 14, fraction = 4, message = "最高预警数量最多14位整数和4位小数")
    private BigDecimal warnMax;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
