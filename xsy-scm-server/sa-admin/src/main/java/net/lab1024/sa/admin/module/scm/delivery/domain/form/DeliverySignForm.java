package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 订单签收：终态只有 SIGNED 与 EXCEPTION 两种，不做部分签收（P2 裁决第 12 条）。
 *
 * <p>{@code version} 锁的是 {@code delivery_route_order} 这一行，不是线路 —— 同一线路上的
 * 不同订单要能被并发签收，只有对同一订单重复签收才需要互相拒绝。
 */
@Data
public class DeliverySignForm {
    @NotNull
    @Min(0)
    private Integer version;

    @NotBlank
    @Pattern(regexp = "SIGNED|EXCEPTION")
    private String result;

    /**
     * 异常签收必填；正常签收可留一句备注。
     */
    @Size(max = 500)
    private String reason;
}
