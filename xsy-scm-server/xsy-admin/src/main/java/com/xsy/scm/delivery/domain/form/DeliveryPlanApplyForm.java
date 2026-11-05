package com.xsy.scm.delivery.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 应用排线建议：要显式确认，按线路的乐观锁。
 *
 * <p>
 * {@code version} 是线路的版本，不是建议的：应用会改停靠顺序，算改线路，必须和界面上看到的线路版本一致。
 */
@Data
public class DeliveryPlanApplyForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
