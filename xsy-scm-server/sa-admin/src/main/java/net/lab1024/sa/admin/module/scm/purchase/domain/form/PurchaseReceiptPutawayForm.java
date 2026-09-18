package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 仓库确认入库（B1，HD-B1-03）。
 *
 * <p>仅适用于 {@code receipt_mode = WAREHOUSE_CONFIRM} 且
 * {@code putaway_status = PENDING} 的已确认收货单。依赖乐观锁 {@code version}。
 */
@Data
public class PurchaseReceiptPutawayForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
}
