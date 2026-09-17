package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品-供应商关系 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductSupplierVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "供应价（不含税）")
    private BigDecimal supplyPrice;

    @Schema(description = "是否默认供应商")
    private Boolean defaultFlag;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
