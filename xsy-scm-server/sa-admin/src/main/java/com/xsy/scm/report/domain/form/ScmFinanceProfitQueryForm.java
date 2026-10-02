package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/** Read-only sales gross-profit query over Finance and inventory facts. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinanceProfitQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "分析维度不能为空")
    private ScmFinanceProfitDimensionEnum dimension;

    @Positive(message = "客户编号必须大于0")
    private Long customerId;

    @Positive(message = "销售员编号必须大于0")
    private Long sellerId;

    @Positive(message = "商品编号必须大于0")
    private Long skuId;

    @Positive(message = "分类编号必须大于0")
    private Long categoryId;

    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;

    @Size(max = 120, message = "关键字不能超过120个字符")
    private String keyword;
}
