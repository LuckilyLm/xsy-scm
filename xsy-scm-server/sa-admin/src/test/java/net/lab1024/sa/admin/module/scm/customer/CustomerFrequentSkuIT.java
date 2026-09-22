package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerFrequentSkuVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerQueryService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderActualQuantityForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderCancelForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderVersionForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderDetailVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 7 客户「常购商品」只读聚合的真实 SQL 验证。
 *
 * <p>聚合口径全部落在 PostgreSQL 的 JOIN / 状态过滤 / {@code COUNT(DISTINCT)} / {@code ROW_NUMBER} /
 * {@code SUM} 与日界窗口上，MockMvc 触不到 mapper，因此这里用真实库跑一遍：
 * 只取未删除 CONFIRMED 单（排除 DRAFT / PENDING / CANCELLED）、同 (SKU, 单位) 分组、
 * 订购量求和、最近单价取「最近一张已确认行」而非平均 / 最早、limit 按频次降序、
 * null 锁定单价不回退。用例包在事务里结束回滚，不留数据。
 */
class CustomerFrequentSkuIT extends ScmW3PgITBase {

    @Autowired
    SalesOrderService orders;

    @Autowired
    CustomerService customers;

    @Autowired
    CustomerQueryService customerQuery;

    private Long customer(String sfx) {
        var f = new CustomerAddForm();
        f.setCustomerCode(prefix + sfx);
        f.setName(prefix + sfx);
        f.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        f.setSettleMode("INDEPENDENT");
        f.setSellerId(anyEmployeeId());
        Long id = customers.add(f);
        var s = new CustomerStatusForm();
        s.setCustomerId(id);
        s.setVersion(0);
        s.setStatus("COOPERATING");
        customers.updateStatus(s);
        return id;
    }

    /** 单行下单表单；manual=true 时锁 9.0000 手改价，否则走市场价 1.2000。 */
    private SalesOrderAddForm form(Long c, Long sku, boolean manual) {
        var f = new SalesOrderAddForm();
        f.setCustomerId(c);
        f.setOrderSource("ADMIN");
        var a = new OrderAddressForm();
        a.setReceiverName("测试客户");
        a.setReceiverPhone("13800000000");
        a.setAddress("测试地址");
        f.setAddress(a);
        var i = new SalesOrderItemForm();
        i.setSkuId(sku);
        i.setOrderedQuantity("2.0000");
        if (manual) {
            i.setManualPriceOverride(true);
            i.setUnitPrice("9.0000");
            i.setOverrideReason("协议改价");
        } else {
            i.setManualPriceOverride(false);
        }
        f.setItems(new ArrayList<>(List.of(i)));
        return f;
    }

    private OrderVersionForm version(SalesOrderDetailVO o) {
        var f = new OrderVersionForm();
        f.setOrderId(o.getOrderId());
        f.setVersion(o.getVersion());
        return f;
    }

    /** create → submit，停在 PENDING（已锁价但未确认）。 */
    private SalesOrderDetailVO submitted(Long c, Long sku, String tag) {
        var o = orders.create(form(c, sku, false), prefix + tag + "c");
        return orders.submit(version(o), prefix + tag + "s");
    }

    /** create → submit → 录实重 → confirm，得到带锁定单价的 CONFIRMED 单。 */
    private SalesOrderDetailVO confirmed(Long c, Long sku, String tag, boolean manual) {
        var o = orders.create(form(c, sku, manual), prefix + tag + "c");
        o = orders.submit(version(o), prefix + tag + "s");
        var a = new OrderActualQuantityForm();
        a.setOrderId(o.getOrderId());
        a.setItemId(o.getItems().getFirst().getItemId());
        a.setVersion(o.getItems().getFirst().getVersion());
        a.setActualQuantity("1.5000");
        a.setReason("实际称重");
        o = orders.actualQuantity(a, prefix + tag + "a");
        return orders.confirm(version(o), prefix + tag + "f");
    }

