package com.xsy.scm.payment.domain.vo;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 回调事件记录。
 *
 * <p>
 * {@code processStatus = REJECTED} 的记录必须能查：它回答「有没有人伪造过回调」，这也是未验签事件也要保存的理由。
 */
@Data
public class PaymentCallbackEventVO {

    private Long id;

    private String provider;

    private String providerEventId;

    /** 归一化事件名；不认识渠道给的名字时保留渠道原文。 */
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
