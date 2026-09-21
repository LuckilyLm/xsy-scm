package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 规格转换单分页查询条件。
 *
 * <p>与其它库存单据查询同取向：**没有 {@code sortItemList}**，排序写死在 mapper 里。
 * {@code status} / {@code convertType} 不加 {@code @Pattern} 白名单：非法值只会筛出空列表
 * （无害），加白名单反而会引入「前端把『全部』提交成空串 → 40000」这类与业务无关的失败。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryConversionQueryForm extends PageParam {

    /**
     * 转换单号（模糊）。
     */
    @Size(max = 64)
    private String conversionNo;

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * 转换类型：{@code SPLIT} / {@code COMBINE}（精确）。
     */
    @Size(max = 20)
    private String convertType;

    /**
     * 状态：{@code PENDING} / {@code COMPLETED} / {@code REJECTED}（精确）。
     */
    @Size(max = 20)
    private String status;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Max(100)
    @Min(1)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
