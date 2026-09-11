package com.xianshuyuan.scm.mall.row;

import com.xianshuyuan.scm.product.entity.ProductType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商城商品查询行。仅用于 SQL 映射，业务价格由价格解析服务补充。
 */
@Data
public class MallProductRow {

    private Long skuId;
    private Long spuId;
    private String productName;
    private String skuCode;
    private String specName;
    private Map<String, String> specValues;
    private String saleUnit;
    private ProductType productType;
    private BigDecimal marketPrice;
    private Long categoryId;
    private String categoryName;
}
