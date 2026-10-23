package com.xsy.scm.trace.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 秤端发码结果与标签数据。
 *
 * <p>
 * 同时带 {@code unitCode} 与 {@code traceUrl}：前者用于现场核对与后台补打，后者直接编码进二维码。 链接由服务端下发，秤端不再自己拼模板，两处各拼一次迟早会分叉。
 */
@Data
public class TraceScaleUnitVO {

    private Long unitId;

    private String unitCode;

    private String publicToken;

    /** 未配置公开地址时为空串，此时标签只印码不印二维码。 */
    private String traceUrl;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netWeight;

    private String weightUnit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    /** 包装数量用的记账单位。 */
    private String unit;

    /** 重试发码时命中已有记录，原样返回同一枚码。 */
    private Boolean duplicated;
}
