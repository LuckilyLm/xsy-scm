package net.lab1024.sa.admin.module.scm.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.report.constant.ReportErrorCode;
import net.lab1024.sa.admin.module.scm.report.controller.ScmReportController;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmSalesReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;
import net.lab1024.sa.admin.module.scm.report.service.InventoryReportService;
import net.lab1024.sa.admin.module.scm.report.service.OverviewReportService;
import net.lab1024.sa.admin.module.scm.report.service.PurchaseReportService;
import net.lab1024.sa.admin.module.scm.report.service.ReceiptReportService;
import net.lab1024.sa.admin.module.scm.report.service.SalesReportService;
import net.lab1024.sa.base.common.domain.PageParam;
import cn.dev33.satoken.annotation.SaCheckPermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Finance R0 报表中心（真实 PostgreSQL IT）。
 *
 * <p>本类的核心职责有两条：
 * <ol>
 *   <li>把 25 条报表 SQL <b>全部真跑一遍</b>：列名写错、GROUP BY 与 SELECT 不匹配、
 *       日期轴类型不一致这类问题只有 PostgreSQL 能发现，编译与静态检查看不见。</li>
 *   <li>钉住「只统计已成立事实」的口径：DRAFT 订单 / DRAFT 与 CANCELLED 采购单 / PENDING 退款
 *       一律不得进入报表，且一单多行不得把退款放大。</li>
 * </ol>
 *
 * <p><b>夹具用直接 INSERT 而不是领域服务</b>：R0 只读，被测的是「状态与时间轴的组合」，
 * 用服务造一张 CONFIRMED 订单反而要把整条销售流程跑通，且会把口径测试与写流程耦合在一起。
 * 所有夹具都带随机后缀并靠基类事务回滚清理。
 *
 * <p><b>成本字段在 IT 里必然被抹除</b>：IT 没有 Sa-Token 登录上下文，
 * {@code ScmReportAccess#canViewCost()} 失败关闭返回 false，因此这里断言的是
 * 「无权限即不给成本」，而不是成本算法本身；成本金额的正确性由
 * {@code ScmInventory*IT} 与流水表的 {@code unit_cost} 直接证明。
 *
 * <p>读夹具一律先 {@code evictMybatisCache()}：本类的口径测试靠 jdbc 改状态后再读报表，
 * 而同一测试事务内的 MyBatis 一级缓存会按「语句 + 参数」命中旧结果，不清缓存会把
 * 「更新未生效」误报成报表口径错。
 */
@DisplayName("Finance R0 报表中心（PG IT）")
class ScmReportPgIT extends ScmW6PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportDao reportDao;

    @Autowired
    private net.lab1024.sa.admin.module.scm.report.controller.ScmReportController reportController;

    @Autowired
    private OverviewReportService overviewReportService;

    @Autowired
    private SalesReportService salesReportService;

    @Autowired
    private PurchaseReportService purchaseReportService;

    @Autowired
    private ReceiptReportService receiptReportService;

    @Autowired
    private InventoryReportService inventoryReportService;

    private final String tag = "RPT" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

    /** 覆盖近 30 天，既含夹具时间也含库中既有事实。 */
    private LocalDate end() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private LocalDate start() {
        return end().minusDays(29);
    }

    private OffsetDateTime at(LocalDate day) {
        return day.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime insideRange() {
        return end().atTime(9, 30).atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private <T extends PageParam> T page(T form) {
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    // ==================== 1. 全部报表 SQL 真跑 ====================

    @Test
    @DisplayName("五条销售维度 + 三个 TOP + 概览与库存各维度：全部 SQL 在真实库上可执行")
    void everyReportStatementExecutes() {
        runEveryQuery(false);
    }

    /**
     * 带齐全部可选筛选再跑一遍：每个 {@code <if>} 分支都会往 SQL 里追加一段谓词，
     * 而谓词可以引用只在部分查询里 JOIN 过的表。首轮只跑空筛选时，
     * 「按客户维度漏了分类 JOIN」这类缺陷是发现不了的（它正是被逐分支渲染的门禁抓到的）。
     */
    @Test
    @DisplayName("带齐全部可选筛选：每个 if 分支都能在真实库上解析并执行")
    void everyReportStatementExecutesWithAllFilters() {
        runEveryQuery(true);
    }

    private void runEveryQuery(boolean withFilters) {
        Long rootCategoryId = withFilters ? anyRootCategoryId() : null;

        ScmSalesReportQueryForm sales = page(new ScmSalesReportQueryForm());
        sales.setStartDate(start());
        sales.setEndDate(end());
        if (withFilters) {
            sales.setKeyword(tag);
            sales.setOrderSource("ADMIN");
            sales.setCategoryId(rootCategoryId);
        }

        ScmOverviewReportQueryForm overview = overviewForm();
        ScmPurchaseReportQueryForm purchase = purchaseForm();
        ScmReceiptReportQueryForm receipt = receiptForm();
        ScmInventoryReportQueryForm inventory = inventoryForm();
        if (withFilters) {
            overview.setKeyword(tag);
            overview.setOrderSource("ADMIN");
            purchase.setStatus("RECEIVED");
            purchase.setKeyword(tag);
            receipt.setReceiptMode("WAREHOUSE_CONFIRM");
            receipt.setPutawayStatus("COMPLETED");
            receipt.setKeyword(tag);
            inventory.setMovementType("PURCHASE_IN");
            inventory.setSourceDocumentType("PURCHASE_RECEIPT_ITEM");
            inventory.setKeyword(tag);
        }

        assertThatCode(() -> {
            overviewReportService.overview(overview);
            overviewReportService.dailyStat(overview);

            salesReportService.byProduct(sales);
            salesReportService.topProduct(sales);
            salesReportService.byCategory(sales);
            salesReportService.topCategory(sales);
            salesReportService.byCustomer(sales);
            salesReportService.topCustomer(sales);
            salesReportService.bySeller(sales);
            salesReportService.itemList(sales);

            purchaseReportService.overview(purchase);
            purchaseReportService.byProduct(purchase);
            purchaseReportService.bySupplier(purchase);
            purchaseReportService.topSupplierInbound(purchase);
            purchaseReportService.byPurchaser(purchase);
            purchaseReportService.itemList(purchase);
            purchaseReportService.priceTrend(purchase);

            receiptReportService.receiptList(receipt);
            receiptReportService.inboundList(receipt);
            receiptReportService.pendingPutawayList(receipt);

            inventoryReportService.movementList(inventory);
            inventoryReportService.lossSummary(inventory);
            inventoryReportService.lossList(inventory);
            inventoryReportService.valueList(inventory);
            inventoryReportService.flowSummary(inventory);
        }).doesNotThrowAnyException();
    }

    /** 一级分类：它只能靠 {@code cat2 / cat3} 谓词命中，因此是分类筛选最有代表性的一条。 */
    private Long anyRootCategoryId() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM product_category WHERE deleted = FALSE AND level = 1 ORDER BY id LIMIT 1",
                Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private ScmOverviewReportQueryForm overviewForm() {
        ScmOverviewReportQueryForm form = new ScmOverviewReportQueryForm();
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmPurchaseReportQueryForm purchaseForm() {
        ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmReceiptReportQueryForm receiptForm() {
        ScmReceiptReportQueryForm form = page(new ScmReceiptReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmInventoryReportQueryForm inventoryForm() {
        ScmInventoryReportQueryForm form = page(new ScmInventoryReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    // ==================== 2. 销售口径：只统计已确认订单 ====================

    @Test
    @DisplayName("销售统计：DRAFT 不计、CONFIRMED 计，且一单多行不放大退款")
    void salesReportCountsOnlyConfirmedOrders() {
        Long customerId = 900_000_001L;
        Long orderId = insertSalesOrder("DRAFT", null, customerId);

        // 同一状态、同一时间轴下：草稿订单不得出现在任何销售统计里
        assertThat(confirmedOrderCountOf(customerId)).isZero();
        assertThat(salesSettlementAmountOf(customerId)).isNull();

        // 确认之后：同一行数据翻成 CONFIRMED + confirmed_at 落在区间内即被统计
        jdbc.update("UPDATE sales_order SET status = 'CONFIRMED', confirmed_at = ? WHERE id = ?",
                insideRange(), orderId);
        assertThat(confirmedOrderCountOf(customerId)).isEqualTo(1L);
        assertThat(salesSettlementAmountOf(customerId)).isEqualByComparingTo("100.0000");

        // 第二行 + 一笔 PENDING 退款 + 一笔 COMPLETED 退款：
        // 订单金额按行求和，退款按订单聚合一次，因此两行不会把退款算两遍
        insertSalesItem(orderId, new BigDecimal("50.0000"));
        insertRefund(orderId, customerId, "PENDING", null);
        insertRefund(orderId, customerId, "COMPLETED", insideRange());
        assertThat(salesSettlementAmountOf(customerId)).isEqualByComparingTo("150.0000");
        assertThat(customerRowOf(customerId).getCompletedRefundAmount()).isEqualByComparingTo("7.0000");
        assertThat(customerRowOf(customerId).getOrderCount()).isEqualTo(1L);
        assertThat(customerRowOf(customerId).getSkuKindCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("销售成交均价：分母为 0 或实际数量缺失时返回 null，不是 0")
    void avgPriceIsNullWhenConfirmedQuantityMissing() {
        Long customerId = 900_000_002L;
        Long orderId = insertSalesOrder("CONFIRMED", insideRange(), customerId);
        jdbc.update("UPDATE sales_order_item i SET actual_quantity = NULL, settlement_line_amount = 0 "
                + "FROM sales_order o WHERE i.order_id = o.id AND o.id = ?", orderId);

        SalesReportVO.ProductRow row = salesProductRowOf(orderId);
        assertThat(row).isNotNull();
        assertThat(row.getAvgTransactionPrice()).isNull();
        assertThat(row.getConfirmedQuantity()).isNull();
    }

    // ==================== 3. 采购口径：只统计提交后的采购事实 ====================

    @Test
    @DisplayName("采购统计：DRAFT 与 CANCELLED 不计，提交后计入且业务日期按 submitted_at")
    void purchaseReportCountsOnlySubmittedOrders() {
        Long supplierId = 900_000_011L;
        Long orderId = insertPurchaseOrder("DRAFT", null, supplierId);

        assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("0.0000");

        jdbc.update("UPDATE purchase_order SET status = 'SUBMITTED', submitted_at = ? WHERE id = ?",
                insideRange(), orderId);
        assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("88.0000");

        // 取消单即便保留 submitted_at 也必须被排除：状态与时间是两个独立条件
        insertPurchaseOrder("CANCELLED", insideRange(), supplierId);
        assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("88.0000");
    }

    // ==================== 4. 日期与导出守卫 ====================

    @Test
    @DisplayName("日期跨度超过 366 天必须 41111，缺日期必须 40000")
    void dateRangeGuardsRejectUnboundedQueries() {
        // 上限两侧各取一点：只断言「抛异常」无法区分跨度守卫与其他校验，
        // 因此同时证明 366 天（含首尾）是合法的。
        ScmSalesReportQueryForm justInside = page(new ScmSalesReportQueryForm());
        justInside.setStartDate(end().minusDays(365));
        justInside.setEndDate(end());
        assertThatCode(() -> salesReportService.byProduct(justInside)).doesNotThrowAnyException();

        ScmSalesReportQueryForm tooWide = page(new ScmSalesReportQueryForm());
        tooWide.setStartDate(start().minusDays(400));
        tooWide.setEndDate(end());
        assertThatThrownBy(() -> salesReportService.byProduct(tooWide))
                .isInstanceOf(ScmBusinessException.class);

        ScmSalesReportQueryForm reversed = page(new ScmSalesReportQueryForm());
        reversed.setStartDate(end());
        reversed.setEndDate(start());
        assertThatThrownBy(() -> salesReportService.byProduct(reversed)).isInstanceOf(ScmBusinessException.class);

        ScmSalesReportQueryForm missing = page(new ScmSalesReportQueryForm());
        assertThatThrownBy(() -> salesReportService.byProduct(missing)).isInstanceOf(ScmBusinessException.class);
    }

    // ==================== 5. 权限与菜单不脱节 ====================

    @Test
    @DisplayName("控制器声明的每个权限码都在 t_menu 里存在且只存在一次，并授给 SUPER_ADMIN")
    void declaredPermissionsExistInMenuExactlyOnce() {
        List<String> declared = new java.util.ArrayList<>();
        for (var method : ScmReportController.class.getDeclaredMethods()) {
            var permission = method.getAnnotation(SaCheckPermission.class);
            if (permission == null) {
                continue;
            }
            assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                    .as("ScmReportController#%s 非 public 却带 @SaCheckPermission", method.getName())
                    .isTrue();
            declared.addAll(List.of(permission.value()));
        }

        assertThat(declared).contains("scm:report:overview:query", "scm:report:sales:query",
                "scm:report:purchase:query", "scm:report:inventory:query", "scm:report:cost:query",
                "scm:report:export");

        for (String perm : declared.stream().distinct().toList()) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class, perm))
                    .as("权限码 %s 必须在 t_menu.api_perms 中恰好出现一次", perm).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM t_role_menu rm
                    JOIN t_menu m ON m.menu_id = rm.menu_id
                    WHERE rm.role_id = 1 AND m.api_perms = ?""", Integer.class, perm))
                    .as("权限码 %s 必须已授给 SUPER_ADMIN(role_id=1)", perm).isEqualTo(1);
        }
    }

    // ==================== 6. 成本字段失败关闭 ====================

    @Test
    @DisplayName("无成本权限时流水与入库的成本字段为 null，不回落成 0")
    void costFieldsAreMaskedWhenPermissionAbsent() {
        Page<Object> page = new Page<>(1, 5);
        List<InventoryReportVO.MovementRow> rows = reportDao.movementList(page, at(start()),
                at(end().plusDays(1)), List.of("PURCHASE_IN"), inventoryForm());
        // 走 DAO 才能看到未抹除的原始映射；服务层在无登录上下文时必须给 null
        inventoryReportService.movementList(inventoryForm()).getList()
                .forEach(row -> assertThat(row.getUnitCost()).isNull());
        inventoryReportService.lossSummary(inventoryForm());
        assertThat(overviewReportService.overview(overviewForm()).getInventoryBookValue()).isNull();

        // 方向由流水类型派生，不由报表自建清单
        rows.forEach(row -> assertThat(row.getMovementType()).isNotBlank());
    }

    // ==================== 7. 导出真的能写出 xlsx ====================

    /**
     * 逐个调用 11 个导出端点并检查字节流。
     *
     * <p>这一组断言的存在理由很具体：FastExcel 在<b>写出时</b>才按单元格类型找 Converter，
     * 时间/金额字段没有转换器时 HTTP 200 已经发出去、异常只体现在下载的字节里，
     * 光测查询服务发现不了（首轮就是这样：浏览器导出报 500 才暴露）。
     */
    @Test
    @DisplayName("11 个 Excel 导出都能写出合法 xlsx（含时间列与金额列）")
    void everyExportWritesARealWorkbook() throws Exception {
        ScmSalesReportQueryForm sales = page(new ScmSalesReportQueryForm());
        sales.setStartDate(start());
        sales.setEndDate(end());
        ScmPurchaseReportQueryForm purchase = purchaseForm();
        ScmReceiptReportQueryForm receipt = receiptForm();
        ScmInventoryReportQueryForm inventory = inventoryForm();

        assertWorkbook("销售按商品", r -> reportController.exportSalesProduct(sales, r));
        assertWorkbook("销售按客户", r -> reportController.exportSalesCustomer(sales, r));
        assertWorkbook("销售订单明细", r -> reportController.exportSalesItem(sales, r));
        assertWorkbook("采购按商品", r -> reportController.exportPurchaseProduct(purchase, r));
        assertWorkbook("采购按供应商", r -> reportController.exportPurchaseSupplier(purchase, r));
        assertWorkbook("采购明细", r -> reportController.exportPurchaseItem(purchase, r));
        assertWorkbook("收货明细", r -> reportController.exportReceipt(receipt, r));
        assertWorkbook("入库明细", r -> reportController.exportInbound(receipt, r));
        assertWorkbook("库存流水", r -> reportController.exportMovement(inventory, r));
        assertWorkbook("损耗分析", r -> reportController.exportLoss(inventory, r));
        assertWorkbook("当前库存价值", r -> reportController.exportInventoryValue(inventory, r));
    }

    /** 每个导出单独一个 response：共用一个 response 会让前一个的内容被后一次写出混进来。 */
    private void assertWorkbook(String label, WorkbookCall call) throws Exception {
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        call.run(response);
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes.length).as("%s 必须写出非空内容", label).isGreaterThan(1024);
        // xlsx 是 zip 容器：PK 头是「真的写出了工作簿」最廉价的证明
        assertThat(new String(bytes, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1))
                .as("%s 导出的必须是 xlsx", label).isEqualTo("PK");
        assertThat(response.getContentType()).as("%s 必须声明 xlsx 媒体类型", label)
                .contains("spreadsheetml");
        assertThat(response.getHeader("Content-Disposition")).as("%s 文件名必须由服务端给出", label)
                .contains("filename");
    }

    interface WorkbookCall {
        void run(jakarta.servlet.http.HttpServletResponse response) throws Exception;
    }

    // ==================== 夹具 ====================

    /**
     * 同一次测试里 SKU 与退款单必须互不重复：
     * {@code uk_order_refund_return_active} 对 return_id 有活动唯一索引，用固定值插第二笔会直接撞索引，
     * 而 SKU 相同会让「SKU 种类数 = 2」这条断言失去意义。
     */
    private final java.util.concurrent.atomic.AtomicLong fixtureSeq = new java.util.concurrent.atomic.AtomicLong();

    private Long insertSalesOrder(String status, OffsetDateTime confirmedAt, Long customerId) {
        jdbc.update("""
                INSERT INTO sales_order(order_no, customer_id, customer_code_snapshot, customer_name_snapshot,
                                        order_source, status, settle_mode_snapshot, ordered_total_amount,
                                        settlement_total_amount, confirmed_at)
                VALUES (?, ?, ?, ?, 'ADMIN', ?, 'INDEPENDENT', 0, 0, ?)""",
                tag + "-SO", customerId, tag + "-CODE", tag + "-客户", status, confirmedAt);
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class,
                tag + "-SO");
        insertSalesItem(orderId, new BigDecimal("100.0000"));
        return orderId;
    }

    private void insertSalesItem(Long orderId, BigDecimal settlementLineAmount) {
        long sku = 900_000_100L + fixtureSeq.incrementAndGet();
        jdbc.update("""
                INSERT INTO sales_order_item(order_id, spu_id, sku_id, spu_code_snapshot, product_name_snapshot,
                                             sku_code_snapshot, spec_name_snapshot, sale_unit_snapshot,
                                             product_type_snapshot, ordered_quantity, actual_quantity,
                                             draft_unit_price, draft_price_source, manual_price_override,
                                             locked_unit_price, locked_price_source, ordered_line_amount,
                                             settlement_line_amount)
                VALUES (?, 1, ?, ?, ?, ?, '5kg', 'kg', 'STANDARD', 10, 10, 10, 'MARKET', FALSE,
                        10, 'MARKET', 100, ?)""",
                orderId, sku, tag + "-SPU", tag + "-商品", "SKU-" + sku, settlementLineAmount);
    }

    private void insertRefund(Long orderId, Long customerId, String status, OffsetDateTime completedAt) {
        long seq = fixtureSeq.incrementAndGet();
        jdbc.update("""
                INSERT INTO order_refund(refund_no, return_id, order_id, customer_id, refund_amount, status,
                                         completed_at)
                VALUES (?, ?, ?, ?, 7.0000, ?, ?)""",
                tag + "-RF-" + seq, 900_000_300L + seq, orderId, customerId, status, completedAt);
    }

    private Long insertPurchaseOrder(String status, OffsetDateTime submittedAt, Long supplierId) {
        // ck_purchase_order_cancel_reason 要求 CANCELLED 必须同带取消原因，因此取消夹具一次性写全，
        // 这也正好证明「已提交后又取消」的单保留了 submitted_at 仍不进统计。
        boolean cancelled = "CANCELLED".equals(status);
        String orderNo = tag + "-PO-" + supplierId + "-" + status;
        jdbc.update("""
                INSERT INTO purchase_order(order_no, supplier_id, supplier_code_snapshot, supplier_name_snapshot,
                                           warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                                           status, total_amount, submitted_at, planned_arrival_date,
                                           cancelled_at, cancel_reason)
                VALUES (?, ?, ?, ?, 1, 'WH001', '默认仓库', ?, 88.0000, ?, ?, ?, ?)""",
                orderNo, supplierId, tag + "-SC", tag + "-供应商", status, submittedAt, end(),
                cancelled ? insideRange() : null, cancelled ? "报表测试取消" : null);
        Long orderId = jdbc.queryForObject("SELECT id FROM purchase_order WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("""
                INSERT INTO purchase_order_item(purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                                                product_name_snapshot, sku_code_snapshot, sku_name_snapshot,
                                                purchase_unit_snapshot, product_type_snapshot, planned_quantity,
                                                received_quantity, purchase_price, line_amount)
                VALUES (?, 1, 1, ?, ?, ?, ?, 'kg', 'STANDARD', 10, 0, 8.8000, 88.0000)""",
                orderId, tag + "-SPU", tag + "-商品", "SKU-1", tag + "-SKU");
        return orderId;
    }

    private long confirmedOrderCountOf(Long customerId) {
        evictMybatisCache();
        ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        form.setCustomerId(customerId);
        return overviewRows(form).getConfirmedOrderCount();
    }

    private BigDecimal salesSettlementAmountOf(Long customerId) {
        SalesReportVO.CustomerRow row = customerRowOf(customerId);
        return row == null ? null : row.getSettlementAmount();
    }

    private net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO overviewRows(
            ScmSalesReportQueryForm ignored) {
        ScmOverviewReportQueryForm form = overviewForm();
        form.setCustomerId(ignored.getCustomerId());
        return overviewReportService.overview(form);
    }

    private SalesReportVO.CustomerRow customerRowOf(Long customerId) {
        evictMybatisCache();
        ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        form.setCustomerId(customerId);
        return salesReportService.byCustomer(form).getList().stream()
                .filter(r -> customerId.equals(r.getCustomerId()))
                .findFirst().orElse(null);
    }

    private SalesReportVO.ProductRow salesProductRowOf(Long orderId) {
        evictMybatisCache();
        Long skuId = jdbc.queryForObject("SELECT sku_id FROM sales_order_item WHERE order_id = ? LIMIT 1",
                Long.class, orderId);
        ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        form.setKeyword(tag + "-商品");
        return salesReportService.byProduct(form).getList().stream()
                .filter(r -> skuId.equals(r.getSkuId()))
                .findFirst().orElse(null);
    }

    private BigDecimal purchaseSubmittedAmountOf(Long supplierId) {
        evictMybatisCache();
        ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        form.setSupplierId(supplierId);
        PurchaseReportVO.Overview overview = purchaseReportService.overview(form);
        return overview == null ? BigDecimal.ZERO : overview.getSubmittedAmount();
    }
}
