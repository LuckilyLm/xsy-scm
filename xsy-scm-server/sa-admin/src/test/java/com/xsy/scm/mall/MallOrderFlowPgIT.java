package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.domain.vo.MallCartVO;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.order.constant.MallOrderDisplayStatusEnum;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderItemVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderTimelineVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.service.PromotionCouponService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城主链端到端联调（真实 PostgreSQL + 真实服务，不 mock 商城域）。
 *
 * <p>
 * 覆盖「后台开通账号 → 客户登录并改临时密码 → 目录可见性与客户价 → 购物车 → 收货地址 → 结算预览 → 正式下单（来源
 * {@code MALL}）→ 后台按业务员范围接单」的完整链路，并专门验证一个已知风险：
 * <b>客户未分配业务员时，其商城订单对普通业务员是否可见</b>。
 *
 * <p>
 * 为什么必须跑真实库：可见性、客户价、订单归属、业务员范围这四件事全部由 SQL 谓词决定
 * （{@code xsy_customer.visibility_policy}、{@code PriceResolver} 的三级取价、 {@code seller_id IN (...)} 对
 * NULL 的排除），单测里 mock 掉任何一层都验证不到真正的口径。
 *
 * <p>
 * Sa-Token 在无 Web 上下文的测试里拿不到 token 容器，因此登录动作借 {@code mockStatic(StpUtil.class)} 吸收；
 * 被验证的是「账号状态 + 密码校验 + 临时密码守卫 + 客户可交易性」这条业务判定，不是令牌本身。
 * 静态 mock 同时让 {@code ScmDataScopeService.hasPermission} 返回 false，于是非超管员工的范围收敛成
 * 「本人」，这正是要验证的那条口径。
 */
class MallOrderFlowPgIT extends ScmW5PgITBase {

    private static final String TEMPORARY_PASSWORD = "Mall@123456";
    private static final String NEW_PASSWORD = "Mall@654321";

    @Autowired
    private MallAccountService mallAccountService;
    @Autowired
    private PromotionCouponService promotionCouponService;
    @Autowired
    private MallAuthService mallAuthService;
    @Autowired
    private MallCatalogService mallCatalogService;
    @Autowired
    private MallCartService mallCartService;
    @Autowired
    private MallAddressService mallAddressService;
    @Autowired
    private MallCheckoutService mallCheckoutService;
    @Autowired
    private MallOrderQueryService mallOrderQueryService;
    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private SalesOrderQueryService salesOrderQueryService;

    private int customerSequence;

    @Test
    @EnabledIfSystemProperty(named = "scm.mall.promotion.confirm-it", matches = "true")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredPreselectedCouponBlocksOrderConfirmation() {
        String expectedDatabase = System.getProperty("scm.mall.promotion.confirm-db", "");
        assertThat(expectedDatabase).matches("xsy_(verify|codex_mkt)_[A-Za-z0-9_]+");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);
        Long sellerId = newEmployee("coupon");
        Long skuId = newOnShelfSku("coupon");
        Long customerId = newCustomerAssignedTo(sellerId);
        PromotionCouponForm coupon = new PromotionCouponForm();
        coupon.setCouponCode(prefix + "-MALL-COUPON");
        coupon.setCouponName(prefix + "商城预选券");
        coupon.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
        coupon.setDiscountValue(new BigDecimal("0.5000"));
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setClaimable(true);
        coupon.setValidFrom(OffsetDateTime.now().minusMinutes(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(1));
        Long couponId = promotionCouponService.save(coupon);
        PromotionStatusForm status = new PromotionStatusForm();
        status.setVersion(0);
        status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
        promotionCouponService.updateStatus(couponId, status);

        RequestMallCustomer customer = signIn(openMallAccount(customerId));
        Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":claim");
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
        previewForm.setCouponInstanceId(instanceId);
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
        assertThat(preview.getPromotion().getCouponDiscount()).isEqualByComparingTo("0.5000");
        MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
        submitForm.setCouponInstanceId(instanceId);
        MallOrderSubmitVO submitted = mallCheckoutService.submit(customer, submitForm, prefix + ":submit-coupon");
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        asAdmin();
        SalesOrderDetailVO order = salesOrderQueryService.detail(submitted.getOrderId());
        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(order.getOrderId());
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity("2.0000");
        actual.setReason("券失效确认回归");
        SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual-coupon");

