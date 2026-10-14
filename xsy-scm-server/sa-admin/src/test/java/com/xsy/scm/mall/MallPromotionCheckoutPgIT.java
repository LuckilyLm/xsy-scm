package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
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
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.domain.vo.MallSettlementOptionVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.service.PromotionCouponService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 商城营销优惠「领券 → 结算预览 → 下单 → 后台确认 → 客户查看优惠结果」的完整闭环（真实 PostgreSQL + 真实服务）。
 *
 * <p>
 * 这条链把 MKT-01~05 的关键口径一次钉死：
 * <ul>
 * <li><b>预览不占用</b>（ADR-009）：结算预览带券只做试算，券实例必须仍为 {@code AVAILABLE}；</li>
 * <li><b>下单不冻结</b>：下单把预选券写到订单上，冻结只发生在后台确认命令内；</li>
 * <li><b>确认才冻结</b>：确认成功后券转 {@code RESERVED}，并落 {@code order_discount} 正式优惠事实；</li>
 * <li><b>客户可见</b>：客户侧订单详情能读到优惠金额与券名，金额与后台一致。</li>
 * </ul>
 */
class MallPromotionCheckoutPgIT extends ScmW5PgITBase {

    private static final String TEMPORARY_PASSWORD = "Mall@123456";
    private static final String NEW_PASSWORD = "Mall@654321";

    @Autowired
    private MallAccountService mallAccountService;
    @Autowired
    private MallAuthService mallAuthService;
    @Autowired
    private MallAddressService mallAddressService;
    @Autowired
    private MallCheckoutService mallCheckoutService;
    @Autowired
    private MallOrderQueryService mallOrderQueryService;
    @Autowired
    private PromotionCouponService promotionCouponService;
    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private SalesOrderQueryService salesOrderQueryService;
    @Autowired
    private OrderCreditService orderCreditService;

    /** 建一张「可自助领取 + 立即生效」的定额券。 */
    private Long activeClaimableCoupon(String tag, String discountValue) {
        PromotionCouponForm couponForm = new PromotionCouponForm();
        couponForm.setCouponCode(prefix + "-" + tag);
        couponForm.setCouponName(prefix + "商城自助领券");
        couponForm.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
        couponForm.setDiscountValue(new BigDecimal(discountValue));
        couponForm.setMinOrderAmount(BigDecimal.ZERO);
        couponForm.setClaimable(true);
        couponForm.setPerCustomerLimit(1);
        couponForm.setTotalLimit(100);
        couponForm.setValidFrom(OffsetDateTime.now().minusMinutes(1));
        couponForm.setValidTo(OffsetDateTime.now().plusDays(1));
        Long couponId = promotionCouponService.save(couponForm);
        PromotionStatusForm status = new PromotionStatusForm();
        status.setVersion(0);
        status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
        promotionCouponService.updateStatus(couponId, status);
        return couponId;
    }

    @Test
    @DisplayName("客户领券 → 结算预览 → 下单 → 后台确认 → 客户看到优惠")
    void claimedCouponFlowsThroughPreviewSubmitAndConfirmation() {
        Long skuId = newOnShelfSku("mkt");
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
        jdbc.update("UPDATE xsy_customer SET credit_limit = 2.0000 WHERE id = ?", customerId);
        evictMybatisCache();

        // 1. 后台建一张可自助领取的券并启用
        Long couponId = activeClaimableCoupon("MKT-CLAIM", "0.5000");

        // 2. 客户登录（含首次改密）
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);

