package com.xsy.scm.delivery.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 轨迹查询 / 回放。
 *
 * <p>
 * 时间区间按**采集时间**过滤（半开区间 {@code [from, to)}）：设备可能离线补传，
 * 用接收时间筛选会把补传点整段漏掉。
 */
@Data
public class DeliveryGpsQueryForm {

    @NotNull(message = "线路不能为空")
    @Positive(message = "线路编号必须大于零")
    private Long routeId;

    private OffsetDateTime from;

    private OffsetDateTime to;

    @Min(value = 1, message = "返回点数必须至少为1")
    @Max(value = 2000, message = "单次最多返回2000个轨迹点")
    private Integer limit = 500;
}
