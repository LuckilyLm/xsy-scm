package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 发车结果。整条线路零实发（全部订单行 OUT_OF_STOCK）时 {@code outboundId / outboundNo} 为 null ——
 * 没有实物离开仓库就不该存在一张出库单，这与客户看到「已发车但没有单号」是同一个事实的正确表达。
 */
@Data
public class DeliveryDispatchResultVO {
    private Long routeId;
    private String status;
    private OffsetDateTime dispatchedAt;
    private Long outboundId;
    private String outboundNo;
    /**
     * 本次进入在途的活动订单数。
     */
    private Integer orderCount;
    /**
     * 实际生成的出库明细行数（实发为 0 的订单行不出库，因此它小于等于订单行总数）。
     */
    private Integer shippedLineCount;
}
