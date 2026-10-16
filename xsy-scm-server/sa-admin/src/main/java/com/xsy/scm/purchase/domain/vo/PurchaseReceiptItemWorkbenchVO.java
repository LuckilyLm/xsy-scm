package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/** 收货工作台按商品规格和采购单位汇总待收量；不同单位不能相加。已收量与待收量从原采购和收货记录计算，不新增第二套收货事实。 */
@Data
public class PurchaseReceiptItemWorkbenchVO {

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private String purchaseUnit;

    /**
     * STANDARD / NON_STANDARD，沿用采购行的商品类型快照。
     */
    private String productType;

    /**
     * 命中的待收采购单数（{@code COUNT(DISTINCT purchase_order_id)}）。
     */
    private Long orderCount;

    /**
     * 命中的采购行数。
     */
    private Long lineCount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal pendingQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
}
