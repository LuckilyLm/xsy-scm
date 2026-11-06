package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 按财务与库存数据只读查询销售毛利。
 */
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

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "ScmFinanceProfitFilter")
    public static class Filter extends ScmFinanceProfitQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
