package com.xsy.scm.admin.module.business.order.domain.vo;

import com.xsy.scm.admin.module.business.order.constant.OrderSourceEnum;
import com.xsy.scm.admin.module.business.order.constant.OrderStatusEnum;
import com.xsy.scm.admin.module.business.order.constant.PayStatusEnum;
import com.xsy.scm.admin.module.business.order.constant.SettleTypeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderVO {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "下单客户ID")
    private Long customerId;

    @Schema(description = "结算客户ID")
    private Long settleCustomerId;

    @SchemaEnum(OrderSourceEnum.class)
    private Integer source;

    @SchemaEnum(SettleTypeEnum.class)
    private Integer settleType;

    @Schema(description = "下单金额（不含税，快照价计算）")
    private BigDecimal totalAmount;

    @Schema(description = "优惠金额（不含税）")
    private BigDecimal discountAmount;

    @Schema(description = "应付金额（不含税）")
    private BigDecimal payableAmount;

    @Schema(description = "核算金额（不含税，按实重核算后）")
    private BigDecimal actualAmount;

    @SchemaEnum(PayStatusEnum.class)
    private Integer payStatus;

    @Schema(description = "期望配送时间")
    private LocalDateTime expectDeliveryTime;

    @Schema(description = "归属业务员ID")
    private Long sellerId;

    @SchemaEnum(OrderStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
