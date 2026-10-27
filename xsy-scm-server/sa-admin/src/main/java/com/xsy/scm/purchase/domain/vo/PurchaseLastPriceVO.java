package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品规格最近一条已提交采购行，只读供其它域参考。
 */
@Data
public class PurchaseLastPriceVO {

    private Long skuId;

    private BigDecimal purchasePrice;

    /** 采购价对应的单位；与销售单位不一致时，定价侧不能直接把它当销售单位成本使用。 */
    private String purchaseUnit;

    private String purchaseOrderNo;

    private OffsetDateTime submittedAt;

    private String supplierName;
}
