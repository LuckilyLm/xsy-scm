package com.xsy.scm.purchase.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

/**
 * 采购单分享页看到的任务。
 *
 * <p>
 * 只给采购员现场需要的信息：买什么、买多少、约定价多少、已经回填过什么。 不含成本口径、不含供应商结算信息，也不含其他采购单的任何数据。
 */
@Data
public class PurchaseSharePublicVO {

    private String orderNo;
    private String supplierName;
    private String purchaserName;
    private String warehouseName;
    private LocalDate plannedArrivalDate;
    private String status;
    private OffsetDateTime expiresAt;
    private List<Line> lines;

    /**
     * 采购单行 + 最近一次回填。
     */
    @Data
    public static class Line {
        private Long itemId;
        private String productName;
        private String skuName;
        private String purchaseUnit;
        private BigDecimal plannedQuantity;
        private BigDecimal receivedQuantity;
        private BigDecimal purchasePrice;
        /**
         * 最近一次回填的单价，没有则为 null。
         */
        private BigDecimal lastUnitPrice;
        /**
         * 最近一次回填的实收重量，没有则为 null。
         */
        private BigDecimal lastReceivedQuantity;
        private String lastRemark;
        private OffsetDateTime lastSubmittedAt;
    }
}
