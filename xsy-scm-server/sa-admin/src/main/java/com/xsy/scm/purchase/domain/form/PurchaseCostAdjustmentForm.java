package com.xsy.scm.purchase.domain.form;

import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchaseCostAdjustmentForm {
    @NotNull(message = "请选择已确认的收货明细")
    @Positive(message = "收货明细编号无效")
    private Long receiptItemId;
    @Positive(message = "前次更正编号无效")
    private Long previousAdjustmentId;
    @NotBlank(message = "请核对当前结算单价")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "当前结算单价格式无效")
    private String expectedPrice;
    @NotBlank(message = "请填写更正后的单价")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "更正单价格式无效")
    private String newPrice;
    @NotBlank(message = "请填写成本更正原因")
    @Size(max = 500, message = "更正原因不能超过500字")
    private String reason;
}
