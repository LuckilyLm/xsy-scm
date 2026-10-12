package com.xsy.scm.mall.order.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/orders")
@RequiredArgsConstructor
public class MallOrderController {
    private final MallCheckoutService checkoutService;
    private final MallOrderQueryService orderQueryService;

    @GetMapping
    public ResponseDTO<PageResult<MallOrderVO>> list(@Valid MallOrderQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderQueryService.list(MallRequestSupport.current(), form));
    }

    @GetMapping("/{orderId}")
    public ResponseDTO<MallOrderVO> detail(@PathVariable Long orderId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(orderQueryService.detail(MallRequestSupport.current(), orderId));
    }

    /** 结算预览：返回明细、预估金额与价格指纹；不占用库存、不建单 */
    @PostMapping("/preview")
    public ResponseDTO<MallCheckoutPreviewVO> preview(@Valid @RequestBody MallCheckoutPreviewForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(checkoutService.preview(MallRequestSupport.current(), form));
    }

    /**
     * 提交订单：必须携带 {@code Idempotency-Key} 与预览返回的 {@code priceFingerprint}。
     * 服务端重验客户、地址、商品与价格后写入正式销售订单；库存由后台在货到后显式预留。
     */
    @PostMapping
    public ResponseDTO<MallOrderSubmitVO> submit(@Valid @RequestBody MallOrderSubmitForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(checkoutService.submit(MallRequestSupport.current(), form, key));
    }
}
