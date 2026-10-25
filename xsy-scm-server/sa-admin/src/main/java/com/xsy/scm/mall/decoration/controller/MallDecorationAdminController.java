package com.xsy.scm.mall.decoration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.mall.decoration.domain.form.MallPagePublishForm;
import com.xsy.scm.mall.decoration.domain.form.MallPageSaveForm;
import com.xsy.scm.mall.decoration.domain.form.MallStoreSaveForm;
import com.xsy.scm.mall.decoration.domain.vo.MallPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStoreVO;
import com.xsy.scm.mall.decoration.service.MallDecorationService;
import com.xsy.scm.mall.permission.MallPermission;
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
@RequiredArgsConstructor
@RequestMapping("/scm/mall-admin/decoration")
public class MallDecorationAdminController {
    private final MallDecorationService decorationService;

    @GetMapping("/page/{pageCode}")
    @SaCheckPermission(MallPermission.PAGE_QUERY)
    public ResponseDTO<MallPageVO> page(@PathVariable String pageCode) {
        return ResponseDTO.ok(decorationService.page(pageCode));
    }

    @PostMapping("/page")
    @SaCheckPermission(MallPermission.PAGE_EDIT)
    @OperateLog
    public ResponseDTO<MallPageVO> savePage(@Valid @RequestBody MallPageSaveForm form) {
        return ResponseDTO.ok(decorationService.savePage(form));
    }

    @PostMapping("/page/publish")
    @SaCheckPermission(MallPermission.PAGE_EDIT)
    @OperateLog
    public ResponseDTO<MallPageVO> publishPage(@Valid @RequestBody MallPagePublishForm form) {
        return ResponseDTO.ok(decorationService.publishPage(form));
    }

    @GetMapping("/store")
    @SaCheckPermission(MallPermission.STORE_QUERY)
    public ResponseDTO<MallStoreVO> store() {
        return ResponseDTO.ok(decorationService.store());
    }

    @PostMapping("/store")
    @SaCheckPermission(MallPermission.STORE_EDIT)
    @OperateLog
    public ResponseDTO<MallStoreVO> saveStore(@Valid @RequestBody MallStoreSaveForm form) {
        return ResponseDTO.ok(decorationService.saveStore(form));
    }
}
