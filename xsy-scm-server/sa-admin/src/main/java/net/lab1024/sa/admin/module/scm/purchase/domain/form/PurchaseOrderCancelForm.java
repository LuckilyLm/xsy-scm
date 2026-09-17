package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 取消采购单（W5 Target Design §4.2 T4）。
 *
 * <p>允许 `DRAFT` 与 `SUBMITTED`；**`PARTIALLY_RECEIVED` 不允许取消**（P14），
 * 需要终止时用 `shortClose`（Q2a）。原因必填，否则 40086。
 */
@Data
public class PurchaseOrderCancelForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
    @NotBlank @Size(max=500) private String cancelReason;
}
