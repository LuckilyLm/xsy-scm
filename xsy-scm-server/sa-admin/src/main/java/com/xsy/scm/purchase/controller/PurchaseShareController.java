package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.purchase.domain.form.PurchaseShareCreateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseShareVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseShareService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 采购单二维码分享（后台侧）。 */
@RestController
@RequestMapping("/scm/purchase/share")
@RequiredArgsConstructor
public class PurchaseShareController {

    private final PurchaseShareService purchaseShareService;

    /** 生成分享链接：同一张单上的旧链接当场失效。 */
    @PostMapping("/create")
    @SaCheckPermission(PurchasePermission.SHARE_CREATE)
    public ResponseDTO<PurchaseShareVO> create(@RequestBody @Valid PurchaseShareCreateForm form) {
        return ResponseDTO.ok(purchaseShareService.create(form, ScmOperator.current()));
    }

    @PostMapping("/revoke")
    @SaCheckPermission(PurchasePermission.SHARE_REVOKE)
    public ResponseDTO<String> revoke(@RequestBody @Valid RevokeForm form) {
        purchaseShareService.revoke(form.getId());
        return ResponseDTO.ok();
    }

    @GetMapping("/list")
    @SaCheckPermission(PurchasePermission.SHARE_CREATE)
    public ResponseDTO<List<PurchaseShareVO>> list(@RequestParam @NotNull Long purchaseOrderId) {
        return ResponseDTO.ok(purchaseShareService.list(purchaseOrderId));
    }

    @Data
    public static class RevokeForm {
        @NotNull(message = "分享记录不能为空")
        private Long id;
    }
}
