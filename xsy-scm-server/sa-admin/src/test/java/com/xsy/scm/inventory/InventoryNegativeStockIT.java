package com.xsy.scm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.report.domain.form.ScmNegativeStockReportQueryForm;
import com.xsy.scm.report.service.InventoryReportService;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 负库存出库与损耗报表。
 *
 * <p>负库存只对开启开关的仓库、且没有预留的余额生效；关闭开关前必须先把负余额补平。
 * 报表是查询时点快照，只读余额和流水，不生成报损单。
 */
class InventoryNegativeStockIT extends ScmW6PgITBase {

    @Autowired
    private InventoryReportService inventoryReportService;

    private Long negativeWarehouseId;

    @BeforeEach
    void createNegativeWarehouse() {
        WarehouseAddForm form = new WarehouseAddForm();
        form.setName(prefix + "负库存仓");
        form.setAllowNegativeStock(true);
        negativeWarehouseId = warehouseService.create(form);
    }

    @Override
    protected Long fixtureWarehouseId() {
        return negativeWarehouseId;
    }

    @Test
    @DisplayName("#1 开启开关后可以出到负库存，流水快照与报表同步为负")
    void outboundBelowZeroIsAllowedAndReported() {
        Long skuId = newSkuOfType("NS1", "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture("NS1", skuId, "2.0000");
        confirmReceipt(fixture.receipt().getId(), "2.0000");

        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
            negativeWarehouseId, skuId, 0L, 950101L, new BigDecimal("5.0000"), null,
            OffsetDateTime.now(), "test:1"));

        assertThat(balanceRow(negativeWarehouseId, skuId).getQuantity()).isEqualByComparingTo("-3.0000");
        assertThat(jdbc.queryForObject(
            "SELECT unit_snapshot FROM xsy_inventory_movement WHERE warehouse_id = ? AND sku_id = ? "
                + "AND movement_type = 'SALES_OUT'",
            String.class, negativeWarehouseId, skuId)).isEqualTo("kg");

        ScmNegativeStockReportQueryForm query = new ScmNegativeStockReportQueryForm();
        query.setPageNum(1L);
        query.setPageSize(20L);
        query.setWarehouseId(negativeWarehouseId);
        var page = inventoryReportService.negativeStockList(query);
        assertThat(page.getList()).hasSize(1);
        var row = page.getList().getFirst();
        assertThat(row.getSkuId()).isEqualTo(skuId);
        assertThat(row.getQuantity()).isEqualByComparingTo("-3.0000");
        assertThat(row.getUnit()).isEqualTo("kg");
        // 夹具操作者没有成本权限，报表把金额列抹掉；成本本身回余额行核对
        assertThat(row.getAvgCost()).isNull();
        assertThat(row.getEstimatedAmount()).isNull();
        assertThat(balanceRow(negativeWarehouseId, skuId).getAvgCost()).isEqualByComparingTo("6.2000");
        assertThat(row.getLastOutboundAt()).isNotNull();
    }

    @Test
    @DisplayName("#2 无余额时必须以出库单位建零行；没有单位仍按 41011 拒绝")
    void balanceLessOutboundNeedsExplicitUnit() {
        Long skuId = newSkuOfType("NS2", "NON_STANDARD", "ON_SHELF");

        expectCode(() -> inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
            negativeWarehouseId, skuId, 0L, 950102L, new BigDecimal("1.0000"), null,
            OffsetDateTime.now(), "test:1")), 41011);
        assertThat(balanceRowCount(negativeWarehouseId, skuId)).isZero();

        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
            negativeWarehouseId, skuId, 0L, 950103L, new BigDecimal("1.5000"), null,
            OffsetDateTime.now(), "test:1", "kg"));
        assertThat(balanceRow(negativeWarehouseId, skuId).getQuantity()).isEqualByComparingTo("-1.5000");
        assertThat(balanceRow(negativeWarehouseId, skuId).getUnit()).isEqualTo("kg");
    }

    @Test
    @DisplayName("#3 关闭负库存开关前必须先补平负余额（41010）")
    void cannotDisableWhileNegativeBalanceExists() {
        Long skuId = newSkuOfType("NS3", "NON_STANDARD", "ON_SHELF");
        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
            negativeWarehouseId, skuId, 0L, 950104L, new BigDecimal("2.0000"), null,
            OffsetDateTime.now(), "test:1", "kg"));

        WarehouseUpdateForm update = new WarehouseUpdateForm();
        update.setId(negativeWarehouseId);
        update.setVersion(warehouseService.require(negativeWarehouseId).getVersion());
        update.setName(prefix + "负库存仓");
        update.setAllowNegativeStock(false);
        expectCode(() -> warehouseService.update(update), 41010);
        assertThat(warehouseService.require(negativeWarehouseId).getAllowNegativeStock()).isTrue();
    }

    @Test
    @DisplayName("#4 数据库守卫：未开启开关的仓库不能写入负余额")
    void databaseRejectsNegativeBalanceOnNormalWarehouse() {
        Long normalWarehouseId = newWarehouse("普通");
        Long skuId = newSkuOfType("NS4", "NON_STANDARD", "ON_SHELF");

        expectSqlFailure("INSERT INTO xsy_inventory_balance "
            + "(warehouse_id, sku_id, unit, quantity, reserved_quantity, avg_cost, version, deleted, created_by) "
            + "VALUES (?, ?, 'kg', -1, 0, 0, 0, FALSE, 'it')", normalWarehouseId, skuId);
        assertThat(balanceRowCount(normalWarehouseId, skuId)).isZero();
    }
}
