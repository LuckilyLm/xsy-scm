package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 少收关单。
 *
 * <p>只允许 `PARTIALLY_RECEIVED` → `SHORT_CLOSED`，且要求「至少一行已收 **且** 至少一行未收齐」；
 * 原因必填，否则 40087。少收关单保留已收数量，不会冲销已收货物。
 */
@Data
public class PurchaseOrderShortCloseForm {
    @NotNull(message = "采购单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "少收关单原因不能为空")
    @Size(max = 500, message = "少收关单原因不能超过500个字符")
    private String shortCloseReason;
}
