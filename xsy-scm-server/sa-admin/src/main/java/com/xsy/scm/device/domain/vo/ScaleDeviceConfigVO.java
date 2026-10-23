package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 设备激活后读取的自身配置。
 *
 * <p>
 * 只返回设备自己的绑定信息，不含其他设备、其他仓库或员工权限。
 */
@Data
public class ScaleDeviceConfigVO {

    private String deviceCode;

    private Long warehouseId;

    private String stationCode;

    private String status;

    private String protocolType;

    private String agentVersion;

    /** 设备凭证到期时间；到期前需重新激活，不能靠延长有效期绕过撤销。 */
    private OffsetDateTime credentialExpiresAt;
}
