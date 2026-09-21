package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建 / 编辑预警阈值配置。
 *
 * <p>创建与编辑共用同一表单：配置只有「一个 (仓库, SKU) 一条」这一种形态，
 * 拆成两个类只会制造两份需要同步维护的校验规则。
 *
 * <p><b>上下限至少填一个、下限不得大于上限</b>：表单层的 {@code @DecimalMin} 只能挡住「为负」，
 * 跨字段的两条判据由服务层给出可归因的 41051（表单校验的错误码是 40000，信息量更少）。
 */
@Data
public class InventoryWarningThresholdAddForm {

    @NotNull
    private Long warehouseId;

    @NotNull
    private Long skuId;

    /**
     * 预警下限；可为空表示不设下限。
     */
    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 14, fraction = 4)
    private BigDecimal warnMin;

    /**
     * 预警上限；可为空表示不设上限。
     */
    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 14, fraction = 4)
    private BigDecimal warnMax;

    @Size(max = 500)
    private String remark;
}
