package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.form.MallOrderSummaryQueryForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderSummaryVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商城「下单商品汇总」的聚合口径。
 *
 * <p>
 * 订单行直接写入而不走结算下单：被测对象是对已持久化行的只读聚合，走一遍选品、地址与结算只会把商品和促销域的依赖拖进来，
 * 与聚合口径无关。写入的每一行都满足订单表的非空约束与状态取值。
 */
class MallOrderSummaryPgIT extends ScmW5PgITBase {

    @Autowired
    private MallOrderQueryService mallOrderQueryService;

    @Test
    @DisplayName("#1 按规格聚合：同规格跨订单相加，取消与非商城订单不计入")
    void aggregatesBySkuAndIgnoresCancelledOrAdminOrders() {
        Long customerId = newCustomer();
        Long skuA = newOnShelfSku("SUMA");
        Long skuB = newOnShelfSku("SUMB");

        Long first = insertOrder(customerId, "MALL", "CONFIRMED", OffsetDateTime.now().minusDays(2));
        insertItem(first, skuA, "2.0000", "20.0000", "甲商品");
        insertItem(first, skuB, "1.0000", "30.0000", "乙商品");

        Long second = insertOrder(customerId, "MALL", "PENDING", OffsetDateTime.now().minusDays(1));
        insertItem(second, skuA, "3.0000", "30.0000", "甲商品");

        // 取消的不算买过；后台代客下单不属于客户自己的商城下单
        Long cancelled = insertOrder(customerId, "MALL", "CANCELLED", OffsetDateTime.now());
        insertItem(cancelled, skuA, "99.0000", "990.0000", "甲商品");
        Long adminOrder = insertOrder(customerId, "ADMIN", "CONFIRMED", OffsetDateTime.now());
        insertItem(adminOrder, skuA, "88.0000", "880.0000", "甲商品");

        MallOrderSummaryVO summary = mallOrderQueryService.summary(customer(customerId), query(null, null));

        assertThat(summary.getItems()).hasSize(2);
        MallOrderSummaryVO.Item rowA = summary.getItems().stream()
                .filter(item -> item.getSkuId().equals(skuA))
                .findFirst()
                .orElseThrow();
        assertThat(rowA.getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(rowA.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(rowA.getOrderCount()).isEqualTo(2);

        assertThat(summary.getTotals().getQuantity()).isEqualByComparingTo("6.0000");
        assertThat(summary.getTotals().getAmount()).isEqualByComparingTo("80.0000");
        assertThat(summary.getTotals().getOrderCount()).isEqualTo(2);
        assertThat(summary.getTotals().getSkuCount()).isEqualTo(2);
        assertThat(summary.getTotal()).isEqualTo(2);
        assertThat(summary.getEmptyFlag()).isFalse();
    }

    @Test
    @DisplayName("#2 日期区间按天收窄：止日当天含在区间内")
    void filtersByInclusiveDateRange() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("SUMC");

        Long january = insertOrder(customerId, "MALL", "CONFIRMED",
                OffsetDateTime.of(2026, 1, 5, 23, 30, 0, 0, ZoneOffset.ofHours(8)));
        insertItem(january, skuId, "1.0000", "10.0000", "丙商品");
        Long march = insertOrder(customerId, "MALL", "CONFIRMED",
                OffsetDateTime.of(2026, 3, 5, 0, 30, 0, 0, ZoneOffset.ofHours(8)));
        insertItem(march, skuId, "2.0000", "20.0000", "丙商品");

        MallOrderSummaryVO januaryOnly = mallOrderQueryService.summary(customer(customerId),
                query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)));
        assertThat(januaryOnly.getItems()).hasSize(1);
        assertThat(januaryOnly.getItems().getFirst().getQuantity()).isEqualByComparingTo("1.0000");

        // 止日当天 00:30 的单必须落在区间内：按「止日加一天」的半开区间比较
        MallOrderSummaryVO marchOnly = mallOrderQueryService.summary(customer(customerId),
                query(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5)));
        assertThat(marchOnly.getItems()).hasSize(1);
        assertThat(marchOnly.getItems().getFirst().getQuantity()).isEqualByComparingTo("2.0000");
    }

    private MallOrderSummaryQueryForm query(LocalDate startDate, LocalDate endDate) {
        MallOrderSummaryQueryForm form = new MallOrderSummaryQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        return form;
    }

    private RequestMallCustomer customer(Long customerId) {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setCustomerId(customerId);
        customer.setCustomerName("W9 汇总客户");
        return customer;
    }

    private Long insertOrder(Long customerId, String orderSource, String status, OffsetDateTime createdAt) {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM xsy_sales_order", Long.class);
        jdbc.update("""
                INSERT INTO xsy_sales_order
                  (id, order_no, customer_id, customer_code_snapshot, customer_name_snapshot, order_source,
                   status, settle_mode_snapshot, settlement_customer_id, settlement_customer_name_snapshot,
                   cancel_reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'INDEPENDENT', ?, ?, ?, ?, ?)
                """, id, "W9SUM" + id, customerId, "C" + customerId, "W9 汇总客户", orderSource, status, customerId,
                "W9 汇总客户", "CANCELLED".equals(status) ? "汇总用例取消" : null, createdAt, createdAt);
        return id;
    }

    private void insertItem(Long orderId, Long skuId, String quantity, String amount, String productName) {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM xsy_sales_order_item", Long.class);
        Long spuId = jdbc.queryForObject("SELECT spu_id FROM xsy_product_sku WHERE id = ?", Long.class, skuId);
        BigDecimal orderedQuantity = new BigDecimal(quantity);
        BigDecimal lineAmount = new BigDecimal(amount);
        // ck_sales_order_item_draft_amount 要求单价与行金额同时有值或同时为空
        BigDecimal unitPrice = lineAmount.divide(orderedQuantity, 4, RoundingMode.HALF_UP);
        jdbc.update("""
                INSERT INTO xsy_sales_order_item
                  (id, order_id, spu_id, sku_id, spu_code_snapshot, product_name_snapshot, sku_code_snapshot,
                   spec_name_snapshot, sale_unit_snapshot, product_type_snapshot, ordered_quantity,
                   draft_unit_price, draft_price_source, ordered_line_amount, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, '默认规格', 'kg', 'STANDARD', ?, ?, 'MARKET', ?, 1)
                """, id, orderId, spuId, skuId, "SPU" + spuId, productName, "SKU" + skuId, orderedQuantity, unitPrice,
                lineAmount);
    }
}
