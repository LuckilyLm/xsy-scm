package com.xsy.scm.admin.module.business.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单操作日志 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderLogVO {

    @Schema(description = "日志ID")
    private Long logId;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "操作类型：1 创建，2 确认，3 改价，4 编辑，5 取消，6 发货，7 签收，8 核算，9 退款，10 作废")
    private Integer operateType;

    @Schema(description = "修改前值")
    private String beforeValue;

    @Schema(description = "修改后值")
    private String afterValue;

    @Schema(description = "操作人")
    private Long operateBy;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
