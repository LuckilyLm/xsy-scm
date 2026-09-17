package com.xsy.scm.admin.module.business.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigUpdateForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ExternalConfigVO;
import com.xsy.scm.admin.module.business.finance.service.ExternalConfigService;
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
 * 外部系统配置 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "财务管理-外部系统配置")
public class ExternalConfigController {

    @Resource
    private ExternalConfigService externalConfigService;

    @Operation(summary = "分页查询外部系统配置 @author xsy-scm")
    @PostMapping("/finance/externalConfig/query")
    @SaCheckPermission("externalConfig:query")
    public ResponseDTO<PageResult<ExternalConfigVO>> query(@RequestBody @Valid ExternalConfigQueryForm queryForm) {
        return externalConfigService.query(queryForm);
    }

    @Operation(summary = "添加外部系统配置 @author xsy-scm")
    @PostMapping("/finance/externalConfig/add")
    @SaCheckPermission("externalConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid ExternalConfigAddForm addForm) {
        return externalConfigService.add(addForm);
    }

    @Operation(summary = "更新外部系统配置（密钥留空则不修改） @author xsy-scm")
    @PostMapping("/finance/externalConfig/update")
    @SaCheckPermission("externalConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid ExternalConfigUpdateForm updateForm) {
        return externalConfigService.update(updateForm);
    }

    @Operation(summary = "删除外部系统配置 @author xsy-scm")
    @GetMapping("/finance/externalConfig/delete/{configId}")
    @SaCheckPermission("externalConfig:delete")
    public ResponseDTO<String> delete(@PathVariable Long configId) {
        return externalConfigService.delete(configId);
    }

    @Operation(summary = "批量删除外部系统配置 @author xsy-scm")
    @PostMapping("/finance/externalConfig/batchDelete")
    @SaCheckPermission("externalConfig:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return externalConfigService.batchDelete(idList);
    }
}
