package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 秤读数（作业台展示与追溯）。
 *
 * <p>
 * 原始读数与接受数量都返回：两者不一致时（当前规则下不会发生，但历史数据可能有）
 * 必须能看出来，而不是只留一个「最终值」。
 */
@Data
public class SortingScaleEventVO {

    private Long id;

    private String eventKey;

    private Long taskId;

    private Long taskItemId;

    private Long skuId;

    private String skuCodeSnapshot;

    private String productNameSnapshot;

    private String deviceCode;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal rawReading;

    private String unit;

    private Boolean stableFlag;

    private OffsetDateTime capturedAt;

    private OffsetDateTime receivedAt;

    /** {@code PENDING} / {@code ACCEPTED} / {@code REJECTED}。 */
    private String status;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal acceptedQuantity;

    private OffsetDateTime acceptedAt;

    private String acceptedBy;

    private OffsetDateTime rejectedAt;

    private String rejectedBy;

    private String rejectReason;

    private Integer version;

    /**
     * 本次上报是否为重复事件（服务端已有同键记录）；不是错误。
     */
    private boolean duplicated;
}
