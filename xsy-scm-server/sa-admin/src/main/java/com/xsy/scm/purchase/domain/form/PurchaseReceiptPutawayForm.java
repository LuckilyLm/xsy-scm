package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 仓库确认入库（B1，HD-B1-03）。
 *
 * <p>仅适用于 {@code receipt_mode = WAREHOUSE_CONFIRM} 且
 * {@code putaway_status = PENDING} 的已确认收货单。依赖乐观锁 {@code version}。
 */
@Data
public class PurchaseReceiptPutawayForm {
    @NotNull(message = "收货单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
