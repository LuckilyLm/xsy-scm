package com.xsy.scm.admin.module.business.customer.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户商品可见性 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerGoodsVisibleVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "可见性：1 显示，2 屏蔽")
    private Integer visibleType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
