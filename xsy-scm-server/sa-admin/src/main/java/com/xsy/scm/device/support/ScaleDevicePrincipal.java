package com.xsy.scm.device.support;

import java.time.OffsetDateTime;

/**
 * 一次设备请求解析出的双重身份：哪台秤，以及谁在操作。
 *
 * <p>
 * 两者缺一不可。设备身份决定可见仓库范围，员工身份决定业务归属与数据权限； 任一失效都不能写业务结果。
 *
 * @param deviceId
 *            设备主键
 * @param deviceCode
 *            设备编号，写入读数事实
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
