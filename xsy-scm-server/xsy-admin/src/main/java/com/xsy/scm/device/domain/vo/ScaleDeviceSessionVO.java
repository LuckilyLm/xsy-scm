package com.xsy.scm.device.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 设备上的员工会话；不返回令牌摘要。 */
@Data
public class ScaleDeviceSessionVO {
    private Long id;
    private Long deviceId;
    private Long employeeId;
    private String employeeName;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime endedAt;
    private String endReason;
    private Boolean active;
}
