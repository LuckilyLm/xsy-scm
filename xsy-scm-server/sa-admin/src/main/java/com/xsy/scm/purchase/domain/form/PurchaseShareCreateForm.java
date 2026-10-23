package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 生成采购单分享链接。
 */
@Data
public class PurchaseShareCreateForm {

    @NotNull(message = "采购单不能为空")
    private Long purchaseOrderId;

    /**
     * 有效期天数；留空按 7 天。
     */
    @Min(value = 1, message = "有效期至少 1 天")
    @Max(value = 30, message = "有效期最多 30 天")
    private Integer validDays;
}
