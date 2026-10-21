package com.xsy.scm.device.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 设备接入域使用 41170–41199 错误码，与分拣域的 41120–41133 及 41870–41873 保持分离。
 *
 * <p>
 * 设备端会把这些码原样显示给操作员，因此 msg 必须是现场看得懂的说明，不能暴露内部实现。
 */
@Getter
@RequiredArgsConstructor
public enum ScaleDeviceErrorCode implements ScmErrorCode {

    /** 设备编号未登记，或已被撤销。 */
    DEVICE_NOT_FOUND(41170, "设备未登记或已被撤销，请联系管理员"),

    /** 设备已登记但未激活，或处于暂停状态。 */
    DEVICE_NOT_ACTIVE(41171, "设备当前状态不允许接入，请重新激活"),

    /** 配对码不存在、已过期或已被使用。 */
    PAIRING_CODE_INVALID(41172, "配对码无效或已过期，请向管理员重新索取"),

    /** 设备凭证缺失、过期或与请求设备不一致。 */
    DEVICE_UNAUTHORIZED(41173, "设备凭证无效，请重新激活"),

    /** 员工授权会话缺失、过期或已结束。 */
    OPERATOR_SESSION_INVALID(41174, "员工授权已失效，请重新登录"),

    /** 请求的员工会话不属于当前设备。 */
    OPERATOR_SESSION_DEVICE_MISMATCH(41175, "员工授权与当前设备不匹配，请重新登录"),

    /** 任务不存在，或不在设备绑定仓库与员工授权范围内。 */
    JOB_NOT_FOUND(41176, "分拣任务不存在或不在授权范围内"),

    /** 任务状态已变化（完成、取消、重开），不允许继续作业。 */
    JOB_STATE_INVALID(41177, "任务状态已变化，请刷新任务列表"),

    /** 同一任务已被另一台设备或另一位员工持有有效租约。 */
    JOB_LEASED_BY_OTHER(41178, "该任务已被其他设备领取，请刷新后重试"),

    /** 设备绑定仓库与任务仓库不一致。 */
    DEVICE_WAREHOUSE_MISMATCH(41179, "设备未绑定该任务的仓库，不能领取"),

    /** 员工不是该任务的受指派分拣人。 */
    OPERATOR_NOT_ASSIGNEE(41180, "当前员工不是该任务的分拣人"),

    /** 协议或 Agent 版本超出服务端支持范围。 */
    PROTOCOL_VERSION_UNSUPPORTED(41181, "设备程序版本过旧，请升级后再接入"),

    /** 设备编号已被登记；编号不复用，避免历史读数指向另一台秤。 */
    DEVICE_ALREADY_REGISTERED(41182, "设备编码已登记，请核对铭牌或联系管理员"),

    /** 员工不存在或已停用，不能签发操作会话。 */
    OPERATOR_INVALID(41183, "操作员工不存在或已停用");

    private final int code;
    private final String msg;
}
