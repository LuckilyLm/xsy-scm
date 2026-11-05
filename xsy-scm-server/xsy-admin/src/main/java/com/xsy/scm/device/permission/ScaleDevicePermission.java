package com.xsy.scm.device.permission;

/**
 * 设备接入域的权限标识。
 *
 * <p>
 * 设备凭证不走这里：设备端接口只认设备令牌和员工授权会话。这些权限只给后台员工，用来登记设备、发码、签发会话和撤销绑定；设备管理员不会因此拿到财务或库存权限。
 */
public final class ScaleDevicePermission {

    /** 查询设备台账、绑定状态与最近心跳。 */
    public static final String QUERY = "scm:device:scale:query";

    /** 登记设备、生成配对码、撤销绑定。 */
    public static final String BIND = "scm:device:scale:bind";

    /** 为设备上的员工签发操作会话。 */
    public static final String SESSION_ISSUE = "scm:device:scale:session:issue";

    private ScaleDevicePermission() {
    }
}
