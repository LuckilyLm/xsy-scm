package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/** 支付回调事件按渠道和事件 ID 唯一防重。重复通知不能重复执行；验签失败也要以拒绝状态记录，回调事件只追加。 */
@Data
@TableName(value = "xsy_payment_callback_event", autoResultMap = true)
public class PaymentCallbackEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String providerEventId;

    private String eventType;

    private Boolean signatureVerified;

    private String payloadHash;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> payload;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long transactionId;

    private String processStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;

    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;

    private OffsetDateTime createdAt;

    private String createdBy;
}
