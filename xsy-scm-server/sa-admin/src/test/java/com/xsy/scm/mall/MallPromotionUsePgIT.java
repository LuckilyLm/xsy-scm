package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.dao.PromotionCouponInstanceDao;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.service.PromotionCouponService;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import com.xsy.scm.promotion.support.PromotionOrderFacts;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 营销优惠的归属与失效校验（PG IT，事务内回滚）。
 *
 * <p>这里直接验 {@link PromotionDiscountService} 的试算与冻结，因为「跨客户不能使用他人券」与
 * 「失效券不能冻结」都是<b>营销域的核心业务事实</b>，商城结算与订单确认都只是转发它们 —— 在
 * 转发层再验一遍只会重复测同一段代码。订单确认命令里 freeze 的编排正确性由 {@code MallOrderFlowPgIT}
 * 与订单域 IT 覆盖。
 *
 * <p>三个口径：
 * <ol>
 *   <li><b>跨客户</b>：券实例归属校验用 {@code COUPON_INSTANCE_NOT_FOUND}（41328）回答，
 *       不泄露「这张券存在、只是不属于你」—— 试算与冻结一致；</li>
 *   <li><b>失效券（已用 / 已释放）</b>：实例状态不是 {@code AVAILABLE} 即 {@code COUPON_INSTANCE_STATE_INVALID}（41330）；</li>
 *   <li><b>失效券（模板过期）</b>：模板过了有效期即 {@code COUPON_STATE_INVALID}（41327）。</li>
 * </ol>
 */
@DisplayName("营销优惠归属与失效校验（PG IT）")
class MallPromotionUsePgIT extends ScmW5PgITBase {

    @Autowired
    private PromotionCouponService promotionCouponService;

    @Autowired
    private PromotionDiscountService promotionDiscountService;

    @Autowired
    private PromotionCouponInstanceDao promotionCouponInstanceDao;

