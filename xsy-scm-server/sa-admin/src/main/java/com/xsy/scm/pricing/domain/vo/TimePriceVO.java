package com.xsy.scm.pricing.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 时价列表行。 */
@Data
public class TimePriceVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String skuName;
    private String saleUnit;
    private BigDecimal unitPrice;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String remark;
    private Integer version;
    private String createdBy;
    private OffsetDateTime createdAt;

    /** 当前时刻是否落在生效区间内。 */
    private Boolean effectiveNow;
}
