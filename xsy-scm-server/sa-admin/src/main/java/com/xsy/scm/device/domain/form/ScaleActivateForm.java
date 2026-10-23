package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备激活请求，对应秤端 {@code POST /agents/activate}。
 *
 * <p>
 * 配对码由后台员工签发，一次性且短有效期。激活成功后服务端清空配对码摘要， 重放同一配对码不能再换到第二个凭证。
 */
@Data
public class ScaleActivateForm {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码最多 64 个字符")
    private String deviceCode;

    /**
     * 安装标识；用于区分同一设备编号下的重装，不参与业务授权。
     */
    @Size(max = 128, message = "安装标识最多 128 个字符")
    private String installationId;

    @NotBlank(message = "配对码不能为空")
    @Size(max = 200, message = "配对码最多 200 个字符")
    private String pairingCode;

    @Size(max = 16, message = "协议版本最多 16 个字符")
    private String protocolVersion;

    @Size(max = 40, message = "设备程序版本最多 40 个字符")
    private String agentVersion;
}
