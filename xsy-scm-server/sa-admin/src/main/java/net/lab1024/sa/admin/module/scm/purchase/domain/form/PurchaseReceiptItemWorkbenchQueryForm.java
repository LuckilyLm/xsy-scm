package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 按商品收货工作台查询条件（Wave 2B §6.3，只读）。
 *
 * <p>范围固定为「可收货」的采购单（{@code SUBMITTED} / {@code PARTIALLY_RECEIVED}），
 * 不额外开放状态入参 —— 已收货完成 / 短关 / 取消 / 草稿单都不该出现在收货工作台上。
 * 其余均为可选过滤，仅缩小视图范围，不改变任何聚合口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseReceiptItemWorkbenchQueryForm extends PageParam {

    private Long supplierId;

    private Long warehouseId;

    @Size(max = 64)
    private String orderNo;

    /**
     * 模糊匹配商品名 / SKU 编码 / SKU 名称快照。
     */
    @Size(max = 64)
    private String keyword;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(1)
    @Max(100)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
