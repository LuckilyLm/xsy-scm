package com.xsy.scm.customer.controller;

import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;
import com.xsy.scm.customer.service.CustomerPromotionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推广码扫码落地（免登录）。
 *
 * <p>
 * 挂在 {@code /scm/mall/promo/**} 下并被排除在商城登录拦截器之外：扫码的人可能还不是客户， 没有账号也就没有令牌。码撤销后立即不可用。
 */
@RestController
@RequestMapping("/scm/mall/promo")
@RequiredArgsConstructor
public class CustomerPromotionPublicController {

    private final CustomerPromotionService customerPromotionService;

    @GetMapping("/{code}")
    public ResponseDTO<CustomerPromotionVO.Landing> landing(@PathVariable String code, HttpServletRequest request) {
        return ResponseDTO
                .ok(customerPromotionService.landing(code, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
}
