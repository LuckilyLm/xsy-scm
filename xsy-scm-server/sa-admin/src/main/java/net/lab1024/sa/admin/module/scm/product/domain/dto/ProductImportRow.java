package net.lab1024.sa.admin.module.scm.product.domain.dto;

import lombok.Data;

/**
 * 商品导入的单个 Excel 行，一行一个 SKU，同 SPU 编码的多行在服务层聚合为一张商品。
 * 字段全部按字符串接收，数值/日期/枚举校验在 {@code ProductImportService} 里做，避免解析期丢精度。
 */
@Data
public class ProductImportRow {
    private int rowNumber;
    private String templateVersion;
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
