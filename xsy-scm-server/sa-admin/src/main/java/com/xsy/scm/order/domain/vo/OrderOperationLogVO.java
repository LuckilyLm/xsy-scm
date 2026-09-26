package com.xsy.scm.order.domain.vo;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.order.support.OrderJsonbTypeHandler;

@Data
public class OrderOperationLogVO {
    private Long logId;
    private Long orderId;
    private String operationType;
    private String operator;
    private String reason;
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String operatorName;
}
