package net.lab1024.sa.base.module.support.message.constant;


import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;


/**
 * 消息类型
 *
 */
@Getter
@AllArgsConstructor
public enum MessageTypeEnum implements BaseEnum {

    MAIL(1, "站内信"),

    ORDER(2, "订单"),

    /**
     * 库存报损报溢单据消息：{@code dataId} 为报损报溢单 id，前端据此跳回目标单据。
     * 新增 SCM 业务消息类型时同步前端 {@code message-const.ts} 的数值镜像，否则类型列渲染为空。
     */
    SCM_INVENTORY_LOSS_GAIN(3, "库存报损报溢"),

    /**
     * 库存预警阈值通知：{@code dataId} 为预警阈值配置 id，前端据此跳到该条预警。
     * 新增 SCM 业务消息类型时同步前端 {@code message-const.ts} 与
     * {@code message-business-link.ts}，否则类型列渲染为空、消息点不出去。
     */
    SCM_INVENTORY_WARNING(4, "库存预警"),
    ;

    private final Integer value;

    private final String desc;
}
