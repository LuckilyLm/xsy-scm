package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 为设备上的员工签发操作会话。
 *
 * <p>
 * 秤端拿到的令牌只是「该员工在这台秤上授权到某个时刻」的凭证，不携带后台权限菜单； 业务写入仍按该员工的真实数据范围校验。
 */
@Data
public class ScaleOperatorSessionForm {

    @NotNull(message = "操作员工不能为空")
    private Long employeeId;

    /**
     * 会话有效分钟数；现场一个班次通常不超过 12 小时，超过由管理员重新签发。
     */
    @NotNull(message = "会话有效时长不能为空")
    @Min(value = 5, message = "会话有效时长至少 5 分钟")
    @Max(value = 720, message = "会话有效时长最多 720 分钟")
    private Integer validMinutes;
}
