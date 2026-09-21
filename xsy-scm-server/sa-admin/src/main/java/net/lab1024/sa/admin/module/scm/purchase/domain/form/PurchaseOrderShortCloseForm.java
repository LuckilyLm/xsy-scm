package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 少收关单（W5 Target Design §4.2 T5 / Q2a）。
 *
 * <p>只允许 `PARTIALLY_RECEIVED` → `SHORT_CLOSED`，且要求「至少一行已收 **且** 至少一行未收齐」；
 * 原因必填，否则 40087。W5 **不冲销**已收部分（R12，登记后续波次）。
 */
@Data
public class PurchaseOrderShortCloseForm {
    @NotNull
    private Long id;
    @NotNull
    @Min(0)
    private Integer version;
    @NotBlank
    @Size(max = 500)
    private String shortCloseReason;
}
