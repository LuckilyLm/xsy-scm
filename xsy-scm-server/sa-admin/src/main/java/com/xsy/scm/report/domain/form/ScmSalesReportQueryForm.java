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
 * 销售分析仅统计已确认订单，使用确认日期和结算金额。订单可能跨仓履约，不提供虚构的订单仓库筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmSalesReportQueryForm extends PageParam implements ScmReportDateFilter {

    /**
     * 确认日期起（闭）。
     */
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    /**
     * 确认日期止（闭）。
     */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long customerId;

    /**
     * 销售员，对应 {@code xsy_sales_order.seller_id}；{@code null} 表示不过滤。
     */
    private Long sellerId;

    /**
     * 订单来源，取值同 {@code ck_sales_order_source}。
     */
    private String orderSource;

    /**
     * 分类节点，命中该节点及其所有子孙分类（{@code xsy_product_category} 固定三级，无 path 列）。
     */
    private Long categoryId;

    /**
     * 商品名称 / SPU 编码 / SKU 编码模糊匹配。
     */
    private String keyword;

    @Size(max = 30, message = "最多选择30个导出字段")
    private List<String> exportColumnKeys;
    @Size(max = 8, message = "最多设置8个自定义筛选条件")
    private List<Condition> advancedFilters;
}
