package com.xsy.scm.mall.promotion.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.promotion.domain.form.MallCouponQueryForm;
import com.xsy.scm.mall.promotion.domain.form.MallPromotionPageForm;
import com.xsy.scm.mall.promotion.domain.vo.MallClaimableCouponVO;
import com.xsy.scm.mall.promotion.domain.vo.MallCouponInstanceVO;
import com.xsy.scm.mall.promotion.domain.vo.MallPromotionActivityVO;
import com.xsy.scm.mall.promotion.service.MallPromotionService;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/promotion")
@RequiredArgsConstructor
public class MallPromotionController {

    private final MallPromotionService promotionService;

    @GetMapping("/activities")
    public ResponseDTO<List<MallPromotionActivityVO>> activities() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(promotionService.activities(MallRequestSupport.current()));
    }

    @GetMapping("/new-products")
    public ResponseDTO<List<MallProductVO>> newProducts() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(promotionService.newProducts(MallRequestSupport.current()));
    }

    @GetMapping("/coupons/claimable")
    public ResponseDTO<PageResult<MallClaimableCouponVO>> claimableCoupons(@Valid MallPromotionPageForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(promotionService.claimableCoupons(MallRequestSupport.current(), form));
    }

    @GetMapping("/coupons/mine")
    public ResponseDTO<PageResult<MallCouponInstanceVO>> myCoupons(@Valid MallCouponQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(promotionService.myCoupons(MallRequestSupport.current(), form));
    }

    /**
     * 客户自助领券：必须携带 {@code Idempotency-Key}，重复点击返回首次领取结果而不是报错。
     */
    @PostMapping("/coupons/{couponId}/claim")
    public ResponseDTO<Long> claim(@PathVariable Long couponId,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(promotionService.claim(MallRequestSupport.current(), couponId, key));
    }
}
