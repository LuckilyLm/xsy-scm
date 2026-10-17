package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

public final class PurchaseInquiryVO {
    private PurchaseInquiryVO() {}

    @Data
    public static class Inquiry {
        private Long id;
        private Long batchId;
        private Long warehouseId;
        private Long purchaserId;
        private Long skuId;
        private String skuCodeSnapshot;
        private String productNameSnapshot;
        private String demandUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal requiredQuantity;
        private OffsetDateTime deadlineAt;
        private String status;
        private Integer version;
        private Long acceptedQuoteId;
        private OffsetDateTime acceptedAt;
        private String acceptedBy;
        private OffsetDateTime createdAt;
        private String createdBy;
    }

    @Data
    public static class Quote {
        private Long id;
        private Long inquiryId;
        private Long supplierId;
        private String supplierNameSnapshot;
        private Integer revision;
        private String purchaseUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitPrice;
        private OffsetDateTime validUntil;
        private String remark;
        private OffsetDateTime recordedAt;
        private String recordedBy;
    }

    @Data
    public static class Candidate {
        private Long skuId;
        private String skuCode;
        private String productName;
        private String demandUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal requiredQuantity;
        private Long existingInquiryId;
    }

    public record BatchPage(List<Inquiry> records, long total) {}

    public record HistoryPage(List<Quote> records, boolean hasMore, Long nextBeforeId) {}

    public record Detail(Inquiry inquiry, List<Quote> currentOffers, List<Quote> quotes,
            boolean hasMoreQuotes, Long nextQuoteBeforeId) {}
}
