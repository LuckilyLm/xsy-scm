package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品转换单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertVO {

    @Schema(description = "主键ID")
    private Long convertId;

    @Schema(description = "转换单号")
    private String convertNo;

    @Schema(description = "转换类型：1 整件拆零，2 组合拆分")
    private Integer convertType;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "来源：1 手工创建，2 发货差异表批量转换")
    private Integer sourceType;

    @Schema(description = "状态：1 待审核，2 已完成，3 已驳回")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
