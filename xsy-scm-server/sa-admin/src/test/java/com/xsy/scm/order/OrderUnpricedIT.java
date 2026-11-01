package com.xsy.scm.order;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.order.service.*;
import com.xsy.scm.order.domain.form.*;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.domain.form.*;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.constant.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class OrderUnpricedIT extends ScmW3PgITBase {
    @Autowired
    SalesOrderService orders;
    @Autowired
    SalesOrderQueryService query;
    @Autowired
    CustomerService customers;
    @MockitoSpyBean
    PriceResolver resolver;

    @Test
    void missingPriceRejectsCreationAndLeavesNoOrder() throws Exception {
        var customer = new CustomerAddForm();
        customer.setName(prefix);
        customer.setSettleMode("INDEPENDENT");
        var c = customers.add(customer);
        var status = new CustomerStatusForm();
        status.setCustomerId(c);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        customers.updateStatus(status);
        var sku = newOnShelfSku("NULL");
        var p = new ResolvedPriceVO();
        p.setSkuId(sku);
        p.setSellable(true);
        p.price(null, ScmPriceSourceEnum.MARKET, null);
        // Product pricing requires a market price, so the published UNPRICED resolver contract is injected here.
        // 下单解析按 (客户, SKU, 时点, 仓库) 解析；仓库为 null 表示不按仓取成本。
        doReturn(List.of(p)).when(resolver).resolve(eq(c), anyList(), any(), isNull());
        var f = new SalesOrderAddForm();
        f.setCustomerId(c);
        f.setOrderSource("ADMIN");
        var a = new OrderAddressForm();
        a.setReceiverName("客户");
        a.setReceiverPhone("123");
        a.setAddress("地址");
        f.setAddress(a);
        var i = new SalesOrderItemForm();
        i.setSkuId(sku);
        i.setOrderedQuantity("1.0000");
        i.setManualPriceOverride(false);
        f.setItems(List.of(i));
        // 新建即锁价：解析不出价格就直接失败，不留下任何订单。
        expectCode(() -> orders.create(f, prefix), 40949);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id = ?", Integer.class, c))
                .isZero();

        // SQL CHECK must reject price/source asymmetry, including PostgreSQL's NULL boolean semantics.
        p.price(new java.math.BigDecimal("5.0000"), ScmPriceSourceEnum.MARKET, null);
        var order = orders.create(f, prefix + "priced");
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo("5.0000");
        var savepoint = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<java.sql.Savepoint>) con -> con.setSavepoint());
        assertThatThrownBy(() -> jdbc.update("UPDATE xsy_sales_order_item SET manual_price_override=TRUE,draft_unit_price=NULL,draft_price_source=NULL,manual_price_reason='test' WHERE id=?", order.getItems().getFirst().getItemId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            con.rollback(savepoint);
            return null;
        });
    }
}
