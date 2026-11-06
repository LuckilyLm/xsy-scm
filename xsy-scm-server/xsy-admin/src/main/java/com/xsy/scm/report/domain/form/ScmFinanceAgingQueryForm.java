package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.constant.ScmFinanceAgingAccountTypeEnum;
import com.xsy.scm.report.constant.ScmFinanceAgingBucketEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.base.common.domain.PageParam;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinanceAgingQueryForm extends PageParam {
    @NotNull(message = "账款类型不能为空")
    private ScmFinanceAgingAccountTypeEnum accountType;
    @NotNull(message = "查询截止日不能为空")
    private LocalDate asOfDate;
    private ScmFinanceAgingBucketEnum agingBucket;
    @Positive(message = "客户编号必须大于0")
    private Long customerId;
    @Positive(message = "结算客户编号必须大于0")
    private Long settlementCustomerId;
    @Positive(message = "供应商编号必须大于0")
    private Long supplierId;
    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;
    @Size(max = 120, message = "关键字不能超过120个字符")
    private String keyword;

    public boolean isReceivable() {
        return accountType == ScmFinanceAgingAccountTypeEnum.RECEIVABLE;
    }

    public String getAgingBucketCode() {
        return agingBucket == null ? null : agingBucket.name();
    }

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "ScmFinanceAgingFilter")
    public static class Filter extends ScmFinanceAgingQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
