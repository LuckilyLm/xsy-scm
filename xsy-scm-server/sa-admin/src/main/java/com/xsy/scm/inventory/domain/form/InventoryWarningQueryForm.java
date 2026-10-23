package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 库存预警列表查询条件。
 *
 * <p>
 * {@code status} 为空时只返回异常项，不是「全部」：这是预警列表，一个全是正常项的列表对使用者没有意义，而默认刷出全仓配置过的条目只会淹没真正需要处理的那几条。要看正常项就显式传
 * {@code NORMAL}。前端下拉的第一项因此标成「仅异常」而不是「全部」。
 *
 * <p>
 * 与其它库存列表做法一致：没有 {@code sortItemList}。
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
     * SKU 编码模糊匹配（联 {@code xsy_product_sku}）。
     */
    @Size(max = 64, message = "商品规格编码不能超过64个字符")
    private String skuCode;

    /**
     * 阈值配置 id（精确）。
     *
     * <p>
     * 供站内信跳转使用：预警消息的 {@code dataId} 就是配置 id，点进来要能直接定位到那一条。
     */
    private Long thresholdId;

    /**
     * 预警状态精确过滤：{@code NORMAL} / {@code LOW} / {@code HIGH}。
     *
     * <p>
     * 为空 → 只返回 {@code LOW} 与 {@code HIGH}。
     */
    @Size(max = 20, message = "预警状态不能超过20个字符")
    private String status;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Max(value = 100, message = "每页条数不能超过100")
    @Min(value = 1, message = "每页条数必须至少为1")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
