package com.xsy.scm.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionCouponInstanceStatusEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.dao.PromotionCouponDao;
import com.xsy.scm.promotion.dao.PromotionCouponInstanceDao;
import com.xsy.scm.promotion.domain.entity.PromotionCouponEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponIssueForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponQueryForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.domain.vo.PromotionCouponVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券模板与客户券。
 *
 * <p>
 * 券模板只在 {@code DRAFT/STOPPED} 可改；发券一次一条 INSERT 批量落库，
 * 每张券带自己的 {@code instance_no}，因此「这个客户有几张、用到第几张」都能查。
 */
@Service
@RequiredArgsConstructor
public class PromotionCouponService {

    private static final int INSTANCE_LIST_LIMIT = 200;

    private final PromotionCouponDao promotionCouponDao;

    private final PromotionCouponInstanceDao promotionCouponInstanceDao;

    private final CustomerQueryService customerQueryService;

    private final ScmIdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public PageResult<PromotionCouponVO> queryPage(PromotionCouponQueryForm query) {
        Page<?> page = SmartPageUtil.convert2PageQuery(query);
        List<PromotionCouponVO> rows = promotionCouponDao.queryPage(page, query).stream()
                .map(PromotionCouponService::toVO).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public PromotionCouponVO detail(Long id) {
        PromotionCouponEntity row = promotionCouponDao.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_NOT_FOUND);
        }
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(PromotionCouponForm form) {
        ScmPromotionCouponDiscountTypeEnum type = requireType(form.getDiscountType());
        if (!form.getValidFrom().isBefore(form.getValidTo())) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_WINDOW_INVALID);
        }
        if (type == ScmPromotionCouponDiscountTypeEnum.RATE
                && (form.getDiscountValue().signum() <= 0 || form.getDiscountValue().compareTo(BigDecimal.ONE) >= 0)) {
            // 折扣率必须落在 (0,1)：1 表示不减，>1 表示加价，都不是券
            throw new ScmBusinessException(PromotionErrorCode.COUPON_RATE_INVALID);
        }
        String operator = ScmOperator.current();

        if (form.getId() == null) {
            requireCodeAvailable(form.getCouponCode(), null);
            PromotionCouponEntity row = new PromotionCouponEntity();
            row.setCouponCode(form.getCouponCode().trim());
            apply(row, form, type);
            row.setStatus(ScmPromotionStatusEnum.DRAFT.name());
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            promotionCouponDao.insert(row);
            return row.getId();
        }

        PromotionCouponEntity row = promotionCouponDao.lockById(form.getId());
        if (row == null) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_NOT_FOUND);
        }
        if (!Objects.equals(form.getVersion(), row.getVersion())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        if (ScmPromotionStatusEnum.ACTIVE.name().equals(row.getStatus())) {
            // 生效中的券改内容，会让同一批已发出的券按两套规则核销
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        requireCodeAvailable(form.getCouponCode(), row.getId());
        apply(row, form, type);
        row.setUpdatedBy(operator);
        if (promotionCouponDao.updateById(row) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, PromotionStatusForm form) {
        PromotionCouponEntity coupon = promotionCouponDao.lockById(id);
        if (coupon == null) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_NOT_FOUND);
        }
        if (!Objects.equals(coupon.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        boolean activate = ScmPromotionStatusEnum.ACTIVE.name().equals(form.getStatus());
        boolean stop = ScmPromotionStatusEnum.STOPPED.name().equals(form.getStatus());
        boolean currentlyActive = ScmPromotionStatusEnum.ACTIVE.name().equals(coupon.getStatus());
        if ((!activate && !stop) || activate == currentlyActive) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        // 允许提前启用未来生效的券；实际发券仍须进入有效期，过期券不能再次启用。
        if (activate && !coupon.getValidTo().isAfter(OffsetDateTime.now())) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        coupon.setStatus(form.getStatus());
        coupon.setUpdatedBy(ScmOperator.current());
        coupon.setUpdatedAt(OffsetDateTime.now());
        if (promotionCouponDao.updateById(coupon) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 发券：给某客户发 N 张可用券。
     *
     * <p>
     * 只有生效中的券模板可以发：发出去的券立刻可用，模板还没生效等于发了一批用不了的券。
     */
    @Transactional(rollbackFor = Exception.class)
    public int issue(PromotionCouponIssueForm form, String key) {
        // 重放同样重新检查客户当前归属，旧请求不保留已撤销的数据范围。
        customerQueryService.detail(form.getCustomerId());
        var claim = idempotencyService.claim("PROMOTION_COUPON_ISSUE", key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, Integer.class);
        }
        PromotionCouponEntity coupon = promotionCouponDao.lockById(form.getCouponId());
        if (coupon == null) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_NOT_FOUND);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!ScmPromotionStatusEnum.ACTIVE.name().equals(coupon.getStatus())
                || coupon.getValidFrom().isAfter(now) || !coupon.getValidTo().isAfter(now)) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        int quantity = form.getQuantity() == null ? 1 : form.getQuantity();
        List<PromotionCouponInstanceEntity> rows = new ArrayList<>(quantity);
        String batch = "ISSUE-" + claim.record().getId();
        for (int index = 0; index < quantity; index++) {
            PromotionCouponInstanceEntity row = new PromotionCouponInstanceEntity();
            row.setCouponId(coupon.getId());
            row.setCustomerId(form.getCustomerId());
            // 幂等记录主键标识批次，不以毫秒时间猜测唯一性。
            row.setInstanceNo(batch + "-" + (index + 1));
            row.setStatus(ScmPromotionCouponInstanceStatusEnum.AVAILABLE.name());
            rows.add(row);
        }
        if (promotionCouponInstanceDao.insertBatch(rows) != quantity) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_UNAVAILABLE);
        }
        idempotencyService.complete(claim, "PROMOTION_COUPON", coupon.getId(), quantity);
        return quantity;
    }

    @Transactional(readOnly = true)
    public List<PromotionCouponVO.Instance> listInstances(Long customerId, String status) {
        customerQueryService.detail(customerId);
        if (StringUtils.isNotBlank(status)) {
            try {
                ScmPromotionCouponInstanceStatusEnum.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID);
            }
        }
        List<PromotionCouponInstanceEntity> rows = promotionCouponInstanceDao.listByCustomer(customerId, status,
                INSTANCE_LIST_LIMIT);
        Map<Long, PromotionCouponEntity> coupons = new HashMap<>();
        List<Long> couponIds = rows.stream().map(PromotionCouponInstanceEntity::getCouponId).filter(Objects::nonNull)
                .distinct().toList();
        if (!couponIds.isEmpty()) {
            promotionCouponDao.selectBatchIds(couponIds).forEach(coupon -> coupons.put(coupon.getId(), coupon));
        }
        return rows.stream().map(row -> PromotionCouponVO.Instance.of(row, coupons.get(row.getCouponId()))).toList();
    }

    private void apply(PromotionCouponEntity row, PromotionCouponForm form,
            ScmPromotionCouponDiscountTypeEnum type) {
        row.setCouponName(form.getCouponName().trim());
        row.setDiscountType(type.name());
        row.setDiscountValue(form.getDiscountValue());
        row.setMinOrderAmount(form.getMinOrderAmount() == null ? BigDecimal.ZERO : form.getMinOrderAmount());
        row.setValidFrom(form.getValidFrom());
        row.setValidTo(form.getValidTo());
        row.setRemark(StringUtils.isBlank(form.getRemark()) ? null : form.getRemark().trim());
    }

    private void requireCodeAvailable(String code, Long selfId) {
        LambdaQueryWrapper<PromotionCouponEntity> wrapper = new LambdaQueryWrapper<PromotionCouponEntity>()
                .eq(PromotionCouponEntity::getCouponCode, code.trim())
                .eq(PromotionCouponEntity::getDeleted, false);
        if (selfId != null) {
            wrapper.ne(PromotionCouponEntity::getId, selfId);
        }
        if (promotionCouponDao.selectCount(wrapper) > 0) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_CODE_DUPLICATED);
        }
    }

    private static ScmPromotionCouponDiscountTypeEnum requireType(String discountType) {
        if (!ScmPromotionCouponDiscountTypeEnum.isSupported(discountType)) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        return ScmPromotionCouponDiscountTypeEnum.valueOf(discountType);
    }

    static PromotionCouponVO toVO(PromotionCouponEntity row) {
        String label = ScmPromotionCouponDiscountTypeEnum.isSupported(row.getDiscountType())
                ? ScmPromotionCouponDiscountTypeEnum.valueOf(row.getDiscountType()).getDesc()
                : row.getDiscountType();
        return PromotionCouponVO.of(row, label);
    }
}
