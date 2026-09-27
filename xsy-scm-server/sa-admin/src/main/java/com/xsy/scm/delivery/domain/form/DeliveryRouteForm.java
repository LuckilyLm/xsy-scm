package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class DeliveryRouteForm {
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "线路名称不能为空")
    @Size(max = 100, message = "线路名称长度不能超过100")
    private String routeName;
    @NotNull(message = "配送日期不能为空")
    private LocalDate deliveryDate;
    @NotNull(message = "仓库编号不能为空")
    @Positive(message = "仓库编号必须为正数")
    private Long warehouseId;
    @Positive(message = "司机编号必须为正数")
    private Long driverId;
    @Positive(message = "车辆编号必须为正数")
    private Long vehicleId;
    private OffsetDateTime plannedDepartureTime;
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
