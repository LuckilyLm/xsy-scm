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

/**
 * 回调事件：渠道投递过来的每一次通知。
 *
 * <p>
 * <b>这是回调幂等的锚点</b>：{@code (provider, provider_event_id)} 唯一。重复投递同一事件
 * 必然撞唯一索引，业务侧据此判定「已处理过」并静默返回，**不重复执行任何副作用**。
 *
 * <p>
 * 未通过验签的事件**照样落库**（{@code process_status = REJECTED}）：
 * 「有人伪造回调」必须留下证据，而不是被静默丢弃。
 *
 * <p>
 * 这张表没有 {@code version} / {@code deleted}：事实表只有追加。
 */
@Data
@TableName(value = "payment_callback_event", autoResultMap = true)
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
