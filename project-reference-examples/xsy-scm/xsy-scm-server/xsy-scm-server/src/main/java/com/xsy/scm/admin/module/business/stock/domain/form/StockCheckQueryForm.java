package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 库存盘点单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckQueryForm extends PageParam {

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "盘点类型：1 全面盘点，2 动态盘点，3 抽盘")
    private Integer checkType;

    @Schema(description = "状态：1 待盘点，2 盘点中，3 已提交，4 已差异处理")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