    @Test
    void aggregatesConfirmedCountAndQuantityAndTakesLatestPrice() {
        Long c = customer("A");
        Long sku = newOnShelfSku("A");

        // 两张已确认单：先 1.2000（市场），后 9.0000（手改）；同事务内 confirmed_at 相同，靠 order_id DESC 定最新
        confirmed(c, sku, "1", false);
        confirmed(c, sku, "2", true);

        List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
        assertThat(rows).hasSize(1);
        CustomerFrequentSkuVO row = rows.getFirst();
        assertThat(row.getSkuId()).isEqualTo(sku);
        assertThat(row.getUnit()).isEqualTo("kg");
        // 订单次数 = 两张已确认单；订购量 = 2.0000 + 2.0000，不是实重 1.5×2
        assertThat(row.getOrderCount()).isEqualTo(2L);
        assertThat(row.getOrderedQuantity()).isEqualByComparingTo("4.0000");
        // 最近单价取最新一张（9.0000），既非平均 5.1 也非最早 1.2
        assertThat(row.getRecentUnitPrice()).isEqualByComparingTo("9.0000");
        assertThat(row.getSkuCode()).isEqualTo(prefix + "A-K");
        assertThat(row.getProductName()).isEqualTo(prefix + "A商品");
        assertThat(row.getLastConfirmedAt()).isNotNull();
    }

    @Test
    void excludesDraftPendingAndCancelled() {
        Long c = customer("B");
        Long sku = newOnShelfSku("B");

        confirmed(c, sku, "cf", false);
        orders.create(form(c, sku, false), prefix + "draft");
        submitted(c, sku, "pending");
        var toCancel = submitted(c, sku, "tc");
        var cancel = new OrderCancelForm();
        cancel.setOrderId(toCancel.getOrderId());
        cancel.setVersion(toCancel.getVersion());
        cancel.setReason("客户取消");
        orders.cancel(cancel, prefix + "cx");

        // 四张单里只有 CONFIRMED 计入：次数 1、订购量 2.0000
        List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getOrderCount()).isEqualTo(1L);
        assertThat(rows.getFirst().getOrderedQuantity()).isEqualByComparingTo("2.0000");
    }

    @Test
    void limitReturnsHighestFrequencyFirst() {
        Long c = customer("C");
        Long hot = newOnShelfSku("C1");
        Long cold = newOnShelfSku("C2");
        confirmed(c, hot, "h1", false);
        confirmed(c, hot, "h2", false);
        confirmed(c, cold, "c1", false);

        // limit=1 只保留频次最高的 SKU（2 次）
        assertThat(customerQuery.frequentSkus(c, 90, 1))
                .extracting(CustomerFrequentSkuVO::getSkuId).containsExactly(hot);
        // limit=2 按频次降序：hot(2) 在 cold(1) 之前
        assertThat(customerQuery.frequentSkus(c, 90, 2))
                .extracting(CustomerFrequentSkuVO::getSkuId).containsExactly(hot, cold);
    }

    @Test
    void onlyCountsConfirmationsWithinDayWindow() {
        Long c = customer("D");
        Long sku = newOnShelfSku("D");
        SalesOrderDetailVO o = confirmed(c, sku, "d", false);
        // 把确认时间推到 200 天前，落在 90 天窗口外、365 天窗口内
        jdbc.update("UPDATE sales_order SET confirmed_at = now() - interval '200 days' WHERE id = ?",
                o.getOrderId());

        assertThat(customerQuery.frequentSkus(c, 90, 20)).isEmpty();
        assertThat(customerQuery.frequentSkus(c, 365, 20)).hasSize(1);
    }

    @Test
    void nullLockedPriceHasNoFallback() {
        Long c = customer("E");
        Long sku = newOnShelfSku("E");
        SalesOrderDetailVO o = confirmed(c, sku, "e", false);
        jdbc.update("UPDATE sales_order_item SET locked_unit_price = NULL WHERE order_id = ?",
                o.getOrderId());

        List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
        assertThat(rows).hasSize(1);
        // 锁定单价缺失 → null，不用市场价 / 草稿价兜底；订购量与次数不受影响
        assertThat(rows.getFirst().getRecentUnitPrice()).isNull();
        assertThat(rows.getFirst().getOrderedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(rows.getFirst().getOrderCount()).isEqualTo(1L);
    }

    @Test
    void rejectsUnknownCustomer() {
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM customer", Long.class);
        expectCode(() -> customerQuery.frequentSkus(absent, 90, 20), 40430);
    }
}
