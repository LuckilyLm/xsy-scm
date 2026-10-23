package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单上发生过的操作类型，用于订单操作日志。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderOperationTypeEnum {

    /**
     * 新建订单。
     */
    CREATE("建单"),

    /**
     * 修改订单内容。
     */
    UPDATE("修改"),

    /**
     * 提交待确认。
     */
    SUBMIT("提交"),

    /**
     * 修改实发数量。
     */
    ACTUAL_QUANTITY("修改实发量"),

    /**
     * 确认订单。
     */
    CONFIRM("确认"),

    /**
     * 取消订单。
     */
    CANCEL("取消"),

    /**
     * 预留库存。
     */
    RESERVE_STOCK("预留库存"),

    /**
     * 登记退货。
     */
    RETURN("退货"),

    /**
     * 登记退款。
     */
    REFUND("退款"),

    /**
     * 超授信放行。
     */
    CREDIT_OVERRIDE("超授信放行"),

    /**
     * 指派处理人。
     */
    ASSIGN("指派"),

    /**
     * 重新定价。
     */
    REPRICE("重新定价");

    private final String desc;
}
