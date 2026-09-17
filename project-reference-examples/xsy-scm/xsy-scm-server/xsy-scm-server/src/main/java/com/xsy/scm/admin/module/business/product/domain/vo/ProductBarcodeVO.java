package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品条码 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductBarcodeVO {

    @Schema(description = "主键ID")
    private Long barcodeId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "条形码")
    private String barcode;

    @Schema(description = "对应单位")
    private String unit;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
