package com.xsy.scm.metrics;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.report.domain.form.ScmOverviewReportQueryForm;
import com.xsy.scm.report.domain.vo.ReportOverviewVO;
import com.xsy.scm.report.service.OverviewReportService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经营指标的单一来源与确认口径（PG IT）。
 *
 * <p>钉住三件事，它们都不会报错、只会给出一个看起来正常的错数：
 * <ol>
 * <li>销售额按 {@code confirmed_at} 归属，不按 {@code created_at} —— 否则昨天创建今天确认的单会落到昨天。</li>
 * <li>今日 KPI 与趋势序列最后一点必须同口径 —— 大屏的环比正是拿这两者相减，口径不一致会算出无意义的百分比。</li>
 * <li>首页 / 大屏 / 报表对同一区间必须给出同一个数。</li>
 * </ol>
 *
 * <p><b>本类验的是口径，不是数据范围</b>，因此以超管身份执行，让夹具造的单必然落在统计窗口内；范围收窄由
 * {@code ScmScreenDataScopePgIT} 负责。
 */
@DisplayName("经营指标单一来源与确认口径（PG IT）")
class ScmBusinessMetricsConsistencyPgIT extends ScmW6PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ScmBusinessMetricsService metricsService;

    @Autowired
    private ScmDataScopeService dataScopeService;

    @Autowired
    private OverviewReportService overviewReportService;

    @BeforeEach
    void loginAsAdministrator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("经营指标口径 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    @Test
    @DisplayName("销售额按确认时间归属：只挪创建时间不影响今日销售额，挪确认时间才影响")
    void salesAmountBelongsToConfirmedAt() {
        Long sku = newOnShelfSku("MX");
        Long customer = newCustomer();
        Long orderId = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
        // 用这一单自己的两列做基准：下单金额与结算金额是两个不同的列，互相替代会让断言算错。
        BigDecimal settlement = jdbc.queryForObject("SELECT settlement_total_amount FROM sales_order WHERE id = ?",
                BigDecimal.class, orderId);
        BigDecimal ordered = jdbc.queryForObject("SELECT ordered_total_amount FROM sales_order WHERE id = ?",
                BigDecimal.class, orderId);
        assertThat(ordered.signum()).as("造数必须让这一单有下单金额，否则创建轴的断言会变成空转").isPositive();
        assertThat(settlement.signum()).as("同上").isPositive();

        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesMetrics before = metricsService.todaySales(scope);

        // 只把创建时间挪到昨天：这一单变成「昨天创建、今天确认」。
        // 销售额与订单数走确认轴，都不受影响；下单金额走创建轴，少掉这一单。
        assertThat(jdbc.update("UPDATE sales_order SET created_at = now() - interval '1 day' WHERE id = ?", orderId))
                .isEqualTo(1);
        evictMybatisCache();
        SalesMetrics afterCreatedMove = metricsService.todaySales(scope);
        assertThat(afterCreatedMove.todayOrderCount()).as("订单数按确认轴，创建时间变化不应影响它")
                .isEqualTo(before.todayOrderCount());
        assertThat(afterCreatedMove.todaySettlementAmount()).as("销售额按确认轴，创建时间变化不应影响它")
                .isEqualByComparingTo(before.todaySettlementAmount());
        assertThat(afterCreatedMove.todayOrderedAmount()).as("下单金额按创建轴，应当少掉这一单")
                .isEqualByComparingTo(before.todayOrderedAmount().subtract(ordered));

        // 再把确认时间也挪到昨天：销售额与订单数这才少掉这一单。
        assertThat(jdbc.update("UPDATE sales_order SET confirmed_at = now() - interval '1 day' WHERE id = ?", orderId))
                .isEqualTo(1);
        evictMybatisCache();
        SalesMetrics afterConfirmedMove = metricsService.todaySales(scope);
        assertThat(afterConfirmedMove.todayOrderCount()).as("订单数按确认轴，确认时间挪走后才应少这一单")
                .isEqualTo(before.todayOrderCount() - 1);
        assertThat(afterConfirmedMove.todaySettlementAmount()).as("销售额同上")
                .isEqualByComparingTo(before.todaySettlementAmount().subtract(settlement));
    }

    @Test
    @DisplayName("今日 KPI 与趋势序列最后一点同口径（大屏环比依赖这一点）")
    void todayKpiMatchesTrendLastPoint() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesMetrics sales = metricsService.todaySales(scope);
        TrendMetrics trend = metricsService.trend("7d", scope);

        int last = trend.sales().size() - 1;
        assertThat(last).as("趋势至少要有今天与昨天两个点，否则环比算不出来").isGreaterThanOrEqualTo(1);
        assertThat(trend.fullDates().get(last)).isEqualTo(LocalDate.now(BUSINESS_ZONE).toString());
        assertThat(trend.sales().get(last)).isEqualByComparingTo(sales.todaySettlementAmount());
        assertThat(trend.orders().get(last)).isEqualTo(sales.todayOrderCount());
    }

    @Test
    @DisplayName("跨日边界：昨天创建、今天确认的单同时计入大屏与报表的今日销售额")
    void crossDayBoundaryAgreesBetweenMetricsAndReport() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        BigDecimal beforeSales = metricsService.todaySales(scope).todaySettlementAmount();

        Long sku = newOnShelfSku("MX2");
        Long customer = newCustomer();
        Long orderId = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
        BigDecimal settlement = jdbc.queryForObject("SELECT settlement_total_amount FROM sales_order WHERE id = ?",
                BigDecimal.class, orderId);
        // 昨天 23:50 创建、今天 09:00 确认。按创建轴归属会落到昨天，按确认轴归属才是今天。
        jdbc.update("UPDATE sales_order SET"
                + " created_at = (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '10 minutes')"
                + " AT TIME ZONE 'Asia/Shanghai',"
                + " confirmed_at = (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') + interval '9 hours')"
                + " AT TIME ZONE 'Asia/Shanghai' WHERE id = ?", orderId);
        evictMybatisCache();

        BigDecimal metricsToday = metricsService.todaySales(scope).todaySettlementAmount();
        ReportOverviewVO overview = overviewReportService.overview(todayForm());

        assertThat(metricsToday).as("昨天创建、今天确认的单必须计入今日销售额")
                .isEqualByComparingTo(beforeSales.add(settlement));
        assertThat(overview.getConfirmedOrderAmount()).as("跨日边界上报表与大屏仍必须同数")
                .isEqualByComparingTo(metricsToday);
    }

    @Test
    @DisplayName("报表概览与大屏对同一区间给出同一个销售额与订单数")
    void reportOverviewAgreesWithMetricsOnSameRange() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesMetrics sales = metricsService.todaySales(scope);

        ReportOverviewVO overview = overviewReportService.overview(todayForm());

        assertThat(overview).isNotNull();
        assertThat(overview.getConfirmedOrderAmount()).as("销售额：首页 / 大屏与报表必须同一个数")
                .isEqualByComparingTo(sales.todaySettlementAmount());
        assertThat(overview.getConfirmedOrderCount()).as("订单数：同上").isEqualTo(sales.todayOrderCount());
    }

    private ScmOverviewReportQueryForm todayForm() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        ScmOverviewReportQueryForm form = new ScmOverviewReportQueryForm();
        form.setStartDate(today);
        form.setEndDate(today);
        return form;
    }

}
