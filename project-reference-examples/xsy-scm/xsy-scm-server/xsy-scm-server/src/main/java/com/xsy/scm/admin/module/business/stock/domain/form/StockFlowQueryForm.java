package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 库存流水 分页查询表单（只读）
 *
 * @author xsy-scm
 */
@Data
public class StockFlowQueryForm extends PageParam {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "流水类型：1 采购入库，2 销售出库，3 退货入库，4 报损，5 报溢，6 盘点调整，7 规格转换出，8 规格转换入")
    private Integer flowType;

    @Schema(description = "关联业务：1 采购，2 订单，3 分拣，4 盘点，5 报损报溢，6 规格转换")
    private Integer bizType;

    @Schema(description = "方向：1 入，2 出")
    private Integer direction;
}
