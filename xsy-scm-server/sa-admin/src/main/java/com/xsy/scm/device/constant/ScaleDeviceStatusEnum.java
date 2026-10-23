package com.xsy.scm.device.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备绑定状态，与 {@code xsy_scale_device.status} 的 CHECK 约束逐值对应，两侧要同时改。
 */
@Getter
@AllArgsConstructor
public enum ScaleDeviceStatusEnum {

    /** 已登记但尚未用配对码激活，此时不签发设备凭证。 */
    PENDING_ACTIVATION("待激活"),

    /** 已激活，可携带设备凭证访问任务与上报接口。 */
    ACTIVE("已激活"),

    /** 管理员暂停：保留绑定与历史，但不再签发或接受凭证。 */
    SUSPENDED("已暂停"),

    /** 已撤销：不可再领取任务，历史读数仍按原设备归属保留。 */
    REVOKED("已撤销");

    private final String desc;
}