        assertThat(jdbc.update("UPDATE xsy_promotion_coupon SET valid_from = ?, valid_to = ? WHERE id = ?",
                java.sql.Timestamp.from(OffsetDateTime.now().minusHours(2).toInstant()),
                java.sql.Timestamp.from(OffsetDateTime.now().minusHours(1).toInstant()), couponId)).isEqualTo(1);
        evictMybatisCache();
        OrderVersionForm confirm = new OrderVersionForm();
        confirm.setOrderId(weighted.getOrderId());
        confirm.setVersion(weighted.getVersion());
        assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":confirm-coupon"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_STATE_INVALID));
        evictMybatisCache();
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?", String.class,
                submitted.getOrderId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_promotion_coupon_instance WHERE id = ?", String.class,
                instanceId)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id = ?",
                Integer.class, submitted.getOrderId())).isZero();
    }

    @Test
    void mallOrderRunsFromLoginToAdminConfirmation() {
        Long sellerEmployeeId = newEmployee("s1");
        Long skuId = newOnShelfSku("flow");
        Long customerId = newCustomerAssignedTo(sellerEmployeeId);
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);

        // 目录：可见性与客户价都由服务端解析（基类 SKU 只有市场价，落 MARKET 兜底档）
        List<MallProductVO> products = mallCatalogService.visibleProducts(customer, List.of(skuId));
        assertThat(products).as("客户应看到刚上架的 SKU").hasSize(1);
        assertThat(products.getFirst().getUnitPrice()).isEqualByComparingTo("1.2000");
        assertThat(products.getFirst().getPriceSource()).isEqualTo("MARKET");
        assertThat(products.getFirst().getNonStandard()).as("基类 SKU 是非标品").isTrue();

        // 购物车
        MallCartVO cart = mallCartService.add(customer, cartAdd(skuId, "2.0000"));
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getAvailableCount()).isEqualTo(1);
        assertThat(cart.getEstimatedAmount()).isEqualByComparingTo("2.4000");

        // 收货地址
        MallAddressSaveForm address = address();
        Long addressId = mallAddressService.create(customer, address).getFirst().getAddressId();

        // 结算预览：金额与价格指纹都来自服务端，且不含不可售项
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        assertThat(preview.getTotalCount()).isEqualTo(1);
        assertThat(preview.getAvailableCount()).isEqualTo(1);
        assertThat(preview.getUnavailableSkuIds()).isEmpty();
        assertThat(preview.getEstimatedAmount()).isEqualByComparingTo("2.4000");
        assertThat(preview.getPriceFingerprint()).isNotBlank();

        // 正式下单
        String idempotencyKey = prefix + ":mall:submit";
        MallOrderSubmitForm submit = submitForm(skuId, addressId, preview.getPriceFingerprint());
        var submitted = mallCheckoutService.submit(customer, submit, idempotencyKey);
        assertThat(submitted.getOrderSource()).isEqualTo(ScmOrderSourceEnum.MALL.name());
        assertThat(submitted.getOrderedTotalAmount()).isEqualByComparingTo("2.4000");
        // 非标品：商城不自动确认，等后台录实重后接单
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        // 下单成功后，已下单规格从购物车移除
        assertThat(mallCartService.cart(customer).getItems()).isEmpty();

        // 幂等：同一键重试拿回原订单，不重复建单
        assertThat(mallCheckoutService.submit(customer, submit, idempotencyKey).getOrderId())
                .isEqualTo(submitted.getOrderId());

        // 客户侧订单中心：展示分类由订单状态 + 履约事实派生
        MallOrderVO mine = mallOrderQueryService.detail(customer, submitted.getOrderId());
        assertThat(mine.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PENDING.name());
        assertThat(mine.getCancelable()).isTrue();
        assertThat(mallOrderQueryService.list(customer, orderQuery(MallOrderDisplayStatusEnum.PENDING.name()))
                .getList()).extracting(MallOrderVO::getOrderId).contains(submitted.getOrderId());

        // 订单归属取客户快照上的业务员
        Long sellerId = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class,
                submitted.getOrderId());
        assertThat(sellerId).isEqualTo(sellerEmployeeId);

        // 后台：全量范围的操作者能看到 MALL 来源订单
        //
        // 先钉一条容易踩空的口径：商城身份不是员工，ScmDataScopeService.resolve() 对它返回 denied()，
        // 所以拿着商城身份查后台订单列表必然是空 —— 商城身份不会漏进后台范围。
        assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                .as("商城客户身份不属于后台订单范围，后台列表必为空").isEmpty();
        asAdmin();
        assertThat(mallOrderIdsVisibleToCurrentActor(customerId)).contains(submitted.getOrderId());

        // 后台接单：该订单的业务员本人（非超管）能看见并处理
        asEmployee(sellerEmployeeId, () -> {
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                    .as("订单业务员本人应能在后台看到该商城订单").contains(submitted.getOrderId());
            confirmOrder(submitted.getOrderId(), "2.0000");
            return null;
        });

        // 后台确认后，客户侧展示分类跟着履约事实前进，且已确认订单不能再取消
        SmartRequestUtil.setRequestUser(customer);
        MallOrderVO confirmed = mallOrderQueryService.detail(customer, submitted.getOrderId());
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PREPARING.name());
        assertThat(confirmed.getCancelable()).isFalse();

        // 内部审计记录不进客户时间线：超额授信放行的审批原因留在库里，但客户看不到它
        jdbc.update("INSERT INTO xsy_order_operation_log (order_id, operation_type, operator, reason)"
                + " VALUES (?, 'CREDIT_OVERRIDE', '1', '内部审批：临时放宽额度')", submitted.getOrderId());
        evictMybatisCache();
        MallOrderVO withOverride = mallOrderQueryService.detail(customer, submitted.getOrderId());
        assertThat(withOverride.getTimeline()).extracting(MallOrderTimelineVO::getOperationType)
                .as("内部授信审批不得出现在客户时间线").doesNotContain("CREDIT_OVERRIDE");
        assertThat(withOverride.getTimeline()).extracting(MallOrderTimelineVO::getOperationType)
                .as("客户可见的生命周期节点仍在").contains("CREATE", "SUBMIT", "CONFIRM");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ?"
                + " AND operation_type = 'CREDIT_OVERRIDE'", Integer.class, submitted.getOrderId()))
                .as("后台审计记录保持完整，商城只是收窄展示").isEqualTo(1);
    }

    /**
     * 已知风险：订单业务员取自客户快照。客户未分配业务员时订单 {@code seller_id} 为空，
     * 而普通业务员的范围是 {@code seller_id IN (本人)} —— {@code IN} 天然排除 NULL。
     *
     * <p>
     * 本用例把这个口径钉下来：客户自己看得到自己的订单，全量范围的操作者看得到，
     * 普通业务员看不到。若产品要求「未分配单人人可见」，就得改范围口径（而不是改这条测试）。
     */
    @Test
    void mallOrderOfACustomerWithoutASellerIsInvisibleToOrdinaryEmployees() {
        Long skuId = newOnShelfSku("noseller");
        Long customerId = newCustomerAssignedTo(null);
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);

        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
        mallCartService.add(customer, cartAdd(skuId, "1.0000"));
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        var submitted = mallCheckoutService.submit(customer, submitForm(skuId, addressId, preview.getPriceFingerprint()),
                prefix + ":mall:noseller");

        // 根因：客户没有业务员，订单业务员快照跟着为空
        assertThat(jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class,
                submitted.getOrderId())).isNull();

        // 客户自己按 customerId 收窄，不受业务员范围影响
        assertThat(mallOrderQueryService.detail(customer, submitted.getOrderId()).getOrderId())
                .isEqualTo(submitted.getOrderId());

        // 全量范围的操作者看得到
        asAdmin();
        assertThat(mallOrderIdsVisibleToCurrentActor(customerId)).contains(submitted.getOrderId());

        // 普通业务员看不到未分配单
        Long ordinaryEmployeeId = newEmployee("s2");
        asEmployee(ordinaryEmployeeId, () -> {
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                    .as("未分配业务员的商城订单对普通业务员不可见（seller_id IN (...) 排除 NULL）").isEmpty();
            return null;
        });
    }

    /**
     * 后台接单：非标品必须先生成实重再确认（与人工在后台录实重后接单同一条路径）。
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
     * 配送进度：商城的配送详情与进度节点只读配送域事实，不自己推导。
     *
     * <p>
     * 线路的建线 / 定位 / 发车 / 签收正确性由配送域自己的 IT（{@code DeliveryDispatchPgIT}）覆盖，
     * 本用例只验证商城这一侧的<b>只读投影</b>，因此线路与关联行用直插固定输入 —— 否则要把仓库坐标、入库、 分拣完成那一整套夹具复制过来，重复测别人已经测过的部分。
     *
     * <p>
     * 直插之后必须 {@code evictMybatisCache()}：MyBatis 一级缓存与用例事务同生命周期， 否则会读到事务内的旧值，表现为「改了库但断言没变」。
     *
     * <p>
     * <b>正常签收与异常签收各用一张订单</b>：正式签收只允许 {@code PENDING / IN_TRANSIT → SIGNED|EXCEPTION}
     * （见 {@code DeliveryQueryDao.markSigned}），把同一行从 SIGNED 再改成 EXCEPTION 是正式业务做不到的序列。
     */
    @Test
    void deliveryProgressReflectsRealRouteFacts() {
        Long sellerEmployeeId = newEmployee("s3");
        Long skuId = newOnShelfSku("delivery");
        Long customerId = newCustomerAssignedTo(sellerEmployeeId);
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);
        Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();

        MallOrderSubmitVO submitted = placeMallOrder(customer, skuId, addressId, "delivery-ok");
        Long orderId = submitted.getOrderId();

        // 尚未排车：没有配送信息，进度停在「已提交」，不伪造线路
        MallOrderVO beforeRoute = mallOrderQueryService.detail(customer, orderId);
        assertThat(beforeRoute.getDelivery()).as("未排车不得凭空返回线路").isNull();
        assertThat(nodeCodes(beforeRoute)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
        assertThat(doneCodes(beforeRoute)).containsExactly("SUBMITTED");

        // 后台接单后：确认节点点亮，展示分类进入「备货中」；实重与结算金额同时落定
        asEmployee(sellerEmployeeId, () -> {
            // 实重与订货量故意不同：只有不同才验证得到「结算金额 = 锁定价 × 实重」而不是「× 订货量」
            confirmOrder(orderId, "1.2000");
            return null;
        });
        MallOrderVO confirmed = mallOrderQueryService.detail(customer, orderId);
        assertThat(confirmed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PREPARING.name());
        assertThat(doneCodes(confirmed)).containsExactly("SUBMITTED", "CONFIRMED");
        assertThat(confirmed.getItems()).hasSize(1);
        MallOrderItemVO line = confirmed.getItems().getFirst();
        // 订货量 2.0000（结算表单下发的数量）、实重 1.2000、锁定价 1.2000
        assertThat(line.getOrderedQuantity()).as("订货量").isEqualByComparingTo("2.0000");
        assertThat(line.getActualQuantity()).as("实重来自正式订单行，不是前端模拟").isEqualByComparingTo("1.2000");
        assertThat(line.getUnitPrice()).as("结算单价取锁定价").isEqualByComparingTo("1.2000");
        assertThat(line.getOrderedLineAmount()).as("订货金额按订货量算").isEqualByComparingTo("2.4000");
        assertThat(line.getSettlementLineAmount()).as("结算金额按实重算").isEqualByComparingTo("1.4400");
        assertThat(confirmed.getSettlementTotalAmount()).isEqualByComparingTo("1.4400");

        // 排车并已发车：线路信息与发车节点都来自配送事实
        Long routeId = insertDispatchedRoute("ok");
        insertActiveRouteOrder(routeId, orderId, customerId, submitted.getOrderNo(), "IN_TRANSIT");
        evictMybatisCache();

        MallOrderVO inTransit = mallOrderQueryService.detail(customer, orderId);
        assertThat(inTransit.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.DELIVERING.name());
        assertThat(inTransit.getDelivery()).isNotNull();
        assertThat(inTransit.getDelivery().getRouteName()).isEqualTo(prefix + "线路");
        assertThat(inTransit.getDelivery().getDriverName()).isEqualTo("IT 司机");
        assertThat(inTransit.getDelivery().getDispatchedAt()).as("发车时刻来自线路行").isNotNull();
        assertThat(doneCodes(inTransit)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED");

        // 正常签收（在途 → 已签收）：进度走完，展示分类进入「已完成」
        signRouteOrder(routeId, orderId, "SIGNED", "放前台代收");
        MallOrderVO signed = mallOrderQueryService.detail(customer, orderId);
        assertThat(signed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.COMPLETED.name());
        assertThat(signed.getSignedAt()).isNotNull();
        assertThat(doneCodes(signed)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
        assertThat(nodeText(signed, "SIGNED")).as("正常签收不写成异常").isEqualTo("已签收");

        // 异常签收另起一张订单，同样从「在途」出发：最后一格显式写成「配送异常」并带原因，不冒充正常签收
        MallOrderSubmitVO abnormalOrder = placeMallOrder(customer, skuId, addressId, "delivery-abnormal");
        asEmployee(sellerEmployeeId, () -> {
            confirmOrder(abnormalOrder.getOrderId(), "2.0000");
            return null;
        });
        Long abnormalRouteId = insertDispatchedRoute("abnormal");
        insertActiveRouteOrder(abnormalRouteId, abnormalOrder.getOrderId(), customerId, abnormalOrder.getOrderNo(),
                "IN_TRANSIT");
        signRouteOrder(abnormalRouteId, abnormalOrder.getOrderId(), "EXCEPTION", "客户拒收");
        MallOrderVO abnormal = mallOrderQueryService.detail(customer, abnormalOrder.getOrderId());
        assertThat(doneCodes(abnormal)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
        assertThat(nodeText(abnormal, "SIGNED")).isEqualTo("配送异常");
        assertThat(nodeDetail(abnormal, "SIGNED")).isEqualTo("客户拒收");
    }

    /**
     * 走一遍商城下单：加购 → 预览 → 提交，返回提交结果（含订单号，线路快照要用）。
     *
     * <p>
     * 自己先切回商城身份：{@code asEmployee(...)} 结束时会把请求上下文清空，之后直接调商城服务会以
     * {@code SCM mutation requires an authenticated operator} 失败（{@code ScmOperator.current()} 拿不到操作者）。
     */
    private MallOrderSubmitVO placeMallOrder(RequestMallCustomer customer, Long skuId, Long addressId,
            String keySuffix) {
        SmartRequestUtil.setRequestUser(customer);
        mallCartService.add(customer, cartAdd(skuId, "1.0000"));
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        return mallCheckoutService.submit(customer, submitForm(skuId, addressId, preview.getPriceFingerprint()),
                prefix + ":mall:" + keySuffix);
    }

    // ------------------------------------------------------------------
    // 配送事实（直插固定输入，仅用于验证商城只读投影）
    // ------------------------------------------------------------------

    private Long insertDispatchedRoute(String suffix) {
        String routeNo = prefix + "-DR-" + suffix;
        jdbc.update("INSERT INTO xsy_delivery_route (route_no, route_name, delivery_date, warehouse_id,"
                + " warehouse_name_snapshot, driver_name_snapshot, driver_phone_snapshot, vehicle_no_snapshot,"
                + " planned_departure_time, status, dispatched_at, dispatched_by, version, deleted,"
                + " created_at, updated_at)"
                + " VALUES (?, ?, CURRENT_DATE, ?, ?, ?, ?, ?, now(), 'DISPATCHED', now(), 'it', 0, FALSE,"
                + " now(), now())",
                routeNo, prefix + "线路", fixtureWarehouseId(), prefix + "仓", "IT 司机", "13900000000", "沪A00001");
        return jdbc.queryForObject("SELECT id FROM xsy_delivery_route WHERE route_no = ?", Long.class, routeNo);
    }

    private void insertActiveRouteOrder(Long routeId, Long orderId, Long customerId, String orderNo,
            String fulfillmentStatus) {
        jdbc.update("INSERT INTO xsy_delivery_route_stop (route_id, stop_seq, customer_id,"
                + " customer_name_snapshot, address_snapshot, version, deleted, created_at, updated_at)"
                + " VALUES (?, 1, ?, ?, ?, 0, FALSE, now(), now())",
                routeId, customerId, prefix + "客户", "IT 配送地址");
        Long stopId = jdbc.queryForObject(
                "SELECT id FROM xsy_delivery_route_stop WHERE route_id = ? AND stop_seq = 1", Long.class, routeId);
        jdbc.update("INSERT INTO xsy_delivery_route_order (route_id, stop_id, order_id, customer_id,"
                + " order_no_snapshot, assignment_status, fulfillment_status, version, deleted,"
                + " created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, FALSE, now(), now())",
                routeId, stopId, orderId, customerId, orderNo, fulfillmentStatus);
    }

    private void signRouteOrder(Long routeId, Long orderId, String result, String reason) {
        jdbc.update("UPDATE xsy_delivery_route_order SET fulfillment_status = ?, signed_at = now(),"
                + " signed_by = 'it', sign_reason = ? WHERE route_id = ? AND order_id = ?",
                result, reason, routeId, orderId);
        evictMybatisCache();
    }

    private static List<String> nodeCodes(MallOrderVO order) {
        return order.getProgress().stream().map(MallOrderProgressNodeVO::getCode).toList();
    }

    private static List<String> doneCodes(MallOrderVO order) {
        return order.getProgress().stream().filter(node -> Boolean.TRUE.equals(node.getDone()))
                .map(MallOrderProgressNodeVO::getCode).toList();
    }

    private static String nodeText(MallOrderVO order, String code) {
        return order.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                .map(MallOrderProgressNodeVO::getText).orElse(null);
    }

    private static String nodeDetail(MallOrderVO order, String code) {
        return order.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                .map(MallOrderProgressNodeVO::getDetail).orElse(null);
    }

    // ------------------------------------------------------------------
    // 商城侧
    // ------------------------------------------------------------------

    /**
     * 后台开通商城账号，返回账号与登录手机号。
     */
    private MallAccount openMallAccount(Long customerId) {
        String phone = newPhone();
        MallAccountCreateForm form = new MallAccountCreateForm();
        form.setCustomerId(customerId);
        form.setLoginPhone(phone);
        form.setTemporaryPassword(TEMPORARY_PASSWORD);
        MallAccountVO account = mallAccountService.create(form);
        assertThat(account.getFirstPasswordChange()).as("新账号必须强制改临时密码").isTrue();
        return new MallAccount(account.getAccountId(), phone);
    }

    /**
     * 走真实登录动作并完成首次改密，返回该客户的请求身份。
     *
     * <p>
     * 改密不是可省略的仪式：{@code MallRequestSupport.requirePasswordChanged()} 会挡住临时密码未改的账号，
     * 跳过它就等于跳过了商城接口的前置守卫。
     */
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
        RequestMallCustomer customer = mallAuthService.requestCustomer(MallAuthService.loginId(account.accountId(), version),
                new MockHttpServletRequest());
        assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
        return customer;
    }

    private static MallCartAddForm cartAdd(Long skuId, String quantity) {
        MallCartAddForm form = new MallCartAddForm();
        form.setSkuId(skuId);
        form.setQuantity(quantity);
        return form;
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
        form.setRemark("IT 商城下单");
        form.setSettlementMethod("COD");
        return form;
    }

    private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
        MallCheckoutItemForm item = new MallCheckoutItemForm();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private static MallOrderQueryForm orderQuery(String displayStatus) {
        MallOrderQueryForm form = new MallOrderQueryForm();
        form.setPageNum(1L);
        form.setPageSize(50L);
        form.setDisplayStatus(displayStatus);
        return form;
    }

    // ------------------------------------------------------------------
    // 后台侧
    // ------------------------------------------------------------------

    /**
     * 当前操作者在后台订单列表里能看到的本客户 {@code MALL} 订单。
     *
     * <p>
     * 同时按 {@code customerId} 与 {@code orderSource} 收窄，断言才不会被库里其它用例留下的订单影响；
     * 范围谓词由 {@code SalesOrderMapper} 拼装，因此这条查询本身就是「可见性」的被测对象。
     */
    private List<Long> mallOrderIdsVisibleToCurrentActor(Long customerId) {
        SalesOrderQueryForm form = new SalesOrderQueryForm();
        form.setPageNum(1L);
        form.setPageSize(50L);
        form.setCustomerId(customerId);
        form.setOrderSource(ScmOrderSourceEnum.MALL.name());
        return salesOrderQueryService.query(form).getList().stream().map(SalesOrderVO::getOrderId).toList();
    }

    /**
     * 切回「全量范围的操作者」。
     *
     * <p>
     * 必须在每次做后台断言前显式调用：登录商城会把请求身份换成 {@link RequestMallCustomer}， 而商城身份不是员工，
     * {@code ScmDataScopeService.resolve()} 对它返回 {@code denied()} —— 后台订单列表会静默变空，
     * 断言写成「包含某订单」就会莫名其妙地失败。
     */
    private void asAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W5 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    /**
     * 以指定员工的身份执行一段动作。
     *
     * <p>
     * {@code administratorFlag=false} 是这里的关键：它让 {@code ScmDataScopeService.resolve()} 走受限分支，
     * 订单范围收敛成 {@code seller_id IN (本人)}。静态 mock 掉 Sa-Token 后权限判定一律为 false， 与「普通业务员没有全量范围权限」的真实形态一致。
     */
    private <T> T asEmployee(Long employeeId, Supplier<T> action) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("业务员" + employeeId);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            return action.get();
        } finally {
            SmartRequestUtil.remove();
        }
    }

    // ------------------------------------------------------------------
    // 造数
    // ------------------------------------------------------------------

    /**
     * 造一个非超管员工（业务员）。直接插 {@code t_employee}：本用例不验证员工域，
     * 而 {@code administrator_flag=FALSE} 正是「普通业务员」的定义。
     */
    private Long newEmployee(String suffix) {
        String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "业务员" + suffix);
        return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
    }

    /**
     * 造一个已合作客户，并显式指定业务员；{@code sellerId} 传 {@code null} 即「未分配业务员」。
     *
     * <p>
     * 为什么要借 {@code CUSTOMER_ASSIGN_PERM}：{@code CustomerService.resolveSellerOnCreate} 在调用方没有分配权时
     * 会把业务员落成「当前登录人」，无法造出未分配客户。分配权是真实存在的权限，这里只是以具备该权限的身份造数。
     */
    private Long newCustomerAssignedTo(Long sellerId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "-M" + (++customerSequence) + "客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        form.setCodAllowed(true);
        form.setSellerId(sellerId);
        Long customerId;
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            when(StpUtil.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).thenReturn(true);
            customerId = customerService.add(form);
        }
        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return customerId;
    }

    /** 11 位登录手机号；同一用例内多次开号也不会撞唯一索引。 */
    private static String newPhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
        return "13" + (100_000_000L + suffix);
    }

    private record MallAccount(Long accountId, String phone) {
    }
}
