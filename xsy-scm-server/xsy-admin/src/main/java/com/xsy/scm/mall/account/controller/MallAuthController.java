package com.xsy.scm.mall.account.controller;

import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallWechatPhoneLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallLoginVO;
import com.xsy.scm.mall.account.domain.vo.MallProfileVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.annoation.NoNeedLogin;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
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

    /**
     * @param form
     *            微信手机号授权code，不接受客户端自报手机号
     * @param request
     *            用于记录登录来源IP
     * @return 已开通且可交易账号的会话；未开通或微信授权失败时返回业务错误
     */
    @PostMapping("/wechat/phone-login")
    @NoNeedLogin
    public ResponseDTO<MallLoginVO> wechatPhoneLogin(@Valid @RequestBody MallWechatPhoneLoginForm form,
        HttpServletRequest request) {
        return ResponseDTO.ok(authService.wechatPhoneLogin(form, request.getRemoteAddr()));
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
        return (RequestMallCustomer) XsyRequestUtil.getRequestUser();
    }
}
