package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编辑采购收货单（仅 DRAFT）：只允许改备注。
 */
@Data
public class PurchaseReceiptUpdateForm {
    @NotNull(message = "收货单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
