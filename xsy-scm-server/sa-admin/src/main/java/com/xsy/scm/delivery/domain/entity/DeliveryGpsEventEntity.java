package com.xsy.scm.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * GPS 轨迹事件。
 *
 * <p>
 * <b>两个时间分列</b>：{@code capturedAt} 是设备采集时间（设备时钟不可信、可能离线补传），
 * {@code receivedAt} 是服务端接收时间。合成一列会让「这条轨迹是什么时候到的」永久丢失，
 * 而排查轨迹缺失、乱序、时钟漂移都要靠它。
 *
 * <p>
 * {@code eventKey} 唯一：同一设备事件重复上报只落一行，重复上报按成功返回既有记录，
 * 而不是报错 —— 弱网重试是常态，把它当错误会让客户端不断重试。
 */
@Data
@TableName(value = "delivery_gps_event")
public class DeliveryGpsEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;

    private Long routeId;

    private Long driverId;

    private String deviceCode;

    private OffsetDateTime capturedAt;

    private OffsetDateTime receivedAt;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String geomCrs;

    private BigDecimal accuracyMeters;

    private BigDecimal speedKph;

    private String reportedBy;
}
