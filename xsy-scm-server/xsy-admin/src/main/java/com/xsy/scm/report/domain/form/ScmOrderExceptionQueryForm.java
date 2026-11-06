package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.constant.ScmOrderExceptionTypeEnum;
import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmOrderExceptionQueryForm extends PageParam implements ScmReportDateFilter {
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
    private ScmOrderExceptionTypeEnum exceptionType;
    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;
    @Size(max = 120, message = "关键字不能超过120个字符")
    private String keyword;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    public Long getPageSize() {
        return super.getPageSize();
    }

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "ScmOrderExceptionFilter")
    public static class Filter extends ScmOrderExceptionQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
