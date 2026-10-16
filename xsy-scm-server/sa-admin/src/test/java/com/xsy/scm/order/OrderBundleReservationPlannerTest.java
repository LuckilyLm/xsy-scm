package com.xsy.scm.order;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderBundleReservationPlanner;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBundleReservationPlannerTest {

    @Test
    void usesFrozenComponentSkuQuantityUnitAndSourceInsteadOfBundleSku() {
        var parent = parent("2.0000");
        var child = child("6.0000", "3.0000", "10.0000");
        var lines = OrderBundleReservationPlanner.lines(List.of(child), List.of(parent));

        assertThat(lines).hasSize(1);
        var line = lines.getFirst();
        assertThat(line.itemId()).isEqualTo(500L);
        assertThat(line.skuId()).isEqualTo(300L);
        assertThat(line.quantity()).isEqualByComparingTo("6.0000");
        assertThat(line.stockUnit()).isEqualTo("kg");
        assertThat(line.factor()).isEqualByComparingTo("10.0000");
        assertThat(line.sourceType())
                .isEqualTo(ScmInventorySourceDocumentTypeEnum.ORDER_BUNDLE_COMPONENT.name());
    }

    @Test
    void noBundleComponentsAddsNoSeparateReservation() {
        assertThat(OrderBundleReservationPlanner.lines(List.of(), List.of(parent("1")))).isEmpty();
    }

    @Test
    void tamperedFrozenQuantityMustNotReserveOrRecomputeSilently() {
        assertThatThrownBy(() -> OrderBundleReservationPlanner.lines(
                List.of(child("5.0000", "3.0000", "10.0000")), List.of(parent("2.0000"))))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void componentStockConversionMustStayExact() {
        assertThatThrownBy(() -> OrderBundleReservationPlanner.lines(
                List.of(child("0.3333", "0.3333", "0.3333")), List.of(parent("1.0000"))))
                .isInstanceOf(ScmBusinessException.class);
    }

    private static SalesOrderItemEntity parent(String quantity) {
        var row = new SalesOrderItemEntity();
        row.setId(200L);
        row.setSkuId(100L);
        row.setActualQuantity(new BigDecimal(quantity));
        return row;
    }

    private static OrderBundleComponentEntity child(String frozenQuantity, String perBundle, String factor) {
        var row = new OrderBundleComponentEntity();
        row.setId(500L);
        row.setOrderItemId(200L);
        row.setBundleSkuId(100L);
        row.setComponentSkuId(300L);
        row.setComponentSaleQuantitySnapshot(new BigDecimal(frozenQuantity));
        row.setQuantityPerBundleSaleUnit(new BigDecimal(perBundle));
        row.setComponentStockUnitSnapshot("kg");
        row.setStockUnitsPerComponentSaleUnit(new BigDecimal(factor));
        return row;
    }
}
