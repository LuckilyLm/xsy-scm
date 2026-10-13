package com.xsy.scm.mall.promotion.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.promotion.dao.MallPromotionDao;
import com.xsy.scm.mall.promotion.domain.vo.MallClaimableCouponVO;
import com.xsy.scm.mall.promotion.domain.vo.MallCouponInstanceVO;
import com.xsy.scm.mall.promotion.domain.vo.MallPromotionActivityVO;
import com.xsy.scm.mall.promotion.domain.form.MallCouponQueryForm;
import com.xsy.scm.mall.promotion.domain.form.MallPromotionPageForm;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionCouponIssueSourceEnum;
import com.xsy.scm.promotion.service.PromotionCouponService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城营销适配层：活动展示、领券中心与「我的优惠券」。
 *
 * <p>
 * 只读投影走商城自有 DAO，写操作（领券）复用营销域正式服务； 不复制活动与券的计算规则，也不把后台 {@code /scm/promotion/**} 接口开放给客户。
 */
@Service
@RequiredArgsConstructor
public class MallPromotionService {

    private final MallPromotionDao promotionDao;

    private final PromotionCouponService promotionCouponService;
    private final MallCatalogService catalogService;

    @Transactional(readOnly = true)
    public List<MallPromotionActivityVO> activities(RequestMallCustomer identity) {
        List<MallPromotionActivityVO> rows = promotionDao.listActiveActivities(OffsetDateTime.now());
        List<Long> specialSkuIds = rows.stream()
                .filter(row -> ScmPromotionActivityTypeEnum.SPECIAL_PRICE.name().equals(row.getActivityType()))
                .map(row -> Long.valueOf(String.valueOf(row.getRule().get("skuId")))).distinct().toList();
        Map<Long, MallProductVO> products = catalogService.visibleProducts(identity, specialSkuIds).stream()
                .collect(Collectors.toMap(MallProductVO::getSkuId, product -> product));
        List<MallPromotionActivityVO> visible = new ArrayList<>(rows.size());
        for (MallPromotionActivityVO row : rows) {
            if (ScmPromotionActivityTypeEnum.SPECIAL_PRICE.name().equals(row.getActivityType())) {
                MallProductVO product = products.get(Long.valueOf(String.valueOf(row.getRule().get("skuId"))));
                BigDecimal specialPrice = new BigDecimal(String.valueOf(row.getRule().get("specialPrice")));
                if (product == null || !Boolean.TRUE.equals(product.getSellable()) || product.getUnitPrice() == null
                        || specialPrice.compareTo(product.getUnitPrice()) >= 0) {
                    continue;
                }
                row.setProduct(product);
                row.setSpecialPrice(specialPrice);
            }
            row.setActivityTypeLabel(activityTypeLabel(row.getActivityType()));
            row.setSummary(activitySummary(row));
            visible.add(row);
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public List<MallProductVO> newProducts(RequestMallCustomer identity) {
        return catalogService.newest(identity, 8);
    }

    @Transactional(readOnly = true)
    public PageResult<MallClaimableCouponVO> claimableCoupons(RequestMallCustomer identity,
            MallPromotionPageForm form) {
        Page<MallClaimableCouponVO> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        page.setOptimizeCountSql(false);
        List<MallClaimableCouponVO> rows = promotionDao.listClaimableCoupons(page, OffsetDateTime.now(),
                identity.getCustomerId(), ScmPromotionCouponIssueSourceEnum.MALL_CLAIM.name());
        for (MallClaimableCouponVO row : rows) {
            row.setDiscountTypeLabel(couponTypeLabel(row.getDiscountType()));
            boolean perCustomerOpen = row.getPerCustomerLimit() == null
                    || row.getClaimedByCustomer() < row.getPerCustomerLimit();
            boolean totalOpen = row.getTotalLimit() == null || row.getClaimedTotal() < row.getTotalLimit();
            row.setClaimableNow(perCustomerOpen && totalOpen);
        }
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public PageResult<MallCouponInstanceVO> myCoupons(RequestMallCustomer identity, MallCouponQueryForm form) {
        Page<MallCouponInstanceVO> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        page.setOptimizeCountSql(false);
        List<MallCouponInstanceVO> rows = promotionDao.listMyInstances(page, identity.getCustomerId(),
                OffsetDateTime.now(), form.getTab());
        for (MallCouponInstanceVO row : rows) {
            row.setDiscountTypeLabel(couponTypeLabel(row.getDiscountType()));
        }
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    /**
     * 客户自助领券：模板必须开放领取且在有效期内，领取上限由营销域在模板行锁内校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long claim(RequestMallCustomer identity, Long couponId, String idempotencyKey) {
        return promotionCouponService.claimFromMall(identity.getCustomerId(), couponId, idempotencyKey);
    }

    private static String activityTypeLabel(String activityType) {
        return ScmPromotionActivityTypeEnum.isSupported(activityType)
                ? ScmPromotionActivityTypeEnum.valueOf(activityType).getDesc()
                : "活动";
    }

    private static String activitySummary(MallPromotionActivityVO row) {
        Map<String, Object> rule = row.getRule();
        return switch (ScmPromotionActivityTypeEnum.valueOf(row.getActivityType())) {
            case FULL_REDUCE -> "满¥" + rule.get("thresholdAmount") + "减¥" + rule.get("reduceAmount");
            case DISCOUNT -> new BigDecimal(String.valueOf(rule.get("discountRate"))).multiply(BigDecimal.TEN)
                    .stripTrailingZeros().toPlainString() + "折";
            case FULL_GIFT -> "满¥" + rule.get("thresholdAmount") + "赠礼";
            case SPECIAL_PRICE -> "限时价 ¥" + row.getSpecialPrice().toPlainString() + "/"
                    + row.getProduct().getSaleUnit();
        };
    }

    private static String couponTypeLabel(String discountType) {
        return ScmPromotionCouponDiscountTypeEnum.isSupported(discountType)
                ? ScmPromotionCouponDiscountTypeEnum.valueOf(discountType).getDesc()
                : discountType;
    }
}
