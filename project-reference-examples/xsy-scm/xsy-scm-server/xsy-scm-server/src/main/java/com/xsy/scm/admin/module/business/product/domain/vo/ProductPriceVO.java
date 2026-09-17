package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品价格 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductPriceVO {

    @Schema(description = "价格ID")
    private Long priceId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID，为空表示按商品定价")
    private Long skuId;

    @Schema(description = "价格类型：1 基础价，2 客户分级价，3 时价，4 协议价")
    private Integer priceType;

    @Schema(description = "客户分级ID，分级价使用")
    private Long customerLevelId;

    @Schema(description = "指定客户ID，协议价使用")
    private Long customerId;

    @Schema(description = "单价（不含税）")
    private BigDecimal price;

    @Schema(description = "生效时间，时价使用")
    private LocalDateTime effectiveTime;

    @Schema(description = "失效时间")
    private LocalDateTime expireTime;

    @Schema(description = "状态：1 生效，2 失效")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
