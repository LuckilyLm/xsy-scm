package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.domain.form.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SalesOrderServiceIT extends ScmW3PgITBase {
    @Autowired SalesOrderService orders;
    @Autowired SalesOrderQueryService query;
    @Autowired OrderReturnService returns;
    @Autowired OrderRefundService refunds;
    @Autowired CustomerService customers;
    @Autowired org.apache.ibatis.session.SqlSession session;
    Long customer() {var f=new CustomerAddForm();f.setCustomerCode(prefix);f.setName(prefix);f.setCustomerTypeId(customerTypeId("ENTERPRISE"));f.setSettleMode("INDEPENDENT");f.setSellerId(anyEmployeeId());Long id=customers.add(f);var s=new CustomerStatusForm();s.setCustomerId(id);s.setVersion(0);s.setStatus("COOPERATING");customers.updateStatus(s);return id;}
    SalesOrderAddForm form(Long c,Long sku) {
        var f=new SalesOrderAddForm();f.setCustomerId(c);f.setOrderSource("ADMIN");f.setRemark("测试订单");
        var a=new OrderAddressForm();a.setReceiverName("测试客户");a.setReceiverPhone("13800000000");a.setAddress("测试地址");f.setAddress(a);
        var i=new SalesOrderItemForm();i.setSkuId(sku);i.setOrderedQuantity("2.0000");i.setManualPriceOverride(false);f.setItems(new ArrayList<>(List.of(i)));return f;
    }
    OrderVersionForm version(SalesOrderDetailVO o){var f=new OrderVersionForm();f.setOrderId(o.getOrderId());f.setVersion(o.getVersion());return f;}
    SalesOrderDetailVO confirmed(Long c,Long sku){var o=orders.create(form(c,sku),prefix+"c");o=orders.submit(version(o),prefix+"s");var a=new OrderActualQuantityForm();a.setOrderId(o.getOrderId());a.setItemId(o.getItems().getFirst().getItemId());a.setVersion(o.getItems().getFirst().getVersion());a.setActualQuantity("1.5000");a.setReason("实际称重");o=orders.actualQuantity(a,prefix+"a");return orders.confirm(version(o),prefix+"f");}
    @Test void lifecycleRetainsSnapshotsAndLockedPricesAndAudit(){
        var c=customer();var sku=newOnShelfSku("L");var f=form(c,sku);var o=orders.create(f,prefix);assertThat(o.getVersion()).isZero();assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");assertThat(o.getSellerId()).isEqualTo(anyEmployeeId());
        var u=new SalesOrderUpdateForm();org.springframework.beans.BeanUtils.copyProperties(f,u);u.setOrderId(o.getOrderId());u.setVersion(o.getVersion());u.getItems().getFirst().setItemId(o.getItems().getFirst().getItemId());u.getItems().getFirst().setVersion(o.getItems().getFirst().getVersion());u.getItems().getFirst().setOrderedQuantity("3.0000");
        var updated=orders.update(u);assertThat(updated.getItems().getFirst().getItemId()).isEqualTo(o.getItems().getFirst().getItemId());expectCode(()->orders.update(u),40921);
        jdbc.update("UPDATE product_sku SET market_price=2.5000 WHERE id=?",sku);session.clearCache();o=orders.submit(version(updated),prefix+"s");assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");assertThat(o.getItems().getFirst().getActualQuantity()).isNull();
        jdbc.update("UPDATE product_sku SET market_price=8.0000 WHERE id=?",sku);jdbc.update("UPDATE customer SET name='changed',settle_mode='GROUP',seller_id=NULL WHERE id=?",c);session.clearCache();
        var pending=o;expectCode(()->orders.confirm(version(pending),prefix+"early"),40963);
        var a=new OrderActualQuantityForm();a.setOrderId(o.getOrderId());a.setItemId(o.getItems().getFirst().getItemId());a.setVersion(o.getItems().getFirst().getVersion());a.setActualQuantity("2.1234");a.setReason("人工称重");o=orders.actualQuantity(a,prefix+"actual");o=orders.confirm(version(o),prefix+"confirm");
        assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("5.3085");assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");assertThat(o.getCustomerNameSnapshot()).isEqualTo(prefix);assertThat(o.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo("2.5000");
        var logs=new OrderLogQueryForm();logs.setOrderId(o.getOrderId());logs.setPageNum(1L);logs.setPageSize(20L);assertThat(query.logs(logs).getList()).extracting(OrderOperationLogVO::getOperationType).contains("CREATE","UPDATE","SUBMIT","ACTUAL_QUANTITY","CONFIRM");assertThat(query.logs(logs).getList()).allSatisfy(l->{assertThat(l.getOperator()).isEqualTo("1:1");assertThat(l.getAfterData()).isNotEmpty();});
        var jsonValue=json.valueToTree(o);assertThat(jsonValue.get("settlementTotalAmount").asText()).isEqualTo("5.3085");
    }
    @Test void zeroIsPricedStandardQuantityAndIdempotentReplay(){
        var c=customer();var sku=newOnShelfSku("Z");jdbc.update("UPDATE product_sku SET market_price=0,product_type='STANDARD' WHERE id=?",sku);var f=form(c,sku);var o=orders.create(f,prefix);assertThat(orders.create(f,prefix).getOrderId()).isEqualTo(o.getOrderId());
        f.setRemark("changed");expectCode(()->orders.create(f,prefix),40966);var request=version(o);o=orders.submit(request,prefix+"s");assertThat(orders.submit(request,prefix+"s").getVersion()).isEqualTo(o.getVersion());assertThat(o.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.0000");
        var actual=new OrderActualQuantityForm();actual.setOrderId(o.getOrderId());actual.setItemId(o.getItems().getFirst().getItemId());actual.setVersion(o.getItems().getFirst().getVersion());actual.setActualQuantity("1.0000");actual.setReason("不能改标品");expectCode(()->orders.actualQuantity(actual,prefix+"invalidactual"),40962);
        o=orders.confirm(version(o),prefix+"f");assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("0.0000");
    }
    @Test void manualOverrideDoesNotBypassVisibilityAndDraftCanClearIt(){
        var c=customer();var sku=newOnShelfSku("M");var f=form(c,sku);var item=f.getItems().getFirst();item.setManualPriceOverride(true);item.setUnitPrice("4.5000");item.setOverrideReason("协商");var o=orders.create(f,prefix);assertThat(o.getItems().getFirst().getDraftPriceSource()).isEqualTo("OVERRIDE");
        jdbc.update("UPDATE customer SET visibility_policy='ALLOWLIST' WHERE id=?",c);session.clearCache();var draft=o;expectCode(()->orders.submit(version(draft),prefix+"s"),40949);
    }
    @Test void afterSalesQuotaApprovalAndRefundAreAtomic(){
        var o=confirmed(customer(),newOnShelfSku("R"));var f=new OrderReturnAddForm();f.setOrderId(o.getOrderId());f.setReason("品质问题");var row=new OrderReturnItemForm();row.setOrderItemId(o.getItems().getFirst().getItemId());row.setRequestedQuantity("1.0000");f.setItems(List.of(row));var r=returns.create(f,prefix+"r");expectCode(()->returns.create(f,prefix+"r2"),40969);
        var approve=new OrderReturnApproveForm();approve.setReturnId(r.getReturnId());approve.setVersion(r.getVersion());var approved=new OrderReturnApproveItemForm();approved.setOrderItemId(row.getOrderItemId());approved.setApprovedQuantity("0.5000");approve.setItems(List.of(approved));r=returns.approve(approve,prefix+"approve");assertThat(r.getStatus()).isEqualTo("APPROVED");
        var q=new OrderRefundQueryForm();q.setOrderId(o.getOrderId());q.setPageNum(1L);q.setPageSize(20L);var refund=refunds.query(q).getList().getFirst();assertThat(refund.getRefundAmount()).isEqualByComparingTo("0.6000");
        var complete=new OrderRefundCompleteForm();complete.setRefundId(refund.getRefundId());complete.setVersion(refund.getVersion());complete.setExternalReference(prefix);assertThat(refunds.complete(complete,prefix+"complete").getStatus()).isEqualTo("COMPLETED");assertThat(refunds.complete(complete,prefix+"complete").getStatus()).isEqualTo("COMPLETED");expectCode(()->refunds.complete(complete,prefix+"staleRefund"),40921);expectCode(()->returns.approve(approve,prefix+"staleReturn"),40921);assertThat(query.detail(o.getOrderId()).getStatus()).isEqualTo("CONFIRMED");
    }
    @Test void cancelAndDraftDeletionRespectStateAndVersions(){
        var c=customer();var sku=newOnShelfSku("D");var o=orders.create(form(c,sku),prefix);
        var deleteCustomer=new CustomerDeleteForm();deleteCustomer.setCustomerId(c);deleteCustomer.setVersion(1);expectCode(()->customers.delete(deleteCustomer),40939);
        orders.delete(version(o));assertThat(jdbc.queryForObject("SELECT deleted FROM sales_order WHERE id=?",Boolean.class,o.getOrderId())).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_item WHERE order_id=? AND NOT deleted",Integer.class,o.getOrderId())).isZero();orders.delete(version(o));
        o=orders.create(form(c,sku),prefix+"other");var cancel=new OrderCancelForm();cancel.setOrderId(o.getOrderId());cancel.setVersion(o.getVersion());cancel.setReason("客户取消");assertThat(orders.cancel(cancel,prefix+"cancel").getStatus()).isEqualTo("CANCELLED");
    }
    @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentDuplicateCreatesAndReturnQuotaHaveOneWinner() throws Exception {
        var c=customer();var sku=newOnShelfSku("CONCURRENT");var f=form(c,sku);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<SalesOrderDetailVO> create=()->{operator();try{barrier.await();return orders.create(f,prefix+"same");}finally{net.lab1024.sa.base.common.util.SmartRequestUtil.remove();}};
            var first=pool.submit(create);var second=pool.submit(create);assertThat(first.get().getOrderId()).isEqualTo(second.get().getOrderId());
            var o=confirmed(c,sku);var r=new OrderReturnAddForm();r.setOrderId(o.getOrderId());r.setReason("并发退货");var i=new OrderReturnItemForm();i.setOrderItemId(o.getItems().getFirst().getItemId());i.setRequestedQuantity("1.0000");r.setItems(List.of(i));
            java.util.concurrent.Callable<Integer> attempt=()->{operator();try{barrier.await();returns.create(r,UUID.randomUUID().toString());return 0;}catch(net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException e){return e.getErrorCode().getCode();}finally{net.lab1024.sa.base.common.util.SmartRequestUtil.remove();}};
            var one=pool.submit(attempt);var two=pool.submit(attempt);assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(0,40969);
        } finally {pool.shutdownNow();}
    }
    private void operator(){var e=new net.lab1024.sa.admin.module.system.login.domain.RequestEmployee();e.setEmployeeId(1L);e.setActualName("W4 IT");e.setUserType(net.lab1024.sa.base.common.enumeration.UserTypeEnum.ADMIN_EMPLOYEE);net.lab1024.sa.base.common.util.SmartRequestUtil.setRequestUser(e);}
    @Test void snapshotsOrderingManualLockAndItemVersionArePreserved(){
        var c=customer();var first=newOnShelfSku("S1");var second=newOnShelfSku("S2");var f=form(c,first);var i=f.getItems().getFirst();i.setSortOrder(4);i.setManualPriceOverride(true);i.setUnitPrice("2.1234");i.setOverrideReason("客户协商");
        var j=new SalesOrderItemForm();j.setSkuId(second);j.setOrderedQuantity("1.0000");j.setManualPriceOverride(false);j.setSortOrder(1);f.getItems().add(j);var o=orders.create(f,prefix);assertThat(o.getItems()).extracting(SalesOrderItemVO::getSkuId).containsExactly(second,first);
        assertThat(o.getItems().getFirst().getSpecValuesSnapshot()).isNotEmpty();assertThat(o.getItems().getFirst().getSaleUnitSnapshot()).isEqualTo("kg");
        o=orders.submit(version(o),prefix+"submit");var manual=o.getItems().getLast();assertThat(manual.getLockedUnitPrice()).isEqualByComparingTo("2.1234");assertThat(manual.getLockedPriceSource()).isEqualTo("OVERRIDE");
        var a=new OrderActualQuantityForm();a.setOrderId(o.getOrderId());a.setItemId(manual.getItemId());a.setVersion(999);a.setActualQuantity("1.0000");a.setReason("实测");expectCode(()->orders.actualQuantity(a,prefix+"bad"),40965);
        assertThat(o.getAddress().getAddress()).isEqualTo("测试地址");
    }
    @Test void rejectedAndCancelledReturnsReleaseQuotaAndStaleApprovalFails(){
        var o=confirmed(customer(),newOnShelfSku("DECIDE"));var f=new OrderReturnAddForm();f.setOrderId(o.getOrderId());f.setReason("退货");var i=new OrderReturnItemForm();i.setOrderItemId(o.getItems().getFirst().getItemId());i.setRequestedQuantity("1.0000");f.setItems(List.of(i));
        var r=returns.create(f,prefix+"r1");var decision=new OrderReturnDecisionForm();decision.setReturnId(r.getReturnId());decision.setVersion(999);decision.setDecisionReason("审核");expectCode(()->returns.reject(decision,prefix+"stale"),40921);decision.setVersion(r.getVersion());assertThat(returns.reject(decision,prefix+"reject").getStatus()).isEqualTo("REJECTED");
        r=returns.create(f,prefix+"r2");decision.setReturnId(r.getReturnId());decision.setVersion(r.getVersion());assertThat(returns.cancel(decision,prefix+"cancel").getStatus()).isEqualTo("CANCELLED");
        assertThat(returns.create(f,prefix+"r3").getStatus()).isEqualTo("PENDING");
    }
}
