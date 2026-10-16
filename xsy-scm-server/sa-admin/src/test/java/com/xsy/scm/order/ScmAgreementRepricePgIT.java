package com.xsy.scm.order;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderRepriceForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V139：只允许有权限的人手动重算草稿价格，冻结订单不可重算。 */
class ScmAgreementRepricePgIT extends ScmW5PgITBase {

    @Test
    void repriceUnconfirmedDraftRecordsBothPriceChangeAndAuditReason() {
        Long customer = newCustomer();
        Long sku = newOnShelfSku("REPRICE-DRAFT");
        SalesOrderAddForm form = new SalesOrderAddForm();
        form.setCustomerId(customer);
        form.setOrderSource("ADMIN");
        var address = new OrderAddressForm();
        address.setReceiverName("重算测试");
        address.setReceiverPhone("13800000000");
        address.setAddress("重算测试地址");
        form.setAddress(address);
        var item = new SalesOrderItemForm();
        item.setSkuId(sku);
        item.setOrderedQuantity("2.0000");
        item.setManualPriceOverride(false);
        form.setItems(List.of(item));
        var draft = salesOrderService.create(form, prefix + ":create");
        Long orderId = draft.getOrderId();
        BigDecimal before = jdbc.queryForObject(
                "SELECT draft_unit_price FROM xsy_sales_order_item WHERE order_id=?",
                BigDecimal.class, orderId);
        jdbc.update("UPDATE xsy_product_sku SET market_price=2.5000 WHERE id=?", sku);
        evictMybatisCache();

        var request = new OrderRepriceForm();
        request.setOrderId(orderId);
        request.setVersion(draft.getVersion());
        request.setReason("采购市场参考价变化后人工核价");
        var repriced = salesOrderService.reprice(request);
        assertThat(repriced.getStatus()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT draft_unit_price FROM xsy_sales_order_item WHERE order_id=?",
                BigDecimal.class, orderId)).isEqualByComparingTo("2.5000");
        assertThat(jdbc.queryForObject(
                "SELECT ordered_total_amount FROM xsy_sales_order WHERE id=?",
                BigDecimal.class, orderId)).isEqualByComparingTo("5.0000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? "
                + "AND operation_type='REPRICE' AND reason LIKE '%人工核价%'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(before).isNotEqualByComparingTo("2.5000");

        assertThatThrownBy(() -> salesOrderService.reprice(request))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void confirmedOrderCannotRepriceEvenWhenMarketPriceChanged() {
        Long customer = newCustomer();
        Long sku = newOnShelfSku("REPRICE-FROZEN");
        Long orderId = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
        jdbc.update("UPDATE xsy_product_sku SET market_price=99.0000 WHERE id=?", sku);
        evictMybatisCache();

        var request = new OrderRepriceForm();
        request.setOrderId(orderId);
        request.setVersion(jdbc.queryForObject(
                "SELECT version FROM xsy_sales_order WHERE id=?", Integer.class, orderId));
        request.setReason("订单已确认后禁止重算");
        assertThatThrownBy(() -> salesOrderService.reprice(request))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? "
                + "AND operation_type='REPRICE'", Integer.class, orderId)).isZero();
    }
}
