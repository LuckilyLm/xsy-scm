package com.xsy.scm.mall.order.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 商城订单详情里的一格配送进度节点。
 *
 * <p>
 * {@code done} 由服务端按「对应的正式事实是否存在」给出，客户端只负责渲染，不再自己推导进度； 未发生的节点 {@code time} 为空。文案也由服务端给，避免客户端拼业务口径。
 */
@Data
public class MallOrderProgressNodeVO {
    /**
     * 节点标识：SUBMITTED / CONFIRMED / DISPATCHED / SIGNED / CANCELLED
     */
    private String code;
    private String text;
    /**
     * 补充说明：异常签收原因、取消原因等，可空
     */
    private String detail;
    private OffsetDateTime time;
    private Boolean done;
}
