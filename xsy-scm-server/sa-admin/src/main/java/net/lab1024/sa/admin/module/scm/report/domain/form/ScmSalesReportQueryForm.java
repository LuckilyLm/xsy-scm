package net.lab1024.sa.admin.module.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportDateFilter;

/**
 * 销售分析页的共用筛选条件（按商品 / 按分类 / 按客户 / 按销售员 / 订单明细）。
 *
 * <p><b>日期是「确认日期」</b>：R0 的销售统计主口径是 {@code CONFIRMED + confirmed_at +
 * settlement_*}，不是创建时间，也不是下单金额。
 *
 * <p><b>没有仓库筛选</b>：{@code sales_order} 上没有仓库归属列，订单可以跨仓履约，
 * 现在加一个仓筛选只能靠猜，因此首期不提供（计划 §6.1 的「仅当事实可靠」条件不成立）。
 *
 * <p>五个维度共用一个表单：字段互为可选，页面切 Tab 不串条件是前端职责，
 * 后端只保证「同一筛选在不同维度下口径一致」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmSalesReportQueryForm extends PageParam implements ScmReportDateFilter {

    /** 确认日期起（闭）。 */
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    /** 确认日期止（闭）。 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long customerId;

    /** 销售员，对应 {@code sales_order.seller_id}；{@code null} 表示不过滤。 */
    private Long sellerId;

    /** 订单来源，取值同 {@code ck_sales_order_source}。 */
    private String orderSource;

    /** 分类节点，命中该节点及其所有子孙分类（{@code product_category} 固定三级，无 path 列）。 */
    private Long categoryId;

    /** 商品名称 / SPU 编码 / SKU 编码模糊匹配。 */
    private String keyword;
}
