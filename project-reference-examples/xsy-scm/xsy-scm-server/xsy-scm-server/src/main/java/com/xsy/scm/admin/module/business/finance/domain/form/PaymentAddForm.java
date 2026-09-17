package com.xsy.scm.admin.module.business.finance.domain.form;

import com.xsy.scm.admin.module.business.finance.constant.PayChannelEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class PaymentAddForm {

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "核销目标应收单ID")
    private Long receivableId;

    @Schema(description = "客户ID")
    @NotNull(message = "客户不能为空")
    private Long customerId;

    @Schema(description = "收款金额（不含税）")
    @NotNull(message = "收款金额不能为空")
    private BigDecimal amount;

    @SchemaEnum(PayChannelEnum.class)
    @CheckEnum(message = "收款渠道错误", value = PayChannelEnum.class, required = true)
    @NotNull(message = "收款渠道不能为空")
    private Integer payChannel;

    @Schema(description = "收款时间")
    private LocalDateTime payTime;

    @Schema(description = "凭证图片")
    private String proofImage;

    @Schema(description = "备注")
    private String remark;
}
