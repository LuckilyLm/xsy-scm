package com.xsy.scm.supplier.domain.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 一家供应商的可供货汇总，供列表页的公司卡片使用。
 *
 * <p>
 * 只放「一眼能判断这家公司值不值得点进去」的数字：多少商品、多少规格、几个优先采购、参考价区间。 明细仍然走分页反查，卡片不承载明细。
 */
@Data
public class SupplierSkuSupplierSummaryVO {

    private Long supplierId;

    private String supplierName;

    private String supplierCode;

    /**
     * 可供商品数（按 SPU 去重）。
     */
    private Integer productCount;

    /**
     * 商品规格数：一行一个规格，等于关联行数。
     */
    private Integer specCount;

    /**
     * 标记为优先采购的规格数。
     */
    private Integer defaultCount;

    /**
     * 参考价区间；全部未填时两者都为空。
     */
    private BigDecimal minReferencePrice;

    private BigDecimal maxReferencePrice;
}
