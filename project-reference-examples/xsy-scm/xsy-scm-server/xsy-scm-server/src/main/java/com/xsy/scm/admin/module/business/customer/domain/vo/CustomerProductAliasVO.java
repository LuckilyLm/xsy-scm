package com.xsy.scm.admin.module.business.customer.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户商品别名 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerProductAliasVO {

    @Schema(description = "主键ID")
    private Long aliasId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "别名")
    private String aliasName;

    @Schema(description = "别名描述")
    private String aliasDesc;

    @Schema(description = "副别名")
    private String subAliasName;

    @Schema(description = "副别名描述")
    private String subAliasDesc;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
