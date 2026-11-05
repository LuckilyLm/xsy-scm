package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

import lombok.Data;

/**
 * 商城「下单商品汇总」查询。
 *
 * <p>
 * 只按时间区间收窄，客户身份取自登录态；不提供客户 id 入参，避免越权读取他人历史。
 */
@Data
public class MallOrderSummaryQueryForm {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Long pageSize;

    /**
     * 统计区间起（含当天），留空表示不限。
     */
    private LocalDate startDate;

    /**
     * 统计区间止（含当天），留空表示不限。
     */
    private LocalDate endDate;
}
