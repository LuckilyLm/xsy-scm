package net.lab1024.sa.admin.module.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ScmErrorCode {
    NOT_FOUND(41100, "配送记录不存在"), STATE_INVALID(41101, "线路状态不允许此操作"),
    ORDER_INELIGIBLE(41102, "订单不符合配送条件，请刷新候选订单"),
    ORDER_ASSIGNED(41103, "订单已加入配送线路，请先移除订单或取消线路"),
    LOCATION_REQUIRED(41104, "仓库及所有停靠点必须完成定位，且坐标系一致"),
    MASTER_DISABLED(41105, "仓库、司机或车辆不存在或已停用"),
    DUPLICATE(41106, "司机编码或车牌已存在"), STOP_ORDER_INVALID(41107, "停靠顺序必须完整且不重复"),
    EMPTY_ROUTE(41108, "请先加入配送订单"), LIMIT_EXCEEDED(41109, "单条线路最多支持500张订单");
    private final int code;
    private final String msg;
}
