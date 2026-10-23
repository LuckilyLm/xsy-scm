package com.xsy.scm.mall.order.domain.vo;

import lombok.Data;

/**
 * SKU 与其主图 file_key。订单行不存图，历史商品的图只能在展示时回读。
 */
@Data
public class MallSkuImageVO {
    private Long skuId;
    private String primaryImageKey;
}
