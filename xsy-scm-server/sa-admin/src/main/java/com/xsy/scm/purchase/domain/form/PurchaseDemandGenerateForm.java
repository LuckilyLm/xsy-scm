package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * 生成采购需求（汇总已确认销售订单行）。
 *
 * <p>
 * {@code startAt} / {@code endAt} 是<b>半开区间</b> {@code [startAt, endAt)}，按 {@code sales_order.confirmed_at} 过滤；
 * {@code startAt < endAt} 由 Service 校验（否则 {@code PURCHASE_QUANTITY_INVALID}）。
 *
 * <p>
 * 需求日期：生成出来的 {@code demand_date} 取每行 {@code source_confirmed_at} 在 {@code Asia/Shanghai} 下的日期， <b>不是</b>本窗口的第一天 ——
 * 跨多日窗口不会被压平成同一天。
 */
@Data
public class PurchaseDemandGenerateForm {
    @NotNull(message = "开始时间不能为空")
    private OffsetDateTime startAt;
    @NotNull(message = "结束时间不能为空")
    private OffsetDateTime endAt;
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    /**
     * 可选：指定后直接写入需求的 supplier_id；留空则待第一次分配时固定。
     */
    private Long supplierId;
    /**
     * 可选：默认采购员。
     */
    private Long purchaserId;
}
