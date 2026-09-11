package com.xianshuyuan.scm.marketing.row;

import lombok.Data;

import java.math.BigDecimal;

/**
 * "再来一单"条目行。直接取订单明细快照，避免商品改名或下架后再来一单出现歧义。
 */
@Data
public class ReorderItemRow {

    private Long skuId;
    private String productName;
    private String skuCode;
    private String specName;
    private String saleUnit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
