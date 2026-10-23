package com.xsy.scm.device.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 设备接口专用响应体。
 *
 * <p>
 * 不复用 {@code ResponseDTO}：后台响应的 {@code code} 是数字业务码，设备协议要求 {@code code} 是 {@code OK} / {@code DUPLICATED}
 * 这样的字符串，语义和类型都不同，共用一个类会让秤端把成功当成解析失败。
 *
 * <p>
 * 失败时 HTTP 状态码与这里只出现其一：非 2xx 表示明确拒绝，{@code code} 给出可判定的错误码； 网络超时表示结果未知，秤端必须用同一幂等键重试，而不是另建事件。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScaleDeviceResponse<T> {

    public static final String CODE_OK = "OK";

    public static final String CODE_DUPLICATED = "DUPLICATED";

    private final String code;

    private final String msg;

    private final T data;

    private ScaleDeviceResponse(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ScaleDeviceResponse<T> ok(T data) {
        return new ScaleDeviceResponse<>(CODE_OK, null, data);
    }

    /** 同一幂等键重复提交且请求内容一致：返回原结果，不当作错误。 */
    public static <T> ScaleDeviceResponse<T> duplicated(T data) {
        return new ScaleDeviceResponse<>(CODE_DUPLICATED, null, data);
    }

    public static <T> ScaleDeviceResponse<T> error(String code, String msg) {
        return new ScaleDeviceResponse<>(code, msg, null);
    }
}
