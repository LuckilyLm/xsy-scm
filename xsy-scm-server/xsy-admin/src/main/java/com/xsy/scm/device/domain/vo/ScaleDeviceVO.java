package com.xsy.scm.device.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ScaleDeviceVO {
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceModel;
    private Long warehouseId;
    private String warehouseName;
    private String stationCode;
    private String status;
    private String protocolType;
    private String agentVersion;
    private OffsetDateTime pairingExpiresAt;
    private OffsetDateTime credentialExpiresAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime lastReadingAt;
    private Integer queueDepth;
    private Boolean scaleConnected;
    private Boolean printerConnected;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
    /** 当前仍在有效期内的员工会话数。 */
    private Integer activeSessionCount;
}
