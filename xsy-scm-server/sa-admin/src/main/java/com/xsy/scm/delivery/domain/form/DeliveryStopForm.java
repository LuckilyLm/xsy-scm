package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Data
public class DeliveryStopForm extends com.xsy.scm.common.domain.ScmLocationForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    private OffsetDateTime plannedArrivalTime;
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
