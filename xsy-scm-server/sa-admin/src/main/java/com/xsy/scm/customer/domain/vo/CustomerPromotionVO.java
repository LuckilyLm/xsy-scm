package com.xsy.scm.customer.domain.vo;

import java.time.OffsetDateTime;
import lombok.Data;

/** 客户专属推广二维码。 */
@Data
public class CustomerPromotionVO {

    private Long id;
    private Long customerId;
    private String customerName;
    private String code;
    private Long sellerId;
    private String sellerName;
    private OffsetDateTime revokedAt;
    private Integer scanCount;
    private String createdBy;
    private OffsetDateTime createdAt;

    /** 扫码记录（后台查看来源）。 */
    @Data
    public static class Scan {
        private Long id;
        private OffsetDateTime scannedAt;
        private Long sellerId;
        private String sellerName;
        private String clientIp;
        private String userAgent;
    }

    /** 扫码落地页看到的归属信息。 */
    @Data
    public static class Landing {
        private String customerName;
        private String sellerName;
        private OffsetDateTime scannedAt;
    }
}
