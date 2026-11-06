package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseDailyQueryForm extends PageParam {
    @NotNull(message = "请选择清单日期")
    private LocalDate reportDate;
    private Long warehouseId;
    @Size(max = 100, message = "商品关键字不能超过100个字符")
    private String keyword;

    @Override
    @Min(value = 1, message = "页码必须大于零")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页数量必须大于零")
    public Long getPageSize() {
        return super.getPageSize();
    }

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "PurchaseDailyFilter")
    public static class Filter extends PurchaseDailyQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
