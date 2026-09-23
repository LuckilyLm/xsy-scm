package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 线路「按订单」视角的一行：有效关联订单及其打印状态。
 */
@Data
public class DeliveryOrderViewVO {
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private Long stopId;
    private Integer stopSeq;
    private String address;
    private Integer itemCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderAmount;
    private Integer printCount;
    private OffsetDateTime lastPrintedAt;
    /**
     * PRINTED / UNPRINTED；订单维度不存在 PARTIAL。
     */
    private String printStatus;
}
