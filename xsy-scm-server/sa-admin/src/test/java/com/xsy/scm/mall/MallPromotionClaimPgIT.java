package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.promotion.service.MallPromotionService;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.service.PromotionCouponService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 商城客户自助领券的并发与幂等（PG IT，无外层事务）。
 *
 * <p>关外层事务的原因与 {@code DeliveryDispatchConcurrencyPgIT} 相同：把多个线程塞进同一个测试事务，
 * 它们共享同一条连接、互相排队，「并发」是假的。
 *
 * <p>两条不变量：
 * <ol>
 *   <li><b>并发领券不超上限</b>：每人限领与发放总量都在模板行锁内计数，同一模板并发领取串行，
 *       最终实例数恰好等于上限，不多一张；</li>
 *   <li><b>同一幂等键不重复领券</b>：领券幂等键带 {@code MALL_PROMOTION_COUPON_CLAIM:{customerId}}
 *       前缀，同一客户同一键重试拿回首次结果，不生成第二张券实例。</li>
 * </ol>
 */
@DisplayName("商城领券并发与幂等（PG IT）")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MallPromotionClaimPgIT extends ScmW5PgITBase {

    private static final long TIMEOUT_SECONDS = 90;

    @Autowired
    private PromotionCouponService promotionCouponService;

    @Autowired
    private MallPromotionService mallPromotionService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("并发领券不超每人限领：10 个线程抢 1 张券，只落 1 张实例")
    void concurrentClaimRespectsPerCustomerLimit() throws Exception {
        asAdmin();
        Long couponId = claimableCoupon("并发每人限领", null, 1);
        Long customerId = newCustomer();
        RequestMallCustomer customer = mallCustomer(customerId);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transactionTemplate.execute(status ->
                    mallPromotionService.claim(customer, couponId, key("per"))));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        long succeeded = outcomes.stream().filter(Outcome::ok).count();
        assertThat(succeeded).as("每人限领 1 张，10 个并发请求只允许 1 个成功").isEqualTo(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.ok()).allSatisfy(outcome ->
                assertThat(outcome.error()).as("并发失败必须是领取上限业务拒绝")
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                failure -> assertThat(failure.getErrorCode())
                                        .isEqualTo(PromotionErrorCode.COUPON_CLAIM_LIMIT_REACHED)));
        assertThat(claimCount(couponId, customerId))
                .as("并发下该客户最终只落 1 张券实例").isEqualTo(1);
    }

    @Test
    @DisplayName("并发领券不超发放总量：总量 1 张，两个客户并发抢，只落 1 张")
    void concurrentClaimRespectsTotalLimit() throws Exception {
        asAdmin();
        Long couponId = claimableCoupon("并发总量", 1, null);
        Long first = newCustomer();
        Long second = newCustomer();
        RequestMallCustomer c1 = mallCustomer(first);
        RequestMallCustomer c2 = mallCustomer(second);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> transactionTemplate.execute(status -> mallPromotionService.claim(c1, couponId, key("t1"))),
                () -> transactionTemplate.execute(status -> mallPromotionService.claim(c2, couponId, key("t2")))));

        long succeeded = outcomes.stream().filter(Outcome::ok).count();
        assertThat(succeeded).as("发放总量 1 张，两个客户并发抢只允许 1 个成功").isEqualTo(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.ok()).allSatisfy(outcome ->
                assertThat(outcome.error()).as("超总量必须是领取上限业务拒绝")
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                failure -> assertThat(failure.getErrorCode())
                                        .isEqualTo(PromotionErrorCode.COUPON_CLAIM_LIMIT_REACHED)));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_promotion_coupon_instance WHERE coupon_id = ?",
                Integer.class, couponId)).as("并发下该模板只落 1 张券实例").isEqualTo(1);
    }

    @Test
    @DisplayName("同一幂等键重试拿回首次领券，不生成第二张实例")
    void sameIdempotencyKeyReturnsFirstClaim() {
        asAdmin();
        Long couponId = claimableCoupon("幂等", null, null);
        Long customerId = newCustomer();
        RequestMallCustomer customer = mallCustomer(customerId);
        String key = key("idem");

        Long first = mallPromotionService.claim(customer, couponId, key);
        Long replay = mallPromotionService.claim(customer, couponId, key);

        assertThat(replay).as("同一幂等键重试必须拿回首次领取的券实例").isEqualTo(first);
        assertThat(claimCount(couponId, customerId)).as("幂等重试不生成第二张券实例").isEqualTo(1);
    }

    @Test
    @DisplayName("未开放领取的券不可自助领取")
    void nonClaimableCouponIsRejected() {
        asAdmin();
        Long couponId = coupon(false, null, null);
        Long customerId = newCustomer();
        RequestMallCustomer customer = mallCustomer(customerId);

        assertThatThrownBy(() -> mallPromotionService.claim(customer, couponId, key("nc")))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_NOT_CLAIMABLE));
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record Outcome(boolean ok, Throwable error) {
    }

    private List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>(tasks.size());
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    asAdmin();
                    start.await();
                    try {
                        task.call();
                        return new Outcome(true, null);
                    } catch (Throwable error) {
                        return new Outcome(false, error);
                    } finally {
                        SmartRequestUtil.remove();
                    }
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>(futures.size());
            for (Future<Object> future : futures) {
                try {
                    outcomes.add((Outcome) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (ExecutionException | TimeoutException e) {
                    outcomes.add(new Outcome(false, e.getCause() == null ? e : e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 造一张开放领取且在有效期内的券模板（默认每人限领与总量都不限）。 */
    private Long claimableCoupon(String suffix, Integer totalLimit, Integer perCustomerLimit) {
        return coupon(true, totalLimit, perCustomerLimit);
    }

    private Long coupon(boolean claimable, Integer totalLimit, Integer perCustomerLimit) {
        PromotionCouponForm form = new PromotionCouponForm();
        form.setCouponCode(prefix + "-CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        form.setCouponName(prefix + "券" + claimable + totalLimit + perCustomerLimit);
        form.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
        form.setDiscountValue(new BigDecimal("5.0000"));
        form.setMinOrderAmount(BigDecimal.ZERO);
        form.setClaimable(claimable);
        form.setTotalLimit(totalLimit);
        form.setPerCustomerLimit(perCustomerLimit);
        form.setValidFrom(OffsetDateTime.now().minusMinutes(1));
        form.setValidTo(OffsetDateTime.now().plusDays(1));
        Long id = promotionCouponService.save(form);
        PromotionStatusForm status = new PromotionStatusForm();
        status.setVersion(0);
        status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
        promotionCouponService.updateStatus(id, status);
        return id;
    }

    private RequestMallCustomer mallCustomer(Long customerId) {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setAccountId(customerId);
        customer.setCustomerId(customerId);
        customer.setCustomerName(prefix + "客户");
        return customer;
    }

    private int claimCount(Long couponId, Long customerId) {
        evictMybatisCache();
        return jdbc.queryForObject(
                "SELECT count(*) FROM xsy_promotion_coupon_instance WHERE coupon_id = ? AND customer_id = ?",
                Integer.class, couponId, customerId);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private void asAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Promotion claim IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }
}
