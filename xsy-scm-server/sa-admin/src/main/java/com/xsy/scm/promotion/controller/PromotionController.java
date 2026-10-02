package com.xsy.scm.promotion.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.promotion.domain.form.PromotionActivityForm;
import com.xsy.scm.promotion.domain.form.PromotionActivityQueryForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponIssueForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponQueryForm;
import com.xsy.scm.promotion.domain.form.PromotionDiscountConfirmForm;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.domain.vo.PromotionActivityVO;
import com.xsy.scm.promotion.domain.vo.PromotionCouponVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.permission.PromotionPermission;
import com.xsy.scm.promotion.service.PromotionActivityService;
import com.xsy.scm.promotion.service.PromotionCouponService;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销中心：活动、优惠券与优惠试算 / 冻结。
 *
 * <p>
 * 试算是只读的（{@code POST} 只是因为要传订单行数组），**不占用券、不写任何表**；
 * 只有 {@code confirm} 才占用券并冻结优惠。两者分开是 ADR-009 的明确要求：
 * 预览不等于最终占用。
 */
@RestController
@RequestMapping("/scm/promotion")
@Tag(name = "SCM 营销中心")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionActivityService promotionActivityService;

    private final PromotionCouponService promotionCouponService;

    private final PromotionDiscountService promotionDiscountService;

    // ------------------------------------------------------------------
    // 活动
    // ------------------------------------------------------------------

    @PostMapping("/activity/query")
    @SaCheckPermission(PromotionPermission.ACTIVITY_QUERY)
    public ResponseDTO<PageResult<PromotionActivityVO>> queryActivity(
            @Valid @RequestBody PromotionActivityQueryForm form) {
        return ResponseDTO.ok(promotionActivityService.queryPage(form));
    }

    @GetMapping("/activity/{id}")
    @SaCheckPermission(PromotionPermission.ACTIVITY_QUERY)
    public ResponseDTO<PromotionActivityVO> activityDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(promotionActivityService.detail(id));
    }

    @PostMapping("/activity/save")
    @SaCheckPermission(PromotionPermission.ACTIVITY_EDIT)
    @OperateLog
    public ResponseDTO<Long> saveActivity(@Valid @RequestBody PromotionActivityForm form) {
        return ResponseDTO.ok(promotionActivityService.save(form));
    }

    /**
     * 启停活动；生效中的活动不能再改内容，只能先停用。
     */
    @PostMapping("/activity/{id}/status")
    @SaCheckPermission(PromotionPermission.ACTIVITY_STATUS)
    @OperateLog
    public ResponseDTO<String> activityStatus(@PathVariable("id") Long id,
            @Valid @RequestBody PromotionStatusForm form) {
        promotionActivityService.updateStatus(id, form);
        return ResponseDTO.ok();
    }

    // ------------------------------------------------------------------
    // 优惠券
    // ------------------------------------------------------------------

    @PostMapping("/coupon/query")
    @SaCheckPermission(PromotionPermission.COUPON_QUERY)
    public ResponseDTO<PageResult<PromotionCouponVO>> queryCoupon(
            @Valid @RequestBody PromotionCouponQueryForm form) {
        return ResponseDTO.ok(promotionCouponService.queryPage(form));
    }

    @GetMapping("/coupon/{id}")
    @SaCheckPermission(PromotionPermission.COUPON_QUERY)
    public ResponseDTO<PromotionCouponVO> couponDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(promotionCouponService.detail(id));
    }

    @PostMapping("/coupon/save")
    @SaCheckPermission(PromotionPermission.COUPON_EDIT)
    @OperateLog
    public ResponseDTO<Long> saveCoupon(@Valid @RequestBody PromotionCouponForm form) {
        return ResponseDTO.ok(promotionCouponService.save(form));
    }

    /**
     * 发券：给某客户发 N 张可用券。
     */
    @PostMapping("/coupon/issue")
    @SaCheckPermission(PromotionPermission.COUPON_ISSUE)
    @OperateLog
    public ResponseDTO<Integer> issueCoupon(@Valid @RequestBody PromotionCouponIssueForm form) {
        return ResponseDTO.ok(promotionCouponService.issue(form));
    }

    /**
     * 某客户的券；{@code status} 为空返回全部。
     */
    @GetMapping("/coupon/instances")
    @SaCheckPermission(PromotionPermission.COUPON_QUERY)
    public ResponseDTO<List<PromotionCouponVO.Instance>> couponInstances(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseDTO.ok(promotionCouponService.listInstances(customerId, status));
    }

    // ------------------------------------------------------------------
    // 优惠试算与冻结
    // ------------------------------------------------------------------

    /**
     * 试算：只读，不占用券。
     */
    @PostMapping("/discount/preview")
    @SaCheckPermission(PromotionPermission.ACTIVITY_QUERY)
    public ResponseDTO<PromotionDiscountVO> preview(@Valid @RequestBody PromotionDiscountPreviewForm form) {
        return ResponseDTO.ok(promotionDiscountService.preview(form));
    }

    /**
     * 冻结：占用券并写入订单优惠快照；服务端重验活动与券的当前状态。
     */
    @PostMapping("/discount/confirm")
    @SaCheckPermission(PromotionPermission.ACTIVITY_QUERY)
    @OperateLog
    public ResponseDTO<PromotionDiscountVO> confirm(@Valid @RequestBody PromotionDiscountConfirmForm form) {
        return ResponseDTO.ok(promotionDiscountService.confirm(form));
    }
}
