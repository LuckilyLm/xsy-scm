package com.xsy.scm.device.support;

import com.xsy.scm.device.constant.ScaleDeviceErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

/**
 * 当前设备请求的双重身份。
 *
 * <p>
 * 由设备拦截器在请求进入时写入、请求结束时清理。业务层不解析请求头，只读这里，避免每个接口各判断一次身份，也避免漏判。
 */
public final class ScaleDeviceContext {

    private static final ThreadLocal<ScaleDevicePrincipal> HOLDER = new ThreadLocal<>();

    private ScaleDeviceContext() {
    }

    public static void set(ScaleDevicePrincipal principal) {
        HOLDER.set(principal);
    }

    /** 取当前设备身份。缺失说明请求没有经过设备拦截器，属于配置错误而不是业务拒绝。 */
    public static ScaleDevicePrincipal require() {
        ScaleDevicePrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_UNAUTHORIZED);
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
