package com.xianshuyuan.scm.marketing.controller;

import com.xianshuyuan.scm.auth.security.AuthenticatedUser;
import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.marketing.dto.CouponIssueRequest;
import com.xianshuyuan.scm.marketing.dto.CouponPageQuery;
import com.xianshuyuan.scm.marketing.dto.CouponSaveRequest;
import com.xianshuyuan.scm.marketing.dto.HomeSectionSaveRequest;
import com.xianshuyuan.scm.marketing.dto.PromotionPageQuery;
import com.xianshuyuan.scm.marketing.dto.PromotionSaveRequest;
import com.xianshuyuan.scm.marketing.dto.ThemeConfigSaveRequest;
import com.xianshuyuan.scm.marketing.service.MarketingService;
import com.xianshuyuan.scm.marketing.vo.CouponResponse;
import com.xianshuyuan.scm.marketing.vo.CustomerCouponResponse;
import com.xianshuyuan.scm.marketing.vo.FrequentSkuResponse;
import com.xianshuyuan.scm.marketing.vo.HomeSectionResponse;
import com.xianshuyuan.scm.marketing.vo.PromotionResponse;
import com.xianshuyuan.scm.marketing.vo.ReorderItemResponse;
import com.xianshuyuan.scm.marketing.vo.SettlementMethodResponse;
import com.xianshuyuan.scm.mall.service.MallCatalogService;
import com.xianshuyuan.scm.mall.vo.MallHomeResponse;
import com.xianshuyuan.scm.mall.vo.MallThemeConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台营销管理接口。写入操作记录操作人。
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/marketing")
public class MarketingController {

    private final MarketingService marketing;
    private final MallCatalogService catalog;

    @GetMapping("/theme")
    public ApiResponse<MallThemeConfig> currentTheme() {
        return ApiResponse.success(marketing.currentTheme());
    }

    /**
     * 后台商城预览：与小程序共用同一份首页聚合数据，仅分类按全量可见性统计。
     * 后台会话无法通过商城客户令牌链访问 /api/mall/**，因此单列此入口。
     */
    @GetMapping("/home-preview")
    public ApiResponse<MallHomeResponse> homePreview() {
        return ApiResponse.success(catalog.homePreview());
    }

    @PutMapping("/theme")
    public ApiResponse<MallThemeConfig> saveTheme(Authentication authentication,
                                                   @Valid @RequestBody ThemeConfigSaveRequest request) {
        return ApiResponse.success(marketing.saveTheme(request, operatorName(authentication)));
    }

    @GetMapping("/promotions")
    public ApiResponse<PageData<PromotionResponse>> listPromotions(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(marketing.listPromotions(new PromotionPageQuery(page, pageSize, keyword, type, status)));
    }

    @GetMapping("/promotions/effective")
    public ApiResponse<List<PromotionResponse>> effectivePromotions() {
        return ApiResponse.success(marketing.effectivePromotions());
    }

    @PostMapping("/promotions")
    public ApiResponse<PromotionResponse> createPromotion(Authentication authentication,
                                                          @Valid @RequestBody PromotionSaveRequest request) {
        return ApiResponse.success(marketing.createPromotion(request, operatorName(authentication)));
    }

    @PutMapping("/promotions/{id}")
    public ApiResponse<PromotionResponse> updatePromotion(Authentication authentication, @PathVariable long id,
                                                          @Valid @RequestBody PromotionSaveRequest request) {
        return ApiResponse.success(marketing.updatePromotion(id, request, operatorName(authentication)));
    }

    @PostMapping("/promotions/{id}/status")
    public ApiResponse<PromotionResponse> changePromotionStatus(Authentication authentication,
                                                                @PathVariable long id,
                                                                @RequestParam String status) {
        return ApiResponse.success(marketing.changePromotionStatus(id, status, operatorName(authentication)));
    }

    @GetMapping("/home-sections")
    public ApiResponse<List<HomeSectionResponse>> listHomeSections() {
        return ApiResponse.success(marketing.listHomeSections());
    }

    @PostMapping("/home-sections")
    public ApiResponse<HomeSectionResponse> createHomeSection(Authentication authentication,
                                                              @Valid @RequestBody HomeSectionSaveRequest request) {
        return ApiResponse.success(marketing.createHomeSection(request, operatorName(authentication)));
    }

    @PutMapping("/home-sections/{id}")
    public ApiResponse<HomeSectionResponse> updateHomeSection(Authentication authentication, @PathVariable long id,
                                                              @Valid @RequestBody HomeSectionSaveRequest request) {
        return ApiResponse.success(marketing.updateHomeSection(id, request, operatorName(authentication)));
    }

    @GetMapping("/coupons")
    public ApiResponse<PageData<CouponResponse>> listCoupons(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(marketing.listCoupons(new CouponPageQuery(page, pageSize, keyword, status)));
    }

    @PostMapping("/coupons")
    public ApiResponse<CouponResponse> createCoupon(Authentication authentication,
                                                    @Valid @RequestBody CouponSaveRequest request) {
        return ApiResponse.success(marketing.createCoupon(request, operatorName(authentication)));
    }

    @PostMapping("/coupons/issue")
    public ApiResponse<List<CustomerCouponResponse>> issueCoupon(Authentication authentication,
                                                                 @Valid @RequestBody CouponIssueRequest request) {
        return ApiResponse.success(marketing.issueCoupon(request, operatorName(authentication)));
    }

    @GetMapping("/coupons/mine")
    public ApiResponse<List<CustomerCouponResponse>> myCoupons(@RequestParam long customerId,
                                                               @RequestParam(required = false) String status) {
        return ApiResponse.success(marketing.myCoupons(customerId, status));
    }

    @PostMapping("/coupons/use")
    public ApiResponse<CustomerCouponResponse> useCoupon(Authentication authentication,
                                                         @RequestParam String couponNo,
                                                         @RequestParam long orderId) {
        return ApiResponse.success(marketing.useCoupon(couponNo, orderId, operatorName(authentication)));
    }

    @GetMapping("/frequent-skus")
    public ApiResponse<PageData<FrequentSkuResponse>> frequentSkus(
            @RequestParam long customerId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.success(marketing.frequentSkus(customerId, page, pageSize));
    }

    @GetMapping("/reorder/{orderId}")
    public ApiResponse<List<ReorderItemResponse>> reorderItems(@PathVariable long orderId) {
        return ApiResponse.success(marketing.reorderItems(orderId));
    }

    @GetMapping("/settlement-methods")
    public ApiResponse<List<SettlementMethodResponse>> settlementMethods() {
        return ApiResponse.success(marketing.settlementMethods());
    }

    private static String operatorName(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.displayName() != null && !user.displayName().isBlank() ? user.displayName() : user.username();
        }
        return "SYSTEM";
    }
}
