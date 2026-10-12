package com.xsy.scm.mall.cart.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.domain.form.MallCartEstimateForm;
import com.xsy.scm.mall.cart.domain.form.MallCartQuantityForm;
import com.xsy.scm.mall.cart.domain.form.MallCartRemoveForm;
import com.xsy.scm.mall.cart.domain.vo.MallCartEstimateVO;
import com.xsy.scm.mall.cart.domain.vo.MallCartVO;
import com.xsy.scm.mall.cart.service.MallCartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/cart")
@RequiredArgsConstructor
public class MallCartController {
    private final MallCartService cartService;

    @GetMapping
    public ResponseDTO<MallCartVO> cart() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.cart(MallRequestSupport.current()));
    }

    @PostMapping("/items")
    public ResponseDTO<MallCartVO> add(@Valid @RequestBody MallCartAddForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.add(MallRequestSupport.current(), form));
    }

    @PutMapping("/items/{skuId}")
    public ResponseDTO<MallCartVO> updateQuantity(@PathVariable Long skuId,
            @Valid @RequestBody MallCartQuantityForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.updateQuantity(MallRequestSupport.current(), skuId, form));
    }

    @DeleteMapping("/items/{skuId}")
    public ResponseDTO<MallCartVO> remove(@PathVariable Long skuId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.remove(MallRequestSupport.current(), skuId));
    }

    @PostMapping("/items/remove")
    public ResponseDTO<MallCartVO> removeBatch(@Valid @RequestBody MallCartRemoveForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.removeBatch(MallRequestSupport.current(), form));
    }

    @PostMapping("/estimate")
    public ResponseDTO<MallCartEstimateVO> estimate(@Valid @RequestBody MallCartEstimateForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.estimate(MallRequestSupport.current(), form.getSkuIds()));
    }

    @DeleteMapping
    public ResponseDTO<MallCartVO> clear() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(cartService.clear(MallRequestSupport.current()));
    }
}
