package com.xsy.scm.delivery.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 应用排线建议：显式确认，按线路乐观锁。
 *
 * <p>
 * {@code version} 是<b>线路</b>的版本而不是建议的：应用会写回停靠顺序，属于对线路的一次变更， 必须与界面上看到的线路状态同一版本。
 */
@Data
public class DeliveryPlanApplyForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
