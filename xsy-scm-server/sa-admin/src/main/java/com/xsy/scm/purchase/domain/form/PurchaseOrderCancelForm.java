package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 取消采购单（W5 Target Design §4.2 T4）。
 *
 * <p>允许 `DRAFT` 与 `SUBMITTED`；**`PARTIALLY_RECEIVED` 不允许取消**（P14），
 * 需要终止时用 `shortClose`（Q2a）。原因必填，否则 40086。
 */
@Data
public class PurchaseOrderCancelForm {
    @NotNull(message = "采购单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 500, message = "取消原因不能超过500个字符")
    private String cancelReason;
}
