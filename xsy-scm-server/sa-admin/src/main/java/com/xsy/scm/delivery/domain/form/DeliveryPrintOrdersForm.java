package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 按订单正式生成打印：提交预览时确认的具体订单集合，不接受含义不明的「ALL」。
 */
@Data
public class DeliveryPrintOrdersForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotEmpty(message = "请至少选择一张订单")
    @Size(max = 500, message = "订单数量不能超过500张")
    private List<
            @NotNull(message = "订单编号不能为空") @Positive(message = "订单编号必须为正数") Long> orderIds;
}
