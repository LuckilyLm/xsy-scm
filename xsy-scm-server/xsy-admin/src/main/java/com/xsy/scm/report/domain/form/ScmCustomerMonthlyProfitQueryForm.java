package com.xsy.scm.report.domain.form;

import java.util.List;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmCustomerMonthlyProfitQueryForm extends ScmFinanceProfitQueryForm {
    @Size(max = 30, message = "导出字段不能超过30列")
    private List<String> exportColumns;

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "ScmCustomerMonthlyProfitFilter")
    public static class Filter extends ScmCustomerMonthlyProfitQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
