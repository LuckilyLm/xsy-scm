package com.xsy.scm.purchase.domain.form;

import java.util.List;
import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchaseFinanceConfirmForm {
    @NotNull(message = "请选择采购单")
    @Positive(message = "采购单编号无效")
    private Long purchaseOrderId;
    @NotNull(message = "采购单版本不能为空")
    @Min(value = 0, message = "采购单版本无效")
    private Integer version;
    @Valid
    @NotEmpty(message = "请核对采购明细")
    @Size(max = 500, message = "一次最多确认500行")
    private List<Item> items;
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;

    @Data
    public static class Item {
        @NotNull(message = "采购明细不能为空")
        private Long orderItemId;
        @NotNull(message = "明细版本不能为空")
        @Min(value = 0, message = "明细版本无效")
        private Integer version;
        @NotBlank(message = "请填写实际采购数量")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "实际采购数量格式无效")
        private String quantity;
        @NotBlank(message = "请核对采购单价")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "采购单价格式无效")
        private String expectedPrice;
        @NotBlank(message = "采购单位不能为空")
        @Size(max = 32, message = "采购单位过长")
        private String purchaseUnit;
    }
}
