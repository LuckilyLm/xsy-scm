package com.xsy.scm.trace.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/**
 * 免登录扫码页看到的内容。
 *
 * <p>
 * 只放「贴在这件货上就能公开」的字段：商品、批次来源、重量、脱敏后的去向、资质与报告。 内部主键、操作人、金额、批次级出库明细都不出现在这里——它们对扫码的人没有解释力，只会变成信息泄露面。
 */
@Data
public class TracePublicVO {

    private String unitCode;

    /** ACTIVE 或 VOID；作废的码仍要能查到，页面据此显示「请勿食用」。 */
    private String status;

    private String productName;

    private String skuName;

    private String packageType;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unit;

    /** 净重；未经称重时为 null，页面显示「待称重」。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netWeight;

    private String weightUnit;

    private OffsetDateTime measuredAt;

    private String lotNo;

    private String sourceLotNo;

    /** 来源供应商与收货时间：溯源的起点。 */
    private String supplierName;

    private OffsetDateTime receivedAt;

    /** 客户名脱敏后展示，例如「张**」。 */
    private String customerName;

    /** 订单号脱敏后展示，例如「SO20****0031」。 */
    private String orderNo;

    private List<TraceAttachmentVO> attachments;

    private List<TracePublicInspectionReportVO> inspectionReports;
}
