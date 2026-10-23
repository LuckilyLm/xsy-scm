package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * 采购需求按订单确认时间的半开区间生成；需求日期由每张订单的北京时间确认日决定，不是筛选窗口首日。
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
