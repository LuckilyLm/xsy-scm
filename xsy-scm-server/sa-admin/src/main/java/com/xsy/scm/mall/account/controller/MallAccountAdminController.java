package com.xsy.scm.mall.account.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.mall.account.constant.MallPermission;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallAccountStatusForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordResetForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.service.MallAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/customer/mall-account")
@RequiredArgsConstructor
public class MallAccountAdminController {
    private final MallAccountService accountService;

    @GetMapping("/{customerId}")
    @SaCheckPermission(MallPermission.ACCOUNT_QUERY)
    public ResponseDTO<MallAccountVO> detail(@PathVariable Long customerId) {
        return ResponseDTO.ok(accountService.detail(customerId));
    }

    @PostMapping("/create")
    @SaCheckPermission(MallPermission.ACCOUNT_CREATE)
    @OperateLog
    public ResponseDTO<MallAccountVO> create(@Valid @RequestBody MallAccountCreateForm form) {
        return ResponseDTO.ok(accountService.create(form));
    }

    @PostMapping("/status")
    @SaCheckPermission(MallPermission.ACCOUNT_STATUS)
    @OperateLog
    public ResponseDTO<String> status(@Valid @RequestBody MallAccountStatusForm form) {
        accountService.updateStatus(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/password/reset")
    @SaCheckPermission(MallPermission.ACCOUNT_PASSWORD_RESET)
    @OperateLog
    public ResponseDTO<String> reset(@Valid @RequestBody MallPasswordResetForm form) {
        accountService.resetPassword(form);
        return ResponseDTO.ok();
    }
}
