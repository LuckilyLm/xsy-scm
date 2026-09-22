package net.lab1024.sa.admin.module.scm.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 导入错误定位到 Excel 行号 + 列 + 稳定原因码。 */
@Data
@AllArgsConstructor
public class ProductImportErrorVO {
    private int rowNumber;
    private String spuCode;
    private String column;
    private String code;
    private String message;
}
