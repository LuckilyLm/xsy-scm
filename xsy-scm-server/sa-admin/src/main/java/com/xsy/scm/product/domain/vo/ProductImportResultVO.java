package com.xsy.scm.product.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果：没有错误才保存；保存失败整批回滚并回填错误定位信息。mode 为 CREATE 或 UPDATE。
 */
@Data
public class ProductImportResultVO {
    private String mode;
    private int totalRows;
    private int totalProducts;
    private int totalErrors;
    private int importedProducts;
    private int updatedProducts;
    private List<Long> spuIds = new ArrayList<>();
    private List<ProductImportErrorVO> errors = new ArrayList<>();
}
