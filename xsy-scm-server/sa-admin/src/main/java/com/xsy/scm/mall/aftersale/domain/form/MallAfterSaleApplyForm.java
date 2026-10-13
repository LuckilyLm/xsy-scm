package com.xsy.scm.mall.aftersale.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class MallAfterSaleApplyForm {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotBlank(message = "售后原因不能为空")
    @Size(max = 500, message = "售后原因不能超过500个字符")
    private String reason;
    @Valid
    @NotEmpty(message = "售后明细不能为空")
    @Size(max = 500, message = "售后明细不能超过500项")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "订单明细 ID 不能为空")
        private Long orderItemId;
        @NotBlank(message = "申请数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String quantity;
    }
}
