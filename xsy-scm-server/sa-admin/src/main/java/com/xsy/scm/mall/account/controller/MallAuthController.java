package com.xsy.scm.mall.account.controller;

import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallLoginVO;
import com.xsy.scm.mall.account.domain.vo.MallProfileVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.annoation.NoNeedLogin;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/auth")
@RequiredArgsConstructor
public class MallAuthController {
    private final MallAuthService authService;

    @PostMapping("/login")
    @NoNeedLogin
    public ResponseDTO<MallLoginVO> login(@Valid @RequestBody MallLoginForm form, HttpServletRequest request) {
        return ResponseDTO.ok(authService.login(form, request.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseDTO<String> logout() {
        authService.logout();
        return ResponseDTO.ok();
    }

    @GetMapping("/profile")
    public ResponseDTO<MallProfileVO> profile() {
        return ResponseDTO.ok(authService.profile(current()));
    }

    @PostMapping("/password/change")
    public ResponseDTO<MallProfileVO> changePassword(@Valid @RequestBody MallPasswordChangeForm form) {
        return ResponseDTO.ok(authService.changePassword(current(), form));
    }

    private static RequestMallCustomer current() {
        return (RequestMallCustomer) SmartRequestUtil.getRequestUser();
    }
}
