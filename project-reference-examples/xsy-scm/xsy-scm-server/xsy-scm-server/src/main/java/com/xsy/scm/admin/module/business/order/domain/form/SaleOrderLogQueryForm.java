package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 订单操作日志 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderLogQueryForm extends PageParam {

    @Schema(description = "订单ID，用于定位操作日志")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "操作类型：1 创建，2 确认，3 改价，4 编辑，5 取消，6 发货，7 签收，8 核算，9 退款，10 作废")
    private Integer operateType;
}
