package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.xsy.scm.common.json.ScmOperatorSnapshotSerializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 采购操作日志。
 *
 * <p>
 * {@code purchaseOrderId} / {@code purchaseReceiptId} 的取值由 {@code operationType} 决定： {@code DEMAND_GENERATE} 两者皆空 ·
 * {@code DEMAND_ALLOCATE} 只有采购单 id · {@code RECEIPT_*} 双 id 非空。 {@code beforeData} / {@code afterData} 是全量快照。
 */
@Data
public class PurchaseOperationLogVO {
    private Long id;
    private Long purchaseOrderId;
    private Long purchaseReceiptId;
    private String operationType;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
    private String reason;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> beforeData;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> afterData;
    private OffsetDateTime createdAt;
}
