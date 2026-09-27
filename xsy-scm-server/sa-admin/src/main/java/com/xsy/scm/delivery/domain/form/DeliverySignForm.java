package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.delivery.constant.ScmDeliverySignResultEnum;

/**
 * 订单签收：终态只有 SIGNED 与 EXCEPTION 两种，不做部分签收（P2 裁决第 12 条）。
 *
 * <p>{@code version} 锁的是 {@code delivery_route_order} 这一行，不是线路 —— 同一线路上的
 * 不同订单要能被并发签收，只有对同一订单重复签收才需要互相拒绝。
 */
@Data
public class DeliverySignForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @NotBlank(message = "签收结果不能为空")
    @ScmEnumValue(enumClass = ScmDeliverySignResultEnum.class, message = "签收结果无效")
    private String result;

    /**
     * 异常签收必填；正常签收可留一句备注。
     */
    @Size(max = 500, message = "原因长度不能超过500")
    private String reason;
}
