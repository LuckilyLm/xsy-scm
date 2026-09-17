package com.xsy.scm.admin.module.business.customer.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户折扣率 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerDiscountVO {

    @Schema(description = "主键ID")
    private Long discountId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "折扣范围：1 统一折扣，2 按商品，3 按分类")
    private Integer scopeType;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "折扣率（0~1）")
    private BigDecimal discountRate;

    @Schema(description = "状态：1 生效，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
