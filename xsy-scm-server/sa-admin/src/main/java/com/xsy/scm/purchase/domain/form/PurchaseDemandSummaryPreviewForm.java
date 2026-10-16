package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

/** 缺口预览使用已确认订单的半开时间区间，并要求指定仓库作库存比较。订单行没有供应商和采购员字段，不允许按这些维度虚构筛选。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseDemandSummaryPreviewForm extends PageParam {

    @NotNull(message = "开始时间不能为空")
    private OffsetDateTime startAt;

    @NotNull(message = "结束时间不能为空")
    private OffsetDateTime endAt;

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    /**
     * 可选：按三级分类过滤（命中 {@code xsy_product_spu.category_id}）。仅辅助决策，不改变聚合口径。
     */
    private Long categoryId;

    /**
     * 可选：模糊匹配 SPU 编码 / 商品名 / SKU 编码。
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
