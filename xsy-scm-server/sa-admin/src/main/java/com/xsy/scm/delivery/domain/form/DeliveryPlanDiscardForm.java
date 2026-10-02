package com.xsy.scm.delivery.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 放弃排线建议。
 *
 * <p>
 * 不改线路，因此只需建议自己的版本；放弃后线路停靠顺序保持原样。
 */
@Data
public class DeliveryPlanDiscardForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