        // 3. 客户领券
        Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":claim");
        assertThat(instanceId).as("领券必须返回券实例").isNotNull();
        assertThat(instanceStatus(instanceId)).isEqualTo("AVAILABLE");

        // 4. 结算预览带券：只试算，不占用
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
        previewForm.setCouponInstanceId(instanceId);
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
        assertThat(preview.getPromotion()).as("预览必须返回优惠明细").isNotNull();
        assertThat(preview.getPromotion().getCouponDiscount()).isEqualByComparingTo("0.5000");
        assertThat(preview.getPromotion().getPayableAmount()).isEqualByComparingTo("1.9000");
        MallSettlementOptionVO creditOption = preview.getSettlementOptions().stream()
                .filter(option -> "CREDIT".equals(option.getMethod())).findFirst().orElseThrow();
        assertThat(creditOption.getEnabled())
                .as("账期按优惠后 1.9 占用（额度 2.0 够），不再按优惠前 2.4 保守判定")
                .isTrue();
        assertThat(creditOption.getAvailableAmount()).isEqualByComparingTo("2.0000");
        assertThat(preview.getPromotion().getCouponInstanceId()).isEqualTo(instanceId);
        assertThat(instanceStatus(instanceId)).as("预览不得占用券（ADR-009）").isEqualTo("AVAILABLE");

        // 5. 下单携带预选券：写入订单，但券仍不被冻结
        MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
        submitForm.setCouponInstanceId(instanceId);
        submitForm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
        var submitted = mallCheckoutService.submit(customer, submitForm, prefix + ":submit");
        assertThat(submitted.getStatus()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT mall_coupon_instance_id FROM xsy_sales_order WHERE id = ?",
                Long.class, submitted.getOrderId())).isEqualTo(instanceId);
        assertThat(instanceStatus(instanceId)).as("下单不冻结券，冻结只在确认命令内").isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id = ?",
                Integer.class, submitted.getOrderId())).as("下单阶段不得产生优惠事实").isZero();

        // 6. 后台确认：非标品先录实重，再确认（确认命令内冻结优惠）
        asAdmin();
        SalesOrderDetailVO order = salesOrderQueryService.detail(submitted.getOrderId());
        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(order.getOrderId());
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity("2.0000");
        actual.setReason("营销闭环 IT 实重");
        SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual");
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(weighted.getOrderId());
        confirm.setVersion(weighted.getVersion());
        salesOrderService.confirm(confirm, prefix + ":confirm");

        assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?", String.class,
                submitted.getOrderId())).isEqualTo("CONFIRMED");
        assertThat(instanceStatus(instanceId)).as("确认后券被占用为 RESERVED").isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT coupon_instance_id FROM xsy_order_discount WHERE sales_order_id = ?",
                Long.class, submitted.getOrderId())).as("确认后落正式优惠事实并绑定券实例").isEqualTo(instanceId);

        // 7. 客户侧订单详情：能看到优惠金额与券名，且与后台一致
        SmartRequestUtil.setRequestUser(customer);
        MallOrderVO mine = mallOrderQueryService.detail(customer, submitted.getOrderId());
        assertThat(mine.getStatus()).isEqualTo("CONFIRMED");
        assertThat(mine.getDiscount()).as("客户订单详情必须带回优惠").isNotNull();
        assertThat(mine.getDiscount().getDiscountAmount()).isEqualByComparingTo("0.5000");
        assertThat(mine.getDiscount().getCouponName()).isEqualTo(prefix + "商城自助领券");
        assertThat(mine.getOrderedTotalAmount()).isEqualByComparingTo("2.4000");
    }

    @Test
    @DisplayName("结算方式：按配置给出可用性，选定后随订单持久化，不可用的方式被拒绝")
    void settlementOptionsFollowCustomerConfiguration() {
        Long skuId = newOnShelfSku("settle");
        Long customerId = newCustomer();
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();

        // 1. 初始：未配置授信、钱包为空、货到付款未开通 → 三种方式都不可选
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        Map<String, MallSettlementOptionVO> options = preview.getSettlementOptions().stream()
                .collect(Collectors.toMap(MallSettlementOptionVO::getMethod, Function.identity()));
        assertThat(options).containsOnlyKeys("WALLET", "CREDIT", "COD");
        assertThat(options.get("CREDIT").getEnabled()).as("未配置授信额度时账期不可选").isFalse();
        assertThat(options.get("CREDIT").getDisabledReason()).isNotBlank();
        assertThat(options.get("WALLET").getEnabled()).as("钱包余额不足时不可选").isFalse();
        assertThat(options.get("WALLET").getAvailableAmount()).isEqualByComparingTo("0.0000");
        assertThat(options.get("COD").getEnabled()).as("货到付款默认关闭").isFalse();
        assertThat(options.get("COD").getDisabledReason()).contains("未开通");
        assertThat(options.get("CREDIT").getAmountVisible()).as("独立结算客户可见自己的额度").isTrue();
        assertThat(options.get("WALLET").getAmountVisible()).isTrue();

        // 2. 不可用的方式直接拒绝下单（服务端按当前金额重算，不采信旧预览结论）
        MallOrderSubmitForm blocked = submitForm(skuId, addressId, preview.getPriceFingerprint());
        blocked.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
        assertThatThrownBy(() -> mallCheckoutService.submit(customer, blocked, prefix + ":settle-blocked"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_SETTLEMENT_METHOD_UNAVAILABLE));

        // 3. 配好授信后账期可选，并给出可用额度
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                customerId);
        evictMybatisCache();
        MallCheckoutPreviewVO withCredit = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallSettlementOptionVO credit = withCredit.getSettlementOptions().stream()
                .filter(option -> "CREDIT".equals(option.getMethod())).findFirst().orElseThrow();
        assertThat(credit.getEnabled()).isTrue();
        assertThat(credit.getAvailableAmount()).isEqualByComparingTo("100.0000");

        // 4. 后台明确开通后才可选货到付款；下单仅持久化约定，不产生资金事实
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
        evictMybatisCache();
        assertThat(mallCheckoutService.preview(customer, previewForm(skuId, addressId)).getSettlementOptions())
                .filteredOn(option -> "COD".equals(option.getMethod()))
                .singleElement().extracting(MallSettlementOptionVO::getEnabled).isEqualTo(true);
        MallOrderSubmitForm submit = submitForm(skuId, addressId, withCredit.getPriceFingerprint());
        submit.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
        var submitted = mallCheckoutService.submit(customer, submit, prefix + ":settle-cod");
        assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id = ?",
                String.class, submitted.getOrderId())).isEqualTo("COD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id = ?",
                Integer.class, submitted.getOrderId())).isZero();
    }

    @Test
    @DisplayName("余额订单确认不受零授信阻断；本阶段仍不产生钱包扣款事实")
    void walletConfirmationDoesNotUseCreditApproval() {
        Long skuId = newOnShelfSku("wallet");
        Long customerId = newCustomer();
        Long walletId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                """, Long.class, customerId);
        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                    customer_id, type, direction, amount, reason, occurred_at)
                VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', 10.0000, '结算方式回归', now())
                """, prefix + "-WALLET", walletId, customerId, customerId);
        evictMybatisCache();
        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        assertThat(preview.getSettlementOptions()).filteredOn(option -> "WALLET".equals(option.getMethod()))
                .singleElement().extracting(MallSettlementOptionVO::getEnabled).isEqualTo(true);
        MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
        form.setSettlementMethod(ScmSettlementMethodEnum.WALLET.name());
        Long orderId = mallCheckoutService.submit(customer, form, prefix + ":wallet-submit").getOrderId();

        SalesOrderDetailVO weighted = recordActualQuantity(orderId, "wallet");
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        assertThat(salesOrderService.confirm(confirm, prefix + ":wallet-confirm").getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id = ?", String.class,
                orderId)).isEqualTo("WALLET");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement WHERE account_id = ?"
                + " AND direction = 'DEBIT'", Integer.class, walletId)).as("支付扣款由 PAY-01 处理").isZero();
    }

    @Test
    @DisplayName("账期订单的确认按净额放行：额度只够优惠后净额、不够优惠前毛额时，确认不再被毛额挡住")
    void creditConfirmationUsesTheNetAmountFrozenInTheSameCommand() {
        Long skuId = newOnShelfSku("mkt-credit");
        Long customerId = newCustomer();
        // 额度 2.0000：够付优惠后 1.9000，不够付优惠前 2.4000 —— 优惠越大，旧口径挡得越狠
        jdbc.update("UPDATE xsy_customer SET credit_limit = 2.0000 WHERE id = ?", customerId);
        evictMybatisCache();
        Long couponId = activeClaimableCoupon("MKT-CREDIT", "0.5000");

        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":credit-claim");
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
        previewForm.setCouponInstanceId(instanceId);
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
        assertThat(preview.getPromotion().getPayableAmount()).isEqualByComparingTo("1.9000");

        MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
        submitForm.setCouponInstanceId(instanceId);
        submitForm.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
        Long orderId = mallCheckoutService.submit(customer, submitForm, prefix + ":credit-submit").getOrderId();

        SalesOrderDetailVO weighted = recordActualQuantity(orderId, "mkt-credit");
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        assertThat(salesOrderService.confirm(confirm, prefix + ":credit-confirm").getStatus())
                .as("确认按同一命令内冻结出来的净额 1.9000 判定，而不是优惠前毛额 2.4000")
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                BigDecimal.class, orderId)).as("订单上的结算额仍然是优惠前毛额").isEqualByComparingTo("2.4000");
        assertThat(instanceStatus(instanceId)).as("确认后券被占用为 RESERVED").isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM xsy_order_discount WHERE sales_order_id = ?",
                BigDecimal.class, orderId)).isEqualByComparingTo("0.5000");

        // 确认后的额度占用同样按净额：签收前的这段时间占用不能虚高
        assertThat(orderCreditService.checkSelfSettlement(customerId, BigDecimal.ZERO).getConfirmedOrderAmount())
                .isEqualByComparingTo("1.9000");
    }

    @Test
    @DisplayName("COD 下单后若后台撤销客户资格，订单确认必须拒绝")
    void codPermissionIsRecheckedAtConfirmation() {
        Long skuId = newOnShelfSku("cod");
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
        evictMybatisCache();
        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
        form.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
        Long orderId = mallCheckoutService.submit(customer, form, prefix + ":cod-submit").getOrderId();
        SalesOrderDetailVO weighted = recordActualQuantity(orderId, "cod");
        jdbc.update("UPDATE xsy_customer SET cod_allowed = FALSE WHERE id = ?", customerId);
        evictMybatisCache();
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":cod-confirm"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED));
    }

    @Test
    @DisplayName("COD 下单后出现逾期账单，订单确认必须按客户风险限制拒绝")
    void codOverdueIsRecheckedAtConfirmation() {
        Long skuId = newOnShelfSku("cod-overdue");
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
        evictMybatisCache();
        Long oldOrderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
        form.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
        Long orderId = mallCheckoutService.submit(customer, form, prefix + ":cod-overdue-submit").getOrderId();
        SalesOrderDetailVO weighted = recordActualQuantity(orderId, "cod-overdue");
        Long receivableId = jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', 1.0000, now()
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, prefix + "-COD-OVERDUE", oldOrderId);
        jdbc.update("UPDATE xsy_finance_receivable SET due_date = CURRENT_DATE - 1 WHERE id = ?", receivableId);
        evictMybatisCache();
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":cod-overdue-confirm"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED));
    }

    @Test
    @DisplayName("账期下单后额度被撤销，订单确认不能沿用旧预览放行")
    void creditIsRecheckedAtConfirmation() {
        Long skuId = newOnShelfSku("credit");
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET credit_limit = 10.0000 WHERE id = ?", customerId);
        evictMybatisCache();
        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
        form.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
        Long orderId = mallCheckoutService.submit(customer, form, prefix + ":credit-submit").getOrderId();
        SalesOrderDetailVO weighted = recordActualQuantity(orderId, "credit");
        jdbc.update("UPDATE xsy_customer SET credit_limit = 0 WHERE id = ?", customerId);
        evictMybatisCache();
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(orderId);
        confirm.setVersion(weighted.getVersion());
        assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":credit-confirm"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_CREDIT_BLOCKED));
    }

    private SalesOrderDetailVO recordActualQuantity(Long orderId, String tag) {
        asAdmin();
        SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(orderId);
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity("2.0000");
        actual.setReason("结算方式 " + tag + " 回归");
        return salesOrderService.actualQuantity(actual, prefix + ":actual-" + tag);
    }

    // ------------------------------------------------------------------
    // 商城侧辅助
    // ------------------------------------------------------------------
    private MallAccount openMallAccount(Long customerId) {
        String phone = newPhone();
        MallAccountCreateForm form = new MallAccountCreateForm();
        form.setCustomerId(customerId);
        form.setLoginPhone(phone);
        form.setTemporaryPassword(TEMPORARY_PASSWORD);
        MallAccountVO account = mallAccountService.create(form);
        assertThat(account.getFirstPasswordChange()).isTrue();
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
        RequestMallCustomer customer = requestCustomer(account, 1);
        assertThat(customer.getFirstPasswordChange()).isFalse();
        SmartRequestUtil.setRequestUser(customer);
        return customer;
    }

    private RequestMallCustomer requestCustomer(MallAccount account, int version) {
        RequestMallCustomer customer = mallAuthService.requestCustomer(
                MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
        assertThat(customer).isNotNull();
        return customer;
    }

    private String instanceStatus(Long instanceId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT status FROM xsy_promotion_coupon_instance WHERE id = ?",
                String.class, instanceId);
    }

    private static MallAddressSaveForm address() {
        MallAddressSaveForm form = new MallAddressSaveForm();
        form.setReceiverName("商城收货人");
        form.setReceiverPhone("13800000000");
        form.setProvinceName("上海市");
        form.setCityName("上海市");
        form.setDistrictName("浦东新区");
        form.setDetailAddress("IT 测试路 1 号");
        form.setIsDefault(true);
        return form;
    }

    private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
        MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
        form.setItems(List.of(checkoutItem(skuId, "2.0000")));
        form.setAddressId(addressId);
        return form;
    }

    private static MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
        MallOrderSubmitForm form = new MallOrderSubmitForm();
        form.setItems(List.of(checkoutItem(skuId, "2.0000")));
        form.setAddressId(addressId);
        form.setPriceFingerprint(priceFingerprint);
        form.setRemark("营销闭环 IT");
        return form;
    }

    private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
        MallCheckoutItemForm item = new MallCheckoutItemForm();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private void asAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("营销 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private static String newPhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
        return "13" + (100_000_000L + suffix);
    }

    private record MallAccount(Long accountId, String phone) {
    }
}
