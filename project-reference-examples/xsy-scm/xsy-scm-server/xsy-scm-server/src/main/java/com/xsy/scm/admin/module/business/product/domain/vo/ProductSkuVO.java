package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品规格 SKU 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductSkuVO {

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "所属商品ID")
    private Long productId;

    @Schema(description = "规格编码")
    private String skuNo;

    @Schema(description = "规格名称")
    private String specName;

    @Schema(description = "销售单位")
    private String unit;

    @Schema(description = "单件折算重量（kg）")
    private BigDecimal unitWeight;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