    @Test
    @DisplayName("跨客户：客户 B 不能用客户 A 的券试算或冻结")
    void crossCustomerCouponIsRejected() {
        asAdmin();
        Long couponId = claimableCoupon("归属");
        Long owner = newCustomer();
        Long intruder = newCustomer();
        Long instanceId = claim(owner, couponId, key("own"));

        // 试算：客户 B 用客户 A 的券
        PromotionDiscountPreviewForm preview = previewForm(intruder, instanceId, "100.0000");
        assertThatThrownBy(() -> promotionDiscountService.preview(preview))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND));

        // 冻结：客户 B 用客户 A 的券（订单事实的客户是 B）
        PromotionOrderFacts facts = new PromotionOrderFacts(999_000_001L, intruder,
                List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
        assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND));
    }

    @Test
    @DisplayName("失效券（已用）无法冻结")
    void usedCouponCannotBeFrozen() {
        asAdmin();
        Long couponId = claimableCoupon("已用");
        Long customerId = newCustomer();
        Long instanceId = claim(customerId, couponId, key("used"));
        markInstanceStatus(instanceId, "USED");

        PromotionOrderFacts facts = new PromotionOrderFacts(999_000_002L, customerId,
                List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
        assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID));
    }

    @Test
    @DisplayName("失效券（已释放）无法冻结")
    void releasedCouponCannotBeFrozen() {
        asAdmin();
        Long couponId = claimableCoupon("已释放");
        Long customerId = newCustomer();
        Long instanceId = claim(customerId, couponId, key("rel"));
        markInstanceStatus(instanceId, "RELEASED");

        PromotionOrderFacts facts = new PromotionOrderFacts(999_000_003L, customerId,
                List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
        assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID));
    }

    @Test
    @DisplayName("失效券（模板过期）无法冻结")
    void expiredCouponCannotBeFrozen() {
        asAdmin();
        // 先造一张有效期内的券模板并启用、领券，然后把模板有效期改成过去，
        // 模拟「领券时有效、冻结前模板已过期」的窗口（过期模板无法直接启用，updateStatus 会拒绝）。
        Long couponId = claimableCoupon("过期");
        Long customerId = newCustomer();
        Long instanceId = claim(customerId, couponId, key("exp"));
        expireCouponTemplate(couponId);

        PromotionOrderFacts facts = new PromotionOrderFacts(999_000_004L, customerId,
                List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
        assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_STATE_INVALID));
    }

    @Test
    @DisplayName("有效券冻结成功：占用该券并写入优惠事实")
    void validCouponFreezesSuccessfully() {
        asAdmin();
        Long couponId = claimableCoupon("有效");
        Long customerId = newCustomer();
        Long instanceId = claim(customerId, couponId, key("ok"));

        PromotionOrderFacts facts = new PromotionOrderFacts(999_000_005L, customerId,
                List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
        var frozen = promotionDiscountService.freeze(facts, instanceId);

        assertThat(frozen.isFrozen()).as("有效券冻结后返回冻结标记").isTrue();
        assertThat(frozen.getCouponInstanceId()).isEqualTo(instanceId);
        assertThat(frozen.getCouponDiscount()).isEqualByComparingTo("5.0000");
        assertThat(frozen.getFinalAmount()).isEqualByComparingTo("95.0000");

        PromotionCouponInstanceEntity instance = promotionCouponInstanceDao.selectById(instanceId);
        assertThat(instance.getStatus()).as("冻结后券实例转为 RESERVED").isEqualTo("RESERVED");
        assertThat(instance.getReservedOrderId()).isEqualTo(999_000_005L);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private PromotionDiscountPreviewForm previewForm(Long customerId, Long instanceId, String baseAmount) {
        PromotionDiscountPreviewForm form = new PromotionDiscountPreviewForm();
        form.setCustomerId(customerId);
        form.setCouponInstanceId(instanceId);
        PromotionDiscountPreviewForm.Line line = new PromotionDiscountPreviewForm.Line();
        line.setOrderItemId(1L);
        line.setSkuId(1L);
        line.setQuantity(new BigDecimal("1.0000"));
        line.setBaseAmount(new BigDecimal(baseAmount));
        form.setLines(List.of(line));
        return form;
    }

    private Long claim(Long customerId, Long couponId, String key) {
        return promotionCouponService.claimFromMall(customerId, couponId, key);
    }

    private Long claimableCoupon(String suffix) {
        PromotionCouponForm form = new PromotionCouponForm();
        form.setCouponCode(prefix + "-U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        form.setCouponName(prefix + "券" + suffix);
        form.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
        form.setDiscountValue(new BigDecimal("5.0000"));
        form.setMinOrderAmount(BigDecimal.ZERO);
        form.setClaimable(true);
        form.setValidFrom(OffsetDateTime.now().minusMinutes(1));
        form.setValidTo(OffsetDateTime.now().plusDays(1));
        Long id = promotionCouponService.save(form);
        PromotionStatusForm status = new PromotionStatusForm();
        status.setVersion(0);
        status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
        promotionCouponService.updateStatus(id, status);
        return id;
    }

    /** 把券模板有效期整体改成过去，模拟「领券时有效、冻结前模板已过期」的窗口（保持 valid_from < valid_to）。 */
    private void expireCouponTemplate(Long couponId) {
        evictMybatisCache();
        int updated = jdbc.update("UPDATE xsy_promotion_coupon SET valid_from = ?, valid_to = ? WHERE id = ?",
                java.sql.Timestamp.from(OffsetDateTime.now().minusHours(2).toInstant()),
                java.sql.Timestamp.from(OffsetDateTime.now().minusHours(1).toInstant()), couponId);
        assertThat(updated).isEqualTo(1);
        evictMybatisCache();
    }

    /**
     * 把券实例置为指定终态，满足状态 CHECK 约束（终态必须带时间 / 订单痕迹）。
     *
     * <p>这里直接落完整字段而不是走 {@code markUsed} / {@code markReleased}：后两者要求前置状态是
     * {@code RESERVED}（先冻结再核销 / 释放），而本测试要验证的正是「已用 / 已释放的券不能再冻结」，
     * 用状态流转 DAO 会把「制造失效券」和「验证拒绝冻结」两件事缠在一起。
     */
    private void markInstanceStatus(Long instanceId, String status) {
        evictMybatisCache();
        int updated;
        if ("USED".equals(status)) {
            updated = jdbc.update("UPDATE xsy_promotion_coupon_instance SET status = 'USED',"
                    + " used_order_id = 999_000_000, used_at = CURRENT_TIMESTAMP WHERE id = ?", instanceId);
        } else {
            updated = jdbc.update("UPDATE xsy_promotion_coupon_instance SET status = 'RELEASED',"
                    + " released_at = CURRENT_TIMESTAMP WHERE id = ?", instanceId);
        }
        assertThat(updated).isEqualTo(1);
        evictMybatisCache();
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private void asAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Promotion use IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }
}
