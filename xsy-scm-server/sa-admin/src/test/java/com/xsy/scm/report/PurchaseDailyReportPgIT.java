package com.xsy.scm.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.report.controller.PurchaseDailyReportController;
import com.xsy.scm.report.dao.PurchaseDailyReportDao;
import com.xsy.scm.report.domain.form.PurchaseDailyQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseDailyReportVO;
import com.xsy.scm.report.service.PurchaseDailyGenerationService;
import com.xsy.scm.report.service.PurchaseDailyQueryService;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/** 复用真实 PostgreSQL 的事务回滚夹具，验证 SQL 聚合和持久快照口径。 */
class PurchaseDailyReportPgIT extends ScmW6PgITBase {
    private static final LocalDate REPORT_DATE = LocalDate.of(2000, 1, 17);
    private static final long SKU_ID = 987_654_321L;
    private static final long WAREHOUSE_A = 987_654_322L;
    private static final long WAREHOUSE_B = 987_654_323L;
    private static final long PURCHASER_A = 987_654_324L;
    private static final long PURCHASER_B = 987_654_325L;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PurchaseDailyGenerationService generationService;
    @Autowired
    private PurchaseDailyQueryService queryService;
    @Autowired
    private PurchaseDailyReportDao reportDao;
    @Autowired
    private PurchaseDailyReportController reportController;

    @Test
    void aggregatesValidSubmittedOrdersInsideShanghaiDayAndKeepsUnitsSeparate() {
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.PARTIALLY_RECEIVED, start().plusHours(8), "kg", "3", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.RECEIVED, start().plusDays(1).minusNanos(1000), "kg", "4", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.SHORT_CLOSED, start().plusHours(12), "箱", "5", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.SUBMITTED, start().minusNanos(1000), "kg", "100", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.SUBMITTED, start().plusDays(1), "kg", "100", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.DRAFT, null, "kg", "100", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.CANCELLED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
        Long deletedOrder = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
        jdbcTemplate.update("UPDATE purchase_order SET deleted = TRUE WHERE id = ?", deletedOrder);
        Long deletedItem = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
        jdbcTemplate.update("UPDATE purchase_order_item SET deleted = TRUE WHERE purchase_order_id = ?", deletedItem);
        evictMybatisCache();

        assertThat(generationService.generate(REPORT_DATE)).isTrue();
        List<PurchaseDailyReportVO.ProductRow> rows = products(ScmValueScope.all(), ScmValueScope.all(), null);
        assertThat(rows).hasSize(2);
        PurchaseDailyReportVO.ProductRow kilograms = rows.stream().filter(row -> "kg".equals(row.getPurchaseUnit()))
                .findFirst().orElseThrow();
        assertThat(kilograms.getOrderCount()).isEqualTo(3);
        assertThat(kilograms.getPlannedQuantity()).isEqualByComparingTo("9");
        assertThat(kilograms.getOrderAmount()).isEqualByComparingTo("18");
        assertThat(rows.stream().filter(row -> "箱".equals(row.getPurchaseUnit())).findFirst().orElseThrow()
                .getPlannedQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void repeatedExecutionPreservesSnapshotAfterSourceCancellation() throws Exception {
        Long orderId = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
        assertThat(generationService.generate(REPORT_DATE)).isTrue();
        OffsetDateTime generatedAt = reportDao.findGeneratedAt(REPORT_DATE);
        jdbcTemplate.update("UPDATE purchase_order SET status = 'CANCELLED', cancel_reason = '测试取消', "
                + "cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", orderId);
        evictMybatisCache();

        assertThat(generationService.generate(REPORT_DATE)).isFalse();
        assertThat(reportDao.findGeneratedAt(REPORT_DATE)).isEqualTo(generatedAt);
        assertThat(products(ScmValueScope.all(), ScmValueScope.all(), null).getFirst().getPlannedQuantity())
                .isEqualByComparingTo("2");
        PurchaseDailyReportVO report = queryService.query(form());
        assertThat(report.getGeneratedAt()).isNotNull();
        assertThat(report.getProducts().getTotal()).isEqualTo(1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        reportController.export(form(), response);
        assertThat(response.getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void intersectsWarehouseAndPurchaserScopesBeforeAggregation() {
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "30", WAREHOUSE_A, PURCHASER_B);
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "40", WAREHOUSE_B, PURCHASER_A);
        generationService.generate(REPORT_DATE);

        ScmValueScope warehouse = ScmValueScope.of(List.of(WAREHOUSE_A));
        ScmValueScope purchaser = ScmValueScope.of(List.of(PURCHASER_A));
        assertThat(products(warehouse, purchaser, null).getFirst().getPlannedQuantity()).isEqualByComparingTo("2");
        assertThat(products(warehouse, purchaser, WAREHOUSE_B)).isEmpty();
        assertThat(products(null, purchaser, null)).isEmpty();
        assertThat(products(warehouse, null, null)).isEmpty();
        assertThat(products(ScmValueScope.none(), purchaser, null)).isEmpty();
        assertThat(products(warehouse, ScmValueScope.none(), null)).isEmpty();
    }

    @Test
    void queryServiceKeepsPurchaserScopeWhenWarehousePermissionIsUnrestricted() {
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "30", WAREHOUSE_A, PURCHASER_B);
        order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "40", WAREHOUSE_B, PURCHASER_A);
        generationService.generate(REPORT_DATE);
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(PURCHASER_A);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
            permissions.when(() -> StpUtil.hasPermission(ScmDataScopeService.WAREHOUSE_ALL_PERM)).thenReturn(true);
            PurchaseDailyQueryForm query = form();
            query.setWarehouseId(WAREHOUSE_A);
            PurchaseDailyReportVO result = queryService.query(query);
            assertThat(result.getProducts().getList()).hasSize(1);
            assertThat(result.getProducts().getList().getFirst().getPlannedQuantity()).isEqualByComparingTo("2");
        }
    }

