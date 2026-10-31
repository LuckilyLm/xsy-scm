package com.xsy.scm.purchase.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

@Data
public class PurchaseAccountVO {
    private Long id;
    private String accountNo;
    private Long purchaseOrderId;
    private Long supplierId;
    private String supplierNameSnapshot;
    private Long warehouseId;
    private String remark;
    private OffsetDateTime confirmedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String confirmedBy;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Long accountId;
        private Long purchaseOrderItemId;
        private Long skuId;
        private String productNameSnapshot;
        private String skuNameSnapshot;
        private String unitSnapshot;
        @JsonSerialize(using = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class)
        private BigDecimal unitPrice;
        @JsonSerialize(using = ScmFixedScale4Serializer.class)
        private BigDecimal amount;
    }
}
