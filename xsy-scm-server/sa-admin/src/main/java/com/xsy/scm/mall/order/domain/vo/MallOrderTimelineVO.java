package com.xsy.scm.mall.order.domain.vo;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 订单处理记录的一行。
 *
 * <p>
 * 直接来自 {@code xsy_order_operation_log}，不做业务解释：{@code operationType} 是正式操作类型， 客户端只做文案映射。商城客户操作的操作者以「客户」呈现，不解析成员工姓名。
 */
@Data
public class MallOrderTimelineVO {
    private Long logId;
    private String operationType;
    /** 可读操作者：员工显示姓名，商城客户统一显示「客户」，系统操作显示「系统」 */
    private String operatorName;
    private String reason;
    private OffsetDateTime createdAt;
}
