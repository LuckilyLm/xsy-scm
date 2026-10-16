package com.xsy.scm.order;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在隔离 PostgreSQL 18 + 真实 Flyway V140 上验证组合快照的不可变性与数据库约束。
 * 每个测试在事务中回滚，不更改业务环境，也不依赖假的 H2 行为。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class ScmBundleMigrationPgIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v140SnapshotContainsFrozenMarketAndAllocationColumns() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()"
                + " AND table_name='xsy_order_bundle_component' AND column_name IN "
                + "('component_sale_quantity_snapshot','market_unit_price_snapshot',"
                + "'market_reference_amount_snapshot','allocated_sale_amount_snapshot')"
                + " AND is_nullable='NO'", Integer.class);
        assertThat(count).isEqualTo(4);
        Integer fks = jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f'"
                + " AND connamespace=current_schema()::regnamespace", Integer.class);
        assertThat(fks).isZero();
    }

    @Test
    void snapshotCannotBeChangedAfterFreeze() {
        Long id = insertSnapshot(11L, 101L, "32.0000", "20.00000000");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE xsy_order_bundle_component SET allocated_sale_amount_snapshot=0 WHERE id=?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("order bundle snapshot is immutable");
    }

    @Test
    void sameOrderItemCannotFreezeSameComponentTwice() {
        insertSnapshot(12L, 101L, "32.0000", "20.00000000");
        assertThatThrownBy(() -> insertSnapshot(12L, 101L, "32.0000", "20.00000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNegativeAllocation() {
        assertThatThrownBy(() -> insertSnapshot(13L, 101L, "-0.0001", "20.00000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsZeroMarketReferenceEvenIfAllocationIsPositive() {
        assertThatThrownBy(() -> insertSnapshot(14L, 101L, "10.0000", "0.00000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertSnapshot(Long orderItemId, Long componentSkuId,
            String allocatedAmount, String marketAmount) {
        return jdbc.queryForObject(
                "INSERT INTO xsy_order_bundle_component "
                + "(order_id,order_item_id,warehouse_id,bundle_sku_id,component_sku_id,"
                + "component_sku_code_snapshot,component_product_name_snapshot,"
                + "component_sale_unit_snapshot,component_stock_unit_snapshot,"
                + "quantity_per_bundle_sale_unit,stock_units_per_component_sale_unit,"
                + "component_sale_quantity_snapshot,market_unit_price_snapshot,"
                + "market_reference_amount_snapshot,allocated_sale_amount_snapshot,"
                + "product_bundle_component_id,product_bundle_component_version) "
                + "VALUES (1,?,42,200,?,'COMP','组成商品','kg','kg',"
                + "2.0000,1.0000,2.0000,10.0000,?::numeric,?::numeric,7,1) RETURNING id",
                Long.class, orderItemId, componentSkuId, marketAmount, allocatedAmount);
    }
}
