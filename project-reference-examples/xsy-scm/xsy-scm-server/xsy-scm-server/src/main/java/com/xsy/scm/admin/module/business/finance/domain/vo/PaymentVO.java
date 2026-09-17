package com.xsy.scm.admin.module.business.finance.domain.vo;

import com.xsy.scm.admin.module.business.finance.constant.PaymentStatusEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class PaymentVO {

    @Schema(description = "收款单ID")
    private Long paymentId;

    @Schema(description = "收款单号")
    private String paymentNo;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "核销目标应收单ID")
    private Long receivableId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "收款金额（不含税）")
    private BigDecimal amount;

    @Schema(description = "收款渠道：1 现金，2 转账，3 在线支付，4 余额扣减")
    private Integer payChannel;

    @Schema(description = "收款时间")
    private LocalDateTime payTime;

    @Schema(description = "凭证图片")
    private String proofImage;

    @SchemaEnum(PaymentStatusEnum.class)
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
