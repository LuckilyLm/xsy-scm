package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 预警阈值配置列表查询条件。
 *
 * <p>与其它库存列表同取向：**没有 {@code sortItemList}**，排序写死在 mapper 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryWarningThresholdQueryForm extends PageParam {

    /** 仓库（精确）。 */
    private Long warehouseId;

    /** SKU（精确）。 */
    private Long skuId;

    /** SKU 编码模糊匹配（联 {@code product_sku}）。 */
    @Size(max = 64)
    private String skuCode;

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
