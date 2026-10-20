package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.address.service.MallMapService;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.mall.order.service.MallPaymentService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城余额支付闭环（PAY-01，真实 PostgreSQL + 真实服务）。
 *
 * <p>钉死三件事：
 * <ul>
 * <li><b>金额只由服务端算</b>：扣款额 = 实重 × 冻结锁定价 − 冻结优惠等比分摊（应付净额）再减已付；客户端不传金额，也没有部分支付；</li>
 * <li><b>只扣一次</b>：订单行锁把并发支付串行化，已付清再点与并发双击都扣不出第二笔；</li>
 * <li><b>资金事实同事务</b>：钱包 DEBIT、支付意图与交易、订单资金取数在同一笔提交里，任一步失败整笔回滚。</li>
 * </ul>
 *
 * <p><b>为什么必须跑真实库</b>：钱包余额是流水的代数和（{@code sumSignedByAccount}）、扣款要走 {@code FOR UPDATE} 的账户锁、
 * 已付金额又来自资金取数 SQL 的重算。这三件都在 SQL 与事务边界里，mock 掉任何一层都会把「扣了多少」变成对桩的断言。
 *
 * <p><b>夹具为什么直插钱包余额</b>：充值到账链（{@code createForBalanceRecharge} + 渠道回调）有自己的用例，
 * 本类要测的是「钱已经在钱包里之后，支付怎么扣」，因此用一笔 {@code CORRECTION} CREDIT 把余额铺好。
 */
@DisplayName("商城余额支付闭环（PAY-01，PG IT）")
class MallWalletPaymentPgIT extends ScmW5PgITBase {

    private static final String TEMPORARY_PASSWORD = "Mall@123456";
    private static final String NEW_PASSWORD = "Mall@654321";

    /** 下单 3.0000、实重 2.0000、客户价 1.2000：应付必须按实重算成 2.4000，而不是按订货量 3.6000。 */
    private static final String ORDERED_QUANTITY = "3.0000";
    private static final String ACTUAL_QUANTITY = "2.0000";
    private static final BigDecimal PAYABLE = new BigDecimal("2.4000");

    @Autowired
    private MallAccountService mallAccountService;

    @Autowired
    private MallAuthService mallAuthService;

    @Autowired
    private MallAddressService mallAddressService;

    @MockitoBean
    private MallMapService mapService;

    @BeforeEach
    void stubExternalGeocode() {
        MallMapLocationVO location = new MallMapLocationVO();
        location.setLongitude(new BigDecimal("121.544000"));
        location.setLatitude(new BigDecimal("31.221000"));
        location.setProvinceCode(310000);
        location.setCityCode(310100);
        location.setDistrictCode(310115);
        location.setProvinceName("上海市");
        location.setCityName("上海市");
        location.setDistrictName("浦东新区");
        when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
    }

