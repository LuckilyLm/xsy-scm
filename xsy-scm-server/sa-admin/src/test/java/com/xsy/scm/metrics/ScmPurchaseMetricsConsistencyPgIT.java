package com.xsy.scm.metrics;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.purchase.domain.form.PurchaseOrderCancelForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.service.PurchaseReportService;
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
 * 采购指标口径（PG IT）：提交轴 + 已提交状态。
 *
 * <p>钉住四件事，它们都不会报错、只会给出一个看起来正常的错数：
 * <ol>
 * <li>采购额按 {@code submitted_at} 归属，不按 {@code created_at}。</li>
 * <li>草稿不计入 —— 它还没进入履约链路。</li>
 * <li>取消后不计入（当前有效口径）：这是有意的，前提是「取消只发生在未收货的单上」。</li>
 * <li>收货单在确认之后才计入，草稿收货单不算「今天收了多少货」。</li>
 * </ol>
 *
 * <p>本类验的是口径，不是数据范围，因此以超管身份执行；范围收窄由 {@code ScmScreenDataScopePgIT} 负责。
 */
@DisplayName("采购指标口径：提交轴 + 已提交状态（PG IT）")
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
        employee.setActualName("采购口径 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    @Test
    @DisplayName("采购额按提交时间归属：挪创建时间不影响，挪提交时间才影响")
    void purchaseAmountBelongsToSubmittedAt() {
        Long skuId = newOnShelfSku("PQA");
        Long supplierId = newPurchasableSupplier("PQA", skuId);
        PurchaseOrderVO draft = createDraftOrder("PQA", supplierId, skuId, "1.0000", "100.0000");
        submitOrder(draft.getId());
        BigDecimal amount = jdbc.queryForObject("SELECT total_amount FROM purchase_order WHERE id = ?",
                BigDecimal.class, draft.getId());

        ScmDataScopeContext scope = dataScopeService.resolve();
        BigDecimal before = metricsService.todayPurchase(scope).todayPurchaseAmount();

        // 变成「昨天 23:50 创建、今天 09:00 提交」：采购额按提交轴，仍算今天。
        jdbc.update("UPDATE purchase_order SET created_at ="
                + " (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '10 minutes')"
                + " AT TIME ZONE 'Asia/Shanghai' WHERE id = ?", draft.getId());
        evictMybatisCache();
        assertThat(metricsService.todayPurchase(scope).todayPurchaseAmount())
                .as("采购额按提交轴，创建时间挪到昨天不应影响它").isEqualByComparingTo(before);

        // 再把提交时间也挪到昨天：采购额这才少掉这一单。
        jdbc.update("UPDATE purchase_order SET submitted_at ="
                + " (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '10 minutes')"
                + " AT TIME ZONE 'Asia/Shanghai' WHERE id = ?", draft.getId());
        evictMybatisCache();
        assertThat(metricsService.todayPurchase(scope).todayPurchaseAmount())
                .as("采购额按提交轴，提交时间挪走后才应少掉这一单")
                .isEqualByComparingTo(before.subtract(amount));
    }

    @Test
    @DisplayName("草稿采购单不计入采购额")
    void draftOrderIsNotCounted() {
        Long skuId = newOnShelfSku("PQB");
        Long supplierId = newPurchasableSupplier("PQB", skuId);
        ScmDataScopeContext scope = dataScopeService.resolve();
        PurchaseMetrics before = metricsService.todayPurchase(scope);

        createDraftOrder("PQB", supplierId, skuId, "1.0000", "200.0000");
        evictMybatisCache();

        PurchaseMetrics after = metricsService.todayPurchase(scope);
        assertThat(after.todayPurchaseAmount()).as("草稿还没进入履约链路，金额不该变")
                .isEqualByComparingTo(before.todayPurchaseAmount());
        assertThat(after.todayPurchaseOrderCount()).as("单数同理").isEqualTo(before.todayPurchaseOrderCount());
    }

    @Test
    @DisplayName("提交后又取消：退出采购额（当前有效口径）")
    void cancelledOrderDropsOutOfPurchaseAmount() {
        Long skuId = newOnShelfSku("PQC");
        Long supplierId = newPurchasableSupplier("PQC", skuId);
        PurchaseOrderVO draft = createDraftOrder("PQC", supplierId, skuId, "1.0000", "300.0000");
        submitOrder(draft.getId());
        BigDecimal amount = jdbc.queryForObject("SELECT total_amount FROM purchase_order WHERE id = ?",
                BigDecimal.class, draft.getId());

        ScmDataScopeContext scope = dataScopeService.resolve();
        BigDecimal afterSubmit = metricsService.todayPurchase(scope).todayPurchaseAmount();

        PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
        cancel.setId(draft.getId());
        cancel.setVersion(reloadOrder(draft.getId()).getVersion());
        cancel.setCancelReason("口径 IT：提交后取消");
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
        assertThat(trend.purchaseAmounts().get(last)).as("趋势末点与大屏 KPI 必须同口径")
                .isEqualByComparingTo(kpi.todayPurchaseAmount());
        assertThat(trend.purchaseOrders().get(last)).as("单数同理").isEqualTo(kpi.todayPurchaseOrderCount());

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        ScmPurchaseReportQueryForm form = new ScmPurchaseReportQueryForm();
        form.setStartDate(today);
        form.setEndDate(today);
        PurchaseReportVO.Overview overview = purchaseReportService.overview(form);
        assertThat(overview).isNotNull();
        assertThat(overview.getSubmittedAmount()).as("报表采购概览与大屏必须同一个数")
                .isEqualByComparingTo(kpi.todayPurchaseAmount());
        assertThat(overview.getSubmittedOrderCount()).as("单数同理").isEqualTo(kpi.todayPurchaseOrderCount());
    }
}
