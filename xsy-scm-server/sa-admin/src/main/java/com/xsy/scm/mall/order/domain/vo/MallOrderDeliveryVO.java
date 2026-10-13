package com.xsy.scm.mall.order.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 商城订单详情里的配送信息。
 *
 * <p>
 * 全部字段直接来自配送域的正式事实（线路 + 线路订单），商城不另存快照、不推导状态。 线路尚未排车时整体为 {@code null}，页面按「暂无配送信息」展示。
 */
@Data
public class MallOrderDeliveryVO {
    private String routeNo;
    private String routeName;
    private LocalDate deliveryDate;
    private String warehouseName;
    private String driverName;
    private String driverPhone;
    private String vehicleNo;
    /** 计划发车时刻 */
    private OffsetDateTime plannedDepartureTime;
    /** 实际发车时刻 */
    private OffsetDateTime dispatchedAt;
    /** 线路完成时刻 */
    private OffsetDateTime completedAt;
}
