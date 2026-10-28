package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 独立退货某商品的可取价格；{@code priced=false} 表示最近成交价与定价链都没有价格，必须人工填价。
 */
@Data
public class OrderReturnPricePreviewVO {
    private Long skuId;
    private String productName;
    private String specName;
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private String priceSource;
    private boolean priced;
}
