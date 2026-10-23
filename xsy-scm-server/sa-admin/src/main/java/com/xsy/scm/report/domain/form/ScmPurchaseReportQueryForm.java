package com.xsy.scm.report.domain.form;

import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.report.support.ScmReportDateFilter;

/**
 * 采购分析按提交日期统计已提交的有效采购单，不包含草稿和已取消单据。分页端点与概览端点共用日期和业务筛选，分页参数只用于分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmPurchaseReportQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long supplierId;

    private Long purchaserId;

    private Long warehouseId;

    /**
     * 单个采购状态，仅在四个「已提交」状态内进一步收窄；传其他值即查不到数据。
     */
    private String status;

    /**
     * 商品名称 / SPU 编码 / SKU 编码模糊匹配。
     */
    private String keyword;

    /**
     * 价格波动维度：按 SKU 收窄，避免把不同单位的价格画进同一条线。
     */
    private Long skuId;

    @Size(max = 30, message = "最多选择30个导出字段")
    private List<String> exportColumnKeys;
    @Size(max = 8, message = "最多设置8个自定义筛选条件")
    private List<Condition> advancedFilters;
}
