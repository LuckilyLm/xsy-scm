package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.domain.form.*;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.ResolvedPriceVO;
import net.lab1024.sa.admin.module.scm.pricing.constant.*;
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
    void missingPricePersistsNullRejectsSubmitAndRollbackKeepsDraft() throws Exception {
        var customer = new CustomerAddForm();
        customer.setCustomerCode(prefix);
        customer.setName(prefix);
        customer.setCustomerTypeId(customerTypeId("ENTERPRISE"));
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
        // W3 Product currently requires market price; inject the published UNPRICED resolver contract here.
        doReturn(List.of(p)).when(resolver).resolve(eq(c), anyList(), any());
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
        var draft = orders.create(f, prefix);
        assertThat(draft.getOrderedTotalAmount()).isNull();
        assertThat(draft.getItems().getFirst().getDraftPriceSource()).isNull();
        assertThat(draft.getItems().getFirst().getOrderedLineAmount()).isNull();
        assertThat(json.readTree(json.writeValueAsString(draft)).get("orderedTotalAmount").isNull()).isTrue();
        var submit = new OrderVersionForm();
        submit.setOrderId(draft.getOrderId());
        submit.setVersion(draft.getVersion());
        expectCode(() -> orders.submit(submit, prefix + "s"), 40949);
        assertThat(query.detail(draft.getOrderId()).getStatus()).isEqualTo("DRAFT");
        assertThat(query.detail(draft.getOrderId()).getItems().getFirst().getLockedUnitPrice()).isNull();
        // SQL CHECK must reject price/source asymmetry, including PostgreSQL's NULL boolean semantics.
        var connection = jdbc.getDataSource().getConnection();
        connection.close();
        var savepoint = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<java.sql.Savepoint>) con -> con.setSavepoint());
        assertThatThrownBy(() -> jdbc.update("UPDATE sales_order_item SET manual_price_override=TRUE,draft_unit_price=NULL,draft_price_source=NULL,manual_price_reason='test' WHERE id=?", draft.getItems().getFirst().getItemId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            con.rollback(savepoint);
            return null;
        });
    }
}
