package com.xsy.scm.device.permission;

/**
 * 设备接入域的稳定权限标识。
 *
 * <p>
 * 设备自身的凭证不经过这里的权限：设备端接口只认设备令牌与员工授权会话。以下权限只授予 后台员工，用于登记设备、发码、签发操作会话与撤销绑定。设备管理员不应因此获得财务或库存权限。
 */
public final class ScaleDevicePermission {

    /**
     * 查询设备台账、绑定状态与最近心跳。
     */
    public static final String QUERY = "scm:device:scale:query";

    /**
     * 登记设备、生成配对码、撤销绑定。
     */
    public static final String BIND = "scm:device:scale:bind";

    /**
     * 为设备上的员工签发操作会话。
     */
    public static final String SESSION_ISSUE = "scm:device:scale:session:issue";

    private ScaleDevicePermission() {
    }
}
