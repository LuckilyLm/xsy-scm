package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.xsy.scm.common.json.ScmOperatorSnapshotSerializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class OrderOperationLogVO {
    private Long logId;
    private Long orderId;
    private String operationType;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
    private String reason;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> beforeData;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> afterData;
    private OffsetDateTime createdAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    private String operatorName;
}
