package net.lab1024.sa.admin.module.scm.product.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 商品导出行：一行一个 SKU，覆盖列表可见主档字段 + SKU + 分类 + 标签 + 计量单位 + 更新导入定位键。
 * 只用于导出，字段顺序即列顺序，改列需同步导出设置说明。
 */
@Data
public class ProductExportExcelVO {
    /** 更新导入模板的四个定位键列，顺序与 ProductImportService.UPDATE_HEADERS 一致。 */
    @ExcelProperty("SPU ID")
    private String spuId;
    @ExcelProperty("SPU版本")
    private String spuVersion;
    @ExcelProperty("SKU ID")
    private String skuId;
    @ExcelProperty("SKU版本")
    private String skuVersion;
    @ExcelProperty("SPU编码")
    private String spuCode;
    @ExcelProperty("商品名称")
    private String spuName;
    @ExcelProperty("别名")
    private String alias;
    @ExcelProperty("分类")
    private String categoryPath;
    @ExcelProperty("助记码")
    private String mnemonicCode;
    @ExcelProperty("品牌")
    private String brandName;
    @ExcelProperty("产地")
    private String origin;
    @ExcelProperty("储存方式")
    private String storageMethod;
    @ExcelProperty("主档状态")
    private String masterStatus;
    @ExcelProperty("商品上下架")
    private String spuStatus;
    @ExcelProperty("标签")
    private String tagNames;
    @ExcelProperty("SKU编码")
    private String skuCode;
    @ExcelProperty("条码")
    private String barcode;
    @ExcelProperty("规格名称")
    private String specName;
    @ExcelProperty("销售单位")
    private String saleUnit;
    @ExcelProperty("商品类型")
    private String productType;
    @ExcelProperty("市场价")
    private String marketPrice;
    @ExcelProperty("SKU上下架")
    private String skuStatus;
    @ExcelProperty("默认SKU")
    private String defaultFlag;
    @ExcelProperty("排序")
    private String sortOrder;
}
