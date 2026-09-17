package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单操作日志 添加表单
 *
 * <p>日志类数据，仅支持写入与查询，不支持修改与删除。</p>
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderLogAddForm {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "操作类型：1 创建，2 确认，3 改价，4 编辑，5 取消，6 发货，7 签收，8 核算，9 退款，10 作废")
    @NotNull(message = "操作类型不能为空")
    private Integer operateType;

    @Schema(description = "修改前值")
    private String beforeValue;

    @Schema(description = "修改后值")
    private String afterValue;

    @Schema(description = "操作人")
    private Long operateBy;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;
}
