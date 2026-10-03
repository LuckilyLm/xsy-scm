package com.xsy.scm.payment.domain.vo;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 回调事件（留证）。
 *
 * <p>
 * {@code processStatus = REJECTED} 的记录**必须能查**：它回答「有没有人伪造过回调」，
 * 而这正是把未验签事件也落库的理由。
 */
@Data
public class PaymentCallbackEventVO {

    private Long id;

    private String provider;

    private String providerEventId;

    /** 归一化事件名；渠道给的名字不认识时是渠道原文。 */
    private String eventType;

    private Boolean signatureVerified;

    private String payloadHash;

    private Map<String, Object> payload;

    private Long transactionId;

    private String processStatus;

    private String rejectReason;

    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;
}
