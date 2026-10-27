package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采购单锁单 / 解锁。
 *
 * <p>
 * 锁单原因可选（默认 {@code MANUAL}）；解锁必须说明原因，因为把已对账的单重新打开是需要解释的动作。
 */
@Data
public class PurchaseOrderLockForm {

    @NotNull(message = "采购单不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @Size(max = 500, message = "原因不能超过500个字符")
    private String reason;
}
