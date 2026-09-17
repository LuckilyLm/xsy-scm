package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存盘点单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class StockCheckVO {

    @Schema(description = "盘点单ID")
    private Long checkId;

    @Schema(description = "盘点单号")
    private String checkNo;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "盘点类型：1 全面盘点，2 动态盘点，3 抽盘")
    private Integer checkType;

    @Schema(description = "状态：1 待盘点，2 盘点中，3 已提交，4 已差异处理")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
