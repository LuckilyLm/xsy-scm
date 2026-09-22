package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

/**
 * 订单汇总 / 库存缺口预览查询条件（Wave 2A §6A.4，只读）。
 *
 * <p>沿用 {@link PurchaseDemandGenerateForm} 的窗口语义：{@code [startAt, endAt)} 按
 * {@code sales_order.confirmed_at} 过滤已确认订单，{@code warehouseId} 决定比对哪个仓的余额。
 *
 * <p><b>为什么 warehouseId 必填</b>：预览要「同仓」比订单量与可用量，而订单行本身不带仓库
 * （仓库只存在于采购需求/库存侧），必须由调用方显式指定，服务端不替调用方选仓（与 Q12 一致）。
 *
 * <p>{@code supplierId} / {@code purchaserId} 不在本波实现：{@code sales_order_item} 上没有这两
 * 个维度，加进来只会给前端一个静默失效的筛选项，等第二阶段随采购建议口径一并裁决。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseDemandSummaryPreviewForm extends PageParam {

    @NotNull
    private OffsetDateTime startAt;

    @NotNull
    private OffsetDateTime endAt;

    @NotNull
    private Long warehouseId;

    /**
     * 可选：按三级分类过滤（命中 {@code product_spu.category_id}）。仅辅助决策，不改变聚合口径。
     */
    private Long categoryId;

    /**
     * 可选：模糊匹配 SPU 编码 / 商品名 / SKU 编码。
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
