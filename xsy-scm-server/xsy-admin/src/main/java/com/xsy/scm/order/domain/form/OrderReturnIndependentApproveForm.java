package com.xsy.scm.order.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 独立退货审批：明细没有原订单行，按退货行 ID 给批准数量。
 */
@Data
public class OrderReturnIndependentApproveForm {
    @NotNull(message = "退货单 ID 不能为空")
    private Long returnId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Valid
    @NotEmpty(message = "退货审批明细不能为空")
    @Size(max = 500, message = "退货审批明细不能超过500项")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "退货行不能为空")
        private Long returnItemId;
        @NotBlank(message = "批准数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String approvedQuantity;
    }
}