    @Test
    void distinguishesEmptySnapshotFromMissingSnapshotAndRejectsUnfinishedDays() {
        assertThat(queryService.query(form()).getGeneratedAt()).isNull();
        assertThat(generationService.generate(REPORT_DATE)).isTrue();
        assertThat(queryService.query(form()).getGeneratedAt()).isNotNull();
        assertThat(queryService.query(form()).getProducts().getList()).isEmpty();
        LocalDate today = LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE);
        assertThatThrownBy(() -> generationService.generate(today)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generationService.generate(today.plusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    private List<PurchaseDailyReportVO.ProductRow> products(ScmValueScope warehouses, ScmValueScope purchasers,
            Long warehouseFilter) {
        PurchaseDailyQueryForm query = form();
        query.setWarehouseId(warehouseFilter);
        return reportDao.queryProducts(new Page<>(1, 100), query, warehouses, purchasers);
    }

    private PurchaseDailyQueryForm form() {
        PurchaseDailyQueryForm form = new PurchaseDailyQueryForm();
        form.setReportDate(REPORT_DATE);
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    private OffsetDateTime start() {
        return REPORT_DATE.atStartOfDay(ScmReportTimeRangeResolver.BUSINESS_ZONE).toOffsetDateTime();
    }

    private Long order(ScmPurchaseStatusEnum status, OffsetDateTime submittedAt, String unit, String quantity,
            long warehouseId, long purchaserId) {
        String orderNo = "DAILY-" + UUID.randomUUID();
        boolean cancelled = status == ScmPurchaseStatusEnum.CANCELLED;
        boolean shortClosed = status == ScmPurchaseStatusEnum.SHORT_CLOSED;
        BigDecimal amount = new BigDecimal(quantity).multiply(new BigDecimal("2"));
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO purchase_order (order_no, supplier_id, supplier_code_snapshot, supplier_name_snapshot,
                    warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot, purchaser_id, status,
                    total_amount, submitted_at, planned_arrival_date, cancelled_at, cancel_reason,
                    short_closed_at, short_close_reason)
                VALUES (?, 1, 'SUP', '供应商', ?, 'WH', '仓库', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, orderNo, warehouseId, purchaserId, status.name(), amount, submittedAt, REPORT_DATE,
                cancelled ? start() : null, cancelled ? "取消" : null,
                shortClosed ? start() : null, shortClosed ? "短结" : null);
        jdbcTemplate.update("""
                INSERT INTO purchase_order_item (purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                    product_name_snapshot, sku_code_snapshot, sku_name_snapshot, purchase_unit_snapshot,
                    product_type_snapshot, planned_quantity, received_quantity, purchase_price, line_amount)
                VALUES (?, 1, ?, 'DAILY-SPU', '每日商品', 'DAILY-SKU', '规格', ?, 'STANDARD', ?, 0, 2, ?)
                """, orderId, SKU_ID, unit, new BigDecimal(quantity), amount);
        return orderId;
    }
}
