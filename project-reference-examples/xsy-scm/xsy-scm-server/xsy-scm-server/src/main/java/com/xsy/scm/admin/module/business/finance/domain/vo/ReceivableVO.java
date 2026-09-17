package com.xsy.scm.admin.module.business.finance.domain.vo;

import com.xsy.scm.admin.module.business.finance.constant.ReceivableStatusEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应收单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ReceivableVO {

    @Schema(description = "应收单ID")
    private Long receivableId;

    @Schema(description = "应收单号")
    private String receivableNo;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "下单客户ID")
    private Long customerId;

    @Schema(description = "结算客户ID")
    private Long settleCustomerId;

    @Schema(description = "结算方式")
    private Integer settleType;

    @Schema(description = "应收金额（不含税）")
    private BigDecimal amount;

    @Schema(description = "已收金额（不含税）")
    private BigDecimal receivedAmount;

    @Schema(description = "待收余额（不含税）")
    private BigDecimal balanceAmount;

    @Schema(description = "到期日")
    private LocalDateTime dueTime;

    @SchemaEnum(ReceivableStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
