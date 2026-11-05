package com.xsy.scm.order;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.order.service.SalesOrderDispatchService;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.domain.form.OrderTagCreateForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.service.OrderTagService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class OrderSingleStateTagPgIT extends ScmW6PgITBase {
    @Autowired
    private SalesOrderDispatchService lifecycle;
    @Autowired
    private SalesOrderQueryService queries;
    @Autowired
    private OrderTagService tags;

    @Test
    void statusFilterAndDetailFollowLifecycleWhileLockDoesNotChangeStatus() {
        Long customer = newCustomer();
        Long first = confirmedSalesOrder(customer, newOnShelfSku("STATUS-A"), "3.0000", "3.0000");
        Long second = confirmedSalesOrder(customer, newOnShelfSku("STATUS-B"), "2.0000", "2.0000");
        assertThat(queries.detail(first).getStatus()).isEqualTo("CONFIRMED");
        lifecycle.markDispatched(first, OffsetDateTime.now(), "1:1");
        jdbc.update("UPDATE xsy_sales_order SET locked_flag = TRUE, locked_at = CURRENT_TIMESTAMP, locked_by = '1:1' WHERE id = ?", first);
        evictMybatisCache();
        var form = query(customer, "DISPATCHED");
        assertThat(queries.query(form).getList()).extracting(row -> row.getOrderId()).containsExactly(first);
        assertThat(queries.detail(first).getStatus()).isEqualTo("DISPATCHED");
        assertThat(queries.query(query(customer, "CONFIRMED")).getList()).extracting(row -> row.getOrderId()).containsExactly(second);
        assertThat(lifecycle.markSigned(first, "SIGNED", "1:1")).isTrue();
        assertThat(queries.query(form).getList()).isEmpty();
        assertThat(queries.query(query(customer, "SIGNED")).getList()).extracting(row -> row.getOrderId()).containsExactly(first);
        assertThat(queries.detail(first).getStatus()).isEqualTo("SIGNED");
        lifecycle.markDispatched(second, OffsetDateTime.now(), "1:1");
        assertThat(lifecycle.markSigned(second, "EXCEPTION", "1:1")).isTrue();
        assertThat(queries.detail(second).getStatus()).isEqualTo("EXCEPTION");
        assertThat(queries.query(query(customer, "EXCEPTION")).getList()).extracting(row -> row.getOrderId()).containsExactly(second);
        assertThat(lifecycle.markDispatched(first, OffsetDateTime.now(), "1:1")).isFalse();
        assertThat(lifecycle.markSigned(first, "EXCEPTION", "1:1")).isFalse();
    }

    @Test
    void pendingAndCancelledStatusesStayExclusive() {
        Long customer = newCustomer();
        Long pending = confirmedSalesOrder(customer, newOnShelfSku("PENDING-STATUS"), "1.0000", "1.0000");
        Long cancelled = confirmedSalesOrder(customer, newOnShelfSku("CANCEL-STATUS"), "1.0000", "1.0000");
        jdbc.update("UPDATE xsy_sales_order SET status = 'PENDING' WHERE id = ?", pending);
        jdbc.update("UPDATE xsy_sales_order SET status = 'CANCELLED', cancel_reason = '测试取消', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", cancelled);
        evictMybatisCache();
        assertThat(queries.query(query(customer, "PENDING")).getList()).extracting(row -> row.getOrderId()).containsExactly(pending);
        assertThat(queries.query(query(customer, "CANCELLED")).getList()).extracting(row -> row.getOrderId()).containsExactly(cancelled);
    }

    @Test
    void assigningReplacesSingleTagAndClearingRetainsOldLinksWithoutChangingOrderVersionOrAmounts() {
        Long orderId = confirmedSalesOrder(newCustomer(), newOnShelfSku("TAG"), "3.0000", "3.0000");
        var before = queries.detail(orderId);
        Long first = tag("第一个");
        Long second = tag("第二个");
        Long third = tag("第三个");
        apply(orderId, first, OrderTagAssignForm.Action.SET);
        apply(orderId, second, OrderTagAssignForm.Action.ADD);
        apply(orderId, second, OrderTagAssignForm.Action.SET);
        var current = queries.detail(orderId);
        assertThat(current.getTag().getTagId()).isEqualTo(second);
        assertThat(current.getTags()).hasSize(1);
        assertThat(current.getVersion()).isEqualTo(before.getVersion());
        assertThat(current.getSettlementTotalAmount()).isEqualByComparingTo(before.getSettlementTotalAmount());
        expectSqlFailure("INSERT INTO xsy_order_tag_link(order_id, tag_id, created_by) VALUES (?, ?, 'it')", orderId, third);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_tag_link WHERE NOT active AND order_id = ?", Integer.class, orderId)).isEqualTo(1);
        apply(orderId, null, OrderTagAssignForm.Action.CLEAR);
        assertThat(queries.detail(orderId).getTag()).isNull();
        assertThat(queries.detail(orderId).getTags()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_tag_link WHERE NOT active AND order_id = ?", Integer.class, orderId)).isEqualTo(2);
    }

    private SalesOrderQueryForm query(Long customer, String status) {
        var form = new SalesOrderQueryForm();
        form.setCustomerId(customer);
        form.setStatus(status);
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }
    private Long tag(String name) {
        var form = new OrderTagCreateForm();
        form.setName("单标签-" + java.util.UUID.randomUUID().toString().substring(0, 8) + name);
        form.setColor("BLUE");
        return tags.create(form);
    }
    private void apply(Long orderId, Long tagId, OrderTagAssignForm.Action action) {
        var form = new OrderTagAssignForm();
        form.setOrderIds(List.of(orderId));
        form.setTagId(tagId);
        form.setAction(action);
        tags.apply(form);
    }
}
