package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 销售退款单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SaleRefundQueryForm extends PageParam {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "退款类型：1 仅退款，2 退货退款")
    private Integer refundType;

    @Schema(description = "状态：1 待审核，2 已通过，3 已退款，4 已驳回")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
