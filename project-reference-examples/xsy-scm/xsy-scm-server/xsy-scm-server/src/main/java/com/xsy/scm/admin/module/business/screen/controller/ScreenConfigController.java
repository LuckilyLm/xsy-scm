package com.xsy.scm.admin.module.business.screen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigAddForm;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigQueryForm;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigUpdateForm;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenConfigVO;
import com.xsy.scm.admin.module.business.screen.service.ScreenConfigService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据大屏配置 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_SCREEN)
public class ScreenConfigController {

    @Resource
    private ScreenConfigService screenConfigService;

    @Operation(summary = "分页查询大屏配置 @author xsy-scm")
    @PostMapping("/screen/config/query")
    @SaCheckPermission("screenConfig:query")
    public ResponseDTO<PageResult<ScreenConfigVO>> query(@RequestBody @Valid ScreenConfigQueryForm queryForm) {
        return screenConfigService.query(queryForm);
    }

    @Operation(summary = "添加大屏配置 @author xsy-scm")
    @PostMapping("/screen/config/add")
    @SaCheckPermission("screenConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid ScreenConfigAddForm addForm) {
        return screenConfigService.add(addForm);
    }

    @Operation(summary = "更新大屏配置 @author xsy-scm")
    @PostMapping("/screen/config/update")
    @SaCheckPermission("screenConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid ScreenConfigUpdateForm updateForm) {
        return screenConfigService.update(updateForm);
    }

    @Operation(summary = "删除大屏配置 @author xsy-scm")
    @GetMapping("/screen/config/delete/{screenId}")
    @SaCheckPermission("screenConfig:delete")
    public ResponseDTO<String> delete(@PathVariable Long screenId) {
        return screenConfigService.delete(screenId);
    }

    @Operation(summary = "批量删除大屏配置 @author xsy-scm")
    @PostMapping("/screen/config/batchDelete")
    @SaCheckPermission("screenConfig:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return screenConfigService.batchDelete(idList);
    }
}
