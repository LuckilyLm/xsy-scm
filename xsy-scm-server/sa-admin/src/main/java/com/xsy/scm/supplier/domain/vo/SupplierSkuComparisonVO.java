package com.xsy.scm.supplier.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

/** 同 SKU、同采购单位的供应商参考价对比；不是合同报价、成交价或入库成本。 */
@Data
public class SupplierSkuComparisonVO {

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long skuId;
    private String purchaseUnit;
    private Boolean defaultFlag;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal referencePrice;

    /** 同一采购单位内有效参考价的最低值；无报价则为 null。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lowestReferencePrice;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal priceGapAmount;

    /** 相对最低有效参考价的溢价百分数；最低价为零时未知。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal priceGapPercent;

    private Integer pricedSupplierCount;
}
