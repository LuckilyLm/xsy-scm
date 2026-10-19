package com.xsy.scm.purchase.controller;

import com.xsy.scm.purchase.domain.form.PurchaseShareFeedbackForm;
import com.xsy.scm.purchase.domain.vo.PurchaseSharePublicVO;
import com.xsy.scm.purchase.service.PurchaseShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购单分享页（手机端，免登录）。
 *
 * <p>
 * 令牌即凭证：路径里的 token 决定能看到哪张单。这里不读登录态，也不套数据范围—— 链接的签发与撤销在后台完成，撤销后令牌立即失效。
 */
@RestController
@RequestMapping("/scm/purchase/share/public")
@RequiredArgsConstructor
public class PurchaseSharePublicController {

    private final PurchaseShareService purchaseShareService;

    @GetMapping("/{token}")
    public ResponseDTO<PurchaseSharePublicVO> view(@PathVariable String token) {
        return ResponseDTO.ok(purchaseShareService.view(token));
    }

    @PostMapping("/{token}/feedback")
    public ResponseDTO<String> feedback(@PathVariable String token, @RequestBody @Valid PurchaseShareFeedbackForm form,
            HttpServletRequest request) {
        purchaseShareService.submitFeedback(token, form, request.getRemoteAddr());
        return ResponseDTO.ok();
    }
}
