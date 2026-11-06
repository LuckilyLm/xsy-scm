package com.xsy.scm.metrics;

import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.purchase.domain.form.PurchaseOrderCancelForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.report.domain.form.ScmOverviewReportQueryForm;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.domain.vo.ReportOverviewVO;
import com.xsy.scm.report.service.OverviewReportService;
import com.xsy.scm.report.service.PurchaseReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTest {

    @Nested
    @DisplayName("经营指标单一来源与确认规则（PG IT）")
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
            employee.setActualName("经营指标规则 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
        }

        @Test
        @DisplayName("销售额按确认时间归属：只挪创建时间不影响今日销售额，挪确认时间才影响")
        void salesAmountBelongsToConfirmedAt() {
            Long sku = newOnShelfSku("MX");
            Long customer = newCustomer();
            Long orderId = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
            // 用这一单自己的两列做基准：下单金额与结算金额是两个不同的列，互相替代会让断言算错。
            BigDecimal settlement = jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                    BigDecimal.class, orderId);
            BigDecimal ordered = jdbc.queryForObject("SELECT ordered_total_amount FROM xsy_sales_order WHERE id = ?",
                    BigDecimal.class, orderId);
            assertThat(ordered.signum()).as("造数必须让这一单有下单金额，否则创建轴的断言会变成空转").isPositive();
            assertThat(settlement.signum()).as("同上").isPositive();

            ScmDataScopeContext scope = dataScopeService.resolve();
            SalesMetrics before = metricsService.todaySales(scope);

            // 只把创建时间挪到昨天：这一单变成「昨天创建、今天确认」。
            // 销售额与订单数走确认轴，都不受影响；下单金额走创建轴，少掉这一单。
            assertThat(jdbc.update("UPDATE xsy_sales_order SET created_at = now() - interval '1 day' WHERE id = ?", orderId))
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
            assertThat(jdbc.update("UPDATE xsy_sales_order SET confirmed_at = now() - interval '1 day' WHERE id = ?", orderId))
                    .isEqualTo(1);
            evictMybatisCache();
            SalesMetrics afterConfirmedMove = metricsService.todaySales(scope);
            assertThat(afterConfirmedMove.todayOrderCount()).as("订单数按确认轴，确认时间挪走后才应少这一单")
                    .isEqualTo(before.todayOrderCount() - 1);
            assertThat(afterConfirmedMove.todaySettlementAmount()).as("销售额同上")
                    .isEqualByComparingTo(before.todaySettlementAmount().subtract(settlement));
        }

        @Test
        @DisplayName("今日 KPI 与趋势序列最后一点同规则（大屏环比依赖这一点）")
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
            BigDecimal settlement = jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                    BigDecimal.class, orderId);
            // 昨天 23:50 创建、今天 09:00 确认。按创建轴归属会落到昨天，按确认轴归属才是今天。
            jdbc.update("UPDATE xsy_sales_order SET"
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

    @Nested
    @DisplayName("采购指标规则：提交轴 + 已提交状态（PG IT）")
    class ScmPurchaseMetricsConsistencyPgIT extends ScmW5PgITBase {
        private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

        @Autowired
        private ScmBusinessMetricsService metricsService;

        @Autowired
        private ScmDataScopeService dataScopeService;

        @Autowired
        private PurchaseReportService purchaseReportService;

        @BeforeEach
        void loginAsAdministrator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("采购规则 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
        }

        @Test
        @DisplayName("采购额按提交时间归属：挪创建时间不影响，挪提交时间才影响")
        void purchaseAmountBelongsToSubmittedAt() {
            Long skuId = newOnShelfSku("PQA");
            Long supplierId = newPurchasableSupplier("PQA", skuId);
            PurchaseOrderVO draft = createDraftOrder("PQA", supplierId, skuId, "1.0000", "100.0000");
            submitOrder(draft.getId());
            BigDecimal amount = jdbc.queryForObject("SELECT total_amount FROM xsy_purchase_order WHERE id = ?",
                    BigDecimal.class, draft.getId());

            ScmDataScopeContext scope = dataScopeService.resolve();
            BigDecimal before = metricsService.todayPurchase(scope).todayPurchaseAmount();

            // 变成「昨天 23:50 创建、今天 09:00 提交」：采购额按提交轴，仍算今天。
            jdbc.update("UPDATE xsy_purchase_order SET created_at ="
                    + " (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '10 minutes')"
                    + " AT TIME ZONE 'Asia/Shanghai' WHERE id = ?", draft.getId());
            evictMybatisCache();
            assertThat(metricsService.todayPurchase(scope).todayPurchaseAmount())
                    .as("采购额按提交轴，创建时间挪到昨天不应影响它").isEqualByComparingTo(before);

            // 再把提交时间也挪到昨天：采购额这才少掉这一单。
            jdbc.update("UPDATE xsy_purchase_order SET submitted_at ="
                    + " (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '10 minutes')"
                    + " AT TIME ZONE 'Asia/Shanghai' WHERE id = ?", draft.getId());
            evictMybatisCache();
            assertThat(metricsService.todayPurchase(scope).todayPurchaseAmount())
                    .as("采购额按提交轴，提交时间挪走后才应少掉这一单")
                    .isEqualByComparingTo(before.subtract(amount));
        }

        @Test
        @DisplayName("新建采购单即已提交，直接计入采购额")
        void createdOrderCountsImmediately() {
            Long skuId = newOnShelfSku("PQB");
            Long supplierId = newPurchasableSupplier("PQB", skuId);
            ScmDataScopeContext scope = dataScopeService.resolve();
            PurchaseMetrics before = metricsService.todayPurchase(scope);

            // 采购单草稿态已下线：创建即提交，不再存在「草稿不计入」这一步。
            createDraftOrder("PQB", supplierId, skuId, "1.0000", "200.0000");
            evictMybatisCache();

            PurchaseMetrics after = metricsService.todayPurchase(scope);
            assertThat(after.todayPurchaseAmount()).as("新建即提交，金额立即进入今天采购额")
                    .isEqualByComparingTo(before.todayPurchaseAmount().add(new BigDecimal("200.0000")));
            assertThat(after.todayPurchaseOrderCount()).as("单数同理").isEqualTo(before.todayPurchaseOrderCount() + 1);
        }

        @Test
        @DisplayName("提交后又取消：退出采购额（当前有效规则）")
        void cancelledOrderDropsOutOfPurchaseAmount() {
            Long skuId = newOnShelfSku("PQC");
            Long supplierId = newPurchasableSupplier("PQC", skuId);
            PurchaseOrderVO draft = createDraftOrder("PQC", supplierId, skuId, "1.0000", "300.0000");
            submitOrder(draft.getId());
            BigDecimal amount = jdbc.queryForObject("SELECT total_amount FROM xsy_purchase_order WHERE id = ?",
                    BigDecimal.class, draft.getId());

            ScmDataScopeContext scope = dataScopeService.resolve();
            BigDecimal afterSubmit = metricsService.todayPurchase(scope).todayPurchaseAmount();

            PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
            cancel.setId(draft.getId());
            cancel.setVersion(reloadOrder(draft.getId()).getVersion());
            cancel.setCancelReason("规则 IT：提交后取消");
            purchaseOrderService.cancel(cancel, prefix + ":cancel:" + draft.getId());
            evictMybatisCache();

            assertThat(metricsService.todayPurchase(scope).todayPurchaseAmount())
                    .as("取消后退出履约链路，采购额回落到提交前的水平")
                    .isEqualByComparingTo(afterSubmit.subtract(amount));
        }

        @Test
        @DisplayName("收货单：草稿不计入，确认后才算「今天收了多少货」")
        void receiptCountsOnlyAfterConfirm() {
            Long skuId = newOnShelfSku("PQD");
            Long supplierId = newPurchasableSupplier("PQD", skuId);
            PurchaseOrderVO draft = createDraftOrder("PQD", supplierId, skuId, "3.0000", "50.0000");
            submitOrder(draft.getId());

            ScmDataScopeContext scope = dataScopeService.resolve();
            long before = metricsService.todayPurchase(scope).todayReceiptCount();

            PurchaseReceiptVO created = createReceipt(draft.getId());
            evictMybatisCache();
            assertThat(metricsService.todayPurchase(scope).todayReceiptCount())
                    .as("草稿收货单还没提交，不算今天收了多少货").isEqualTo(before);

            PurchaseReceiptVO receipt = reloadReceipt(created.getId());
            PurchaseReceiptItemVO line = receipt.getItems().getFirst();
            purchaseReceiptService.confirm(
                    confirmForm(receipt.getId(), receipt.getVersion(), receiptLine(line.getId(), line.getVersion(),
                            "3.0000")),
                    prefix + ":confirm:" + receipt.getId());
            evictMybatisCache();

            assertThat(metricsService.todayPurchase(scope).todayReceiptCount())
                    .as("确认后才计入").isEqualTo(before + 1);
        }

        @Test
        @DisplayName("大屏采购 KPI、趋势最后一点、报表采购概览三者同数")
        void screenTrendAndReportAgreeOnPurchase() {
            Long skuId = newOnShelfSku("PQE");
            Long supplierId = newPurchasableSupplier("PQE", skuId);
            PurchaseOrderVO draft = createDraftOrder("PQE", supplierId, skuId, "1.0000", "66.0000");
            submitOrder(draft.getId());
            evictMybatisCache();

            ScmDataScopeContext scope = dataScopeService.resolve();
            PurchaseMetrics kpi = metricsService.todayPurchase(scope);
            TrendMetrics trend = metricsService.trend("7d", scope);

            int last = trend.purchaseAmounts().size() - 1;
            assertThat(trend.fullDates().get(last)).isEqualTo(LocalDate.now(BUSINESS_ZONE).toString());
            assertThat(trend.purchaseAmounts().get(last)).as("趋势末点与大屏 KPI 必须同规则")
                    .isEqualByComparingTo(kpi.todayPurchaseAmount());
            assertThat(trend.purchaseOrders().get(last)).as("单数同理").isEqualTo(kpi.todayPurchaseOrderCount());

            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            ScmPurchaseReportQueryForm form = new ScmPurchaseReportQueryForm();
            form.setPageNum(1L);
            form.setPageSize(10L);
            form.setStartDate(today);
            form.setEndDate(today);
            PurchaseReportVO.Overview overview = purchaseReportService.overview(form);
            assertThat(overview).isNotNull();
            assertThat(overview.getSubmittedAmount()).as("报表采购概览与大屏必须同一个数")
                    .isEqualByComparingTo(kpi.todayPurchaseAmount());
            assertThat(overview.getSubmittedOrderCount()).as("单数同理").isEqualTo(kpi.todayPurchaseOrderCount());
        }
    }
}
