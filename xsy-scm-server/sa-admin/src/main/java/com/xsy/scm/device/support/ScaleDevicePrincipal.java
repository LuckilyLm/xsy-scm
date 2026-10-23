package com.xsy.scm.device.support;

import java.time.OffsetDateTime;

/**
 * 一次设备请求解析出的双重身份：哪台秤，以及谁在操作。
 *
 * <p>
 * 业务请求两者缺一不可。心跳和本机配置只有设备身份，员工字段为空，不授权任何业务操作。
 *
 * @param deviceId
 *            设备主键
 * @param deviceCode
 *            设备编号，随读数一并记录
 * @param warehouseId
 *            设备绑定仓库，任务不得超出
 * @param sessionId
 *            员工授权会话主键
 * @param employeeId
 *            操作员工主键
 * @param sessionExpiresAt
 *            会话到期时间，用于回答秤端「离线还能操作多久」
 */
public record ScaleDevicePrincipal(Long deviceId, String deviceCode, Long warehouseId, Long sessionId, Long employeeId,
    OffsetDateTime sessionExpiresAt) {
}
