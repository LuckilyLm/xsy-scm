package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderActualQuantityForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderCancelForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderVersionForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderRecentPriceVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderDetailVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderQueryService;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 3 §7.5：「最近已确认订单价」只读参考的真实 SQL 验证。
 *
 * <p>过滤口径（只取未删除 CONFIRMED 单，排除 DRAFT / PENDING / CANCELLED）、按 (客户, SKU) 收敛、
 * 订单分组的 limit 语义与 confirmed_at 倒序，都依赖 PostgreSQL 上的 JOIN / 状态 / 唯一索引，
 * MockMvc 只 mock 到 service 触不到 mapper，因此这里用真实库跑一遍。用例包在事务里结束回滚，不留数据。
 */
class SalesOrderRecentPriceIT extends ScmW3PgITBase {
    @Autowired
    SalesOrderService orders;
    @Autowired
    SalesOrderQueryService query;
    @Autowired
    CustomerService customers;

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

    private SalesOrderAddForm form(Long c, Long sku) {
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
        i.setManualPriceOverride(false);
        f.setItems(new ArrayList<>(List.of(i)));
        return f;
    }

    private OrderVersionForm version(SalesOrderDetailVO o) {
        var f = new OrderVersionForm();
        f.setOrderId(o.getOrderId());
        f.setVersion(o.getVersion());
        return f;
    }

    /** create → submit，停在 PENDING（已锁价但未确认），用于验证 PENDING 被排除。 */
    private SalesOrderDetailVO submitted(Long c, Long sku, String tag) {
        var o = orders.create(form(c, sku), prefix + tag + "c");
        return orders.submit(version(o), prefix + tag + "s");
    }

    /** create → submit → 录实重 → confirm，得到带锁定单价的 CONFIRMED 单。 */
    private SalesOrderDetailVO confirmed(Long c, Long sku, String tag) {
        var o = submitted(c, sku, tag);
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
    void recentPricesReturnsOnlyConfirmedForCustomerAndSkuNewestFirst() {
        Long c = customer("A");
        Long sku = newOnShelfSku("RP");
        Long other = customer("B");

        SalesOrderDetailVO first = confirmed(c, sku, "1");
        SalesOrderDetailVO second = confirmed(c, sku, "2");

        // DRAFT：未提交，必须排除
        SalesOrderDetailVO draft = orders.create(form(c, sku), prefix + "dc");
        // PENDING：submit 已锁价但未确认，按 §7.5 新口径必须排除（区别于旧的 status<>CANCELLED）
        SalesOrderDetailVO pending = submitted(c, sku, "p");
        // CANCELLED：从 PENDING 取消得到（状态机禁止 CONFIRMED 取消），必须排除
        SalesOrderDetailVO pendingToCancel = submitted(c, sku, "x");
        var cancel = new OrderCancelForm();
        cancel.setOrderId(pendingToCancel.getOrderId());
        cancel.setVersion(pendingToCancel.getVersion());
        cancel.setReason("客户取消");
        orders.cancel(cancel, prefix + "cx");
        // 其它客户的 CONFIRMED 不得混入本客户结果
        confirmed(other, sku, "o");

        List<OrderRecentPriceVO> rows = query.recentPrices(c, sku, 10);
        assertThat(rows).extracting(OrderRecentPriceVO::getOrderNo)
                .containsExactly(second.getOrderNo(), first.getOrderNo());
        assertThat(rows).extracting(OrderRecentPriceVO::getOrderNo)
                .doesNotContain(draft.getOrderNo(), pending.getOrderNo(), pendingToCancel.getOrderNo());

        var latest = rows.getFirst();
        assertThat(latest.getUnitPrice()).isEqualByComparingTo("1.2000");
        assertThat(latest.getOrderedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(latest.getPriceSource()).isEqualTo("MARKET");
        assertThat(latest.getOrderSource()).isEqualTo("ADMIN");
        assertThat(latest.getCreatedAt()).isNotNull();
        assertThat(latest.getConfirmedAt()).isNotNull();
        assertThat(latest.getItemId()).isNotNull();
        assertThat(latest.getOrderId()).isNotNull();
        assertThat(latest.getSaleUnit()).isEqualTo("kg");

        // 客户维度隔离：另一客户只有它自己那一条
        assertThat(query.recentPrices(other, sku, 10)).extracting(OrderRecentPriceVO::getOrderNo)
                .containsExactly(confirmedOrderNoOf(other, sku));
    }

    /** 取该客户该 SKU 的最近已确认订单号（用于跨客户隔离断言，不引入新查询）。 */
    private String confirmedOrderNoOf(Long c, Long sku) {
        return query.recentPrices(c, sku, 1).getFirst().getOrderNo();
    }

    @Test
    void recentPricesLimitTruncatesNewestNOrders() {
        Long c = customer("A");
        Long sku = newOnShelfSku("RL");
        SalesOrderDetailVO a = confirmed(c, sku, "a");
        SalesOrderDetailVO b = confirmed(c, sku, "b");
        SalesOrderDetailVO newest = confirmed(c, sku, "c");

        // limit 约束订单数：取最近 2 张 → 只含 b、newest，最旧的 a 被截断
        assertThat(query.recentPrices(c, sku, 2)).extracting(OrderRecentPriceVO::getOrderNo)
                .containsExactly(newest.getOrderNo(), b.getOrderNo());
        // limit=0 经服务层裁剪到下限 1，仍返回最近一张
        assertThat(query.recentPrices(c, sku, 0)).hasSize(1);
    }
}
