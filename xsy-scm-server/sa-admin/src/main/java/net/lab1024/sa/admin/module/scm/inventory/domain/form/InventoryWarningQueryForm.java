package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 库存预警列表查询条件。
 *
 * <p><b>{@code status} 为空时的语义是「只看异常」，不是「全部」</b>：
 * 这是**预警列表**，一个全是正常项的列表对使用者没有意义，而默认刷出全仓
 * 配置过的条目只会淹没真正需要处理的那几条。要看正常项就显式传 {@code NORMAL}。
 * 前端下拉的第一项因此标成「仅异常」而不是「全部」。
 *
 * <p>与其它库存列表同取向：**没有 {@code sortItemList}**。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryWarningQueryForm extends PageParam {

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * SKU（精确）。
     */
    private Long skuId;

    /**
     * SKU 编码模糊匹配（联 {@code product_sku}）。
     */
    @Size(max = 64)
    private String skuCode;

    /**
     * 预警状态精确过滤：{@code NORMAL} / {@code LOW} / {@code HIGH}。
     *
     * <p>为空 → 只返回 {@code LOW} 与 {@code HIGH}。
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
