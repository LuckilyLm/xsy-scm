package net.lab1024.sa.admin.module.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 配送域错误码。码段占用必须现查现用（{@code grep} 全库 411xx），不要相信任何注释里的「本段空闲」。
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ScmErrorCode {
    NOT_FOUND(41100, "配送记录不存在"), STATE_INVALID(41101, "线路状态不允许此操作"),
    ORDER_INELIGIBLE(41102, "订单不符合配送条件，请刷新候选订单"),
    ORDER_ASSIGNED(41103, "订单已加入配送线路，请先移除订单或取消线路"),
    LOCATION_REQUIRED(41104, "仓库及所有停靠点必须完成定位，且坐标系一致"),
    MASTER_DISABLED(41105, "仓库、司机或车辆不存在或已停用"),
    DUPLICATE(41106, "司机编码或车牌已存在"), STOP_ORDER_INVALID(41107, "停靠顺序必须完整且不重复"),
    EMPTY_ROUTE(41108, "请先加入配送订单"), LIMIT_EXCEEDED(41109, "单条线路最多支持500张订单"),

    /** 正式司机启用前必须绑定员工，否则登录人无法映射回司机档案，线路范围收不出来。 */
    DRIVER_EMPLOYEE_REQUIRED(41113, "启用司机前必须绑定系统员工"),

    /** 绑定的员工不存在或已删除；关系列不建外键，因此只能在写入侧校验。 */
    DRIVER_EMPLOYEE_INVALID(41114, "绑定的员工不存在或已删除"),

    /** 一个员工最多绑一个活动司机（库里由部分唯一索引兜底）。 */
    DRIVER_EMPLOYEE_BOUND(41115, "该员工已绑定其他司机");
    private final int code;
    private final String msg;
}
