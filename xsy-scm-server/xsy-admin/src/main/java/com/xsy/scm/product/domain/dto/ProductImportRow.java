package com.xsy.scm.product.domain.dto;

import lombok.Data;

/**
 * Excel 每行对应一个商品规格，同一商品编码的多行会聚合。所有字段先按文本读取，再由导入服务校验。
 */
@Data
public class ProductImportRow {
    private int rowNumber;
    private String templateVersion;
    private String spuId;
    private String spuVersion;
    private String skuId;
    private String skuVersion;
    private String spuCode;
    private String spuName;
    private String alias;
    private String categoryCode;
    private String mnemonicCode;
    private String brandName;
    private String origin;
    private String storageMethod;
    private String shelfLifeDays;
    private String tagCodes;
    private String spuStatus;
    private String skuCode;
    private String barcode;
    private String specName;
    private String saleUnit;
    private String productType;
    private String marketPrice;
    private String skuStatus;
    private String defaultFlag;
    private String sortOrder;
}
