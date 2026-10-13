package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.service.InventoryOutboundService;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.aftersale.constant.MallAfterSaleDisplayStatusEnum;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleApplyForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleQueryService;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleService;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderRefundCompleteForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.OrderRefundService;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 商城售后全链路（PG IT）：客户申请 → 配额守卫 → 后台审批与收货 → 退款完成 → 客户侧投影与审计。
 *
 * <p>
 * 为什么必须跑真实库：可退数量上限（实数量减去在途申请）、退货成本取历史销售出库成本、
 * 库存回补与退款单的净额计算，全部由 SQL 谓词与事务边界决定；商城侧只是把正式退货单 / 退款单
 * 投影给客户，mock 掉任何一层都会把「客户看到什么」变成对桩的断言。
 *
 * <p>
 * <b>资金到账的分界</b>：{@code OrderRefundService.complete} 只把退款单置为 COMPLETED；
 * 真正的资金事实（渠道原路退回 / 退回余额）由资金分摊或旧钱包返还路径产生。本类夹具没有支付，
 * 因此断言的是「退款单已完成、资金尚未到账」这一侧（客户看到 REFUNDING），
 * 资金到账后的 REFUNDED 由分摊与钱包那两组用例负责（{@code PaymentRefundSumBasisPgIT}、
 * {@code BalanceRefundServiceTest}），本类不重复也不放宽它们。
 */
@DisplayName("商城售后全链路（PG IT）")
class MallAfterSaleFlowPgIT extends ScmW6PgITBase {

    private static final String TEMPORARY_PASSWORD = "Mall@123456";
    private static final String NEW_PASSWORD = "Mall@654321";

    @Autowired
    private MallAccountService mallAccountService;

    @Autowired
    private MallAuthService mallAuthService;

    @Autowired
    private MallCartService mallCartService;

    @Autowired
    private MallAddressService mallAddressService;

    @Autowired
    private MallCheckoutService mallCheckoutService;

    @Autowired
    private MallAfterSaleService mallAfterSaleService;

    @Autowired
    private MallAfterSaleQueryService mallAfterSaleQueryService;

    @Autowired
    private OrderReturnService orderReturnService;

    @Autowired
    private OrderReturnReceiptService orderReturnReceiptService;

    @Autowired
    private OrderRefundService orderRefundService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private SalesOrderQueryService salesOrderQueryService;

    @Autowired
    private InventoryOutboundService inventoryOutboundService;

    // ------------------------------------------------------------------
    // 主链路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("客户申请售后到退款完成：配额受限、库存回补、退款单落账、客户看到处理中")
    void mallAfterSaleRunsFromCustomerApplicationToCompletedRefund() {
        AfterSaleFixture fixture = confirmedMallOrderWithCost("flow");

        // 客户申请：归属、状态门槛与可退数量都在正式退货路径里判定
        MallAfterSaleVO applied = apply(fixture, "2.0000", "flow-apply");
        assertThat(applied.getStatus()).isEqualTo("PENDING");
        assertThat(applied.getDisplayStatus()).isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING.name());
        assertThat(applied.getCancelable()).as("待审核才允许客户撤销").isTrue();
        assertThat(applied.getRequestedAmount()).as("申请金额 = 申请数量 × 锁定价").isEqualByComparingTo("2.4000");

        // 可退数量上限 = 实数量 3.0000 − 在途申请 2.0000 = 1.0000，再申请 2.0000 必须被拒
        assertThatThrownBy(() -> apply(fixture, "2.0000", "flow-over"))
                .isInstanceOf(ScmBusinessException.class)
                .as("累计申请不得超过实数量");

        // 后台审批：生成应退净额的退款单
        asBackOffice();
        OrderReturnDetailVO approved = approve(fixture);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        Long refundId = refundIdOf(fixture.orderId());
        assertThat(refundId).as("批准后必须生成退款单").isNotNull();
        assertThat(jdbc.queryForObject("SELECT refund_amount FROM xsy_order_refund WHERE id = ?",
                BigDecimal.class, refundId)).isEqualByComparingTo("2.4000");

