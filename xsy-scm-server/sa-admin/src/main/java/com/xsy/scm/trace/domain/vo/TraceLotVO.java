package com.xsy.scm.trace.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 批次档案与收货行批次事实的视图。 */
public final class TraceLotVO {

    private TraceLotVO() {
    }

    /** 批次档案行：写入时复用同一个类，id 由数据库回填。 */
    @Data
    public static class Lot {
        private Long id;
        private String lotNo;
        private String sourceLotNo;
        private Long supplierId;
        private Long skuId;
        private String originType;
        private Long firstReceiptItemId;
        private OffsetDateTime createdAt;
        private String createdBy;
    }

    /** 收货行上的批次事实：批次身份 + 本行分到的数量。 */
    @Data
    public static class ReceiptItemLot {
        private Long receiptItemId;
        private Long lotId;
        private String lotNo;
        private String sourceLotNo;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
    }
}
