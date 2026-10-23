package com.xsy.scm.delivery.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 配送进度只展示线路、发车、签收和异常记录。时间都来自原始数据，不另存一份配送状态。
 */
@Data
public class DeliveryOrderProgressVO {
    private Long orderId;
    private Long routeId;
    private String routeNo;
    private String routeName;
    private LocalDate deliveryDate;
    /** 线路状态：DRAFT / PLANNED / DISPATCHED / COMPLETED / CANCELLED。 */
    private String routeStatus;
    private String warehouseNameSnapshot;
    private String driverNameSnapshot;
    private String driverPhoneSnapshot;
    private String vehicleNoSnapshot;
    private OffsetDateTime plannedDepartureTime;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime completedAt;
    /** 订单级履约状态：PENDING / IN_TRANSIT / SIGNED / EXCEPTION。 */
    private String fulfillmentStatus;
    private OffsetDateTime signedAt;
    private String signReason;
}