        // 后台收货：库存按历史销售出库成本回补（入库 10 → 出库 3 → 退回 2 = 9）
        receiveReturn(fixture, approved, "2.0000");
        assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity())
                .as("退回入库必须把数量加回余额").isEqualByComparingTo("9.0000");
        assertThat(jdbc.queryForObject("SELECT i.unit_cost FROM xsy_order_return_receipt_item i"
                        + " JOIN xsy_order_return_receipt r ON r.id = i.receipt_id WHERE r.return_id = ?",
                BigDecimal.class, approved.getReturnId()))
                .as("退回成本取历史销售出库成本，而不是当前市价").isEqualByComparingTo("6.2000");

        // 后台完成退款：退款单置为 COMPLETED 并留下订单审计
        completeRefund(refundId);
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_order_refund WHERE id = ?",
                String.class, refundId)).isEqualTo("COMPLETED");
        assertThat(operationTypesOf(fixture.orderId()))
                .as("售后申请与退款完成都必须在订单审计里留痕")
                .contains("RETURN", "REFUND");

        // 客户侧投影：正式事实派生展示分类，本夹具无支付 → 资金未到 → 退款处理中
        MallAfterSaleVO finalView = mallAfterSaleView(fixture, approved.getReturnId());
        assertThat(finalView.getStatus()).isEqualTo("APPROVED");
        assertThat(finalView.getDisplayStatus())
                .as("有退款单但资金未到账，展示分类必须是「退款处理中」而不是「已退款」")
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING.name());
        assertThat(finalView.getCancelable()).as("已审核的售后不能再撤销").isFalse();
        assertThat(finalView.getApprovedAmount()).isEqualByComparingTo("2.4000");
        assertThat(finalView.getRefund()).isNotNull();
        assertThat(finalView.getRefund().getStatus()).isEqualTo("COMPLETED");
        assertThat(finalView.getRefund().getRefundAmount()).isEqualByComparingTo("2.4000");
        assertThat(finalView.getRefund().getCompletedAt())
                .as("退款单完成但资金未到账时，资金时间必须为空而不是伪造时间")
                .isNull();
        assertThat(finalView.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getApprovedQuantity()).isEqualByComparingTo("2.0000"));
        assertThat(progressText(finalView, "REFUND")).isEqualTo("退款处理中");
    }

    // ------------------------------------------------------------------
    // 资金到账：纯余额支付的订单走到 REFUNDED
    // ------------------------------------------------------------------

    @Test
    @DisplayName("纯余额支付的订单：退款完成即把余额原路返还，客户侧展示转为已退款且资金时间不再是空")
    void balancePaidOrderReachesRefundedOnceTheWalletReturnLands() {
        // 余额支付的钱包返还走的是「与退款完成同事务」的旧钱包路径，因此这里不再人工执行资金分摊。
        AfterSaleFixture fixture = confirmedMallOrderWithCost("refunded", this::payByBalance);

        MallAfterSaleVO applied = apply(fixture, "2.0000", "refunded-apply");
        asBackOffice();
        OrderReturnDetailVO approved = approve(fixture);
        receiveReturn(fixture, approved, "2.0000");
        completeRefund(refundIdOf(fixture.orderId()));

        // 退款完成时刻与钱包返还流水的发生时刻必须一致：这条路径由 hasInvalidReturns 强校验
        OffsetDateTime refundCompletedAt = jdbc.queryForObject(
                "SELECT completed_at FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE",
                OffsetDateTime.class, fixture.orderId());
        OffsetDateTime walletOccurredAt = jdbc.queryForObject(
                "SELECT m.occurred_at FROM xsy_customer_balance_movement m"
                        + " JOIN xsy_order_refund r ON m.source_id = r.id WHERE r.order_id = ?"
                        + " AND m.source_type = 'ORDER_REFUND' AND m.type = 'REFUND' AND m.direction = 'CREDIT'",
                OffsetDateTime.class, fixture.orderId());
        assertThat(walletOccurredAt)
                .as("同事务返还的流水时间必须等于退款完成时刻，否则 hasInvalidReturns 会把这条返还判成脏数据")
                .isEqualTo(refundCompletedAt);

        MallAfterSaleVO finalView = mallAfterSaleView(fixture, approved.getReturnId());
        assertThat(finalView.getStatus()).isEqualTo("APPROVED");
        assertThat(finalView.getDisplayStatus())
                .as("资金已实际到账，展示分类必须从退款处理中前进到已退款")
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED.name());
        assertThat(finalView.getRefund()).isNotNull();
        assertThat(finalView.getRefund().getStatus()).isEqualTo("COMPLETED");
        assertThat(finalView.getRefund().getCompletedAt())
                .as("资金到账后必须给出真实到账时间")
                .isEqualTo(walletOccurredAt);
        assertThat(finalView.getRefund().getAllocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.getMethod()).as("原路退回：这笔钱本来就是从余额出的").isEqualTo("BALANCE");
            assertThat(allocation.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("2.4000");
            assertThat(allocation.getSucceededAt()).isEqualTo(walletOccurredAt);
        });
        assertThat(progressText(finalView, "REFUND")).isEqualTo("退款完成");
        assertThat(applied.getDisplayStatus()).as("申请当时的展示分类是待审核").isEqualTo("REVIEWING");
    }

    // ------------------------------------------------------------------
    // 撤销：释放可退额度
    // ------------------------------------------------------------------

    @Test
    @DisplayName("客户撤销待审核申请后额度回到可申请状态，且撤销同样留审计")
    void customerCancelReleasesTheReturnableQuota() {
        AfterSaleFixture fixture = confirmedMallOrderWithCost("cancel");

        MallAfterSaleVO applied = apply(fixture, "2.0000", "cancel-apply");
        MallAfterSaleVO cancelled = mallAfterSaleService.cancel(fixture.customer(), applied.getReturnId(),
                prefix + ":cancel");

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getDisplayStatus()).isEqualTo(MallAfterSaleDisplayStatusEnum.CANCELLED.name());
        assertThat(cancelled.getCancelable()).isFalse();

        // 额度已释放：原来超限的 2.0000 现在可以申请
        MallAfterSaleVO reapplied = apply(fixture, "2.0000", "cancel-reapply");
        assertThat(reapplied.getStatus()).isEqualTo("PENDING");
        assertThat(reapplied.getReturnId()).isNotEqualTo(applied.getReturnId());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record AfterSaleFixture(Long skuId, Long warehouseId, Long customerId, Long orderId, Long orderItemId,
                                    RequestMallCustomer customer) {
    }

    /**
     * 一张「已确认且有销售出库成本」的商城订单。
     *
     * <p>商城下单只走到 PENDING（非标品等后台录实重），确认与出库都由后台完成 ——
     * 这正是真实运营顺序，也让退货收货能取到历史出库成本。
     */
    private AfterSaleFixture confirmedMallOrderWithCost(String tag) {
        return confirmedMallOrderWithCost(tag, (orderId, customerId) -> {
        });
    }

    /**
     * 同上，并允许在确认之前登记订单的支付事实。
     *
     * <p>支付事实决定退款完成时走哪条资金路径：纯余额支付由旧钱包路径同事务原路返还，
     * 混合资金才需要人工执行资金分摊。回调拿到订单与客户 id，因为支付意图要挂在订单上、金额取订单结算额。
     */
    private AfterSaleFixture confirmedMallOrderWithCost(String tag, java.util.function.BiConsumer<Long, Long> pay) {
        Long skuId = newOnShelfSku("mas-" + tag);
        W6Fixture inbound = inboundFixture("mas-" + tag, skuId, "10.0000");
        confirmReceipt(inbound.receipt().getId(), "10.0000");

        Long customerId = newCustomer();
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);
        Long addressId = mallAddressService.create(customer, mallAddress()).getFirst().getAddressId();

        SmartRequestUtil.setRequestUser(customer);
        mallCartService.add(customer, cartAdd(skuId, "3.0000"));
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallOrderSubmitVO submitted = mallCheckoutService.submit(customer,
                submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":mall:" + tag);
        Long orderId = submitted.getOrderId();
        assertThat(submitted.getOrderSource()).isEqualTo("MALL");

        asBackOffice();
        confirmOrder(orderId, "3.0000");
        // 支付事实登记在确认之后：结算额要等实重录入才落定，而支付意图挂在已确认的订单上。
        pay.accept(orderId, customerId);
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        confirmSalesOutbound(orderId, orderItemId, skuId, "3.0000");
        assertThat(balanceRow(fixtureWarehouseId(), skuId).getQuantity())
                .as("出库后余额 = 入库 10 − 出库 3").isEqualByComparingTo("7.0000");

        return new AfterSaleFixture(skuId, fixtureWarehouseId(), customerId, orderId, orderItemId, customer);
    }

    /**
     * 登记一笔「纯余额支付」的订单资金事实：钱包账户 + 余额支付意图 + 交易 + 余额消费流水。
     *
     * <p>三条事实缺一不可：意图与交易是订单资金的取数来源（{@code selectOrderFunding}），
     * 消费流水是钱包返还的本金依据（{@code BalanceRefundService} 按它的金额算可退上限）。
     * 金额取订单结算额，保证返还金额不超过本金。
     */
    private void payByBalance(Long orderId, Long customerId) {
        BigDecimal orderAmount = jdbc.queryForObject(
                "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
        Long settlementCustomerId = jdbc.queryForObject(
                "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
        String settlementCustomerName = jdbc.queryForObject(
                "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);
        String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                orderId);

        Long accountId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                    created_by, updated_by)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

        // ck_payment_intent_method_provider：BALANCE 只能配 INTERNAL_BALANCE，且来源必须是销售订单。
        Long intentId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_intent
                    (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                     amount, method, provider, status, succeeded_at, created_by, updated_by)
                VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                settlementCustomerName, orderId, orderNo, orderAmount, operator(), operator());

        jdbc.update("""
                INSERT INTO xsy_payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?)
                """, prefix + "-BT-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                prefix + "-BPTN-" + UUID.randomUUID().toString().substring(0, 8), orderAmount, orderAmount,
                operator());

        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement
                    (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                     source_type, source_id, reason, occurred_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                    CURRENT_TIMESTAMP, ?, ?)
                """, prefix + "-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                settlementCustomerId, customerId, orderAmount, intentId, operator(), operator());
    }

    private String operator() {
        return "IT-MAS";
    }

    private MallAfterSaleVO apply(AfterSaleFixture fixture, String quantity, String keyTag) {
        SmartRequestUtil.setRequestUser(fixture.customer());
        MallAfterSaleApplyForm form = new MallAfterSaleApplyForm();
        form.setOrderId(fixture.orderId());
        form.setReason("商品破损");
        MallAfterSaleApplyForm.Item item = new MallAfterSaleApplyForm.Item();
        item.setOrderItemId(fixture.orderItemId());
        item.setQuantity(quantity);
        form.setItems(List.of(item));
        return mallAfterSaleService.apply(fixture.customer(), form, prefix + ":" + keyTag);
    }

    private MallAfterSaleVO mallAfterSaleView(AfterSaleFixture fixture, Long returnId) {
        SmartRequestUtil.setRequestUser(fixture.customer());
        return mallAfterSaleQueryService.detail(fixture.customer(), returnId);
    }

    private OrderReturnDetailVO approve(AfterSaleFixture fixture) {
        OrderReturnApproveForm form = new OrderReturnApproveForm();
        form.setReturnId(returnIdOf(fixture.orderId()));
        form.setVersion(returnVersion(form.getReturnId()));
        OrderReturnApproveItemForm item = new OrderReturnApproveItemForm();
        item.setOrderItemId(fixture.orderItemId());
        item.setApprovedQuantity("2.0000");
        form.setItems(List.of(item));
        return orderReturnService.approve(form, prefix + ":approve");
    }

    private void receiveReturn(AfterSaleFixture fixture, OrderReturnDetailVO approved, String quantity) {
        OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
        item.setReturnItemId(jdbc.queryForObject("SELECT id FROM xsy_order_return_item WHERE return_id = ?"
                + " AND deleted = FALSE ORDER BY id LIMIT 1", Long.class, approved.getReturnId()));
        item.setQuantity(quantity);
        item.setDisposition("RETURN_TO_STOCK");
        OrderReturnReceiveForm form = new OrderReturnReceiveForm();
        form.setReturnId(approved.getReturnId());
        form.setVersion(returnVersion(approved.getReturnId()));
        form.setWarehouseId(fixture.warehouseId());
        form.setItems(List.of(item));
        orderReturnReceiptService.receive(form, prefix + ":receive");
    }

    private void completeRefund(Long refundId) {
        OrderRefundCompleteForm form = new OrderRefundCompleteForm();
        form.setRefundId(refundId);
        form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_order_refund WHERE id = ?",
                Integer.class, refundId));
        form.setExternalReference("MAS-" + UUID.randomUUID());
        orderRefundService.complete(form, prefix + ":refund:" + refundId);
    }

    /**
     * 后台录实重并确认订单（非标品必须先生成实重）。
     */
    private void confirmOrder(Long orderId, String actualQuantity) {
        SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(order.getOrderId());
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity(actualQuantity);
        actual.setReason("IT 实重");
        SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual:" + orderId);

        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(weighted.getOrderId());
        confirm.setVersion(weighted.getVersion());
        salesOrderService.confirm(confirm, prefix + ":confirm:" + orderId);
    }

    /**
     * 销售出库并把它关联到订单行 —— 退货收货的历史成本就来自这条出库。
     */
    private void confirmSalesOutbound(Long orderId, Long orderItemId, Long skuId, String quantity) {
        InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
        outbound.setWarehouseId(fixtureWarehouseId());
        InventoryOutboundAddForm.Item item = new InventoryOutboundAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity(new BigDecimal(quantity));
        outbound.setItems(List.of(item));
        Long outboundId = inventoryOutboundService.create(outbound);
        jdbc.update("UPDATE xsy_inventory_outbound_item SET sales_order_id = ?, sales_order_item_id = ?"
                + " WHERE outbound_id = ?", orderId, orderItemId, outboundId);
        evictMybatisCache();
        inventoryOutboundService.confirm(outboundId);
    }

    private Long returnIdOf(Long orderId) {
        return jdbc.queryForObject("SELECT id FROM xsy_order_return WHERE order_id = ? AND deleted = FALSE"
                + " ORDER BY id DESC LIMIT 1", Long.class, orderId);
    }

    private Integer returnVersion(Long returnId) {
        return jdbc.queryForObject("SELECT version FROM xsy_order_return WHERE id = ?", Integer.class, returnId);
    }

    private Long refundIdOf(Long orderId) {
        return jdbc.queryForObject("SELECT id FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE",
                Long.class, orderId);
    }

    private List<String> operationTypesOf(Long orderId) {
        return jdbc.queryForList("SELECT operation_type FROM xsy_order_operation_log WHERE order_id = ?",
                String.class, orderId);
    }

    private static String progressText(MallAfterSaleVO afterSale, String code) {
        return afterSale.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                .map(MallOrderProgressNodeVO::getText).orElse(null);
    }

    /**
     * 切回后台身份。
     *
     * <p>登录商城会把请求身份换成 {@link RequestMallCustomer}，而商城身份不是员工，
     * 后台命令的权限闸门与操作人都会取不到人。
     */
    private void asBackOffice() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("MAS IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    // ------------------------------------------------------------------
    // 商城身份与账号
    // ------------------------------------------------------------------

    private MallAccount openMallAccount(Long customerId) {
        String phone = newPhone();
        MallAccountCreateForm form = new MallAccountCreateForm();
        form.setCustomerId(customerId);
        form.setLoginPhone(phone);
        form.setTemporaryPassword(TEMPORARY_PASSWORD);
        MallAccountVO account = mallAccountService.create(form);
        return new MallAccount(account.getAccountId(), phone);
    }

    private RequestMallCustomer signIn(MallAccount account) {
        MallLoginForm login = new MallLoginForm();
        login.setPhone(account.phone());
        login.setPassword(TEMPORARY_PASSWORD);
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            mallAuthService.login(login, "127.0.0.1");
            MallPasswordChangeForm change = new MallPasswordChangeForm();
            change.setOldPassword(TEMPORARY_PASSWORD);
            change.setNewPassword(NEW_PASSWORD);
            mallAuthService.changePassword(requestCustomer(account, 0), change);
            login.setPassword(NEW_PASSWORD);
            mallAuthService.login(login, "127.0.0.1");
        }
        return requestCustomer(account, 1);
    }

    private RequestMallCustomer requestCustomer(MallAccount account, int version) {
        RequestMallCustomer customer = mallAuthService.requestCustomer(
                MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
        assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
        return customer;
    }

    private static MallCartAddForm cartAdd(Long skuId, String quantity) {
        MallCartAddForm form = new MallCartAddForm();
        form.setSkuId(skuId);
        form.setQuantity(quantity);
        return form;
    }

    private static MallAddressSaveForm mallAddress() {
        MallAddressSaveForm form = new MallAddressSaveForm();
        form.setReceiverName("售后收货人");
        form.setReceiverPhone("13800000000");
        form.setProvinceName("上海市");
        form.setCityName("上海市");
        form.setDistrictName("浦东新区");
        form.setDetailAddress("IT 测试路 3 号");
        form.setIsDefault(true);
        return form;
    }

    private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
        MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
        form.setItems(List.of(checkoutItem(skuId, "3.0000")));
        form.setAddressId(addressId);
        return form;
    }

    private static MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
        MallOrderSubmitForm form = new MallOrderSubmitForm();
        form.setItems(List.of(checkoutItem(skuId, "3.0000")));
        form.setAddressId(addressId);
        form.setPriceFingerprint(priceFingerprint);
        form.setRemark("商城售后 IT");
        return form;
    }

    private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
        MallCheckoutItemForm item = new MallCheckoutItemForm();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private static String newPhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
        return "13" + (100_000_000L + suffix);
    }

    private record MallAccount(Long accountId, String phone) {
    }
}
