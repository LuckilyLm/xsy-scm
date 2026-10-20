package com.xsy.scm.purchase.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** 采购员在手机上回填的单价与实收重量。 */
@Data
public class PurchaseShareFeedbackForm {

    @NotEmpty(message = "至少填写一行")
    @Valid
    private List<Line> items;

    /** 单行回填：单价与重量至少一项。 */
    @Data
    public static class Line {

        @NotNull(message = "采购单明细不能为空")
        private Long purchaseOrderItemId;

        @DecimalMin(value = "0", message = "单价不能为负")
        @Digits(integer = 14, fraction = 4, message = "单价最多四位小数")
        private BigDecimal unitPrice;

        @DecimalMin(value = "0", message = "实收重量不能为负")
        @Digits(integer = 14, fraction = 4, message = "实收重量最多四位小数")
        private BigDecimal receivedQuantity;

        @Size(max = 200, message = "备注不能超过200个字符")
        private String remark;
    }
}
