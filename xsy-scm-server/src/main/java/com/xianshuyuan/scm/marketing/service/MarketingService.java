package com.xianshuyuan.scm.marketing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.marketing.dto.CouponIssueRequest;
import com.xianshuyuan.scm.marketing.dto.CouponPageQuery;
import com.xianshuyuan.scm.marketing.dto.CouponSaveRequest;
import com.xianshuyuan.scm.marketing.dto.HomeSectionSaveRequest;
import com.xianshuyuan.scm.marketing.dto.PromotionPageQuery;
import com.xianshuyuan.scm.marketing.dto.PromotionSaveRequest;
import com.xianshuyuan.scm.marketing.dto.ThemeConfigSaveRequest;
import com.xianshuyuan.scm.marketing.entity.CouponStatus;
import com.xianshuyuan.scm.marketing.entity.CouponType;
import com.xianshuyuan.scm.marketing.entity.HomeSectionType;
import com.xianshuyuan.scm.marketing.entity.MarketingCouponEntity;
import com.xianshuyuan.scm.marketing.entity.MarketingCustomerCouponEntity;
import com.xianshuyuan.scm.marketing.entity.MarketingFrequentSkuEntity;
import com.xianshuyuan.scm.marketing.entity.MarketingHomeSectionEntity;
import com.xianshuyuan.scm.marketing.entity.MarketingPromotionEntity;
import com.xianshuyuan.scm.marketing.entity.MarketingThemeConfigEntity;
import com.xianshuyuan.scm.marketing.entity.PromotionScope;
import com.xianshuyuan.scm.marketing.entity.PromotionStatus;
import com.xianshuyuan.scm.marketing.entity.PromotionType;
import com.xianshuyuan.scm.marketing.entity.SettlementMethod;
import com.xianshuyuan.scm.marketing.mapper.MarketingCouponMapper;
import com.xianshuyuan.scm.marketing.mapper.MarketingCustomerCouponMapper;
import com.xianshuyuan.scm.marketing.mapper.MarketingFrequentSkuMapper;
import com.xianshuyuan.scm.marketing.mapper.MarketingHomeSectionMapper;
import com.xianshuyuan.scm.marketing.mapper.MarketingPromotionMapper;
import com.xianshuyuan.scm.marketing.mapper.MarketingThemeConfigMapper;
import com.xianshuyuan.scm.marketing.row.FrequentSkuRow;
import com.xianshuyuan.scm.marketing.row.PromotionRow;
import com.xianshuyuan.scm.marketing.row.ReorderItemRow;
import com.xianshuyuan.scm.marketing.vo.CouponResponse;
import com.xianshuyuan.scm.marketing.vo.CustomerCouponResponse;
import com.xianshuyuan.scm.marketing.vo.FrequentSkuResponse;
import com.xianshuyuan.scm.marketing.vo.HomeSectionResponse;
import com.xianshuyuan.scm.marketing.vo.PromotionResponse;
import com.xianshuyuan.scm.marketing.vo.ReorderItemResponse;
import com.xianshuyuan.scm.marketing.vo.SettlementMethodResponse;
import com.xianshuyuan.scm.mall.vo.MallThemeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 营销服务。覆盖促销活动（抢购 / 满减满赠 / 限时特价）、首页板块、优惠券发放与核销、
 * 常用菜品、"再来一单"，以及四种结算方式。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingService {

    private final MarketingPromotionMapper promotions;
    private final MarketingHomeSectionMapper homeSections;
    private final MarketingCouponMapper coupons;
    private final MarketingCustomerCouponMapper customerCoupons;
    private final MarketingFrequentSkuMapper frequentSkus;
    private final MarketingThemeConfigMapper themeConfigs;

    public MallThemeConfig currentTheme() {
        MarketingThemeConfigEntity entity = themeConfigs.selectCurrent();
        if (entity == null) {
            return defaultTheme();
        }
        return toThemeConfig(entity);
    }

    @Transactional
    public MallThemeConfig saveTheme(ThemeConfigSaveRequest request, String operator) {
        String themeCode = request.themeCode().trim().toUpperCase();
        String cardStyle = request.cardStyle().trim().toUpperCase();
        String productCardStyle = request.productCardStyle().trim().toUpperCase();
        String navigationStyle = request.navigationStyle().trim().toUpperCase();
        if (!java.util.Set.of("FRESH_GREEN", "OCEAN_BLUE", "WARM_ORANGE").contains(themeCode)
                || !java.util.Set.of("FLAT", "BORDER", "SHADOW").contains(cardStyle)
                || !java.util.Set.of("COMPACT", "COMFORTABLE", "SPACIOUS").contains(productCardStyle)
                || !java.util.Set.of("TOP", "BOTTOM", "SIDEBAR").contains(navigationStyle)) {
            throw new BusinessException(MarketingErrorCodes.THEME_VALUE_INVALID);
        }
        MarketingThemeConfigEntity entity = themeConfigs.selectCurrent();
        if (entity == null) {
            entity = new MarketingThemeConfigEntity();
            entity.setCreatedBy(operator);
            entity.setCreatedAt(OffsetDateTime.now());
        }
        entity.setThemeCode(themeCode);
        entity.setPrimaryColor(request.primaryColor().toUpperCase());
        entity.setAccentColor(request.accentColor().toUpperCase());
        entity.setPageBackground(request.pageBackground().toUpperCase());
        entity.setCardRadius(request.cardRadius() == null ? 16 : request.cardRadius());
        entity.setCardStyle(cardStyle);
        entity.setProductCardStyle(productCardStyle);
        entity.setNavigationStyle(navigationStyle);
        entity.setUpdatedBy(operator);
        if (entity.getId() == null) {
            themeConfigs.insert(entity);
        } else {
            themeConfigs.updateById(entity);
        }
        return toThemeConfig(entity);
    }

    public PageData<PromotionResponse> listPromotions(PromotionPageQuery query) {
        OffsetDateTime now = OffsetDateTime.now();
        Page<PromotionRow> page = new Page<>(query.page(), query.pageSize());
        List<PromotionRow> rows = promotions.selectPage(page, query);
        List<PromotionResponse> items = rows.stream()
                .map(row -> toPromotionResponse(row, now))
                .toList();
        return new PageData<>(items, query.page(), query.pageSize(), page.getTotal());
    }

    /**
     * 当前时间生效的促销，供商城首页与结算使用。
     */
    public List<PromotionResponse> effectivePromotions() {
        OffsetDateTime now = OffsetDateTime.now();
        return promotions.selectEffective(now).stream()
                .map(entity -> toPromotionResponse(entity, now))
                .toList();
    }

    @Transactional
    public PromotionResponse createPromotion(PromotionSaveRequest request, String operator) {
        MarketingPromotionEntity entity = new MarketingPromotionEntity();
        applyPromotion(entity, request);
        entity.setStatus(defaultStatus(request.status()));
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        promotions.insert(entity);
        return toPromotionResponse(requirePromotion(entity.getId()), OffsetDateTime.now());
    }

    @Transactional
    public PromotionResponse updatePromotion(long id, PromotionSaveRequest request, String operator) {
        MarketingPromotionEntity entity = requirePromotion(id);
        applyPromotion(entity, request);
        if (request.status() != null && !request.status().isBlank()) {
            entity.setStatus(promotionStatus(request.status()).name());
        }
        entity.setUpdatedBy(operator);
        promotions.updateById(entity);
        return toPromotionResponse(requirePromotion(id), OffsetDateTime.now());
    }

    @Transactional
    public PromotionResponse changePromotionStatus(long id, String status, String operator) {
        MarketingPromotionEntity entity = requirePromotion(id);
        entity.setStatus(promotionStatus(status).name());
        entity.setUpdatedBy(operator);
        promotions.updateById(entity);
        return toPromotionResponse(requirePromotion(id), OffsetDateTime.now());
    }

    public List<HomeSectionResponse> listHomeSections() {
        return homeSections.selectActive(OffsetDateTime.now()).stream()
                .map(MarketingService::toHomeSectionResponse)
                .toList();
    }

    @Transactional
    public HomeSectionResponse createHomeSection(HomeSectionSaveRequest request, String operator) {
        MarketingHomeSectionEntity entity = new MarketingHomeSectionEntity();
        applyHomeSection(entity, request);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        homeSections.insert(entity);
        return toHomeSectionResponse(entity);
    }

    @Transactional
    public HomeSectionResponse updateHomeSection(long id, HomeSectionSaveRequest request, String operator) {
        MarketingHomeSectionEntity entity = homeSections.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(MarketingErrorCodes.HOME_SECTION_NOT_FOUND);
        }
        applyHomeSection(entity, request);
        entity.setUpdatedBy(operator);
        homeSections.updateById(entity);
        return toHomeSectionResponse(homeSections.selectById(id));
    }

    public PageData<CouponResponse> listCoupons(CouponPageQuery query) {
        Page<MarketingCouponEntity> page = new Page<>(query.page(), query.pageSize());
        List<MarketingCouponEntity> rows = coupons.selectPage(page, query);
        List<CouponResponse> items = rows.stream().map(MarketingService::toCouponResponse).toList();
        return new PageData<>(items, query.page(), query.pageSize(), page.getTotal());
    }

    @Transactional
    public CouponResponse createCoupon(CouponSaveRequest request, String operator) {
        MarketingCouponEntity entity = new MarketingCouponEntity();
        entity.setName(request.name().trim());
        entity.setCouponType(couponType(request.couponType()).name());
        entity.setThresholdAmount(request.thresholdAmount());
        entity.setDiscountRate(request.discountRate());
        entity.setReduceAmount(request.reduceAmount());
        entity.setTotalQuantity(request.totalQuantity() == null ? 0 : request.totalQuantity());
        entity.setIssuedQuantity(0);
        entity.setPerLimit(request.perLimit());
        entity.setValidFrom(request.validFrom());
        entity.setValidTo(request.validTo());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "ENABLED" : request.status().trim());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        coupons.insert(entity);
        return toCouponResponse(entity);
    }

    /**
     * 发放优惠券。总量与每人限领在服务端校验，券码服务端生成。
     */
    @Transactional
    public List<CustomerCouponResponse> issueCoupon(CouponIssueRequest request, String operator) {
        MarketingCouponEntity coupon = coupons.selectById(request.couponId());
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            throw new BusinessException(MarketingErrorCodes.COUPON_NOT_FOUND);
        }
        if (!"ENABLED".equals(coupon.getStatus())) {
            throw new BusinessException(MarketingErrorCodes.COUPON_DISABLED);
        }
        int issued = coupon.getIssuedQuantity() == null ? 0 : coupon.getIssuedQuantity();
        int total = coupon.getTotalQuantity() == null ? 0 : coupon.getTotalQuantity();
        if (total - issued < request.quantity()) {
            throw new BusinessException(MarketingErrorCodes.COUPON_SOLD_OUT);
        }
        if (coupon.getPerLimit() != null
                && customerCoupons.countIssued(coupon.getId(), request.customerId()) + request.quantity()
                > coupon.getPerLimit()) {
            throw new BusinessException(MarketingErrorCodes.COUPON_PER_LIMIT);
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<CustomerCouponResponse> issuedCoupons = new ArrayList<>();
        for (int index = 0; index < request.quantity(); index++) {
            MarketingCustomerCouponEntity entity = new MarketingCustomerCouponEntity();
            entity.setCouponId(coupon.getId());
            entity.setCustomerId(request.customerId());
            entity.setCouponNo(couponNo());
            entity.setStatus(CouponStatus.UNUSED.name());
            entity.setObtainedAt(now);
            entity.setValidFrom(coupon.getValidFrom());
            entity.setValidTo(coupon.getValidTo());
            entity.setVersion(0);
            entity.setDeleted(false);
            entity.setCreatedBy(operator);
            entity.setUpdatedBy(operator);
            customerCoupons.insert(entity);
            issuedCoupons.add(toCustomerCouponResponse(entity));
        }
        coupon.setIssuedQuantity(issued + request.quantity());
        coupon.setUpdatedBy(operator);
        coupons.updateById(coupon);
        return issuedCoupons;
    }

    public List<CustomerCouponResponse> myCoupons(long customerId, String status) {
        return customerCoupons.selectByCustomer(customerId, normalizeStatus(status)).stream()
                .map(MarketingService::toCustomerCouponResponse)
                .toList();
    }

    /**
     * 核销优惠券。已使用、已过期均拒绝，避免重复抵扣。
     */
    @Transactional
    public CustomerCouponResponse useCoupon(String couponNo, long orderId, String operator) {
        MarketingCustomerCouponEntity entity = customerCoupons.selectByCouponNo(couponNo.trim());
        if (entity == null) {
            throw new BusinessException(MarketingErrorCodes.COUPON_NO_NOT_FOUND);
        }
        if (CouponStatus.USED.name().equals(entity.getStatus())) {
            throw new BusinessException(MarketingErrorCodes.COUPON_ALREADY_USED);
        }
        if (entity.getValidTo() != null && entity.getValidTo().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(MarketingErrorCodes.COUPON_EXPIRED);
        }
        entity.setStatus(CouponStatus.USED.name());
        entity.setUsedOrderId(orderId);
        entity.setUsedAt(OffsetDateTime.now());
        entity.setUpdatedBy(operator);
        customerCoupons.updateById(entity);
        return toCustomerCouponResponse(customerCoupons.selectById(entity.getId()));
    }

    public PageData<FrequentSkuResponse> frequentSkus(long customerId, long page, long pageSize) {
        Page<FrequentSkuRow> pager = new Page<>(page, pageSize);
        List<FrequentSkuRow> rows = frequentSkus.selectFrequent(pager, customerId);
        List<FrequentSkuResponse> items = rows.stream().map(MarketingService::toFrequentSkuResponse).toList();
        return new PageData<>(items, page, pageSize, pager.getTotal());
    }

    public List<ReorderItemResponse> reorderItems(long orderId) {
        return frequentSkus.selectOrderItems(orderId).stream()
                .map(MarketingService::toReorderItemResponse)
                .toList();
    }

    /**
     * 下单成功后回写常用菜品：已存在则累加购买次数并刷新最近订单。
     */
    @Transactional
    public void recordFrequentSkus(long customerId, long orderId, List<Long> skuIds, String operator) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Long skuId : skuIds) {
            if (skuId == null) {
                continue;
            }
            MarketingFrequentSkuEntity entity = frequentSkus.selectByCustomerAndSku(customerId, skuId);
            if (entity == null) {
                entity = new MarketingFrequentSkuEntity();
                entity.setCustomerId(customerId);
                entity.setSkuId(skuId);
                entity.setBuyCount(1);
                entity.setVersion(0);
                entity.setDeleted(false);
                entity.setCreatedBy(operator);
                entity.setUpdatedBy(operator);
            } else {
                entity.setBuyCount((entity.getBuyCount() == null ? 0 : entity.getBuyCount()) + 1);
                entity.setUpdatedBy(operator);
            }
            entity.setLastOrderId(orderId);
            entity.setLastOrderedAt(now);
            if (entity.getId() == null) {
                frequentSkus.insert(entity);
            } else {
                frequentSkus.updateById(entity);
            }
        }
    }

    /**
     * 需求约定的四种结算方式。
     */
    public List<SettlementMethodResponse> settlementMethods() {
        return List.of(
                new SettlementMethodResponse(SettlementMethod.CREDIT, "账期支付", "按客户账期结算，到期统一收款"),
                new SettlementMethodResponse(SettlementMethod.COD, "货到付款", "送货到达后现场收款"),
                new SettlementMethodResponse(SettlementMethod.ONLINE, "在线支付", "下单时在线完成支付"),
                new SettlementMethodResponse(SettlementMethod.BALANCE, "余额充值", "使用客户预存余额扣减")
        );
    }

    private static MallThemeConfig defaultTheme() {
        return new MallThemeConfig("FRESH_GREEN", "#16A34A", "#F97316", "#F5F7FA", 16,
                "SHADOW", "COMFORTABLE", "BOTTOM");
    }

    private static MallThemeConfig toThemeConfig(MarketingThemeConfigEntity entity) {
        return new MallThemeConfig(entity.getThemeCode(), entity.getPrimaryColor(), entity.getAccentColor(),
                entity.getPageBackground(), entity.getCardRadius(), entity.getCardStyle(),
                entity.getProductCardStyle(), entity.getNavigationStyle());
    }

    private MarketingPromotionEntity requirePromotion(long id) {
        MarketingPromotionEntity entity = promotions.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(MarketingErrorCodes.PROMOTION_NOT_FOUND);
        }
        return entity;
    }

    private static void applyPromotion(MarketingPromotionEntity entity, PromotionSaveRequest request) {
        entity.setName(request.name().trim());
        entity.setType(promotionType(request.type()).name());
        entity.setScopeType(request.scopeType() == null || request.scopeType().isBlank()
                ? PromotionScope.ALL.name() : promotionScope(request.scopeType()).name());
        entity.setScopeIds(request.scopeIds());
        entity.setThresholdAmount(request.thresholdAmount());
        entity.setDiscountRate(request.discountRate());
        entity.setReduceAmount(request.reduceAmount());
        entity.setPromoPrice(request.promoPrice());
        entity.setGiftSkuId(request.giftSkuId());
        entity.setGiftQuantity(request.giftQuantity());
        entity.setLimitQuantity(request.limitQuantity());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setPriority(request.priority() == null ? 0 : request.priority());
        entity.setDescription(request.description());
    }

    private static void applyHomeSection(MarketingHomeSectionEntity entity, HomeSectionSaveRequest request) {
        entity.setSectionType(homeSectionType(request.sectionType()).name());
        entity.setTitle(request.title());
        entity.setPromotionId(request.promotionId());
        entity.setCategoryId(request.categoryId());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "ENABLED" : request.status().trim());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setPayload(request.payload());
    }

    private static String defaultStatus(String status) {
        return (status == null || status.isBlank()) ? PromotionStatus.DRAFT.name() : promotionStatus(status).name();
    }

    private static String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
    }

    private static PromotionType promotionType(String value) {
        try {
            return PromotionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketingErrorCodes.PROMOTION_TYPE_INVALID);
        }
    }

    private static PromotionScope promotionScope(String value) {
        try {
            return PromotionScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketingErrorCodes.PROMOTION_SCOPE_INVALID);
        }
    }

    private static PromotionStatus promotionStatus(String value) {
        try {
            return PromotionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketingErrorCodes.INVALID_PARAM);
        }
    }

    private static HomeSectionType homeSectionType(String value) {
        try {
            return HomeSectionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketingErrorCodes.HOME_SECTION_TYPE_INVALID);
        }
    }

    private static CouponType couponType(String value) {
        try {
            return CouponType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketingErrorCodes.COUPON_TYPE_INVALID);
        }
    }

    private static boolean effective(String status, OffsetDateTime startAt, OffsetDateTime endAt,
                                     OffsetDateTime now) {
        if (!PromotionStatus.ENABLED.name().equals(status)) {
            return false;
        }
        return (startAt == null || !startAt.isAfter(now)) && (endAt == null || !endAt.isBefore(now));
    }

    private static String couponNo() {
        return "CP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private static PromotionResponse toPromotionResponse(PromotionRow row, OffsetDateTime now) {
        return new PromotionResponse(row.getId(), row.getName(), safeType(row.getType()),
                safeScope(row.getScopeType()), row.getScopeIds(), row.getThresholdAmount(), row.getDiscountRate(),
                row.getReduceAmount(), row.getPromoPrice(), row.getGiftSkuId(), row.getGiftQuantity(),
                row.getLimitQuantity(), row.getStartAt(), row.getEndAt(), safeStatus(row.getStatus()),
                row.getPriority(), row.getDescription(),
                effective(row.getStatus(), row.getStartAt(), row.getEndAt(), now));
    }

    private static PromotionResponse toPromotionResponse(MarketingPromotionEntity entity, OffsetDateTime now) {
        return new PromotionResponse(entity.getId(), entity.getName(), safeType(entity.getType()),
                safeScope(entity.getScopeType()), entity.getScopeIds(), entity.getThresholdAmount(),
                entity.getDiscountRate(), entity.getReduceAmount(), entity.getPromoPrice(), entity.getGiftSkuId(),
                entity.getGiftQuantity(), entity.getLimitQuantity(), entity.getStartAt(), entity.getEndAt(),
                safeStatus(entity.getStatus()), entity.getPriority(), entity.getDescription(),
                effective(entity.getStatus(), entity.getStartAt(), entity.getEndAt(), now));
    }

    private static PromotionType safeType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PromotionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PromotionScope safeScope(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PromotionScope.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PromotionStatus safeStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PromotionStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static HomeSectionResponse toHomeSectionResponse(MarketingHomeSectionEntity entity) {
        HomeSectionType type = entity.getSectionType() == null ? null
                : HomeSectionType.valueOf(entity.getSectionType());
        return new HomeSectionResponse(entity.getId(), type, entity.getTitle(), entity.getPromotionId(),
                entity.getCategoryId(), entity.getSortOrder(), entity.getStatus(), entity.getStartAt(),
                entity.getEndAt(), entity.getPayload());
    }

    private static CouponResponse toCouponResponse(MarketingCouponEntity entity) {
        CouponType type = entity.getCouponType() == null ? null : CouponType.valueOf(entity.getCouponType());
        int total = entity.getTotalQuantity() == null ? 0 : entity.getTotalQuantity();
        int issued = entity.getIssuedQuantity() == null ? 0 : entity.getIssuedQuantity();
        return new CouponResponse(entity.getId(), entity.getName(), type, entity.getThresholdAmount(),
                entity.getDiscountRate(), entity.getReduceAmount(), total, issued, entity.getPerLimit(),
                total - issued, entity.getValidFrom(), entity.getValidTo(), entity.getStatus());
    }

    private static CustomerCouponResponse toCustomerCouponResponse(MarketingCustomerCouponEntity entity) {
        CouponStatus status = entity.getStatus() == null ? null : CouponStatus.valueOf(entity.getStatus());
        return new CustomerCouponResponse(entity.getId(), entity.getCouponId(), entity.getCustomerId(),
                entity.getCouponNo(), status, entity.getUsedOrderId(), entity.getUsedAt(),
                entity.getObtainedAt(), entity.getValidFrom(), entity.getValidTo());
    }

    private static FrequentSkuResponse toFrequentSkuResponse(FrequentSkuRow row) {
        return new FrequentSkuResponse(row.getSkuId(), row.getProductName(), row.getSkuCode(), row.getSpecName(),
                row.getSpecValues(), row.getSaleUnit(), row.getMarketPrice(), row.getLastUnitPrice(),
                row.getLastQuantity(), row.getBuyCount(), row.getLastOrderId(), row.getLastOrderedAt());
    }

    private static ReorderItemResponse toReorderItemResponse(ReorderItemRow row) {
        return new ReorderItemResponse(row.getSkuId(), row.getProductName(), row.getSkuCode(), row.getSpecName(),
                row.getSaleUnit(), row.getQuantity(), row.getUnitPrice());
    }
}
