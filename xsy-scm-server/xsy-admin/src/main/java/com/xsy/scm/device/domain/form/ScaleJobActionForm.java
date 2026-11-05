package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 领取或释放作业租约，对应秤端 {@code POST /jobs/{jobId}/claim|release}。
 *
 * <p>
 * {@code jobVersion} 是领取时的乐观锁依据：任务在秤端看到它之后被改派或重开，版本就对不上，必须重新拉取任务而不是继续作业。
 */
@Data
public class ScaleJobActionForm {

    @Size(max = 64, message = "设备编码最多 64 个字符")
    private String deviceCode;

    @NotBlank(message = "作业版本不能为空")
    @Size(max = 40, message = "作业版本最多 40 个字符")
    private String jobVersion;

    /**
     * 释放原因；正常退出可留空。
     */
    @Size(max = 200, message = "释放原因最多 200 个字符")
    private String reason;
}
