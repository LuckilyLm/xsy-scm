package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台登记一台电子秤。登记后生成一次性配对码，由现场操作员在秤端输入。
 */
@Data
public class ScaleDeviceRegisterForm {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码最多 64 个字符")
    private String deviceCode;

    @Size(max = 100, message = "设备名称最多 100 个字符")
    private String deviceName;

    @Size(max = 100, message = "设备型号最多 100 个字符")
    private String deviceModel;

    @NotNull(message = "绑定仓库不能为空")
    private Long warehouseId;

    @Size(max = 64, message = "工作站编码最多 64 个字符")
    private String stationCode;
}
