package com.xsy.scm.admin.module.business.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 经营大屏 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ScreenBusinessVO {

    @Schema(description = "订单量")
    private Long orderCount;

    @Schema(description = "销售额（不含税）")
    private BigDecimal salesAmount;

    @Schema(description = "待收余额（不含税）")
    private BigDecimal receivableBalance;

    @Schema(description = "客户数")
    private Long customerCount;
}
