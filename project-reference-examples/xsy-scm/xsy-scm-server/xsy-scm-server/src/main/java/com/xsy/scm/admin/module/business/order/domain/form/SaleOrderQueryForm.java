package com.xsy.scm.admin.module.business.order.domain.form;

import com.xsy.scm.admin.module.business.order.constant.OrderSourceEnum;
import com.xsy.scm.admin.module.business.order.constant.OrderStatusEnum;
import com.xsy.scm.admin.module.business.order.constant.PayStatusEnum;
import com.xsy.scm.admin.module.business.order.constant.SettleTypeEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 销售订单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderQueryForm extends PageParam {

    @Schema(description = "订单号 模糊搜索")
    @Size(max = 30, message = "搜索词最多30字符")
    private String orderNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @SchemaEnum(OrderSourceEnum.class)
    @CheckEnum(message = "订单来源错误", value = OrderSourceEnum.class, required = false)
    private Integer source;

    @SchemaEnum(SettleTypeEnum.class)
    @CheckEnum(message = "结算方式错误", value = SettleTypeEnum.class, required = false)
    private Integer settleType;

    @SchemaEnum(OrderStatusEnum.class)
    @CheckEnum(message = "订单状态错误", value = OrderStatusEnum.class, required = false)
    private Integer status;

    @SchemaEnum(PayStatusEnum.class)
    @CheckEnum(message = "支付状态错误", value = PayStatusEnum.class, required = false)
    private Integer payStatus;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
