package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

/**
 * 生成采购需求（汇总已确认销售订单行）（W5 Target Design §7.4）。
 *
 * <p>`startAt` / `endAt` 是**半开区间** `[startAt, endAt)`，按 `sales_order.confirmed_at` 过滤；
 * `startAt < endAt` 由 Service 校验（否则 `PURCHASE_QUANTITY_INVALID`）。
 *
 * <p>**Q6a**：生成出来的 `demand_date` 取每行 `source_confirmed_at` 在 `Asia/Shanghai` 下的日期，
 * **不是**本窗口的第一天 —— 跨多日窗口不会被压平成同一天。
 */
@Data
public class PurchaseDemandGenerateForm {
    @NotNull
    private OffsetDateTime startAt;
    @NotNull
    private OffsetDateTime endAt;
    @NotNull
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
