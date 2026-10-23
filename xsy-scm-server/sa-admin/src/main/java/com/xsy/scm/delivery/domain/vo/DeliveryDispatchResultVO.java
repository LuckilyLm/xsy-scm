package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 发车结果。整条线路零实发（全部订单行 OUT_OF_STOCK）时 {@code outboundId / outboundNo} 为 null：没有货离开仓库就不该有出库单，也就是「已发车但没有单号」。
 */
@Data
public class DeliveryDispatchResultVO {
    private Long routeId;
    private String status;
    private OffsetDateTime dispatchedAt;
    private Long outboundId;
    private String outboundNo;
    /** 本次进入在途的活动订单数。 */
    private Integer orderCount;
    /** 实际生成的出库明细行数（实发为 0 的订单行不出库，因此小于等于订单行总数）。 */
    private Integer shippedLineCount;

    /**
     * 本次随车出库的赠品行数。和 {@link #shippedLineCount} 分开：赠品不挂订单行、不进应收，混在一起就要多减一次才知道送了多少行商品。没有赠品时为 0。
     */
    private Integer giftLineCount;
}
