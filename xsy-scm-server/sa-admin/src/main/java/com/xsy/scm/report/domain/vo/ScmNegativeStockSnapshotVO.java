package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

@Data
public class ScmNegativeStockSnapshotVO {
    private Long id;
    private String snapshotNo;
    private Long warehouseId;
    private String warehouseName;
    private LocalDate snapshotMonth;
    private OffsetDateTime capturedAt;
    private Long rowCount;
    private Long zeroCostRowCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal estimatedAmount;
    private String remark;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    private JsonNode rows;
    @JsonIgnore
    private String payloadJson;
}
