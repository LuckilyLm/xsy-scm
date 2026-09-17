package com.xsy.scm.admin.module.business.order.domain.form;

import com.xsy.scm.admin.module.business.order.constant.OrderSourceEnum;
import com.xsy.scm.admin.module.business.order.constant.SettleTypeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售订单 添加表单
 *
 * <p>金额字段（下单/优惠/应付/核算）由服务端按成交价快照核算，不在表单中传入。</p>
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderAddForm {

    @Schema(description = "下单客户ID（下属单位下单时为本单位）")
    @NotNull(message = "下单客户不能为空")
    private Long customerId;

    @Schema(description = "结算客户ID（集团统一结算时为集团客户）")
    private Long settleCustomerId;

    @SchemaEnum(OrderSourceEnum.class)
    @CheckEnum(message = "订单来源错误", value = OrderSourceEnum.class, required = true)
    private Integer source;

    @SchemaEnum(SettleTypeEnum.class)
    @CheckEnum(message = "结算方式错误", value = SettleTypeEnum.class, required = true)
    private Integer settleType;

    @Schema(description = "期望配送时间")
    private LocalDateTime expectDeliveryTime;

    @Schema(description = "归属业务员ID")
    private Long sellerId;
}
