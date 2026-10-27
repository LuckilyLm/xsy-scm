package com.xsy.scm.order;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.vo.PurchaseLastPriceVO;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 录单参考的最近采购价读路径：只取已提交采购单、每规格一条、按提交时间取最新，并遵守采购员范围。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class OrderLastPurchasePriceIT {

    private static final List<String> COMMITTED = ScmPurchaseStatusEnum.committedNames();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PurchaseOrderItemDao purchaseOrderItemDao;

    private static final OffsetDateTime EARLY = OffsetDateTime.parse("2026-10-01T08:00:00+08:00");
    private static final OffsetDateTime LATE = OffsetDateTime.parse("2026-10-05T08:00:00+08:00");

    @Test
    void returnsLatestSubmittedLinePerSku() {
        long skuId = 8_831_000_001L;
        long older = order(ScmPurchaseStatusEnum.SUBMITTED, 101L, EARLY, "早供应商");
        item(older, skuId, "3.2000", false);
        long newer = order(ScmPurchaseStatusEnum.RECEIVED, 101L, LATE, "新供应商");
        item(newer, skuId, "3.8000", false);

        var rows = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, ScmValueScope.all());

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getSkuId()).isEqualTo(skuId);
            assertThat(row.getPurchasePrice()).isEqualByComparingTo("3.8000");
            assertThat(row.getPurchaseUnit()).isEqualTo("斤");
            assertThat(row.getSupplierName()).isEqualTo("新供应商");
            assertThat(row.getSubmittedAt().toInstant()).isEqualTo(LATE.toInstant());
        });
    }

    @Test
    void ignoresDraftCancelledAndDeletedRows() {
        long skuId = 8_831_000_002L;
        long draft = order(ScmPurchaseStatusEnum.DRAFT, 101L, null, "草稿供应商");
        item(draft, skuId, "99.0000", false);
        long cancelled = order(ScmPurchaseStatusEnum.CANCELLED, 101L, EARLY, "取消供应商");
        item(cancelled, skuId, "88.0000", false);
        long committed = order(ScmPurchaseStatusEnum.SUBMITTED, 101L, EARLY, "已提交供应商");
        item(committed, skuId, "5.5000", false);
        // 已提交单上被软删的行仍不能参与参考价
        long deletedOrder = order(ScmPurchaseStatusEnum.RECEIVED, 101L, LATE, "已删行供应商");
        item(deletedOrder, skuId, "66.0000", true);

        var rows = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, ScmValueScope.all());

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPurchasePrice()).isEqualByComparingTo("5.5000");
            assertThat(row.getSupplierName()).isEqualTo("已提交供应商");
        });
    }

    @Test
    void respectsPurchaserScopeAndFailsClosedWithoutScope() {
        long skuId = 8_831_000_003L;
        long mine = order(ScmPurchaseStatusEnum.SUBMITTED, 201L, EARLY, "我的供应商");
        item(mine, skuId, "4.1000", false);
        long other = order(ScmPurchaseStatusEnum.RECEIVED, 202L, LATE, "别人的供应商");
        item(other, skuId, "4.9000", false);

        var visible = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED,
                ScmValueScope.of(List.of(201L)));

        assertThat(visible).singleElement()
                .satisfies(row -> assertThat(row.getSupplierName()).isEqualTo("我的供应商"));
        assertThat(purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, null)).isEmpty();
    }

    private long order(ScmPurchaseStatusEnum status, Long purchaserId, OffsetDateTime submittedAt,
            String supplierName) {
        boolean cancelled = status == ScmPurchaseStatusEnum.CANCELLED;
        return jdbc.queryForObject("""
                INSERT INTO xsy_purchase_order (order_no, supplier_id, supplier_code_snapshot,
                    supplier_name_snapshot, warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                    purchaser_id, status, total_amount, submitted_at, cancelled_at, cancel_reason)
                VALUES (?, 1, 'SUP', ?, 1, 'WH', '仓库', ?, ?, 0, ?, ?, ?) RETURNING id
                """, Long.class, "IT-" + UUID.randomUUID(), supplierName, purchaserId, status.name(), submittedAt,
                cancelled ? EARLY : null, cancelled ? "取消" : null);
    }

    private void item(long orderId, long skuId, String purchasePrice, boolean deleted) {
        jdbc.update("""
                INSERT INTO xsy_purchase_order_item (purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                    product_name_snapshot, sku_code_snapshot, sku_name_snapshot, purchase_unit_snapshot,
                    product_type_snapshot, planned_quantity, received_quantity, purchase_price, line_amount, deleted)
                VALUES (?, 1, ?, 'SPU', '商品', 'SKU', '规格', '斤', 'STANDARD', 1, 0, ?, ?, ?)
                """, orderId, skuId, new BigDecimal(purchasePrice),
                new BigDecimal(purchasePrice).multiply(BigDecimal.ONE), deleted);
    }
}
