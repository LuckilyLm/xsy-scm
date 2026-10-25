package com.xsy.scm.mall.home.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.home.domain.form.MallHomeDraftSaveForm;
import com.xsy.scm.mall.home.domain.form.MallHomePreviewForm;
import com.xsy.scm.mall.home.domain.form.MallHomePopupForm;
import com.xsy.scm.mall.home.domain.form.MallHomePublishForm;
import com.xsy.scm.mall.home.domain.form.MallHomeRollbackForm;
import com.xsy.scm.mall.home.domain.vo.MallHomeDraftVO;
import com.xsy.scm.mall.home.domain.vo.MallHomePageVO;
import com.xsy.scm.mall.home.domain.vo.MallHomePopupVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeReleaseVO;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;
import com.xsy.scm.mall.home.service.MallHomePopupService;
import com.xsy.scm.mall.home.service.MallHomeProjectionService;
import com.xsy.scm.mall.permission.MallPermission;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
/**
 * 商城装修管理端。
 *
 * <p>
 * 路径刻意不落在 {@code /scm/mall/**} 下：那个前缀归 {@code MallCustomerInterceptor} 管，员工令牌会被判成非客户身份（30007）。 管理端与客户端共用域名，但走两套拦截器。
 */
@RequestMapping("/scm/mall-admin/home")
public class MallHomeAdminController {
    private final MallHomeLayoutService layoutService;
    private final MallHomeProjectionService projectionService;
    private final MallHomePopupService popupService;
    private final CustomerService customerService;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    @GetMapping("/draft")
    @SaCheckPermission(MallPermission.HOME_QUERY)
    public ResponseDTO<MallHomeDraftVO> draft() {
        return ResponseDTO.ok(layoutService.draft());
    }

    @GetMapping("/history")
    @SaCheckPermission(MallPermission.HOME_QUERY)
    public ResponseDTO<List<MallHomeReleaseVO>> history() {
        return ResponseDTO.ok(layoutService.history());
    }

    @GetMapping("/popup")
    @SaCheckPermission(MallPermission.POPUP_QUERY)
    public ResponseDTO<MallHomePopupVO> popup() {
        return ResponseDTO.ok(popupService.detail());
    }

    @PostMapping("/popup")
    @SaCheckPermission(MallPermission.POPUP_EDIT)
    @OperateLog
    public ResponseDTO<MallHomePopupVO> savePopup(@Valid @RequestBody MallHomePopupForm form) {
        return ResponseDTO.ok(popupService.save(form));
    }

    @PostMapping("/preview")
    @SaCheckPermission(MallPermission.HOME_QUERY)
    public ResponseDTO<MallHomePageVO> preview(@Valid @RequestBody MallHomePreviewForm form) {
        customerService.requireInCurrentScope(form.getCustomerId());
        warehouseScopeGuard.require(form.getWarehouseId());
        return ResponseDTO.ok(projectionService.draftPreview(form.getCustomerId(), form.getWarehouseId()));
    }

    @PostMapping("/draft")
    @SaCheckPermission(MallPermission.HOME_EDIT)
    @OperateLog
    public ResponseDTO<MallHomeDraftVO> save(@Valid @RequestBody MallHomeDraftSaveForm form) {
        return ResponseDTO.ok(layoutService.save(form));
    }

    @PostMapping("/publish")
    @SaCheckPermission(MallPermission.HOME_PUBLISH)
    @OperateLog
    public ResponseDTO<Long> publish(@Valid @RequestBody MallHomePublishForm form) {
        return ResponseDTO.ok(layoutService.publish(form));
    }

    @PostMapping("/rollback")
    @SaCheckPermission(MallPermission.HOME_PUBLISH)
    @OperateLog
    public ResponseDTO<Long> rollback(@Valid @RequestBody MallHomeRollbackForm form) {
        return ResponseDTO.ok(layoutService.rollback(form));
    }
}
