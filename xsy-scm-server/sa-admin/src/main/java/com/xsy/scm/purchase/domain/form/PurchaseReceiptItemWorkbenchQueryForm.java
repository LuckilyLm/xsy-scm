package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 按商品收货工作台查询条件（只读）。
 *
 * <p>
 * 范围固定为「可收货」的采购单（{@code SUBMITTED} / {@code PARTIALLY_RECEIVED}）， 不额外开放状态入参 —— 已收货完成 / 短关 / 取消 / 草稿单都不该出现在收货工作台上。
 * 其余均为可选过滤，仅缩小视图范围，不改变任何聚合口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseReceiptItemWorkbenchQueryForm extends PageParam {

    private Long supplierId;

    private Long warehouseId;

    @Size(max = 64, message = "采购单号不能超过64个字符")
    private String orderNo;

    /**
     * 模糊匹配商品名 / SKU 编码 / SKU 名称快照。
     */
    @Size(max = 64, message = "搜索关键词不能超过64个字符")
    private String keyword;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
