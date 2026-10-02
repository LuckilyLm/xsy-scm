package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * GPS 轨迹点。
 *
 * <p>
 * 采集时间与接收时间都返回：回放按采集时间排序，而排查「这条轨迹为什么迟到了」要看接收时间。
 */
@Data
public class DeliveryGpsEventVO {

    private Long id;

    private String eventKey;

    private Long routeId;

    private Long driverId;

    private String deviceCode;

    private OffsetDateTime capturedAt;

    private OffsetDateTime receivedAt;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal longitude;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal latitude;

    private String geomCrs;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal accuracyMeters;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal speedKph;

    private String reportedBy;

    /**
     * 本次上报是否为重复事件（服务端已有同键记录）。
     *
     * <p>
     * 回给客户端而不是报错：弱网重试是常态，把重试当错误会让客户端一直重试；
     * 但也不能静默，否则「我明明上报了 10 个点，库里只有 3 个」无从解释。
     */
    private boolean duplicated;
}