    @Override
    protected Long newOnShelfSku(String suffix) {
        Long skuId = super.newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix + "-stock", skuId);
        var purchase = createDraftOrder(suffix + "-stock", supplierId, skuId, "100.0000", "6.2000");
        var receipt = submittedOrderReceipt(purchase.getId());
        var current = reloadReceipt(receipt.getId());
        var item = current.getItems().getFirst();
        purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(item.getId(), item.getVersion(), "100.0000")),
                prefix + ":stock:" + suffix);
        evictMybatisCache();
        return skuId;
    }

    @Autowired
    private MallCheckoutService mallCheckoutService;

    @Autowired
    private MallOrderQueryService mallOrderQueryService;

    @Autowired
    private MallPaymentService mallPaymentService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private SalesOrderQueryService salesOrderQueryService;

    @Test
    @DisplayName("客户支付：扣款额等于服务端算出的应付净额，钱包流水与资金事实同事务落库")
    void walletPaymentDeductsExactlyTheServerComputedPayable() {
        WalletOrder fixture = mallOrder("happy", "10.0000", ScmSettlementMethodEnum.WALLET, true);

        MallOrderVO before = view(fixture);
        assertThat(before.getSettlementMethod()).isEqualTo(ScmSettlementMethodEnum.WALLET.name());
        assertThat(before.getPayableAmount())
                .as("应付净额按实重算：实重 2.0000 × 1.2000，不是按订货量 3.0000")
                .isEqualByComparingTo(PAYABLE);
        assertThat(before.getPaidAmount()).isEqualByComparingTo("0.0000");

        MallOrderVO paid = pay(fixture, prefix + ":pay-happy");

        assertThat(paid.getPayableAmount()).isEqualByComparingTo(PAYABLE);
        assertThat(paid.getPaidAmount()).isEqualByComparingTo(PAYABLE);
        assertThat(walletDebits(fixture.walletId()))
                .as("钱包只被扣走应付净额，多一分少一分都说明金额不是服务端算的")
                .isEqualByComparingTo(PAYABLE);
        assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_payment_intent WHERE source_type = 'SALES_ORDER'"
                + " AND source_id = ? AND deleted = FALSE", String.class, fixture.orderId()))
                .as("余额在本地消费成功即推进到成功，没有外部渠道要等")
                .isEqualTo("SUCCEEDED");
        assertThat(paymentTransactionAmount(fixture.orderId())).isEqualByComparingTo(PAYABLE);

        assertThatThrownBy(() -> pay(fixture, prefix + ":pay-again"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .as("已付清再点必须被拒，而不是再扣一笔")
                        .isEqualTo(MallErrorCode.ORDER_ALREADY_PAID));
        assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
    }

    @Test
    @DisplayName("集团共享结算：子客户支付扣的是集团钱包，自己的余额一分不动")
    void sharedSettlementPaysFromGroupWallet() {
        asAdmin();
        // 走正式建档命令而不是改库：上级客户必须是集团类型、结算主体要指向集团，这些校验只在命令里跑
        Long groupId = customerService.add(groupForm(null));
        cooperate(groupId);
        jdbc.update("UPDATE xsy_customer SET shared_settlement_enabled=TRUE WHERE id = ?", groupId);
        Long groupWalletId = fundWallet(groupId, "50.0000");
        Long memberId = customerService.add(groupForm(groupId));
        cooperate(memberId);
        jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id=? WHERE id=?", seedWarehouseId(), memberId);
        Long memberWalletId = fundWallet(memberId, "999.0000");
        evictMybatisCache();

        WalletOrder fixture = mallOrderFor(memberId, memberWalletId, "group", ScmSettlementMethodEnum.WALLET, true);
        MallOrderVO paid = pay(fixture, prefix + ":pay-group");

        assertThat(paid.getPaidAmount()).isEqualByComparingTo(paid.getPayableAmount());
        assertThat(walletDebits(groupWalletId))
                .as("集团共享结算扣的是集团钱包")
                .isEqualByComparingTo(paid.getPayableAmount());
        assertThat(walletDebits(memberWalletId))
                .as("子客户自己的余额不该被动")
                .isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_customer_balance_movement"
                + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", Long.class, groupWalletId))
                .as("流水落在集团账上")
                .isEqualTo(groupId);
    }

    @Test
    @DisplayName("同一幂等键重放返回已付订单，钱包不出现第二笔扣款")
    void replayingTheSameIdempotencyKeyNeverDeductsASecondTime() {
        WalletOrder fixture = mallOrder("replay", "10.0000", ScmSettlementMethodEnum.WALLET, true);
        String key = prefix + ":pay-replay";

        pay(fixture, key);

        MallOrderVO replayed = pay(fixture, key);
        assertThat(replayed.getPaidAmount()).isEqualByComparingTo(PAYABLE);

        assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
        assertThat(walletDebits(fixture.walletId())).isEqualByComparingTo(PAYABLE);
        assertThat(paymentIntentCount(fixture.orderId()))
                .as("重放不能造出第二个支付意图")
                .isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("余额不足：在写任何资金事实之前失败，订单仍是未付")
    void insufficientWalletBalanceFailsBeforeAnyFundingFact() {
        // 「整笔回滚」只在真提交时才暴露：测试外层事务会把失败行留在库里等着回滚，断言就没有意义。
        WalletOrder fixture = mallOrder("short", "10.0000", ScmSettlementMethodEnum.WALLET, true);
        spendWallet(fixture, "9.0000");
        assertThat(walletBalance(fixture.walletId())).as("余额已降到应付之下").isEqualByComparingTo("1.0000");

        assertThatThrownBy(() -> pay(fixture, prefix + ":pay-short"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(BalanceErrorCode.BALANCE_INSUFFICIENT));

        assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isZero();
        assertThat(paymentIntentCount(fixture.orderId()))
                .as("整笔支付是一个事务：意图插入必须随失败一起回滚")
                .isZero();
        assertThat(view(fixture).getPaidAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("未确认、非余额结算、别人的订单：都在扣款之前被挡下")
    void unconfirmedNonWalletAndForeignOrdersAreRejectedBeforeDeduction() {
        WalletOrder unconfirmed = mallOrder("pending", "10.0000", ScmSettlementMethodEnum.WALLET, false);
        assertThatThrownBy(() -> pay(unconfirmed, prefix + ":pay-pending"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .as("确认前实重未齐，没有权威应付口径，也就没有可支付的金额")
                        .isEqualTo(MallErrorCode.ORDER_NOT_PAYABLE));
        assertThat(walletMovementCount(unconfirmed.walletId(), "CONSUME")).isZero();

        WalletOrder credit = mallOrder("credit", "10.0000", ScmSettlementMethodEnum.CREDIT, true);
        assertThatThrownBy(() -> pay(credit, prefix + ":pay-credit"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .as("账期订单的钱走应收，不能从钱包扣")
                        .isEqualTo(MallErrorCode.ORDER_SETTLEMENT_NOT_WALLET));
        assertThat(walletMovementCount(credit.walletId(), "CONSUME")).isZero();

        WalletOrder foreign = mallOrder("foreign", "10.0000", ScmSettlementMethodEnum.WALLET, true);
        RequestMallCustomer stranger = signIn(openMallAccount(newCustomer()));
        assertThatThrownBy(() -> mallPaymentService.pay(stranger, foreign.orderId(), prefix + ":pay-foreign"))
                .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .as("归属按订单客户收窄，别人的订单连存在性都不该暴露")
                        .isEqualTo(MallErrorCode.ORDER_NOT_FOUND));
        assertThat(walletMovementCount(foreign.walletId(), "CONSUME")).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("并发双击：订单行锁把两次支付串行化，钱包只被扣一次")
    void concurrentDoubleClickDeductsExactlyOnce() throws Exception {
        // 本用例要真并发，因此关掉测试外层事务：夹具会真实提交，只应在授权的隔离库运行。
        WalletOrder fixture = mallOrder("race", "10.0000", ScmSettlementMethodEnum.WALLET, true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> outcomes = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                final String key = prefix + ":race-" + index;
                outcomes.add(pool.submit(() -> {
                    SmartRequestUtil.setRequestUser(fixture.customer());
                    ready.countDown();
                    go.await();
                    try {
                        mallPaymentService.pay(fixture.customer(), fixture.orderId(), key);
                        return "PAID";
                    } catch (ScmBusinessException rejected) {
                        return String.valueOf(rejected.getErrorCode().getCode());
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }
            assertThat(results)
                    .as("恰好一侧成功，另一侧必须是被业务规则拒绝，而不是死锁、超时或裸约束异常")
                    .containsExactlyInAnyOrder("PAID", String.valueOf(MallErrorCode.ORDER_ALREADY_PAID.getCode()));
        } finally {
            pool.shutdownNow();
        }

        assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
        assertThat(walletDebits(fixture.walletId())).isEqualByComparingTo(PAYABLE);
        assertThat(paymentIntentCount(fixture.orderId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 新建的客户是「潜在」状态，不能交易；商城链路要求「合作中」。 */
    private void cooperate(Long customerId) {
        var status = new com.xsy.scm.customer.domain.form.CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
    }

    /** 集团（{@code parentId=null}）或集团成员；成员的上级与结算主体都指向集团。 */
    private com.xsy.scm.customer.domain.form.CustomerAddForm groupForm(Long parentId) {
        var form = new com.xsy.scm.customer.domain.form.CustomerAddForm();
        form.setName(prefix + "-G" + java.util.UUID.randomUUID().toString().substring(0, 6));
        form.setCustomerTypeId(customerTypeId(parentId == null ? "GROUP" : "ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setSellerId(anyEmployeeId());
        form.setParentCustomerId(parentId);
        form.setSettlementCustomerId(parentId);
        return form;
    }

    private record WalletOrder(Long orderId, Long customerId, Long walletId, RequestMallCustomer customer) {
    }

    /**
     * 一张指定结算方式的商城订单。
     *
     * <p>走真实商城链路：开账号 → 登录 → 建地址 → 结算预览 → 下单 → 后台录实重 → 确认。
     * 支付能力只认已确认订单，因此 {@code confirm} 为假时得到的是「待确认」这一侧。
     */
    private WalletOrder mallOrder(String tag, String walletCredit, ScmSettlementMethodEnum settlementMethod,
            boolean confirm) {
        // 建客户与挂商品都要员工数据范围；上一次 signIn 留下的商城身份会在这里被拒，因此先切回后台。
        asAdmin();
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id=? WHERE id=?", seedWarehouseId(),
                customerId);
        Long walletId = fundWallet(customerId, walletCredit);
        if (settlementMethod == ScmSettlementMethodEnum.CREDIT) {
            // 账期可用性看正式授信口径：没有额度时结算页就不会把账期标成可选，单也下不出来
            jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                    customerId);
            evictMybatisCache();
        }
        return mallOrderFor(customerId, walletId, tag, settlementMethod, confirm);
    }

    /**
     * 用既有客户走完商城链路下单；客户由调用方准备（例如集团共享结算的成员客户）。
     */
    private WalletOrder mallOrderFor(Long customerId, Long walletId, String tag,
            ScmSettlementMethodEnum settlementMethod, boolean confirm) {
        asAdmin();
        Long skuId = newOnShelfSku("pay-" + tag);
        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
        form.setSettlementMethod(settlementMethod.name());
        Long orderId = mallCheckoutService.submit(customer, form, prefix + ":submit:" + tag).getOrderId();
        if (confirm) {
            confirmOrder(orderId, tag);
        }
        return new WalletOrder(orderId, customerId, walletId, customer);
    }

    /**
     * 预置钱包余额。
     *
     * <p>用一笔 {@code CORRECTION} CREDIT 铺余额而不是走充值链：充值到账有自己的用例，
     * 本类要测的是「钱已经在钱包里之后支付怎么扣」；{@code signedBalance} 取的是流水代数和，因此这笔同样构成真实可用余额。
     */
    private Long fundWallet(Long customerId, String credit) {
        Long walletId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                """, Long.class, customerId);
        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                    customer_id, type, direction, amount, reason, occurred_at)
                VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', ?, 'PAY-01 IT 预置余额', now())
                """, prefix + "-CR-" + UUID.randomUUID(), walletId, customerId, customerId, new BigDecimal(credit));
        evictMybatisCache();
        return walletId;
    }

    /** 把钱包里的钱花掉一部分：余额是流水代数和，因此一笔 DEBIT 就足以让支付撞上余额不足。 */
    private void spendWallet(WalletOrder fixture, String amount) {
        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                    customer_id, type, direction, amount, reason, occurred_at)
                VALUES (?, ?, ?, ?, 'CORRECTION', 'DEBIT', ?, 'PAY-01 IT 花掉余额', now())
                """, prefix + "-DB-" + UUID.randomUUID(), fixture.walletId(), fixture.customerId(),
                fixture.customerId(), new BigDecimal(amount));
        evictMybatisCache();
    }

    private BigDecimal walletBalance(Long walletId) {
        BigDecimal credits = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                + " WHERE account_id = ? AND direction = 'CREDIT' AND deleted = FALSE", BigDecimal.class, walletId);
        BigDecimal debits = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", BigDecimal.class, walletId);
        return credits.subtract(debits).setScale(4, RoundingMode.HALF_UP);
    }

    /** 后台录实重并确认：实重与下单量不同，才能证明应付是按实重算的。 */
    private void confirmOrder(Long orderId, String tag) {
        asAdmin();
        SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(orderId);
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity(ACTUAL_QUANTITY);
        actual.setReason("PAY-01 IT 实重");
        SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual:" + tag);
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        salesOrderService.confirm(confirm, prefix + ":confirm:" + tag);
        evictMybatisCache();
    }

    private MallOrderVO view(WalletOrder fixture) {
        SmartRequestUtil.setRequestUser(fixture.customer());
        return mallOrderQueryService.detail(fixture.customer(), fixture.orderId());
    }

    private MallOrderVO pay(WalletOrder fixture, String idempotencyKey) {
        SmartRequestUtil.setRequestUser(fixture.customer());
        return mallPaymentService.pay(fixture.customer(), fixture.orderId(), idempotencyKey);
    }

    private BigDecimal walletDebits(Long walletId) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", BigDecimal.class, walletId);
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private Integer walletMovementCount(Long walletId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement WHERE account_id = ?"
                + " AND type = ? AND deleted = FALSE", Integer.class, walletId, type);
    }

    private Integer paymentIntentCount(Long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_payment_intent WHERE source_type = 'SALES_ORDER'"
                + " AND source_id = ? AND deleted = FALSE", Integer.class, orderId);
    }

    private BigDecimal paymentTransactionAmount(Long orderId) {
        return jdbc.queryForObject("SELECT t.amount FROM xsy_payment_transaction t JOIN xsy_payment_intent i"
                + " ON i.id = t.intent_id WHERE i.source_type = 'SALES_ORDER' AND i.source_id = ?"
                + " AND t.deleted = FALSE", BigDecimal.class, orderId);
    }

    // ------------------------------------------------------------------
    // 商城身份与后台身份
    // ------------------------------------------------------------------

    /** 登录商城会把请求身份换成商城客户，后台命令前必须切回员工身份。 */
    private void asAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("PAY-01 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

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

    private static MallAddressSaveForm address() {
        MallAddressSaveForm form = new MallAddressSaveForm();
        form.setReceiverName("余额支付收货人");
        form.setReceiverPhone("13800000000");
        form.setProvinceName("上海市");
        form.setCityName("上海市");
        form.setDistrictName("浦东新区");
        form.setDetailAddress("IT 测试路 2 号");
        form.setIsDefault(true);
        form.setLongitude(new BigDecimal("121.544000"));
        form.setLatitude(new BigDecimal("31.221000"));
        return form;
    }

    private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
        MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
        form.setItems(List.of(checkoutItem(skuId, ORDERED_QUANTITY)));
        form.setAddressId(addressId);
        return form;
    }

    private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
        MallOrderSubmitForm form = new MallOrderSubmitForm();
        form.setItems(List.of(checkoutItem(skuId, ORDERED_QUANTITY)));
        form.setAddressId(addressId);
        form.setWarehouseId(seedWarehouseId());
        form.setPriceFingerprint(priceFingerprint);
        form.setRemark("PAY-01 IT");
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
