package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登记结果。配对码明文只在这里出现一次，库里只留摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleDeviceRegisterVO {

    private Long deviceId;

    private String deviceCode;

    private String pairingCode;

    private OffsetDateTime pairingExpiresAt;
}
